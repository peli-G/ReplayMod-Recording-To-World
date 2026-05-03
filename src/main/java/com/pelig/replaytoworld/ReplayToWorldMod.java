package com.pelig.replaytoworld;

import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;
import static com.mojang.brigadier.arguments.StringArgumentType.greedyString;
import static com.mojang.brigadier.arguments.StringArgumentType.getString;

public class ReplayToWorldMod implements ClientModInitializer {

    public static final String MOD_ID = "replay-to-world";
    public static final Logger LOGGER  = LoggerFactory.getLogger(MOD_ID);

    /** .minecraft/replay_recordings/ — default ReplayMod folder */
    public static Path getReplaysFolder() {
        return FabricLoader.getInstance().getGameDir().resolve("replay_recordings");
    }

    /** List .mcpr files in the replays folder (top-level only — ReplayMod doesn't use subdirs). */
    public static List<String> listReplays(Path root, Path dir) throws IOException {
        List<String> results = new ArrayList<>();
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir)) {
            for (Path p : ds) {
                if (!Files.isDirectory(p)) {
                    String name = p.getFileName().toString().toLowerCase();
                    if (name.endsWith(".mcpr")) {
                        results.add(p.getFileName().toString());
                    }
                }
            }
        }
        results.sort(String::compareToIgnoreCase);
        return results;
    }

    /** Search replaysRoot (top-level only) for a file matching the given name. */
    public static Path findReplay(Path replaysRoot, String name) {
        try {
            Path direct = replaysRoot.resolve(name);
            if (Files.exists(direct) && !Files.isDirectory(direct)) return direct;

            final String nameLower = name.toLowerCase();
            final String nameNoExt = nameLower.contains(".")
                    ? nameLower.substring(0, nameLower.lastIndexOf('.')) : nameLower;

            try (DirectoryStream<Path> ds = Files.newDirectoryStream(replaysRoot)) {
                for (Path p : ds) {
                    if (Files.isDirectory(p)) continue;
                    String fn = p.getFileName().toString().toLowerCase();
                    String fnNoExt = fn.contains(".") ? fn.substring(0, fn.lastIndexOf('.')) : fn;
                    if (fn.equals(nameLower) || fnNoExt.equals(nameNoExt)) return p;
                }
            }
        } catch (IOException e) {
            // fall through
        }
        return null;
    }

    @Override
    public void onInitializeClient() {

        // Ensure replay_recordings/ exists on startup
        try {
            Files.createDirectories(getReplaysFolder());
        } catch (IOException e) {
            LOGGER.warn("[ReplayToWorld] Could not create replay_recordings folder: {}", e.getMessage());
        }

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {

            dispatcher.register(literal("replaytoworld")

                // /replaytoworld list
                .then(literal("list").executes(ctx -> {
                    Path replaysDir = getReplaysFolder();
                    try {
                        List<String> names = listReplays(replaysDir, replaysDir);
                        if (names.isEmpty()) {
                            ctx.getSource().sendFeedback(Component.literal(
                                    "§e[ReplayToWorld] No replays found in replay_recordings/"));
                        } else {
                            ctx.getSource().sendFeedback(Component.literal(
                                    "§a[ReplayToWorld] Replays in replay_recordings/:"));
                            for (String name : names) {
                                ctx.getSource().sendFeedback(Component.literal("§7  - " + name));
                            }
                        }
                    } catch (IOException e) {
                        ctx.getSource().sendError(Component.literal(
                                "§c[ReplayToWorld] Could not read replay_recordings/: " + e.getMessage()));
                    }
                    return 1;
                }))

                // /replaytoworld <name or full path>
                .then(argument("path", greedyString())
                .suggests((SuggestionProvider<FabricClientCommandSource>) (ctx, builder) -> {
                    try {
                        String remaining = builder.getRemaining().toLowerCase();
                        for (String name : listReplays(getReplaysFolder(), getReplaysFolder())) {
                            String normalized = name.replace("\\", "/");
                            if (normalized.toLowerCase().contains(remaining)) {
                                builder.suggest(normalized);
                            }
                        }
                    } catch (IOException ignored) {}
                    return builder.buildFuture();
                })
                .executes(ctx -> {
                    String input = getString(ctx, "path").trim()
                            .replace("\"", "").replace("'", "");
                    Minecraft mc = Minecraft.getInstance();

                    var gameRegistryAccess = mc.getConnection() != null
                            ? mc.getConnection().registryAccess()
                            : mc.level != null ? mc.level.registryAccess() : null;
                    if (gameRegistryAccess == null) {
                        ctx.getSource().sendError(Component.literal(
                                "[ReplayToWorld] Must be in a world or replay to use this command."));
                        return 1;
                    }

                    // Resolve: full path first, then search replay_recordings/
                    Path mcprPath = Paths.get(input);
                    if (!Files.exists(mcprPath)) {
                        Path found = findReplay(getReplaysFolder(), input);
                        if (found != null) mcprPath = found;
                    }

                    boolean started = McprWorldConverter.start(mcprPath, gameRegistryAccess, mc);
                    if (!started) {
                        ctx.getSource().sendError(Component.literal(
                                "[ReplayToWorld] Already converting, please wait."));
                    }
                    return 1;
                }))

                // /replaytoworld  (no args) — list replays
                .executes(ctx -> {
                    Path replaysDir = getReplaysFolder();
                    try {
                        List<String> names = listReplays(replaysDir, replaysDir);
                        if (names.isEmpty()) {
                            ctx.getSource().sendFeedback(Component.literal(
                                    "§e[ReplayToWorld] No replays found in replay_recordings/. " +
                                    "Usage: /replaytoworld <filename>"));
                        } else {
                            ctx.getSource().sendFeedback(Component.literal(
                                    "§a[ReplayToWorld] Replays (use /replaytoworld <name>):"));
                            for (String name : names) {
                                ctx.getSource().sendFeedback(Component.literal("§7  - " + name));
                            }
                        }
                    } catch (IOException e) {
                        ctx.getSource().sendFeedback(Component.literal(
                                "§e[ReplayToWorld] Usage: /replaytoworld <filename>"));
                    }
                    return 1;
                })
            );
        });

        LOGGER.info("Replay-to-World loaded. Usage: /replaytoworld <recording name or path>");
    }
}

package com.pelig.replaytoworld.screen;

import com.pelig.replaytoworld.RrRegistryLoader;
import com.pelig.replaytoworld.ReplayToWorldMod;
import com.pelig.replaytoworld.McprWorldConverter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.AlertScreen;
import net.minecraft.client.gui.screens.GenericMessageScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class RrConversionLauncher {

    private RrConversionLauncher() {}

    public static void start(Minecraft minecraft, Screen previousScreen, List<Path> recordings) {
        start(minecraft, previousScreen, recordings, null);
    }

    public static void startImport(Minecraft minecraft, Screen previousScreen, List<Path> recordings, Path targetWorld) {
        start(minecraft, previousScreen, recordings, targetWorld);
    }

    private static void start(Minecraft minecraft, Screen previousScreen, List<Path> recordings, Path targetWorld) {
        List<Path> paths = new ArrayList<>(recordings);
        if (paths.isEmpty()) {
            ReplayToWorldMod.LOGGER.warn("[RrToWorld] Convert requested with no recordings selected");
            return;
        }

        var connection = minecraft.getConnection();
        var level = minecraft.level;
        var cached = ReplayToWorldMod.cachedRegistryAccess;

        ReplayToWorldMod.LOGGER.info(
                "[RrToWorld] Convert clicked — recordings={}, targetWorld={}, connection={}, level={}, cachedRegistryAccess={}",
                paths.size(), targetWorld, connection != null, level != null, cached != null);

        RegistryAccess registryAccess;
        String source;
        if (connection != null) {
            registryAccess = connection.registryAccess();
            source = "live connection";
        } else if (level != null) {
            registryAccess = level.registryAccess();
            source = "live level";
        } else if (cached != null) {
            registryAccess = cached;
            source = "cached from last session";
        } else {
            registryAccess = null;
            source = "none available";
        }

        ReplayToWorldMod.LOGGER.info("[RrToWorld] Using registry access source: {}", source);

        if (registryAccess == null) {
            ReplayToWorldMod.LOGGER.info(
                    "[RrToWorld] No live/cached registries — loading vanilla registries in the background");
            startAfterLoadingRegistries(minecraft, previousScreen, paths, targetWorld);
            return;
        }

        boolean started = targetWorld != null
                ? McprWorldConverter.startImport(paths, registryAccess, minecraft, targetWorld)
                : McprWorldConverter.start(paths, registryAccess, minecraft);
        ReplayToWorldMod.LOGGER.info("[RrToWorld] McprWorldConverter start returned: {}", started);
        if (started) {
            minecraft.setScreen(new RrConvertProgressScreen(previousScreen));
        }
    }

    private static void startAfterLoadingRegistries(Minecraft minecraft, Screen previousScreen, List<Path> paths, Path targetWorld) {
        minecraft.setScreen(new GenericMessageScreen(Component.literal("Loading registries...")));

        RrRegistryLoader.loadVanillaRegistries().thenAccept(registryAccess -> {
            minecraft.execute(() -> {
                ReplayToWorldMod.LOGGER.info("[RrToWorld] Vanilla registries loaded, starting conversion");
                boolean started = targetWorld != null
                        ? McprWorldConverter.startImport(paths, registryAccess, minecraft, targetWorld)
                        : McprWorldConverter.start(paths, registryAccess, minecraft);
                if (started) {
                    minecraft.setScreen(new RrConvertProgressScreen(previousScreen));
                } else {
                    minecraft.setScreen(previousScreen);
                }
            });
        }).exceptionally(throwable -> {
            minecraft.execute(() -> {
                ReplayToWorldMod.LOGGER.error("[RrToWorld] Failed to load vanilla registries", throwable);
                minecraft.setScreen(new AlertScreen(
                        () -> minecraft.setScreen(previousScreen),
                        Component.literal("Couldn't Load Registries"),
                        Component.literal("Failed to load vanilla registries: " + throwable.getMessage()
                                + " — try joining any world once this session instead."),
                        Component.literal("Ok"),
                        true
                ));
            });
            return null;
        });
    }
}

package com.pelig.replaytoworld;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.minecraft.ChatFormatting;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.AlertScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class RrVersionCheck {

    private RrVersionCheck() {}

    public static String findMismatch(List<Path> recordings) {
        for (Path recording : recordings) {
            String message = findMismatch(recording);
            if (message != null) return message;
        }
        return null;
    }

    public static String findMismatch(Path recording) {
        String recorded = readRecordedVersion(recording);
        String current = SharedConstants.getCurrentVersion().name();
        RrDeveloperMode.log("Version check for {}: recorded on {}, instance is {}",
                recording.getFileName(), recorded, current);
        if (VersionSupport.accepts(recorded)) return null;
        if (RrDeveloperMode.isEnabled()) {
            RrDeveloperMode.log("Version limit bypassed for {} (recorded on {}, instance is {})",
                    recording.getFileName(), recorded, current);
            return null;
        }
        RrDiagnostics.info("[ReplayToWorld] Version mismatch for {}: recorded on {}, instance is {}",
                recording.getFileName(), recorded, current);
        return "This Replay was recorded on version " + recorded + ", and you are trying to convert it on "
                + current + ". Please use an older or newer version of this mod that lets you convert this Replay.";
    }

    public static void showError(Minecraft minecraft, Screen returnTo, String message) {
        minecraft.setScreen(new AlertScreen(
                () -> minecraft.setScreen(returnTo),
                Component.literal("Version Mismatch").withStyle(ChatFormatting.RED),
                Component.literal(message).withStyle(ChatFormatting.RED),
                Component.literal("Close"),
                true
        ));
    }

    private static String readRecordedVersion(Path recording) {
        try (ZipFile zip = new ZipFile(recording.toFile())) {
            ZipEntry entry = zip.getEntry("metaData.json");
            if (entry == null) return null;
            try (InputStreamReader reader = new InputStreamReader(zip.getInputStream(entry), StandardCharsets.UTF_8)) {
                JsonObject json = new Gson().fromJson(reader, JsonObject.class);
                if (json == null || !json.has("mcversion") || json.get("mcversion").isJsonNull()) return null;
                return json.get("mcversion").getAsString();
            }
        } catch (Exception e) {
            return null;
        }
    }
}

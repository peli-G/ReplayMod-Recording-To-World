package com.pelig.replaytoworld;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

public final class RrDeveloperMode {

    private static volatile boolean enabled = false;

    private RrDeveloperMode() {}

    public static boolean isEnabled() {
        return enabled;
    }

    public static boolean toggle() {
        enabled = !enabled;
        ReplayToWorldMod.LOGGER.info("[ReplayToWorld][DEV] Developer mode {}", enabled ? "enabled" : "disabled");
        return enabled;
    }

    public static void log(String message, Object... args) {
        if (enabled) ReplayToWorldMod.LOGGER.info("[ReplayToWorld][DEV] " + message, args);
    }

    public static void error(String message, Throwable error) {
        if (enabled) ReplayToWorldMod.LOGGER.error("[ReplayToWorld][DEV] " + message, error);
    }

    public static void drawLabel(GuiGraphics guiGraphics, Font font, int screenHeight) {
        if (!enabled) return;
        int y = screenHeight - font.lineHeight - 4;
        if (RrDiagnostics.isEnabled()) y -= font.lineHeight + 2;
        guiGraphics.drawString(font, "Developer Mode", 4, y, 0xFFAA0000, true);
    }
}

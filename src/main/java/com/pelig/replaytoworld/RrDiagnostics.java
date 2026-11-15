package com.pelig.replaytoworld;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import org.lwjgl.glfw.GLFW;

public final class RrDiagnostics {

    private static volatile boolean enabled = false;
    private static volatile boolean viewerExpected = false;
    private static volatile boolean installed = false;
    private static volatile java.lang.ref.WeakReference<net.minecraft.client.gui.screens.Screen> viewerScreen = null;

    private RrDiagnostics() {}

    public static boolean isEnabled() {
        return enabled;
    }

    public static boolean toggle() {
        enabled = !enabled;
        ReplayToWorldMod.LOGGER.info("[ReplayToWorld] Diagnostic mode {}", enabled ? "enabled" : "disabled");
        return enabled;
    }

    public static void info(String message, Object... args) {
        if (enabled) ReplayToWorldMod.LOGGER.info(message, args);
    }

    public static void expectViewer() {
        viewerExpected = true;
    }

    public static synchronized void install() {
        if (installed) return;
        installed = true;
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (!isDiagnosticScreenOnInit(screen)) return;
            ScreenKeyboardEvents.allowKeyPress(screen).register((s, keyEvent) -> {
                int mods = keyEvent.modifiers();
                boolean chord = (mods & GLFW.GLFW_MOD_CONTROL) != 0 && (mods & GLFW.GLFW_MOD_SHIFT) != 0;
                if (chord && keyEvent.key() == GLFW.GLFW_KEY_D) {
                    toggle();
                }
                return !chord;
            });
            ScreenEvents.afterRender(screen).register((s, g, mouseX, mouseY, tickDelta) ->
                    drawLabel(g, client.font, s.height));
        });
    }

    private static boolean isDiagnosticScreenOnInit(net.minecraft.client.gui.screens.Screen screen) {
        boolean ours = screen.getClass().getName().startsWith("com.pelig.replaytoworld.screen.");
        if (!ours && viewerExpected) {
            viewerExpected = false;
            viewerScreen = new java.lang.ref.WeakReference<>(screen);
            ours = true;
        }
        if (!ours) {
            java.lang.ref.WeakReference<net.minecraft.client.gui.screens.Screen> known = viewerScreen;
            ours = known != null && known.get() == screen;
        }
        return ours;
    }

    private static boolean isDiagnosticScreen(net.minecraft.client.gui.screens.Screen screen) {
        if (screen == null) return false;
        if (screen.getClass().getName().startsWith("com.pelig.replaytoworld.screen.")) return true;
        java.lang.ref.WeakReference<net.minecraft.client.gui.screens.Screen> known = viewerScreen;
        return known != null && known.get() == screen;
    }

    public static boolean shouldSwallowTyping(long window) {
        net.minecraft.client.Minecraft minecraft = net.minecraft.client.Minecraft.getInstance();
        net.minecraft.client.gui.screens.Screen current = minecraft.screen;
        if (!isDiagnosticScreen(current)) return false;
        boolean ctrl = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS;
        boolean shift = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;
        return ctrl && shift;
    }

    public static void drawLabel(GuiGraphics guiGraphics, Font font, int screenHeight) {
        if (!enabled) return;
        guiGraphics.drawString(font, "Diagnostic Mode", 4, screenHeight - font.lineHeight - 4, 0xFFFFFF55, true);
    }
}

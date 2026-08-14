package com.pelig.replaytoworld;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.AlertScreen;
import net.minecraft.client.gui.screens.GenericMessageScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;

import java.io.File;
import java.util.List;

public class RrConvertAction {

    public static void openThemedReplayViewer() {
        com.replaymod.replay.gui.screen.GuiReplayViewer viewer =
                new com.replaymod.replay.gui.screen.GuiReplayViewer(com.replaymod.replay.ReplayModReplay.instance);

        viewer.upperButtonPanel.removeElement(viewer.loadButton);

        int spacing = 5;
        int renameW = viewer.renameButton.getMinSize().getWidth();
        int deleteW = viewer.deleteButton.getMinSize().getWidth();
        int cancelW = viewer.cancelButton.getMinSize().getWidth();

        com.replaymod.lib.de.johni0702.minecraft.gui.element.GuiButton convertButton =
                new com.replaymod.lib.de.johni0702.minecraft.gui.element.GuiButton();
        convertButton.setLabel("Convert to World");
        convertButton.setSize(renameW + deleteW + spacing, 20);
        convertButton.onClick(ignored -> {
            List<com.replaymod.replay.gui.screen.GuiReplayViewer.GuiReplayEntry> selected = viewer.list.getSelected();
            if (selected.size() == 1) {
                startConversion(selected.get(0).file);
            }
        });

        com.replaymod.lib.de.johni0702.minecraft.gui.element.GuiButton gameruleButton =
                new com.replaymod.lib.de.johni0702.minecraft.gui.element.GuiButton();
        gameruleButton.setLabel("Gamerule Options");
        gameruleButton.onClick(ignored -> openGameruleOptions());

        viewer.upperButtonPanel.addElements(null, convertButton, gameruleButton);

        viewer.display();

        int editorW = viewer.editorButton.getMinSize().getWidth();
        ReplayToWorldMod.LOGGER.info(
                "[ReplayToWorld] Gamerule Options sizing — editorButton width after display()={}", editorW);
        gameruleButton.setSize(editorW + cancelW + spacing, 20);
    }

    public static void startConversion(File replayFile) {
        Minecraft minecraft = Minecraft.getInstance();
        Screen previousScreen = minecraft.screen;

        var connection = minecraft.getConnection();
        var level = minecraft.level;
        var cached = ReplayToWorldMod.cachedRegistryAccess;

        ReplayToWorldMod.LOGGER.info(
                "[ReplayToWorld] Convert clicked — connection={}, level={}, cachedRegistryAccess={}",
                connection != null, level != null, cached != null);

        RegistryAccess registryAccess;
        if (connection != null) {
            registryAccess = connection.registryAccess();
        } else if (level != null) {
            registryAccess = level.registryAccess();
        } else if (cached != null) {
            registryAccess = cached;
        } else {
            registryAccess = null;
        }

        if (registryAccess != null) {
            startWithRegistryAccess(replayFile, registryAccess, minecraft, previousScreen);
            return;
        }

        ReplayToWorldMod.LOGGER.info(
                "[ReplayToWorld] No live/cached registries — loading vanilla registries in the background");

        minecraft.setScreen(new GenericMessageScreen(Component.literal("Loading registries...")));

        RrRegistryLoader.loadVanillaRegistries().thenAccept(loadedRegistryAccess -> {
            minecraft.execute(() -> {
                ReplayToWorldMod.LOGGER.info("[ReplayToWorld] Vanilla registries loaded, starting conversion");
                startWithRegistryAccess(replayFile, loadedRegistryAccess, minecraft, previousScreen);
            });
        }).exceptionally(throwable -> {
            minecraft.execute(() -> {
                ReplayToWorldMod.LOGGER.error("[ReplayToWorld] Failed to load vanilla registries", throwable);
                minecraft.setScreen(new AlertScreen(
                        () -> minecraft.setScreen(previousScreen),
                        Component.literal("Couldn't Load Registries"),
                        Component.literal(
                                "Failed to load vanilla registries: " + throwable.getMessage()
                                        + " — try joining any world once this session instead."),
                        Component.literal("Ok"),
                        true
                ));
            });
            return null;
        });
    }

    private static void startWithRegistryAccess(File replayFile, RegistryAccess registryAccess,
                                                  Minecraft minecraft, Screen previousScreen) {
        boolean started = McprWorldConverter.start(replayFile.toPath(), registryAccess, minecraft);
        ReplayToWorldMod.LOGGER.info("[ReplayToWorld] McprWorldConverter.start() returned: {}", started);
        if (started) {
            minecraft.setScreen(new com.pelig.replaytoworld.screen.RrConvertProgressScreen(previousScreen));
        }
    }

    public static void openGameruleOptions() {
        Minecraft minecraft = Minecraft.getInstance();
        Screen previousScreen = minecraft.screen;
        minecraft.setScreen(com.pelig.replaytoworld.screen.RrGameRuleScreen.create(previousScreen));
    }
}

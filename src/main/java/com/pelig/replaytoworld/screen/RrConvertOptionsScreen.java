package com.pelig.replaytoworld.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.nio.file.Path;
import java.util.List;

public class RrConvertOptionsScreen extends Screen {

    private final Screen previousScreen;
    private final Path selectedReplay;

    public RrConvertOptionsScreen(Screen previousScreen, Path selectedReplay) {
        super(Component.literal("Select an option"));
        this.previousScreen = previousScreen;
        this.selectedReplay = selectedReplay;
    }

    @Override
    protected void init() {
        int optionWidth = 280;
        int cancelWidth = 150;
        int x = this.width / 2 - optionWidth / 2;
        int y = this.height / 2 - 70;

        this.addRenderableWidget(Button.builder(Component.literal("Convert Replay to World"), btn ->
                RrConversionLauncher.start(this.minecraft, this.previousScreen, List.of(this.selectedReplay)))
                .bounds(x, y, optionWidth, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("Combine Replays into one World"), btn -> {
                    RrReplayViewers.openCombine(this.selectedReplay);
                })
                .bounds(x, y + 24, optionWidth, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("Import chunks into existing worlds"), btn ->
                this.minecraft.setScreen(new RrImportWorldScreen(this.previousScreen, this.selectedReplay)))
                .bounds(x, y + 48, optionWidth, 20).build());

        this.addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, btn ->
                this.minecraft.setScreen(this.previousScreen))
                .bounds(this.width / 2 - cancelWidth / 2, y + 72, cancelWidth, 20).build());
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(this.previousScreen);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, this.height / 2 - 96, 0xFFFFFFFF);
    }
}

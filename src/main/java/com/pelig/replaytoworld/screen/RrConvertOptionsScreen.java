package com.pelig.replaytoworld.screen;

import com.pelig.replaytoworld.McprWorldConverter;
import com.pelig.replaytoworld.config.RrSettings;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.nio.file.Path;
import java.util.List;

public class RrConvertOptionsScreen extends Screen {

    private final Screen previousScreen;
    private final Path selectedReplay;
    private EditBox nameBox;

    public RrConvertOptionsScreen(Screen previousScreen, Path selectedReplay) {
        super(Component.literal("Select an option"));
        this.previousScreen = previousScreen;
        this.selectedReplay = selectedReplay;
    }

    private int top() {
        return Math.max(26, this.height / 2 - 100);
    }

    @Override
    protected void init() {
        int optionWidth = 280;
        int cancelWidth = 150;
        int x = this.width / 2 - optionWidth / 2;
        int y = this.top();

        this.nameBox = new EditBox(this.font, x, y, optionWidth, 20, this.nameBox, Component.literal("World name"));
        this.nameBox.setMaxLength(64);
        this.addRenderableWidget(this.nameBox);

        this.addRenderableWidget(Button.builder(Component.literal("Convert Replay to World"), btn -> {
                    McprWorldConverter.requestedWorldName = this.nameBox.getValue();
                    RrConversionLauncher.start(this.minecraft, this.previousScreen, List.of(this.selectedReplay));
                })
                .bounds(x, y + 38, optionWidth, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("Combine Replays into one World"), btn -> {
                    McprWorldConverter.requestedWorldName = this.nameBox.getValue();
                    RrReplayViewers.openCombine(this.selectedReplay);
                })
                .bounds(x, y + 62, optionWidth, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("Import chunks into existing worlds"), btn -> {
                    McprWorldConverter.requestedWorldName = null;
                    this.minecraft.setScreen(new RrImportWorldScreen(this.previousScreen, this.selectedReplay));
                })
                .bounds(x, y + 86, optionWidth, 20).build());

        this.addRenderableWidget(Button.builder(lz4Label(), btn -> {
                    RrSettings.setLz4(!RrSettings.isLz4());
                    btn.setMessage(lz4Label());
                })
                .bounds(x, y + 114, optionWidth, 20).build());

        this.addRenderableWidget(Button.builder(skipLabel(), btn -> {
                    RrSettings.setSkipAdded(!RrSettings.isSkipAdded());
                    btn.setMessage(skipLabel());
                })
                .bounds(x, y + 138, optionWidth, 20).build());

        this.addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, btn ->
                this.minecraft.setScreen(this.previousScreen))
                .bounds(this.width / 2 - cancelWidth / 2, y + 174, cancelWidth, 20).build());
    }

    private static Component lz4Label() {
        return Component.literal(RrSettings.isLz4()
                ? "Fast compression (LZ4): §aON"
                : "Fast compression (LZ4): §7OFF");
    }

    private static Component skipLabel() {
        return Component.literal(RrSettings.isSkipAdded()
                ? "Import: skip recordings already in the world: §aON"
                : "Import: skip recordings already in the world: §7OFF");
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(this.previousScreen);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        int y = this.top();
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, y - 14, 0xFFFFFFFF);
        guiGraphics.drawCenteredString(this.font, "Leave blank to use the Replay name",
                this.width / 2, y + 24, 0xFF808080);
        guiGraphics.drawCenteredString(this.font, "LZ4 saves much faster but world files are bigger",
                this.width / 2, y + 162, 0xFF808080);
    }
}

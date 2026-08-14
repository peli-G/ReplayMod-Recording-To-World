package com.pelig.replaytoworld.screen;

import com.pelig.replaytoworld.McprWorldConverter;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class RrConvertProgressScreen extends Screen {

    private final Screen parent;
    private Button actionButton;

    public RrConvertProgressScreen(Screen parent) {
        super(Component.literal("Converting Replay"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        this.actionButton = this.addRenderableWidget(Button.builder(Component.literal("Cancel"), btn -> {
            if (!McprWorldConverter.progressDone) {
                McprWorldConverter.cancel();
            }
            this.minecraft.setScreen(this.parent);
        }).bounds(this.width / 2 - 100, this.height / 4 + 150, 200, 20).build());
    }

    @Override
    public void tick() {
        super.tick();
        if (this.actionButton != null) {
            this.actionButton.setMessage(Component.literal(McprWorldConverter.progressDone ? "Close" : "Cancel"));
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        String worldName = McprWorldConverter.progressWorldName.isEmpty()
                ? "Replay" : McprWorldConverter.progressWorldName;
        guiGraphics.drawCenteredString(this.font,
                "Converting Replay '" + worldName + "'", this.width / 2, 20, 0xFFFFFFFF);

        int current = McprWorldConverter.progressCurrent;
        int total = Math.max(McprWorldConverter.progressTotal, 1);
        int pct = McprWorldConverter.progressDone ? 100
                : Math.min(100, (int) ((current * 100L) / total));

        String phaseLabel = McprWorldConverter.progressFailed
                ? "Failed: " + McprWorldConverter.progressResultMessage
                : McprWorldConverter.progressDone
                    ? "Done! " + McprWorldConverter.progressResultMessage
                    : McprWorldConverter.progressPhase;
        int phaseColor = McprWorldConverter.progressFailed ? 0xFFFF5555
                : McprWorldConverter.progressDone ? 0xFF55FF55 : 0xFFA0A0A0;

        guiGraphics.drawCenteredString(this.font, phaseLabel, this.width / 2, 50, phaseColor);

        int barWidth = 200;
        int barX = this.width / 2 - barWidth / 2;
        int barY = this.height / 4 + 100;
        int barHeight = 10;

        guiGraphics.fill(barX, barY, barX + barWidth, barY + barHeight, 0xFF000000);
        int filledWidth = (int) (barWidth * (pct / 100.0));
        int barColor = McprWorldConverter.progressFailed ? 0xFFAA0000 : 0xFF00AA00;
        guiGraphics.fill(barX + 1, barY + 1, barX + 1 + Math.max(0, filledWidth - 2), barY + barHeight - 1, barColor);

        guiGraphics.drawCenteredString(this.font, pct + "%", this.width / 2, barY + barHeight + 4, 0xFFFFFFFF);
        guiGraphics.drawCenteredString(this.font, current + " / " + total,
                this.width / 2, barY + barHeight + 16, 0xFF808080);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }
}

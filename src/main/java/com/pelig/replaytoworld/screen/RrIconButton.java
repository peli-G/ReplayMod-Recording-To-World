package com.pelig.replaytoworld.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public class RrIconButton extends Button {

    private final Identifier icon;

    public RrIconButton(int x, int y, int width, int height, Component component, OnPress onPress, Identifier icon) {
        super(x, y, width, height, component, onPress, DEFAULT_NARRATION);
        this.icon = icon;
        this.setTooltip(Tooltip.create(component));
    }

    private static final int TEXTURE_SIZE = 164;

    @Override
    protected void renderContents(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.renderDefaultSprite(guiGraphics);

        int padding = 3;
        int drawSize = Math.min(this.getWidth(), this.getHeight()) - padding * 2;
        int x = this.getX() + (this.getWidth() - drawSize) / 2;
        int y = this.getY() + (this.getHeight() - drawSize) / 2;

        guiGraphics.blit(RenderPipelines.GUI_TEXTURED, this.icon, x, y, 0f, 0f,
                drawSize, drawSize, TEXTURE_SIZE, TEXTURE_SIZE, TEXTURE_SIZE, TEXTURE_SIZE);
    }
}

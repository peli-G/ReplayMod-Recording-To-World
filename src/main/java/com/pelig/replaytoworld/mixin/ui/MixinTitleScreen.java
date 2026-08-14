package com.pelig.replaytoworld.mixin.ui;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

@Mixin(value = TitleScreen.class, priority = 1400)
public abstract class MixinTitleScreen extends Screen {

    private MixinTitleScreen() {
        super(null);
    }

    @Unique
    private static Class<?> rr$injectedButtonClass;
    static {
        try {
            rr$injectedButtonClass = Class.forName("com.replaymod.replay.handler.GuiHandler$InjectedButton");
        } catch (ClassNotFoundException e) {
            rr$injectedButtonClass = null;
        }
    }

    @Unique
    private final List<AbstractWidget> rr$normalMenuWidgets = new ArrayList<>();

    @Unique
    private AbstractWidget rr$button = null;

    @Unique
    private boolean rr$checkedPosition = false;

    @Inject(method = "init", at = @At("RETURN"))
    private void rr$onInit(CallbackInfo ci) {
        this.rr$checkedPosition = false;
    }

    @Inject(method = "tick", at = @At("RETURN"))
    private void rr$onTick(CallbackInfo ci) {
        if (!this.rr$checkedPosition) {
            this.rr$checkedPosition = true;
            this.rr$createOrPositionButton(true);
        }
    }

    @Unique
    private boolean rr$isWidgetPresent(AbstractWidget widget) {
        for (GuiEventListener listener : this.children()) {
            if (listener == widget) return true;
        }
        return false;
    }

    @Unique
    private AbstractWidget rr$findReplayModButton() {
        if (rr$injectedButtonClass == null) return null;
        for (GuiEventListener listener : this.children()) {
            if (rr$injectedButtonClass.isInstance(listener) && listener != this.rr$button) {
                return (AbstractWidget) listener;
            }
        }
        return null;
    }

    @Unique
    private void rr$createOrPositionButton(boolean allowCreating) {
        if (this.rr$button != null && !rr$isWidgetPresent(this.rr$button)) {
            this.rr$button = null;
        }
        if (!allowCreating && this.rr$button == null) {
            return;
        }

        if (this.rr$button != null && !rr$wouldOverlap(
                this.rr$button.getX(), this.rr$button.getY(),
                this.rr$button.getWidth(), this.rr$button.getHeight())) {
            return;
        }

        AbstractWidget replayModButton = this.rr$findReplayModButton();
        com.pelig.replaytoworld.ReplayToWorldMod.LOGGER.info(
                "[ReplayToWorld] Title screen button placement — found ReplayMod button={}",
                replayModButton != null);

        if (replayModButton != null) {
            int size = replayModButton.getHeight();
            for (int offsetX = 0; offsetX < 5; offsetX++) {
                int x = replayModButton.getRight() + 4 + size * offsetX;
                int y = replayModButton.getY();
                if (!rr$wouldOverlap(x, y, size, size)) {
                    rr$placeButton(x, y, size);
                    return;
                }
            }
        }

        for (int offsetX = 0; offsetX < 5; offsetX++) {
            for (int i = this.rr$normalMenuWidgets.size() - 1; i >= 0; i--) {
                AbstractWidget widget = this.rr$normalMenuWidgets.get(i);
                if (!rr$isWidgetPresent(widget)) continue;

                int size = widget.getHeight();
                int x = widget.getRight() + 4 + size * offsetX;
                int y = widget.getY();

                if (!rr$wouldOverlap(x, y, size, size)) {
                    rr$placeButton(x, y, size);
                    return;
                }
            }
        }
    }

    @Unique
    private static final net.minecraft.resources.Identifier RR_ICON =
            net.minecraft.resources.Identifier.parse("replay-to-world:logo_button.png");

    @Unique
    private void rr$placeButton(int x, int y, int size) {
        if (this.rr$button == null) {
            this.rr$button = new com.pelig.replaytoworld.screen.RrIconButton(
                    x, y, size, size, Component.literal("Replay to World"), btn -> rr$onButtonPressed(), RR_ICON);
            this.addRenderableWidget(this.rr$button);
        } else {
            this.rr$button.setX(x);
            this.rr$button.setY(y);
        }
    }

    @Unique
    private void rr$onButtonPressed() {
        com.pelig.replaytoworld.ReplayToWorldMod.LOGGER.info("[ReplayToWorld] Title screen button pressed");
        com.pelig.replaytoworld.RrConvertAction.openThemedReplayViewer();
    }

    @Unique
    private boolean rr$wouldOverlap(int x, int y, int w, int h) {
        for (GuiEventListener listener : this.children()) {
            if (listener == this.rr$button) continue;
            if (listener instanceof AbstractWidget other) {
                if (x < other.getRight() && x + w > other.getX()
                        && y < other.getBottom() && y + h > other.getY()) {
                    return true;
                }
            }
        }
        return false;
    }

    @WrapOperation(method = "createNormalMenuOptions", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/screens/TitleScreen;addRenderableWidget(Lnet/minecraft/client/gui/components/events/GuiEventListener;)Lnet/minecraft/client/gui/components/events/GuiEventListener;"))
    private GuiEventListener rr$trackNormalMenuOption(TitleScreen instance, GuiEventListener listener, Operation<GuiEventListener> original) {
        GuiEventListener result = original.call(instance, listener);
        if (result instanceof AbstractWidget widget) {
            this.rr$normalMenuWidgets.add(widget);
        }
        return result;
    }

    @Inject(method = "createNormalMenuOptions", at = @At("HEAD"))
    private void rr$clearNormalMenuWidgets(int i, int j, CallbackInfoReturnable<Integer> cir) {
        this.rr$normalMenuWidgets.clear();
    }
}

package com.pelig.replaytoworld.mixin.ui;

import com.pelig.replaytoworld.RrDiagnostics;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.input.CharacterEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardHandler.class)
public class MixinKeyboardHandler {

    @Inject(method = "charTyped", at = @At("HEAD"), cancellable = true, require = 0)
    private void replaytoworld$swallowTypingWhileChordHeld(long window, CharacterEvent event, CallbackInfo ci) {
        if (RrDiagnostics.shouldSwallowTyping(window)) {
            ci.cancel();
        }
    }
}

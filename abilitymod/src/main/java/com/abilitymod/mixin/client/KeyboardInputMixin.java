package com.abilitymod.mixin.client;

import com.abilitymod.client.ClientState;
import net.minecraft.client.input.Input;
import net.minecraft.client.input.KeyboardInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Frozen: ignore WASD, jump and sneak. */
@Mixin(KeyboardInput.class)
public abstract class KeyboardInputMixin extends Input {
    @Inject(method = "tick", at = @At("TAIL"))
    private void abilitymod$freezeInput(boolean slowDown, float slowDownFactor, CallbackInfo ci) {
        if (ClientState.isSelfFrozen()) {
            this.movementForward = 0f;
            this.movementSideways = 0f;
            this.pressingForward = false;
            this.pressingBack = false;
            this.pressingLeft = false;
            this.pressingRight = false;
            this.jumping = false;
            this.sneaking = false;
        }
    }
}

package com.abilitymod.mixin.client;

import com.abilitymod.client.ClientState;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Frozen: the mouse can't turn your camera. */
@Mixin(Entity.class)
public abstract class EntityLookMixin {
    @Inject(method = "changeLookDirection", at = @At("HEAD"), cancellable = true)
    private void abilitymod$lockLook(double cursorDeltaX, double cursorDeltaY, CallbackInfo ci) {
        if ((Object) this instanceof ClientPlayerEntity && ClientState.isSelfFrozen()) ci.cancel();
    }
}

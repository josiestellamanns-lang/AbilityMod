package com.abilitymod.mixin.client;

import com.abilitymod.client.ClientState;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Vanilla skips the ground shadow for invisible entities. While a player is morphed (client side only)
 * they count as invisible, so no shadow is drawn under the block. It comes back as soon as they unmorph.
 */
@Mixin(Entity.class)
public abstract class EntityInvisibleMixin {
    @Inject(method = "isInvisible", at = @At("HEAD"), cancellable = true)
    private void abilitymod$noMorphShadow(CallbackInfoReturnable<Boolean> cir) {
        Entity self = (Entity) (Object) this;
        if (self instanceof AbstractClientPlayerEntity && ClientState.MORPHS.containsKey(self.getId())) {
            cir.setReturnValue(true);
        }
    }
}

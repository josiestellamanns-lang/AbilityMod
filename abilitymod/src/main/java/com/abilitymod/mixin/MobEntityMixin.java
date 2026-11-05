package com.abilitymod.mixin;

import com.abilitymod.AbilityActions;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A tamed mob can't even TRY to attack its owner or the owner's other tamed mobs:
 * the target is refused, so no chasing, swinging or exploding.
 */
@Mixin(MobEntity.class)
public abstract class MobEntityMixin {
    @Inject(method = "setTarget", at = @At("HEAD"), cancellable = true)
    private void abilitymod$noFriendlyTarget(LivingEntity target, CallbackInfo ci) {
        if (target == null) return;
        MobEntity self = (MobEntity) (Object) this;
        if (self.getWorld().isClient()) return;
        if (AbilityActions.isFriendly(self, target)) ci.cancel();
    }
}

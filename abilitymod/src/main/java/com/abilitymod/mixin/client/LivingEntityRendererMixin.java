package com.abilitymod.mixin.client;

import com.abilitymod.client.ClientState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Draws a translucent ice cube around any frozen entity, after the entity itself (so its pose shows through). */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin {
    @Inject(
            method = "render(Lnet/minecraft/entity/LivingEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
            at = @At("TAIL")
    )
    private void abilitymod$ice(LivingEntity entity, float yaw, float tickDelta, MatrixStack matrices,
                                VertexConsumerProvider vertexConsumers, int light, CallbackInfo ci) {
        if (!ClientState.isFrozen(entity.getId())) return;

        float w = entity.getWidth() + 0.4f;
        float h = entity.getHeight() + 0.3f;
        matrices.push();
        matrices.translate(0.0, -0.1, 0.0);
        matrices.scale(w, h, w);
        matrices.translate(-0.5, 0.0, -0.5);
        MinecraftClient.getInstance().getBlockRenderManager()
                .renderBlockAsEntity(Blocks.ICE.getDefaultState(), matrices, vertexConsumers, light, OverlayTexture.DEFAULT_UV);
        matrices.pop();
    }
}

package com.abilitymod.mixin.client;

import com.abilitymod.client.ClientState;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Draws a block instead of the player model while the player is morphed (also hides the name tag). */
@Mixin(PlayerEntityRenderer.class)
public abstract class PlayerRendererMixin {
    @Inject(
            method = "render(Lnet/minecraft/client/network/AbstractClientPlayerEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void abilitymod$renderMorph(AbstractClientPlayerEntity player, float yaw, float tickDelta,
                                        MatrixStack matrices, VertexConsumerProvider vertexConsumers,
                                        int light, CallbackInfo ci) {
        if (ClientState.HIDDEN.contains(player.getId())) {
            ci.cancel();
            return;
        }
        BlockState state = ClientState.MORPHS.get(player.getId());
        if (state == null) return;

        float s = ClientState.morphScale(player.getId());
        matrices.push();
        matrices.scale(s, s, s);
        matrices.translate(-0.5, 0.0, -0.5);
        MinecraftClient.getInstance().getBlockRenderManager()
                .renderBlockAsEntity(state, matrices, vertexConsumers, light, OverlayTexture.DEFAULT_UV);
        matrices.pop();
        ci.cancel();
    }
}

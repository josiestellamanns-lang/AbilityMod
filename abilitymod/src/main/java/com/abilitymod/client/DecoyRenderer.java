package com.abilitymod.client;

import com.abilitymod.DecoyEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.feature.ArmorFeatureRenderer;
import net.minecraft.client.render.entity.feature.HeldItemFeatureRenderer;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.util.Identifier;

/** Draws the decoy as a slim-model player: owner's skin, armor, held items and name tag. */
public class DecoyRenderer extends LivingEntityRenderer<DecoyEntity, PlayerEntityModel<DecoyEntity>> {
    private static final Identifier FALLBACK = Identifier.of("minecraft", "textures/entity/player/slim/alex.png");

    public DecoyRenderer(EntityRendererFactory.Context ctx) {
        super(ctx, new PlayerEntityModel<>(ctx.getPart(EntityModelLayers.PLAYER_SLIM), true), 0.5f);
        this.addFeature(new ArmorFeatureRenderer<>(this,
                new BipedEntityModel<>(ctx.getPart(EntityModelLayers.PLAYER_SLIM_INNER_ARMOR)),
                new BipedEntityModel<>(ctx.getPart(EntityModelLayers.PLAYER_SLIM_OUTER_ARMOR)),
                ctx.getModelManager()));
        this.addFeature(new HeldItemFeatureRenderer<>(this, ctx.getHeldItemRenderer()));
    }

    @Override
    public Identifier getTexture(DecoyEntity entity) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.getNetworkHandler() != null && entity.hasCustomName()) {
            PlayerListEntry e = mc.getNetworkHandler().getPlayerListEntry(entity.getCustomName().getString());
            if (e != null) return e.getSkinTextures().texture();
        }
        return FALLBACK;
    }
}

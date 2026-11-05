package com.abilitymod.client;

import com.abilitymod.AbilityMod;
import com.abilitymod.Payloads;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;

public class AbilityClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        Keys.register();
        EntityRendererRegistry.register(AbilityMod.DECOY, DecoyRenderer::new);

        ClientPlayNetworking.registerGlobalReceiver(Payloads.SyncAbilities.ID,
                (p, ctx) -> ClientState.setMask(p.mask(), p.granted()));
        ClientPlayNetworking.registerGlobalReceiver(Payloads.Morph.ID,
                (p, ctx) -> ClientState.setMorph(p.entityId(), p.stateId()));
        ClientPlayNetworking.registerGlobalReceiver(Payloads.Cooldown.ID,
                (p, ctx) -> ClientState.startCooldown(p.ability(), p.ticks()));
        ClientPlayNetworking.registerGlobalReceiver(Payloads.SabotageHit.ID,
                (p, ctx) -> ClientState.onSabotaged(p.type(), p.attacker()));
        ClientPlayNetworking.registerGlobalReceiver(Payloads.TamedSync.ID,
                (p, ctx) -> ClientState.setTamed(p.uuids()));
        ClientPlayNetworking.registerGlobalReceiver(Payloads.Freeze.ID,
                (p, ctx) -> ClientState.setFrozen(p.entityId(), p.ticks()));
        ClientPlayNetworking.registerGlobalReceiver(Payloads.Hidden.ID,
                (p, ctx) -> ClientState.setHidden(p.entityId(), p.hidden()));

        ClientPlayNetworking.registerGlobalReceiver(Payloads.Banner.ID,
                (p, ctx) -> ClientState.banner(p.text(), p.color()));
        ClientPlayNetworking.registerGlobalReceiver(Payloads.SettingsData.ID, (p, ctx) -> {
            ClientState.applySettings(p.data());
            if (p.open() == 1) ctx.client().setScreen(new SettingsScreen());
            else if (ctx.client().currentScreen instanceof SettingsScreen screen) screen.refresh();
        });

        ClientPlayNetworking.registerGlobalReceiver(Payloads.StructureList.ID, (p, ctx) -> {
            ClientState.setStructures(p.ids());
            ctx.client().setScreen(new LocatorScreen());
        });
        ClientPlayNetworking.registerGlobalReceiver(Payloads.LocatorInfo.ID,
                (p, ctx) -> ClientState.setLocator(p.name(), p.x(), p.z(), p.active()));
        ClientPlayNetworking.registerGlobalReceiver(Payloads.DoubleJumpState.ID,
                (p, ctx) -> ClientState.setDoubleJump(p.on(), p.jumps(), p.power()));

        ClientTickEvents.START_CLIENT_TICK.register(FreezeClient::tick);
        ClientTickEvents.START_CLIENT_TICK.register(DoubleJumpClient::tick);
        ClientTickEvents.END_CLIENT_TICK.register(Keys::tick);
        HudRenderCallback.EVENT.register(AbilityHud::render);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ClientState.reset());
    }
}

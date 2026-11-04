package com.abilitymod.client;

import com.abilitymod.Payloads;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;

public class AbilityClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        Keys.register();

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

        ClientTickEvents.END_CLIENT_TICK.register(Keys::tick);
        HudRenderCallback.EVENT.register(AbilityHud::render);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ClientState.reset());
    }
}

package com.abilitymod;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The jump itself happens on the client (movement is client-side in Minecraft).
 * The server keeps the on/off toggle, hands the settings to the client, and plays the effects.
 */
public final class DoubleJumpAbility {
    private DoubleJumpAbility() {}

    private static final Set<UUID> OFF = new HashSet<>();       // everyone defaults to ON
    private static final Map<UUID, Integer> LAST_FX = new HashMap<>();

    private static boolean has(ServerPlayerEntity p) {
        return AbilityData.get(p.getServer()).has(p.getUuid(), Ability.DOUBLE_JUMP);
    }

    public static void toggle(ServerPlayerEntity p) {
        boolean nowOn = OFF.remove(p.getUuid());
        if (!nowOn) OFF.add(p.getUuid());
        sync(p);
        ServerPlayNetworking.send(p, new Payloads.Banner(nowOn ? "Double Jump: ON" : "Double Jump: OFF", nowOn ? 0x7CFFB2 : 0xFF7A7A));
    }

    public static void sync(ServerPlayerEntity p) {
        MinecraftServer server = p.getServer();
        int jumps = (int) Math.round(AbilitySettings.get(server, Ability.DOUBLE_JUMP, "jumps"));
        int power = (int) Math.round(AbilitySettings.get(server, Ability.DOUBLE_JUMP, "power") * 100);
        ServerPlayNetworking.send(p, new Payloads.DoubleJumpState(OFF.contains(p.getUuid()) ? 0 : 1, jumps, power));
    }

    public static void syncAll(MinecraftServer server) {
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) sync(p);
    }

    /** Called when a client reports that it just did an air jump: play the puff effect for everyone nearby. */
    public static void fx(ServerPlayerEntity p) {
        if (!has(p) || OFF.contains(p.getUuid())) return;
        int now = p.getServer().getTicks();
        if (now - LAST_FX.getOrDefault(p.getUuid(), -100) < 4) return;
        LAST_FX.put(p.getUuid(), now);

        ServerWorld w = p.getServerWorld();
        Fx.ring(w, p, ParticleTypes.CLOUD, 6, 1.3);
        w.spawnParticles(ParticleTypes.CLOUD, p.getX(), p.getY() + 0.1, p.getZ(), 10, 0.25, 0.05, 0.25, 0.03);
        w.spawnParticles(ParticleTypes.POOF, p.getX(), p.getY(), p.getZ(), 4, 0.2, 0.05, 0.2, 0.02);
        w.playSound(null, p.getBlockPos(), SoundEvents.ENTITY_BAT_TAKEOFF, SoundCategory.PLAYERS, 0.7f, 1.6f);
    }
}

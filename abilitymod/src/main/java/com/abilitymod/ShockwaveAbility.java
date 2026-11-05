package com.abilitymod;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.LivingEntity;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;

import java.util.List;

/** Blasts everything within the radius away from you. Closer = harder launch. */
public final class ShockwaveAbility {
    private ShockwaveAbility() {}

    public static void use(ServerPlayerEntity player) {
        if (AbilityActions.onCooldown(player, Ability.SHOCKWAVE)) return;
        MinecraftServer server = player.getServer();
        AbilityData data = AbilityData.get(server);
        double radius = AbilitySettings.get(server, Ability.SHOCKWAVE, "radius");
        double power = AbilitySettings.get(server, Ability.SHOCKWAVE, "strength");
        ServerWorld world = player.getServerWorld();

        List<LivingEntity> targets = world.getEntitiesByClass(LivingEntity.class, player.getBoundingBox().expand(radius),
                e -> e != player && e.isAlive() && !e.isSpectator()
                        && e.squaredDistanceTo(player) <= radius * radius
                        && !player.getUuid().equals(data.ownerOf(e.getUuid())));

        // blast animation: sonic boom + smoke + a shock ring racing out to the edge of the radius
        double cy = player.getBodyY(0.5);
        world.spawnParticles(ParticleTypes.SONIC_BOOM, player.getX(), cy, player.getZ(), 1, 0, 0, 0, 0);
        world.spawnParticles(ParticleTypes.EXPLOSION, player.getX(), cy, player.getZ(), 4, 0.6, 0.3, 0.6, 0);
        world.spawnParticles(ParticleTypes.CLOUD, player.getX(), player.getY() + 0.2, player.getZ(), 40, 0.8, 0.1, 0.8, 0.15);
        Fx.ring(world, player, ParticleTypes.CLOUD, 12, radius);
        world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_WARDEN_SONIC_BOOM, SoundCategory.PLAYERS, 1.4f, 1.0f);

        for (LivingEntity e : targets) {
            double dx = e.getX() - player.getX();
            double dz = e.getZ() - player.getZ();
            double h = Math.sqrt(dx * dx + dz * dz);
            if (h < 0.01) { // standing right on top of you: pick a random direction
                double a = world.getRandom().nextDouble() * Math.PI * 2;
                dx = Math.cos(a);
                dz = Math.sin(a);
                h = 1;
            }
            double falloff = 1.0 - 0.6 * Math.sqrt(e.squaredDistanceTo(player)) / radius;
            double speed = power * falloff;
            e.setVelocity(new Vec3d(dx / h * speed, Math.min(1.2, 0.35 + 0.3 * speed), dz / h * speed));
            e.velocityModified = true;

            if (e instanceof ServerPlayerEntity sp) {
                sp.networkHandler.sendPacket(new EntityVelocityUpdateS2CPacket(sp));
                ServerPlayNetworking.send(sp, new Payloads.Banner(player.getName().getString() + "'s Shockwave!", 0xFFAA55));
            }
            if (e.getWorld() instanceof ServerWorld sw) {
                sw.spawnParticles(ParticleTypes.CLOUD, e.getX(), e.getY() + 0.2, e.getZ(), 12, 0.3, 0.1, 0.3, 0.05);
            }
        }

        AbilityActions.msg(player, "Shockwave! Launched " + targets.size() + (targets.size() == 1 ? " entity" : " entities"), Formatting.GOLD);
        AbilityActions.startCooldown(player, Ability.SHOCKWAVE);
    }
}

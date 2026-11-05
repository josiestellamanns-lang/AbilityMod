package com.abilitymod;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Formatting;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Decoy, Freeze and Grapple. */
public final class ExtraAbilities {
    private ExtraAbilities() {}

    private static final Map<UUID, Integer> HIDDEN_UNTIL = new HashMap<>();
    private static final Map<UUID, GrappleState> GRAPPLES = new HashMap<>();
    private static final Map<UUID, FrozenState> FROZEN = new HashMap<>();

    private static final class GrappleState {
        Vec3d target;
        int endTick;
        double speed; // blocks per tick
        double lastDist = Double.MAX_VALUE;
        int stuck;
    }

    private static final class FrozenState {
        Entity entity;
        double x, y, z;
        boolean prevNoGravity, prevAi;
        int endTick;
    }

    public static void tick(MinecraftServer server) {
        tickHidden(server);
        tickGrapples(server);
        tickFrozen(server);
    }

    // ================================================================== DECOY
    public static void decoy(ServerPlayerEntity player) {
        if (AbilityActions.onCooldown(player, Ability.DECOY)) return;
        MinecraftServer server = player.getServer();
        ServerWorld world = player.getServerWorld();

        double invis = AbilitySettings.get(server, Ability.DECOY, "invisible_time");
        double life = AbilitySettings.get(server, Ability.DECOY, "lifetime");
        double speed = AbilitySettings.get(server, Ability.DECOY, "speed");
        double health = AbilitySettings.get(server, Ability.DECOY, "health");

        DecoyEntity d = new DecoyEntity(AbilityMod.DECOY, world);
        d.refreshPositionAndAngles(player.getX(), player.getY(), player.getZ(), player.getYaw(), 0f);
        d.configure(player.getYaw(), (int) Math.round(life * 20), speed);
        d.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH).setBaseValue(health);
        d.setHealth((float) health);

        // copy name tag
        d.setCustomName(player.getName());
        d.setCustomNameVisible(true);

        // copy held items + armor (and make sure they never drop)
        EquipmentSlot[] slots = {EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND, EquipmentSlot.HEAD,
                EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
        for (EquipmentSlot slot : slots) {
            d.equipStack(slot, player.getEquippedStack(slot).copy());
            d.setEquipmentDropChance(slot, 0f);
        }
        world.spawnEntity(d);

        world.spawnParticles(ParticleTypes.POOF, player.getX(), player.getY() + 0.9, player.getZ(), 25, 0.4, 0.5, 0.4, 0.05);
        world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 0.7f, 1.6f);

        setHidden(player, true, (int) Math.round(invis * 20));
        AbilityActions.msg(player, "Decoy sent! You're hidden for " + (int) invis + "s", Formatting.GREEN);
        AbilityActions.startCooldown(player, Ability.DECOY);
    }

    public static void setHidden(ServerPlayerEntity p, boolean hidden, int ticks) {
        MinecraftServer server = p.getServer();
        if (hidden) {
            HIDDEN_UNTIL.put(p.getUuid(), server.getTicks() + ticks);
            // real invisibility effect too: hides the shadow and lowers mob detection
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.INVISIBILITY, ticks, 0, false, false, true));
        } else {
            HIDDEN_UNTIL.remove(p.getUuid());
            p.removeStatusEffect(StatusEffects.INVISIBILITY);
        }
        AbilityActions.broadcast(server, new Payloads.Hidden(p.getId(), hidden ? 1 : 0));
    }

    public static void syncHiddenTo(ServerPlayerEntity joiner, MinecraftServer server) {
        for (UUID id : HIDDEN_UNTIL.keySet()) {
            ServerPlayerEntity p = server.getPlayerManager().getPlayer(id);
            if (p != null) ServerPlayNetworking.send(joiner, new Payloads.Hidden(p.getId(), 1));
        }
    }

    public static void onPlayerGone(ServerPlayerEntity p) {
        if (HIDDEN_UNTIL.containsKey(p.getUuid())) setHidden(p, false, 0);
        GRAPPLES.remove(p.getUuid());
    }

    private static void tickHidden(MinecraftServer server) {
        if (HIDDEN_UNTIL.isEmpty()) return;
        for (UUID id : new ArrayList<>(HIDDEN_UNTIL.keySet())) {
            Integer until = HIDDEN_UNTIL.get(id);
            if (until == null || server.getTicks() < until) continue;
            ServerPlayerEntity p = server.getPlayerManager().getPlayer(id);
            if (p == null) {
                HIDDEN_UNTIL.remove(id);
            } else {
                setHidden(p, false, 0);
                p.getServerWorld().spawnParticles(ParticleTypes.POOF, p.getX(), p.getY() + 0.9, p.getZ(), 20, 0.3, 0.5, 0.3, 0.05);
            }
        }
    }

    // ================================================================== FREEZE
    /** Freezes every living entity within the radius (not you, and not your own tamed mobs). */
    public static void freeze(ServerPlayerEntity player) {
        if (AbilityActions.onCooldown(player, Ability.FREEZE)) return;
        MinecraftServer server = player.getServer();
        AbilityData data = AbilityData.get(server);
        double r = AbilitySettings.get(server, Ability.FREEZE, "radius");
        int ticks = (int) Math.round(AbilitySettings.get(server, Ability.FREEZE, "duration") * 20);
        ServerWorld w = player.getServerWorld();

        List<LivingEntity> targets = w.getEntitiesByClass(LivingEntity.class, player.getBoundingBox().expand(r),
                e -> e != player && e.isAlive() && !e.isSpectator() && !(e instanceof ArmorStandEntity)
                        && e.squaredDistanceTo(player) <= r * r
                        && !player.getUuid().equals(data.ownerOf(e.getUuid())));

        if (targets.isEmpty()) {
            AbilityActions.msg(player, "Nothing in range to freeze", Formatting.YELLOW);
            return;
        }

        int endTick = server.getTicks() + ticks;
        for (LivingEntity e : targets) {
            FrozenState fs = FROZEN.get(e.getUuid());
            if (fs == null) {
                fs = new FrozenState();
                fs.entity = e;
                fs.x = e.getX();
                fs.y = e.getY();
                fs.z = e.getZ();
                fs.prevNoGravity = e.hasNoGravity();
                fs.prevAi = e instanceof MobEntity m && m.isAiDisabled();
                FROZEN.put(e.getUuid(), fs);
            }
            fs.endTick = endTick;
            if (!(e instanceof ServerPlayerEntity)) {
                e.setNoGravity(true);
                if (e instanceof MobEntity m) m.setAiDisabled(true);
                e.setVelocity(Vec3d.ZERO);
            }
            AbilityActions.broadcast(server, new Payloads.Freeze(e.getId(), ticks));
        }

        // cast animation: expanding ring + snow burst
        for (int i = 0; i < 48; i++) {
            double a = i * Math.PI * 2 / 48;
            w.spawnParticles(ParticleTypes.SNOWFLAKE, player.getX() + Math.cos(a) * r, player.getY() + 0.2,
                    player.getZ() + Math.sin(a) * r, 1, 0, 0.1, 0, 0.02);
        }
        w.spawnParticles(ParticleTypes.SNOWFLAKE, player.getX(), player.getY() + 1, player.getZ(), 60, r / 2, 0.8, r / 2, 0.05);
        w.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_PLAYER_HURT_FREEZE, SoundCategory.PLAYERS, 1.0f, 0.8f);

        AbilityActions.msg(player, "Froze " + targets.size() + (targets.size() == 1 ? " entity" : " entities"), Formatting.AQUA);
        AbilityActions.startCooldown(player, Ability.FREEZE);
    }

    private static void tickFrozen(MinecraftServer server) {
        if (FROZEN.isEmpty()) return;
        Iterator<Map.Entry<UUID, FrozenState>> it = FROZEN.entrySet().iterator();
        while (it.hasNext()) {
            FrozenState fs = it.next().getValue();
            Entity e = fs.entity;
            if (e.isRemoved() || !e.isAlive() || server.getTicks() >= fs.endTick) {
                thaw(server, fs);
                it.remove();
                continue;
            }
            if (!(e instanceof ServerPlayerEntity)) {
                e.setVelocity(Vec3d.ZERO);
                if (e.squaredDistanceTo(fs.x, fs.y, fs.z) > 0.0001) e.requestTeleport(fs.x, fs.y, fs.z);
            }
        }
    }

    private static void thaw(MinecraftServer server, FrozenState fs) {
        Entity e = fs.entity;
        if (!(e instanceof ServerPlayerEntity)) {
            e.setNoGravity(fs.prevNoGravity);
            if (e instanceof MobEntity m) m.setAiDisabled(fs.prevAi);
        }
        AbilityActions.broadcast(server, new Payloads.Freeze(e.getId(), 0));
        if (!e.isRemoved() && e.getWorld() instanceof ServerWorld sw) {
            sw.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.ICE.getDefaultState()),
                    e.getX(), e.getBodyY(0.5), e.getZ(), 40, e.getWidth() / 2, e.getHeight() / 3, e.getWidth() / 2, 0.1);
            sw.playSound(null, e.getBlockPos(), SoundEvents.BLOCK_GLASS_BREAK, SoundCategory.PLAYERS, 0.8f, 1.2f);
        }
    }

    // ================================================================== GRAPPLE
    /** Aim at a block and press the key: you are PULLED toward it (not teleported). Sneak to let go early. */
    public static void grapple(ServerPlayerEntity player) {
        if (AbilityActions.onCooldown(player, Ability.GRAPPLE)) return;
        MinecraftServer server = player.getServer();
        double dist = AbilitySettings.get(server, Ability.GRAPPLE, "distance");
        double speed = AbilitySettings.get(server, Ability.GRAPPLE, "speed") / 20.0; // blocks per tick

        HitResult hit = player.raycast(dist, 0f, false);
        if (hit.getType() != HitResult.Type.BLOCK) {
            AbilityActions.msg(player, "No block within " + (int) dist + " blocks", Formatting.YELLOW);
            return;
        }

        Vec3d target = hit.getPos();
        double d = player.getPos().distanceTo(target);
        GrappleState g = new GrappleState();
        g.target = target;
        g.speed = speed;
        g.endTick = server.getTicks() + (int) (d / speed) + 30;
        GRAPPLES.put(player.getUuid(), g);

        ServerWorld w = player.getServerWorld();
        w.spawnParticles(ParticleTypes.CRIT, target.x, target.y, target.z, 15, 0.2, 0.2, 0.2, 0.2);
        w.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_FISHING_BOBBER_THROW, SoundCategory.PLAYERS, 1.0f, 0.8f);
        w.playSound(null, net.minecraft.util.math.BlockPos.ofFloored(target), SoundEvents.BLOCK_CHAIN_PLACE, SoundCategory.PLAYERS, 1.0f, 1.0f);

        AbilityActions.startCooldown(player, Ability.GRAPPLE);
    }

    private static void tickGrapples(MinecraftServer server) {
        if (GRAPPLES.isEmpty()) return;
        Iterator<Map.Entry<UUID, GrappleState>> it = GRAPPLES.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, GrappleState> en = it.next();
            GrappleState g = en.getValue();
            ServerPlayerEntity p = server.getPlayerManager().getPlayer(en.getKey());
            if (p == null || !p.isAlive() || p.isSneaking() || server.getTicks() > g.endTick) {
                if (p != null) p.fallDistance = 0;
                it.remove();
                continue;
            }

            Vec3d from = p.getPos().add(0, p.getHeight() * 0.5, 0);
            Vec3d to = g.target.subtract(from);
            double dist = to.length();

            if (dist < 2.2) { // arrived: keep a little momentum, slight hop to clear ledges
                Vec3d v = to.normalize().multiply(Math.min(g.speed, 0.6)).add(0, 0.25, 0);
                p.setVelocity(v);
                p.velocityModified = true;
                p.fallDistance = 0;
                p.networkHandler.sendPacket(new EntityVelocityUpdateS2CPacket(p));
                it.remove();
                continue;
            }

            if (dist > g.lastDist - 0.05) { // not getting closer (stuck on a wall)
                if (++g.stuck >= 8) {
                    it.remove();
                    continue;
                }
            } else {
                g.stuck = 0;
            }
            g.lastDist = dist;

            p.setVelocity(to.normalize().multiply(g.speed));
            p.velocityModified = true;
            p.fallDistance = 0; // no fall damage while grappling
            p.networkHandler.sendPacket(new EntityVelocityUpdateS2CPacket(p));

            if (server.getTicks() % 2 == 0) rope(p.getServerWorld(), from, g.target);
        }
    }

    private static void rope(ServerWorld w, Vec3d from, Vec3d to) {
        Vec3d dir = to.subtract(from);
        double len = dir.length();
        Vec3d n = dir.normalize();
        for (double t = 1.0; t < len && t < 60; t += 1.0) {
            w.spawnParticles(ParticleTypes.CRIT, from.x + n.x * t, from.y + n.y * t, from.z + n.z * t, 1, 0, 0, 0, 0);
        }
    }
}

package com.abilitymod;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Calls a flank of monsters that fight for you. They are "tamed" with the normal tamer system
 * (they follow you, defend you, never target you) and vanish when the duration runs out.
 * Only monsters that don't burn in daylight are used.
 */
public final class SummonAbility {
    private SummonAbility() {}

    private static final String TAG = "abilitymod_summon";
    private static final Map<UUID, Integer> END = new HashMap<>(); // monster -> tick it disappears

    private static final List<EntityType<? extends MobEntity>> POOL = List.of(
            EntityType.HUSK, EntityType.PILLAGER, EntityType.HUSK, EntityType.SPIDER,
            EntityType.VINDICATOR, EntityType.HUSK, EntityType.SPIDER, EntityType.PILLAGER);

    public static void register() {
        // a summoned monster that is loaded without a running timer (after a restart) is removed
        ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            if (entity.getCommandTags().contains(TAG) && !END.containsKey(entity.getUuid())) {
                world.getServer().execute(() -> {
                    AbilityData.get(world.getServer()).untame(entity.getUuid());
                    entity.discard();
                });
            }
        });
    }

    public static void use(ServerPlayerEntity player) {
        if (AbilityActions.onCooldown(player, Ability.SUMMON)) return;
        MinecraftServer server = player.getServer();
        AbilityData data = AbilityData.get(server);
        ServerWorld world = player.getServerWorld();
        int count = (int) Math.round(AbilitySettings.get(server, Ability.SUMMON, "count"));
        int ticks = (int) Math.round(AbilitySettings.get(server, Ability.SUMMON, "duration") * 20);

        Vec3d forward = Vec3d.fromPolar(0f, player.getYaw());
        Vec3d right = new Vec3d(-forward.z, 0, forward.x);
        int spawned = 0;

        for (int i = 0; i < count; i++) {
            // V-shaped flank in front of the player, alternating right / left
            int rank = i / 2 + 1;
            double side = (i % 2 == 0 ? 1 : -1) * rank * 1.5;
            double ahead = 2.0 + rank * 0.3;
            Vec3d wanted = player.getPos().add(right.multiply(side)).add(forward.multiply(ahead));
            BlockPos spot = findSpot(world, wanted, player.getBlockPos());

            EntityType<? extends MobEntity> type = POOL.get(i % POOL.size());
            MobEntity mob = type.create(world);
            if (mob == null) continue;

            mob.refreshPositionAndAngles(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, player.getYaw(), 0f);
            mob.initialize(world, world.getLocalDifficulty(spot), SpawnReason.MOB_SUMMONED, null);
            mob.setPersistent();
            mob.addCommandTag(TAG);
            mob.setCustomName(Text.literal(player.getName().getString() + "'s ").append(mob.getType().getName()));
            mob.setCustomNameVisible(true);

            data.tame(mob.getUuid(), player.getUuid());          // register BEFORE it spawns so it never targets you
            END.put(mob.getUuid(), server.getTicks() + ticks);
            world.spawnEntity(mob);
            spawned++;

            // each monster rises out of a burst of soul fire
            Fx.spiral(world, mob, ParticleTypes.SOUL_FIRE_FLAME, 24);
            world.spawnParticles(ParticleTypes.SOUL, mob.getX(), mob.getY() + 0.5, mob.getZ(), 12, 0.3, 0.5, 0.3, 0.05);
            world.spawnParticles(ParticleTypes.POOF, mob.getX(), mob.getY() + 0.3, mob.getZ(), 8, 0.3, 0.2, 0.3, 0.03);
        }

        // summoning circle around the caster
        Fx.ring(world, player, ParticleTypes.SOUL_FIRE_FLAME, 20, 3.5);
        Fx.spiral(world, player, ParticleTypes.SOUL, 30);
        world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_EVOKER_PREPARE_SUMMON, SoundCategory.PLAYERS, 1.2f, 0.9f);

        AbilityActions.sendTamed(player);
        AbilityActions.msg(player, "Summoned " + spawned + " monsters for " + ticks / 20 + "s", Formatting.DARK_AQUA);
        AbilityActions.startCooldown(player, Ability.SUMMON);
    }

    /** Finds a place to stand near a wanted position (air at feet and head, solid floor). */
    private static BlockPos findSpot(ServerWorld world, Vec3d wanted, BlockPos fallback) {
        BlockPos base = BlockPos.ofFloored(wanted);
        for (int dy = 2; dy >= -3; dy--) {
            BlockPos feet = base.up(dy);
            if (isFree(world, feet) && isFree(world, feet.up()) && !isFree(world, feet.down())) return feet;
        }
        return fallback;
    }

    private static boolean isFree(ServerWorld world, BlockPos pos) {
        return world.getBlockState(pos).getCollisionShape(world, pos).isEmpty();
    }

    public static void tick(MinecraftServer server) {
        if (END.isEmpty()) return;
        for (UUID id : new ArrayList<>(END.keySet())) {
            if (server.getTicks() >= END.get(id)) dismiss(server, id);
        }
    }

    private static void dismiss(MinecraftServer server, UUID id) {
        AbilityData data = AbilityData.get(server);
        UUID owner = data.ownerOf(id);
        MobEntity mob = AbilityActions.findMob(server, id);
        data.untame(id);
        END.remove(id);
        if (mob != null) {
            if (mob.getWorld() instanceof ServerWorld sw) {
                sw.spawnParticles(ParticleTypes.SOUL, mob.getX(), mob.getY() + 0.8, mob.getZ(), 20, 0.3, 0.6, 0.3, 0.05);
                sw.spawnParticles(ParticleTypes.POOF, mob.getX(), mob.getY() + 0.8, mob.getZ(), 12, 0.3, 0.5, 0.3, 0.03);
            }
            mob.discard();
        }
        if (owner != null) {
            ServerPlayerEntity op = server.getPlayerManager().getPlayer(owner);
            if (op != null) AbilityActions.sendTamed(op);
        }
    }
}

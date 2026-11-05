package com.abilitymod;

import com.mojang.datafixers.util.Pair;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.passive.AllayEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.entry.RegistryEntryList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import net.minecraft.world.gen.structure.Structure;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * A pet allay ("Guide") appears when you open the Locator screen. Pick any structure and it flies ahead of you,
 * leaving a sparkle trail, and leads you there. It celebrates when you arrive.
 */
public final class LocatorAbility {
    private LocatorAbility() {}

    private static final String TAG = "abilitymod_guide";
    private static final double ARRIVE_DISTANCE = 10.0;

    private static final class Guide {
        AllayEntity allay;
        BlockPos target;       // only x and z are known/used
        String name = "";
        int arrivedTick = -1;
    }

    private static final Map<UUID, Guide> GUIDES = new HashMap<>(); // player -> guide
    private static final Set<UUID> GUIDE_IDS = new HashSet<>();      // allay uuids that belong to us

    public static void register() {
        // a guide loaded after a restart has no owner any more: remove it
        ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            if (entity.getCommandTags().contains(TAG) && !GUIDE_IDS.contains(entity.getUuid())) {
                world.getServer().execute(entity::discard);
            }
        });
    }

    // ------------------------------------------------------------------ opening the screen
    public static void open(ServerPlayerEntity player) {
        if (ensureGuide(player) == null) return;

        Registry<Structure> reg = player.getServer().getRegistryManager().get(RegistryKeys.STRUCTURE);
        List<String> ids = new ArrayList<>();
        for (Identifier id : reg.getIds()) ids.add(id.toString());
        Collections.sort(ids);

        StringBuilder sb = new StringBuilder();
        for (String id : ids) {
            if (sb.length() + id.length() + 1 > 30000) break; // stay under the packet string limit
            if (sb.length() > 0) sb.append(',');
            sb.append(id);
        }
        ServerPlayNetworking.send(player, new Payloads.StructureList(sb.toString()));
    }

    private static Guide ensureGuide(ServerPlayerEntity player) {
        Guide g = GUIDES.get(player.getUuid());
        if (g != null && !g.allay.isRemoved() && g.allay.getWorld() == player.getWorld()) return g;
        if (g != null) removeAllay(g);

        ServerWorld world = player.getServerWorld();
        AllayEntity allay = EntityType.ALLAY.create(world);
        if (allay == null) return null;

        Vec3d start = player.getPos().add(1.2, 1.6, 0);
        allay.refreshPositionAndAngles(start.x, start.y, start.z, player.getYaw(), 0f);
        allay.setAiDisabled(true);
        allay.setNoGravity(true);
        allay.setInvulnerable(true);
        allay.setPersistent();
        allay.addCommandTag(TAG);
        allay.setCustomName(Text.literal("Guide").formatted(Formatting.AQUA));
        allay.setCustomNameVisible(true);

        g = new Guide();
        g.allay = allay;
        GUIDES.put(player.getUuid(), g);
        GUIDE_IDS.add(allay.getUuid());
        world.spawnEntity(allay);

        // arrival animation: sparkle spiral + burst + chime
        Fx.spiral(world, allay, ParticleTypes.END_ROD, 24);
        world.spawnParticles(ParticleTypes.HAPPY_VILLAGER, start.x, start.y, start.z, 15, 0.4, 0.4, 0.4, 0.05);
        world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_ALLAY_ITEM_GIVEN, SoundCategory.NEUTRAL, 1.0f, 1.2f);
        return g;
    }

    // ------------------------------------------------------------------ choosing a structure
    public static void locate(ServerPlayerEntity player, String idStr) {
        MinecraftServer server = player.getServer();
        if (!AbilityData.get(server).has(player.getUuid(), Ability.LOCATOR)) return;
        if (idStr.equals("stop")) {
            stop(player, true);
            return;
        }

        Identifier id = Identifier.tryParse(idStr);
        if (id == null) return;
        Registry<Structure> reg = server.getRegistryManager().get(RegistryKeys.STRUCTURE);
        Optional<RegistryEntry.Reference<Structure>> entry = reg.getEntry(id);
        if (entry.isEmpty()) {
            ServerPlayNetworking.send(player, new Payloads.Banner("Unknown structure", 0xFF5555));
            return;
        }

        String name = Pretty.structure(idStr);
        int radius = (int) Math.round(AbilitySettings.get(server, Ability.LOCATOR, "search_radius"));
        ServerPlayNetworking.send(player, new Payloads.Banner("Searching for " + name + "...", 0x7FB4FF));

        ServerWorld world = player.getServerWorld();
        Pair<BlockPos, RegistryEntry<Structure>> found = world.getChunkManager().getChunkGenerator()
                .locateStructure(world, RegistryEntryList.of(entry.get()), player.getBlockPos(), radius, false);

        if (found == null) {
            ServerPlayNetworking.send(player, new Payloads.Banner(
                    "No " + name + " within " + radius + " chunks (wrong dimension?)", 0xFF5555));
            return;
        }

        Guide g = ensureGuide(player);
        if (g == null) return;
        g.target = found.getFirst();
        g.name = name;
        g.arrivedTick = -1;

        ServerPlayNetworking.send(player, new Payloads.LocatorInfo(name, g.target.getX(), g.target.getZ(), 1));
        ServerPlayNetworking.send(player, new Payloads.Banner("Follow the guide to " + name, 0x7FFFD4));
        world.spawnParticles(ParticleTypes.FIREWORK, g.allay.getX(), g.allay.getY(), g.allay.getZ(), 25, 0.4, 0.4, 0.4, 0.1);
        world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.NEUTRAL, 1.0f, 1.4f);
    }

    public static void stop(ServerPlayerEntity player, boolean announce) {
        Guide g = GUIDES.remove(player.getUuid());
        if (g != null) removeAllay(g);
        ServerPlayNetworking.send(player, new Payloads.LocatorInfo("", 0, 0, 0));
        if (announce && g != null) ServerPlayNetworking.send(player, new Payloads.Banner("Guide dismissed", 0xA0A0B0));
    }

    public static void onPlayerGone(ServerPlayerEntity player) {
        Guide g = GUIDES.remove(player.getUuid());
        if (g != null) removeAllay(g);
    }

    private static void removeAllay(Guide g) {
        GUIDE_IDS.remove(g.allay.getUuid());
        if (!g.allay.isRemoved()) {
            if (g.allay.getWorld() instanceof ServerWorld sw) {
                sw.spawnParticles(ParticleTypes.POOF, g.allay.getX(), g.allay.getY(), g.allay.getZ(), 12, 0.3, 0.3, 0.3, 0.03);
                sw.spawnParticles(ParticleTypes.END_ROD, g.allay.getX(), g.allay.getY(), g.allay.getZ(), 12, 0.3, 0.3, 0.3, 0.05);
            }
            g.allay.discard();
        }
    }

    // ------------------------------------------------------------------ every tick
    public static void tick(MinecraftServer server) {
        if (GUIDES.isEmpty()) return;
        Iterator<Map.Entry<UUID, Guide>> it = GUIDES.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Guide> en = it.next();
            Guide g = en.getValue();
            ServerPlayerEntity p = server.getPlayerManager().getPlayer(en.getKey());

            if (p == null || !p.isAlive() || g.allay.isRemoved() || g.allay.getWorld() != p.getWorld()) {
                removeAllay(g);
                it.remove();
                if (p != null) ServerPlayNetworking.send(p, new Payloads.LocatorInfo("", 0, 0, 0));
                continue;
            }
            step(server, p, g);

            // a few seconds after arriving, the guide says goodbye
            if (g.arrivedTick >= 0 && server.getTicks() - g.arrivedTick > 80) {
                removeAllay(g);
                it.remove();
                ServerPlayNetworking.send(p, new Payloads.LocatorInfo("", 0, 0, 0));
            }
        }
    }

    private static void step(MinecraftServer server, ServerPlayerEntity p, Guide g) {
        ServerWorld world = p.getServerWorld();
        AllayEntity a = g.allay;
        Vec3d pp = p.getPos();
        int ticks = server.getTicks();
        double bob = Math.sin(ticks * 0.15) * 0.15;

        Vec3d desired;
        float yaw;
        Vec3d heading = null;

        if (g.target == null) {
            // waiting: circle the player while the screen is open
            double ang = ticks * 0.06;
            desired = pp.add(Math.cos(ang) * 1.4, 1.9 + bob, Math.sin(ang) * 1.4);
            yaw = (float) Math.toDegrees(Math.atan2(-(pp.x - a.getX()), pp.z - a.getZ()));
        } else {
            double dx = g.target.getX() + 0.5 - pp.x;
            double dz = g.target.getZ() + 0.5 - pp.z;
            double dist = Math.sqrt(dx * dx + dz * dz);
            if (g.arrivedTick < 0 && dist < ARRIVE_DISTANCE) arrive(server, p, g);

            if (g.arrivedTick >= 0) {
                desired = pp.add(0, 2.4 + bob, 0);
                yaw = p.getYaw() + 180f;
            } else {
                double nx = dx / dist, nz = dz / dist;            // dist >= ARRIVE_DISTANCE here
                double lead = Math.min(5.0, dist);
                double ax = pp.x + nx * lead, az = pp.z + nz * lead;
                desired = new Vec3d(ax, flyHeight(world, ax, az, pp.y) + 1.6 + bob, az);
                yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
                heading = new Vec3d(nx, 0, nz);
            }
        }

        Vec3d cur = a.getPos();
        Vec3d delta = desired.subtract(cur);
        Vec3d next;
        if (delta.length() > 24) {
            next = desired;
        } else {
            Vec3d move = delta.multiply(0.25);
            if (move.length() > 1.0) move = move.normalize();
            next = cur.add(move);
        }
        a.refreshPositionAndAngles(next.x, next.y, next.z, yaw, 0f);
        a.setHeadYaw(yaw);
        a.setBodyYaw(yaw);

        // sparkle trail behind the guide, and a short row of sparks pointing the way
        world.spawnParticles(ParticleTypes.END_ROD, next.x, next.y - 0.1, next.z, 1, 0.05, 0.05, 0.05, 0.0);
        if (ticks % 12 == 0) world.spawnParticles(ParticleTypes.HAPPY_VILLAGER, next.x, next.y, next.z, 2, 0.2, 0.2, 0.2, 0.0);
        if (heading != null && ticks % 10 == 0) {
            for (int i = 1; i <= 6; i++) {
                world.spawnParticles(ParticleTypes.END_ROD, next.x + heading.x * i, next.y - 0.3, next.z + heading.z * i, 1, 0, 0, 0, 0);
            }
        }
    }

    private static void arrive(MinecraftServer server, ServerPlayerEntity p, Guide g) {
        g.arrivedTick = server.getTicks();
        ServerWorld world = p.getServerWorld();
        world.spawnParticles(ParticleTypes.FIREWORK, g.allay.getX(), g.allay.getY(), g.allay.getZ(), 60, 0.6, 0.6, 0.6, 0.15);
        Fx.ring(world, p, ParticleTypes.HAPPY_VILLAGER, 16, 4.0);
        world.playSound(null, p.getBlockPos(), SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 1.0f, 1.0f);
        world.playSound(null, p.getBlockPos(), SoundEvents.ENTITY_FIREWORK_ROCKET_TWINKLE, SoundCategory.PLAYERS, 1.0f, 1.0f);
        ServerPlayNetworking.send(p, new Payloads.Banner("Arrived at " + g.name + "!", 0x7CFFB2));
    }

    /** Height the guide flies at: follows the terrain near the player, but never far above them (caves). */
    private static double flyHeight(ServerWorld world, double x, double z, double playerY) {
        int bx = MathHelper.floor(x), bz = MathHelper.floor(z);
        if (!world.getChunkManager().isChunkLoaded(bx >> 4, bz >> 4)) return playerY;
        int top = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, bx, bz);
        if (top > playerY + 6) return playerY - 0.5;
        return MathHelper.clamp(top, playerY - 1, playerY + 4);
    }
}

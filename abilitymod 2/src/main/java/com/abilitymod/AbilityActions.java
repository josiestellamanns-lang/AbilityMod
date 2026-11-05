package com.abilitymod;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** All server-side ability logic. The server is the authority; clients only send requests. */
public final class AbilityActions {
    private AbilityActions() {}

    private static final Map<UUID, BlockState> MORPHS = new HashMap<>();
    private static final Map<String, Integer> COOLDOWN_END = new HashMap<>();
    private static final Map<UUID, Integer> NEXT_ATTACK = new HashMap<>();

    // ------------------------------------------------------------------ setup
    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(Payloads.UseAbility.ID, (payload, ctx) -> {
            ServerPlayerEntity player = ctx.player();
            Ability a = Ability.byIndex(payload.ability());
            if (a == null || !has(player, a)) return;
            if (a == Ability.BLOCK_MORPH) morph(player);
            else if (a == Ability.MOB_TAMER) tame(player);
            else if (a == Ability.DECOY) ExtraAbilities.decoy(player);
            else if (a == Ability.FREEZE) ExtraAbilities.freeze(player);
            else if (a == Ability.GRAPPLE) ExtraAbilities.grapple(player);
        });

        ServerPlayNetworking.registerGlobalReceiver(Payloads.Sabotage.ID, (payload, ctx) -> {
            ServerPlayerEntity player = ctx.player();
            if (!has(player, Ability.SABOTAGE) || onCooldown(player, Ability.SABOTAGE)) return;
            SabotageType type = SabotageType.byIndex(payload.type());
            if (type == null) return;
            LivingEntity target = resolveTarget(ctx.server(), player, payload.kind(), payload.target());
            if (target == null) {
                msg(player, "That target isn't available", Formatting.RED);
                return;
            }
            applyEffect(player, target, type);
        });

        ServerPlayNetworking.registerGlobalReceiver(Payloads.ChangeSetting.ID,
                (payload, ctx) -> AbilitySettings.handleChange(ctx.player(), payload.key(), payload.value()));

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayerEntity p = handler.player;
            sync(p, 0);
            sendTamed(p);
            ExtraAbilities.syncHiddenTo(p, server);
            for (ServerPlayerEntity other : server.getPlayerManager().getPlayerList()) {
                BlockState st = MORPHS.get(other.getUuid());
                if (st != null) ServerPlayNetworking.send(p, new Payloads.Morph(other.getId(), Block.getRawIdFromState(st)));
            }
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            clearMorph(handler.player);
            ExtraAbilities.onPlayerGone(handler.player);
        });

        // Tamed mobs and their owner never hurt each other.
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            Entity atk = source.getAttacker();
            if (atk == null || atk == entity || entity.getServer() == null) return true;
            AbilityData d = AbilityData.get(entity.getServer());
            UUID a = atk instanceof ServerPlayerEntity ? atk.getUuid() : d.ownerOf(atk.getUuid());
            UUID e = entity instanceof ServerPlayerEntity ? entity.getUuid() : d.ownerOf(entity.getUuid());
            return !(a != null && a.equals(e));
        });

        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (entity instanceof ServerPlayerEntity p) {
                clearMorph(p);
                ExtraAbilities.onPlayerGone(p);
            } else if (entity.getServer() != null) {
                AbilityData d = AbilityData.get(entity.getServer());
                UUID owner = d.ownerOf(entity.getUuid());
                d.untame(entity.getUuid());
                NEXT_ATTACK.remove(entity.getUuid());
                if (owner != null) {
                    ServerPlayerEntity op = entity.getServer().getPlayerManager().getPlayer(owner);
                    if (op != null) sendTamed(op);
                }
            }
        });
    }

    // ------------------------------------------------------------------ helpers
    private static boolean has(ServerPlayerEntity p, Ability a) {
        return AbilityData.get(p.getServer()).has(p.getUuid(), a);
    }

    public static void msg(ServerPlayerEntity p, String text, Formatting color) {
        p.sendMessage(Text.literal(text).formatted(color), true);
    }

    public static void sync(ServerPlayerEntity p, int granted) {
        int mask = AbilityData.get(p.getServer()).getMask(p.getUuid());
        ServerPlayNetworking.send(p, new Payloads.SyncAbilities(mask, granted));
    }

    /** Tells the owner's client which mobs are theirs (used by the Sabotage menu's Tamed tab). */
    public static void sendTamed(ServerPlayerEntity p) {
        AbilityData d = AbilityData.get(p.getServer());
        StringBuilder sb = new StringBuilder();
        int n = 0;
        for (Map.Entry<UUID, UUID> e : d.tamedEntries()) {
            if (!e.getValue().equals(p.getUuid())) continue;
            if (n++ >= 800) break; // keeps the packet under the string size limit
            if (sb.length() > 0) sb.append(',');
            sb.append(e.getKey());
        }
        ServerPlayNetworking.send(p, new Payloads.TamedSync(sb.toString()));
    }

    private static String key(ServerPlayerEntity p, Ability a) {
        return p.getUuid() + ":" + a.id;
    }

    public static boolean onCooldown(ServerPlayerEntity p, Ability a) {
        int left = COOLDOWN_END.getOrDefault(key(p, a), 0) - p.getServer().getTicks();
        if (left > 0) {
            msg(p, a.displayName + " ready in " + (left / 20 + 1) + "s", Formatting.RED);
            return true;
        }
        return false;
    }

    public static void startCooldown(ServerPlayerEntity p, Ability a) {
        int ticks = AbilitySettings.cooldownTicks(p.getServer(), a);
        COOLDOWN_END.put(key(p, a), p.getServer().getTicks() + ticks);
        ServerPlayNetworking.send(p, new Payloads.Cooldown(a.ordinal(), ticks));
    }

    // ------------------------------------------------------------------ block morph
    public static void broadcast(MinecraftServer server, CustomPayload payload) {
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) ServerPlayNetworking.send(p, payload);
    }

    private static void setMorph(ServerPlayerEntity p, BlockState state) {
        if (state == null) MORPHS.remove(p.getUuid());
        else MORPHS.put(p.getUuid(), state);
        broadcast(p.getServer(), new Payloads.Morph(p.getId(), state == null ? -1 : Block.getRawIdFromState(state)));
    }

    public static void clearMorph(ServerPlayerEntity p) {
        if (MORPHS.containsKey(p.getUuid())) setMorph(p, null);
    }

    /** Same key both ways: if you're morphed it reverts you (look anywhere), otherwise it morphs you into the block you look at. */
    private static void morph(ServerPlayerEntity player) {
        ServerWorld world = player.getServerWorld();

        if (MORPHS.containsKey(player.getUuid())) {
            BlockState old = MORPHS.get(player.getUuid());
            setMorph(player, null);
            poof(world, player, old);
            msg(player, "You returned to normal", Formatting.GRAY);
            return;
        }

        if (onCooldown(player, Ability.BLOCK_MORPH)) return;

        HitResult hit = player.raycast(AbilitySettings.get(player.getServer(), Ability.BLOCK_MORPH, "range"), 0f, false);
        if (hit.getType() != HitResult.Type.BLOCK) {
            msg(player, "Look at a block to morph into it", Formatting.YELLOW);
            return;
        }
        BlockState state = world.getBlockState(((BlockHitResult) hit).getBlockPos());
        if (state.getRenderType() != BlockRenderType.MODEL) {
            msg(player, "You can't morph into that block", Formatting.RED);
            return;
        }
        setMorph(player, state);
        poof(world, player, state);
        msg(player, "Morphed into " + state.getBlock().getName().getString() + " (press again to change back)", Formatting.GREEN);
        startCooldown(player, Ability.BLOCK_MORPH);
    }

    private static void poof(ServerWorld world, ServerPlayerEntity p, BlockState state) {
        world.spawnParticles(ParticleTypes.POOF, p.getX(), p.getY() + 0.9, p.getZ(), 25, 0.4, 0.5, 0.4, 0.05);
        world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, state),
                p.getX(), p.getY() + 0.9, p.getZ(), 30, 0.4, 0.5, 0.4, 0.1);
        world.playSound(null, p.getBlockPos(), SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 0.6f, 1.4f);
    }

    // ------------------------------------------------------------------ mob tamer
    /** Free and unlimited. Look at any mob and press the key: hearts pop up and it's yours. */
    private static void tame(ServerPlayerEntity player) {
        if (onCooldown(player, Ability.MOB_TAMER)) return;
        AbilityData data = AbilityData.get(player.getServer());
        double range = AbilitySettings.get(player.getServer(), Ability.MOB_TAMER, "range");
        Vec3d start = player.getEyePos();
        Vec3d look = player.getRotationVec(1f);
        Vec3d end = start.add(look.multiply(range));
        Box box = player.getBoundingBox().stretch(look.multiply(range)).expand(1.0);
        EntityHitResult hit = ProjectileUtil.raycast(player, start, end, box,
                e -> !e.isSpectator() && e.canHit(), range * range);

        if (hit == null || !(hit.getEntity() instanceof MobEntity mob)) {
            msg(player, "Look at a mob to tame it", Formatting.YELLOW);
            return;
        }
        if (data.ownerOf(mob.getUuid()) != null) {
            msg(player, "That mob is already tamed", Formatting.RED);
            return;
        }

        ServerWorld world = player.getServerWorld();
        data.tame(mob.getUuid(), player.getUuid());
        mob.setPersistent();
        mob.setTarget(null);
        mob.setCustomName(Text.literal(player.getName().getString() + "'s ").append(mob.getType().getName()));
        mob.setCustomNameVisible(true);

        // "you tamed it" animation: hearts spiral up, a ring of hearts spreads out, sparkle burst, happy hop
        Fx.spiral(world, mob, ParticleTypes.HEART, 40);
        Fx.ring(world, mob, ParticleTypes.HEART, 24, 1.8);
        world.spawnParticles(ParticleTypes.TOTEM_OF_UNDYING, mob.getX(), mob.getBodyY(0.6), mob.getZ(), 30, 0.3, 0.5, 0.3, 0.4);
        mob.setVelocity(0.0, 0.35, 0.0);
        mob.velocityModified = true;
        world.playSound(null, mob.getBlockPos(), SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.NEUTRAL, 0.8f, 1.3f);

        ServerPlayNetworking.send(player, new Payloads.Banner("Tamed " + mob.getType().getName().getString() + "!", 0xFF7AA8));
        sendTamed(player);
        startCooldown(player, Ability.MOB_TAMER);
    }

    /**
     * Dog-like behaviour, runs every 2 ticks:
     * - follows the owner (teleports if far away) and shows an occasional heart
     * - attacks whatever the owner hits, and whatever hits the owner (players included)
     * - never targets the owner or the owner's other tamed mobs (see MobEntityMixin)
     * - peaceful mobs (pigs, cows, sheep...) fight like wolves
     */
    public static void tickTamed(MinecraftServer server) {
        if (server.getTicks() % 2 != 0) return;
        AbilityData data = AbilityData.get(server);
        for (Map.Entry<UUID, UUID> en : data.tamedEntries()) {
            MobEntity mob = findMob(server, en.getKey());
            if (mob == null) continue;
            ServerPlayerEntity owner = server.getPlayerManager().getPlayer(en.getValue());
            if (owner == null || mob.getWorld() != owner.getWorld()) continue;

            // a heart every few seconds so you can tell which mobs are yours
            if (Math.floorMod(server.getTicks() + en.getKey().hashCode(), 80) < 2) {
                ((ServerWorld) mob.getWorld()).spawnParticles(ParticleTypes.HEART,
                        mob.getX(), mob.getBodyY(1.1), mob.getZ(), 1, 0.2, 0.1, 0.2, 0.0);
            }

            // safety net: never keep the owner (or their pets) as a target
            LivingEntity target = mob.getTarget();
            if (target != null && friendly(data, owner, target)) mob.setTarget(null);

            // whatever the owner hit most recently, else whatever hit the owner
            LivingEntity want = owner.getAttacking();
            if (want == null || !want.isAlive() || friendly(data, owner, want)) want = owner.getAttacker();
            if (want != null && want.isAlive() && !friendly(data, owner, want) && mob.getTarget() != want) {
                mob.setTarget(want);
            }

            target = mob.getTarget();
            if (target != null && target.isAlive()) {
                if (!mob.getAttributes().hasAttribute(EntityAttributes.GENERIC_ATTACK_DAMAGE)) {
                    fightLikeWolf(server, mob, target); // mob has no attack of its own
                }
            } else {
                double d = mob.squaredDistanceTo(owner);
                if (d > 24 * 24) mob.requestTeleport(owner.getX(), owner.getY(), owner.getZ());
                else if (d > 5 * 5) mob.getNavigation().startMovingTo(owner, 1.2);
            }
        }
    }

    /** Peaceful mobs have no attack goal or damage stat, so they chase and bite the target themselves. */
    private static void fightLikeWolf(MinecraftServer server, MobEntity mob, LivingEntity target) {
        mob.getLookControl().lookAt(target, 30f, 30f);
        if (mob.getBoundingBox().expand(0.9).intersects(target.getBoundingBox())) {
            if (server.getTicks() >= NEXT_ATTACK.getOrDefault(mob.getUuid(), 0)) {
                NEXT_ATTACK.put(mob.getUuid(), server.getTicks() + 20);
                mob.swingHand(Hand.MAIN_HAND);
                float dmg = (float) AbilitySettings.get(server, Ability.MOB_TAMER, "passive_damage");
                target.damage(mob.getDamageSources().mobAttack(mob), dmg);
            }
        } else if (server.getTicks() % 10 == 0) {
            mob.getNavigation().startMovingTo(target, 1.3);
        }
    }

    /** True if "target" is this tamed mob's owner or another mob tamed by the same owner. */
    public static boolean isFriendly(MobEntity mob, LivingEntity target) {
        MinecraftServer server = mob.getServer();
        if (server == null) return false;
        AbilityData data = AbilityData.get(server);
        UUID owner = data.ownerOf(mob.getUuid());
        if (owner == null) return false;
        return target.getUuid().equals(owner) || owner.equals(data.ownerOf(target.getUuid()));
    }

    private static boolean friendly(AbilityData data, ServerPlayerEntity owner, LivingEntity other) {
        if (other == owner) return true;
        UUID o = data.ownerOf(other.getUuid());
        return o != null && o.equals(owner.getUuid());
    }

    private static MobEntity findMob(MinecraftServer server, UUID id) {
        for (ServerWorld w : server.getWorlds()) {
            Entity e = w.getEntity(id);
            if (e instanceof MobEntity m && m.isAlive()) return m;
        }
        return null;
    }

    // ------------------------------------------------------------------ sabotage / effects
    /** kind 0 = player, 1 = nearby entity, 2 = one of your own tamed mobs. */
    private static LivingEntity resolveTarget(MinecraftServer server, ServerPlayerEntity player, int kind, String idStr) {
        UUID id;
        try {
            id = UUID.fromString(idStr);
        } catch (IllegalArgumentException e) {
            return null;
        }
        if (kind == 0) return server.getPlayerManager().getPlayer(id);

        if (kind == 2) {
            if (!player.getUuid().equals(AbilityData.get(server).ownerOf(id))) return null;
            return findMob(server, id);
        }

        if (kind == 1) {
            for (ServerWorld w : server.getWorlds()) {
                Entity e = w.getEntity(id);
                if (e instanceof LivingEntity le && !(e instanceof PlayerEntity)
                        && e.getWorld() == player.getWorld() && e.squaredDistanceTo(player) < 64 * 64) {
                    return le;
                }
            }
        }
        return null;
    }

    private static void applyEffect(ServerPlayerEntity player, LivingEntity target, SabotageType type) {
        MinecraftServer srv = player.getServer();
        int dur = Math.max(20, (int) Math.round(type.durationTicks * AbilitySettings.get(srv, Ability.SABOTAGE, "duration_multiplier")));
        int amp = type.amplifier + (int) Math.round(AbilitySettings.get(srv, Ability.SABOTAGE, "strength"));
        target.addStatusEffect(new StatusEffectInstance(type.effect, dur, amp, false, true, true));

        ServerWorld world = (ServerWorld) target.getWorld();
        if (type.positive) {
            Fx.spiral(world, target, ParticleTypes.HAPPY_VILLAGER, 40);
            world.spawnParticles(ParticleTypes.END_ROD, target.getX(), target.getBodyY(0.5), target.getZ(), 20, 0.4, 0.6, 0.4, 0.03);
            world.playSound(null, target.getBlockPos(), SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.PLAYERS, 0.8f, 1.2f);
        } else {
            Fx.spiral(world, target, ParticleTypes.WITCH, 40);
            world.spawnParticles(ParticleTypes.SMOKE, target.getX(), target.getBodyY(0.5), target.getZ(), 25, 0.4, 0.6, 0.4, 0.02);
            world.playSound(null, target.getBlockPos(), SoundEvents.ENTITY_ELDER_GUARDIAN_CURSE, SoundCategory.PLAYERS, 0.8f, 1.0f);
        }

        if (target instanceof ServerPlayerEntity tp) {
            ServerPlayNetworking.send(tp, new Payloads.SabotageHit(type.ordinal(), player.getName().getString()));
        }

        int secs = dur / 20;
        String time = " (" + secs / 60 + ":" + String.format("%02d", secs % 60) + ")";
        msg(player, (type.positive ? "Gave " : "Sabotaged ") + target.getName().getString()
                + (type.positive ? " " : " with ") + type.displayName + time,
                type.positive ? Formatting.GREEN : Formatting.RED);
        startCooldown(player, Ability.SABOTAGE);
    }
}

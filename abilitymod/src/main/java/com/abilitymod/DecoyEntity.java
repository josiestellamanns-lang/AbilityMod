package com.abilitymod;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.world.World;

/**
 * A fake player. It stands perfectly still, facing the way its owner was facing.
 * Skin, name, armor and held items are copied by the ability.
 * It dies when it is hit enough, or vanishes when its lifetime runs out.
 */
public class DecoyEntity extends PathAwareEntity {
    private boolean configured = false;
    private int lifetime = 400;
    private float yawDir;
    private float pitchDir;

    public DecoyEntity(EntityType<? extends DecoyEntity> type, World world) {
        super(type, world);
        this.setPersistent();
        this.experiencePoints = 0;
    }

    public static DefaultAttributeContainer.Builder createDecoyAttributes() {
        return MobEntity.createMobAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 20.0)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.1);
    }

    public void configure(float yaw, float pitch, int lifetimeTicks) {
        this.yawDir = yaw;
        this.pitchDir = pitch;
        this.lifetime = lifetimeTicks;
        this.configured = true;
        this.setYaw(yaw);
        this.setHeadYaw(yaw);
        this.setBodyYaw(yaw);
        this.setPitch(pitch);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.getWorld().isClient() || !this.isAlive()) return;
        // not configured = loaded from a save file after a restart: just remove it
        if (!configured || this.age >= lifetime) {
            if (this.getWorld() instanceof ServerWorld sw) {
                sw.spawnParticles(ParticleTypes.POOF, getX(), getY() + 0.9, getZ(), 20, 0.3, 0.5, 0.3, 0.05);
                sw.spawnParticles(ParticleTypes.PORTAL, getX(), getY() + 1.0, getZ(), 30, 0.3, 0.7, 0.3, 0.4);
            }
            this.discard();
            return;
        }
        // keep looking exactly the way the owner did
        this.setYaw(yawDir);
        this.setHeadYaw(yawDir);
        this.setBodyYaw(yawDir);
        this.setPitch(pitchDir);
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.ENTITY_PLAYER_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.ENTITY_PLAYER_DEATH;
    }
}

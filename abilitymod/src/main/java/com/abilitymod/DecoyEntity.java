package com.abilitymod;

import net.minecraft.block.BlockState;
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
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * A fake player. It sprint-jumps in a straight line (the way its owner was facing) until it dies.
 * Dies when hit enough, or after its lifetime. Stands still if a wall is within 1 block in front
 * (top half or bottom half of its body). Skin, name, armor and held items are copied by the ability.
 */
public class DecoyEntity extends PathAwareEntity {
    private boolean configured = false;
    private int lifetime = 400;
    private double speedMult = 1.3;
    private float yawDir;

    public DecoyEntity(EntityType<? extends DecoyEntity> type, World world) {
        super(type, world);
        this.setPersistent();
        this.experiencePoints = 0;
        this.setSprinting(true);
    }

    public static DefaultAttributeContainer.Builder createDecoyAttributes() {
        return MobEntity.createMobAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 20.0)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.1);
    }

    public void configure(float yaw, int lifetimeTicks, double speedMultiplier) {
        this.yawDir = yaw;
        this.lifetime = lifetimeTicks;
        this.speedMult = speedMultiplier;
        this.configured = true;
        this.setYaw(yaw);
        this.setHeadYaw(yaw);
        this.setBodyYaw(yaw);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.getWorld().isClient() || !this.isAlive()) return;
        // not configured = loaded from a save file after a restart: just remove it
        if (!configured || this.age >= lifetime) {
            if (this.getWorld() instanceof ServerWorld sw) {
                sw.spawnParticles(ParticleTypes.POOF, getX(), getY() + 0.9, getZ(), 20, 0.3, 0.5, 0.3, 0.05);
            }
            this.discard();
        }
    }

    @Override
    protected void mobTick() {
        super.mobTick();
        if (!configured) return;
        this.setSprinting(true);
        this.setYaw(yawDir);
        this.setHeadYaw(yawDir);
        if (blockedAhead()) return; // stand still
        Vec3d dir = Vec3d.fromPolar(0f, yawDir);
        this.getMoveControl().moveTo(getX() + dir.x * 8, getY(), getZ() + dir.z * 8, speedMult);
        if (this.isOnGround()) this.getJumpControl().setActive(); // keeps it hopping like a sprint-jump
    }

    /** True if there is a solid block 0-1 blocks in front of the lower half OR the upper half of the body. */
    private boolean blockedAhead() {
        Vec3d dir = Vec3d.fromPolar(0f, yawDir);
        double reach = this.getWidth() / 2.0 + 1.0;
        double[] heights = {0.3, 1.5};
        for (double h : heights) {
            BlockPos pos = BlockPos.ofFloored(getX() + dir.x * reach, getY() + h, getZ() + dir.z * reach);
            BlockState st = this.getWorld().getBlockState(pos);
            if (!st.getCollisionShape(this.getWorld(), pos).isEmpty()) return true;
        }
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

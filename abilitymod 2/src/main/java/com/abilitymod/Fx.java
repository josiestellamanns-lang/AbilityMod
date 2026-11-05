package com.abilitymod;

import net.minecraft.entity.Entity;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.server.world.ServerWorld;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Server-side particle animations that play over several ticks. */
public final class Fx {
    private Fx() {}

    private abstract static class Anim {
        ServerWorld world;
        Entity entity;
        ParticleEffect particle;
        int t, max;

        /** Returns false when finished. */
        boolean tick() {
            if (entity.isRemoved()) return false;
            step();
            return ++t < max;
        }

        abstract void step();
    }

    private static final List<Anim> ACTIVE = new ArrayList<>();

    private static void start(Anim a, ServerWorld world, Entity entity, ParticleEffect particle, int ticks) {
        a.world = world;
        a.entity = entity;
        a.particle = particle;
        a.max = ticks;
        ACTIVE.add(a);
    }

    /** A double spiral that rises around an entity. */
    public static void spiral(ServerWorld world, Entity entity, ParticleEffect particle, int ticks) {
        start(new Anim() {
            @Override void step() {
                double a = t * 0.6;
                double h = entity.getHeight() * t / max;
                double r = entity.getWidth() * 0.9 + 0.3;
                for (int k = 0; k < 2; k++) {
                    double aa = a + k * Math.PI;
                    world.spawnParticles(particle, entity.getX() + Math.cos(aa) * r, entity.getY() + h,
                            entity.getZ() + Math.sin(aa) * r, 1, 0, 0, 0, 0);
                }
            }
        }, world, entity, particle, ticks);
    }

    /** A ring that spreads out along the ground from an entity. */
    public static void ring(ServerWorld world, Entity entity, ParticleEffect particle, int ticks, double maxRadius) {
        start(new Anim() {
            @Override void step() {
                double r = 0.3 + maxRadius * t / max;
                int n = 20;
                for (int i = 0; i < n; i++) {
                    double a = i * Math.PI * 2 / n;
                    world.spawnParticles(particle, entity.getX() + Math.cos(a) * r, entity.getY() + 0.15,
                            entity.getZ() + Math.sin(a) * r, 1, 0, 0, 0, 0);
                }
            }
        }, world, entity, particle, ticks);
    }

    public static void tick() {
        Iterator<Anim> it = ACTIVE.iterator();
        while (it.hasNext()) {
            if (!it.next().tick()) it.remove();
        }
    }
}

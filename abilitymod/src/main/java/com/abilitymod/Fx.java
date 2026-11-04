package com.abilitymod;

import net.minecraft.entity.Entity;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.server.world.ServerWorld;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Server-side particle animations (a double spiral that rises around an entity). */
public final class Fx {
    private Fx() {}

    private static final class Spiral {
        ServerWorld world; Entity entity; ParticleEffect particle; int t; int max;
    }

    private static final List<Spiral> ACTIVE = new ArrayList<>();

    public static void spiral(ServerWorld world, Entity entity, ParticleEffect particle, int ticks) {
        Spiral s = new Spiral();
        s.world = world; s.entity = entity; s.particle = particle; s.max = ticks;
        ACTIVE.add(s);
    }

    public static void tick() {
        Iterator<Spiral> it = ACTIVE.iterator();
        while (it.hasNext()) {
            Spiral s = it.next();
            if (s.entity.isRemoved()) { it.remove(); continue; }
            double a = s.t * 0.6;
            double h = s.entity.getHeight() * s.t / s.max;
            double r = s.entity.getWidth() * 0.9 + 0.3;
            for (int k = 0; k < 2; k++) {
                double aa = a + k * Math.PI;
                s.world.spawnParticles(s.particle,
                        s.entity.getX() + Math.cos(aa) * r, s.entity.getY() + h, s.entity.getZ() + Math.sin(aa) * r,
                        1, 0, 0, 0, 0);
            }
            if (++s.t >= s.max) it.remove();
        }
    }
}

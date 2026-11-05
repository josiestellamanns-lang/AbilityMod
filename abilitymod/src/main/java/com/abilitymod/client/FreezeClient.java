package com.abilitymod.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.Vec3d;

/** While the local player is frozen: no gravity, no velocity (input and mouse are blocked by mixins). */
public final class FreezeClient {
    private FreezeClient() {}

    private static boolean applied = false;
    private static boolean prevNoGravity = false;

    public static void tick(MinecraftClient mc) {
        ClientPlayerEntity p = mc.player;
        if (p == null) {
            applied = false;
            return;
        }
        if (ClientState.isSelfFrozen()) {
            if (!applied) {
                prevNoGravity = p.hasNoGravity();
                applied = true;
            }
            p.setNoGravity(true);
            p.setVelocity(Vec3d.ZERO);
            p.setSprinting(false);
        } else if (applied) {
            p.setNoGravity(prevNoGravity);
            applied = false;
        }
    }
}

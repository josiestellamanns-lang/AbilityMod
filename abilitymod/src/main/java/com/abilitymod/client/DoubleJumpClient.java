package com.abilitymod.client;

import com.abilitymod.Ability;
import com.abilitymod.Payloads;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.Vec3d;

/** Press jump again in mid-air to jump once more (movement is client-side in Minecraft). */
public final class DoubleJumpClient {
    private DoubleJumpClient() {}

    private static boolean wasDown;
    private static int airTicks;
    private static int jumpsLeft;

    public static void tick(MinecraftClient mc) {
        ClientPlayerEntity p = mc.player;
        if (p == null) {
            wasDown = false;
            airTicks = 0;
            return;
        }

        boolean down = mc.options.jumpKey.isPressed();
        boolean pressed = down && !wasDown;
        wasDown = down;

        boolean grounded = p.isOnGround() || p.isTouchingWater() || p.isInLava() || p.isClimbing()
                || p.hasVehicle() || p.isFallFlying() || p.getAbilities().flying;
        if (grounded) {
            airTicks = 0;
            jumpsLeft = ClientState.djJumps;
            return;
        }
        airTicks++;

        if (!pressed || mc.currentScreen != null) return;
        if (!ClientState.has(Ability.DOUBLE_JUMP) || !ClientState.djOn || ClientState.isSelfFrozen()) return;
        if (airTicks < 4 || jumpsLeft <= 0) return; // the first jump itself must not count

        Vec3d v = p.getVelocity();
        p.setVelocity(v.x, ClientState.djPower, v.z);
        jumpsLeft--;
        ClientPlayNetworking.send(new Payloads.DoubleJumped(jumpsLeft));
    }
}

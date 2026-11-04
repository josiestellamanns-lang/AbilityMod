package com.abilitymod.client;

import com.abilitymod.Ability;
import com.abilitymod.SabotageType;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.util.Util;
import net.minecraft.util.math.MathHelper;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Everything the client knows about abilities (synced from the server). */
public final class ClientState {
    private ClientState() {}

    public static int mask;
    public static final long[] cdEnd = new long[Ability.values().length];
    public static final long[] cdTotal = new long[Ability.values().length];
    public static final long[] appearAt = new long[Ability.values().length];
    public static final long[] flashAt = new long[Ability.values().length];
    public static final boolean[] wasCooling = new boolean[Ability.values().length];

    public static final Map<Integer, BlockState> MORPHS = new HashMap<>();
    private static final Map<Integer, Long> MORPH_START = new HashMap<>();

    /** UUIDs of mobs this player has tamed. */
    public static final Set<UUID> TAMED = new HashSet<>();

    public static String bannerText;
    public static int bannerColor;
    public static long bannerStart;
    public static final long BANNER_MS = 3000;

    public static int flashColor;
    public static long flashStart;

    public static boolean has(Ability a) {
        return (mask & a.bit()) != 0;
    }

    public static long cooldownLeftMs(Ability a) {
        return cdEnd[a.ordinal()] - Util.getMeasuringTimeMs();
    }

    public static void setMask(int newMask, int granted) {
        mask = newMask;
        if (granted != 0) {
            StringBuilder sb = new StringBuilder();
            for (Ability a : Ability.values()) {
                if ((granted & a.bit()) != 0) {
                    if (sb.length() > 0) sb.append(", ");
                    sb.append(a.displayName);
                }
            }
            banner("Ability unlocked: " + sb, 0xFFD34F);
        }
    }

    public static void startCooldown(int ability, int ticks) {
        if (ability < 0 || ability >= cdEnd.length) return;
        long now = Util.getMeasuringTimeMs();
        cdTotal[ability] = ticks * 50L;
        cdEnd[ability] = now + ticks * 50L;
    }

    public static void banner(String text, int color) {
        bannerText = text;
        bannerColor = color;
        bannerStart = Util.getMeasuringTimeMs();
    }

    public static void setTamed(String csv) {
        TAMED.clear();
        if (csv == null || csv.isEmpty()) return;
        for (String part : csv.split(",")) {
            try { TAMED.add(UUID.fromString(part.trim())); } catch (IllegalArgumentException ignored) {}
        }
    }

    public static void onSabotaged(int type, String attacker) {
        SabotageType t = SabotageType.byIndex(type);
        if (t == null) return;
        flashColor = t.color;
        flashStart = Util.getMeasuringTimeMs();
        var me = net.minecraft.client.MinecraftClient.getInstance().player;
        boolean self = me != null && me.getName().getString().equals(attacker);
        if (t.positive) {
            banner(self ? "You gave yourself " + t.displayName + "!" : attacker + " gave you " + t.displayName + "!", 0x55FF55);
        } else {
            banner(self ? "You gave yourself " + t.displayName : attacker + " sabotaged you: " + t.displayName + "!", 0xFF5555);
        }
    }

    public static void setMorph(int entityId, int stateId) {
        if (stateId < 0) {
            MORPHS.remove(entityId);
            MORPH_START.remove(entityId);
        } else {
            MORPHS.put(entityId, Block.getStateFromRawId(stateId));
            MORPH_START.put(entityId, Util.getMeasuringTimeMs());
        }
    }

    /** Pop-in animation: grows from 0 with a little overshoot. */
    public static float morphScale(int entityId) {
        Long start = MORPH_START.get(entityId);
        if (start == null) return 1f;
        float t = MathHelper.clamp((Util.getMeasuringTimeMs() - start) / 380f, 0f, 1f);
        float c1 = 1.70158f, c3 = c1 + 1f;
        float f = t - 1f;
        return 1f + c3 * f * f * f + c1 * f * f;
    }

    public static void reset() {
        mask = 0;
        MORPHS.clear();
        MORPH_START.clear();
        TAMED.clear();
        bannerText = null;
        flashStart = 0;
        for (int i = 0; i < cdEnd.length; i++) {
            cdEnd[i] = 0; cdTotal[i] = 0; appearAt[i] = 0; flashAt[i] = 0; wasCooling[i] = false;
        }
    }
}

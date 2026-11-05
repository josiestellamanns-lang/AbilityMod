package com.abilitymod.client;

import com.abilitymod.Ability;
import com.abilitymod.AbilitySettings;
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

    /** entity id -> time (ms) when the ice melts */
    public static final Map<Integer, Long> FROZEN = new HashMap<>();
    /** players that are not drawn at all (Decoy) */
    public static final Set<Integer> HIDDEN = new HashSet<>();

    /** Current ability settings from the server ("ability.setting" -> value). */
    public static final Map<String, Double> SETTINGS = new HashMap<>();

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

    public static void setFrozen(int entityId, int ticks) {
        if (ticks <= 0) {
            FROZEN.remove(entityId);
            return;
        }
        FROZEN.put(entityId, Util.getMeasuringTimeMs() + ticks * 50L);
        var me = net.minecraft.client.MinecraftClient.getInstance().player;
        if (me != null && me.getId() == entityId) banner("You're frozen!", 0x9AD8FF);
    }

    public static boolean isFrozen(int entityId) {
        Long end = FROZEN.get(entityId);
        if (end == null) return false;
        if (end < Util.getMeasuringTimeMs()) {
            FROZEN.remove(entityId);
            return false;
        }
        return true;
    }

    public static boolean isSelfFrozen() {
        var me = net.minecraft.client.MinecraftClient.getInstance().player;
        return me != null && isFrozen(me.getId());
    }

    public static long selfFrozenLeftMs() {
        var me = net.minecraft.client.MinecraftClient.getInstance().player;
        if (me == null) return 0;
        Long end = FROZEN.get(me.getId());
        return end == null ? 0 : Math.max(0, end - Util.getMeasuringTimeMs());
    }

    public static void setHidden(int entityId, int hidden) {
        if (hidden != 0) HIDDEN.add(entityId);
        else HIDDEN.remove(entityId);
    }

    public static double setting(Ability a, AbilitySettings.Def d) {
        return SETTINGS.getOrDefault(AbilitySettings.key(a, d.name()), d.def());
    }

    public static void putSetting(Ability a, AbilitySettings.Def d, double value) {
        SETTINGS.put(AbilitySettings.key(a, d.name()), value);
    }

    public static void applySettings(String data) {
        SETTINGS.clear();
        for (String part : data.split(";")) {
            int eq = part.indexOf('=');
            if (eq < 0) continue;
            try {
                SETTINGS.put(part.substring(0, eq), Double.parseDouble(part.substring(eq + 1)));
            } catch (NumberFormatException ignored) {
            }
        }
    }

    public static void reset() {
        mask = 0;
        MORPHS.clear();
        MORPH_START.clear();
        TAMED.clear();
        FROZEN.clear();
        HIDDEN.clear();
        bannerText = null;
        flashStart = 0;
        for (int i = 0; i < cdEnd.length; i++) {
            cdEnd[i] = 0; cdTotal[i] = 0; appearAt[i] = 0; flashAt[i] = 0; wasCooling[i] = false;
        }
    }
}

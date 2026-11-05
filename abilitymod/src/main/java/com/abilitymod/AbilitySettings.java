package com.abilitymod;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Every tweakable number for every ability, with its DEFAULT.
 * Ops edit them in-game with the /abilities settings screen.
 */
public final class AbilitySettings {
    private AbilitySettings() {}

    public record Def(String name, double def, double min, double max, double step, String unit) {}

    private static final Map<Ability, List<Def>> DEFS = new EnumMap<>(Ability.class);

    static {
        DEFS.put(Ability.BLOCK_MORPH, List.of(
                new Def("cooldown", 1, 0, 600, 1, "seconds"),
                new Def("range", 6, 1, 64, 1, "blocks")));
        DEFS.put(Ability.MOB_TAMER, List.of(
                new Def("cooldown", 5, 0, 600, 1, "seconds"),
                new Def("range", 16, 1, 64, 1, "blocks"),
                new Def("passive_damage", 4, 0, 100, 1, "damage")));
        DEFS.put(Ability.SABOTAGE, List.of(
                new Def("cooldown", 15, 0, 600, 1, "seconds"),
                new Def("duration_multiplier", 1.0, 0.1, 10, 0.1, "x"),
                new Def("strength", 0, 0, 4, 1, "levels")));
        DEFS.put(Ability.DECOY, List.of(
                new Def("cooldown", 30, 0, 600, 1, "seconds"),
                new Def("invisible_time", 10, 1, 120, 1, "seconds"),
                new Def("lifetime", 20, 1, 300, 1, "seconds"),
                new Def("health", 20, 1, 100, 1, "hp")));
        DEFS.put(Ability.FREEZE, List.of(
                new Def("cooldown", 30, 0, 600, 1, "seconds"),
                new Def("radius", 5, 1, 32, 1, "blocks"),
                new Def("duration", 5, 1, 60, 1, "seconds")));
        DEFS.put(Ability.GRAPPLE, List.of(
                new Def("cooldown", 5, 0, 600, 1, "seconds"),
                new Def("distance", 40, 5, 128, 5, "blocks"),
                new Def("speed", 20, 1, 40, 1, "blocks/sec")));
        DEFS.put(Ability.SHOCKWAVE, List.of(
                new Def("cooldown", 30, 0, 600, 1, "seconds"),
                new Def("radius", 20, 1, 64, 1, "blocks"),
                new Def("strength", 2.0, 0.1, 6, 0.1, "power")));
        DEFS.put(Ability.SUMMON, List.of(
                new Def("cooldown", 60, 0, 600, 1, "seconds"),
                new Def("count", 5, 1, 12, 1, "monsters"),
                new Def("duration", 60, 5, 600, 5, "seconds")));
        DEFS.put(Ability.DOUBLE_JUMP, List.of(
                new Def("jumps", 1, 1, 5, 1, "extra jumps"),
                new Def("power", 0.6, 0.2, 1.5, 0.1, "jump power")));
        DEFS.put(Ability.LOCATOR, List.of(
                new Def("search_radius", 100, 10, 250, 10, "chunks")));
    }

    public static List<Def> defs(Ability a) {
        return DEFS.getOrDefault(a, List.of());
    }

    public static Def find(Ability a, String name) {
        for (Def d : defs(a)) if (d.name().equalsIgnoreCase(name)) return d;
        return null;
    }

    public static String key(Ability a, String name) {
        return a.id + "." + name.toLowerCase();
    }

    /** "invisible_time" -> "Invisible time" */
    public static String label(Def d) {
        String s = d.name().replace('_', ' ');
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    /** 40.0 -> "40", 1.3 -> "1.3" */
    public static String fmt(double v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : String.valueOf(v);
    }

    public static double get(MinecraftServer server, Ability a, String name) {
        Def d = find(a, name);
        if (d == null) return 0;
        Double v = AbilityData.get(server).getSetting(key(a, d.name()));
        return v == null ? d.def() : v;
    }

    public static int cooldownTicks(MinecraftServer server, Ability a) {
        return (int) Math.round(get(server, a, "cooldown") * 20);
    }

    // ------------------------------------------------------------------ GUI sync
    /** Sends every current value to a player. open = true also opens the settings screen on their client. */
    public static void sendTo(ServerPlayerEntity player, boolean open) {
        MinecraftServer server = player.getServer();
        StringBuilder sb = new StringBuilder();
        for (Ability a : Ability.values()) {
            for (Def d : defs(a)) {
                if (sb.length() > 0) sb.append(';');
                sb.append(key(a, d.name())).append('=').append(get(server, a, d.name()));
            }
        }
        ServerPlayNetworking.send(player, new Payloads.SettingsData(sb.toString(), open ? 1 : 0));
    }

    /** A change requested from the settings screen. Only ops may change settings; values are clamped. */
    public static void handleChange(ServerPlayerEntity player, String key, String value) {
        if (!player.getCommandSource().hasPermissionLevel(2)) return;
        int dot = key.indexOf('.');
        if (dot < 0) return;
        Ability a = Ability.byId(key.substring(0, dot));
        if (a == null) return;
        Def d = find(a, key.substring(dot + 1));
        if (d == null) return;

        AbilityData data = AbilityData.get(player.getServer());
        if (value.equalsIgnoreCase("reset")) {
            data.clearSetting(key(a, d.name()));
        } else {
            try {
                double v = Double.parseDouble(value);
                if (Double.isNaN(v) || Double.isInfinite(v)) return;
                data.setSetting(key(a, d.name()), Math.max(d.min(), Math.min(d.max(), v)));
            } catch (NumberFormatException e) {
                return;
            }
        }
        sendTo(player, false);
        DoubleJumpAbility.syncAll(player.getServer());
    }
}

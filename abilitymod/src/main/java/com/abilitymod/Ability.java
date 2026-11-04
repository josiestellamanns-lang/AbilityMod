package com.abilitymod;

public enum Ability {
    BLOCK_MORPH("block_morph", "Block Morph", 20),
    MOB_TAMER("mob_tamer", "Mob Tamer", 100),
    SABOTAGE("sabotage", "Sabotage", 300);

    public final String id;
    public final String displayName;
    public final int cooldownTicks;

    Ability(String id, String displayName, int cooldownTicks) {
        this.id = id;
        this.displayName = displayName;
        this.cooldownTicks = cooldownTicks;
    }

    public int bit() {
        return 1 << ordinal();
    }

    public static int allBits() {
        int m = 0;
        for (Ability a : values()) m |= a.bit();
        return m;
    }

    public static Ability byId(String id) {
        for (Ability a : values()) if (a.id.equalsIgnoreCase(id)) return a;
        return null;
    }

    public static Ability byIndex(int i) {
        Ability[] v = values();
        return i >= 0 && i < v.length ? v[i] : null;
    }
}

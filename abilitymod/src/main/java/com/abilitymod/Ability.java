package com.abilitymod;

/** New abilities are always added at the END so saved player data stays valid. */
public enum Ability {
    BLOCK_MORPH("block_morph", "Block Morph"),
    MOB_TAMER("mob_tamer", "Mob Tamer"),
    SABOTAGE("sabotage", "Sabotage"),
    DECOY("decoy", "Decoy"),
    FREEZE("freeze", "Freeze"),
    GRAPPLE("grapple", "Grapple"),
    SHOCKWAVE("shockwave", "Shockwave"),
    SUMMON("summon", "Summon"),
    DOUBLE_JUMP("double_jump", "Double Jump"),
    LOCATOR("locator", "Locator");

    public final String id;
    public final String displayName;

    Ability(String id, String displayName) {
        this.id = id;
        this.displayName = displayName;
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

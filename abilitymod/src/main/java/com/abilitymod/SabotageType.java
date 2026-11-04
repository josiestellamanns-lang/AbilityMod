package com.abilitymod;

import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.registry.entry.RegistryEntry;

/**
 * All effects the Sabotage menu can apply. Edit durations here.
 * 2400 ticks = 2 minutes (20 ticks = 1 second).
 * Order matters: harmful ones first, helpful ones after.
 */
public enum SabotageType {
    // harmful
    NAUSEA("Nausea", StatusEffects.NAUSEA, 2400, 0, 0x8BD94F, false),
    SLOWNESS("Slowness", StatusEffects.SLOWNESS, 2400, 1, 0x7FA8FF, false),
    BLINDNESS("Blindness", StatusEffects.BLINDNESS, 300, 0, 0xC0C0E0, false),
    WEAKNESS("Weakness", StatusEffects.WEAKNESS, 2400, 0, 0xB0B0B0, false),
    MINING_FATIGUE("Mining Fatigue", StatusEffects.MINING_FATIGUE, 2400, 1, 0xE0B050, false),
    HUNGER("Hunger", StatusEffects.HUNGER, 2400, 1, 0xC08A4A, false),
    POISON("Poison", StatusEffects.POISON, 300, 0, 0x4E9331, false),
    DARKNESS("Darkness", StatusEffects.DARKNESS, 1200, 0, 0x6A5A8A, false),

    // helpful
    SPEED("Speed", StatusEffects.SPEED, 2400, 1, 0x7CAFC6, true),
    STRENGTH("Strength", StatusEffects.STRENGTH, 2400, 1, 0xD04040, true),
    REGENERATION("Regeneration", StatusEffects.REGENERATION, 2400, 1, 0xF080A0, true),
    RESISTANCE("Resistance", StatusEffects.RESISTANCE, 2400, 1, 0x9090C0, true),
    JUMP_BOOST("Jump Boost", StatusEffects.JUMP_BOOST, 2400, 2, 0x22FF4C, true),
    HASTE("Haste", StatusEffects.HASTE, 2400, 1, 0xD9C043, true),
    INVISIBILITY("Invisibility", StatusEffects.INVISIBILITY, 2400, 0, 0xC8D8E8, true),
    NIGHT_VISION("Night Vision", StatusEffects.NIGHT_VISION, 2400, 0, 0x6060FF, true),
    FIRE_RESISTANCE("Fire Resistance", StatusEffects.FIRE_RESISTANCE, 2400, 0, 0xE49A3A, true),
    ABSORPTION("Absorption", StatusEffects.ABSORPTION, 2400, 2, 0xFFD34F, true);

    public final String displayName;
    public final RegistryEntry<StatusEffect> effect;
    public final int durationTicks;
    public final int amplifier;
    public final int color;
    public final boolean positive;

    SabotageType(String displayName, RegistryEntry<StatusEffect> effect, int durationTicks, int amplifier, int color, boolean positive) {
        this.displayName = displayName;
        this.effect = effect;
        this.durationTicks = durationTicks;
        this.amplifier = amplifier;
        this.color = color;
        this.positive = positive;
    }

    public static SabotageType byIndex(int i) {
        SabotageType[] v = values();
        return i >= 0 && i < v.length ? v[i] : null;
    }
}

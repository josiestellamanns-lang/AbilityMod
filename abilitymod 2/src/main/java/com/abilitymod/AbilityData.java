package com.abilitymod;

import net.minecraft.datafixer.DataFixTypes;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.PersistentState;

import java.util.*;

/** Saved with the world: which player owns which abilities, and which mobs are tamed. */
public class AbilityData extends PersistentState {
    private final Map<UUID, Integer> masks = new HashMap<>();
    private final Map<UUID, UUID> tamed = new HashMap<>(); // mob -> owner
    private final Map<String, Double> settings = new HashMap<>();

    public static final PersistentState.Type<AbilityData> TYPE =
            new PersistentState.Type<>(AbilityData::new, AbilityData::fromNbt, DataFixTypes.LEVEL);

    public static AbilityData get(MinecraftServer server) {
        return server.getOverworld().getPersistentStateManager().getOrCreate(TYPE, AbilityMod.MOD_ID);
    }

    public static AbilityData fromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        AbilityData d = new AbilityData();
        NbtCompound a = nbt.getCompound("abilities");
        for (String k : a.getKeys()) d.masks.put(UUID.fromString(k), a.getInt(k));
        NbtCompound t = nbt.getCompound("tamed");
        for (String k : t.getKeys()) d.tamed.put(UUID.fromString(k), UUID.fromString(t.getString(k)));
        NbtCompound st = nbt.getCompound("settings");
        for (String k : st.getKeys()) d.settings.put(k, st.getDouble(k));
        return d;
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        NbtCompound a = new NbtCompound();
        masks.forEach((k, v) -> a.putInt(k.toString(), v));
        nbt.put("abilities", a);
        NbtCompound t = new NbtCompound();
        tamed.forEach((k, v) -> t.putString(k.toString(), v.toString()));
        nbt.put("tamed", t);
        NbtCompound st = new NbtCompound();
        settings.forEach(st::putDouble);
        nbt.put("settings", st);
        return nbt;
    }

    public int getMask(UUID player) {
        return masks.getOrDefault(player, 0);
    }

    public boolean has(UUID player, Ability a) {
        return (getMask(player) & a.bit()) != 0;
    }

    public void setMask(UUID player, int mask) {
        if (mask == 0) masks.remove(player);
        else masks.put(player, mask);
        markDirty();
    }

    public UUID ownerOf(UUID mob) {
        return tamed.get(mob);
    }

    public void tame(UUID mob, UUID owner) {
        tamed.put(mob, owner);
        markDirty();
    }

    public void untame(UUID mob) {
        if (tamed.remove(mob) != null) markDirty();
    }

    public int countTamed(UUID owner) {
        int c = 0;
        for (UUID o : tamed.values()) if (o.equals(owner)) c++;
        return c;
    }

    public List<Map.Entry<UUID, UUID>> tamedEntries() {
        return new ArrayList<>(new HashMap<>(tamed).entrySet());
    }

    public Double getSetting(String key) {
        return settings.get(key);
    }

    public void setSetting(String key, double value) {
        settings.put(key, value);
        markDirty();
    }

    public void clearSetting(String key) {
        if (settings.remove(key) != null) markDirty();
    }
}

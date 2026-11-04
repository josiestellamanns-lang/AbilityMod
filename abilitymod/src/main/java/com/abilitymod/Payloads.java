package com.abilitymod;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public final class Payloads {
    private Payloads() {}

    // ---------- client -> server ----------
    public record UseAbility(int ability) implements CustomPayload {
        public static final CustomPayload.Id<UseAbility> ID = new CustomPayload.Id<>(Identifier.of(AbilityMod.MOD_ID, "use_ability"));
        public static final PacketCodec<RegistryByteBuf, UseAbility> CODEC =
                PacketCodec.tuple(PacketCodecs.INTEGER, UseAbility::ability, UseAbility::new);
        @Override public CustomPayload.Id<? extends CustomPayload> getId() { return ID; }
    }

    /** kind: 0 = player, 1 = nearby entity, 2 = one of your tamed mobs. */
    public record Sabotage(String target, int type, int kind) implements CustomPayload {
        public static final CustomPayload.Id<Sabotage> ID = new CustomPayload.Id<>(Identifier.of(AbilityMod.MOD_ID, "sabotage"));
        public static final PacketCodec<RegistryByteBuf, Sabotage> CODEC =
                PacketCodec.tuple(PacketCodecs.STRING, Sabotage::target, PacketCodecs.INTEGER, Sabotage::type,
                        PacketCodecs.INTEGER, Sabotage::kind, Sabotage::new);
        @Override public CustomPayload.Id<? extends CustomPayload> getId() { return ID; }
    }

    // ---------- server -> client ----------
    /** mask = abilities owned, granted = bit mask of abilities just unlocked (for the animation). */
    public record SyncAbilities(int mask, int granted) implements CustomPayload {
        public static final CustomPayload.Id<SyncAbilities> ID = new CustomPayload.Id<>(Identifier.of(AbilityMod.MOD_ID, "sync_abilities"));
        public static final PacketCodec<RegistryByteBuf, SyncAbilities> CODEC =
                PacketCodec.tuple(PacketCodecs.INTEGER, SyncAbilities::mask, PacketCodecs.INTEGER, SyncAbilities::granted, SyncAbilities::new);
        @Override public CustomPayload.Id<? extends CustomPayload> getId() { return ID; }
    }

    /** stateId = -1 means "not morphed". */
    public record Morph(int entityId, int stateId) implements CustomPayload {
        public static final CustomPayload.Id<Morph> ID = new CustomPayload.Id<>(Identifier.of(AbilityMod.MOD_ID, "morph"));
        public static final PacketCodec<RegistryByteBuf, Morph> CODEC =
                PacketCodec.tuple(PacketCodecs.INTEGER, Morph::entityId, PacketCodecs.INTEGER, Morph::stateId, Morph::new);
        @Override public CustomPayload.Id<? extends CustomPayload> getId() { return ID; }
    }

    public record Cooldown(int ability, int ticks) implements CustomPayload {
        public static final CustomPayload.Id<Cooldown> ID = new CustomPayload.Id<>(Identifier.of(AbilityMod.MOD_ID, "cooldown"));
        public static final PacketCodec<RegistryByteBuf, Cooldown> CODEC =
                PacketCodec.tuple(PacketCodecs.INTEGER, Cooldown::ability, PacketCodecs.INTEGER, Cooldown::ticks, Cooldown::new);
        @Override public CustomPayload.Id<? extends CustomPayload> getId() { return ID; }
    }

    public record SabotageHit(int type, String attacker) implements CustomPayload {
        public static final CustomPayload.Id<SabotageHit> ID = new CustomPayload.Id<>(Identifier.of(AbilityMod.MOD_ID, "sabotage_hit"));
        public static final PacketCodec<RegistryByteBuf, SabotageHit> CODEC =
                PacketCodec.tuple(PacketCodecs.INTEGER, SabotageHit::type, PacketCodecs.STRING, SabotageHit::attacker, SabotageHit::new);
        @Override public CustomPayload.Id<? extends CustomPayload> getId() { return ID; }
    }

    /** Comma separated UUIDs of the mobs this player has tamed. */
    public record TamedSync(String uuids) implements CustomPayload {
        public static final CustomPayload.Id<TamedSync> ID = new CustomPayload.Id<>(Identifier.of(AbilityMod.MOD_ID, "tamed_sync"));
        public static final PacketCodec<RegistryByteBuf, TamedSync> CODEC =
                PacketCodec.tuple(PacketCodecs.STRING, TamedSync::uuids, TamedSync::new);
        @Override public CustomPayload.Id<? extends CustomPayload> getId() { return ID; }
    }

    public static void register() {
        PayloadTypeRegistry.playC2S().register(UseAbility.ID, UseAbility.CODEC);
        PayloadTypeRegistry.playC2S().register(Sabotage.ID, Sabotage.CODEC);
        PayloadTypeRegistry.playS2C().register(SyncAbilities.ID, SyncAbilities.CODEC);
        PayloadTypeRegistry.playS2C().register(Morph.ID, Morph.CODEC);
        PayloadTypeRegistry.playS2C().register(Cooldown.ID, Cooldown.CODEC);
        PayloadTypeRegistry.playS2C().register(SabotageHit.ID, SabotageHit.CODEC);
        PayloadTypeRegistry.playS2C().register(TamedSync.ID, TamedSync.CODEC);
    }
}

package com.abilitymod;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public class AbilityMod implements ModInitializer {
    public static final String MOD_ID = "abilitymod";

    /** The fake player used by the Decoy ability. */
    public static EntityType<DecoyEntity> DECOY;

    @Override
    public void onInitialize() {
        DECOY = Registry.register(Registries.ENTITY_TYPE, Identifier.of(MOD_ID, "decoy"),
                EntityType.Builder.create(DecoyEntity::new, SpawnGroup.MISC)
                        .dimensions(0.6f, 1.8f)
                        .maxTrackingRange(10)
                        .trackingTickInterval(1)
                        .build("decoy"));
        FabricDefaultAttributeRegistry.register(DECOY, DecoyEntity.createDecoyAttributes());

        Payloads.register();
        AbilityCommand.register();
        AbilityActions.register();
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            Fx.tick();
            AbilityActions.tickTamed(server);
            ExtraAbilities.tick(server);
        });
    }
}

package com.abilitymod;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

public class AbilityMod implements ModInitializer {
    public static final String MOD_ID = "abilitymod";

    @Override
    public void onInitialize() {
        Payloads.register();
        AbilityCommand.register();
        AbilityActions.register();
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            Fx.tick();
            AbilityActions.tickTamed(server);
        });
    }
}

package com.abilitymod.client;

import com.abilitymod.Ability;
import com.abilitymod.Payloads;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.lwjgl.glfw.GLFW;

import java.util.EnumMap;
import java.util.Map;

/** Keybinds show up in Options > Controls > Key Binds under "Abilities". */
public final class Keys {
    private Keys() {}

    private static final String CATEGORY = "key.categories.abilitymod";
    private static final Map<Ability, KeyBinding> KEYS = new EnumMap<>(Ability.class);

    public static void register() {
        bind(Ability.BLOCK_MORPH, "morph", GLFW.GLFW_KEY_G);
        bind(Ability.MOB_TAMER, "tame", GLFW.GLFW_KEY_H);
        bind(Ability.SABOTAGE, "sabotage", GLFW.GLFW_KEY_K);
        bind(Ability.DECOY, "decoy", GLFW.GLFW_KEY_B);
        bind(Ability.FREEZE, "freeze", GLFW.GLFW_KEY_V);
        bind(Ability.GRAPPLE, "grapple", GLFW.GLFW_KEY_C);
        bind(Ability.SHOCKWAVE, "shockwave", GLFW.GLFW_KEY_R);
        bind(Ability.SUMMON, "summon", GLFW.GLFW_KEY_Y);
        bind(Ability.DOUBLE_JUMP, "double_jump", GLFW.GLFW_KEY_Z);
        bind(Ability.LOCATOR, "locator", GLFW.GLFW_KEY_N);
    }

    private static void bind(Ability a, String name, int key) {
        KEYS.put(a, KeyBindingHelper.registerKeyBinding(
                new KeyBinding("key.abilitymod." + name, InputUtil.Type.KEYSYM, key, CATEGORY)));
    }

    public static KeyBinding forAbility(Ability a) {
        return KEYS.get(a);
    }

    public static void tick(MinecraftClient mc) {
        if (mc.player == null) return;
        for (Ability a : Ability.values()) {
            KeyBinding k = KEYS.get(a);
            while (k.wasPressed()) {
                if (mc.currentScreen != null) continue;
                if (!ClientState.has(a)) {
                    mc.inGameHud.setOverlayMessage(Text.literal("You don't have that ability").formatted(Formatting.RED), false);
                } else if (a == Ability.SABOTAGE) {
                    mc.setScreen(new SabotageScreen()); // opens locally; everything else asks the server
                } else {
                    ClientPlayNetworking.send(new Payloads.UseAbility(a.ordinal()));
                }
            }
        }
    }
}

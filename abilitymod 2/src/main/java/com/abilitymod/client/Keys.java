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

/** Keybinds show up in Options > Controls > Key Binds under "Abilities". */
public final class Keys {
    private Keys() {}

    private static final String CATEGORY = "key.categories.abilitymod";
    public static KeyBinding MORPH, TAME, SABOTAGE, DECOY, FREEZE, GRAPPLE;

    public static void register() {
        MORPH = KeyBindingHelper.registerKeyBinding(
                new KeyBinding("key.abilitymod.morph", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_G, CATEGORY));
        TAME = KeyBindingHelper.registerKeyBinding(
                new KeyBinding("key.abilitymod.tame", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_H, CATEGORY));
        SABOTAGE = KeyBindingHelper.registerKeyBinding(
                new KeyBinding("key.abilitymod.sabotage", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_K, CATEGORY));
        DECOY = KeyBindingHelper.registerKeyBinding(
                new KeyBinding("key.abilitymod.decoy", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_B, CATEGORY));
        FREEZE = KeyBindingHelper.registerKeyBinding(
                new KeyBinding("key.abilitymod.freeze", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_V, CATEGORY));
        GRAPPLE = KeyBindingHelper.registerKeyBinding(
                new KeyBinding("key.abilitymod.grapple", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_C, CATEGORY));
    }

    public static KeyBinding forAbility(Ability a) {
        return switch (a) {
            case BLOCK_MORPH -> MORPH;
            case MOB_TAMER -> TAME;
            case SABOTAGE -> SABOTAGE;
            case DECOY -> DECOY;
            case FREEZE -> FREEZE;
            case GRAPPLE -> GRAPPLE;
        };
    }

    public static void tick(MinecraftClient mc) {
        if (mc.player == null) return;
        while (MORPH.wasPressed()) use(mc, Ability.BLOCK_MORPH);
        while (TAME.wasPressed()) use(mc, Ability.MOB_TAMER);
        while (DECOY.wasPressed()) use(mc, Ability.DECOY);
        while (FREEZE.wasPressed()) use(mc, Ability.FREEZE);
        while (GRAPPLE.wasPressed()) use(mc, Ability.GRAPPLE);
        while (SABOTAGE.wasPressed()) {
            if (mc.currentScreen != null) continue;
            if (!ClientState.has(Ability.SABOTAGE)) notOwned(mc);
            else mc.setScreen(new SabotageScreen());
        }
    }

    private static void use(MinecraftClient mc, Ability a) {
        if (mc.currentScreen != null) return;
        if (!ClientState.has(a)) {
            notOwned(mc);
            return;
        }
        ClientPlayNetworking.send(new Payloads.UseAbility(a.ordinal()));
    }

    private static void notOwned(MinecraftClient mc) {
        mc.inGameHud.setOverlayMessage(Text.literal("You don't have that ability").formatted(Formatting.RED), false);
    }
}

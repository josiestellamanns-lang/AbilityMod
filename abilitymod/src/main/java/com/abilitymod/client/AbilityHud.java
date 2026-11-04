package com.abilitymod.client;

import com.abilitymod.Ability;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Util;
import net.minecraft.util.math.MathHelper;

/** Ability bar on the left of the screen, unlock/sabotage banner at the top, and the "you got sabotaged" flash. */
public final class AbilityHud {
    private AbilityHud() {}

    private static ItemStack[] icons;

    public static void render(DrawContext ctx, RenderTickCounter tickCounter) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.options.hudHidden) return;
        long now = Util.getMeasuringTimeMs();
        flash(ctx, mc, now);
        slots(ctx, mc, now);
        banner(ctx, mc, now);
    }

    private static void flash(DrawContext ctx, MinecraftClient mc, long now) {
        long t = now - ClientState.flashStart;
        if (ClientState.flashStart == 0 || t > 1800) return;
        float f = 1f - t / 1800f;
        float pulse = 0.6f + 0.4f * (float) Math.sin(t / 90.0);
        int alpha = (int) (110 * f * pulse);
        if (alpha < 6) return;
        int w = mc.getWindow().getScaledWidth(), h = mc.getWindow().getScaledHeight();
        int rgb = ClientState.flashColor & 0xFFFFFF;
        ctx.fill(0, 0, w, h, (alpha << 24) | rgb);
    }

    private static void slots(DrawContext ctx, MinecraftClient mc, long now) {
        if (icons == null) {
            icons = new ItemStack[]{
                    new ItemStack(Items.GRASS_BLOCK),
                    new ItemStack(Items.LEAD),
                    new ItemStack(Items.FERMENTED_SPIDER_EYE)
            };
        }
        TextRenderer tr = mc.textRenderer;
        int baseY = mc.getWindow().getScaledHeight() / 2 - 40;
        int n = 0;

        for (Ability a : Ability.values()) {
            int i = a.ordinal();
            if (!ClientState.has(a)) {
                ClientState.appearAt[i] = 0;
                continue;
            }
            if (ClientState.appearAt[i] == 0) ClientState.appearAt[i] = now;

            // slide-in with overshoot
            float t = MathHelper.clamp((now - ClientState.appearAt[i]) / 450f, 0f, 1f);
            float c1 = 1.70158f, c3 = c1 + 1f, f = t - 1f;
            float ease = 1f + c3 * f * f * f + c1 * f * f;
            int x = (int) (-44 + (6 + 44) * ease);
            int y = baseY + n * 28;
            n++;

            long left = ClientState.cooldownLeftMs(a);
            boolean cooling = left > 0;
            if (ClientState.wasCooling[i] && !cooling) ClientState.flashAt[i] = now;
            ClientState.wasCooling[i] = cooling;

            float pulse = 0.5f + 0.5f * (float) Math.sin(now / 260.0);
            int border;
            if (now - ClientState.flashAt[i] < 450) border = 0xFFFFFFFF;
            else if (cooling) border = 0xFF555555;
            else border = lerp(0xFF3A7BD5, 0xFF7CFFB2, pulse);

            ctx.fill(x - 1, y - 1, x + 23, y + 23, border);
            ctx.fill(x, y, x + 22, y + 22, 0xC0101018);
            ctx.drawItem(icons[i], x + 3, y + 3);

            if (cooling) {
                float frac = MathHelper.clamp(left / (float) Math.max(1, ClientState.cdTotal[i]), 0f, 1f);
                int h = (int) (22 * frac);
                ctx.fill(x, y + 22 - h, x + 22, y + 22, 0xB0000000);
                ctx.drawCenteredTextWithShadow(tr, String.valueOf(left / 1000 + 1), x + 11, y + 7, 0xFFFFFFFF);
            }

            String key = Keys.forAbility(a).getBoundKeyLocalizedText().getString();
            boolean active = a == Ability.BLOCK_MORPH && ClientState.MORPHS.containsKey(mc.player.getId());
            String label = "[" + key + "] " + a.displayName + (active ? "  (ACTIVE)" : "");
            int color = active ? 0xFF7CFFB2 : cooling ? 0xFF888888 : 0xFFFFFFFF;
            ctx.drawTextWithShadow(tr, label, x + 28, y + 7, color);
        }
    }

    private static void banner(DrawContext ctx, MinecraftClient mc, long now) {
        if (ClientState.bannerText == null) return;
        long t = now - ClientState.bannerStart;
        if (t > ClientState.BANNER_MS) {
            ClientState.bannerText = null;
            return;
        }
        float slide;
        if (t < 300) slide = ease(t / 300f);
        else if (t > ClientState.BANNER_MS - 300) slide = ease((ClientState.BANNER_MS - t) / 300f);
        else slide = 1f;

        TextRenderer tr = mc.textRenderer;
        int w = tr.getWidth(ClientState.bannerText) + 28;
        int cx = mc.getWindow().getScaledWidth() / 2;
        int y = (int) (-26 + (26 + 34) * slide);
        int rgb = ClientState.bannerColor & 0xFFFFFF;

        ctx.fill(cx - w / 2 - 1, y - 1, cx + w / 2 + 1, y + 21, 0xFF000000 | rgb);
        ctx.fill(cx - w / 2, y, cx + w / 2, y + 20, 0xE0101018);
        ctx.drawCenteredTextWithShadow(tr, ClientState.bannerText, cx, y + 6, 0xFF000000 | rgb);
    }

    private static float ease(float t) {
        float f = 1f - MathHelper.clamp(t, 0f, 1f);
        return 1f - f * f * f;
    }

    private static int lerp(int a, int b, float t) {
        int r = (int) MathHelper.lerp(t, (a >> 16) & 255, (b >> 16) & 255);
        int g = (int) MathHelper.lerp(t, (a >> 8) & 255, (b >> 8) & 255);
        int bl = (int) MathHelper.lerp(t, a & 255, b & 255);
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }
}

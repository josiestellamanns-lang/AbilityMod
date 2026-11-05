package com.abilitymod.client;

import com.abilitymod.Ability;
import com.abilitymod.AbilitySettings;
import com.abilitymod.Payloads;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Util;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Opened by /abilities settings. Pick an ability on the left, edit its settings on the right.
 * Every change is saved straight to the server (ops only).
 */
public class SettingsScreen extends Screen {
    private static final int W = 400, H = 272;
    private static final int TAB_X = 10, TAB_W = 108, TAB_Y = 46, TAB_H = 20, TAB_GAP = 2;
    private static final int ROW_Y = 74, ROW_H = 32;

    private final long openedAt = Util.getMeasuringTimeMs();
    private final Map<String, TextFieldWidget> fields = new HashMap<>();
    private Ability tab = Ability.BLOCK_MORPH;
    private String status = "";
    private long statusAt;

    public SettingsScreen() {
        super(Text.literal("Ability Settings"));
    }

    // ---------------------------------------------------------------- layout
    private int x0() { return (width - W) / 2; }
    private int y0() { return Math.max(4, (height - H) / 2); }

    private float anim() {
        float t = MathHelper.clamp((Util.getMeasuringTimeMs() - openedAt) / 260f, 0f, 1f);
        float f = 1f - t;
        return 1f - f * f * f;
    }

    private static boolean in(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    @Override
    protected void init() {
        rebuild();
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    /** The background is drawn by render() itself, so the widgets end up on top of it. */
    @Override
    public void renderBackground(DrawContext ctx, int mx, int my, float delta) {
    }

    // ---------------------------------------------------------------- widgets
    private void rebuild() {
        clearChildren();
        fields.clear();
        int x = x0(), y = y0();
        List<AbilitySettings.Def> defs = AbilitySettings.defs(tab);

        for (int i = 0; i < defs.size(); i++) {
            AbilitySettings.Def d = defs.get(i);
            int ry = y + ROW_Y + i * ROW_H;
            int cx = x + 244;

            addDrawableChild(ButtonWidget.builder(Text.literal("-"), b -> nudge(d, -1))
                    .dimensions(cx, ry, 20, 20).build());

            TextFieldWidget f = new TextFieldWidget(textRenderer, cx + 22, ry, 62, 20, Text.literal(d.name()));
            f.setMaxLength(8);
            f.setTextPredicate(s -> s.matches("[0-9.]*"));
            f.setText(AbilitySettings.fmt(ClientState.setting(tab, d)));
            addDrawableChild(f);
            fields.put(d.name(), f);

            addDrawableChild(ButtonWidget.builder(Text.literal("+"), b -> nudge(d, 1))
                    .dimensions(cx + 86, ry, 20, 20).build());
            addDrawableChild(ButtonWidget.builder(Text.literal("Reset"), b -> reset(d))
                    .dimensions(cx + 110, ry, 36, 20).build());
        }

        addDrawableChild(ButtonWidget.builder(Text.literal("Done"), b -> close())
                .dimensions(x + W - 92, y + H - 28, 80, 20).build());
    }

    /** Called when the server sends fresh values. */
    public void refresh() {
        for (AbilitySettings.Def d : AbilitySettings.defs(tab)) {
            TextFieldWidget f = fields.get(d.name());
            if (f != null && !f.isFocused()) f.setText(AbilitySettings.fmt(ClientState.setting(tab, d)));
        }
    }

    // ---------------------------------------------------------------- editing
    private double typedValue(AbilitySettings.Def d) {
        TextFieldWidget f = fields.get(d.name());
        try {
            return Double.parseDouble(f.getText());
        } catch (NumberFormatException e) {
            return ClientState.setting(tab, d);
        }
    }

    private void nudge(AbilitySettings.Def d, int dir) {
        apply(d, typedValue(d) + dir * d.step());
    }

    private void apply(AbilitySettings.Def d, double raw) {
        double v = MathHelper.clamp(Math.round(raw * 100.0) / 100.0, d.min(), d.max());
        ClientState.putSetting(tab, d, v);
        fields.get(d.name()).setText(AbilitySettings.fmt(v));
        send(d, String.valueOf(v));
        say("Saved " + AbilitySettings.label(d) + " = " + AbilitySettings.fmt(v));
    }

    private void reset(AbilitySettings.Def d) {
        ClientState.putSetting(tab, d, d.def());
        fields.get(d.name()).setText(AbilitySettings.fmt(d.def()));
        send(d, "reset");
        say(AbilitySettings.label(d) + " reset to default");
    }

    private void send(AbilitySettings.Def d, String value) {
        ClientPlayNetworking.send(new Payloads.ChangeSetting(AbilitySettings.key(tab, d.name()), value));
    }

    private void say(String text) {
        status = text;
        statusAt = Util.getMeasuringTimeMs();
    }

    private void click() {
        client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK, 1.0f));
    }

    // ---------------------------------------------------------------- render
    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        long now = Util.getMeasuringTimeMs();
        float p = anim();
        int pa = Math.max(16, (int) (p * 255));
        int x = x0(), y = y0();
        float pulse = 0.5f + 0.5f * (float) Math.sin(now / 300.0);

        ctx.fill(0, 0, width, height, ((int) (p * 150) << 24));

        ctx.getMatrices().push();
        ctx.getMatrices().translate(0, (1f - p) * 22f, 0);

        // panel + pulsing border
        ctx.fill(x - 2, y - 2, x + W + 2, y + H + 2, ((int) (p * (140 + 100 * pulse)) << 24) | 0x3A7BD5);
        ctx.fill(x, y, x + W, y + H, ((int) (p * 245) << 24) | 0x141822);

        // title
        ctx.getMatrices().push();
        ctx.getMatrices().translate(x + W / 2f, y + 10, 0);
        ctx.getMatrices().scale(1.5f, 1.5f, 1f);
        ctx.drawCenteredTextWithShadow(textRenderer, "ABILITY SETTINGS", 0, 0, (pa << 24) | 0x7FB4FF);
        ctx.getMatrices().pop();

        // ability tabs
        for (Ability a : Ability.values()) {
            int tx = x + TAB_X, ty = y + TAB_Y + a.ordinal() * (TAB_H + TAB_GAP);
            boolean sel = a == tab;
            boolean hov = in(mx, my, tx, ty, TAB_W, TAB_H);
            int bg = sel ? 0xFF2B3345 : hov ? 0xFF232A3A : 0xFF1B2130;
            ctx.fill(tx, ty, tx + TAB_W, ty + TAB_H, ((int) (p * 255) << 24) | bg & 0xFFFFFF);
            if (sel) ctx.fill(tx, ty, tx + 3, ty + TAB_H, 0xFF7FB4FF);
            ctx.drawItem(AbilityHud.icon(a), tx + 6, ty + 2);
            ctx.drawTextWithShadow(textRenderer, a.displayName, tx + 28, ty + 6, (pa << 24) | (sel ? 0xFFFFFF : 0xA8ADBD));
        }

        // selected ability header
        ctx.drawTextWithShadow(textRenderer, tab.displayName, x + 132, y + 46, (pa << 24) | 0x7FB4FF);
        ctx.drawText(textRenderer, "Applies to everyone on the server", x + 132, y + 58, 0xFF8A8FA0, false);

        // setting labels (the buttons and text boxes are drawn by super.render below)
        List<AbilitySettings.Def> defs = AbilitySettings.defs(tab);
        for (int i = 0; i < defs.size(); i++) {
            AbilitySettings.Def d = defs.get(i);
            int ry = y + ROW_Y + i * ROW_H;
            ctx.drawTextWithShadow(textRenderer, AbilitySettings.label(d), x + 132, ry + 1, (pa << 24) | 0xFFFFFF);
            ctx.drawText(textRenderer, "default " + AbilitySettings.fmt(d.def()) + " " + d.unit(), x + 132, ry + 11, 0xFF8A8FA0, false);
        }

        // footer
        boolean showStatus = !status.isEmpty() && now - statusAt < 2500;
        ctx.drawText(textRenderer, showStatus ? status : "Type a number + Enter, or use - / +", x + 12, y + H - 22,
                showStatus ? 0xFF7CFFB2 : 0xFF8A8FA0, false);

        super.render(ctx, mx, my, delta);
        ctx.getMatrices().pop();
    }

    // ---------------------------------------------------------------- input
    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0) {
            for (Ability a : Ability.values()) {
                int tx = x0() + TAB_X, ty = y0() + TAB_Y + a.ordinal() * (TAB_H + TAB_GAP);
                if (in(mx, my, tx, ty, TAB_W, TAB_H)) {
                    if (a != tab) {
                        tab = a;
                        rebuild();
                    }
                    click();
                    return true;
                }
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            for (AbilitySettings.Def d : AbilitySettings.defs(tab)) {
                TextFieldWidget f = fields.get(d.name());
                if (f != null && f.isFocused()) {
                    apply(d, typedValue(d));
                    return true;
                }
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}

package com.abilitymod.client;

import com.abilitymod.Payloads;
import com.abilitymod.Pretty;
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

import java.util.ArrayList;
import java.util.List;

/** Pick any structure in the game. Your allay guide then leads you there. */
public class LocatorScreen extends Screen {
    private static final int W = 300, H = 254, ROW = 18, VISIBLE = 8;

    private final long openedAt = Util.getMeasuringTimeMs();
    private final List<String> filtered = new ArrayList<>();
    private TextFieldWidget search;
    private int scroll;

    public LocatorScreen() {
        super(Text.literal("Locator"));
    }

    private int x0() { return (width - W) / 2; }
    private int y0() { return Math.max(4, (height - H) / 2); }
    private int listY() { return y0() + 72; }

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
        int x = x0(), y = y0();
        search = new TextFieldWidget(textRenderer, x + 12, y + 46, W - 24, 18, Text.literal("Search"));
        search.setMaxLength(32);
        search.setChangedListener(s -> {
            scroll = 0;
            refilter();
        });
        addDrawableChild(search);
        setInitialFocus(search);

        addDrawableChild(ButtonWidget.builder(Text.literal("Stop guide"), b -> {
            send("stop");
            close();
        }).dimensions(x + 12, y + H - 28, 90, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Close"), b -> close())
                .dimensions(x + W - 102, y + H - 28, 90, 20).build());
        refilter();
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void renderBackground(DrawContext ctx, int mx, int my, float delta) {
    }

    private void refilter() {
        String q = search == null ? "" : search.getText().trim().toLowerCase();
        filtered.clear();
        for (String id : ClientState.STRUCTURES) {
            if (q.isEmpty() || id.toLowerCase().contains(q) || Pretty.structure(id).toLowerCase().contains(q)) filtered.add(id);
        }
        scroll = MathHelper.clamp(scroll, 0, Math.max(0, filtered.size() - VISIBLE));
    }

    private void send(String id) {
        ClientPlayNetworking.send(new Payloads.LocateRequest(id));
    }

    private void choose(String id) {
        client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK, 1.0f));
        send(id);
        close();
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

        ctx.fill(x - 2, y - 2, x + W + 2, y + H + 2, ((int) (p * (140 + 100 * pulse)) << 24) | 0x3AD0C0);
        ctx.fill(x, y, x + W, y + H, ((int) (p * 245) << 24) | 0x101C22);

        ctx.getMatrices().push();
        ctx.getMatrices().translate(x + W / 2f, y + 9, 0);
        ctx.getMatrices().scale(1.6f, 1.6f, 1f);
        ctx.drawCenteredTextWithShadow(textRenderer, "LOCATOR", 0, 0, (pa << 24) | 0x7FFFD4);
        ctx.getMatrices().pop();
        ctx.drawCenteredTextWithShadow(textRenderer, "Pick a structure. Your allay will lead the way.",
                x + W / 2, y + 30, (pa << 24) | 0xA0B8C0);

        if (search.getText().isEmpty() && !search.isFocused()) {
            ctx.drawText(textRenderer, "Search...", x + 18, y + 51, 0xFF6A7A80, false);
        }

        // structure list
        for (int i = 0; i < VISIBLE; i++) {
            int idx = scroll + i;
            if (idx >= filtered.size()) break;
            String id = filtered.get(idx);
            float rp = MathHelper.clamp((now - openedAt - 35L * i) / 200f, 0f, 1f);
            int rx = x + 12 + (int) ((1f - rp) * 12), ry = listY() + i * (ROW + 2);
            int ra = Math.max(16, (int) (rp * 255));
            boolean hov = in(mx, my, x + 12, ry, W - 24, ROW);
            int bg = hov ? 0xFF24424A : 0xFF18282E;
            ctx.fill(rx, ry, rx + W - 24, ry + ROW, ((int) (rp * 255) << 24) | bg & 0xFFFFFF);
            if (hov) ctx.fill(rx, ry, rx + 3, ry + ROW, 0xFF7FFFD4);
            ctx.drawTextWithShadow(textRenderer, textRenderer.trimToWidth(Pretty.structure(id), W - 40),
                    rx + 8, ry + 5, (ra << 24) | 0xFFFFFF);
        }
        if (filtered.isEmpty()) {
            ctx.drawCenteredTextWithShadow(textRenderer, "No structures match", x + W / 2, listY() + 30, 0xFF8A9AA0);
        }
        if (scroll > 0) ctx.drawCenteredTextWithShadow(textRenderer, "^", x + W / 2, listY() - 9, 0xFFAAAAAA);
        if (scroll + VISIBLE < filtered.size()) {
            ctx.drawCenteredTextWithShadow(textRenderer, "v", x + W / 2, listY() + VISIBLE * (ROW + 2), 0xFFAAAAAA);
        }

        super.render(ctx, mx, my, delta);
        ctx.getMatrices().pop();
    }

    // ---------------------------------------------------------------- input
    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0) {
            for (int i = 0; i < VISIBLE; i++) {
                int idx = scroll + i;
                if (idx >= filtered.size()) break;
                if (in(mx, my, x0() + 12, listY() + i * (ROW + 2), W - 24, ROW)) {
                    choose(filtered.get(idx));
                    return true;
                }
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double horizontal, double vertical) {
        scroll = MathHelper.clamp(scroll - (int) Math.signum(vertical), 0, Math.max(0, filtered.size() - VISIBLE));
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if ((keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) && !filtered.isEmpty()) {
            choose(filtered.get(0)); // Enter picks the first match
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}

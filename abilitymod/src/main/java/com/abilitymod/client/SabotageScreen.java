package com.abilitymod.client;

import com.abilitymod.Ability;
import com.abilitymod.Payloads;
import com.abilitymod.SabotageType;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Util;
import net.minecraft.util.math.MathHelper;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Tabs: Players / Entities / Tamed. Pick a target, pick an effect (Harmful or Helpful), press GO.
 */
public class SabotageScreen extends Screen {
    private static final int W = 366, H = 240, ROW = 20, VISIBLE = 7;
    private static final String[] TABS = {"Players", "Entities", "Tamed"};
    private static final int TAB_W = 55;

    private record Target(UUID id, String label, int kind) {}

    private final long openedAt = Util.getMeasuringTimeMs();
    private final List<Target> targets = new ArrayList<>();
    private int tab;
    private float underline = -1f;
    private boolean helpful;
    private Target selTarget;
    private SabotageType selEffect;
    private int tScroll, eScroll, tickCount;

    public SabotageScreen() {
        super(Text.literal("Sabotage"));
    }

    @Override
    protected void init() {
        rebuild();
    }

    @Override
    public void tick() {
        if (++tickCount % 10 == 0) rebuild();
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    // ---------------------------------------------------------------- data
    private void rebuild() {
        targets.clear();
        if (client == null || client.world == null || client.player == null) return;
        var me = client.player;

        if (tab == 0) {
            if (client.getNetworkHandler() != null) {
                for (PlayerListEntry e : client.getNetworkHandler().getPlayerList()) {
                    UUID id = e.getProfile().getId();
                    String name = e.getProfile().getName() + (id.equals(me.getUuid()) ? " (you)" : "");
                    targets.add(new Target(id, name, 0));
                }
                targets.sort(Comparator.comparing((Target t) -> t.label(), String.CASE_INSENSITIVE_ORDER));
            }
        } else {
            List<Entity> found = new ArrayList<>();
            for (Entity e : client.world.getEntities()) {
                if (!(e instanceof LivingEntity) || e instanceof PlayerEntity || e == me) continue;
                boolean tamed = ClientState.TAMED.contains(e.getUuid());
                if (tab == 1 && tamed) continue;
                if (tab == 2 && !tamed) continue;
                if (e.distanceTo(me) > 48f) continue;
                found.add(e);
            }
            found.sort(Comparator.comparingDouble((Entity e) -> e.distanceTo(me)));
            for (Entity e : found) {
                if (targets.size() >= 60) break;
                targets.add(new Target(e.getUuid(), e.getName().getString() + " (" + (int) e.distanceTo(me) + "m)", tab));
            }
        }

        if (selTarget != null) {
            boolean still = false;
            for (Target t : targets) if (t.id().equals(selTarget.id())) { still = true; break; }
            if (!still) selTarget = null;
        }
        tScroll = MathHelper.clamp(tScroll, 0, Math.max(0, targets.size() - VISIBLE));
    }

    private List<SabotageType> effects() {
        List<SabotageType> out = new ArrayList<>();
        for (SabotageType t : SabotageType.values()) if (t.positive == helpful) out.add(t);
        return out;
    }

    private boolean canSend() {
        return selTarget != null && selEffect != null && ClientState.cooldownLeftMs(Ability.SABOTAGE) <= 0;
    }

    private static String duration(int ticks) {
        int s = ticks / 20;
        return s / 60 + ":" + String.format("%02d", s % 60);
    }

    // ---------------------------------------------------------------- layout
    private float anim() {
        float t = MathHelper.clamp((Util.getMeasuringTimeMs() - openedAt) / 260f, 0f, 1f);
        float f = 1f - t;
        return 1f - f * f * f;
    }

    private int x0() { return (width - W) / 2; }
    private int y0() { return (height - H) / 2 + (int) ((1f - anim()) * 22); }
    private int lx() { return x0() + 12; }          // left column x
    private static final int LW = 166;
    private int rx() { return x0() + 190; }         // right column x
    private static final int RW = 164;
    private int listY() { return y0() + 66; }
    private int btnX() { return x0() + W / 2 - 70; }
    private int btnY() { return y0() + H - 28; }

    private static boolean in(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    // ---------------------------------------------------------------- render
    @Override
    public void renderBackground(DrawContext ctx, int mx, int my, float delta) {
        ctx.fill(0, 0, width, height, ((int) (anim() * 150) << 24));
    }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        renderBackground(ctx, mx, my, delta);

        long now = Util.getMeasuringTimeMs();
        float p = anim();
        int pa = Math.max(16, (int) (p * 255));
        int x = x0(), y = y0();
        float pulse = 0.5f + 0.5f * (float) Math.sin(now / 280.0);

        // panel + pulsing border
        int bAlpha = (int) (p * (150 + 100 * pulse));
        ctx.fill(x - 2, y - 2, x + W + 2, y + H + 2, (bAlpha << 24) | 0xC83232);
        ctx.fill(x, y, x + W, y + H, ((int) (p * 245) << 24) | 0x141822);

        // title
        ctx.getMatrices().push();
        ctx.getMatrices().translate(x + W / 2f, y + 8, 0);
        ctx.getMatrices().scale(1.6f, 1.6f, 1f);
        ctx.drawCenteredTextWithShadow(textRenderer, "SABOTAGE", 0, 0, (pa << 24) | 0xFF5555);
        ctx.getMatrices().pop();
        ctx.drawCenteredTextWithShadow(textRenderer, "Pick a target, pick an effect, press GO", x + W / 2, y + 28, (pa << 24) | 0xA0A0B0);

        // ---- tabs (animated underline) ----
        float goalX = lx() + tab * TAB_W;
        underline = underline < 0 ? goalX : underline + (goalX - underline) * 0.3f;
        for (int i = 0; i < TABS.length; i++) {
            int tx = lx() + i * TAB_W, ty = y + 42;
            boolean on = i == tab;
            boolean hov = in(mx, my, tx, ty, TAB_W - 2, 18);
            int bg = on ? 0xFF2B3345 : hov ? 0xFF232A3A : 0xFF1B2130;
            ctx.fill(tx, ty, tx + TAB_W - 2, ty + 18, ((int) (p * 255) << 24) | bg & 0xFFFFFF);
            ctx.drawCenteredTextWithShadow(textRenderer, TABS[i], tx + (TAB_W - 2) / 2, ty + 5, (pa << 24) | (on ? 0xFFFFFF : 0x9A9AAA));
        }
        ctx.fill((int) underline, y + 42 + 16, (int) underline + TAB_W - 2, y + 42 + 18, 0xFFFF5555);

        // ---- harmful / helpful toggle ----
        for (int i = 0; i < 2; i++) {
            boolean helpTab = i == 1;
            int tx = rx() + i * (RW / 2), ty = y + 42;
            boolean on = helpful == helpTab;
            boolean hov = in(mx, my, tx, ty, RW / 2 - 2, 18);
            int bg = on ? (helpTab ? 0xFF2A7A44 : 0xFF8A2A2A) : hov ? 0xFF232A3A : 0xFF1B2130;
            ctx.fill(tx, ty, tx + RW / 2 - 2, ty + 18, ((int) (p * 255) << 24) | bg & 0xFFFFFF);
            ctx.drawCenteredTextWithShadow(textRenderer, helpTab ? "Helpful" : "Harmful", tx + (RW / 2 - 2) / 2, ty + 5,
                    (pa << 24) | (on ? 0xFFFFFF : 0x9A9AAA));
        }

        // ---- target list ----
        if (targets.isEmpty()) {
            String empty = tab == 0 ? "No players online" : tab == 1 ? "No entities nearby" : "No tamed mobs nearby";
            ctx.drawTextWithShadow(textRenderer, empty, lx() + 4, listY() + 6, (pa << 24) | 0x888899);
        }
        for (int i = 0; i < VISIBLE; i++) {
            int idx = tScroll + i;
            if (idx >= targets.size()) break;
            Target t = targets.get(idx);
            float rp = MathHelper.clamp((now - openedAt - 50L * i) / 220f, 0f, 1f);
            int rxx = lx() + (int) ((1f - rp) * 14), ry = listY() + i * ROW;
            int ra = Math.max(16, (int) (rp * 255));
            boolean sel = selTarget != null && selTarget.id().equals(t.id());
            boolean hov = in(mx, my, lx(), ry, LW, ROW - 2);
            int bg = sel ? 0xFF8A2A2A : hov ? 0xFF2B3345 : 0xFF1E2433;
            ctx.fill(rxx, ry, rxx + LW, ry + ROW - 2, ((int) (rp * 255) << 24) | bg & 0xFFFFFF);
            String label = textRenderer.trimToWidth(t.label(), LW - 10);
            ctx.drawTextWithShadow(textRenderer, label, rxx + 6, ry + 5, (ra << 24) | 0xFFFFFF);
        }
        if (tScroll > 0) ctx.drawCenteredTextWithShadow(textRenderer, "^", lx() + LW / 2, listY() - 9, 0xFFAAAAAA);
        if (tScroll + VISIBLE < targets.size()) ctx.drawCenteredTextWithShadow(textRenderer, "v", lx() + LW / 2, listY() + VISIBLE * ROW, 0xFFAAAAAA);

        // ---- effect list ----
        List<SabotageType> effs = effects();
        for (int i = 0; i < VISIBLE; i++) {
            int idx = eScroll + i;
            if (idx >= effs.size()) break;
            SabotageType t = effs.get(idx);
            float rp = MathHelper.clamp((now - openedAt - 50L * i - 80L) / 220f, 0f, 1f);
            int rxx = rx() + (int) ((1f - rp) * 14), ry = listY() + i * ROW;
            int ra = Math.max(16, (int) (rp * 255));
            boolean sel = t == selEffect;
            boolean hov = in(mx, my, rx(), ry, RW, ROW - 2);
            int bg = sel ? (t.positive ? 0xFF2A7A44 : 0xFF8A2A2A) : hov ? 0xFF2B3345 : 0xFF1E2433;
            ctx.fill(rxx, ry, rxx + RW, ry + ROW - 2, ((int) (rp * 255) << 24) | bg & 0xFFFFFF);
            ctx.fill(rxx + 4, ry + 5, rxx + 12, ry + 13, (ra << 24) | (t.color & 0xFFFFFF));
            ctx.drawTextWithShadow(textRenderer, t.displayName, rxx + 17, ry + 5, (ra << 24) | 0xFFFFFF);
            String d = duration(t.durationTicks);
            ctx.drawTextWithShadow(textRenderer, d, rxx + RW - 5 - textRenderer.getWidth(d), ry + 5, (ra << 24) | 0xA0A0B0);
        }
        if (eScroll > 0) ctx.drawCenteredTextWithShadow(textRenderer, "^", rx() + RW / 2, listY() - 9, 0xFFAAAAAA);
        if (eScroll + VISIBLE < effs.size()) ctx.drawCenteredTextWithShadow(textRenderer, "v", rx() + RW / 2, listY() + VISIBLE * ROW, 0xFFAAAAAA);

        // ---- GO button ----
        int bx = btnX(), by = btnY();
        boolean ok = canSend();
        long cd = ClientState.cooldownLeftMs(Ability.SABOTAGE);
        boolean hov = ok && in(mx, my, bx, by, 140, 22);
        int fill;
        String label;
        if (cd > 0) { fill = 0xFF333333; label = "Cooldown: " + (cd / 1000 + 1) + "s"; }
        else if (!ok) { fill = 0xFF333344; label = "Select target & effect"; }
        else if (selEffect.positive) {
            int g = (int) (140 + 60 * pulse);
            fill = hov ? 0xFF3AE060 : (0xFF000000 | (0x28 << 16) | (g << 8) | 0x40);
            label = "GO!";
        } else {
            int g = (int) (140 + 60 * pulse);
            fill = hov ? 0xFFE03A3A : (0xFF000000 | (g << 16) | (0x28 << 8) | 0x28);
            label = "GO!";
        }
        ctx.fill(bx, by, bx + 140, by + 22, fill);
        ctx.drawCenteredTextWithShadow(textRenderer, label, bx + 70, by + 7, ok ? 0xFFFFFFFF : 0xFF999999);
    }

    // ---------------------------------------------------------------- input
    private void click() {
        client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK, 1.0f));
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0) return super.mouseClicked(mx, my, button);
        int y = y0();

        // tabs
        for (int i = 0; i < TABS.length; i++) {
            if (in(mx, my, lx() + i * TAB_W, y + 42, TAB_W - 2, 18)) {
                if (tab != i) {
                    tab = i;
                    selTarget = null;
                    tScroll = 0;
                    rebuild();
                }
                click();
                return true;
            }
        }
        // harmful / helpful
        for (int i = 0; i < 2; i++) {
            if (in(mx, my, rx() + i * (RW / 2), y + 42, RW / 2 - 2, 18)) {
                boolean h = i == 1;
                if (helpful != h) {
                    helpful = h;
                    eScroll = 0;
                    if (selEffect != null && selEffect.positive != helpful) selEffect = null;
                }
                click();
                return true;
            }
        }
        // targets
        for (int i = 0; i < VISIBLE; i++) {
            int idx = tScroll + i;
            if (idx >= targets.size()) break;
            if (in(mx, my, lx(), listY() + i * ROW, LW, ROW - 2)) {
                selTarget = targets.get(idx);
                click();
                return true;
            }
        }
        // effects
        List<SabotageType> effs = effects();
        for (int i = 0; i < VISIBLE; i++) {
            int idx = eScroll + i;
            if (idx >= effs.size()) break;
            if (in(mx, my, rx(), listY() + i * ROW, RW, ROW - 2)) {
                selEffect = effs.get(idx);
                click();
                return true;
            }
        }
        // GO
        if (in(mx, my, btnX(), btnY(), 140, 22) && canSend()) {
            ClientPlayNetworking.send(new Payloads.Sabotage(selTarget.id().toString(), selEffect.ordinal(), selTarget.kind()));
            click();
            close();
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double horizontal, double vertical) {
        int dir = (int) Math.signum(vertical);
        if (mx < rx() - 6) {
            tScroll = MathHelper.clamp(tScroll - dir, 0, Math.max(0, targets.size() - VISIBLE));
        } else {
            eScroll = MathHelper.clamp(eScroll - dir, 0, Math.max(0, effects().size() - VISIBLE));
        }
        return true;
    }
}

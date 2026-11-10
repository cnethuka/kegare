package com.cnethuka.kegare.client;

import com.cnethuka.kegare.Kegare;
import com.cnethuka.kegare.client.gui.GuiDraw;
import com.cnethuka.kegare.config.KegareClientConfig;
import com.cnethuka.kegare.innatetechnique.Ability;
import com.cnethuka.kegare.innatetechnique.CastType;
import com.cnethuka.kegare.innatetechnique.CostEnvironment;
import com.cnethuka.kegare.innatetechnique.InnateTechnique;
import com.cnethuka.kegare.loadout.AbilityLookup;
import com.cnethuka.kegare.network.SyncPayload;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * The combat panel: Technique Mode's HUD. It does not exist until the player presses the Technique Mode key (G); then
 * it slides up and takes the hotbar's place (the hotbar moves to a column on the left edge, {@link CombatHotbarLayer};
 * hearts and food sit on top of the panel). The art is our own
 * (textures under {@code textures/gui/combat/}).
 * <ul>
 *   <li>The crest: freeze pips.</li>
 *   <li>The meter housing: 16 cells of Malice in the innate technique's Malice color (a paler ghost where Malice was
 *       just spent), the current amount on the centre plate (cyan/orange and a snowflake/flame when the surroundings
 *       make casting cheaper/dearer), and a thin charge/channel line under the cells.</li>
 *   <li>The tray: the nine loadout slots plus the ultimate. Each shows its icon, number key, cooldown sweep,
 *       ready flash and R badge (greyed out with no special, or while it's locked); the selected slot is lit gold,
 *       active toggles green; an empty slot stays empty and the ultimate slot shows a lock until it's unlocked. Innate
 *       techniques can add their own marks to their slots ({@link #addSlotDecorator}). No Grade or XP here.</li>
 * </ul>
 * Display only: reads {@link ClientData}.
 */
public final class CombatPanelLayer implements LayeredDraw.Layer {
    public static final ResourceLocation ID = Kegare.id("combat_panel");

    public static final int W = 250;
    public static final int H = 50;
    private static final ResourceLocation PANEL = Kegare.id("textures/gui/combat/panel.png");
    private static final ResourceLocation ACCENT = Kegare.id("textures/gui/combat/panel_accent.png");
    private static final ResourceLocation CELLS = Kegare.id("textures/gui/combat/meter_cells.png");
    private static final ResourceLocation SLOT = Kegare.id("textures/gui/combat/slot.png");
    private static final ResourceLocation SLOT_SELECTED = Kegare.id("textures/gui/combat/slot_selected.png");
    private static final ResourceLocation SLOT_ULTIMATE = Kegare.id("textures/gui/combat/slot_ultimate.png");
    private static final ResourceLocation ENV_COLD = Kegare.id("env_cold_mini");
    private static final ResourceLocation ENV_HOT = Kegare.id("env_hot_mini");

    private static final int CELL_W = 11;
    private static final int CELL_H = 8;
    private static final int CELL_Y = 11;
    /** 16 cells: eight either side of the centre plate. */
    private static final int[] CELL_X = {9, 20, 31, 42, 53, 64, 75, 86, 154, 165, 176, 187, 198, 209, 220, 231};
    /** Tray slots: a 1px ink edge and a 1px rim around the 16x16 icon (the ultimate has 4px of rings). */
    public static final int SLOT_SIZE = 20;
    private static final int SLOT_INSET = 2;
    private static final int ULT_SIZE = 24;
    private static final int ULT_INSET = 4;
    private static final int SLOT_GAP = 2;
    private static final int ULT_GAP = 6;
    private static final int SLOT_Y = 25;
    /** Hearts and food sit this far above the bottom of the screen while the panel is up (vanilla: 39). */
    public static final int STACK_BASE = H + 11;

    private static final int READY_FLASH_TICKS = 8;
    private static final long SLOT_SLIDE_MS = 180;
    private static final long GHOST_HOLD_MS = 450;
    private static final float GHOST_DRAIN_PER_SECOND = 0.5f;

    private static final Map<ResourceLocation, Long> READY_AT = new HashMap<>();
    private static final Map<ResourceLocation, Boolean> WAS_COOLING = new HashMap<>();
    private static float ghost = -1f;
    private static float lastFraction = -1f;
    private static long ghostHoldUntil;
    private static long lastFrameMs;

    /**
     * Something an innate technique draws on one of its own slots, over the icon (e.g. how many puppets there are, or
     * the current order). Drawn wherever the panel is drawn (the HUD and the Sorcerer screen's tray).
     */
    @FunctionalInterface
    public interface SlotDecorator {
        void decorate(GuiGraphics g, Font font, Ability ability, int x, int y, int size);
    }

    private static final List<SlotDecorator> DECORATORS = new java.util.concurrent.CopyOnWriteArrayList<>();

    public static void addSlotDecorator(SlotDecorator decorator) {
        DECORATORS.add(decorator);
    }

    /** Something an innate technique draws on the panel itself, given its top-left corner (e.g. a count of its own). */
    @FunctionalInterface
    public interface PanelDecorator {
        void decorate(GuiGraphics g, Font font, int x0, int y0);
    }

    private static final List<PanelDecorator> PANEL_DECORATORS = new java.util.concurrent.CopyOnWriteArrayList<>();

    public static void addPanelDecorator(PanelDecorator decorator) {
        PANEL_DECORATORS.add(decorator);
    }

    /** True while the panel is up (and so the hotbar is drawn as a column instead). */
    public static boolean shown() {
        Minecraft mc = Minecraft.getInstance();
        return KegareClientConfig.HUD_ENABLED.get() && !mc.options.hideGui && mc.player != null && !mc.player.isSpectator()
                && TechniqueMode.panelShown();
    }

    /** How far the vanilla stack (hearts, food, armour, air, action bar) moves up while the panel is up. */
    public static int extraHeight() {
        return shown() ? STACK_BASE - 39 : 0;
    }

    @Override
    public void render(GuiGraphics g, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        if (!shown() || mc.level == null) return;
        SyncPayload data = ClientData.get();
        InnateTechnique technique = TechniqueMode.current();
        if (data == null || technique == null) return;
        List<Ability> abilities = ClientLoadout.abilities();

        // Hearts, food, armour and air stack on top of the panel.
        mc.gui.leftHeight = Math.max(mc.gui.leftHeight, STACK_BASE);
        mc.gui.rightHeight = Math.max(mc.gui.rightHeight, STACK_BASE);

        // Bottom centre, at the GUI's own scale: icons are the size of the hotbar's items.
        int x0 = (g.guiWidth() - W) / 2;
        int slide = Math.round((1f - easeOut(Math.min(1f, (Util.getMillis() - TechniqueMode.shownAtMs()) / (float) SLOT_SLIDE_MS))) * (H + 4));
        int y0 = g.guiHeight() - H + slide;
        drawPanel(g, mc.font, data, technique, abilities, mc.level.getGameTime(), x0, y0, -1);
    }

    /** Where a slot of the tray was drawn ({@code index} = loadout slot, or the slot count for the ultimate). */
    public record SlotRect(int index, int x, int y, int size, boolean ultimate) {
        public boolean contains(double mx, double my) {
            return mx >= x && mx < x + size && my >= y && my < y + size;
        }
    }

    /**
     * Draws the whole panel at {@code (x0, y0)}: the HUD uses it, and the Sorcerer screen draws the same panel as its
     * loadout tray. {@code dropTarget}: a slot index to outline as a drop target (-1 = none). Returns the slots drawn.
     */
    public static List<SlotRect> drawPanel(GuiGraphics g, Font font, SyncPayload data, InnateTechnique technique, List<Ability> abilities,
                                           long now, int x0, int y0, int dropTarget) {
        int color = technique.maliceColor();
        RenderSystem.enableBlend();
        g.blit(PANEL, x0, y0, 0f, 0f, W, H, W, H);
        tint(g, GuiDraw.mix(color, 0xFFFFFF, 0.45f), 1f);
        g.blit(ACCENT, x0, y0, 0f, 0f, W, H, W, H);
        g.setColor(1f, 1f, 1f, 1f);

        drawMeter(g, font, data, color, x0, y0);
        drawFreeze(g, data, now, x0, y0);
        drawCastProgress(g, technique, data, now, x0, y0);
        List<SlotRect> slots = drawTray(g, font, data, technique, abilities, now, x0, y0, dropTarget);
        for (PanelDecorator decorator : PANEL_DECORATORS) decorator.decorate(g, font, x0, y0);
        return slots;
    }

    // ---------------------------------------------------------------- meter

    private static void drawMeter(GuiGraphics g, Font font, SyncPayload data, int color, int x0, int y0) {
        float fraction = data.capacity() <= 0 ? 0f : Mth.clamp(data.current() / data.capacity(), 0f, 1f);
        float ghostFraction = ghost(fraction);
        float cells = CELL_X.length;
        int fillColor = GuiDraw.mix(color, 0xFFFFFF, 0.15f);
        for (int i = 0; i < CELL_X.length; i++) {
            int cx = x0 + CELL_X[i];
            int cy = y0 + CELL_Y;
            g.blit(CELLS, cx, cy, 0f, 0f, CELL_W, CELL_H, 33, CELL_H);
            float full = Mth.clamp(fraction * cells - i, 0f, 1f);
            float ghostFull = Mth.clamp(ghostFraction * cells - i, 0f, 1f);
            if (ghostFull > full) { // Malice just spent: a paler ghost that drains away
                int from = Math.round(full * 10), to = Math.round(ghostFull * 10);
                if (to > from) {
                    tint(g, GuiDraw.mix(color, 0xFFFFFF, 0.55f), 0.5f);
                    g.blit(CELLS, cx + from, cy, 11f + from, 0f, to - from, CELL_H, 33, CELL_H);
                    g.setColor(1f, 1f, 1f, 1f);
                }
            }
            int w = Math.round(full * 10);
            if (w > 0) {
                tint(g, fillColor, 1f);
                g.blit(CELLS, cx, cy, 11f, 0f, w, CELL_H, 33, CELL_H);
                g.setColor(1f, 1f, 1f, 1f);
                g.blit(CELLS, cx, cy, 22f, 0f, w, CELL_H, 33, CELL_H);
            }
        }
        // The number, colored by the surroundings' cost multiplier, with its glyph in the plate's corner.
        CostEnvironment env = data.environment();
        int textColor = env.icon() == CostEnvironment.Icon.COLD ? 0xFF8FE3FF : env.icon() == CostEnvironment.Icon.HOT ? 0xFFFFA45C : 0xFFFFFFFF;
        String amount = Integer.toString(Mth.floor(data.current()));
        // Five digits (Vast: up to 11000) fit the plate at full size, clear of the glyph. Only wider numbers (a custom config) shrink,
        // because at 75% the font's pixels miss screen pixels and the holes in the zeros close up.
        float scale = font.width(amount) > 30 ? 0.75f : 1f;
        GuiDraw.centered(g, font, Component.literal(amount), x0 + W / 2, y0 + CELL_Y + (scale < 1f ? 2 : 0), scale, textColor);
        if (env.icon() != CostEnvironment.Icon.NONE) {
            GuiDraw.sprite(g, env.icon() == CostEnvironment.Icon.COLD ? ENV_COLD : ENV_HOT, x0 + W / 2 + 16, y0 + 9, 5, 5);
        }
    }

    /** The ghost fill: spent Malice lingers for a moment, then drains toward the new value. Gains show at once. */
    private static float ghost(float fraction) {
        long ms = Util.getMillis();
        if (ghost < 0 || fraction >= ghost) {
            ghost = fraction;
        } else {
            if (fraction < lastFraction - 1.0e-4f) ghostHoldUntil = ms + GHOST_HOLD_MS;
            if (ms > ghostHoldUntil) ghost = Math.max(fraction, ghost - (ms - lastFrameMs) / 1000f * GHOST_DRAIN_PER_SECOND);
        }
        lastFraction = fraction;
        lastFrameMs = ms;
        return ghost;
    }

    // ---------------------------------------------------------------- crest

    private static void drawFreeze(GuiGraphics g, SyncPayload data, long now, int x0, int y0) {
        int max = Math.max(1, data.maxFreezeStacks());
        boolean frozen = data.frozenUntil() > now;
        int step = max <= 5 ? 8 : Math.max(5, 40 / max);
        int startX = x0 + W / 2 - (max * step - 1) / 2;
        float pulse = frozen ? 0.6f + 0.4f * Mth.sin((now % 20) / 20f * Mth.TWO_PI) : 1f;
        for (int i = 0; i < max; i++) {
            boolean lit = frozen || i < data.freezeStacks();
            if (lit && frozen) g.setColor(1f, 1f, 1f, pulse);
            GuiDraw.sprite(g, lit ? GuiDraw.FREEZE_PIP : GuiDraw.FREEZE_PIP_EMPTY, startX + i * step, y0 - 1, 7, 7);
            g.setColor(1f, 1f, 1f, 1f);
        }
    }

    // ---------------------------------------------------------------- charge / channel line

    private static void drawCastProgress(GuiGraphics g, InnateTechnique technique, SyncPayload data, long now, int x0, int y0) {
        data.casting().ifPresent(casting -> {
            Ability ability = AbilityLookup.find(technique, casting.ability()).orElse(null);
            if (ability == null) return;
            long elapsed = Math.max(0, now - casting.startTick());
            boolean charged = ability.castType() == CastType.CHARGED;
            float progress = casting.maxTicks() <= 0 ? 1f
                    : charged ? Math.min(1f, elapsed / (float) casting.maxTicks()) : Math.max(0f, 1f - elapsed / (float) casting.maxTicks());
            int left = x0 + 6, right = x0 + W - 6;
            int filled = Math.round((right - left) * progress);
            int c = charged ? 0xFFFFD24A : 0xFF7FD4FF;
            g.fill(left, y0 + 20, right, y0 + 21, 0xAA000000);
            g.fill(left, y0 + 20, left + filled, y0 + 21, c);
        });
    }

    // ---------------------------------------------------------------- tray

    private static List<SlotRect> drawTray(GuiGraphics g, Font font, SyncPayload data, InnateTechnique technique, List<Ability> abilities,
                                           long now, int x0, int y0, int dropTarget) {
        List<SlotRect> rects = new java.util.ArrayList<>();
        int n = abilities.size();
        boolean hasUlt = technique.ultimate().isPresent() || technique.ultimateLocked();
        int gap = SLOT_GAP, ultGap = ULT_GAP;
        int width = n * SLOT_SIZE + Math.max(0, n - 1) * gap + (hasUlt ? ultGap + ULT_SIZE : 0);
        int sx = x0 + (W - width) / 2;
        ResourceLocation castingId = data.casting().map(SyncPayload.CastingEntry::ability).orElse(null);
        for (int i = 0; i < n; i++) {
            int x = sx + i * (SLOT_SIZE + gap);
            int y = y0 + SLOT_Y;
            drawSlot(g, font, data, abilities.get(i), x, y, i == data.selectedSlot(), ClientLoadout.numberKey(i), castingId, now);
            if (i == dropTarget) g.renderOutline(x - 1, y - 1, SLOT_SIZE + 2, SLOT_SIZE + 2, 0xFFFFFFFF);
            rects.add(new SlotRect(i, x, y, SLOT_SIZE, false));
        }
        if (hasUlt) {
            int ux = sx + n * (SLOT_SIZE + gap) - gap + ultGap;
            int uy = y0 + SLOT_Y - 2;
            RenderSystem.enableBlend();
            g.blit(SLOT_ULTIMATE, ux, uy, 0f, 0f, ULT_SIZE, ULT_SIZE, ULT_SIZE, ULT_SIZE);
            Optional<Ability> ult = technique.ultimate();
            if (ult.isPresent() && !ClientLoadout.isLocked(data, ult.get())) {
                GuiDraw.abilityIcon(g, ult.get(), ux + ULT_INSET, uy + ULT_INSET);
                drawCooldown(g, font, data, ult.get(), ux + ULT_INSET, uy + ULT_INSET, 16, now);
                burntOut(g, ult.get(), ux + ULT_INSET, uy + ULT_INSET, 16);
            } else {
                GuiDraw.sprite(g, GuiDraw.LOCK, ux + (ULT_SIZE - 11) / 2, uy + (ULT_SIZE - 11) / 2, 11, 11);
            }
            label(g, font, KegareKeys.ULTIMATE.getTranslatedKeyMessage(), ux + ULT_SIZE, uy + ULT_SIZE, 0xFFB8B0C4);
            rects.add(new SlotRect(n, ux, uy, ULT_SIZE, true));
        }
        return rects;
    }

    private static void drawSlot(GuiGraphics g, Font font, SyncPayload data, @Nullable Ability ability, int x, int y, boolean selected,
                                 Component key, @Nullable ResourceLocation castingId, long now) {
        RenderSystem.enableBlend();
        g.blit(selected ? SLOT_SELECTED : SLOT, x, y, 0f, 0f, SLOT_SIZE, SLOT_SIZE, SLOT_SIZE, SLOT_SIZE);
        if (ability == null) { // empty loadout slot
            label(g, font, key, x + SLOT_SIZE, y + SLOT_SIZE, selected ? GuiDraw.WHITE : 0xFF6B6276);
            return;
        }
        if (!GuiDraw.abilityIcon(g, ability, x + SLOT_INSET, y + SLOT_INSET)) {
            g.drawCenteredString(font, ability.displayName().getString().substring(0, 1), x + SLOT_SIZE / 2, y + 6, GuiDraw.WHITE);
        }
        boolean cooling = drawCooldown(g, font, data, ability, x + SLOT_INSET, y + SLOT_INSET, 16, now);
        burntOut(g, ability, x + SLOT_INSET, y + SLOT_INSET, 16);
        if (ClientLoadout.toggledOn(data, ability)) g.renderOutline(x + 1, y + 1, SLOT_SIZE - 2, SLOT_SIZE - 2, 0xFF5CFF7A);
        if (ability.id().equals(castingId) || ability.special().map(s -> s.id().equals(castingId)).orElse(false)) {
            g.renderOutline(x + 1, y + 1, SLOT_SIZE - 2, SLOT_SIZE - 2, 0xFFFFD24A);
        }
        GuiDraw.specialBadge(g, x + SLOT_SIZE - 7, y - 3, ability.special().filter(special -> !ClientLoadout.isLocked(data, special)).isPresent());
        if (!DECORATORS.isEmpty()) {
            g.pose().pushPose();
            g.pose().translate(0, 0, 250);
            for (SlotDecorator decorator : DECORATORS) decorator.decorate(g, font, ability, x, y, SLOT_SIZE);
            g.pose().popPose();
        }
        if (!cooling) label(g, font, key, x + SLOT_SIZE, y + SLOT_SIZE, selected ? GuiDraw.WHITE : 0xFFD8D2E0);
    }

    /** A bare 20x20 tray slot frame (selected = lit gold; the icon goes 2px in), for screens that show abilities the HUD's way. */
    public static void slotFrame(GuiGraphics g, int x, int y, boolean selected) {
        RenderSystem.enableBlend();
        g.blit(selected ? SLOT_SELECTED : SLOT, x, y, 0f, 0f, SLOT_SIZE, SLOT_SIZE, SLOT_SIZE, SLOT_SIZE);
    }

    /** Burnout (DESIGN §3.25): an innate technique slot is greyed over while it lasts. Shared abilities stay lit: they still cast. */
    private static void burntOut(GuiGraphics g, Ability ability, int x, int y, int size) {
        if (!BurnoutHud.active() || com.cnethuka.kegare.loadout.SharedAbilities.find(ability.id()).isPresent()) return;
        g.fill(x, y, x + size, y + size, 0x9A3A3430);
    }

    /** Cooldown sweep (and seconds) over a 16x16 icon area, plus the ready flash. Returns whether it is cooling. */
    private static boolean drawCooldown(GuiGraphics g, Font font, SyncPayload data, Ability ability, int x, int y, int size, long now) {
        int remaining = 0, duration = 0;
        for (SyncPayload.CooldownEntry c : data.cooldowns()) {
            if (c.ability().equals(ability.cooldownKey())) {
                remaining = (int) Math.max(0, c.endTick() - now);
                duration = c.duration();
            }
        }
        boolean cooling = remaining > 0 && duration > 0;
        if (cooling) {
            int sweep = Mth.ceil(size * Math.min(1f, remaining / (float) duration));
            g.fill(x, y, x + size, y + size, 0xA0000000);
            g.fill(x, y + size - sweep, x + size, y + size, 0x55000000 | (GuiDraw.MALICE & 0xFFFFFF));
            g.pose().pushPose();
            g.pose().translate(0, 0, 200);
            g.drawCenteredString(font, Integer.toString(Mth.ceil(remaining / 20f)), x + size / 2, y + (size - 8) / 2, 0xFFFF6B6B);
            g.pose().popPose();
        }
        Boolean was = WAS_COOLING.put(ability.id(), cooling);
        if (Boolean.TRUE.equals(was) && !cooling) READY_AT.put(ability.id(), now);
        Long readyAt = READY_AT.get(ability.id());
        if (readyAt != null && now - readyAt < READY_FLASH_TICKS) {
            int alpha = (int) (190 * (1f - (now - readyAt) / (float) READY_FLASH_TICKS));
            g.fill(x - 1, y - 1, x + size + 1, y + size + 1, (alpha << 24) | 0xFFFFFF);
        }
        return cooling;
    }

    /** The key under a slot's bottom-right corner, big and white like the hotbar's numbers. */
    private static void label(GuiGraphics g, Font font, Component text, int right, int bottom, int color) {
        g.pose().pushPose();
        g.pose().translate(0, 0, 250);
        g.drawString(font, text, right - font.width(text) + 1, bottom - 8, color, true);
        g.pose().popPose();
    }

    private static void tint(GuiGraphics g, int rgb, float alpha) {
        g.setColor(((rgb >> 16) & 0xFF) / 255f, ((rgb >> 8) & 0xFF) / 255f, (rgb & 0xFF) / 255f, alpha);
    }

    private static float easeOut(float t) {
        float u = 1f - t;
        return 1f - u * u * u;
    }
}

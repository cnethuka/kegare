package com.cnethuka.kegare.client;

import com.cnethuka.kegare.Kegare;
import com.cnethuka.kegare.client.gui.GuiDraw;
import com.cnethuka.kegare.config.KegareClientConfig;
import com.cnethuka.kegare.network.SyncPayload;
import com.cnethuka.kegare.registry.KegareRegistries;
import com.cnethuka.kegare.innatetechnique.Ability;
import com.cnethuka.kegare.innatetechnique.CastManager;
import com.cnethuka.kegare.innatetechnique.CastType;
import com.cnethuka.kegare.innatetechnique.InnateTechnique;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * Top-left HUD: innate technique name, Malice bar (current/capacity) with the surroundings cost icon, and your freeze
 * stacks. Ability slots are on the ability bar ({@link AbilityBarLayer}). Severed players see only freeze stacks.
 * Purely visual: reads {@link ClientData}, never decides anything.
 */
public final class MaliceHudLayer implements LayeredDraw.Layer {
    private static final long GHOST_HOLD_MS = 450;
    /** Fraction of the whole bar the ghost drains per second. */
    private static final float GHOST_DRAIN_PER_SECOND = 0.5f;
    private static float ghost = -1f;
    private static float lastFraction = -1f;
    private static long ghostHoldUntil;
    private static long lastFrameMs;

    public static final ResourceLocation ID = Kegare.id("malice_hud");

    private static final int BAR_WIDTH = 100;
    private static final int BAR_HEIGHT = GuiDraw.BAR_HEIGHT;

    private static final int COLOR_TEXT = 0xFFFFFF;
    private static final int COLOR_MALICE_TEXT = 0xD9B3FF;

    @Override
    public void render(GuiGraphics g, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        if (!KegareClientConfig.HUD_ENABLED.get() || mc.options.hideGui || mc.player == null || mc.level == null) return;
        if (mc.player.isSpectator() || mc.getDebugOverlay().showDebugScreen()) return;
        SyncPayload data = ClientData.get();
        if (data == null || !data.born()) return;

        Font font = mc.font;
        long now = mc.level.getGameTime();
        int x = KegareClientConfig.HUD_X.get();
        int y = KegareClientConfig.HUD_Y.get();
        if (data.severed()) { // Severed: no HUD, but anyone can be frozen
            drawFreeze(g, font, data, x, y, now);
            return;
        }

        InnateTechnique innateTechnique = data.innateTechnique().map(KegareRegistries.INNATE_TECHNIQUE::get).orElse(null);
        Component title = innateTechnique != null
                ? innateTechnique.displayName()
                : Component.translatable("hud.kegare.no_innate_technique").withStyle(ChatFormatting.GRAY);
        g.drawString(font, title, x, y, COLOR_TEXT);
        y += font.lineHeight + 2;

        float fraction = data.capacity() <= 0 ? 0f : data.current() / data.capacity();
        int color = innateTechnique != null ? innateTechnique.maliceColor() : InnateTechnique.DEFAULT_MALICE_COLOR;
        GuiDraw.maliceBar(g, x, y, BAR_WIDTH, fraction, color, ghost(fraction));
        int textEnd = g.drawString(font, Component.translatable("hud.kegare.malice", Mth.floor(data.current()), data.capacity()), x + BAR_WIDTH + 4, y + 1, COLOR_MALICE_TEXT);
        drawEnvironment(g, font, data, textEnd + 4, y);
        y += BAR_HEIGHT + 4;

        drawFreeze(g, font, data, x, y, now); // ability slots live on the ability bar (AbilityBarLayer)
    }

    /**
     * The ghost fill: after Malice is spent, the lost part stays (paler) for a moment, then drains down to the new
     * value. Gains show at once.
     */
    private static float ghost(float fraction) {
        long ms = net.minecraft.Util.getMillis();
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

    /** Snowflake (cheaper) or flame (dearer) next to the Malice bar, with the current cost multiplier. */
    private static void drawEnvironment(GuiGraphics g, Font font, SyncPayload data, int x, int y) {
        if (data.environment().icon() == com.cnethuka.kegare.innatetechnique.CostEnvironment.Icon.NONE) return;
        boolean cold = data.environment().icon() == com.cnethuka.kegare.innatetechnique.CostEnvironment.Icon.COLD;
        GuiDraw.sprite(g, cold ? GuiDraw.ENV_COLD : GuiDraw.ENV_HOT, x, y - 1, 11, 11);
        float multiplier = data.environment().multiplier();
        int color = multiplier < 1f ? 0x7FE6FF : multiplier > 1f ? 0xFF8A5C : 0xAAAAAA;
        g.drawString(font, Component.translatable("hud.kegare.cost_multiplier", GuiDraw.multiplier(multiplier)), x + 13, y + 1, color);
    }

    /** Your freeze stacks as pips (only when you have some), or "Frozen!" while frozen solid. */
    private static void drawFreeze(GuiGraphics g, Font font, SyncPayload data, int x, int y, long now) {
        boolean frozen = data.frozenUntil() > now;
        if (data.freezeStacks() <= 0 && !frozen) return;
        int labelWidth = g.drawString(font, Component.translatable(frozen ? "hud.kegare.frozen" : "hud.kegare.freeze"), x, y + 1,
                frozen ? 0x7FE6FF : 0xC2EEFA) - x;
        int px = x + labelWidth + 3;
        for (int i = 0; i < data.maxFreezeStacks(); i++) {
            boolean full = frozen || i < data.freezeStacks();
            GuiDraw.sprite(g, full ? GuiDraw.FREEZE_PIP : GuiDraw.FREEZE_PIP_EMPTY, px, y + 1, 7, 7);
            px += 8;
        }
    }

}

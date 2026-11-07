package com.cnethuka.kegare.client;

import com.cnethuka.kegare.Kegare;
import com.cnethuka.kegare.client.gui.GuiDraw;
import com.cnethuka.kegare.config.KegareClientConfig;
import com.cnethuka.kegare.innatetechnique.Ability;
import com.cnethuka.kegare.innatetechnique.CastType;
import com.cnethuka.kegare.innatetechnique.InnateTechnique;
import com.cnethuka.kegare.loadout.AbilityLookup;
import com.cnethuka.kegare.network.SyncPayload;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * Without the combat panel the HUD stays out of the way: only what can't wait shows, small and under the crosshair.
 * Freeze stacks while there are any ("Frozen!" while frozen; Severed players too), and the charge/channel line for a cast still
 * held after Technique Mode ended. Innate techniques can add lines that show in Technique Mode too ({@link #addExtra}).
 */
public final class StatusLayer implements LayeredDraw.Layer {
    public static final ResourceLocation ID = Kegare.id("status");

    /**
     * A line an innate technique adds under the crosshair, drawn in AND out of Technique Mode (e.g. possessing a
     * puppet). Draws at {@code y} (centered on {@code cx}) and returns the y for whatever comes next.
     */
    @FunctionalInterface
    public interface Extra {
        int render(GuiGraphics g, Minecraft mc, int cx, int y);
    }

    private static final java.util.List<Extra> EXTRAS = new java.util.concurrent.CopyOnWriteArrayList<>();

    public static void addExtra(Extra extra) {
        EXTRAS.add(extra);
    }

    @Override
    public void render(GuiGraphics g, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        if (!KegareClientConfig.HUD_ENABLED.get() || mc.options.hideGui || mc.player == null || mc.level == null) return;
        if (mc.player.isSpectator()) return;
        int extraY = g.guiHeight() / 2 + (CombatPanelLayer.shown() ? 10 : 40);
        for (Extra extra : EXTRAS) extraY = extra.render(g, mc, g.guiWidth() / 2, extraY);
        if (CombatPanelLayer.shown()) return;
        SyncPayload data = ClientData.get();
        if (data == null || !data.born()) return;
        long now = mc.level.getGameTime();
        int cx = g.guiWidth() / 2;
        int y = g.guiHeight() / 2 + 10;

        boolean frozen = data.frozenUntil() > now;
        if (frozen || data.freezeStacks() > 0) {
            int max = Math.max(1, data.maxFreezeStacks());
            int step = max <= 5 ? 8 : Math.max(5, 40 / max);
            int startX = cx - (max * step - 1) / 2;
            for (int i = 0; i < max; i++) {
                GuiDraw.sprite(g, frozen || i < data.freezeStacks() ? GuiDraw.FREEZE_PIP : GuiDraw.FREEZE_PIP_EMPTY, startX + i * step, y, 7, 7);
            }
            if (frozen) GuiDraw.centered(g, mc.font, Component.translatable("hud.kegare.frozen"), cx, y + 9, 1f, 0xFF8FE3FF);
            y += frozen ? 20 : 10;
        }

        InnateTechnique technique = TechniqueMode.current();
        if (technique == null) return;
        data.casting().ifPresent(casting -> {
            Ability ability = AbilityLookup.find(technique, casting.ability()).orElse(null);
            if (ability == null) return;
            long elapsed = Math.max(0, now - casting.startTick());
            boolean charged = ability.castType() == CastType.CHARGED;
            float progress = casting.maxTicks() <= 0 ? 1f
                    : charged ? Math.min(1f, elapsed / (float) casting.maxTicks()) : Math.max(0f, 1f - elapsed / (float) casting.maxTicks());
            int w = 40;
            g.fill(cx - w / 2 - 1, g.guiHeight() / 2 + 6 + 20, cx + w / 2 + 1, g.guiHeight() / 2 + 9 + 20, 0xAA000000);
            g.fill(cx - w / 2, g.guiHeight() / 2 + 7 + 20, cx - w / 2 + Mth.ceil(w * progress), g.guiHeight() / 2 + 8 + 20,
                    charged ? 0xFFFFD24A : 0xFF7FD4FF);
        });
    }
}

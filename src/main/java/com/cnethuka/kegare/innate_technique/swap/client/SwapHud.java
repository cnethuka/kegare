package com.cnethuka.kegare.innate_technique.swap.client;

import com.cnethuka.kegare.Kegare;
import com.cnethuka.kegare.client.visual.ClientVisuals;
import com.cnethuka.kegare.innate_technique.swap.SwapIds;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/**
 * What only a disguised player sees, so they know it's on (DESIGN §3.19): thin teal brackets in the four corners of their screen,
 * breathing slowly, for exactly as long as the disguise lasts (the {@code awareness_shimmer} loop, sent to the wearer alone). Crisp
 * GUI pixels at the edges, nothing near the middle of the view. They come in from the corners as it lands and are gone the moment
 * it ends.
 */
final class SwapHud {
    private static final ResourceLocation LAYER = Kegare.id("swap_disguised");
    private static final int COLOR = 0x2EF2DA;
    /** Ticks the brackets take to come in from the corners. */
    private static final float SETTLE = 5f;

    private SwapHud() {}

    static void init(IEventBus modBus) {
        modBus.addListener((RegisterGuiLayersEvent event) -> event.registerBelow(VanillaGuiLayers.CROSSHAIR, LAYER, SwapHud::render));
    }

    private static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;
        float partial = delta.getGameTimeDeltaPartialTick(false);
        ClientVisuals.LoopTime time = ClientVisuals.loopTime(mc.player, SwapIds.AWARENESS_SHIMMER_KEY, partial);
        if (time == null) return;
        float settle = Mth.clamp(time.ticks() / SETTLE, 0f, 1f);
        float ease = 1f - (1f - settle) * (1f - settle);
        // A slow breath once settled; brighter while it comes in.
        float breath = 0.55f + 0.2f * Mth.sin(time.ticks() * Mth.TWO_PI / 40f);
        float alpha = Mth.lerp(ease, 1f, breath);
        int width = g.guiWidth(), height = g.guiHeight();
        int inset = Math.round(Mth.lerp(ease, -6f, 6f));
        int arm = Math.max(10, height / 22);
        int color = Math.round(alpha * 255) << 24 | COLOR;
        corner(g, inset, inset, arm, 1, 1, color);
        corner(g, width - 1 - inset, inset, arm, -1, 1, color);
        corner(g, inset, height - 1 - inset, arm, 1, -1, color);
        corner(g, width - 1 - inset, height - 1 - inset, arm, -1, -1, color);
    }

    /** One bracket: an L with its corner at (x, y), arms {@code arm} long running in (dx, dy), one GUI pixel thick. */
    private static void corner(GuiGraphics g, int x, int y, int arm, int dx, int dy, int color) {
        int x1 = x + dx * arm, y1 = y + dy * arm;
        g.fill(Math.min(x, x1), y, Math.max(x, x1) + 1, y + 1, color);
        g.fill(x, Math.min(y, y1), x + 1, Math.max(y, y1) + 1, color);
    }
}

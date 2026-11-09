package com.cnethuka.kegare.fists.client;

import com.cnethuka.kegare.client.StatusLayer;
import com.cnethuka.kegare.client.gui.GuiDraw;
import com.cnethuka.kegare.fists.ZonePayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.common.NeoForge;

/** The zone's cue under the crosshair (DESIGN §3.28): "120%" and the zone's time left, with the melee cues, only while it lasts. */
public final class ZoneHud {
    private static final int BAR_WIDTH = 34;
    private static final int COLOR = 0xFFF0EEF6;
    private static long until;
    private static int ticks;

    private ZoneHud() {}

    public static void init() {
        ZonePayload.setClientHandler(p -> {
            until = p.until();
            ticks = p.ticks();
        });
        StatusLayer.addExtra(ZoneHud::render);
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut e) -> until = 0);
    }

    private static int render(GuiGraphics g, Minecraft mc, int cx, int y) {
        if (mc.level == null || ticks <= 0) return y;
        long left = until - mc.level.getGameTime();
        if (left <= 0) return y;
        Component text = Component.translatable("hud.kegare.zone", String.format(java.util.Locale.ROOT, "%.0f", Math.ceil(left / 20.0)));
        GuiDraw.centered(g, mc.font, text, cx, y, 1f, COLOR);
        int top = y + 9;
        int fill = Mth.ceil(BAR_WIDTH * Math.min(1f, left / (float) ticks));
        g.fill(cx - BAR_WIDTH / 2 - 1, top, cx + BAR_WIDTH / 2 + 1, top + 3, 0xAA000000);
        g.fill(cx - BAR_WIDTH / 2, top + 1, cx - BAR_WIDTH / 2 + fill, top + 2, COLOR);
        return top + 5;
    }
}

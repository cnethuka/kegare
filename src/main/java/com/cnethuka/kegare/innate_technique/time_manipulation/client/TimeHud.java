package com.cnethuka.kegare.innate_technique.time_manipulation.client;

import com.cnethuka.kegare.client.StatusLayer;
import com.cnethuka.kegare.client.gui.GuiDraw;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * Point Maker on the HUD (DESIGN §3.26): while a mark is out, the next cast is the return, so "Point Maker: return 12.3s" (time left
 * on the mark) shows under the crosshair in bronze, in and out of Technique Mode. With no mark out nothing shows. Burnout has its own
 * line (§3.25).
 */
final class TimeHud {
    private static final int BRONZE = 0xFFD9BC8A;

    private TimeHud() {}

    static void init() {
        StatusLayer.addExtra(TimeHud::render);
    }

    private static int render(GuiGraphics g, Minecraft mc, int cx, int y) {
        long left = MarkClient.remaining();
        if (left <= 0) return y;
        Component line = Component.translatable("hud.kegare.point_maker.return", String.format(Locale.ROOT, "%.1f", left / 20.0));
        GuiDraw.centered(g, mc.font, line, cx, y, 1f, BRONZE);
        return y + 11;
    }
}

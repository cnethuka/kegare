package com.cnethuka.kegare.client;

import com.cnethuka.kegare.client.gui.GuiDraw;
import com.cnethuka.kegare.network.SyncPayload;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * Burnout on the HUD (DESIGN §3.25): "Burnt out 12.3s" under the crosshair while it lasts, in and out of Technique Mode, in ash grey
 * with an ember edge; the combat panel dims its innate technique slots too ({@link #active}).
 */
public final class BurnoutHud {
    private static final int ASH = 0xFFC9BFB4;

    private BurnoutHud() {}

    public static void init() {
        StatusLayer.addExtra(BurnoutHud::render);
    }

    /** Ticks of Burnout left on this client's player, 0 if none. */
    public static int remaining() {
        Minecraft mc = Minecraft.getInstance();
        SyncPayload data = ClientData.get();
        if (data == null || mc.level == null) return 0;
        return (int) Math.max(0, data.burntOutUntil() - mc.level.getGameTime());
    }

    public static boolean active() {
        return remaining() > 0;
    }

    private static int render(GuiGraphics g, Minecraft mc, int cx, int y) {
        int left = remaining();
        if (left <= 0) return y;
        Component line = Component.translatable("hud.kegare.burnt_out", String.format(Locale.ROOT, "%.1f", left / 20.0));
        GuiDraw.centered(g, mc.font, line, cx, y, 1f, ASH);
        return y + 11;
    }
}

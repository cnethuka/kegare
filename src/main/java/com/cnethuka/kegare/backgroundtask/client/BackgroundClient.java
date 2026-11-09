package com.cnethuka.kegare.backgroundtask.client;

import com.cnethuka.kegare.backgroundtask.BackgroundActionPayload;
import com.cnethuka.kegare.backgroundtask.BackgroundSyncPayload;
import com.cnethuka.kegare.backgroundtask.BackgroundTask;
import com.cnethuka.kegare.backgroundtask.BackgroundTasks;
import com.cnethuka.kegare.client.StatusLayer;
import com.cnethuka.kegare.client.gui.GuiDraw;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Your own Background Tasks as the server last said (DESIGN §3.31), for the Sorcerer screen's Background view, and a small icon per
 * running task under the crosshair with the other status lines: lit while it runs, greyed with a pause mark while it's paused. Only you
 * are ever sent any of it.
 */
public final class BackgroundClient {
    private static BackgroundSyncPayload state = BackgroundSyncPayload.NONE;
    /** A refusal to show on the Sorcerer screen's status line (read once). */
    @Nullable
    private static Component refusal;
    private static final int ICON = 9, GAP = 2;

    private BackgroundClient() {}

    public static void init() {
        BackgroundSyncPayload.setClientHandler(BackgroundClient::update);
        StatusLayer.addExtra(BackgroundClient::hud);
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut e) -> state = BackgroundSyncPayload.NONE);
    }

    private static void update(BackgroundSyncPayload payload) {
        state = payload;
        if (!payload.refusal().isEmpty()) refusal = Component.translatable(payload.refusal());
    }

    public static BackgroundSyncPayload state() {
        return state;
    }

    /** The refusal waiting to be shown, if any; cleared once read. */
    @Nullable
    public static Component takeRefusal() {
        Component r = refusal;
        refusal = null;
        return r;
    }

    public static Optional<BackgroundSyncPayload.TaskView> task(ResourceLocation id) {
        return state.tasks().stream().filter(t -> t.id().equals(id)).findFirst();
    }

    public static void set(int slot, ResourceLocation task) {
        PacketDistributor.sendToServer(new BackgroundActionPayload(BackgroundActionPayload.SET, slot, Optional.of(task)));
    }

    public static void clear(int slot) {
        PacketDistributor.sendToServer(new BackgroundActionPayload(BackgroundActionPayload.CLEAR, slot, Optional.empty()));
    }

    public static void switchTo(int slot, boolean on) {
        PacketDistributor.sendToServer(new BackgroundActionPayload(on ? BackgroundActionPayload.ON : BackgroundActionPayload.OFF, slot, Optional.empty()));
    }

    private static int hud(GuiGraphics g, Minecraft mc, int cx, int y) {
        List<BackgroundSyncPayload.SlotView> shown = new ArrayList<>();
        for (BackgroundSyncPayload.SlotView s : state.slots()) {
            if (s.on() && s.task().isPresent() && task(s.task().get()).map(BackgroundSyncPayload.TaskView::unlocked).orElse(false)) shown.add(s);
        }
        if (shown.isEmpty()) return y;
        int x = cx - (shown.size() * (ICON + GAP) - GAP) / 2;
        for (BackgroundSyncPayload.SlotView s : shown) {
            BackgroundTask task = BackgroundTasks.find(s.task().get()).orElse(null);
            if (task == null) continue;
            if (s.paused()) {
                GuiDraw.tinted(g, task.icon(), x, y, ICON, ICON, 0x808080, 0.55f);
                g.fill(x + 2, y + 2, x + 4, y + ICON - 2, 0xFFFFD24A); // the pause mark
                g.fill(x + 5, y + 2, x + 7, y + ICON - 2, 0xFFFFD24A);
            } else {
                GuiDraw.sprite(g, task.icon(), x, y, ICON, ICON);
            }
            x += ICON + GAP;
        }
        return y + ICON + 2;
    }
}

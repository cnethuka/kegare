package com.cnethuka.kegare.client.gui;

import com.cnethuka.kegare.Kegare;
import com.cnethuka.kegare.network.RevealPayload;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.Toast;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ToastAddEvent;

/**
 * Pending reveals. A reveal opens as soon as the player is in-game with no other screen open, so it never
 * fights the world-loading screen on first join or an open inventory.
 * <p>
 * Toasts (e.g. "Chosen by Fate") are held while a reveal is pending or showing, so they can't spoil the result,
 * and are replayed when the last reveal closes.
 */
@EventBusSubscriber(modid = Kegare.MODID, value = Dist.CLIENT)
public final class RevealQueue {
    private static final Deque<RevealPayload> PENDING = new ArrayDeque<>();
    private static final List<Toast> HELD_TOASTS = new ArrayList<>();
    private static boolean flushRequested;

    private RevealQueue() {}

    public static void enqueue(RevealPayload payload) {
        PENDING.add(payload);
    }

    /** Called every client tick (from ClientInput). */
    public static void tick(Minecraft mc) {
        if (!PENDING.isEmpty() && mc.screen == null && mc.level != null && mc.player != null && mc.getOverlay() == null) {
            mc.setScreen(new RevealScreen(PENDING.poll()));
            return;
        }
        if (flushRequested && !active(mc)) {
            flushRequested = false;
            List<Toast> toasts = new ArrayList<>(HELD_TOASTS);
            HELD_TOASTS.clear();
            toasts.forEach(mc.getToasts()::addToast);
        }
    }

    static boolean active(Minecraft mc) {
        return !PENDING.isEmpty() || mc.screen instanceof RevealScreen;
    }

    /** A reveal screen closed: replay held toasts on a later tick, once no reveal is active. */
    static void onRevealClosed() {
        flushRequested = true;
    }

    public static void reset() {
        PENDING.clear();
        HELD_TOASTS.clear();
        flushRequested = false;
    }

    @SubscribeEvent
    static void onToast(ToastAddEvent event) {
        if (active(Minecraft.getInstance())) {
            event.setCanceled(true);
            HELD_TOASTS.add(event.getToast());
        }
    }
}

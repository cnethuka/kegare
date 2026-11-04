package com.cnethuka.kegare.client.gui;

import com.cnethuka.kegare.network.RevealPayload;
import com.cnethuka.kegare.network.SorcererInfoPayload;
import com.cnethuka.kegare.network.TalismanPromptPayload;
import net.minecraft.client.Minecraft;

/**
 * Client entry points for server-to-client GUI payloads. Only referenced from payload handlers that run on the
 * client, so this class (and the client-only classes it touches) is never loaded on a dedicated server.
 */
public final class ClientGuiHooks {
    private ClientGuiHooks() {}

    /** A roll was decided and saved on the server: queue its reveal. */
    public static void onReveal(RevealPayload payload) {
        RevealQueue.enqueue(payload);
    }

    /** Answer to the K key: open (or refresh) the Sorcerer screen. */
    public static void onSorcererInfo(SorcererInfoPayload payload) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof SorcererScreen screen) {
            screen.update(payload);
        } else if (mc.screen == null) {
            mc.setScreen(new SorcererScreen(payload));
        }
    }

    /** The server accepted a talisman use: ask the player to confirm. Nothing has been consumed yet. */
    public static void onTalismanPrompt(TalismanPromptPayload payload) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen == null) mc.setScreen(new TalismanConfirmScreen(payload));
    }
}

package com.cnethuka.kegare.client;

import com.cnethuka.kegare.Kegare;
import com.cnethuka.kegare.network.IntentPayload;
import com.cnethuka.kegare.technique.CastIntent;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Turns key presses/releases into {@link IntentPayload}s. Nothing else is sent. */
@EventBusSubscriber(modid = Kegare.MODID, value = Dist.CLIENT)
public final class ClientInput {
    private static boolean castHeld;
    private static boolean ultimateHeld;

    private ClientInput() {}

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.getConnection() == null) {
            castHeld = false;
            ultimateHeld = false;
            return;
        }
        castHeld = trackHold(KegareKeys.CAST, castHeld, CastIntent.CAST_PRESS, CastIntent.CAST_RELEASE);
        ultimateHeld = trackHold(KegareKeys.ULTIMATE, ultimateHeld, CastIntent.ULTIMATE_PRESS, CastIntent.ULTIMATE_RELEASE);
        while (KegareKeys.NEXT_ABILITY.consumeClick()) send(CastIntent.NEXT_ABILITY);
        while (KegareKeys.PREVIOUS_ABILITY.consumeClick()) send(CastIntent.PREVIOUS_ABILITY);
    }

    /**
     * Sends press on the down edge and release on the up edge. A tap shorter than one tick still
     * sends press then release. Opening a screen releases held keys, which sends a release.
     */
    private static boolean trackHold(KeyMapping key, boolean wasHeld, CastIntent press, CastIntent release) {
        boolean clicked = false;
        while (key.consumeClick()) clicked = true;
        boolean down = key.isDown();
        if (!wasHeld && (down || clicked)) {
            send(press);
            if (!down) {
                send(release);
                return false;
            }
            return true;
        }
        if (wasHeld && !down) {
            send(release);
            return false;
        }
        return wasHeld;
    }

    private static void send(CastIntent intent) {
        PacketDistributor.sendToServer(new IntentPayload(intent));
    }

    @SubscribeEvent
    static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        castHeld = false;
        ultimateHeld = false;
        ClientData.reset();
    }
}

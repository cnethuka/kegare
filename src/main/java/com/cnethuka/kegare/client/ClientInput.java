package com.cnethuka.kegare.client;

import com.cnethuka.kegare.Kegare;
import com.cnethuka.kegare.client.gui.AbilityRadialScreen;
import com.cnethuka.kegare.client.gui.RevealQueue;
import com.cnethuka.kegare.config.KegareClientConfig;
import com.cnethuka.kegare.innatetechnique.CastIntent;
import com.cnethuka.kegare.network.IntentPayload;
import com.cnethuka.kegare.network.SorcererRequestPayload;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Turns key presses/releases into requests. Nothing here decides an outcome:
 * <ul>
 *   <li>cast / ultimate: press and release intents</li>
 *   <li>next ability: a tap cycles; holding it opens the radial menu (selection is a validated request)</li>
 *   <li>previous ability: cycles</li>
 *   <li>Sorcerer screen: asks the server for the data, and the screen opens when it arrives</li>
 * </ul>
 */
@EventBusSubscriber(modid = Kegare.MODID, value = Dist.CLIENT)
public final class ClientInput {
    private static boolean castHeld;
    private static boolean ultimateHeld;
    /** Ticks the next-ability key has been held; -1 = not held (or already consumed by the radial). */
    private static int nextHeldTicks = -1;

    private ClientInput() {}

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.getConnection() == null) {
            castHeld = false;
            ultimateHeld = false;
            nextHeldTicks = -1;
            return;
        }
        RevealQueue.tick(mc);

        castHeld = trackHold(KegareKeys.CAST, castHeld, CastIntent.CAST_PRESS, CastIntent.CAST_RELEASE);
        ultimateHeld = trackHold(KegareKeys.ULTIMATE, ultimateHeld, CastIntent.ULTIMATE_PRESS, CastIntent.ULTIMATE_RELEASE);
        trackNextAbility(mc);
        while (KegareKeys.PREVIOUS_ABILITY.consumeClick()) send(CastIntent.PREVIOUS_ABILITY);
        while (KegareKeys.SORCERER.consumeClick()) PacketDistributor.sendToServer(SorcererRequestPayload.INSTANCE);
    }

    /** Tap = cycle to the next ability. Hold = open the radial menu (release over a slot selects it). */
    private static void trackNextAbility(Minecraft mc) {
        boolean clicked = false;
        while (KegareKeys.NEXT_ABILITY.consumeClick()) clicked = true;
        boolean down = KegareKeys.NEXT_ABILITY.isDown();
        if (down) {
            nextHeldTicks = nextHeldTicks < 0 ? 0 : nextHeldTicks + 1;
            if (nextHeldTicks >= KegareClientConfig.RADIAL_HOLD_TICKS.get() && AbilityRadialScreen.canOpen()) {
                nextHeldTicks = -1;
                mc.setScreen(new AbilityRadialScreen());
            }
        } else {
            // Released before the hold threshold (or tapped within a single tick): plain cycle.
            if (nextHeldTicks >= 0 || clicked) send(CastIntent.NEXT_ABILITY);
            nextHeldTicks = -1;
        }
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
        nextHeldTicks = -1;
        ClientData.reset();
        RevealQueue.reset();
    }
}

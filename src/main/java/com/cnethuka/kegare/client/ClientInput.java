package com.cnethuka.kegare.client;

import com.cnethuka.kegare.Kegare;
import com.cnethuka.kegare.client.gui.AbilityRadialScreen;
import com.cnethuka.kegare.client.gui.RevealQueue;
import com.cnethuka.kegare.config.KegareClientConfig;
import com.cnethuka.kegare.config.KegareClientConfig.InputScheme;
import com.cnethuka.kegare.innatetechnique.CastIntent;
import com.cnethuka.kegare.innatetechnique.InnateTechnique;
import com.cnethuka.kegare.network.IntentPayload;
import com.cnethuka.kegare.network.SelectAbilityPayload;
import com.cnethuka.kegare.network.SorcererRequestPayload;
import com.cnethuka.kegare.network.SyncPayload;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Turns input into requests. Nothing here decides an outcome; the server validates every intent and selection.
 * <ul>
 *   <li>R: the selected ability's special (press / release). V: the ultimate. K: Sorcerer screen.</li>
 *   <li>Hold Z: the radial menu.</li>
 *   <li>{@link InputScheme#TOGGLE_MODE}: G toggles Technique Mode; in it, scroll and number keys pick abilities (the
 *       hotbar never changes) and right-click base-casts without using the held item.</li>
 *   <li>{@link InputScheme#SPLIT}: scroll always picks abilities, number keys stay on the hotbar, C base-casts.</li>
 * </ul>
 */
@EventBusSubscriber(modid = Kegare.MODID, value = Dist.CLIENT)
public final class ClientInput {
    private static boolean castHeld;
    private static boolean specialHeld;
    private static boolean ultimateHeld;
    /** Ticks the radial key has been held; -1 = not held (or already consumed by the radial). */
    private static int radialHeldTicks = -1;
    /** A right-click shorter than a tick, caught before vanilla consumes it (Technique Mode). */
    private static boolean useClicked;

    private ClientInput() {}

    /** Before vanilla handles keys this tick: in Technique Mode the number keys pick abilities, not hotbar slots. */
    @SubscribeEvent
    static void onClientTickPre(ClientTickEvent.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !TechniqueMode.isActive()) return;
        while (mc.options.keyUse.consumeClick()) useClicked = true; // ours, not the held item's
        KeyMapping[] hotbar = mc.options.keyHotbarSlots;
        for (int i = 0; i < hotbar.length; i++) {
            while (hotbar[i].consumeClick()) selectSlot(i);
        }
    }

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.getConnection() == null) {
            reset();
            return;
        }
        RevealQueue.tick(mc);

        // Technique Mode ends when abilities go away: death, Severed, no innate technique.
        if (TechniqueMode.isActive() && !TechniqueMode.abilitiesAvailable()) TechniqueMode.exit();
        while (KegareKeys.TECHNIQUE_MODE.consumeClick()) TechniqueMode.toggle();

        // Base cast: right-click in Technique Mode (TOGGLE_MODE) or the Cast key (SPLIT).
        boolean castDown;
        if (TechniqueMode.scheme() == InputScheme.SPLIT) {
            castDown = consumeAndCheck(KegareKeys.CAST);
        } else {
            while (KegareKeys.CAST.consumeClick()) { } // unused in this scheme
            castDown = TechniqueMode.isActive() && (mc.options.keyUse.isDown() || useClicked);
        }
        useClicked = false;
        castHeld = edge(castDown, castHeld, CastIntent.CAST_PRESS, CastIntent.CAST_RELEASE);
        specialHeld = edge(consumeAndCheck(KegareKeys.SPECIAL), specialHeld, CastIntent.SPECIAL_PRESS, CastIntent.SPECIAL_RELEASE);
        ultimateHeld = edge(consumeAndCheck(KegareKeys.ULTIMATE), ultimateHeld, CastIntent.ULTIMATE_PRESS, CastIntent.ULTIMATE_RELEASE);
        trackRadial(mc);
        while (KegareKeys.SORCERER.consumeClick()) PacketDistributor.sendToServer(SorcererRequestPayload.INSTANCE);
    }

    /** In Technique Mode right-click (and pick block, which would change the hotbar) does nothing vanilla. */
    @SubscribeEvent
    static void onInteraction(InputEvent.InteractionKeyMappingTriggered event) {
        if (TechniqueMode.isActive() && (event.isUseItem() || event.isPickBlock())) {
            event.setCanceled(true);
            event.setSwingHand(false);
        }
    }

    /** Scroll picks abilities in Technique Mode, and always in SPLIT (when the player has abilities). */
    @SubscribeEvent(priority = EventPriority.HIGH)
    static void onScroll(InputEvent.MouseScrollingEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null || mc.player == null) return;
        boolean picksAbilities = TechniqueMode.isActive()
                || (TechniqueMode.scheme() == InputScheme.SPLIT && TechniqueMode.abilitiesAvailable());
        if (!picksAbilities) return;
        event.setCanceled(true);
        double delta = event.getScrollDeltaY();
        if (delta == 0) return;
        SyncPayload data = ClientData.get();
        int size = ClientLoadout.size();
        if (data == null || size == 0) return;
        // Like the hotbar: scrolling up moves left.
        selectSlot(Mth.positiveModulo(data.selectedSlot() + (delta > 0 ? -1 : 1), size));
    }

    /** Opening any screen leaves Technique Mode (except the radial menu, which is part of picking abilities). */
    @SubscribeEvent
    static void onScreenOpening(ScreenEvent.Opening event) {
        if (!(event.getNewScreen() instanceof AbilityRadialScreen)) TechniqueMode.exit();
    }

    /** Hold the radial key: the radial menu opens after the configured hold time. A tap does nothing. */
    private static void trackRadial(Minecraft mc) {
        while (KegareKeys.RADIAL.consumeClick()) { }
        if (KegareKeys.RADIAL.isDown()) {
            radialHeldTicks = radialHeldTicks < 0 ? 0 : radialHeldTicks + 1;
            if (radialHeldTicks >= KegareClientConfig.RADIAL_HOLD_TICKS.get() && AbilityRadialScreen.canOpen()) {
                radialHeldTicks = -1; // opening a screen releases the key, so this restarts cleanly
                mc.setScreen(new AbilityRadialScreen());
            }
        } else {
            radialHeldTicks = -1;
        }
    }

    private static void selectSlot(int slot) {
        if (slot < 0 || slot >= ClientLoadout.size()) return;
        PacketDistributor.sendToServer(new SelectAbilityPayload(slot));
    }

    /** Down if held now or clicked since last tick (a tap shorter than a tick still counts). */
    private static boolean consumeAndCheck(KeyMapping key) {
        boolean clicked = false;
        while (key.consumeClick()) clicked = true;
        return key.isDown() || clicked;
    }

    /** Press on the down edge, release on the up edge. A sub-tick tap sends press then release next tick. */
    private static boolean edge(boolean down, boolean wasHeld, CastIntent press, CastIntent release) {
        if (down && !wasHeld) {
            send(press);
            return true;
        }
        if (!down && wasHeld) {
            send(release);
            return false;
        }
        return wasHeld;
    }

    private static void send(CastIntent intent) {
        PacketDistributor.sendToServer(new IntentPayload(intent));
    }

    private static void reset() {
        castHeld = false;
        specialHeld = false;
        ultimateHeld = false;
        useClicked = false;
        radialHeldTicks = -1;
        TechniqueMode.exit();
    }

    @SubscribeEvent
    static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        reset();
        ClientData.reset();
        RevealQueue.reset();
    }
}

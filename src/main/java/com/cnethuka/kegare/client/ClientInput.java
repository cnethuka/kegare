package com.cnethuka.kegare.client;

import com.cnethuka.kegare.Kegare;
import com.cnethuka.kegare.client.gui.RevealQueue;
import com.cnethuka.kegare.config.KegareClientConfig.TechniqueSelect;
import com.cnethuka.kegare.innatetechnique.CastIntent;
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
 *   <li>C (Cast, rebindable): base-casts the selected ability, in or out of Technique Mode.</li>
 *   <li>G toggles Technique Mode. In it, ONE control picks abilities and the other stays on the hotbar
 *       ({@link TechniqueSelect}: number keys by default, or the scroll wheel), and right-click base-casts too,
 *       without using the held item. Out of it, number keys, scroll and right-click are all vanilla.</li>
 * </ul>
 */
@EventBusSubscriber(modid = Kegare.MODID, value = Dist.CLIENT)
public final class ClientInput {
    private static boolean castHeld;
    private static boolean specialHeld;
    private static boolean ultimateHeld;
    /** A right-click shorter than a tick, caught before vanilla consumes it (Technique Mode). */
    private static boolean useClicked;

    private ClientInput() {}

    /**
     * Before vanilla handles keys this tick. In Technique Mode right-click is ours, and with {@link TechniqueSelect#NUMBER_KEYS}
     * the number keys pick abilities instead of hotbar slots (numbers past the last ability do nothing).
     */
    @SubscribeEvent
    static void onClientTickPre(ClientTickEvent.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !TechniqueMode.isActive()) return;
        while (mc.options.keyUse.consumeClick()) useClicked = true; // ours, not the held item's
        if (TechniqueMode.select() != TechniqueSelect.NUMBER_KEYS) return;
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

        // Base cast: the Cast key (C) in either scheme; in Technique Mode right-click casts too.
        boolean castDown = consumeAndCheck(KegareKeys.CAST)
                || (TechniqueMode.isActive() && (mc.options.keyUse.isDown() || useClicked));
        useClicked = false;
        castHeld = edge(castDown, castHeld, CastIntent.CAST_PRESS, CastIntent.CAST_RELEASE);
        specialHeld = edge(consumeAndCheck(KegareKeys.SPECIAL), specialHeld, CastIntent.SPECIAL_PRESS, CastIntent.SPECIAL_RELEASE);
        ultimateHeld = edge(consumeAndCheck(KegareKeys.ULTIMATE), ultimateHeld, CastIntent.ULTIMATE_PRESS, CastIntent.ULTIMATE_RELEASE);
        while (KegareKeys.SORCERER.consumeClick()) PacketDistributor.sendToServer(SorcererRequestPayload.INSTANCE);
    }

    /** In Technique Mode right-click casts; it never uses the held item. */
    @SubscribeEvent
    static void onInteraction(InputEvent.InteractionKeyMappingTriggered event) {
        if (TechniqueMode.isActive() && event.isUseItem()) {
            event.setCanceled(true);
            event.setSwingHand(false);
        }
    }

    /** With {@link TechniqueSelect#SCROLL_WHEEL}, scroll picks abilities in Technique Mode (otherwise it stays on the hotbar). */
    @SubscribeEvent(priority = EventPriority.HIGH)
    static void onScroll(InputEvent.MouseScrollingEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null || mc.player == null) return;
        if (!TechniqueMode.isActive() || TechniqueMode.select() != TechniqueSelect.SCROLL_WHEEL) return;
        event.setCanceled(true);
        double delta = event.getScrollDeltaY();
        if (delta == 0) return;
        SyncPayload data = ClientData.get();
        int size = ClientLoadout.size();
        if (data == null || size == 0) return;
        // Like the hotbar: scrolling up moves left. Empty slots are skipped (number keys still reach them).
        java.util.List<com.cnethuka.kegare.innatetechnique.Ability> abilities = ClientLoadout.abilities();
        int step = delta > 0 ? -1 : 1;
        int slot = data.selectedSlot();
        for (int i = 0; i < size; i++) {
            slot = Mth.positiveModulo(slot + step, size);
            if (slot < abilities.size() && abilities.get(slot) != null) {
                selectSlot(slot);
                return;
            }
        }
    }

    /** Opening any screen leaves Technique Mode. */
    @SubscribeEvent
    static void onScreenOpening(ScreenEvent.Opening event) {
        TechniqueMode.exit();
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
        TechniqueMode.exit();
    }

    @SubscribeEvent
    static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        reset();
        ClientData.reset();
        RevealQueue.reset();
    }
}

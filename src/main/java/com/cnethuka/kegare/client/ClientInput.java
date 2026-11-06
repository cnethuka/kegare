package com.cnethuka.kegare.client;

import com.cnethuka.kegare.Kegare;
import com.cnethuka.kegare.client.gui.RevealQueue;
import com.cnethuka.kegare.config.KegareClientConfig.TechniqueSelect;
import com.cnethuka.kegare.innatetechnique.CastIntent;
import com.cnethuka.kegare.network.IntentPayload;
import com.cnethuka.kegare.network.SelectAbilityPayload;
import com.cnethuka.kegare.network.SorcererRequestPayload;
import com.cnethuka.kegare.network.SyncPayload;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.core.GlobalPos;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Turns input into requests. Nothing here decides an outcome; the server validates every intent and selection.
 * <ul>
 *   <li>R: the selected ability's special (press / release). V: the ultimate. Both in Technique Mode only.
 *       K: Sorcerer screen.</li>
 *   <li>C (Cast, rebindable): base-casts the selected ability, in or out of Technique Mode.</li>
 *   <li>G toggles Technique Mode. In it, ONE control picks abilities and the other stays on the hotbar
 *       ({@link TechniqueSelect}: number keys by default, or the scroll wheel). Out of it, number keys and scroll are
 *       vanilla. Right-click is always vanilla: it uses the held item and never casts.</li>
 * </ul>
 */
@EventBusSubscriber(modid = Kegare.MODID, value = Dist.CLIENT)
public final class ClientInput {
    private static boolean castHeld;
    private static boolean specialHeld;
    private static boolean ultimateHeld;
    /** Set while a replaced player waits to be compared with the new one; see {@link #onPlayerReplaced}. */
    private static Optional<GlobalPos> deathBeforeReplaced;
    private static final List<BooleanSupplier> SPECIAL_KEY_NEEDED = new ArrayList<>();

    private ClientInput() {}

    /** While this holds, R works outside Technique Mode too: for something R has to end, never for casting. */
    public static void addSpecialKeyNeeded(BooleanSupplier needed) {
        SPECIAL_KEY_NEEDED.add(needed);
    }

    private static boolean specialKeyAllowed() {
        return TechniqueMode.isActive() || SPECIAL_KEY_NEEDED.stream().anyMatch(BooleanSupplier::getAsBoolean);
    }

    /**
     * Before vanilla handles keys this tick. In Technique Mode with {@link TechniqueSelect#NUMBER_KEYS} the number keys
     * pick abilities instead of hotbar slots (numbers past the last ability do nothing).
     */
    @SubscribeEvent
    static void onClientTickPre(ClientTickEvent.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !TechniqueMode.isActive() || TechniqueMode.select() != TechniqueSelect.NUMBER_KEYS) return;
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

        if (deathBeforeReplaced != null) {
            if (!deathBeforeReplaced.equals(mc.player.getLastDeathLocation())) TechniqueMode.exit();
            deathBeforeReplaced = null;
        }
        TechniqueMode.tick();
        while (KegareKeys.TECHNIQUE_MODE.consumeClick()) TechniqueMode.toggle();

        castHeld = edge(consumeAndCheck(KegareKeys.CAST), castHeld, CastIntent.CAST_PRESS, CastIntent.CAST_RELEASE);
        specialHeld = edge(CastKeys.down(consumeAndCheck(KegareKeys.SPECIAL), specialHeld, specialKeyAllowed()),
                specialHeld, CastIntent.SPECIAL_PRESS, CastIntent.SPECIAL_RELEASE);
        ultimateHeld = edge(CastKeys.down(consumeAndCheck(KegareKeys.ULTIMATE), ultimateHeld, TechniqueMode.isActive()),
                ultimateHeld, CastIntent.ULTIMATE_PRESS, CastIntent.ULTIMATE_RELEASE);
        while (KegareKeys.SORCERER.consumeClick()) PacketDistributor.sendToServer(SorcererRequestPayload.INSTANCE);
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

    /**
     * A respawn from dying ends Technique Mode; a dimension change makes a new player too, and the mode carries on. A new death
     * location tells them apart, and the new player only has it once the packet is handled, so the next tick compares them.
     * {@link TechniqueMode#tick} can't catch this one: with instant respawn no tick may see the dead player. Dying twice on one
     * block under instant respawn does look like a dimension change, and only keeps the mode on.
     */
    @SubscribeEvent
    static void onPlayerReplaced(ClientPlayerNetworkEvent.Clone event) {
        if (event.getOldPlayer().isDeadOrDying()) TechniqueMode.exit();
        else deathBeforeReplaced = event.getOldPlayer().getLastDeathLocation();
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
        deathBeforeReplaced = null;
        TechniqueMode.exit();
    }

    @SubscribeEvent
    static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        reset();
        ClientData.reset();
        RevealQueue.reset();
    }
}

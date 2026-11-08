package com.cnethuka.kegare.client;

import com.cnethuka.kegare.Kegare;
import com.cnethuka.kegare.client.gui.RevealQueue;
import com.cnethuka.kegare.innatetechnique.Ability;
import com.cnethuka.kegare.innatetechnique.CastIntent;
import com.cnethuka.kegare.innatetechnique.CastType;
import com.cnethuka.kegare.network.IntentPayload;
import com.cnethuka.kegare.network.SelectAbilityPayload;
import com.cnethuka.kegare.network.SorcererRequestPayload;
import com.cnethuka.kegare.network.SyncPayload;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import javax.annotation.Nullable;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Turns input into requests. Nothing here decides an outcome; the server validates every intent and selection.
 * <ul>
 *   <li>In Technique Mode (G) a number key selects its slot and casts its base ability; hold R with it for the special. Hold the
 *       key to charge or channel, let go to fire or stop. R on its own uses the selected ability's special when it comes up
 *       ({@link InstantCastKeys} has the rules). V: the ultimate. Out of the mode, number keys are the hotbar and none of this casts.</li>
 *   <li>K: Sorcerer screen. Right-click is always vanilla: it uses the held item and never casts.</li>
 * </ul>
 */
@EventBusSubscriber(modid = Kegare.MODID, value = Dist.CLIENT)
public final class ClientInput {
    private static final InstantCastKeys KEYS = new InstantCastKeys();
    /** Number key clicks taken before vanilla could see them, for this tick's {@link InstantCastKeys}. */
    private static final boolean[] NUMBER_CLICKED = new boolean[InstantCastKeys.SLOTS];
    private static boolean ultimateHeld;
    /** Set while a replaced player waits to be compared with the new one; see {@link #onPlayerReplaced}. */
    private static Optional<GlobalPos> deathBeforeReplaced;
    private static final List<BooleanSupplier> SPECIAL_KEY_NEEDED = new ArrayList<>();

    private ClientInput() {}

    /** While this holds, R is for ending something, never for casting: it works out of Technique Mode too, on the press. */
    public static void addSpecialKeyNeeded(BooleanSupplier needed) {
        SPECIAL_KEY_NEEDED.add(needed);
    }

    private static void tickNumberKeysAndSpecial(Minecraft mc) {
        KeyMapping[] hotbar = mc.options.keyHotbarSlots;
        InstantCastKeys.Frame frame = new InstantCastKeys.Frame();
        int slots = ClientLoadout.size();
        for (int i = 0; i < NUMBER_CLICKED.length && i < hotbar.length; i++) {
            frame.numberDown[i] = hotbar[i].isDown();
            frame.numberClicked[i] = NUMBER_CLICKED[i] && i < slots;
            NUMBER_CLICKED[i] = false;
        }
        while (KegareKeys.SPECIAL.consumeClick()) frame.specialClicked = true;
        frame.specialDown = KegareKeys.SPECIAL.isDown();
        frame.modeOn = TechniqueMode.isActive();
        frame.specialNeeded = SPECIAL_KEY_NEEDED.stream().anyMatch(BooleanSupplier::getAsBoolean);
        frame.screenOpen = mc.screen != null;
        KEYS.tick(frame, WORLD, OUTPUT);
    }

    private static final InstantCastKeys.World WORLD = new InstantCastKeys.World() {
        @Override
        public boolean holds(int slot, boolean special) {
            List<Ability> abilities = ClientLoadout.abilities();
            return slot < abilities.size() && wouldHold(abilities.get(slot), special);
        }

        @Override
        public boolean casting() {
            SyncPayload data = ClientData.get();
            return data != null && data.casting().isPresent();
        }

        @Override
        public boolean selectedSpecialHolds() {
            SyncPayload data = ClientData.get();
            return data != null && holds(data.selectedSlot(), true);
        }
    };

    /** A charge or channel, that the server won't turn down for being locked or on cooldown. Those it should be asked about, and it'll say why. */
    private static boolean wouldHold(@Nullable Ability slotted, boolean special) {
        Ability ability = slotted == null || !special ? slotted : slotted.special().orElse(null);
        SyncPayload data = ClientData.get();
        Minecraft mc = Minecraft.getInstance();
        if (ability == null || data == null || mc.level == null) return false;
        if (ability.castType() != CastType.CHARGED && ability.castType() != CastType.CHANNELED) return false;
        long now = mc.level.getGameTime();
        boolean cooling = data.cooldowns().stream().anyMatch(c -> c.ability().equals(ability.cooldownKey()) && c.endTick() > now);
        return !cooling && !ClientLoadout.isLocked(data, ability);
    }

    private static final InstantCastKeys.Output OUTPUT = new InstantCastKeys.Output() {
        @Override
        public void select(int slot) {
            PacketDistributor.sendToServer(new SelectAbilityPayload(slot));
        }

        @Override
        public void intent(CastIntent intent) {
            send(intent);
        }

        @Override
        public void alreadyCasting() {
            actionBar(Component.translatable("message.kegare.cast.busy"));
        }

        @Override
        public void specialNeedsHolding() {
            SyncPayload data = ClientData.get();
            if (data == null) return;
            ClientLoadout.abilities().get(data.selectedSlot()).special().ifPresent(special -> actionBar(Component.translatable(
                    "message.kegare.cast.hold_special", KegareKeys.SPECIAL.getTranslatedKeyMessage(), ClientLoadout.numberKey(data.selectedSlot()), special.displayName())));
        }
    };

    private static void actionBar(Component message) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.displayClientMessage(message, true);
    }

    /**
     * Before vanilla handles keys this tick: in Technique Mode the number keys cast instead of moving the hotbar. A key pressed in
     * the mode stays out of vanilla's hands until it's up, or its auto-repeat would move the hotbar after G.
     */
    @SubscribeEvent
    static void onClientTickPre(ClientTickEvent.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        KeyMapping[] hotbar = mc.options.keyHotbarSlots;
        for (int i = 0; i < NUMBER_CLICKED.length && i < hotbar.length; i++) {
            if (!TechniqueMode.isActive() && !KEYS.ownsKey(i)) continue;
            while (hotbar[i].consumeClick()) NUMBER_CLICKED[i] = true;
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
        while (KegareKeys.TECHNIQUE_MODE.consumeClick()) com.cnethuka.kegare.severed.client.SeveredKeys.pressG(); // Blur for Severed (§3.27)

        tickNumberKeysAndSpecial(mc);
        ultimateHeld = edge(CastKeys.down(consumeAndCheck(KegareKeys.ULTIMATE), ultimateHeld, TechniqueMode.isActive()),
                ultimateHeld, CastIntent.ULTIMATE_PRESS, CastIntent.ULTIMATE_RELEASE);
        while (KegareKeys.SORCERER.consumeClick()) PacketDistributor.sendToServer(SorcererRequestPayload.INSTANCE);
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
        KEYS.reset();
        java.util.Arrays.fill(NUMBER_CLICKED, false);
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

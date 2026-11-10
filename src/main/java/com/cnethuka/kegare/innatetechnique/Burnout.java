package com.cnethuka.kegare.innatetechnique;

import com.cnethuka.kegare.loadout.SharedAbilities;
import com.cnethuka.kegare.network.PlayerSync;
import com.cnethuka.kegare.registry.KegareAttachments;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.server.level.ServerPlayer;

/**
 * Burnout (DESIGN §3.25): a shared state that locks a player's innate technique for a while after something big (Time Manipulation's
 * Standstill; Dominions later). While it lasts, every ability, special and the ultimate of their innate technique fizzles with
 * "Burnt out (Xs).", nothing paid; shared abilities (Reverse Malice Technique) still work, and a running toggle can still be switched
 * off. It counts down only while they're online, so logging out never waits it out. Saved; cleared on death (it isn't copied to the
 * respawned player). Shown on the HUD.
 * <p>
 * A {@link Modifier} changes the length of every Burnout as it's applied (Background RMT shortens it); {@link #shorten} takes time
 * off one that's running.
 */
public final class Burnout {
    /** Changes a Burnout's length as it's applied. Return the new length in ticks. */
    @FunctionalInterface
    public interface Modifier {
        int modify(ServerPlayer player, int ticks);
    }

    /** The attachment ({@code kegare:burnout}): ticks left, counted down while online. */
    public static final class Data {
        public static final Codec<Data> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.optionalFieldOf("remaining", 0).forGetter(d -> d.remaining),
                Codec.INT.optionalFieldOf("length", 0).forGetter(d -> d.length),
                Codec.INT.optionalFieldOf("paid", 0).forGetter(d -> d.paid)
        ).apply(i, Data::new));

        int remaining;
        /**
         * How long it was when it was applied, before anything shortened it (Background RMT): repair's brain cost reads it, as taking away a
         * share of a Burnout costs that share of a full repair.
         */
        int length;
        /** Ticks taken off and paid for on the brain meter (Background RMT's shortening, repair). The rest of what's gone ran down on the countdown. */
        int paid;

        public Data() {
            this(0, 0, 0);
        }

        private Data(int remaining, int length, int paid) {
            this.remaining = Math.max(0, remaining);
            this.length = Math.max(this.remaining, length);
            this.paid = Math.max(0, Math.min(this.length - this.remaining, paid));
        }
    }

    private static final List<Modifier> MODIFIERS = new CopyOnWriteArrayList<>();

    private Burnout() {}

    public static void addModifier(Modifier modifier) {
        MODIFIERS.add(modifier);
    }

    public static void removeModifier(Modifier modifier) {
        MODIFIERS.remove(modifier);
    }

    /**
     * Burns the player out for {@code ticks} (after every {@link Modifier}). Never shortens one already running: the longer of the
     * two stands.
     */
    public static void apply(ServerPlayer player, int ticks) {
        int full = ticks;
        for (Modifier modifier : MODIFIERS) ticks = modifier.modify(player, ticks);
        if (ticks <= 0) return;
        Data data = player.getData(KegareAttachments.BURNOUT);
        if (ticks <= data.remaining) return;
        data.remaining = ticks;
        data.length = Math.max(ticks, full);
        data.paid = data.length - ticks; // what the modifiers took off, they paid for
        PlayerSync.markDirty(player);
    }

    public static boolean isBurntOut(ServerPlayer player) {
        return remaining(player) > 0;
    }

    /** Ticks left, 0 if none. Reading never creates the attachment. */
    public static int remaining(ServerPlayer player) {
        return player.hasData(KegareAttachments.BURNOUT) ? player.getData(KegareAttachments.BURNOUT).remaining : 0;
    }

    /** How long the running Burnout was when it was applied, before anything shortened it, in ticks; 0 if none. */
    public static int length(ServerPlayer player) {
        return isBurntOut(player) ? Math.max(remaining(player), player.getData(KegareAttachments.BURNOUT).length) : 0;
    }

    /** Takes {@code ticks} off a running Burnout that were paid for on the brain meter (a repair). */
    public static void repair(ServerPlayer player, int ticks) {
        if (ticks <= 0 || !isBurntOut(player)) return;
        Data data = player.getData(KegareAttachments.BURNOUT);
        int taken = Math.min(ticks, data.remaining);
        data.paid += taken;
        shorten(player, taken);
    }

    /** The running Burnout's length, or the last one's if it's just ended (a repair finishing it off pays for it); 0 if there's never been one. */
    public static int lastLength(ServerPlayer player) {
        return player.hasData(KegareAttachments.BURNOUT) ? player.getData(KegareAttachments.BURNOUT).length : 0;
    }

    /** Ticks of the running (or just ended) Burnout that its own countdown took, not paid for: what finishing it off still owes. */
    public static int unpaid(ServerPlayer player) {
        if (!player.hasData(KegareAttachments.BURNOUT)) return 0;
        Data data = player.getData(KegareAttachments.BURNOUT);
        return Math.max(0, data.length - data.remaining - data.paid);
    }

    /** Takes {@code ticks} off a running Burnout. */
    public static void shorten(ServerPlayer player, int ticks) {
        if (ticks <= 0 || !isBurntOut(player)) return;
        Data data = player.getData(KegareAttachments.BURNOUT);
        data.remaining = Math.max(0, data.remaining - ticks);
        PlayerSync.markDirty(player);
    }

    public static void clear(ServerPlayer player) {
        if (!isBurntOut(player)) return;
        player.getData(KegareAttachments.BURNOUT).remaining = 0;
        PlayerSync.markDirty(player);
    }

    /** Every server tick, for every online player: one tick off. The HUD learns the end time once, so only the end is synced. */
    public static void tick(ServerPlayer player) {
        if (!isBurntOut(player)) return;
        Data data = player.getData(KegareAttachments.BURNOUT);
        data.remaining--;
        if (data.remaining <= 0) {
            data.remaining = 0;
            PlayerSync.markDirty(player);
        }
    }

    /** What Burnout blocks: anything of their innate technique, not a shared ability. */
    public static boolean blocks(ServerPlayer player, Ability ability) {
        return isBurntOut(player) && SharedAbilities.find(ability.id()).isEmpty()
                && ability.base().map(base -> SharedAbilities.find(base.id()).isEmpty()).orElse(true); // a shared ability's special neither
    }
}

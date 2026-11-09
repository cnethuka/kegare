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
 * Standstill now; Dominions later). While it lasts, every ability, special and the ultimate of their innate technique fizzles with
 * "Burnt out (Xs).", nothing paid; shared abilities (Reverse Malice Technique) still work, and a running toggle can still be switched
 * off. It counts down only while they're online, so logging out never waits it out. Saved; cleared on death (it isn't copied to the
 * respawned player). Shown on the HUD.
 * <p>
 * A {@link Modifier} changes the length of every Burnout as it's applied (Background RMT will shorten it); {@link #shorten} takes time
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
                Codec.INT.optionalFieldOf("remaining", 0).forGetter(d -> d.remaining)
        ).apply(i, Data::new));

        int remaining;

        public Data() {
            this(0);
        }

        private Data(int remaining) {
            this.remaining = Math.max(0, remaining);
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
        for (Modifier modifier : MODIFIERS) ticks = modifier.modify(player, ticks);
        if (ticks <= 0) return;
        Data data = player.getData(KegareAttachments.BURNOUT);
        if (ticks <= data.remaining) return;
        data.remaining = ticks;
        PlayerSync.markDirty(player);
    }

    public static boolean isBurntOut(ServerPlayer player) {
        return remaining(player) > 0;
    }

    /** Ticks left, 0 if none. Reading never creates the attachment. */
    public static int remaining(ServerPlayer player) {
        return player.hasData(KegareAttachments.BURNOUT) ? player.getData(KegareAttachments.BURNOUT).remaining : 0;
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

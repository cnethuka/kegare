package com.cnethuka.kegare.dominion;

import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.util.RandomSource;

/**
 * One clash between Dominions (DESIGN §3.35), server side. Its sides are kept as a list so more could take part one day; for now there
 * are always two ({@link Clashes#MAX_SIDES}): the Dominion that was there first, and the one that opened over it.
 */
public final class Clash {
    public enum Phase {
        /** Both casters have a moment to be holding their key. */
        GRIP,
        /** A tug of war won by timing; casters held in place, paying Malice. */
        MAINTAIN,
        /** Both half-formed; the one who deals the other more damage wins. */
        FIGHT,
        OVER
    }

    /** How it was decided, for the advancements. */
    public enum Result {
        /** Pushed out (or let go, ran dry or cracked apart) before the fight. */
        MAINTAIN,
        /** More damage dealt in the fight. */
        FIGHT,
        /** Too close to call: both broke. */
        TIE,
        /** The other side left (died, logged out, ended by a command). */
        FORFEIT
    }

    /** A timing prompt: which key, and its beat on the server's clock in ms (SubTick.nowMs). */
    record Prompt(int id, int key, double beatMs) {}

    public static final class Side {
        /** Its barrier where the clash is: in an inner space, that's the space's sky (the Dominion itself is its shell, {@code primary()}). */
        final DominionEntity dominion;
        final UUID caster;
        /** How long it had been closed before the clash, or -1 if it never had. */
        final int usedTicks;
        /** Whether its caster is holding the key that opened it (their client says; the server's own guess to start with). */
        boolean held;
        @Nullable Prompt prompt;
        double nextPromptMs;
        int prompts;
        /** In the Fight: damage dealt to the other caster, after defences. */
        double score;

        Side(DominionEntity dominion, int usedTicks) {
            this.dominion = dominion;
            this.caster = dominion.caster;
            this.usedTicks = usedTicks;
            this.held = dominion.keyHeld;
        }

        public DominionEntity dominion() {
            return dominion;
        }

        public UUID caster() {
            return caster;
        }

        public boolean held() {
            return held;
        }

        public double score() {
            return score;
        }

        /** The prompt waiting on this side: its key (0 jump, 1 sneak, 2 sprint), or -1. */
        public int promptKey() {
            return prompt == null ? -1 : prompt.key();
        }

        /** Its beat on the server's clock, or NaN. */
        public double promptBeatMs() {
            return prompt == null ? Double.NaN : prompt.beatMs();
        }
    }

    final List<Side> sides;
    final RandomSource random;
    Phase phase = Phase.GRIP;
    int phaseTicks;
    /** The tug of war, -1 to 1: at 1 the first side has pushed the second out, at -1 the other way round. */
    double bar;

    Clash(Side first, Side second, RandomSource random) {
        this.sides = List.of(first, second);
        this.random = random;
    }

    public Side first() {
        return sides.get(0);
    }

    public Side second() {
        return sides.get(1);
    }

    public Side other(Side side) {
        return side == first() ? second() : first();
    }

    public Phase phase() {
        return phase;
    }

    public int phaseTicks() {
        return phaseTicks;
    }

    /** The bar as {@code side} sees it: 1 when they've pushed the other out. */
    public double bar(Side side) {
        return side == first() ? bar : -bar;
    }

    @Nullable
    public Side sideOf(UUID caster) {
        for (Side s : sides) if (s.caster.equals(caster)) return s;
        return null;
    }

    /** The side this barrier is (either side of it: its shell in the world or its inner space's sky). */
    @Nullable
    public Side sideOf(DominionEntity d) {
        for (Side s : sides) if (s.dominion == d || s.dominion.primary() == d.primary()) return s;
        return null;
    }
}

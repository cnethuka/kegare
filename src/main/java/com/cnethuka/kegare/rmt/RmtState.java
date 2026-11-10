package com.cnethuka.kegare.rmt;

import javax.annotation.Nullable;
import net.minecraft.world.phys.Vec3;

/**
 * Runtime only, per player (attachment {@code kegare:rmt_state}): the green orb waiting for them, if any (the server
 * rolled its route), how far along it they have followed it, and when a player last hit them.
 */
public final class RmtState {
    /** "Long ago" for a last-time field: far enough back to be past any wait, near enough that now minus it can't overflow. */
    static final long NEVER = -1_000_000_000L;
    @Nullable
    OrbPath path;
    /** Game time the orb fades; pushed back every tick of a follow, so it can't fade under them. */
    long orbExpires;
    int followTicks;
    /** The whole route, fixed when it's rolled. */
    int followTotal;
    long lastHitByPlayer = Long.MIN_VALUE;
    /** When the last heal pulse played: about once a second while it's actually healing. */
    long lastPulse = NEVER;
    /** Injury fatigue this fight (RmtFatigue), and until when damage adds none. */
    double fatigue;
    long fatigueIgnoredUntil = Long.MIN_VALUE;
    @javax.annotation.Nullable RmtOutputPayload sentOutput;
    /**
     * rmt_survived_low: they fell to lowHealth or less during their own channel. {@code survivedLowAt}: once healed above it, when
     * they must still be alive.
     */
    boolean lowDuringChannel;
    /** Healing another: who (by UUID), and when the link was last sent. */
    @Nullable java.util.UUID healing;
    long linkSentAt = NEVER;
    /** The moment: who glows for them, until when, and when they last rolled for one. */
    @Nullable java.util.UUID momentTarget;
    long momentUntil;
    long lastMomentRoll = NEVER;
    long survivedLowAt = Long.MAX_VALUE;

    public boolean hasOrb() {
        return path != null;
    }

    /** Where the orb is now. */
    @Nullable
    public Vec3 orb() {
        return path == null ? null : path.at(followTicks / (double) followTotal);
    }

    @Nullable
    public OrbPath path() {
        return path;
    }

    public int followTicks() {
        return followTicks;
    }

    public int followTotal() {
        return followTotal;
    }

    void clearOrb() {
        path = null;
        followTicks = 0;
    }

    public long lastHitByPlayer() {
        return lastHitByPlayer;
    }
}

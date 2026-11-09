package com.cnethuka.kegare.rmt;

import javax.annotation.Nullable;
import net.minecraft.world.phys.Vec3;

/**
 * Runtime only, per player (attachment {@code kegare:rmt_state}): the green orb waiting for them, if any (the server
 * rolled its route), how far along it they have followed it, and when a player last hit them.
 */
public final class RmtState {
    @Nullable
    OrbPath path;
    /** Stays this far ahead while they follow it, so it can't fade under them. */
    long orbExpires;
    int followTicks;
    /** The whole route, fixed when it's rolled. */
    int followTotal;
    long lastHitByPlayer = Long.MIN_VALUE;
    /** When the last heal pulse played: about once a second while it's actually healing. */
    long lastPulse = Long.MIN_VALUE;
    /** Injury fatigue this fight (RmtFatigue), and until when damage adds none. */
    double fatigue;
    long fatigueIgnoredUntil = Long.MIN_VALUE;
    @javax.annotation.Nullable RmtOutputPayload sentOutput;

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

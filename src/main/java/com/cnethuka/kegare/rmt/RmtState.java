package com.cnethuka.kegare.rmt;

import javax.annotation.Nullable;
import net.minecraft.world.phys.Vec3;

/**
 * Runtime only, per player (attachment {@code kegare:rmt_state}): the green orb waiting for them, if any (the server
 * picked its position), how long they have kept looking at it, and when a player last hit them.
 */
public final class RmtState {
    @Nullable
    Vec3 orb;
    long orbExpires;
    int lookTicks;
    long lastHitByPlayer = Long.MIN_VALUE;

    public boolean hasOrb() {
        return orb != null;
    }

    @Nullable
    public Vec3 orb() {
        return orb;
    }

    public long lastHitByPlayer() {
        return lastHitByPlayer;
    }
}

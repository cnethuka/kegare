package com.cnethuka.kegare.severed;

import javax.annotation.Nullable;
import net.minecraft.world.phys.Vec3;

/** A Severed player's runtime state (attachment {@code kegare:severed}), in level game time. Gone on death and logout. */
public final class SeveredState {
    // Blur
    boolean blur;
    long windedUntil;
    /** Where they were the last few ticks (newest last) and how fast they went each tick, horizontally. */
    final Vec3[] positions = new Vec3[8];
    final double[] speeds = new double[8];
    int samples;
    long crashedAt = -100;
    long windTick;
    // Closing Line
    long closingReadyAt;
    long recoveryUntil;
    @Nullable Vec3 rushFrom;
    @Nullable Vec3 rushDirection;
    double rushDistance;
    double rushSpeed;
    int rushTicksLeft;
    int rushTarget = -1;
    long rushAllowedUntil;
    // What the owner's client was last told
    @Nullable BlurPayload sent;

    public boolean blurring() {
        return blur;
    }

    /** Horizontal speed (blocks/tick) {@code ticksAgo} ticks ago (0 = the last tick), or 0 if not known. */
    double speed(int ticksAgo) {
        return ticksAgo < samples ? speeds[(samples - 1 - ticksAgo) % speeds.length] : 0;
    }

    @Nullable
    Vec3 position(int ticksAgo) {
        return ticksAgo < samples ? positions[(samples - 1 - ticksAgo) % positions.length] : null;
    }

    void record(Vec3 at, double speed) {
        positions[samples % positions.length] = at;
        speeds[samples % speeds.length] = speed;
        samples++;
    }

    void forgetMotion() {
        samples = 0;
    }
}

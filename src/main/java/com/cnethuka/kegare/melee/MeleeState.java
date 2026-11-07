package com.cnethuka.kegare.melee;

import javax.annotation.Nullable;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Runtime only (attachment {@code kegare:melee}), all in level game time. Gone on death and logout, like a cast in progress. */
public final class MeleeState {
    // Dash
    long dashReadyAt;
    @Nullable Vec3 dashDirection;
    double dashDistance;
    int dashTicksLeft;
    // Clean dodge: where they were, until when
    @Nullable AABB ghost;
    long ghostUntil;
    // Parry
    boolean guarding;
    long guardStart;
    /** How charged their last swing was, and when: the attack has reset the charge by the time its hit is handled. */
    float swingCharge;
    long swingTick = -1;
    long recoveryUntil;
    boolean parriedThisGuard;
    long guardLoopAt;
    // Reinforced Strike
    long strikeUntil;
    int strikeTicks;
    // What the tell last looked like, to the player themself and to everyone else
    MeleeTell.Look tellOwn = MeleeTell.Look.NONE;
    MeleeTell.Look tellOthers = MeleeTell.Look.NONE;
    // What the owner's HUD last showed
    @Nullable MeleeHudPayload sent;

    public boolean isGuarding() {
        return guarding;
    }
}

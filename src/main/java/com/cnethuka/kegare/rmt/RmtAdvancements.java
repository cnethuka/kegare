package com.cnethuka.kegare.rmt;

import net.minecraft.server.level.ServerPlayer;

/** Reverse Malice Technique's advancements (filled in with the advancements part). */
public final class RmtAdvancements {
    private RmtAdvancements() {}

    /** An RMT channel just started. */
    static void onCastStart(ServerPlayer player) {}

    /** The green orb was taken in: RMT unlocked. */
    static void onUnlock(ServerPlayer player) {}
}

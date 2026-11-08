package com.cnethuka.kegare.severed;

import net.minecraft.server.level.ServerPlayer;

/** Closing Line (R during Blur, DESIGN §3.27). Built in its own step; until then nothing rushes. */
final class ClosingLine {
    private ClosingLine() {}

    static boolean rushing(SeveredState state) {
        return false;
    }

    /** How fast a rush in progress may go, blocks a tick (0: none). */
    static double allowedSpeed(SeveredState state) {
        return 0;
    }

    static void cancel(ServerPlayer player, SeveredState state) {}

    static void stoppedByWall(ServerPlayer player, SeveredState state) {}
}

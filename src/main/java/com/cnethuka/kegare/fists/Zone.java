package com.cnethuka.kegare.fists;

import net.minecraft.server.level.ServerPlayer;

/** The zone after a Black Spark (DESIGN §3.28). */
public final class Zone {
    private Zone() {}

    public static boolean in(ServerPlayer player) {
        return false;
    }
}

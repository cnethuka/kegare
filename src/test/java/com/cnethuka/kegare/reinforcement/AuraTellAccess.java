package com.cnethuka.kegare.reinforcement;

import net.minecraft.server.level.ServerPlayer;

/** Lets tests in other packages read the aura flags everyone sees on a player. */
public final class AuraTellAccess {
    private AuraTellAccess() {}

    public static int flags(ServerPlayer player) {
        return AuraTell.flags(player);
    }
}

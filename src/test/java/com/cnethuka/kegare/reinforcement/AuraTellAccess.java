package com.cnethuka.kegare.reinforcement;

import net.minecraft.server.level.ServerPlayer;

/** Tests elsewhere read what the aura shows on a player for everyone. */
public final class AuraTellAccess {
    private AuraTellAccess() {}

    public static int flags(ServerPlayer player) {
        return AuraTell.flags(player);
    }
}

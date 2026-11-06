package com.cnethuka.kegare.progression;

import net.minecraft.server.level.ServerPlayer;

/** One player's view of a finished fight. Both players get one. */
public record FightReport(ServerPlayer player, ServerPlayer opponent, Fight fight, FightOutcome outcome) {
    public Fight.Side side() {
        return fight.side(player.getUUID());
    }

    public Fight.Side opponentSide() {
        return fight.opponentOf(player.getUUID());
    }

    public double dealt() {
        return side().dealt();
    }

    public double taken() {
        return opponentSide().dealt();
    }
}

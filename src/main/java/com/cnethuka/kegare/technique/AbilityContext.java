package com.cnethuka.kegare.technique;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Everything an ability needs when it runs.
 *
 * @param charge      CHARGED: 0..1 fraction of full charge (also while charging); others: 1
 * @param activeTicks ticks since the charge/channel/toggle started; 0 for INSTANT
 */
public record AbilityContext(ServerPlayer player, Technique technique, Ability ability, CastSlot slot, float charge, int activeTicks) {
    public ServerLevel level() {
        return player.serverLevel();
    }
}

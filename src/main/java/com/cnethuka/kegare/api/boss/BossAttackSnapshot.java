package com.cnethuka.kegare.api.boss;

import net.minecraft.resources.ResourceLocation;

/** Aggregate metrics for all executions of one attack ID in an encounter. */
public record BossAttackSnapshot(
        ResourceLocation attackId,
        long starts,
        long completions,
        long cancellations,
        BossDamageSnapshot damage) {
}

package com.cnethuka.kegare.api.boss;

import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;

/** Detached data for one attack started/ended notification. End tick and reason are unset while running. */
public record BossAttackExecutionSnapshot(
        UUID encounterId,
        UUID bossId,
        UUID attackInstanceId,
        ResourceLocation attackId,
        @Nullable UUID targetId,
        long startTick,
        long endTick,
        BossAttackState state,
        @Nullable BossAttackEndReason endReason) {
}

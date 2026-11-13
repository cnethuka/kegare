package com.cnethuka.kegare.api.boss;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;

/** Immutable, detached encounter data. No entity, world, registry or damage-source object is retained. */
public record BossFightSnapshot(
        UUID encounterId,
        UUID bossId,
        ResourceLocation bossTypeId,
        long startTick,
        long endTick,
        long durationTicks,
        ResourceLocation phaseId,
        List<PhaseChange> phaseHistory,
        @Nullable BossFightEndReason endReason,
        BossDamageSnapshot incomingDamage,
        BossDamageSnapshot outgoingDamage,
        Map<UUID, BossParticipantSnapshot> participants,
        Map<ResourceLocation, BossAttackSnapshot> attacks,
        @Nullable KillingBlow killingBlow,
        Map<ResourceLocation, Number> customMetrics) {
    public BossFightSnapshot {
        phaseHistory = List.copyOf(phaseHistory);
        participants = Map.copyOf(participants);
        attacks = Map.copyOf(attacks);
        customMetrics = Map.copyOf(customMetrics);
    }

    public record PhaseChange(ResourceLocation phaseId, long tick) {}

    public record KillingBlow(
            UUID playerId,
            @Nullable ResourceLocation abilityId,
            ResourceLocation damageTypeId,
            long tick) {}
}

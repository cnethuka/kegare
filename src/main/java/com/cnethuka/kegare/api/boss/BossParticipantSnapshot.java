package com.cnethuka.kegare.api.boss;

import java.util.Map;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;

/** A participant retained by UUID even after death or logout. Activity ticks are -1 until damaging or damaged. */
public record BossParticipantSnapshot(
        UUID playerId,
        long joinedTick,
        long firstActivityTick,
        long lastActivityTick,
        BossDamageSnapshot damageToBoss,
        BossDamageSnapshot damageTakenFromBoss,
        Map<ResourceLocation, BossDamageSnapshot> damageByAbility) {
    public BossParticipantSnapshot {
        damageByAbility = Map.copyOf(damageByAbility);
    }
}

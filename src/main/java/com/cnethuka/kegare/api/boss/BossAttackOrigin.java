package com.cnethuka.kegare.api.boss;

import java.util.UUID;
import net.minecraft.resources.ResourceLocation;

/** Runtime-only provenance for an entity spawned by a boss attack. Never serialized or copied on death. */
public record BossAttackOrigin(UUID encounterId, UUID bossId, UUID attackInstanceId, ResourceLocation attackId) {
    public static final BossAttackOrigin NONE = new BossAttackOrigin(
            new UUID(0, 0), new UUID(0, 0), new UUID(0, 0), ResourceLocation.fromNamespaceAndPath("kegare", "none"));
}

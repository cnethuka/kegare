package com.cnethuka.kegare.api.boss;

import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/** One live encounter. All mutating methods are server-side and become no-ops after it ends. */
public interface BossFight {
    UUID id();

    UUID bossId();

    boolean isActive();

    BossAttackController attacks();

    void join(ServerPlayer player);

    boolean isParticipant(UUID playerId);

    void setPhase(ResourceLocation phaseId);

    BossFightSnapshot snapshot();

    <T extends Number> void set(BossMetric<T> metric, T value);

    <T extends Number> void increment(BossMetric<T> metric, T amount);

    /** Returns zero of the metric's type when it has not been set. */
    <T extends Number> T get(BossMetric<T> metric);
}

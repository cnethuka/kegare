package com.cnethuka.kegare.api.boss;

import com.cnethuka.kegare.boss.BossRuntime;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.LivingEntity;

/** Entry point for the runtime-only boss encounter service. */
public final class BossFights {
    private BossFights() {}

    /** Starts the one active encounter allowed for this {@code c:bosses} entity. */
    public static BossFight start(LivingEntity boss, ResourceLocation initialPhase) {
        return BossRuntime.start(boss, initialPhase);
    }

    public static Optional<BossFight> find(LivingEntity boss) {
        return BossRuntime.findFight(boss);
    }

    public static Optional<BossFight> find(UUID encounterId) {
        return BossRuntime.findFightById(encounterId);
    }

    /** Explicit administrative end. Prefer {@link #reset} when tearing down an addon encounter. */
    public static Optional<BossFightSnapshot> end(LivingEntity boss, BossFightEndReason reason) {
        return BossRuntime.end(boss.getUUID(), reason);
    }

    public static Optional<BossFightSnapshot> reset(LivingEntity boss) {
        return end(boss, BossFightEndReason.RESET);
    }

    /** Ends every active fight belonging to this server. Also useful for addon teardown. */
    public static void resetAll(MinecraftServer server) {
        BossRuntime.endAll(server, BossFightEndReason.RESET);
    }
}

package com.cnethuka.kegare.boss;

import com.cnethuka.kegare.Kegare;
import com.cnethuka.kegare.api.boss.BossEncounterEvent;
import com.cnethuka.kegare.api.boss.BossFightEndReason;
import com.cnethuka.kegare.api.boss.BossFightSnapshot;
import com.cnethuka.kegare.combat.CombatHelper;
import com.cnethuka.kegare.progression.Attribution;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.common.NeoForge;

/** Active-fight registry. Runtime only: encounter reports leave it solely through immutable events/snapshots. */
public final class BossRuntime {
    private static final Map<UUID, BossFightImpl> BY_BOSS = new HashMap<>();
    private static final Map<UUID, BossFightImpl> BY_ENCOUNTER = new HashMap<>();
    private static final Set<MinecraftServer> STOPPING = Collections.newSetFromMap(new IdentityHashMap<>());

    private BossRuntime() {}

    public static BossFightImpl start(LivingEntity boss, ResourceLocation initialPhase) {
        Objects.requireNonNull(boss);
        Objects.requireNonNull(initialPhase);
        if (!(boss.level() instanceof ServerLevel level)) throw new IllegalArgumentException("boss encounters are server-side");
        if (STOPPING.contains(level.getServer())) throw new IllegalStateException("cannot start an encounter while its server is stopping");
        if (!CombatHelper.isBoss(boss)) throw new IllegalArgumentException("boss is not in c:bosses");
        if (!boss.isAlive()) throw new IllegalArgumentException("cannot start an encounter for a dead boss");
        if (BY_BOSS.containsKey(boss.getUUID())) throw new IllegalStateException("boss already has an active encounter");
        BossFightImpl fight = new BossFightImpl(level, boss, initialPhase);
        BY_BOSS.put(boss.getUUID(), fight);
        BY_ENCOUNTER.put(fight.id(), fight);
        NeoForge.EVENT_BUS.post(new BossEncounterEvent.Started(fight.snapshot()));
        return fight;
    }

    public static Optional<BossFightImpl> find(UUID bossId) {
        return Optional.ofNullable(BY_BOSS.get(bossId));
    }

    public static Optional<BossFightImpl> findEncounter(UUID encounterId) {
        return Optional.ofNullable(BY_ENCOUNTER.get(encounterId));
    }

    public static Optional<com.cnethuka.kegare.api.boss.BossFight> findFight(LivingEntity boss) {
        return find(boss.getUUID()).map(fight -> fight);
    }

    public static Optional<com.cnethuka.kegare.api.boss.BossFight> findFightById(UUID encounterId) {
        return findEncounter(encounterId).map(fight -> fight);
    }

    public static Optional<BossFightSnapshot> end(UUID bossId, BossFightEndReason reason) {
        Objects.requireNonNull(reason);
        BossFightImpl fight = BY_BOSS.get(bossId);
        return fight == null ? Optional.empty() : Optional.of(end(fight, reason, null));
    }

    static void defeated(BossFightImpl fight, DamageSource source) {
        if (!fight.isActive()) return;
        end(fight, BossFightEndReason.DEFEATED, source);
    }

    private static BossFightSnapshot end(
            BossFightImpl fight, BossFightEndReason reason, @Nullable DamageSource killingSource) {
        if (!fight.isActive()) return fight.snapshot();
        BossFightSnapshot.KillingBlow blow = reason == BossFightEndReason.DEFEATED
                ? killingBlow(fight, killingSource) : null;
        // Remove first: removal events, callbacks and repeated teardown cannot produce a second report.
        BY_BOSS.remove(fight.bossId(), fight);
        BY_ENCOUNTER.remove(fight.id(), fight);
        fight.markEnded(reason, blow);
        BossFightSnapshot snapshot = fight.snapshot();
        NeoForge.EVENT_BUS.post(new BossEncounterEvent.Ended(snapshot));
        return snapshot;
    }

    @Nullable
    private static BossFightSnapshot.KillingBlow killingBlow(BossFightImpl fight, @Nullable DamageSource source) {
        if (source == null) return null;
        Attribution.Hit hit = Attribution.of(source, fight.level().getServer());
        ServerPlayer killer = hit == null ? null : hit.attacker();
        if (killer == null && fight.boss().getKillCredit() instanceof ServerPlayer credited) killer = credited;
        if (killer == null || !fight.isParticipant(killer.getUUID())) return null;
        ResourceLocation ability = BossAttribution.abilityId(hit, source);
        return new BossFightSnapshot.KillingBlow(killer.getUUID(), ability,
                BossAttribution.damageType(fight.level(), source), fight.now());
    }

    public static void endAll(MinecraftServer server, BossFightEndReason reason) {
        for (BossFightImpl fight : new ArrayList<>(BY_ENCOUNTER.values())) {
            if (fight.level().getServer() == server && fight.isActive()) end(fight, reason, null);
        }
    }

    /** Server shutdown is best effort: every fight and pending capture is cleaned even if an addon listener fails. */
    static void stop(MinecraftServer server) {
        if (!STOPPING.add(server)) return;
        try {
            for (BossFightImpl fight : new ArrayList<>(BY_ENCOUNTER.values())) {
                if (fight.level().getServer() != server || !fight.isActive()) continue;
                try {
                    end(fight, BossFightEndReason.SERVER_STOP, null);
                } catch (RuntimeException e) {
                    Kegare.LOGGER.error("Boss encounter listener failed during server shutdown", e);
                }
            }
        } finally {
            STOPPING.remove(server);
            BossDamageTracker.clear();
        }
    }

    static void removed(LivingEntity boss) {
        BossFightImpl fight = BY_BOSS.get(boss.getUUID());
        if (fight != null) end(fight, BossFightEndReason.REMOVED, null);
    }

    static void tick(MinecraftServer server) {
        try {
            for (BossFightImpl fight : List.copyOf(BY_ENCOUNTER.values())) {
                if (fight.level().getServer() != server || !fight.isActive()) continue;
                try {
                    if (fight.boss().isRemoved()) {
                        end(fight, BossFightEndReason.REMOVED, null);
                    } else if (!fight.boss().isAlive()) {
                        end(fight, BossFightEndReason.DEFEATED, null);
                    } else {
                        fight.tick();
                    }
                } catch (RuntimeException e) {
                    Kegare.LOGGER.error("Boss encounter listener failed while ticking {}", fight.id(), e);
                }
            }
        } finally {
            // Damage Pre/Post is synchronous. An aborted hit must not retain its source or encounter beyond this tick.
            BossDamageTracker.clear();
        }
    }
}

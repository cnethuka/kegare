package com.cnethuka.kegare.boss;

import com.cnethuka.kegare.api.boss.BossAttackController;
import com.cnethuka.kegare.api.boss.BossAttackEndReason;
import com.cnethuka.kegare.api.boss.BossDamageEvent;
import com.cnethuka.kegare.api.boss.BossDamageSnapshot;
import com.cnethuka.kegare.api.boss.BossEncounterEvent;
import com.cnethuka.kegare.api.boss.BossFight;
import com.cnethuka.kegare.api.boss.BossFightEndReason;
import com.cnethuka.kegare.api.boss.BossFightSnapshot;
import com.cnethuka.kegare.api.boss.BossMetric;
import com.cnethuka.kegare.api.boss.BossMetricEvent;
import com.cnethuka.kegare.api.boss.BossParticipantSnapshot;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.common.NeoForge;

final class BossFightImpl implements BossFight {
    private final UUID id = UUID.randomUUID();
    private final ServerLevel level;
    private final LivingEntity boss;
    private final long startTick;
    private final List<BossFightSnapshot.PhaseChange> phases = new ArrayList<>();
    private final Map<UUID, BossStats.Participant> participants = new LinkedHashMap<>();
    private final Map<ResourceLocation, BossStats.Attack> attacks = new LinkedHashMap<>();
    private final Map<UUID, ResourceLocation> attackInstances = new LinkedHashMap<>();
    private final Map<ResourceLocation, Number> customMetrics = new LinkedHashMap<>();
    private final BossStats.Damage incoming = new BossStats.Damage();
    private final BossStats.Damage outgoing = new BossStats.Damage();
    private final BossAttackRunner runner = new BossAttackRunner(this);

    private boolean active = true;
    private long endTick = -1;
    @Nullable private BossFightEndReason endReason;
    @Nullable private BossFightSnapshot.KillingBlow killingBlow;

    BossFightImpl(ServerLevel level, LivingEntity boss, ResourceLocation initialPhase) {
        this.level = level;
        this.boss = boss;
        this.startTick = level.getGameTime();
        this.phases.add(new BossFightSnapshot.PhaseChange(initialPhase, startTick));
    }

    @Override
    public UUID id() {
        return id;
    }

    @Override
    public UUID bossId() {
        return boss.getUUID();
    }

    LivingEntity boss() {
        return boss;
    }

    ServerLevel level() {
        return level;
    }

    long now() {
        return level.getGameTime();
    }

    @Override
    public boolean isActive() {
        return active;
    }

    @Override
    public BossAttackController attacks() {
        return runner;
    }

    BossAttackRunner runner() {
        return runner;
    }

    @Override
    public void join(ServerPlayer player) {
        Objects.requireNonNull(player);
        if (!active || participants.containsKey(player.getUUID())) return;
        if (player.server != level.getServer()) throw new IllegalArgumentException("player belongs to another server");
        participants.put(player.getUUID(), new BossStats.Participant(player.getUUID(), now()));
    }

    @Override
    public boolean isParticipant(UUID playerId) {
        return participants.containsKey(playerId);
    }

    @Override
    public void setPhase(ResourceLocation phaseId) {
        Objects.requireNonNull(phaseId);
        if (!active || currentPhase().equals(phaseId)) return;
        ResourceLocation previous = currentPhase();
        phases.add(new BossFightSnapshot.PhaseChange(phaseId, now()));
        NeoForge.EVENT_BUS.post(new BossEncounterEvent.PhaseChanged(snapshot(), previous));
    }

    private ResourceLocation currentPhase() {
        return phases.get(phases.size() - 1).phaseId();
    }

    @Override
    public BossFightSnapshot snapshot() {
        long until = active ? now() : endTick;
        Map<UUID, BossParticipantSnapshot> participantSnapshots = new LinkedHashMap<>();
        participants.forEach((player, stats) -> participantSnapshots.put(player, stats.snapshot()));
        Map<ResourceLocation, com.cnethuka.kegare.api.boss.BossAttackSnapshot> attackSnapshots = new LinkedHashMap<>();
        attacks.forEach((attack, stats) -> attackSnapshots.put(attack, stats.snapshot()));
        return new BossFightSnapshot(id, bossId(), BuiltInRegistries.ENTITY_TYPE.getKey(boss.getType()), startTick,
                endTick, Math.max(0, until - startTick), currentPhase(), phases, endReason,
                incoming.snapshot(), outgoing.snapshot(), participantSnapshots, attackSnapshots, killingBlow, customMetrics);
    }

    @Override
    public <T extends Number> void set(BossMetric<T> metric, T value) {
        Objects.requireNonNull(metric);
        Objects.requireNonNull(value);
        if (!active) return;
        if (!metric.valueType().isInstance(value)) {
            throw new IllegalArgumentException(metric + " requires " + metric.valueType().getSimpleName());
        }
        Number previous = customMetrics.get(metric.id());
        if (previous != null && !metric.valueType().isInstance(previous)) {
            throw new IllegalStateException(metric.id() + " is already a " + previous.getClass().getSimpleName() + " metric");
        }
        customMetrics.put(metric.id(), value);
        NeoForge.EVENT_BUS.post(new BossMetricEvent(snapshot(), metric.id(), value));
    }

    @Override
    public <T extends Number> void increment(BossMetric<T> metric, T amount) {
        Objects.requireNonNull(metric);
        Objects.requireNonNull(amount);
        if (!active) return;
        if (!metric.valueType().isInstance(amount)) {
            throw new IllegalArgumentException(metric + " requires " + metric.valueType().getSimpleName());
        }
        T current = get(metric);
        if (metric.valueType() == Long.class) {
            @SuppressWarnings("unchecked") T sum = (T) Long.valueOf(Math.addExact(current.longValue(), amount.longValue()));
            set(metric, sum);
        } else if (metric.valueType() == Double.class) {
            @SuppressWarnings("unchecked") T sum = (T) Double.valueOf(current.doubleValue() + amount.doubleValue());
            set(metric, sum);
        } else {
            throw new IllegalArgumentException("unsupported metric type " + metric.valueType());
        }
    }

    @Override
    public <T extends Number> T get(BossMetric<T> metric) {
        Objects.requireNonNull(metric);
        Number value = customMetrics.get(metric.id());
        if (value == null) {
            // Keep this as statements: a Long/Double conditional expression numerically promotes both arms to Double.
            if (metric.valueType() == Long.class) return metric.valueType().cast(0L);
            return metric.valueType().cast(0D);
        }
        return metric.valueType().cast(value);
    }

    void attackStarted(UUID instanceId, ResourceLocation attackId) {
        attackInstances.put(instanceId, attackId);
        attacks.computeIfAbsent(attackId, BossStats.Attack::new).starts++;
    }

    void attackEnded(ResourceLocation attackId, BossAttackEndReason reason) {
        BossStats.Attack stats = attacks.computeIfAbsent(attackId, BossStats.Attack::new);
        if (reason == BossAttackEndReason.COMPLETED) stats.completions++;
        else stats.cancellations++;
    }

    void recordIncoming(
            @Nullable ServerPlayer attacker,
            @Nullable ResourceLocation abilityId,
            ResourceLocation damageTypeId,
            BossDamageSnapshot hit) {
        if (!active) return;
        long tick = now();
        UUID participantId = null;
        if (attacker != null) {
            join(attacker);
            participantId = attacker.getUUID();
            BossStats.Participant participant = participants.get(participantId);
            participant.activity(tick);
            participant.dealt.add(hit);
            if (abilityId != null) participant.abilities.computeIfAbsent(abilityId, id -> new BossStats.Damage()).add(hit);
        }
        incoming.add(hit);
        NeoForge.EVENT_BUS.post(new BossDamageEvent(snapshot(), BossDamageEvent.Direction.INCOMING, bossId(),
                BuiltInRegistries.ENTITY_TYPE.getKey(boss.getType()), participantId, abilityId, damageTypeId,
                null, null, hit, tick));
    }

    void recordOutgoing(
            LivingEntity target,
            @Nullable ServerPlayer participantPlayer,
            ResourceLocation damageTypeId,
            @Nullable ResourceLocation attackId,
            @Nullable UUID attackInstanceId,
            BossDamageSnapshot hit) {
        if (!active) return;
        long tick = now();
        if (participantPlayer != null) {
            join(participantPlayer);
            BossStats.Participant participant = participants.get(participantPlayer.getUUID());
            participant.activity(tick);
            participant.taken.add(hit);
        }
        outgoing.add(hit);
        if (attackId != null && attackInstanceId != null && attackId.equals(attackInstances.get(attackInstanceId))) {
            BossStats.Attack attack = attacks.get(attackId);
            if (attack != null) attack.damage.add(hit);
        } else {
            attackId = null;
            attackInstanceId = null;
        }
        UUID participantId = participantPlayer == null ? null : participantPlayer.getUUID();
        NeoForge.EVENT_BUS.post(new BossDamageEvent(snapshot(), BossDamageEvent.Direction.OUTGOING, target.getUUID(),
                BuiltInRegistries.ENTITY_TYPE.getKey(target.getType()), participantId, null, damageTypeId,
                attackId, attackInstanceId, hit, tick));
    }

    void markEnded(BossFightEndReason reason, @Nullable BossFightSnapshot.KillingBlow blow) {
        if (!active) return;
        endTick = now();
        endReason = reason;
        killingBlow = blow;
        active = false;
        runner.close();
    }

    void tick() {
        if (active) runner.tick();
    }
}

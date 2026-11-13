package com.cnethuka.kegare.boss;

import com.cnethuka.kegare.Kegare;
import com.cnethuka.kegare.api.boss.BossAttack;
import com.cnethuka.kegare.api.boss.BossAttackContext;
import com.cnethuka.kegare.api.boss.BossAttackController;
import com.cnethuka.kegare.api.boss.BossAttackEndReason;
import com.cnethuka.kegare.api.boss.BossAttackEvent;
import com.cnethuka.kegare.api.boss.BossAttackExecutionSnapshot;
import com.cnethuka.kegare.api.boss.BossAttackStartResult;
import com.cnethuka.kegare.api.boss.BossAttackState;
import com.cnethuka.kegare.api.boss.BossAttackTiming;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.common.NeoForge;

final class BossAttackRunner implements BossAttackController {
    private static final class Execution {
        final BossAttack attack;
        final BossAttackTiming timing;
        @Nullable final LivingEntity target;
        final UUID instanceId = UUID.randomUUID();
        final long startTick;
        long stateStartTick;
        long lastTick;
        BossAttackState state;

        Execution(BossAttack attack, BossAttackTiming timing, @Nullable LivingEntity target, long now) {
            this.attack = attack;
            this.timing = timing;
            this.target = target;
            this.startTick = now;
            this.lastTick = now;
            this.state = timing.windupTicks() > 0 ? BossAttackState.WINDUP
                    : timing.activeTicks() > 0 ? BossAttackState.ACTIVE : BossAttackState.RECOVERY;
            this.stateStartTick = now;
        }
    }

    private final BossFightImpl fight;
    private final Map<ResourceLocation, Long> cooldowns = new HashMap<>();
    @Nullable private Execution active;

    BossAttackRunner(BossFightImpl fight) {
        this.fight = fight;
    }

    @Override
    public BossAttackStartResult start(BossAttack attack, @Nullable LivingEntity target) {
        Objects.requireNonNull(attack);
        ResourceLocation attackId = Objects.requireNonNull(attack.id(), "attack.id()");
        BossAttackTiming timing = Objects.requireNonNull(attack.timing(), "attack.timing()");
        if (!fight.isActive()) return BossAttackStartResult.ENCOUNTER_ENDED;
        if (active != null) return BossAttackStartResult.ATTACK_ALREADY_RUNNING;
        long now = fight.now();
        if (now < cooldowns.getOrDefault(attackId, Long.MIN_VALUE)) return BossAttackStartResult.ON_COOLDOWN;
        cooldowns.remove(attackId);
        Execution execution = new Execution(attack, timing, target, now);
        active = execution;
        fight.attackStarted(execution.instanceId, attackId);
        try {
            NeoForge.EVENT_BUS.post(new BossAttackEvent.Started(fight.snapshot(), executionSnapshot(execution, -1, null)));
            // A listener may cancel the attack or end its encounter. Never start an already-ended execution.
            if (active == execution && fight.isActive()) attack.onStart(context(execution, now));
        } catch (RuntimeException e) {
            Kegare.LOGGER.error("Boss attack {} failed during startup; cancelling it", attackId, e);
            if (active == execution) finish(execution, BossAttackEndReason.CANCELLED);
        }
        return BossAttackStartResult.STARTED;
    }

    @Override
    public void tick() {
        Execution execution = active;
        if (execution == null) return;
        long now = fight.now();
        if (now <= execution.lastTick) return;
        execution.lastTick = now;
        long elapsed = Math.max(0, now - execution.startTick);
        BossAttackTiming timing = execution.timing;
        if (elapsed >= timing.totalTicks()) {
            execution.state = BossAttackState.RECOVERY;
            execution.stateStartTick = execution.startTick + timing.windupTicks() + timing.activeTicks();
            finish(execution, BossAttackEndReason.COMPLETED);
            return;
        }
        BossAttackState next;
        long stateStart;
        if (elapsed < timing.windupTicks()) {
            next = BossAttackState.WINDUP;
            stateStart = execution.startTick;
        } else if (elapsed < timing.windupTicks() + timing.activeTicks()) {
            next = BossAttackState.ACTIVE;
            stateStart = execution.startTick + timing.windupTicks();
        } else {
            next = BossAttackState.RECOVERY;
            stateStart = execution.startTick + timing.windupTicks() + timing.activeTicks();
        }
        execution.state = next;
        execution.stateStartTick = stateStart;
        try {
            execution.attack.onTick(context(execution, now));
        } catch (RuntimeException e) {
            Kegare.LOGGER.error("Boss attack {} failed while ticking; cancelling it", execution.attack.id(), e);
            finish(execution, BossAttackEndReason.CANCELLED);
        }
    }

    @Override
    public boolean cancel() {
        if (active == null) return false;
        finish(active, BossAttackEndReason.CANCELLED);
        return true;
    }

    void close() {
        if (active != null) finish(active, BossAttackEndReason.ENCOUNTER_ENDED);
        cooldowns.clear();
    }

    private void finish(Execution execution, BossAttackEndReason reason) {
        if (active != execution) return;
        active = null;
        long now = fight.now();
        cooldowns.put(execution.attack.id(), now + execution.timing.cooldownTicks());
        fight.attackEnded(execution.attack.id(), reason);
        BossAttackContext context = context(execution, now);
        try {
            execution.attack.onEnd(context, reason);
        } catch (RuntimeException e) {
            Kegare.LOGGER.error("Boss attack {} failed during end cleanup", execution.attack.id(), e);
        }
        NeoForge.EVENT_BUS.post(new BossAttackEvent.Ended(fight.snapshot(), executionSnapshot(execution, now, reason)));
    }

    private BossAttackContext context(Execution execution, long now) {
        return new AttackContextImpl(fight.level(), fight.boss(), execution.target, fight, execution.attack.id(),
                execution.instanceId, now, Math.max(0, now - execution.startTick),
                Math.max(0, now - execution.stateStartTick), execution.state);
    }

    private BossAttackExecutionSnapshot executionSnapshot(
            Execution execution, long endTick, @Nullable BossAttackEndReason reason) {
        return new BossAttackExecutionSnapshot(fight.id(), fight.bossId(), execution.instanceId, execution.attack.id(),
                execution.target == null ? null : execution.target.getUUID(), execution.startTick, endTick,
                execution.state, reason);
    }

    DirectAttack directAttack() {
        Execution execution = active;
        return execution == null ? null : new DirectAttack(execution.attack.id(), execution.instanceId);
    }

    @Override
    public Optional<ResourceLocation> activeAttack() {
        return active == null ? Optional.empty() : Optional.of(active.attack.id());
    }

    @Override
    public Optional<UUID> activeAttackInstance() {
        return active == null ? Optional.empty() : Optional.of(active.instanceId);
    }

    @Override
    public int cooldownTicksRemaining(ResourceLocation attackId) {
        long remaining = cooldowns.getOrDefault(attackId, fight.now()) - fight.now();
        if (remaining <= 0) {
            cooldowns.remove(attackId);
            return 0;
        }
        return (int) Math.min(Integer.MAX_VALUE, remaining);
    }

    record DirectAttack(ResourceLocation attackId, UUID attackInstanceId) {}
}

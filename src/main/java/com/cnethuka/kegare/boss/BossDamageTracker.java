package com.cnethuka.kegare.boss;

import com.cnethuka.kegare.api.boss.BossDamageSnapshot;
import com.cnethuka.kegare.progression.Attribution;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;

/** Joins Pre's raw amount/current health to Post's final amount without using aggregate impacts. */
final class BossDamageTracker {
    private static final Map<UUID, Deque<Capture>> PENDING = new HashMap<>();

    private record Capture(
            DamageSource source,
            double raw,
            double healthBefore,
            @Nullable BossFightImpl incoming,
            @Nullable BossAttribution.Outgoing outgoing) {}

    private BossDamageTracker() {}

    static void pre(LivingDamageEvent.Pre event) {
        LivingEntity victim = event.getEntity();
        if (!(victim.level() instanceof ServerLevel)) return;
        BossFightImpl incoming = BossRuntime.find(victim.getUUID())
                .filter(fight -> fight.boss() == victim).orElse(null);
        BossAttribution.Outgoing outgoing = BossAttribution.outgoing(victim, event.getSource());
        if (outgoing != null && outgoing.fight() == incoming) outgoing = null; // the boss hurting itself is not outgoing damage
        if (incoming == null && outgoing == null) return;
        Capture capture = new Capture(event.getSource(), Math.max(0, event.getOriginalDamage()), victim.getHealth(), incoming, outgoing);
        PENDING.computeIfAbsent(victim.getUUID(), id -> new ArrayDeque<>()).push(capture);
    }

    static void post(LivingDamageEvent.Post event) {
        LivingEntity victim = event.getEntity();
        if (!(victim.level() instanceof ServerLevel level)) return;
        Capture capture = take(victim, event.getSource());
        if (capture == null) return;
        double raw = capture.raw();
        double post = Math.max(0, event.getNewDamage());
        if (raw <= 0 && post <= 0) return;
        double effective = Math.min(post, Math.max(0, capture.healthBefore()));
        BossDamageSnapshot hit = new BossDamageSnapshot(raw, post, effective, 1);
        var damageType = BossAttribution.damageType(level, event.getSource());

        if (capture.incoming() != null && capture.incoming().isActive()) {
            Attribution.Hit attributed = Attribution.of(event.getSource(), level.getServer());
            capture.incoming().recordIncoming(attributed == null ? null : attributed.attacker(),
                    BossAttribution.abilityId(attributed, event.getSource()), damageType, hit);
        }

        BossAttribution.Outgoing outgoing = capture.outgoing();
        if (outgoing != null && outgoing.fight().isActive()) {
            ServerPlayer participant = victim instanceof ServerPlayer player ? player : null;
            outgoing.fight().recordOutgoing(victim, participant, damageType,
                    outgoing.attackId(), outgoing.attackInstanceId(), hit);
        }
    }

    @Nullable
    private static Capture take(LivingEntity victim, DamageSource source) {
        Deque<Capture> stack = PENDING.get(victim.getUUID());
        if (stack == null) return null;
        Capture found = null;
        // Post does not expose Pre's DamageContainer. Match the newest pending hit with the same source instead:
        // ordinary and properly nested hits are LIFO, while a different source can still complete out of order.
        for (Iterator<Capture> iterator = stack.iterator(); iterator.hasNext();) {
            Capture candidate = iterator.next();
            if (candidate.source() == source) {
                found = candidate;
                iterator.remove();
                break;
            }
        }
        if (stack.isEmpty()) PENDING.remove(victim.getUUID());
        return found;
    }

    static void clear() {
        PENDING.clear();
    }
}

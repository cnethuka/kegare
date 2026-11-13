package com.cnethuka.kegare.boss;

import com.cnethuka.kegare.api.boss.BossAttackSnapshot;
import com.cnethuka.kegare.api.boss.BossDamageSnapshot;
import com.cnethuka.kegare.api.boss.BossParticipantSnapshot;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;

final class BossStats {
    private BossStats() {}

    static final class Damage {
        private double raw;
        private double post;
        private double effective;
        private long hits;

        void add(BossDamageSnapshot hit) {
            raw += hit.raw();
            post += hit.postMitigation();
            effective += hit.effective();
            hits += hit.hits();
        }

        BossDamageSnapshot snapshot() {
            return new BossDamageSnapshot(raw, post, effective, hits);
        }
    }

    static final class Participant {
        final UUID playerId;
        final long joinedTick;
        long firstActivity = -1;
        long lastActivity = -1;
        final Damage dealt = new Damage();
        final Damage taken = new Damage();
        final Map<ResourceLocation, Damage> abilities = new LinkedHashMap<>();

        Participant(UUID playerId, long joinedTick) {
            this.playerId = playerId;
            this.joinedTick = joinedTick;
        }

        void activity(long tick) {
            if (firstActivity < 0) firstActivity = tick;
            lastActivity = Math.max(lastActivity, tick);
        }

        BossParticipantSnapshot snapshot() {
            Map<ResourceLocation, BossDamageSnapshot> damage = new LinkedHashMap<>();
            abilities.forEach((id, value) -> damage.put(id, value.snapshot()));
            return new BossParticipantSnapshot(playerId, joinedTick, firstActivity, lastActivity,
                    dealt.snapshot(), taken.snapshot(), damage);
        }
    }

    static final class Attack {
        final ResourceLocation attackId;
        long starts;
        long completions;
        long cancellations;
        final Damage damage = new Damage();

        Attack(ResourceLocation attackId) {
            this.attackId = attackId;
        }

        BossAttackSnapshot snapshot() {
            return new BossAttackSnapshot(attackId, starts, completions, cancellations, damage.snapshot());
        }
    }
}

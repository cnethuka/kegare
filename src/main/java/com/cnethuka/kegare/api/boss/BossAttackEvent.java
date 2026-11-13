package com.cnethuka.kegare.api.boss;

import net.neoforged.bus.api.Event;

/** Attack notifications with detached execution and encounter snapshots. */
public abstract class BossAttackEvent extends Event {
    private final BossFightSnapshot encounter;
    private final BossAttackExecutionSnapshot attack;

    private BossAttackEvent(BossFightSnapshot encounter, BossAttackExecutionSnapshot attack) {
        this.encounter = encounter;
        this.attack = attack;
    }

    public final BossFightSnapshot getEncounter() {
        return encounter;
    }

    public final BossAttackExecutionSnapshot getAttack() {
        return attack;
    }

    public static final class Started extends BossAttackEvent {
        public Started(BossFightSnapshot encounter, BossAttackExecutionSnapshot attack) {
            super(encounter, attack);
        }
    }

    public static final class Ended extends BossAttackEvent {
        public Ended(BossFightSnapshot encounter, BossAttackExecutionSnapshot attack) {
            super(encounter, attack);
        }
    }
}

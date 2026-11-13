package com.cnethuka.kegare.api.boss;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.Event;

/** Immutable encounter notifications posted on {@code NeoForge.EVENT_BUS}. Not cancellable. */
public abstract class BossEncounterEvent extends Event {
    private final BossFightSnapshot encounter;

    private BossEncounterEvent(BossFightSnapshot encounter) {
        this.encounter = encounter;
    }

    public final BossFightSnapshot getEncounter() {
        return encounter;
    }

    public static final class Started extends BossEncounterEvent {
        public Started(BossFightSnapshot encounter) {
            super(encounter);
        }
    }

    public static final class Ended extends BossEncounterEvent {
        public Ended(BossFightSnapshot encounter) {
            super(encounter);
        }
    }

    public static final class PhaseChanged extends BossEncounterEvent {
        private final ResourceLocation previousPhase;

        public PhaseChanged(BossFightSnapshot encounter, ResourceLocation previousPhase) {
            super(encounter);
            this.previousPhase = previousPhase;
        }

        public ResourceLocation getPreviousPhase() {
            return previousPhase;
        }

        public ResourceLocation getPhase() {
            return getEncounter().phaseId();
        }
    }
}

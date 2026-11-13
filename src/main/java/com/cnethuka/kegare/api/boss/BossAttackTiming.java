package com.cnethuka.kegare.api.boss;

/** Attack phases and the cooldown started when the attack ends. Values are server ticks. */
public record BossAttackTiming(int windupTicks, int activeTicks, int recoveryTicks, int cooldownTicks) {
    public BossAttackTiming {
        if (windupTicks < 0 || activeTicks < 0 || recoveryTicks < 0 || cooldownTicks < 0) {
            throw new IllegalArgumentException("attack phase and cooldown ticks must be non-negative");
        }
        if (windupTicks == 0 && activeTicks == 0 && recoveryTicks == 0) {
            throw new IllegalArgumentException("an attack needs at least one windup, active or recovery tick");
        }
    }

    public int totalTicks() {
        return Math.addExact(Math.addExact(windupTicks, activeTicks), recoveryTicks);
    }
}

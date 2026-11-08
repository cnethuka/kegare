package com.cnethuka.kegare.innate_technique.time_manipulation;

import com.cnethuka.kegare.innatetechnique.Ability;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;

/**
 * Fired on {@code NeoForge.EVENT_BUS} (server side) before Time Manipulation reaches into someone's own time: before a Rewind snaps
 * them back, and before Decelerate slows their cooldowns (its damage and Slowness land either way). Cancel it to make the target
 * immune: a Rewind then fizzles (its Malice stays spent, no cooldown starts); Decelerate's hit just doesn't slow their cooldowns.
 * Time Manipulation itself never cancels it: immunities belong to whoever listens (a future innate technique).
 */
public class TimeTargetEvent extends Event implements ICancellableEvent {
    private final ServerPlayer caster;
    private final LivingEntity target;
    private final Ability ability;

    public TimeTargetEvent(ServerPlayer caster, LivingEntity target, Ability ability) {
        this.caster = caster;
        this.target = target;
        this.ability = ability;
    }

    public ServerPlayer getCaster() {
        return caster;
    }

    public LivingEntity getTarget() {
        return target;
    }

    /** Rewind, or Decelerate. */
    public Ability getAbility() {
        return ability;
    }
}

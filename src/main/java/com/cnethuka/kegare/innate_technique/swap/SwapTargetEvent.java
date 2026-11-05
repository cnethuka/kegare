package com.cnethuka.kegare.innate_technique.swap;

import com.cnethuka.kegare.innatetechnique.Ability;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;

/**
 * Fired on {@code NeoForge.EVENT_BUS} (server side) before ANY Swap ability acts on something: every position swap
 * (both ends of Switchback included), the Anchor recall (the target is the anchor), Send Off, both disguises,
 * Exchange, Momentum Exchange and Total Swap.
 * <p>
 * Cancel it to make the target unswappable. The cast then fizzles: its Malice stays spent, no cooldown starts.
 * Swap itself never cancels it: immunities belong to whoever listens (a future innate technique).
 */
public class SwapTargetEvent extends Event implements ICancellableEvent {
    private final ServerPlayer caster;
    private final Entity target;
    private final Ability ability;

    public SwapTargetEvent(ServerPlayer caster, Entity target, Ability ability) {
        this.caster = caster;
        this.target = target;
        this.ability = ability;
    }

    public ServerPlayer getCaster() {
        return caster;
    }

    /** Usually a player; the anchor entity for an Anchor recall. */
    public Entity getTarget() {
        return target;
    }

    /** The ability (or special, or ultimate) about to act. */
    public Ability getAbility() {
        return ability;
    }
}

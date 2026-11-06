package com.cnethuka.kegare.innate_technique.puppet_manipulation;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;

/**
 * Fired on {@code NeoForge.EVENT_BUS} (server side) once per target when a string's second stage fires: Reel In and Zip
 * for the attached target, Tie and Leash for each of the two. Cancel it and that target isn't moved (a cancelled Zip
 * doesn't move the caster either: the target refuses). If nothing is left to move the cast fizzles like a refused swap:
 * its Malice stays spent, no cooldown starts, and the string is gone. Strings itself never cancels it: immunities belong
 * to whoever listens.
 */
public class StringsTargetEvent extends Event implements ICancellableEvent {
    private final ServerPlayer caster;
    private final LivingEntity target;
    private final StringsVariation variation;

    public StringsTargetEvent(ServerPlayer caster, LivingEntity target, StringsVariation variation) {
        this.caster = caster;
        this.target = target;
        this.variation = variation;
    }

    public ServerPlayer getCaster() {
        return caster;
    }

    public LivingEntity getTarget() {
        return target;
    }

    public StringsVariation getVariation() {
        return variation;
    }
}

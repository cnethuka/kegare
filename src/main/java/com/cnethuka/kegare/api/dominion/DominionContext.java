package com.cnethuka.kegare.api.dominion;

import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/** An open Dominion, as its {@link DominionUltimate} sees it (server side). */
public interface DominionContext {
    ServerPlayer caster();

    ServerLevel level();

    Vec3 centre();

    double radius();

    /** Ticks since the barrier closed. */
    int ticksOpen();

    /** Everything living trapped inside right now, its caster included. */
    List<LivingEntity> inside();

    /**
     * Who a sure-hit reaches right now: everything trapped inside but its caster and what the caster owns. Severed players are never found
     * (they have no Malice for it to find). Empty while a clash has the sure-hit cancelled, or for a Dominion whose {@code sureHit()} is false.
     */
    List<LivingEntity> sureHitTargets();
}

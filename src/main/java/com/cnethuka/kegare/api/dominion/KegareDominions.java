package com.cnethuka.kegare.api.dominion;

import com.cnethuka.kegare.dominion.DominionEntity;
import com.cnethuka.kegare.dominion.Dominions;
import java.util.List;
import java.util.Optional;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/** Queries about open Dominions, for addons (server side). */
public final class KegareDominions {
    private KegareDominions() {}

    /** The Dominion this player has open (or opening, or clashing), if any. */
    public static Optional<DominionContext> of(ServerPlayer caster) {
        return Dominions.ownedBy(caster).map(DominionEntity::context);
    }

    /** Whether this entity is trapped inside someone's closed Dominion. */
    public static boolean isTrapped(LivingEntity entity) {
        return Dominions.trapping(entity).isPresent();
    }

    /** Who a sure-hit from this caster's open Dominion reaches right now: empty if they have none (see {@link DominionContext#sureHitTargets}). */
    public static List<LivingEntity> sureHitTargets(ServerPlayer caster) {
        return of(caster).map(DominionContext::sureHitTargets).orElse(List.of());
    }
}

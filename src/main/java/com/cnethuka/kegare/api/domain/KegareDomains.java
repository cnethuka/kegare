package com.cnethuka.kegare.api.domain;

import com.cnethuka.kegare.domain.DomainEntity;
import com.cnethuka.kegare.domain.Domains;
import java.util.List;
import java.util.Optional;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/** Queries about open Domains, for addons (server side). */
public final class KegareDomains {
    private KegareDomains() {}

    /** The Domain this player has open (or opening, or clashing), if any. */
    public static Optional<DomainContext> of(ServerPlayer caster) {
        return Domains.ownedBy(caster).map(DomainEntity::context);
    }

    /** Whether this entity is trapped inside someone's closed Domain. */
    public static boolean isTrapped(LivingEntity entity) {
        return Domains.trapping(entity).isPresent();
    }

    /** Who a sure-hit from this caster's open Domain reaches right now: empty if they have none (see {@link DomainContext#sureHitTargets}). */
    public static List<LivingEntity> sureHitTargets(ServerPlayer caster) {
        return of(caster).map(DomainContext::sureHitTargets).orElse(List.of());
    }
}

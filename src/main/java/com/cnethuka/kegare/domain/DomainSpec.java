package com.cnethuka.kegare.domain;

import com.cnethuka.kegare.api.domain.DomainContext;
import com.cnethuka.kegare.api.domain.DomainLook;
import net.minecraft.resources.ResourceLocation;

/**
 * What a Domain about to open is (DESIGN §3.35): the ability opening it, full or Incomplete, its barrier's size, duration and strength,
 * the Burnout and meter it costs, what its inside looks like, whether it has a sure-hit, and what it does while open.
 *
 * @param ability       the ability that opens it ({@code kegare:incomplete_domain}, or a technique's ultimate)
 * @param complete      a Domain Expansion, carrying its technique
 * @param innerRadius   how far its inner space reaches
 * @param meterNeeded   the share of the Domain Meter it takes, 0 to 1
 */
public record DomainSpec(ResourceLocation ability, boolean complete, double radius, int innerRadius, int durationTicks, double barrierStrength,
                           int burnoutTicks, double meterNeeded, DomainLook look, boolean sureHit, Hooks hooks) {

    /** What it does while open (a Domain Expansion's technique; the Incomplete Domain's caster boosts). Server side. */
    public interface Hooks {
        Hooks NONE = new Hooks() {};

        default void onOpen(DomainContext ctx) {}

        default void tickInside(DomainContext ctx) {}

        default void onClose(DomainContext ctx, boolean broken) {}

        /** Its inner space is being built: place what its technique adds, after the floor and ruins. */
        default void furnish(com.cnethuka.kegare.api.domain.DomainInterior interior) {}
    }
}

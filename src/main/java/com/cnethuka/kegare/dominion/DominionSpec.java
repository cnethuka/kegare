package com.cnethuka.kegare.dominion;

import com.cnethuka.kegare.api.dominion.DominionContext;
import com.cnethuka.kegare.api.dominion.DominionLook;
import net.minecraft.resources.ResourceLocation;

/**
 * What a Dominion about to open is (DESIGN §3.35): the ability opening it, full or Incomplete, its barrier's size, duration and strength,
 * the Burnout and meter it costs, what its inside looks like, whether it has a sure-hit, and what it does while open.
 *
 * @param ability       the ability that opens it ({@code kegare:incomplete_dominion}, or a technique's ultimate)
 * @param complete      a full Dominion, carrying its technique
 * @param innerRadius   how far its inner space reaches
 * @param meterNeeded   the share of the Dominion Meter it takes, 0 to 1
 */
public record DominionSpec(ResourceLocation ability, boolean complete, double radius, int innerRadius, int durationTicks, double barrierStrength,
                           int burnoutTicks, double meterNeeded, DominionLook look, boolean sureHit, Hooks hooks) {

    /** What it does while open (a full Dominion's technique; the Incomplete Dominion's caster boosts). Server side. */
    public interface Hooks {
        Hooks NONE = new Hooks() {};

        default void onOpen(DominionContext ctx) {}

        default void tickInside(DominionContext ctx) {}

        default void onClose(DominionContext ctx, boolean broken) {}

        /** Its inner space is being built: place what its technique adds, after the floor and ruins. */
        default void furnish(com.cnethuka.kegare.api.dominion.DominionInterior interior) {}
    }
}

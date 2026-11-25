/**
 * Dominions (DESIGN §3.35): a technique's ultimate as a full Dominion. Build the ultimate on {@link com.cnethuka.kegare.api.dominion.DominionUltimate}
 * and give it to {@code InnateTechnique.Builder.ultimate}; Kegare opens the barrier, traps whoever is inside, takes its hits, ends it and
 * applies its Burnout. What happens inside is yours ({@code tickInside}), with {@link com.cnethuka.kegare.api.dominion.KegareDominions} to find
 * who a sure-hit reaches.
 * <p>
 * <b>Experimental:</b> new in 0.2.x and still settling.
 */
@ApiStatus.Experimental
package com.cnethuka.kegare.api.dominion;

import org.jetbrains.annotations.ApiStatus;

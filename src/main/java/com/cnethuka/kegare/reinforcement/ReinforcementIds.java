package com.cnethuka.kegare.reinforcement;

import com.cnethuka.kegare.Kegare;
import net.minecraft.resources.ResourceLocation;

/** Reinforcement's ids (DESIGN §3.16). */
public final class ReinforcementIds {
    public static final ResourceLocation ABILITY = Kegare.id("reinforcement");
    /** The knockback resistance it adds while on. */
    public static final ResourceLocation KNOCKBACK_MODIFIER = Kegare.id("reinforcement");

    private ReinforcementIds() {}
}

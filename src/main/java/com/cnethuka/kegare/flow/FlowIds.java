package com.cnethuka.kegare.flow;

import com.cnethuka.kegare.Kegare;
import net.minecraft.resources.ResourceLocation;

/** Flow's ids (DESIGN §3.16). */
public final class FlowIds {
    public static final ResourceLocation ABILITY = Kegare.id("flow");
    /** The knockback resistance it adds while on. */
    public static final ResourceLocation KNOCKBACK_MODIFIER = Kegare.id("flow");
    /** The movement speed it adds while on. */
    public static final ResourceLocation SPEED_MODIFIER = Kegare.id("flow_speed");

    private FlowIds() {}
}

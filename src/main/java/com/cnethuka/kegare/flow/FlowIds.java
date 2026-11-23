package com.cnethuka.kegare.flow;

import com.cnethuka.kegare.Kegare;
import net.minecraft.resources.ResourceLocation;

/** Flow's ids (DESIGN §3.16). */
public final class FlowIds {
    public static final ResourceLocation ABILITY = Kegare.id("flow");
    /** The knockback resistance it adds while on. */
    public static final ResourceLocation KNOCKBACK_MODIFIER = Kegare.id("flow");
    /** Its aura (Photon, {@code fx/flow/}): the ability's, the same fuller in a fight, Background Flow's calm one, and your own first person's. */
    public static final ResourceLocation FX_AURA = Kegare.id("flow/aura");
    public static final ResourceLocation FX_AURA_FIGHT = Kegare.id("flow/aura_fight");
    public static final ResourceLocation FX_AURA_CALM = Kegare.id("flow/aura_calm");
    public static final ResourceLocation FX_AURA_SELF = Kegare.id("flow/aura_self");
    public static final ResourceLocation FX_AURA_CALM_SELF = Kegare.id("flow/aura_calm_self");
    /** The movement speed it adds while on. */
    public static final ResourceLocation SPEED_MODIFIER = Kegare.id("flow_speed");

    private FlowIds() {}
}

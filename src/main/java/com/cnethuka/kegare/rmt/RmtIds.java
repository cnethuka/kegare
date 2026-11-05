package com.cnethuka.kegare.rmt;

import com.cnethuka.kegare.Kegare;
import com.cnethuka.kegare.innatetechnique.visual.Visual;
import net.minecraft.resources.ResourceLocation;

/** Reverse Malice Technique's IDs: its animation (player_animations/rmt.json) and Photon effects (fx/rmt/). */
public final class RmtIds {
    public static final ResourceLocation ABILITY = Kegare.id("reverse_malice_technique");

    public static final ResourceLocation ANIM_CHANNEL = Kegare.id("rmt_channel");

    public static final ResourceLocation FX_CHANNEL_AURA = fx("rmt_channel_aura");
    public static final ResourceLocation FX_HEAL_PULSE = fx("rmt_heal_pulse");
    /** The green orb (client-only, its finder alone sees it) and its burst on unlock. */
    public static final ResourceLocation FX_ORB = fx("rmt_orb");
    public static final ResourceLocation FX_UNLOCK_BURST = fx("rmt_unlock_burst");

    public static final Visual HEAL_PULSE = Visual.effect(FX_HEAL_PULSE, Visual.Anchor.BODY);

    private RmtIds() {}

    private static ResourceLocation fx(String name) {
        return Kegare.id("rmt/" + name);
    }
}

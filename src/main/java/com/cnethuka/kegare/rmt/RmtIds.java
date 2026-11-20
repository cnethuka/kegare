package com.cnethuka.kegare.rmt;

import com.cnethuka.kegare.Kegare;
import com.cnethuka.kegare.api.visual.Visual;
import net.minecraft.resources.ResourceLocation;

/** Reverse Malice Technique's IDs: its player animation (player_animations/rmt.json) and its Photon effects (fx/rmt/). */
public final class RmtIds {
    public static final ResourceLocation ABILITY = Kegare.id("reverse_malice_technique");
    /** Its special: healing another player (no name of its own). */
    public static final ResourceLocation ON_ANOTHER = Kegare.id("reverse_malice_technique_on_another");
    /** The Unlocks framework's entry for knowing how. */
    public static final ResourceLocation ON_ANOTHER_UNLOCK = Kegare.id("rmt_heal_others");

    /** While it channels: the right fist held to the chest, over the heart (the light pours off that fist). */
    public static final ResourceLocation ANIM_CHANNEL = Kegare.id("rmt_channel");

    /** While it channels: white light burning off the fist held to the chest, near-black scraps rising off its edge. */
    public static final ResourceLocation FX_CHANNEL_AURA = fx("rmt_channel_aura");
    /** The same in your own first person: a few small, dim licks, so looking down never fills the view (RmtClient). */
    public static final ResourceLocation FX_CHANNEL_AURA_SELF = fx("rmt_channel_aura_self");
    public static final ResourceLocation FX_HEAL_PULSE = fx("rmt_heal_pulse");
    /** Background RMT, as a Six Eyes bearer sees it on anyone running it: small white licks coming and going over the body (AuraClient). */
    public static final ResourceLocation FX_BACKGROUND_SHIMMER = fx("background_rmt_shimmer");
    /** A heal in your own first person: only the rings on the ground (RmtClient). */
    public static final ResourceLocation FX_HEAL_PULSE_SELF = fx("rmt_heal_pulse_self");
    /** The green orb (client-only, its finder alone sees it), the dots marking its route, and its burst on unlock. */
    public static final ResourceLocation FX_ORB = fx("rmt_orb");
    public static final ResourceLocation FX_TRAIL_DOT = fx("rmt_trail_dot");
    public static final ResourceLocation FX_UNLOCK_BURST = fx("rmt_unlock_burst");

    public static final Visual HEAL_PULSE = Visual.effect(FX_HEAL_PULSE, Visual.Anchor.BODY);

    private RmtIds() {}

    private static ResourceLocation fx(String name) {
        return Kegare.id("rmt/" + name);
    }
}

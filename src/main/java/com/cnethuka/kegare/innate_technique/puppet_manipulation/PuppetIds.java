package com.cnethuka.kegare.innate_technique.puppet_manipulation;

import com.cnethuka.kegare.Kegare;
import com.cnethuka.kegare.innatetechnique.visual.Visual;
import net.minecraft.resources.ResourceLocation;

/**
 * Puppet Manipulation's IDs: player animations ({@code assets/kegare/player_animations/puppet_manipulation.json}) and
 * effects, which the client draws itself as lavender thread ({@code PuppetFx}; no Photon files).
 */
public final class PuppetIds {
    // Player Animation Library animations (on the caster)
    public static final ResourceLocation ANIM_CREATE_CHARGE = Kegare.id("puppet_create_charge");
    public static final ResourceLocation ANIM_CREATE_RELEASE = Kegare.id("puppet_create_release");
    public static final ResourceLocation ANIM_POSSESS_CAST = Kegare.id("possess_cast");
    public static final ResourceLocation ANIM_POSSESS_RETURN = Kegare.id("possess_return");
    public static final ResourceLocation ANIM_DIRECTIVE_CAST = Kegare.id("directive_cast");
    public static final ResourceLocation ANIM_FOCUS_POINT = Kegare.id("focus_point");
    public static final ResourceLocation ANIM_CURIO_BURST_CAST = Kegare.id("curio_burst_cast");
    /** Chain Burst's own cast (a special needs its own tell): both hands thrown open, fingers spread. */
    public static final ResourceLocation ANIM_CHAIN_BURST_CAST = Kegare.id("chain_burst_cast");
    public static final ResourceLocation ANIM_UNDERSTUDY_CAST = Kegare.id("understudy_cast");
    public static final ResourceLocation ANIM_STAND_IN_CAST = Kegare.id("stand_in_cast");
    // Strings' five (art/blockbench/string_animations.bbmodel -> player_animations/puppet_strings.json): each its own tell.
    public static final ResourceLocation ANIM_STRINGS_FIRE = Kegare.id("strings_fire");
    public static final ResourceLocation ANIM_STRINGS_REEL = Kegare.id("strings_reel");
    public static final ResourceLocation ANIM_STRINGS_ZIP = Kegare.id("strings_zip");
    public static final ResourceLocation ANIM_STRINGS_TIE = Kegare.id("strings_tie");
    public static final ResourceLocation ANIM_STRINGS_LEASH = Kegare.id("strings_leash");
    /** On the puppet itself: PAL animates players only, so {@code PuppetPoses} samples this one for the puppet model. */
    public static final ResourceLocation ANIM_PUPPET_IDLE_SPIN = Kegare.id("puppet_idle_spin");

    // Effects (drawn by PuppetFx)
    public static final ResourceLocation FX_PUPPET_FORM = fx("puppet_form");
    public static final ResourceLocation FX_PUPPET_DISSOLVE = fx("puppet_dissolve");
    public static final ResourceLocation FX_POSSESS_LINK = fx("possess_link");
    public static final ResourceLocation FX_DIRECTIVE_PULSE = fx("directive_pulse");
    public static final ResourceLocation FX_FOCUS_MARK = fx("focus_mark");
    /** curio_burst_explosion, in one size per charge tier. */
    public static final ResourceLocation FX_CURIO_BURST_EXPLOSION_TAP = fx("curio_burst_explosion_tap");
    public static final ResourceLocation FX_CURIO_BURST_EXPLOSION_HALF = fx("curio_burst_explosion_half");
    public static final ResourceLocation FX_CURIO_BURST_EXPLOSION_FULL = fx("curio_burst_explosion_full");
    public static final ResourceLocation FX_STAND_IN_GROW = fx("stand_in_grow");
    public static final ResourceLocation FX_UNDERSTUDY_SWAP = fx("understudy_swap");
    /** Where a knot grips: a small burst of fibers. */
    public static final ResourceLocation FX_STRINGS_ATTACH = fx("strings_attach");
    /** A string snapping or running out: fibers flying. */
    public static final ResourceLocation FX_STRINGS_SNAP = fx("strings_snap");
    /** Sparks along a string under tension (spawned on each client, see StringsClient). */
    public static final ResourceLocation FX_STRINGS_TENSION = fx("strings_tension");

    private PuppetIds() {}

    private static ResourceLocation fx(String name) {
        return Kegare.id("puppet_manipulation/" + name);
    }

    public static Visual body(ResourceLocation effect) {
        return Visual.effect(effect, Visual.Anchor.BODY);
    }
}

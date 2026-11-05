package com.cnethuka.kegare.innate_technique.puppet_manipulation;

import com.cnethuka.kegare.Kegare;
import com.cnethuka.kegare.innatetechnique.visual.Visual;
import net.minecraft.resources.ResourceLocation;

/**
 * Puppet Manipulation's IDs: player animations ({@code assets/kegare/player_animations/puppet_manipulation.json}) and
 * Photon effects ({@code assets/kegare/fx/puppet_manipulation/}). Lavender palette.
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
    public static final ResourceLocation ANIM_UNDERSTUDY_CAST = Kegare.id("understudy_cast");
    public static final ResourceLocation ANIM_STAND_IN_CAST = Kegare.id("stand_in_cast");
    /** On the puppet itself: PAL animates players only, so {@code PuppetPoses} samples this one for the puppet model. */
    public static final ResourceLocation ANIM_PUPPET_IDLE_SPIN = Kegare.id("puppet_idle_spin");

    // Photon effects
    public static final ResourceLocation FX_PUPPET_FORM = fx("puppet_form");
    public static final ResourceLocation FX_PUPPET_DISSOLVE = fx("puppet_dissolve");
    public static final ResourceLocation FX_POSSESS_LINK = fx("possess_link");
    public static final ResourceLocation FX_DIRECTIVE_PULSE = fx("directive_pulse");
    public static final ResourceLocation FX_FOCUS_MARK = fx("focus_mark");
    public static final ResourceLocation FX_CURIO_BURST_EXPLOSION = fx("curio_burst_explosion");
    public static final ResourceLocation FX_STAND_IN_GROW = fx("stand_in_grow");
    public static final ResourceLocation FX_UNDERSTUDY_SWAP = fx("understudy_swap");

    private PuppetIds() {}

    private static ResourceLocation fx(String name) {
        return Kegare.id("puppet_manipulation/" + name);
    }

    public static Visual body(ResourceLocation effect) {
        return Visual.effect(effect, Visual.Anchor.BODY);
    }
}

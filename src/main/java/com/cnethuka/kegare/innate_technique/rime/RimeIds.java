package com.cnethuka.kegare.innate_technique.rime;

import com.cnethuka.kegare.Kegare;
import com.cnethuka.kegare.innatetechnique.visual.Visual;
import net.minecraft.resources.ResourceLocation;

/** Rime's IDs: player animations (assets/kegare/player_animations/rime.json) and Photon effects (assets/kegare/fx/rime/). */
public final class RimeIds {
    // Player Animation Library animations
    public static final ResourceLocation ANIM_FROST_SHARD_CAST = Kegare.id("frost_shard_cast");
    public static final ResourceLocation ANIM_GLACIAL_LANCE_CHARGE = Kegare.id("glacial_lance_charge");
    public static final ResourceLocation ANIM_GLACIAL_LANCE_RELEASE = Kegare.id("glacial_lance_release");
    public static final ResourceLocation ANIM_FROST_FIELD_CHANNEL = Kegare.id("frost_field_channel");
    public static final ResourceLocation ANIM_RIME_SHELL_TOGGLE = Kegare.id("rime_shell_toggle");
    public static final ResourceLocation ANIM_FROZEN = Kegare.id("frozen");

    // Photon effects
    public static final ResourceLocation FX_FROST_SHARD_TRAIL = fx("frost_shard_trail");
    public static final ResourceLocation FX_FROST_SHARD_IMPACT = fx("frost_shard_impact");
    public static final ResourceLocation FX_GLACIAL_LANCE_CHARGE = fx("glacial_lance_charge");
    public static final ResourceLocation FX_GLACIAL_LANCE_TRAIL = fx("glacial_lance_trail");
    public static final ResourceLocation FX_GLACIAL_LANCE_IMPACT = fx("glacial_lance_impact");
    public static final ResourceLocation FX_FROST_FIELD_CONE = fx("frost_field_cone");
    public static final ResourceLocation FX_RIME_SHELL_AURA = fx("rime_shell_aura");
    public static final ResourceLocation FX_FREEZE_SHATTER = fx("freeze_shatter");

    /** Status visuals (not tied to one ability phase). */
    public static final Visual FROZEN = Visual.of(ANIM_FROZEN, null, Visual.Anchor.BODY);
    public static final Visual SHATTER = Visual.effect(FX_FREEZE_SHATTER, Visual.Anchor.BODY);
    public static final String FROZEN_LOOP_KEY = "kegare:frozen";

    private RimeIds() {}

    private static ResourceLocation fx(String name) {
        return Kegare.id("rime/" + name);
    }
}

package com.cnethuka.kegare.innate_technique.rime;

import com.cnethuka.kegare.Kegare;
import com.cnethuka.kegare.innatetechnique.visual.Visual;
import net.minecraft.resources.ResourceLocation;

/**
 * Rime's IDs: player animations (assets/kegare/player_animations/, a file a move, from tools/anims) and Photon effects
 * (assets/kegare/fx/rime/).
 */
public final class RimeIds {
    // Player Animation Library animations
    public static final ResourceLocation ANIM_FROST_SHARD_CAST = Kegare.id("frost_shard_cast");
    public static final ResourceLocation ANIM_GLACIAL_LANCE_CHARGE = Kegare.id("glacial_lance_charge");
    public static final ResourceLocation ANIM_GLACIAL_LANCE_RELEASE = Kegare.id("glacial_lance_release");
    public static final ResourceLocation ANIM_FROST_FIELD_CHANNEL = Kegare.id("frost_field_channel");
    public static final ResourceLocation ANIM_RIME_SHELL_TOGGLE = Kegare.id("rime_shell_toggle");
    public static final ResourceLocation ANIM_FROZEN = Kegare.id("frozen");
    // Specials
    public static final ResourceLocation ANIM_SPLINTER_CAST = Kegare.id("splinter_cast");
    public static final ResourceLocation ANIM_GLACIAL_SPIRE_CHARGE = Kegare.id("glacial_spire_charge");
    public static final ResourceLocation ANIM_GLACIAL_SPIRE_RELEASE = Kegare.id("glacial_spire_release");
    public static final ResourceLocation ANIM_ICE_PATH_SLIDE = Kegare.id("ice_path_slide");
    public static final ResourceLocation ANIM_SHATTER_SHELL_CAST = Kegare.id("shatter_shell_cast");

    // Photon effects
    public static final ResourceLocation FX_FROST_SHARD_TRAIL = fx("frost_shard_trail");
    public static final ResourceLocation FX_FROST_SHARD_IMPACT = fx("frost_shard_impact");
    public static final ResourceLocation FX_GLACIAL_LANCE_CHARGE = fx("glacial_lance_charge");
    public static final ResourceLocation FX_GLACIAL_LANCE_TRAIL = fx("glacial_lance_trail");
    public static final ResourceLocation FX_GLACIAL_LANCE_IMPACT = fx("glacial_lance_impact");
    /** Client-side pieces of Glacial Lance's look (see the client's LanceHold and LanceAftermath). */
    public static final ResourceLocation FX_GLACIAL_LANCE_STICK = fx("glacial_lance_stick");
    public static final ResourceLocation FX_GLACIAL_LANCE_SHATTER = fx("glacial_lance_shatter");
    public static final ResourceLocation FX_GLACIAL_LANCE_SNAP = fx("glacial_lance_snap");
    public static final ResourceLocation FX_GLACIAL_LANCE_CHARGE_SELF = fx("glacial_lance_charge_self");
    public static final ResourceLocation FX_FROST_FIELD_CONE = fx("frost_field_cone");
    /** Client-side pieces of Frost Field's look (see the client's FieldClient). */
    public static final ResourceLocation FX_FROST_FIELD_CONE_SELF = fx("frost_field_cone_self");
    public static final ResourceLocation FX_FROST_FIELD_MIST = fx("frost_field_mist");
    public static final ResourceLocation FX_RIME_SHELL_AURA = fx("rime_shell_aura");
    public static final ResourceLocation FX_RIME_SHELL_AURA_SELF = fx("rime_shell_aura_self");
    public static final ResourceLocation FX_SHATTER_SHELL_BURST_SELF = fx("shatter_shell_burst_self");
    public static final ResourceLocation FX_FREEZE_SHATTER = fx("freeze_shatter");
    /** Frost falling off a body with Freeze stacks (one loop a stack count, {@link #freezeStackKey}); your own, low round the feet. */
    public static final ResourceLocation FX_FREEZE_FROST = fx("freeze_frost");
    public static final ResourceLocation FX_FREEZE_FROST_SELF = fx("freeze_frost_self");
    // Specials
    public static final ResourceLocation FX_SPLINTER_CAST = fx("splinter_cast");
    public static final ResourceLocation FX_SPLINTER_TRAIL = fx("splinter_trail");
    public static final ResourceLocation FX_GLACIAL_SPIRE_CHARGE = fx("glacial_spire_charge");
    public static final ResourceLocation FX_GLACIAL_SPIRE_WARNING = fx("glacial_spire_warning");
    public static final ResourceLocation FX_GLACIAL_SPIRE_SPIKE = fx("glacial_spire_spike");
    public static final ResourceLocation FX_GLACIAL_SPIRE_ERUPT = fx("glacial_spire_erupt");
    public static final ResourceLocation FX_GLACIAL_SPIRE_SHATTER = fx("glacial_spire_shatter");
    public static final ResourceLocation FX_ICE_PATH_TRAIL = fx("ice_path_trail");
    public static final ResourceLocation FX_SHATTER_SHELL_BURST = fx("shatter_shell_burst");

    /** Status visuals (not tied to one ability phase). */
    public static final Visual FROZEN = Visual.of(ANIM_FROZEN, null, Visual.Anchor.BODY);
    public static final Visual SHATTER = Visual.effect(FX_FREEZE_SHATTER, Visual.Anchor.BODY);
    public static final String FROZEN_LOOP_KEY = "kegare:frozen";
    /** On a body with Freeze stacks: a loop keyed by how many, so every client sees the frost grow with them (FreezeClient). */
    public static final Visual FROST = Visual.effect(FX_FREEZE_FROST, Visual.Anchor.BODY);

    public static String freezeStackKey(int stacks) {
        return "kegare:freeze_" + stacks;
    }

    /** Glacial Spire's entity: the frost circle (warning), then the standing spike. */
    public static final Visual SPIRE_WARNING = Visual.effect(FX_GLACIAL_SPIRE_WARNING, Visual.Anchor.FEET);
    public static final Visual SPIRE_SPIKE = Visual.effect(FX_GLACIAL_SPIRE_SPIKE, Visual.Anchor.FEET);
    public static final String SPIRE_WARNING_KEY = "kegare:glacial_spire/warning";
    public static final String SPIRE_SPIKE_KEY = "kegare:glacial_spire/spike";
    /** Ice Path's trail on the caster: lingers, so the ice left behind fades out on its own time. */
    public static final Visual ICE_PATH_TRAIL = Visual.lingering(FX_ICE_PATH_TRAIL, Visual.Anchor.FEET);
    public static final String ICE_PATH_TRAIL_KEY = "kegare:ice_path/trail";

    private RimeIds() {}

    private static ResourceLocation fx(String name) {
        return Kegare.id("rime/" + name);
    }
}

package com.cnethuka.kegare.fists;

import com.cnethuka.kegare.Kegare;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageType;

/** Reinforced Fists' ids (DESIGN §3.28). */
public final class FistsIds {
    public static final ResourceLocation ABILITY = Kegare.id("reinforced_fists");
    /** Its loop while it's on: the burn on both fists, drawn by the client (no Photon), tinted the wearer's Malice color. */
    public static final ResourceLocation FX_BURN = Kegare.id("fists/burn");
    public static final String BURN_LOOP = "kegare:fists_burn";
    /** The second impact's damage type (data/kegare/damage_type/second_impact.json; no knockback, so a boss is never moved). */
    public static final ResourceKey<DamageType> SECOND_IMPACT = ResourceKey.create(Registries.DAMAGE_TYPE, Kegare.id("second_impact"));

    private FistsIds() {}
}

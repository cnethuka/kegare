package com.cnethuka.kegare.severed;

import com.cnethuka.kegare.Kegare;
import net.minecraft.resources.ResourceLocation;

/** Severed's IDs (DESIGN §3.27): the effects their clients draw themselves (no Photon files). */
public final class SeveredIds {
    /** Blur's loop: watchers draw the smear of afterimages and the dust while it runs. */
    public static final ResourceLocation FX_BLUR = Kegare.id("severed/blur");
    public static final String BLUR_LOOP = "severed/blur";

    private SeveredIds() {}
}

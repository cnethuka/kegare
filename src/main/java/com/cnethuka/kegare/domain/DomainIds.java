package com.cnethuka.kegare.domain;

import com.cnethuka.kegare.Kegare;
import java.util.List;
import net.minecraft.resources.ResourceLocation;

/** Domains' IDs (DESIGN §3.35): the Incomplete Domain, its unlock and its player animation ({@code player_animations/domain.json}). */
public final class DomainIds {
    /** The Incomplete Domain: the shared ability, and its entry in the unlock framework. */
    public static final ResourceLocation INCOMPLETE = Kegare.id("incomplete_domain");
    public static final ResourceLocation ANIM_INCOMPLETE_CAST = Kegare.id("incomplete_domain_cast");
    /** Every animation in domain.json. */
    public static final List<ResourceLocation> ANIMATIONS = List.of(ANIM_INCOMPLETE_CAST);
    /** Your own first-person hands stay vanilla's for these (a two-armed sweep would fill the view). */
    public static final List<ResourceLocation> FIRST_PERSON_VANILLA = List.of(ANIM_INCOMPLETE_CAST);

    private DomainIds() {}
}

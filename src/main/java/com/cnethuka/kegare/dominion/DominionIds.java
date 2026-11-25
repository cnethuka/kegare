package com.cnethuka.kegare.dominion;

import com.cnethuka.kegare.Kegare;
import java.util.List;
import net.minecraft.resources.ResourceLocation;

/** Dominions' IDs (DESIGN §3.35): the Incomplete Dominion, its unlock and its player animation ({@code player_animations/dominion.json}). */
public final class DominionIds {
    /** The Incomplete Dominion: the shared ability, and its entry in the unlock framework. */
    public static final ResourceLocation INCOMPLETE = Kegare.id("incomplete_dominion");
    public static final ResourceLocation ANIM_INCOMPLETE_CAST = Kegare.id("incomplete_dominion_cast");
    /** Every animation in dominion.json. */
    public static final List<ResourceLocation> ANIMATIONS = List.of(ANIM_INCOMPLETE_CAST);
    /** Your own first-person hands stay vanilla's for these (a two-armed sweep would fill the view). */
    public static final List<ResourceLocation> FIRST_PERSON_VANILLA = List.of(ANIM_INCOMPLETE_CAST);
    /** The block an Incomplete Dominion's inner space's floor is made of (DESIGN §3.35). */
    public static final ResourceLocation WASTELAND_GROUND = Kegare.id("wasteland_ground");

    private DominionIds() {}
}

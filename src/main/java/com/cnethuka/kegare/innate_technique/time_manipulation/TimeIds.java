package com.cnethuka.kegare.innate_technique.time_manipulation;

import com.cnethuka.kegare.Kegare;
import java.util.List;
import net.minecraft.resources.ResourceLocation;

/**
 * Time Manipulation's IDs: player animations ({@code assets/kegare/player_animations/time_manipulation.json}, from
 * {@code tools/anims/time_manipulation.py}) and the effects the client draws itself ({@code TimeFx}; no Photon files).
 */
public final class TimeIds {
    public static final ResourceLocation ANIM_POINT_MAKER_MARK = Kegare.id("point_maker_mark");
    public static final ResourceLocation ANIM_POINT_MAKER_RETURN = Kegare.id("point_maker_return");
    public static final ResourceLocation ANIM_REWIND_CAST = Kegare.id("rewind_cast");
    public static final ResourceLocation ANIM_ACCELERATE_TOGGLE = Kegare.id("accelerate_toggle");
    public static final ResourceLocation ANIM_FAST_FORWARD_CAST = Kegare.id("fast_forward_cast");
    public static final ResourceLocation ANIM_DECELERATE_CAST = Kegare.id("decelerate_cast");
    public static final ResourceLocation ANIM_STASIS_FIELD_CAST = Kegare.id("stasis_field_cast");
    public static final ResourceLocation ANIM_STANDSTILL_CAST = Kegare.id("standstill_cast");

    /** Every one of them. */
    public static final List<ResourceLocation> ANIMATIONS = List.of(ANIM_POINT_MAKER_MARK, ANIM_POINT_MAKER_RETURN, ANIM_REWIND_CAST,
            ANIM_ACCELERATE_TOGGLE, ANIM_FAST_FORWARD_CAST, ANIM_DECELERATE_CAST, ANIM_STASIS_FIELD_CAST, ANIM_STANDSTILL_CAST);
    /**
     * In your own first person these leave the hands to vanilla ({@code PlayerAnimations.keepVanillaHands}): Standstill's raised watch
     * and Stasis Field's palm pushed at the ground would cross the middle of the view.
     */
    public static final List<ResourceLocation> FIRST_PERSON_VANILLA = List.of(ANIM_STANDSTILL_CAST, ANIM_STASIS_FIELD_CAST);
    /** The rest keep their hands low and to the sides: shown only while you look about level. */
    public static final List<ResourceLocation> FIRST_PERSON_WHILE_LEVEL = List.of(ANIM_POINT_MAKER_MARK, ANIM_POINT_MAKER_RETURN,
            ANIM_REWIND_CAST, ANIM_ACCELERATE_TOGGLE, ANIM_FAST_FORWARD_CAST, ANIM_DECELERATE_CAST);

    /** Accelerate's toggle loop: the client draws its afterimages while it runs. */
    public static final ResourceLocation FX_ACCELERATE = fx("accelerate");
    /** Its loop's key ({@code VisualService.key(ACCELERATE, TOGGLE_LOOP)}). */
    public static final String ACCELERATE_LOOP_KEY = Kegare.id("accelerate") + "/toggle_loop";

    private TimeIds() {}

    private static ResourceLocation fx(String name) {
        return Kegare.id("time_manipulation/" + name);
    }
}

package com.cnethuka.kegare.severed;

import com.cnethuka.kegare.Kegare;
import net.minecraft.resources.ResourceLocation;

/** Severed's IDs (DESIGN §3.27): the effects their clients draw themselves (no Photon files). */
public final class SeveredIds {
    /** Blur's loop: watchers draw the smear of afterimages and the dust while it runs. */
    public static final ResourceLocation FX_BLUR = Kegare.id("severed/blur");
    public static final String BLUR_LOOP = "severed/blur";

    // Player animations (assets/kegare/player_animations/severed.json, from tools/anims/severed.py)
    public static final ResourceLocation ANIM_BLUR_ON = Kegare.id("blur_on");
    public static final ResourceLocation ANIM_CLOSING_LINE_LAUNCH = Kegare.id("closing_line_launch");
    public static final ResourceLocation ANIM_CLOSING_LINE_ARRIVE = Kegare.id("closing_line_arrive");
    public static final ResourceLocation ANIM_WINDED = Kegare.id("winded");
    public static final ResourceLocation ANIM_WALL_CRASH = Kegare.id("wall_crash");
    public static final java.util.List<ResourceLocation> ANIMATIONS = java.util.List.of(ANIM_BLUR_ON, ANIM_CLOSING_LINE_LAUNCH,
            ANIM_CLOSING_LINE_ARRIVE, ANIM_WINDED, ANIM_WALL_CRASH);
    /** In your own first person: hands on knees and arms flung wide would cross the view, so there it's vanilla's hands... */
    public static final java.util.List<ResourceLocation> FIRST_PERSON_VANILLA = java.util.List.of(ANIM_WINDED, ANIM_WALL_CRASH);
    /** ...and the rest keep the hands low: shown only while you look about level. */
    public static final java.util.List<ResourceLocation> FIRST_PERSON_WHILE_LEVEL = java.util.List.of(ANIM_BLUR_ON, ANIM_CLOSING_LINE_LAUNCH,
            ANIM_CLOSING_LINE_ARRIVE);

    private SeveredIds() {}
}

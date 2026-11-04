package com.cnethuka.kegare.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Client-only preferences (HUD and screens). No gameplay values here. */
public final class KegareClientConfig {
    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.BooleanValue HUD_ENABLED;
    public static final ModConfigSpec.IntValue HUD_X;
    public static final ModConfigSpec.IntValue HUD_Y;
    public static final ModConfigSpec.BooleanValue REVEAL_ANIMATION;
    public static final ModConfigSpec.IntValue RADIAL_HOLD_TICKS;
    public static final ModConfigSpec.BooleanValue FIRST_PERSON_ANIMATIONS;
    public static final ModConfigSpec.EnumValue<VfxQuality> VFX_QUALITY;

    /** How ability effects are drawn. Purely cosmetic: never changes what happens in the game. */
    public enum VfxQuality {
        /** Photon effects as authored. */
        FULL,
        /** Photon effects with about half the particles. */
        REDUCED,
        /** Vanilla particles only (no Photon effects). */
        VANILLA
    }

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.push("hud");
        HUD_ENABLED = b.define("enabled", true);
        HUD_X = b.comment("HUD offset from the left edge of the screen (GUI pixels).").defineInRange("offsetX", 4, 0, 4000);
        HUD_Y = b.comment("HUD offset from the top edge of the screen (GUI pixels).").defineInRange("offsetY", 4, 0, 4000);
        b.pop();
        b.push("reveal");
        REVEAL_ANIMATION = b.comment("Play the roll reveal spin. Off = show the result instantly.").define("animation", true);
        b.pop();
        b.push("radial");
        RADIAL_HOLD_TICKS = b.comment("Hold the next-ability key this many ticks to open the radial menu (a shorter tap cycles).")
                .defineInRange("holdTicks", 5, 1, 40);
        b.pop();
        b.push("visuals");
        FIRST_PERSON_ANIMATIONS = b.comment("Show your own ability animations (arms) in first person.")
                .define("firstPersonAnimations", true);
        VFX_QUALITY = b.comment("Ability effects: FULL (Photon), REDUCED (Photon, about half the particles) or VANILLA (vanilla particles only).")
                .defineEnum("vfxQuality", VfxQuality.FULL);
        b.pop();
        SPEC = b.build();
    }

    private KegareClientConfig() {}
}

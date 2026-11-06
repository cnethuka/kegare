package com.cnethuka.kegare.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Client-only preferences (HUD and screens). No gameplay values here. */
public final class KegareClientConfig {
    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.BooleanValue HUD_ENABLED;
    public static final ModConfigSpec.BooleanValue REVEAL_ANIMATION;
    public static final ModConfigSpec.BooleanValue FIRST_PERSON_ANIMATIONS;
    public static final ModConfigSpec.BooleanValue ANIMATION_FADES;
    public static final ModConfigSpec.EnumValue<VfxQuality> VFX_QUALITY;
    public static final ModConfigSpec.EnumValue<TechniqueSelect> TECHNIQUE_SELECT;

    /** In Technique Mode (G), which control picks abilities. The other one stays on the hotbar. Out of the mode both are vanilla. */
    public enum TechniqueSelect {
        /** Number keys 1-N pick abilities; the scroll wheel moves through the hotbar. */
        NUMBER_KEYS,
        /** The scroll wheel picks abilities; number keys pick hotbar slots. */
        SCROLL_WHEEL
    }

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
        b.pop();
        b.push("reveal");
        REVEAL_ANIMATION = b.comment("Play the roll reveal spin. Off = show the result instantly.").define("animation", true);
        b.pop();
        b.push("input");
        TECHNIQUE_SELECT = b.comment("In Technique Mode (G): NUMBER_KEYS = number keys pick abilities and the scroll wheel stays on "
                        + "the hotbar; SCROLL_WHEEL = the other way round. Also on the Sorcerer screen's Settings page.")
                .defineEnum("techniqueSelect", TechniqueSelect.NUMBER_KEYS);
        b.pop();
        b.push("visuals");
        FIRST_PERSON_ANIMATIONS = b.comment("Show your own ability animations (arms) in first person.")
                .define("firstPersonAnimations", true);
        ANIMATION_FADES = b.comment("Blend ability animations, yours and other players', in and out over a few ticks. Off: they snap on and off.")
                .define("animationFades", true);
        VFX_QUALITY = b.comment("Ability effects: FULL (Photon), REDUCED (Photon, about half the particles) or VANILLA (vanilla particles only).")
                .defineEnum("vfxQuality", VfxQuality.FULL);
        b.pop();
        SPEC = b.build();
    }

    private KegareClientConfig() {}
}

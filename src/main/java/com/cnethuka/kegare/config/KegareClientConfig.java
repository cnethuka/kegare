package com.cnethuka.kegare.config;

import java.util.List;
import net.neoforged.neoforge.common.ModConfigSpec;

/** Client-only preferences (HUD and screens). No gameplay values here. */
public final class KegareClientConfig {
    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.BooleanValue HUD_ENABLED;
    public static final ModConfigSpec.BooleanValue REVEAL_ANIMATION;
    public static final ModConfigSpec.BooleanValue REVEAL_ALWAYS_FULL;
    public static final ModConfigSpec.BooleanValue REVEAL_REDUCED_MOTION;
    public static final ModConfigSpec.DoubleValue REVEAL_FAKE_OUT_CHANCE;
    public static final ModConfigSpec.BooleanValue REVEAL_DEBUG_FORCE_FAKE_OUT;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> REVEAL_SEEN_TECHNIQUES;
    public static final ModConfigSpec.BooleanValue FIRST_PERSON_ANIMATIONS;
    public static final ModConfigSpec.BooleanValue ANIMATION_FADES;
    public static final ModConfigSpec.EnumValue<VfxQuality> VFX_QUALITY;
    public static final ModConfigSpec.IntValue DASH_DOUBLE_TAP_MILLIS;
    /** The Impact System's look (DESIGN §3.23): yours alone. When a hit lands, and how hard, is the server's. */
    public static final ModConfigSpec.DoubleValue SHAKE_STRENGTH;
    public static final ModConfigSpec.BooleanValue IMPACT_FRAMES;
    public static final ModConfigSpec.DoubleValue HIT_STOP_STRENGTH;
    public static final ModConfigSpec.DoubleValue ATTACKER_SHAKE;
    public static final ModConfigSpec.IntValue IMPACT_FRAME_MILLIS;
    /** Per tier (light, medium, heavy, impact_frame, top). */
    public static final ModConfigSpec.IntValue[] HIT_STOP_MILLIS = new ModConfigSpec.IntValue[5];
    public static final ModConfigSpec.DoubleValue[] SHAKE_DEGREES = new ModConfigSpec.DoubleValue[5];
    public static final ModConfigSpec.IntValue[] SHAKE_MILLIS = new ModConfigSpec.IntValue[5];
    public static final ModConfigSpec.DoubleValue[] FLASH_SIZE = new ModConfigSpec.DoubleValue[5];

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
        REVEAL_ANIMATION = b.comment("Play the roll reveal's scenes. Off = show the result instantly.").define("animation", true);
        REVEAL_ALWAYS_FULL = b.comment("Play every roll's full reveal. Off = a technique you've already seen plays the short version (rarity, then name).")
                .define("alwaysFull", false);
        REVEAL_REDUCED_MOTION = b.comment("Tone the reveal's screen flashes right down and turn its screen shake off.")
                .define("reducedMotion", false);
        REVEAL_FAKE_OUT_CHANCE = b.comment("Chance (0-1) that a Legendary's flame starts purple and flares to gold halfway through.")
                .defineInRange("fakeOutChance", 0.2, 0.0, 1.0);
        REVEAL_DEBUG_FORCE_FAKE_OUT = b.comment("Debug: every Legendary reveal plays the fake-out.")
                .define("debugForceFakeOut", false);
        REVEAL_SEEN_TECHNIQUES = b.comment("Innate techniques whose full reveal has played on this client. Remove one (or all) to see it again.")
                .defineListAllowEmpty("seenTechniques", List.of(), () -> "kegare:rime", id -> id instanceof String);
        b.pop();
        b.push("input");
        DASH_DOUBLE_TAP_MILLIS = b.comment("Most milliseconds between the two taps of the sprint key (Ctrl) that make a dash. 0 = off: use the Dash key instead.")
                .defineInRange("dashDoubleTapMillis", 300, 0, 1000);
        b.pop();
        b.push("visuals");
        FIRST_PERSON_ANIMATIONS = b.comment("Show your own ability animations (arms) in first person.")
                .define("firstPersonAnimations", true);
        ANIMATION_FADES = b.comment("Blend ability animations, yours and other players', in and out over a few ticks. Off: they snap on and off.")
                .define("animationFades", true);
        VFX_QUALITY = b.comment("Ability effects: FULL (Photon), REDUCED (Photon, about half the particles) or VANILLA (vanilla particles only).")
                .defineEnum("vfxQuality", VfxQuality.FULL);
        b.pop();
        b.comment("How hits look and feel on your screen (the Impact System). Only you see these; the server decides which tier a hit is.",
                "Edit while the game runs and try a tier with /kegare debug impact @s <tier> [attacker|victim|witness] [technique].").push("impact");
        SHAKE_STRENGTH = b.comment("Screen shake: 0 = none, 1 = as tuned, up to 2. Only the camera moves: never your aim or where you are.")
                .defineInRange("shakeStrength", 1.0, 0.0, 2.0);
        IMPACT_FRAMES = b.comment("The black-and-white impact frame on the biggest hits. It flashes the whole screen, so it can be switched off.",
                "Only drawn at vfxQuality FULL.").define("impactFrames", true);
        HIT_STOP_STRENGTH = b.comment("Hit-stop (the short freeze when a hit lands, for the two in it): 0 = none, 1 = as tuned, up to 2.",
                "Only what you see freezes: the game, your keys and the hit itself carry on.").defineInRange("hitStopStrength", 1.0, 0.0, 2.0);
        ATTACKER_SHAKE = b.comment("How much of the victim's shake the attacker gets.").defineInRange("attackerShake", 0.45, 0.0, 2.0);
        IMPACT_FRAME_MILLIS = b.comment("How long the impact frame shows (milliseconds).").defineInRange("impactFrameMillis", 90, 0, 1000);
        int[] stop = {0, 45, 90, 150, 220};
        double[] degrees = {0.35, 0.9, 1.8, 3.0, 4.5};
        int[] shakeMillis = {140, 220, 320, 450, 650};
        double[] flash = {0.22, 0.36, 0.55, 0.8, 1.1};
        String[] tiers = {"light", "medium", "heavy", "impact_frame", "top"};
        for (int t = 0; t < tiers.length; t++) {
            b.push(tiers[t]);
            HIT_STOP_MILLIS[t] = b.comment("The freeze (milliseconds), for the attacker and the victim.").defineInRange("hitStopMillis", stop[t], 0, 2000);
            SHAKE_DEGREES[t] = b.comment("How far the victim's camera shakes (degrees).").defineInRange("shakeDegrees", degrees[t], 0.0, 30.0);
            SHAKE_MILLIS[t] = b.comment("How long the shake lasts (milliseconds), after the freeze.").defineInRange("shakeMillis", shakeMillis[t], 0, 5000);
            FLASH_SIZE[t] = b.comment("How big the flash at the point of contact is (blocks).").defineInRange("flashSize", flash[t], 0.0, 5.0);
            b.pop();
        }
        b.pop();
        SPEC = b.build();
    }

    private KegareClientConfig() {}
}

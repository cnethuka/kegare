package com.cnethuka.kegare.severed;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * A Severed player's body (DESIGN §3.27): section {@code [severed]} of {@code kegare-server.toml}. None of it grows with Grade or
 * Refinement: their body is complete from birth. Every number is a starting point to tune live.
 */
public final class SeveredConfig {
    // body
    public static ModConfigSpec.DoubleValue MELEE_DAMAGE;
    public static ModConfigSpec.DoubleValue TAKEN_PHYSICAL;
    public static ModConfigSpec.DoubleValue TAKEN_TECHNIQUE;
    public static ModConfigSpec.DoubleValue TAKEN_FALL;
    public static ModConfigSpec.DoubleValue EXHAUSTION;
    // senses
    public static ModConfigSpec.DoubleValue SENSES_RANGE;
    // blur
    public static ModConfigSpec.DoubleValue BLUR_SPEED;
    public static ModConfigSpec.DoubleValue BLUR_TURN;
    public static ModConfigSpec.DoubleValue BLUR_ACCEL;
    public static ModConfigSpec.DoubleValue BLUR_SKID;
    public static ModConfigSpec.DoubleValue BLUR_EXHAUSTION;
    public static ModConfigSpec.IntValue BLUR_FOOD_FLOOR;
    public static ModConfigSpec.DoubleValue WINDED_SECONDS;
    public static ModConfigSpec.IntValue WINDED_SLOWNESS;
    // closing line
    public static ModConfigSpec.DoubleValue RANGE;
    public static ModConfigSpec.DoubleValue AIM_TOLERANCE;
    public static ModConfigSpec.DoubleValue CLOSING_COOLDOWN;
    public static ModConfigSpec.DoubleValue CLOSING_SPEED;
    public static ModConfigSpec.DoubleValue CLOSING_GAP;
    public static ModConfigSpec.DoubleValue CLOSING_RECOVERY;
    public static ModConfigSpec.DoubleValue CLOSING_STRIKE_WINDOW;
    public static ModConfigSpec.DoubleValue CLOSING_CARRY;
    public static ModConfigSpec.DoubleValue PARRY_STAGGER_SECONDS;
    public static ModConfigSpec.IntValue PARRY_STAGGER_SLOWNESS;
    // wall crash
    public static ModConfigSpec.DoubleValue CRASH_MIN_LOSS;
    public static ModConfigSpec.DoubleValue CRASH_DAMAGE;
    public static ModConfigSpec.DoubleValue CRASH_GRACE;
    public static ModConfigSpec.DoubleValue CRASH_MAX_DAMAGE;
    public static ModConfigSpec.DoubleValue CRASH_STUN;
    public static ModConfigSpec.DoubleValue CRASH_MAX_STUN;

    private SeveredConfig() {}

    /** Called from KegareServerConfig while it builds the spec. */
    public static void define(ModConfigSpec.Builder b) {
        b.comment("Severed players: no Malice, and a superhuman body in exchange. None of it grows with Grade or Refinement.").push("severed");

        b.comment("Always on.").push("body");
        MELEE_DAMAGE = b.comment("Strength: their melee hits (fists and weapons) deal this times as much, before a Reinforced Strike's own bonus.")
                .defineInRange("meleeDamage", 1.25, 1.0, 10.0);
        TAKEN_PHYSICAL = b.comment("Toughness: share of damage they take from everything that isn't a technique or a fall (hits, arrows, fire,",
                "explosions). The void, /kill and starving are never reduced.").defineInRange("takenPhysical", 0.75, 0.0, 1.0);
        TAKEN_TECHNIQUE = b.comment("Share of damage they take from techniques (any Kegare damage type).").defineInRange("takenTechnique", 0.9, 0.0, 1.0);
        TAKEN_FALL = b.comment("Share of fall damage they take.").defineInRange("takenFall", 0.4, 0.0, 1.0);
        EXHAUSTION = b.comment("Stamina: share of normal hunger drain (sprinting, jumping, attacking, mining, healing) they feel. Blur's own",
                "drain isn't reduced.").defineInRange("exhaustion", 0.5, 0.0, 1.0);
        b.pop();

        b.comment("Senses: every player and mob this close shows to them as a faint outline through walls, still or moving, invisible",
                "or not.").push("senses");
        SENSES_RANGE = b.defineInRange("range", 16.0, 0.0, 64.0);
        b.pop();

        b.comment("Blur (G for Severed players): extremely fast, with momentum. Speeds in blocks per tick (a sprint is about 0.28).").push("blur");
        BLUR_SPEED = b.comment("Top speed.").defineInRange("speed", 0.56, 0.1, 4.0);
        BLUR_TURN = b.comment("How fast their velocity turns toward where they steer, degrees a tick: sharp turns overshoot.")
                .defineInRange("turnDegrees", 8.0, 0.5, 180.0);
        BLUR_ACCEL = b.comment("Speed gained a tick while steering.").defineInRange("acceleration", 0.07, 0.005, 4.0);
        BLUR_SKID = b.comment("Speed lost a tick once they let go: the skid to a stop.").defineInRange("skid", 0.05, 0.005, 4.0);
        BLUR_EXHAUSTION = b.comment("Hunger it burns per block covered at Blur speed (4 is a food point). Stamina doesn't reduce it.")
                .defineInRange("exhaustionPerBlock", 0.2, 0.0, 10.0);
        BLUR_FOOD_FLOOR = b.comment("At or below this food (6 = 3 shanks, where vanilla stops sprinting) it crashes off and can't come back",
                "on until food is above it.").defineInRange("foodFloor", 6, 0, 20);
        WINDED_SECONDS = b.comment("Winded: the slowdown when it crashes off.").defineInRange("windedSeconds", 4.0, 0.0, 60.0);
        WINDED_SLOWNESS = b.comment("Slowness amplifier while winded (0 = Slowness I).").defineInRange("windedSlowness", 0, 0, 9);
        b.pop();

        b.comment("Closing Line's target: the player or mob under the crosshair, in line of sight.").push("targeting");
        RANGE = b.defineInRange("range", 24.0, 1.0, 128.0);
        AIM_TOLERANCE = b.comment("How far off the crosshair (blocks) still counts as looking at someone.").defineInRange("aimTolerance", 0.3, 0.0, 2.0);
        b.pop();

        b.comment("Closing Line (R during Blur): a straight rush at where the target stood, a Reinforced Strike on arrival.").push("closing_line");
        CLOSING_COOLDOWN = b.defineInRange("cooldown", 6.0, 0.0, 600.0);
        CLOSING_SPEED = b.comment("Rush speed, blocks a tick: a 20-block gap in five ticks.").defineInRange("speed", 4.0, 0.5, 9.0);
        CLOSING_GAP = b.comment("Blocks left between you and the target when you stop: melee reach, in front of them.").defineInRange("stopGap", 0.8, 0.0, 3.0);
        CLOSING_RECOVERY = b.comment("After every Closing Line: this long with no Blur speed.").defineInRange("recoverySeconds", 0.6, 0.0, 10.0);
        CLOSING_STRIKE_WINDOW = b.comment("How long the Strike it earns waits.").defineInRange("strikeWindow", 0.5, 0.05, 10.0);
        CLOSING_CARRY = b.comment("Knockback the Strike carries per block/tick of the rush's speed, on top of a Severed Strike's own.")
                .defineInRange("carriedKnockback", 0.1, 0.0, 5.0);
        PARRY_STAGGER_SECONDS = b.comment("A perfect parry that catches the Strike staggers them this long (a normal stagger is 0.5 s)...")
                .defineInRange("parryStaggerSeconds", 1.5, 0.0, 30.0);
        PARRY_STAGGER_SLOWNESS = b.comment("...at this Slowness amplifier (a normal stagger is 2).").defineInRange("parryStaggerSlowness", 3, 0, 9);
        b.pop();

        b.comment("A wall hit at speed (in Blur or a Closing Line): damage and a stun from the speed lost, like flying into a wall.",
                "Toughness applies to the damage.").push("wall_crash");
        CRASH_MIN_LOSS = b.comment("Speed lost (blocks/tick) below which it's just a scrape: nothing.").defineInRange("minSpeedLost", 0.3, 0.0, 10.0);
        CRASH_DAMAGE = b.comment("Damage per block/tick of speed lost...").defineInRange("damagePerSpeed", 10.0, 0.0, 100.0);
        CRASH_GRACE = b.comment("...less this.").defineInRange("damageGrace", 2.0, 0.0, 100.0);
        CRASH_MAX_DAMAGE = b.defineInRange("maxDamage", 12.0, 0.0, 1000.0);
        CRASH_STUN = b.comment("Stun seconds per block/tick of speed lost.").defineInRange("stunPerSpeed", 2.0, 0.0, 60.0);
        CRASH_MAX_STUN = b.defineInRange("maxStunSeconds", 2.0, 0.0, 60.0);
        b.pop();

        b.pop();
    }

    public static int ticks(double seconds) {
        return (int) Math.round(seconds * 20.0);
    }
}

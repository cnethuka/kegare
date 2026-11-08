package com.cnethuka.kegare.battledamage;

import com.cnethuka.kegare.impact.ImpactTier;
import java.util.List;
import net.neoforged.neoforge.common.ModConfigSpec;

/** Battle damage's server numbers ({@code [battle_damage]} in kegare-server.toml, DESIGN §3.24). */
public final class BattleDamageConfig {
    public enum Mode {
        /** Broken blocks stay broken. */
        PERMANENT,
        /** They rebuild themselves, piece by piece, a while after the fight (containers with their contents). */
        RESTORING,
        /** Cracks and debris only: nothing breaks. */
        COSMETIC
    }

    public static ModConfigSpec.EnumValue<Mode> MODE;
    public static ModConfigSpec.EnumValue<ImpactTier> MIN_TIER;
    public static ModConfigSpec.DoubleValue STRENGTH_SCALE;
    public static ModConfigSpec.DoubleValue TOP_POWER;
    public static ModConfigSpec.DoubleValue BASE_RADIUS;
    public static ModConfigSpec.DoubleValue RADIUS_PER_STRENGTH;
    public static ModConfigSpec.DoubleValue MAX_RADIUS;
    public static ModConfigSpec.BooleanValue SPILL_CONTENTS;
    public static ModConfigSpec.ConfigValue<List<? extends String>> PROTECTED;
    public static ModConfigSpec.IntValue MAX_PER_HIT;
    public static ModConfigSpec.IntValue MAX_PER_SECOND;
    public static ModConfigSpec.BooleanValue MOBS_NEED_GRIEFING;
    public static ModConfigSpec.DoubleValue RESTORE_DELAY;
    public static ModConfigSpec.IntValue RESTORE_INTERVAL;
    public static ModConfigSpec.DoubleValue RESTORE_QUIET_RADIUS;
    public static ModConfigSpec.BooleanValue SLAMS;
    public static ModConfigSpec.DoubleValue SLAM_MIN_SPEED;
    public static ModConfigSpec.DoubleValue SLAM_STRENGTH_PER_SPEED;
    public static ModConfigSpec.DoubleValue SLAM_WINDOW;

    private BattleDamageConfig() {}

    public static void define(ModConfigSpec.Builder b) {
        b.comment("Battle damage (DESIGN §3.24): big hits break the world. A hit breaks blocks round where it met the body when it's at",
                        "least minimumTier, using the Impact System's strength (§3.23). Its power is strengthScale x strength (topPower for",
                        "the top tier), falling off to nothing at its radius (baseRadius + radiusPerStrength x strength, at most maxRadius).",
                        "A block breaks where that power is more than its blast resistance: glass 0.3, dirt 0.5, wood 3, stone 6, obsidian 1200.",
                        "Blocks vanilla makes unbreakable (negative hardness) never break, nor does anything on the protected list. Each break",
                        "fires NeoForge's block-break event, so spawn protection and claim mods can cancel it. Broken blocks never drop",
                        "themselves: they shatter into rubble. Swap never breaks anything.")
                .push("battle_damage");
        MODE = b.comment("PERMANENT: damage stays. RESTORING: blocks rebuild themselves piece by piece some time after the fight",
                "(containers with their contents, never spilled; pending rebuilds survive a restart). COSMETIC: cracks and debris only.")
                .defineEnum("mode", Mode.PERMANENT);
        MIN_TIER = b.comment("The weakest hit that breaks anything (light hits never do).").defineEnum("minimumTier", ImpactTier.MEDIUM);
        STRENGTH_SCALE = b.defineInRange("strengthScale", 5.0, 0.0, 10_000.0);
        TOP_POWER = b.comment("The top tier's power (reserved for Black Spark): ceiling level, the only thing that breaks obsidian.")
                .defineInRange("topPower", 1500.0, 0.0, 1_000_000.0);
        BASE_RADIUS = b.defineInRange("baseRadius", 0.5, 0.0, 16.0);
        RADIUS_PER_STRENGTH = b.defineInRange("radiusPerStrength", 1.2, 0.0, 16.0);
        MAX_RADIUS = b.defineInRange("maxRadius", 4.5, 0.0, 16.0);
        SPILL_CONTENTS = b.comment("A broken container spills what was in it on the ground, as TNT does (never in RESTORING: it comes back full).")
                .define("spillContents", true);
        PROTECTED = b.comment("Blocks that never break: ids (minecraft:glass) or tags (#minecraft:logs).")
                .defineListAllowEmpty("protected", List.of(), () -> "minecraft:glass", o -> o instanceof String);
        MAX_PER_HIT = b.comment("At most this many blocks break per hit (the nearest first)...").defineInRange("maxBlocksPerHit", 40, 0, 4096);
        MAX_PER_SECOND = b.comment("...and this many a second in a dimension, so a long fight can't lag the server.")
                .defineInRange("maxBlocksPerSecond", 160, 0, 100_000);
        MOBS_NEED_GRIEFING = b.comment("A hit by a mob (not a player or their technique) only breaks blocks with the mobGriefing gamerule on.")
                .define("mobsNeedGriefing", true);
        b.comment("RESTORING mode.").push("restoring");
        RESTORE_DELAY = b.comment("Seconds with no battle damage nearby before a scar starts rebuilding.").defineInRange("delay", 60.0, 0.0, 86_400.0);
        RESTORE_INTERVAL = b.comment("Ticks between one block coming back and the next (lowest first).").defineInRange("interval", 3, 1, 1200);
        RESTORE_QUIET_RADIUS = b.comment("Battle damage this close (blocks) to a scar counts as the fight still going.")
                .defineInRange("quietRadius", 32.0, 0.0, 512.0);
        b.pop();
        b.comment("Wall slams: anything knocked hard into a wall, floor or ceiling leaves a crater where it hits, scaled by how fast it",
                "was going. Craters only: a slam does no damage of its own.").push("slams");
        SLAMS = b.define("enabled", true);
        SLAM_MIN_SPEED = b.comment("The slowest (blocks/tick) that counts as a slam. A sprinting hit knocks someone about 0.7.")
                .defineInRange("minSpeed", 0.9, 0.0, 10.0);
        SLAM_STRENGTH_PER_SPEED = b.comment("Strength (as a hit's) per block/tick of speed.").defineInRange("strengthPerSpeed", 1.0, 0.0, 100.0);
        SLAM_WINDOW = b.comment("Seconds after a breaking hit that its victim is watched for a slam.").defineInRange("windowSeconds", 2.5, 0.0, 60.0);
        b.pop();
        b.pop();
    }
}

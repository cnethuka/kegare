package com.cnethuka.kegare.innate_technique.swap;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Swap's numbers: section {@code [swap]} of {@code kegare-server.toml}. Times are in seconds. */
public final class SwapConfig {
    // targeting (every ability that needs a player)
    public static ModConfigSpec.DoubleValue RANGE;
    public static ModConfigSpec.DoubleValue AIM_TOLERANCE;

    // positions (INSTANT)
    public static ModConfigSpec.DoubleValue POSITIONS_COST;
    public static ModConfigSpec.DoubleValue POSITIONS_COOLDOWN;

    // switchback (special of positions, INSTANT)
    public static ModConfigSpec.DoubleValue SWITCHBACK_COST;
    public static ModConfigSpec.DoubleValue SWITCHBACK_DELAY;

    // awareness (INSTANT)
    public static ModConfigSpec.DoubleValue AWARENESS_COST;
    public static ModConfigSpec.DoubleValue AWARENESS_COOLDOWN;
    public static ModConfigSpec.DoubleValue AWARENESS_DURATION;

    // mimic (special of awareness, INSTANT)
    public static ModConfigSpec.DoubleValue MIMIC_COST;
    public static ModConfigSpec.DoubleValue MIMIC_DURATION;

    // anchor (INSTANT, two-stage)
    public static ModConfigSpec.DoubleValue ANCHOR_THROW_COST;
    public static ModConfigSpec.DoubleValue ANCHOR_RECALL_COST;
    public static ModConfigSpec.DoubleValue ANCHOR_THROW_COOLDOWN;
    public static ModConfigSpec.DoubleValue ANCHOR_COOLDOWN;
    public static ModConfigSpec.DoubleValue ANCHOR_LIFETIME;
    public static ModConfigSpec.DoubleValue ANCHOR_SPEED;

    // send_off (special of anchor, INSTANT)
    public static ModConfigSpec.DoubleValue SEND_OFF_COST;

    // exchange (INSTANT)
    public static ModConfigSpec.DoubleValue EXCHANGE_COST;
    public static ModConfigSpec.DoubleValue EXCHANGE_COOLDOWN;

    // momentum_exchange (special of exchange, INSTANT)
    public static ModConfigSpec.DoubleValue MOMENTUM_COST;

    // total_swap (ultimate, INSTANT)
    public static ModConfigSpec.DoubleValue TOTAL_SWAP_COST;
    public static ModConfigSpec.DoubleValue TOTAL_SWAP_COOLDOWN;
    public static ModConfigSpec.DoubleValue TOTAL_SWAP_MIN_DISTANCE;

    private SwapConfig() {}

    /** Called from KegareServerConfig while it builds the spec. */
    public static void define(ModConfigSpec.Builder b) {
        b.comment("Swap (COMMON innate technique): trade places, looks, effects and momentum with other players. Times are in seconds.")
                .push("swap");

        b.comment("Every ability that needs a player: the one you're looking at, in line of sight.").push("targeting");
        RANGE = b.comment("How far away (blocks) a player can be.").defineInRange("range", 24.0, 1.0, 128.0);
        AIM_TOLERANCE = b.comment("How far beside the crosshair (blocks) a player still counts as looked at.")
                .defineInRange("aimTolerance", 0.3, 0.0, 2.0);
        b.pop();

        b.comment("Swap positions and facing with the player you're looking at.").push("positions");
        POSITIONS_COST = b.defineInRange("cost", 48.0, 0.0, 100_000.0);
        POSITIONS_COOLDOWN = b.defineInRange("cooldown", 8.0, 0.0, 3600.0);
        b.pop();

        b.comment("Special of Positions: the same swap, then an automatic swap back.").push("switchback");
        SWITCHBACK_COST = b.defineInRange("cost", 88.0, 0.0, 100_000.0);
        SWITCHBACK_DELAY = b.comment("Seconds until the swap back.").defineInRange("returnDelay", 3.0, 0.05, 600.0);
        b.pop();

        b.comment("Disguise swap: everyone else sees you as the target and the target as you. Visual only.").push("awareness");
        AWARENESS_COST = b.defineInRange("cost", 80.0, 0.0, 100_000.0);
        AWARENESS_COOLDOWN = b.defineInRange("cooldown", 20.0, 0.0, 3600.0);
        AWARENESS_DURATION = b.defineInRange("duration", 5.0, 0.05, 600.0);
        b.pop();

        b.comment("Special of Awareness: one-way; only you look like the target.").push("mimic");
        MIMIC_COST = b.defineInRange("cost", 96.0, 0.0, 100_000.0);
        MIMIC_DURATION = b.defineInRange("duration", 8.0, 0.05, 600.0);
        b.pop();

        b.comment("First cast throws an anchor that sticks where it lands; the second swaps you with it.").push("anchor");
        ANCHOR_THROW_COST = b.defineInRange("throwCost", 32.0, 0.0, 100_000.0);
        ANCHOR_RECALL_COST = b.comment("The second cast (swap with the anchor).").defineInRange("recallCost", 40.0, 0.0, 100_000.0);
        ANCHOR_THROW_COOLDOWN = b.comment("After throwing (so a double press doesn't recall at once).").defineInRange("throwCooldown", 0.5, 0.0, 3600.0);
        ANCHOR_COOLDOWN = b.comment("After the anchor is used (recall or Send Off).").defineInRange("cooldown", 12.0, 0.0, 3600.0);
        ANCHOR_LIFETIME = b.comment("The anchor disappears this long after it is thrown.").defineInRange("lifetime", 20.0, 0.05, 3600.0);
        ANCHOR_SPEED = b.comment("Throw speed in blocks per tick (an ender pearl is 1.5).").defineInRange("throwSpeed", 1.5, 0.1, 10.0);
        b.pop();

        b.comment("Special of Anchor: swap the player you're looking at with your anchor (uses it up).").push("send_off");
        SEND_OFF_COST = b.defineInRange("cost", 72.0, 0.0, 100_000.0);
        b.pop();

        b.comment("Give the target your worst negative effect, take their strongest positive one.").push("exchange");
        EXCHANGE_COST = b.defineInRange("cost", 60.0, 0.0, 100_000.0);
        EXCHANGE_COOLDOWN = b.defineInRange("cooldown", 15.0, 0.0, 3600.0);
        b.pop();

        b.comment("Special of Exchange: swap velocity and fall distance with the target.").push("momentum_exchange");
        MOMENTUM_COST = b.defineInRange("cost", 72.0, 0.0, 100_000.0);
        b.pop();

        b.comment("Ultimate: swap positions, disguises (as Awareness) and health percentage.").push("total_swap");
        TOTAL_SWAP_COST = b.defineInRange("cost", 240.0, 0.0, 100_000.0);
        TOTAL_SWAP_COOLDOWN = b.defineInRange("cooldown", 120.0, 0.0, 3600.0);
        TOTAL_SWAP_MIN_DISTANCE = b.comment("The target must be at least this far away (blocks).").defineInRange("minDistance", 4.0, 0.0, 128.0);
        b.pop();

        b.pop();
    }

    public static int ticks(ModConfigSpec.DoubleValue seconds) {
        return (int) Math.round(seconds.get() * 20.0);
    }
}

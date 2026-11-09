package com.cnethuka.kegare.sight;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Too fast for the eye (DESIGN §3.30): {@code [too_fast_for_the_eye]} of {@code kegare-server.toml}. */
public final class SightConfig {
    public static ModConfigSpec.DoubleValue ACCELERATE_DELAY;
    public static ModConfigSpec.DoubleValue BLUR_DELAY;
    public static ModConfigSpec.IntValue EASE_TICKS;

    private SightConfig() {}

    public static void define(ModConfigSpec.Builder b) {
        b.comment("Too fast for the eye: while a Severed player is Blurring, or a Time Manipulation player has Accelerate on, everyone else sees",
                "them where they were a moment ago (the faster they go, the further behind). Their real position is never sent to a viewer who",
                "shouldn't have it. A Six Eyes bearer sees the real one; hits are always judged against the real one.").push("too_fast_for_the_eye");
        ACCELERATE_DELAY = b.comment("How far behind an Accelerating player is seen, in ms.").defineInRange("accelerateDelay", 100.0, 0.0, 2000.0);
        BLUR_DELAY = b.comment("How far behind a Blurring player is seen, in ms.").defineInRange("blurDelay", 150.0, 0.0, 2000.0);
        EASE_TICKS = b.comment("Ticks the image takes to fall behind when it starts, and to catch up again when it ends: no snap either way.")
                .defineInRange("easeTicks", 4, 1, 100);
        b.pop();
    }
}

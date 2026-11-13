package com.cnethuka.kegare.api.boss;

import net.minecraft.resources.ResourceLocation;

/**
 * An addon-owned boss move. The addon chooses when to start it and selects its target. {@code onStart} runs at the
 * beginning of windup, so tells belong there. {@code onEnd} always runs once after a successful start.
 */
public interface BossAttack {
    ResourceLocation id();

    BossAttackTiming timing();

    default void onStart(BossAttackContext context) {}

    /** Runs once per server tick, after any phase transition due on that tick. */
    default void onTick(BossAttackContext context) {}

    /** Cleanup for both completion and cancellation. */
    default void onEnd(BossAttackContext context, BossAttackEndReason reason) {}
}

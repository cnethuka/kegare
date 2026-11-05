package com.cnethuka.kegare.rmt;

import com.cnethuka.kegare.advancement.KegareAdvancements;
import net.minecraft.server.level.ServerPlayer;

/**
 * Reverse Malice Technique's advancements (Base tab, hidden and silent: announcing them would tell everyone the
 * player can heal).
 * <ul>
 *   <li>{@code a_green_light}: take in the green orb (unlock RMT).</li>
 *   <li>{@code its_been_a_while}: start an RMT cast at {@link #BRINK_HEALTH} health or less, having been hit by a
 *       player in the last {@link #BRINK_WINDOW_TICKS} ticks.</li>
 * </ul>
 */
public final class RmtAdvancements {
    /** 3 hearts. */
    public static final float BRINK_HEALTH = 6f;
    /** 10 seconds. */
    public static final long BRINK_WINDOW_TICKS = 200;

    private RmtAdvancements() {}

    /** An RMT channel just started (paid and running). */
    static void onCastStart(ServerPlayer player) {
        long lastHit = GreenOrbService.state(player).lastHitByPlayer();
        boolean recentlyHit = lastHit != Long.MIN_VALUE && player.level().getGameTime() - lastHit <= BRINK_WINDOW_TICKS;
        if (player.getHealth() <= BRINK_HEALTH && recentlyHit) KegareAdvancements.award(player, KegareAdvancements.ITS_BEEN_A_WHILE);
    }

    /** The green orb was taken in: RMT unlocked. */
    static void onUnlock(ServerPlayer player) {
        KegareAdvancements.award(player, KegareAdvancements.A_GREEN_LIGHT);
    }
}

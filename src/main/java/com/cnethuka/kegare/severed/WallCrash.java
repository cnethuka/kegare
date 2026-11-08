package com.cnethuka.kegare.severed;

import com.cnethuka.kegare.combat.CombatHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;

/**
 * Hitting a wall at Blur or Closing Line speed (DESIGN §3.27): like vanilla's flying into a wall, damage and a stun from the speed it
 * cost them. A glancing scrape loses little (the wall only takes the part of the speed going into it) and does little; a head-on
 * crash a lot. The damage is {@code flyIntoWall}, so Toughness takes its physical share off.
 */
public final class WallCrash {
    private WallCrash() {}

    /** Damage for {@code lost} blocks/tick of speed lost, before Toughness. 0 below the scrape line. */
    public static float damage(double lost) {
        if (lost < SeveredConfig.CRASH_MIN_LOSS.get()) return 0f;
        double raw = lost * SeveredConfig.CRASH_DAMAGE.get() - SeveredConfig.CRASH_GRACE.get();
        return (float) Mth.clamp(raw, 0.0, SeveredConfig.CRASH_MAX_DAMAGE.get());
    }

    /** Stun ticks for {@code lost} blocks/tick of speed lost. */
    public static int stunTicks(double lost) {
        if (lost < SeveredConfig.CRASH_MIN_LOSS.get()) return 0;
        return SeveredConfig.ticks(Math.min(lost * SeveredConfig.CRASH_STUN.get(), SeveredConfig.CRASH_MAX_STUN.get()));
    }

    /** They hit a wall, losing {@code lost} blocks/tick. Returns whether it was a crash (not a scrape). */
    static boolean crash(ServerPlayer player, double lost) {
        float damage = damage(lost);
        int stun = stunTicks(lost);
        if (damage <= 0f && stun <= 0) return false;
        if (damage > 0f) {
            player.invulnerableTime = 0;
            player.hurt(player.damageSources().flyIntoWall(), damage);
        }
        if (stun > 0) CombatHelper.stun(player, stun); // the stun ends Blur and a Closing Line
        SeveredFx.wallCrash(player, lost);
        return true;
    }
}

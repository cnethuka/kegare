package com.cnethuka.kegare.melee;

import com.cnethuka.kegare.malice.MaliceService;
import net.minecraft.world.entity.player.Player;

/**
 * What the layer's numbers are for one player, so nothing else has to ask whether they're Severed. A cost of 0 is always
 * affordable, which is how a Severed player with no Malice gets through the same checks.
 *
 * @param guardMaxTicks 0 = held for as long as the Malice lasts
 */
public record MeleeNumbers(double dashCost, int dashCooldownTicks, double dashDistance, int perfectTicks, double guardMeleeReduction,
                           double guardTechniqueReduction, double guardDrainPerTick, double guardMinMalice, int guardMaxTicks, int recoveryTicks,
                           int strikeTicks, double strikeBonus, double strikeKnockback, double strikeCost) {
    public static MeleeNumbers of(Player player) {
        return MaliceService.isSevered(player) ? severed() : standard();
    }

    static MeleeNumbers standard() {
        return new MeleeNumbers(MeleeConfig.DASH_COST.get(), MeleeConfig.ticks(MeleeConfig.DASH_COOLDOWN.get()), MeleeConfig.DASH_DISTANCE.get(),
                MeleeConfig.ticks(MeleeConfig.PARRY_PERFECT_WINDOW.get()), MeleeConfig.GUARD_MELEE_REDUCTION.get(), MeleeConfig.GUARD_TECHNIQUE_REDUCTION.get(),
                MeleeConfig.GUARD_DRAIN.get() / 20.0, MeleeConfig.GUARD_MIN_MALICE.get(), 0, MeleeConfig.ticks(MeleeConfig.PARRY_RECOVERY.get()),
                MeleeConfig.ticks(MeleeConfig.STRIKE_WINDOW.get()), MeleeConfig.STRIKE_BONUS.get(), MeleeConfig.STRIKE_KNOCKBACK.get(), MeleeConfig.STRIKE_COST.get());
    }

    static MeleeNumbers severed() {
        return new MeleeNumbers(0, MeleeConfig.ticks(MeleeConfig.SEVERED_DASH_COOLDOWN.get()), MeleeConfig.SEVERED_DASH_DISTANCE.get(),
                MeleeConfig.ticks(MeleeConfig.SEVERED_PERFECT_WINDOW.get()), MeleeConfig.SEVERED_GUARD_MELEE_REDUCTION.get(), MeleeConfig.SEVERED_GUARD_TECHNIQUE_REDUCTION.get(),
                0, 0, Math.max(1, MeleeConfig.ticks(MeleeConfig.SEVERED_GUARD_MAX.get())), MeleeConfig.ticks(MeleeConfig.SEVERED_RECOVERY.get()),
                MeleeConfig.ticks(MeleeConfig.SEVERED_STRIKE_WINDOW.get()), MeleeConfig.SEVERED_STRIKE_BONUS.get(), MeleeConfig.SEVERED_STRIKE_KNOCKBACK.get(), 0);
    }
}

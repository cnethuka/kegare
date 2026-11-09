package com.cnethuka.kegare.fists;

import com.cnethuka.kegare.config.ConfigText;
import com.cnethuka.kegare.innatetechnique.AbilityContext;
import com.cnethuka.kegare.innatetechnique.BaseAbility;
import com.cnethuka.kegare.innatetechnique.CastManager;
import com.cnethuka.kegare.innatetechnique.CastType;
import com.cnethuka.kegare.innatetechnique.EndReason;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/**
 * Reinforced Fists (shared ability, TOGGLE, DESIGN §3.28): Malice wrapped round the fists. Every Malice user has it, with nothing to
 * unlock; Severed players never (no Malice). Turning it on costs {@code onCost}; it drains {@code drainPerSecond} while on and turns off
 * when Malice runs out; turning it on again waits {@code rearmCooldown} (its cooldown, which starts when it goes off). It keeps running
 * outside Technique Mode, so the hotbar is free, and through Burnout (shared). Death and logout end it, like every toggle.
 * <p>
 * While on, melee hits deal more ({@link FistsEvents}), and a Reinforced Strike landed with it on hits again a moment later
 * ({@link SecondImpact}). A low burn on both fists shows it ({@link FistsTell}).
 */
public final class ReinforcedFists extends BaseAbility {
    ReinforcedFists() {
        super(FistsIds.ABILITY, CastType.TOGGLE);
    }

    // Shared ability: no innate technique's surroundings modifier applies.
    @Override public double cost(ServerPlayer p) { return FistsConfig.ON_COST.get(); }
    @Override public double upkeepPerSecond(ServerPlayer p) { return FistsConfig.DRAIN.get(); }
    @Override public int cooldownTicks(ServerPlayer p) { return FistsConfig.ticks(FistsConfig.REARM); }
    @Override public boolean hasNoTarget() { return true; }

    @Override
    public Object[] descriptionArgs() {
        return new Object[] {ConfigText.percent(FistsConfig.FIST_BONUS), ConfigText.percent(FistsConfig.WEAPON_BONUS),
                ConfigText.percent(FistsConfig.SECOND_IMPACT_DAMAGE)};
    }

    public static boolean isOn(ServerPlayer player) {
        return CastManager.isToggleActive(player, Fists.REINFORCED_FISTS);
    }

    @Override
    public void execute(AbilityContext ctx) {
        ServerPlayer p = ctx.player();
        p.level().playSound(null, p.getX(), p.getY() + 1.0, p.getZ(), SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 0.55f, 1.35f); // it ignites
        Fists.TURNED_ON.forEach(listener -> listener.accept(p));
        FistsTell.sync(p, true);
    }

    @Override
    public void onEnd(AbilityContext ctx, EndReason reason) {
        FistsTell.sync(ctx.player(), false);
    }
}

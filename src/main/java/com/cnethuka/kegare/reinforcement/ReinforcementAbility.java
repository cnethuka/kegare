package com.cnethuka.kegare.reinforcement;

import com.cnethuka.kegare.config.ConfigText;
import com.cnethuka.kegare.innatetechnique.AbilityContext;
import com.cnethuka.kegare.innatetechnique.BaseAbility;
import com.cnethuka.kegare.innatetechnique.CastManager;
import com.cnethuka.kegare.innatetechnique.CastType;
import com.cnethuka.kegare.innatetechnique.EndReason;
import com.cnethuka.kegare.loadout.AbilityCategory;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/**
 * Reinforcement (Malice, TOGGLE, DESIGN §3.16): Malice routed through the body, improving its durability and force tolerance. Every
 * Malice user has it, with nothing to unlock; Severed never. Turning it on costs {@code onCost}; it drains {@code drainPerSecond} and turns
 * off when Malice runs out; {@code rearmCooldown} before it can go on again. It keeps running outside Technique Mode and through Burnout;
 * death and logout end it. While on, you take less damage and less knockback ({@link Reinforcement}); it adds no damage. Malice wraps the
 * whole body, visible to everyone (client {@code AuraClient}).
 */
public final class ReinforcementAbility extends BaseAbility {
    ReinforcementAbility() {
        super(ReinforcementIds.ABILITY, CastType.TOGGLE);
    }

    @Override public double cost(ServerPlayer p) { return ReinforcementConfig.ON_COST.get(); }
    @Override public double upkeepPerSecond(ServerPlayer p) { return ReinforcementConfig.DRAIN.get(); }
    @Override public int cooldownTicks(ServerPlayer p) { return ReinforcementConfig.ticks(ReinforcementConfig.REARM); }
    @Override public boolean hasNoTarget() { return true; }
    @Override public AbilityCategory category() { return AbilityCategory.MALICE; }

    @Override
    public Object[] descriptionArgs() {
        return new Object[] {ConfigText.percent(ReinforcementConfig.REDUCTION_PHYSICAL), ConfigText.percent(ReinforcementConfig.REDUCTION_TECHNIQUE),
                ConfigText.percent(ReinforcementConfig.KNOCKBACK_RESISTANCE)};
    }

    public static boolean isOn(ServerPlayer player) {
        return CastManager.isToggleActive(player, Reinforcement.REINFORCEMENT);
    }

    @Override
    public void execute(AbilityContext ctx) {
        ServerPlayer p = ctx.player();
        p.level().playSound(null, p.getX(), p.getY() + 1.0, p.getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.4f, 1.7f);
        Reinforcement.update(p, true);
    }

    @Override
    public void onEnd(AbilityContext ctx, EndReason reason) {
        Reinforcement.update(ctx.player(), false);
    }
}

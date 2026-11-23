package com.cnethuka.kegare.flow;

import com.cnethuka.kegare.config.ConfigText;
import com.cnethuka.kegare.api.ability.AbilityContext;
import com.cnethuka.kegare.api.ability.BaseAbility;
import com.cnethuka.kegare.innatetechnique.CastManager;
import com.cnethuka.kegare.api.ability.CastType;
import com.cnethuka.kegare.api.ability.EndReason;
import com.cnethuka.kegare.api.ability.AbilityCategory;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/**
 * Flow (Malice, TOGGLE, DESIGN §3.16): Malice let flow through the whole body, boosting Offense, Defense, Movement and the Innate Technique
 * ({@link Flow}). Every Malice user has it, with nothing to unlock; Severed never. Turning it on costs {@code onCost}; it drains
 * {@code drainPerSecond} and turns off when Malice runs out; {@code rearmCooldown} before it can go on again. It keeps running outside
 * Technique Mode and through Burnout; death and logout end it. Malice wraps the whole body, visible to everyone (client {@code AuraClient}).
 */
public final class FlowAbility extends BaseAbility {
    FlowAbility() {
        super(FlowIds.ABILITY, CastType.TOGGLE);
    }

    @Override public double cost(ServerPlayer p) { return FlowConfig.ON_COST.get(); }
    @Override public double upkeepPerSecond(ServerPlayer p) { return FlowConfig.DRAIN.get(); }
    @Override public int cooldownTicks(ServerPlayer p) { return FlowConfig.ticks(FlowConfig.REARM); }
    @Override public boolean hasNoTarget() { return true; }
    @Override public AbilityCategory category() { return AbilityCategory.MALICE; }

    @Override
    public Object[] descriptionArgs() {
        return new Object[] {ConfigText.percent(FlowConfig.OFFENSE), ConfigText.percent(FlowConfig.REDUCTION_PHYSICAL),
                ConfigText.percent(FlowConfig.REDUCTION_TECHNIQUE), ConfigText.percent(FlowConfig.KNOCKBACK_RESISTANCE),
                ConfigText.percent(FlowConfig.MOVEMENT_SPEED), ConfigText.percent(FlowConfig.DASH_RECHARGE), ConfigText.percent(FlowConfig.TECHNIQUE_COST)};
    }

    public static boolean isOn(ServerPlayer player) {
        return CastManager.isToggleActive(player, Flow.FLOW);
    }

    @Override
    public void execute(AbilityContext ctx) {
        ServerPlayer p = ctx.player();
        p.level().playSound(null, p.getX(), p.getY() + 1.0, p.getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.4f, 1.7f);
        Flow.update(p, true);
    }

    @Override
    public void onEnd(AbilityContext ctx, EndReason reason) {
        Flow.update(ctx.player(), false);
    }
}

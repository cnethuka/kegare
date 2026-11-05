package com.cnethuka.kegare.innate_technique.swap;

import com.cnethuka.kegare.innatetechnique.AbilityContext;
import com.cnethuka.kegare.innatetechnique.CastManager;
import com.cnethuka.kegare.innatetechnique.CastType;
import com.cnethuka.kegare.innatetechnique.visual.AbilityVisuals;
import net.minecraft.server.level.ServerPlayer;

/**
 * Special of Positions (INSTANT): the same swap, then an automatic swap back after {@code returnDelay} seconds
 * ({@link Switchback}). Costs more; shares Positions' cooldown.
 */
public final class SwitchbackAbility extends SwapAbility.Special {
    private static final AbilityVisuals VISUALS = AbilityVisuals.builder().cast(SwapIds.ANIM_SWAP_SNAP, null).build();

    SwitchbackAbility() {
        super("switchback", CastType.INSTANT, () -> Swap.POSITIONS);
    }

    @Override public double cost(ServerPlayer p) { return SwapConfig.SWITCHBACK_COST.get(); }
    @Override public AbilityVisuals visuals() { return VISUALS; }
    @Override public boolean canCast(AbilityContext ctx) { return SwapAbility.hasTarget(ctx); }

    @Override
    public void execute(AbilityContext ctx) {
        ServerPlayer caster = ctx.player();
        ServerPlayer target = SwapTargeting.lookedAt(caster).orElse(null);
        if (target == null || !SwapTargeting.allowed(caster, target, this)) {
            CastManager.fizzleKeepingCost(caster);
            return;
        }
        PositionsAbility.swap(caster, target);
        Switchback.schedule(caster, target);
    }
}

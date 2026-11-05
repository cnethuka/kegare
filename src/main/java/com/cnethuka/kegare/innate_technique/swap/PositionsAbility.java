package com.cnethuka.kegare.innate_technique.swap;

import com.cnethuka.kegare.innatetechnique.Ability;
import com.cnethuka.kegare.innatetechnique.AbilityContext;
import com.cnethuka.kegare.innatetechnique.CastManager;
import com.cnethuka.kegare.innatetechnique.CastType;
import com.cnethuka.kegare.innatetechnique.visual.AbilityVisuals;
import java.util.Optional;
import net.minecraft.server.level.ServerPlayer;

/** Positions (INSTANT): swap positions and facing with the player you're looking at. Special: Switchback. */
public final class PositionsAbility extends SwapAbility {
    private static final AbilityVisuals VISUALS = AbilityVisuals.builder().cast(SwapIds.ANIM_SWAP_SNAP, null).build();

    PositionsAbility() {
        super("positions", CastType.INSTANT);
    }

    @Override public double cost(ServerPlayer p) { return SwapConfig.POSITIONS_COST.get(); }
    @Override public int cooldownTicks(ServerPlayer p) { return SwapConfig.ticks(SwapConfig.POSITIONS_COOLDOWN); }
    @Override public Optional<Ability> special() { return Optional.of(Swap.SWITCHBACK); }
    @Override public AbilityVisuals visuals() { return VISUALS; }
    @Override public boolean canCast(AbilityContext ctx) { return hasTarget(ctx); }

    @Override
    public void execute(AbilityContext ctx) {
        ServerPlayer caster = ctx.player();
        ServerPlayer target = SwapTargeting.lookedAt(caster).orElse(null);
        if (target == null || !SwapTargeting.allowed(caster, target, this)) {
            CastManager.fizzleKeepingCost(caster);
            return;
        }
        swap(caster, target);
    }

    /** The swap itself (Switchback and Total Swap do the same). */
    static void swap(ServerPlayer caster, ServerPlayer target) {
        Swapper.swapPlayers(caster, target, true);
        SwapTracker.swapped(caster, target);
        SwapAdvancements.award(caster, SwapAdvancements.SWITCHEROO);
    }
}

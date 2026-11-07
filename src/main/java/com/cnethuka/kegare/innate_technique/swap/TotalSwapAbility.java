package com.cnethuka.kegare.innate_technique.swap;

import com.cnethuka.kegare.innatetechnique.AbilityContext;
import com.cnethuka.kegare.innatetechnique.CastManager;
import com.cnethuka.kegare.innatetechnique.CastType;
import com.cnethuka.kegare.innatetechnique.visual.AbilityVisuals;
import com.cnethuka.kegare.innatetechnique.visual.VisualService;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Swap's ultimate (V, INSTANT; no special): everything at once with the player you're looking at. Positions and facing
 * swap, both wear each other's look (as Awareness), and health trades as a share of max health (each gets the other's
 * percentage of their own max). The target must be at least {@code minDistance} away.
 */
public final class TotalSwapAbility extends SwapAbility {
    private static final AbilityVisuals VISUALS = AbilityVisuals.builder().cast(SwapIds.ANIM_TOTAL_SWAP_CLAP, null).build();

    TotalSwapAbility() {
        super("total_swap", CastType.INSTANT);
    }

    @Override public double cost(ServerPlayer p) { return SwapConfig.TOTAL_SWAP_COST.get(); }
    @Override public int cooldownTicks(ServerPlayer p) { return SwapConfig.ticks(SwapConfig.TOTAL_SWAP_COOLDOWN); }
    @Override public AbilityVisuals visuals() { return VISUALS; }

    @Override
    public boolean canCast(AbilityContext ctx) {
        return SwapTargeting.lookedAt(ctx.player()).map(t -> farEnough(ctx.player(), t)).orElse(false);
    }

    @Override
    public Component blockedMessage(ServerPlayer player) {
        return SwapTargeting.lookedAt(player).isPresent()
                ? Component.translatable("message.kegare.swap.too_close", displayName(),
                        Long.toString(Math.round(SwapConfig.TOTAL_SWAP_MIN_DISTANCE.get())))
                : super.blockedMessage(player);
    }

    @Override
    public void execute(AbilityContext ctx) {
        ServerPlayer caster = ctx.player();
        ServerPlayer target = SwapTargeting.lookedAt(caster).orElse(null);
        if (target == null || !SwapTargeting.allowed(caster, target, this)) {
            CastManager.fizzleKeepingCost(caster);
            return;
        }
        float casterShare = caster.getHealth() / caster.getMaxHealth();
        float targetShare = target.getHealth() / target.getMaxHealth();
        if (casterShare < SwapAdvancements.IDENTITY_CRISIS_HEALTH) SwapTracker.identityCrisisStarted(caster);
        PositionsAbility.swap(caster, target, SwapSounds.TOTAL_SWAP);
        AwarenessAbility.disguisePair(caster, target, SwapConfig.ticks(SwapConfig.AWARENESS_DURATION), Disguises.Kind.TOTAL_SWAP);
        swapHealth(caster, target, casterShare, targetShare);
        VisualService.playOnce(caster, SwapIds.TOTAL_SWAP_BURST);
        VisualService.playOnce(target, SwapIds.TOTAL_SWAP_BURST);
    }

    /** Each gets the other's share of max health, of their own max (4/20 and 18/20 become 18 and 4). */
    static void swapHealth(ServerPlayer caster, ServerPlayer target, float casterShare, float targetShare) {
        caster.setHealth(targetShare * caster.getMaxHealth());
        target.setHealth(casterShare * target.getMaxHealth());
    }

    static boolean farEnough(ServerPlayer caster, ServerPlayer target) {
        double min = SwapConfig.TOTAL_SWAP_MIN_DISTANCE.get();
        return caster.distanceToSqr(target) >= min * min;
    }
}

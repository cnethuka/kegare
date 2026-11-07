package com.cnethuka.kegare.innate_technique.swap;

import com.cnethuka.kegare.innatetechnique.Ability;
import com.cnethuka.kegare.innatetechnique.AbilityContext;
import com.cnethuka.kegare.innatetechnique.CastManager;
import com.cnethuka.kegare.innatetechnique.CastType;
import com.cnethuka.kegare.innatetechnique.visual.AbilityVisuals;
import com.cnethuka.kegare.innatetechnique.visual.VisualService;
import java.util.Optional;
import net.minecraft.server.level.ServerPlayer;

/**
 * Awareness (INSTANT): a disguise swap for {@code duration} seconds. Everyone else sees you as the target and the
 * target as you (see {@link Disguises}); each of you still sees yourself. Visual only. Special: Mimic.
 */
public final class AwarenessAbility extends SwapAbility {
    private static final AbilityVisuals VISUALS = AbilityVisuals.builder().cast(SwapIds.ANIM_AWARENESS_CAST, null).build();

    AwarenessAbility() {
        super("awareness", CastType.INSTANT);
    }

    @Override public double cost(ServerPlayer p) { return SwapConfig.AWARENESS_COST.get(); }
    @Override public int cooldownTicks(ServerPlayer p) { return SwapConfig.ticks(SwapConfig.AWARENESS_COOLDOWN); }
    @Override public Optional<Ability> special() { return Optional.of(Swap.MIMIC); }
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
        SwapSounds.AWARENESS.at(caster);
        SwapSounds.AWARENESS.at(target);
        disguisePair(caster, target, SwapConfig.ticks(SwapConfig.AWARENESS_DURATION), Disguises.Kind.AWARENESS);
    }

    /** Each looks like the other (Awareness, and Total Swap's disguise): a card flips over on both as the looks change hands. */
    static void disguisePair(ServerPlayer caster, ServerPlayer target, int ticks, Disguises.Kind kind) {
        Disguises.apply(caster, target, ticks, kind, caster);
        Disguises.apply(target, caster, ticks, kind, caster);
        for (ServerPlayer p : new ServerPlayer[] {caster, target}) {
            SwapSounds.CARD_FLIP.at(p);
            VisualService.playAt(p.serverLevel(), Swapper.bodyPos(p), SwapIds.AWARENESS_FLIP, p.getId());
        }
    }
}

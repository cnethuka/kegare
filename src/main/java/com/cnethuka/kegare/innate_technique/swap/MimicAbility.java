package com.cnethuka.kegare.innate_technique.swap;

import com.cnethuka.kegare.innatetechnique.AbilityContext;
import com.cnethuka.kegare.innatetechnique.CastManager;
import com.cnethuka.kegare.innatetechnique.CastType;
import com.cnethuka.kegare.innatetechnique.visual.AbilityVisuals;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/**
 * Special of Awareness (INSTANT): a ONE-WAY disguise. You look like the target for {@code duration} seconds; the
 * target stays as themselves. Same rendering and ending rules as Awareness ({@link Disguises}).
 */
public final class MimicAbility extends SwapAbility.Special {
    private static final AbilityVisuals VISUALS = AbilityVisuals.builder().cast(SwapIds.ANIM_MIMIC_CAST, null).build();

    MimicAbility() {
        super("mimic", CastType.INSTANT, () -> Swap.AWARENESS);
    }

    @Override public double cost(ServerPlayer p) { return SwapConfig.MIMIC_COST.get(); }
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
        Disguises.apply(caster, target, SwapConfig.ticks(SwapConfig.MIMIC_DURATION), Disguises.Kind.MIMIC, caster);
        caster.level().playSound(null, caster.getX(), caster.getY(), caster.getZ(), SoundEvents.ILLUSIONER_MIRROR_MOVE, SoundSource.PLAYERS, 0.8f, 0.9f);
    }
}

package com.cnethuka.kegare.innate_technique.swap;

import com.cnethuka.kegare.innatetechnique.AbilityContext;
import com.cnethuka.kegare.innatetechnique.CastManager;
import com.cnethuka.kegare.innatetechnique.CastType;
import com.cnethuka.kegare.innatetechnique.visual.AbilityVisuals;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;

/**
 * Special of Positions (INSTANT): the same swap, then an automatic swap back after {@code returnDelay} seconds
 * ({@link Switchback}). Costs more; shares Positions' cooldown. Its tell (DESIGN §3.15): its own wrist-flick
 * animation, its own charge sound, and the countdown rings going up on both as it's cast.
 */
public final class SwitchbackAbility extends SwapAbility.Special {
    private static final AbilityVisuals VISUALS = AbilityVisuals.builder().cast(SwapIds.ANIM_SWITCHBACK_CAST, null).build();

    SwitchbackAbility() {
        super("switchback", CastType.INSTANT, () -> Swap.POSITIONS);
    }

    @Override public double cost(ServerPlayer p) { return SwapConfig.SWITCHBACK_COST.get(); }
    @Override public AbilityVisuals visuals() { return VISUALS; }
    @Override public boolean canCast(AbilityContext ctx) { return SwapAbility.hasLivingTarget(ctx); }
    @Override boolean takesMobs() { return true; }

    @Override
    public void execute(AbilityContext ctx) {
        ServerPlayer caster = ctx.player();
        LivingEntity target = SwapTargeting.lookedAtLiving(caster).orElse(null);
        if (target == null || !SwapTargeting.allowed(caster, target, this)) {
            CastManager.fizzleKeepingCost(caster);
            return;
        }
        // The rings and the sound come first, so the cast already reads as "this comes back", unlike Positions.
        Switchback.schedule(caster, target);
        sound(caster);
        sound(target);
        PositionsAbility.swap(caster, target);
    }

    private static void sound(LivingEntity at) {
        at.level().playSound(null, at.getX(), at.getY(), at.getZ(), SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.PLAYERS, 0.7f, 1.6f);
    }
}

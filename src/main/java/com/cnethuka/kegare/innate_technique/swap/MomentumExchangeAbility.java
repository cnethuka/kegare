package com.cnethuka.kegare.innate_technique.swap;

import com.cnethuka.kegare.innatetechnique.AbilityContext;
import com.cnethuka.kegare.innatetechnique.CastManager;
import com.cnethuka.kegare.innatetechnique.CastType;
import com.cnethuka.kegare.innatetechnique.visual.AbilityVisuals;
import com.cnethuka.kegare.innatetechnique.visual.VisualService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

/**
 * Special of Exchange (INSTANT): swap velocity vectors AND fall distance with the target (the one swap that keeps fall
 * distance, on purpose). Velocities are read from what each client last reported, and sent back to both clients.
 * Nobody moves position.
 */
public final class MomentumExchangeAbility extends SwapAbility.Special {
    private static final AbilityVisuals VISUALS = AbilityVisuals.builder().cast(SwapIds.ANIM_MOMENTUM_CAST, null).build();

    MomentumExchangeAbility() {
        super("momentum_exchange", CastType.INSTANT, () -> Swap.EXCHANGE);
    }

    @Override public double cost(ServerPlayer p) { return SwapConfig.MOMENTUM_COST.get(); }
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
        Vec3 casterMotion = caster.getKnownMovement();
        Vec3 targetMotion = target.getKnownMovement();
        setMotion(caster, targetMotion);
        setMotion(target, casterMotion);
        SwapTracker.momentum(caster, target);
        float casterFall = caster.fallDistance;
        caster.fallDistance = target.fallDistance;
        target.fallDistance = casterFall;
        VisualService.playOnce(caster, SwapIds.MOMENTUM_BURST);
        VisualService.playOnce(target, SwapIds.MOMENTUM_BURST);
        caster.level().playSound(null, caster.getX(), caster.getY(), caster.getZ(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.6f, 0.7f);
        target.level().playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.6f, 0.7f);
    }

    /** Sets a player's velocity and sends it to their client (and everyone tracking them). */
    private static void setMotion(ServerPlayer player, Vec3 motion) {
        player.setDeltaMovement(motion);
        player.setKnownMovement(motion);
        player.hurtMarked = true;
    }
}

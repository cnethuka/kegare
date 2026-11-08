package com.cnethuka.kegare.innate_technique.swap;

import com.cnethuka.kegare.innatetechnique.AbilityContext;
import com.cnethuka.kegare.innatetechnique.CastManager;
import com.cnethuka.kegare.innatetechnique.CastType;
import com.cnethuka.kegare.innatetechnique.visual.AbilityVisuals;
import com.cnethuka.kegare.innatetechnique.visual.VisualService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Special of Exchange (INSTANT): swap velocity vectors AND fall distance with the target, a player or a mob (the one swap that keeps fall
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
        Vec3 casterMotion = caster.getKnownMovement();
        Vec3 targetMotion = target.getKnownMovement();
        setMotion(caster, targetMotion);
        setMotion(target, casterMotion);
        Swapper.landed(caster, target, 0, casterMotion.length(), 0);
        SwapTracker.momentum(caster, target);
        float casterFall = caster.fallDistance;
        caster.fallDistance = target.fallDistance;
        target.fallDistance = casterFall;
        VisualService.playOnce(caster, SwapIds.MOMENTUM_BURST);
        VisualService.playOnce(target, SwapIds.MOMENTUM_BURST);
        SwapSounds.MOMENTUM.at(caster);
        SwapSounds.MOMENTUM.at(target);
    }

    /** Sets a player's or mob's velocity and sends it out (to the player's own client, and everyone tracking it). */
    private static void setMotion(LivingEntity entity, Vec3 motion) {
        entity.setDeltaMovement(motion);
        if (entity instanceof ServerPlayer player) player.setKnownMovement(motion);
        entity.hurtMarked = true;
    }
}

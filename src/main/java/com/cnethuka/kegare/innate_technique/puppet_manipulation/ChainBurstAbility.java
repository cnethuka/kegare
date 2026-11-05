package com.cnethuka.kegare.innate_technique.puppet_manipulation;

import com.cnethuka.kegare.innatetechnique.AbilityContext;
import com.cnethuka.kegare.innatetechnique.CastManager;
import com.cnethuka.kegare.innatetechnique.CastType;
import com.cnethuka.kegare.innatetechnique.visual.AbilityVisuals;
import java.util.Optional;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;

/**
 * Special of Curio Burst (INSTANT): every puppet of yours within {@code radius} of what you're looking at (a player, a
 * mob, or one of your puppets) detonates, nearest first, one after another. Shares Curio Burst's cooldown.
 */
public final class ChainBurstAbility extends PuppetAbility.Special {
    private static final AbilityVisuals VISUALS = AbilityVisuals.builder().cast(PuppetIds.ANIM_CHAIN_BURST_CAST, null).build();

    ChainBurstAbility() {
        super("chain_burst", CastType.INSTANT, () -> PuppetManipulation.CURIO_BURST);
    }

    @Override public double cost(ServerPlayer p) { return PuppetConfig.CHAIN_COST.get(); }
    @Override public AbilityVisuals visuals() { return VISUALS; }

    private static Optional<LivingEntity> target(ServerPlayer owner) {
        return PuppetTargeting.lookedAtLiving(owner, PuppetConfig.CURIO_RANGE.get());
    }

    private static boolean anyNear(ServerPlayer owner, LivingEntity target) {
        double r = PuppetConfig.CHAIN_RADIUS.get();
        return !owner.serverLevel().getEntitiesOfClass(PuppetEntity.class, target.getBoundingBox().inflate(r),
                p -> p.isAlive() && !p.isPossessed() && p.isOwnedBy(owner) && p.distanceToSqr(target) <= r * r).isEmpty();
    }

    @Override
    public boolean canCast(AbilityContext ctx) {
        return target(ctx.player()).map(t -> anyNear(ctx.player(), t)).orElse(false);
    }

    @Override
    public Component blockedMessage(ServerPlayer player) {
        return Component.translatable(target(player).isEmpty() ? "message.kegare.chain_burst.no_target" : "message.kegare.chain_burst.no_puppets",
                PuppetConfig.CHAIN_RADIUS.get().intValue());
    }

    @Override
    public void execute(AbilityContext ctx) {
        ServerPlayer owner = ctx.player();
        LivingEntity target = target(owner).orElse(null);
        if (target == null) {
            CastManager.fizzleKeepingCost(owner);
            return;
        }
        // The tell before the first blast: a rising whistle at the target.
        owner.level().playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.PLAYERS, 1.0f, 0.6f);
        CurioBursts.chain(owner, target);
    }
}

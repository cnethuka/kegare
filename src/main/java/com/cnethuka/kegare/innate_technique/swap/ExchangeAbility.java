package com.cnethuka.kegare.innate_technique.swap;

import com.cnethuka.kegare.innatetechnique.Ability;
import com.cnethuka.kegare.innatetechnique.AbilityContext;
import com.cnethuka.kegare.innatetechnique.CastManager;
import com.cnethuka.kegare.innatetechnique.CastType;
import com.cnethuka.kegare.innatetechnique.visual.AbilityVisuals;
import java.util.Comparator;
import java.util.Optional;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;

/**
 * Exchange (INSTANT): you give the target your worst negative effect and take their strongest positive one, each with
 * its duration and amplifier. Either half may be missing; with nothing to swap at all it fizzles for free. Effects
 * with infinite duration never move. Special: Momentum Exchange.
 */
public final class ExchangeAbility extends SwapAbility {
    private static final AbilityVisuals VISUALS = AbilityVisuals.builder().cast(SwapIds.ANIM_EXCHANGE_CAST, null).build();
    /** Strongest first: higher amplifier, then longer duration. */
    private static final Comparator<MobEffectInstance> STRENGTH =
            Comparator.comparingInt(MobEffectInstance::getAmplifier).thenComparingInt(MobEffectInstance::getDuration);
    /** Ticks an exchanged effect's orb takes to cross. */
    private static final int TRAVEL_TICKS = 8;

    ExchangeAbility() {
        super("exchange", CastType.INSTANT);
    }

    @Override public double cost(ServerPlayer p) { return SwapConfig.EXCHANGE_COST.get(); }
    @Override public int cooldownTicks(ServerPlayer p) { return SwapConfig.ticks(SwapConfig.EXCHANGE_COOLDOWN); }
    @Override public Optional<Ability> special() { return Optional.of(Swap.MOMENTUM_EXCHANGE); }
    @Override public AbilityVisuals visuals() { return VISUALS; }

    @Override
    public boolean canCast(AbilityContext ctx) {
        return SwapTargeting.lookedAt(ctx.player()).map(t -> somethingToSwap(ctx.player(), t)).orElse(false);
    }

    @Override
    public Component blockedMessage(ServerPlayer player) {
        return SwapTargeting.lookedAt(player).isPresent()
                ? Component.translatable("message.kegare.swap.nothing_to_exchange")
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
        Optional<MobEffectInstance> give = strongest(caster, MobEffectCategory.HARMFUL);
        Optional<MobEffectInstance> take = strongest(target, MobEffectCategory.BENEFICIAL);
        give.ifPresent(effect -> {
            caster.removeEffect(effect.getEffect());
            target.addEffect(new MobEffectInstance(effect), caster);
            SwapLink.travel(caster.serverLevel(), SwapIds.FX_EXCHANGE_TRANSFER, Swapper.bodyPos(caster), Swapper.bodyPos(target), TRAVEL_TICKS);
        });
        take.ifPresent(effect -> {
            target.removeEffect(effect.getEffect());
            caster.addEffect(new MobEffectInstance(effect), caster);
            SwapLink.travel(caster.serverLevel(), SwapIds.FX_EXCHANGE_TRANSFER, Swapper.bodyPos(target), Swapper.bodyPos(caster), TRAVEL_TICKS);
        });
        caster.level().playSound(null, caster.getX(), caster.getY(), caster.getZ(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.5f, 1.6f);
        target.level().playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.5f, 1.6f);
    }

    static boolean somethingToSwap(ServerPlayer caster, ServerPlayer target) {
        return strongest(caster, MobEffectCategory.HARMFUL).isPresent() || strongest(target, MobEffectCategory.BENEFICIAL).isPresent();
    }

    /** The entity's strongest effect of a category (infinite ones never count). */
    static Optional<MobEffectInstance> strongest(LivingEntity entity, MobEffectCategory category) {
        return entity.getActiveEffects().stream()
                .filter(e -> e.getEffect().value().getCategory() == category && !e.isInfiniteDuration())
                .max(STRENGTH);
    }
}

package com.cnethuka.kegare.domain;

import com.cnethuka.kegare.api.ability.AbilityCategory;
import com.cnethuka.kegare.api.ability.AbilityContext;
import com.cnethuka.kegare.api.ability.BaseAbility;
import com.cnethuka.kegare.api.ability.CastType;
import com.cnethuka.kegare.api.visual.AbilityVisuals;
import com.cnethuka.kegare.config.ConfigText;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * The Incomplete Domain (shared, Malice, INSTANT; DESIGN §3.35): the inner space expanded with Malice alone. It takes half the
 * Domain Meter and its cost in Malice, and opens a smaller, weaker barrier, pitch black, round its caster for a shorter time,
 * the same grey wasteland inside for everyone who opens one. It finds nobody (no sure-hit); instead its caster's technique runs better
 * inside it while it stands. Slotted on the bar like any shared ability, never on V. Learned through the unlock framework
 * ({@link IncompleteDomains}); Severed players never have it.
 */
public final class IncompleteDomain extends BaseAbility {
    private static final AbilityVisuals VISUALS = AbilityVisuals.builder().cast(DomainIds.ANIM_INCOMPLETE_CAST, null).build();

    IncompleteDomain() {
        super(DomainIds.INCOMPLETE, CastType.INSTANT);
    }

    // Shared ability: no technique's surroundings, and none of the boosts it gives the technique.
    @Override public double cost(ServerPlayer player) { return DomainConfig.INCOMPLETE_COST.get(); }
    @Override public int cooldownTicks(ServerPlayer player) { return DomainConfig.ticks(DomainConfig.INCOMPLETE_COOLDOWN); }
    @Override public AbilityCategory category() { return AbilityCategory.MALICE; }
    @Override public AbilityVisuals visuals() { return VISUALS; }

    @Override
    public boolean canCast(AbilityContext ctx) {
        return Domains.refusal(ctx.player(), IncompleteDomains.spec(ctx.player())).isEmpty();
    }

    @Override
    public Component blockedMessage(ServerPlayer player) {
        return Domains.refusal(player, IncompleteDomains.spec(player)).orElseGet(() -> super.blockedMessage(player));
    }

    @Override
    public void execute(AbilityContext ctx) {
        Domains.open(ctx.player(), IncompleteDomains.spec(ctx.player()), DomainExpansions.release(ctx.slot()));
    }

    @Override
    public Component unlockHint() {
        return Component.translatable("ability.kegare.incomplete_domain.unlock",
                Component.translatable(DomainConfig.INCOMPLETE_UNLOCK_GRADE.get().translationKey()), DomainConfig.INCOMPLETE_UNLOCK_REFINEMENT.get());
    }

    @Override
    public Object[] descriptionArgs() {
        return new Object[] {ConfigText.percent(DomainConfig.INCOMPLETE_METER_SHARE), ConfigText.number(DomainConfig.INCOMPLETE_RADIUS),
                ConfigText.times(DomainConfig.INCOMPLETE_DURATION, DomainConfig.MASTERY_LEAST_SHARE), ConfigText.percent(DomainConfig.INCOMPLETE_TECHNIQUE_COST),
                Math.round((DomainConfig.INCOMPLETE_COOLDOWN_RATE.get() - 1.0) * 100) + "%", ConfigText.percent(DomainConfig.INCOMPLETE_OUTPUT),
                ConfigText.number(DomainConfig.INCOMPLETE_BURNOUT), ConfigText.times(DomainConfig.INCOMPLETE_DURATION, DomainConfig.MASTERY_MOST_SHARE)};
    }
}

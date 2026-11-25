package com.cnethuka.kegare.dominion;

import com.cnethuka.kegare.api.ability.AbilityCategory;
import com.cnethuka.kegare.api.ability.AbilityContext;
import com.cnethuka.kegare.api.ability.BaseAbility;
import com.cnethuka.kegare.api.ability.CastType;
import com.cnethuka.kegare.api.visual.AbilityVisuals;
import com.cnethuka.kegare.config.ConfigText;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * The Incomplete Dominion (shared, Malice, INSTANT; DESIGN §3.35): the inner space expanded with Malice alone. It takes half the
 * Dominion Meter and its cost in Malice, and opens a smaller, weaker barrier, pitch black, round its caster for a shorter time,
 * the same grey wasteland inside for everyone who opens one. It finds nobody (no sure-hit); instead its caster's technique runs better
 * inside it while it stands. Slotted on the bar like any shared ability, never on V. Learned through the unlock framework
 * ({@link IncompleteDominions}); Severed players never have it.
 */
public final class IncompleteDominion extends BaseAbility {
    private static final AbilityVisuals VISUALS = AbilityVisuals.builder().cast(DominionIds.ANIM_INCOMPLETE_CAST, null).build();

    IncompleteDominion() {
        super(DominionIds.INCOMPLETE, CastType.INSTANT);
    }

    // Shared ability: no technique's surroundings, and none of the boosts it gives the technique.
    @Override public double cost(ServerPlayer player) { return DominionConfig.INCOMPLETE_COST.get(); }
    @Override public int cooldownTicks(ServerPlayer player) { return DominionConfig.ticks(DominionConfig.INCOMPLETE_COOLDOWN); }
    @Override public AbilityCategory category() { return AbilityCategory.MALICE; }
    @Override public AbilityVisuals visuals() { return VISUALS; }

    @Override
    public boolean canCast(AbilityContext ctx) {
        return Dominions.refusal(ctx.player(), IncompleteDominions.spec(ctx.player())).isEmpty();
    }

    @Override
    public Component blockedMessage(ServerPlayer player) {
        return Dominions.refusal(player, IncompleteDominions.spec(player)).orElseGet(() -> super.blockedMessage(player));
    }

    @Override
    public void execute(AbilityContext ctx) {
        Dominions.open(ctx.player(), IncompleteDominions.spec(ctx.player()), FullDominions.release(ctx.slot()));
    }

    @Override
    public Component unlockHint() {
        return Component.translatable("ability.kegare.incomplete_dominion.unlock",
                Component.translatable(DominionConfig.INCOMPLETE_UNLOCK_GRADE.get().translationKey()), DominionConfig.INCOMPLETE_UNLOCK_REFINEMENT.get());
    }

    @Override
    public Object[] descriptionArgs() {
        return new Object[] {ConfigText.percent(DominionConfig.INCOMPLETE_METER_SHARE), ConfigText.number(DominionConfig.INCOMPLETE_RADIUS),
                ConfigText.number(DominionConfig.INCOMPLETE_DURATION), ConfigText.percent(DominionConfig.INCOMPLETE_TECHNIQUE_COST),
                Math.round((DominionConfig.INCOMPLETE_COOLDOWN_RATE.get() - 1.0) * 100) + "%", ConfigText.percent(DominionConfig.INCOMPLETE_OUTPUT),
                ConfigText.number(DominionConfig.INCOMPLETE_BURNOUT)};
    }
}

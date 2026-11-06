package com.cnethuka.kegare.innate_technique.puppet_manipulation;

import com.cnethuka.kegare.innatetechnique.Ability;
import com.cnethuka.kegare.innatetechnique.AbilityContext;
import com.cnethuka.kegare.innatetechnique.CastManager;
import com.cnethuka.kegare.innatetechnique.CastType;
import java.util.Optional;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Strings (INSTANT, two stages, like Swap's Anchor). Stage 1: fire a string at the target you're looking at. Stage 2,
 * same key: Reel In, or Tie if you're now looking at something else. {@link #cost} is live, so the HUD and Sorcerer
 * screen show the current stage's.
 */
public final class StringsAbility extends PuppetAbility {
    StringsAbility() {
        super("strings", CastType.INSTANT);
    }

    @Override
    public double cost(ServerPlayer p) {
        if (!Strings.isAttached(p)) return PuppetConfig.STRINGS_FIRE_COST.get();
        return Strings.variation(p, false) == StringsVariation.TIE ? PuppetConfig.STRINGS_TIE_COST.get() : PuppetConfig.STRINGS_REEL_COST.get();
    }

    /** Read after the cast: a string still attached means it was only just fired. */
    @Override
    public int cooldownTicks(ServerPlayer p) {
        return Strings.isAttached(p) ? PuppetConfig.ticks(PuppetConfig.STRINGS_FIRE_COOLDOWN) : PuppetConfig.ticks(PuppetConfig.STRINGS_COOLDOWN);
    }

    /** Whatever stage it's at: the fire's lockout is only there to stop a double press. */
    @Override
    public int shownCooldownTicks(ServerPlayer p) {
        return PuppetConfig.ticks(PuppetConfig.STRINGS_COOLDOWN);
    }

    @Override
    public Optional<Ability> special() {
        return Optional.of(PuppetManipulation.ZIP_LEASH);
    }

    @Override
    public boolean canCast(AbilityContext ctx) {
        ServerPlayer p = ctx.player();
        return Strings.isAttached(p) ? Strings.canMoveAnything(p, Strings.variation(p, false))
                : PuppetTargeting.lookedAtStringTarget(p, PuppetConfig.STRINGS_RANGE.get()).isPresent();
    }

    @Override
    public Component blockedMessage(ServerPlayer player) {
        return Component.translatable(Strings.isAttached(player) ? "message.kegare.strings.immovable" : "message.kegare.strings.no_target");
    }

    @Override
    public void execute(AbilityContext ctx) {
        ServerPlayer p = ctx.player();
        if (Strings.isAttached(p)) {
            Strings.stageTwo(p, Strings.variation(p, false));
            return;
        }
        var target = PuppetTargeting.lookedAtStringTarget(p, PuppetConfig.STRINGS_RANGE.get());
        if (target.isPresent()) Strings.fire(p, target.get());
        else CastManager.fizzleKeepingCost(p);
    }
}

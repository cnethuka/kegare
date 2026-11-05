package com.cnethuka.kegare.innate_technique.puppet_manipulation;

import com.cnethuka.kegare.innatetechnique.AbilityContext;
import com.cnethuka.kegare.innatetechnique.CastManager;
import com.cnethuka.kegare.innatetechnique.CastType;
import com.cnethuka.kegare.innatetechnique.visual.AbilityVisuals;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Special of Puppet Creation (INSTANT): possess the puppet you're looking at, or your nearest one ({@link Possession}).
 * R again returns (free; nothing else can be cast meanwhile). Shares Puppet Creation's cooldown.
 */
public final class PossessAbility extends PuppetAbility.Special {
    private static final AbilityVisuals VISUALS = AbilityVisuals.builder().cast(PuppetIds.ANIM_POSSESS_CAST, null).build();

    PossessAbility() {
        super("possess", CastType.INSTANT, () -> PuppetManipulation.PUPPET_CREATION);
    }

    @Override public double cost(ServerPlayer p) { return PuppetConfig.POSSESS_COST.get(); }
    @Override public AbilityVisuals visuals() { return VISUALS; }

    @Override
    public boolean canCast(AbilityContext ctx) {
        return PuppetTargeting.lookedAtOrNearest(ctx.player(), PuppetConfig.POSSESS_RANGE.get()).isPresent();
    }

    @Override
    public Component blockedMessage(ServerPlayer player) {
        return Component.translatable("message.kegare.possess.no_puppet", PuppetConfig.POSSESS_RANGE.get().intValue());
    }

    @Override
    public void execute(AbilityContext ctx) {
        ServerPlayer player = ctx.player();
        PuppetEntity puppet = PuppetTargeting.lookedAtOrNearest(player, PuppetConfig.POSSESS_RANGE.get()).orElse(null);
        if (puppet == null) {
            CastManager.fizzleKeepingCost(player);
            return;
        }
        Possession.possess(player, puppet);
    }
}

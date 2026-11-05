package com.cnethuka.kegare.innate_technique.puppet_manipulation;

import com.cnethuka.kegare.innatetechnique.AbilityContext;
import com.cnethuka.kegare.innatetechnique.CastManager;
import com.cnethuka.kegare.innatetechnique.CastType;
import com.cnethuka.kegare.innatetechnique.visual.AbilityVisuals;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** Curio Burst (INSTANT): detonate the puppet you're looking at ({@link CurioBursts}). Damage grows with its tier. */
public final class CurioBurstAbility extends PuppetAbility {
    private static final AbilityVisuals VISUALS = AbilityVisuals.builder().cast(PuppetIds.ANIM_CURIO_BURST_CAST, null).build();

    CurioBurstAbility() {
        super("curio_burst", CastType.INSTANT);
    }

    @Override public double cost(ServerPlayer p) { return PuppetConfig.CURIO_COST.get(); }
    @Override public int cooldownTicks(ServerPlayer p) { return PuppetConfig.ticks(PuppetConfig.CURIO_COOLDOWN); }
    @Override public AbilityVisuals visuals() { return VISUALS; }
    @Override public java.util.Optional<com.cnethuka.kegare.innatetechnique.Ability> special() { return java.util.Optional.of(PuppetManipulation.CHAIN_BURST); }

    @Override
    public boolean canCast(AbilityContext ctx) {
        return PuppetTargeting.lookedAtPuppet(ctx.player(), PuppetConfig.CURIO_RANGE.get()).isPresent();
    }

    @Override
    public Component blockedMessage(ServerPlayer player) {
        return Component.translatable("message.kegare.curio_burst.no_puppet");
    }

    @Override
    public void execute(AbilityContext ctx) {
        ServerPlayer owner = ctx.player();
        PuppetEntity puppet = PuppetTargeting.lookedAtPuppet(owner, PuppetConfig.CURIO_RANGE.get()).orElse(null);
        if (puppet == null) {
            CastManager.fizzleKeepingCost(owner);
            return;
        }
        CurioBursts.detonate(puppet, owner);
    }
}

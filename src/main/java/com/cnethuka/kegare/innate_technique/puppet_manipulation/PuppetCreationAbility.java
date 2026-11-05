package com.cnethuka.kegare.innate_technique.puppet_manipulation;

import com.cnethuka.kegare.innatetechnique.AbilityContext;
import com.cnethuka.kegare.innatetechnique.CastType;
import com.cnethuka.kegare.innatetechnique.visual.AbilityVisuals;
import com.cnethuka.kegare.innatetechnique.visual.VisualPhase;
import com.cnethuka.kegare.innatetechnique.visual.Visual;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Puppet Creation (CHARGED): hold to charge Malice into a puppet, release to make it beside you. The charge picks its
 * {@link PuppetTier}: a tap, half or full, each with its own cost and health. No limit but the server's safety cap; at
 * the cap it fizzles for free.
 */
public final class PuppetCreationAbility extends PuppetAbility {
    private static final AbilityVisuals VISUALS = AbilityVisuals.builder()
            .chargeLoop(PuppetIds.ANIM_CREATE_CHARGE, null)
            .release(PuppetIds.ANIM_CREATE_RELEASE, null)
            .anchor(VisualPhase.RELEASE, Visual.Anchor.BODY)
            .build();

    PuppetCreationAbility() {
        super("puppet_creation", CastType.CHARGED);
    }

    /** The cheapest release (a tap): what pressing the key checks, and what the HUD shows. */
    @Override public double cost(ServerPlayer p) { return PuppetTier.TAP.cost(); }
    @Override public double releaseCost(ServerPlayer p, float charge) { return PuppetTier.forCharge(charge).cost(); }
    @Override public int cooldownTicks(ServerPlayer p) { return PuppetConfig.ticks(PuppetConfig.CREATION_COOLDOWN); }
    @Override public int maxChargeTicks(ServerPlayer p) { return Math.max(1, PuppetConfig.ticks(PuppetConfig.CREATION_FULL_CHARGE)); }
    @Override public AbilityVisuals visuals() { return VISUALS; }
    @Override public java.util.Optional<com.cnethuka.kegare.innatetechnique.Ability> special() { return java.util.Optional.of(PuppetManipulation.POSSESS); }

    @Override public boolean canCast(AbilityContext ctx) { return !Puppets.atCap(ctx.player()); }
    @Override public boolean canRelease(AbilityContext ctx) { return !Puppets.atCap(ctx.player()); }

    @Override
    public Component blockedMessage(ServerPlayer player) {
        return Component.translatable("message.kegare.puppet.cap", PuppetConfig.SAFETY_CAP.get());
    }

    @Override
    public void execute(AbilityContext ctx) {
        Puppets.create(ctx.player(), PuppetTier.forCharge(ctx.charge()));
    }
}

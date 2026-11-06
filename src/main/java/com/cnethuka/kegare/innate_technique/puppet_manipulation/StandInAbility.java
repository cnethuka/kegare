package com.cnethuka.kegare.innate_technique.puppet_manipulation;

import com.cnethuka.kegare.innatetechnique.AbilityContext;
import com.cnethuka.kegare.innatetechnique.CastManager;
import com.cnethuka.kegare.innatetechnique.CastType;
import com.cnethuka.kegare.innatetechnique.visual.AbilityVisuals;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Special of Understudy (INSTANT): for {@code duration} one puppet (looked at, else nearest) grows to your size and
 * copies you exactly, moves, held items, armor and name ({@link PuppetEntity#startStandIn}); then it shrinks back. One
 * Stand-In per owner at a time: casting again ends the old one. Its look is only ever its OWNER's.
 */
public final class StandInAbility extends PuppetAbility.Special {
    private static final AbilityVisuals VISUALS = AbilityVisuals.builder().cast(PuppetIds.ANIM_STAND_IN_CAST, null).build();
    /** Each owner's current Stand-In (runtime). */
    private static final Map<UUID, PuppetEntity> CURRENT = new HashMap<>();

    StandInAbility() {
        super("stand_in", CastType.INSTANT, () -> PuppetManipulation.UNDERSTUDY);
    }

    @Override public double cost(ServerPlayer p) { return PuppetConfig.STAND_IN_COST.get(); }
    @Override public AbilityVisuals visuals() { return VISUALS; }
    @Override public boolean hasNoTarget() { return true; } // grows your own puppet

    @Override
    public boolean canCast(AbilityContext ctx) {
        return PuppetTargeting.lookedAtOrNearest(ctx.player(), PuppetConfig.UNDERSTUDY_RANGE.get()).isPresent();
    }

    @Override
    public Component blockedMessage(ServerPlayer player) {
        return Component.translatable("message.kegare.possess.no_puppet", PuppetConfig.UNDERSTUDY_RANGE.get().intValue());
    }

    @Override
    public void execute(AbilityContext ctx) {
        ServerPlayer owner = ctx.player();
        PuppetEntity puppet = PuppetTargeting.lookedAtOrNearest(owner, PuppetConfig.UNDERSTUDY_RANGE.get()).orElse(null);
        if (puppet == null) {
            CastManager.fizzleKeepingCost(owner);
            return;
        }
        PuppetEntity old = CURRENT.put(owner.getUUID(), puppet);
        if (old != null && old != puppet && old.isAlive()) old.endStandIn();
        puppet.startStandIn(owner, PuppetConfig.ticks(PuppetConfig.STAND_IN_DURATION));
        Puppets.standInEffect(puppet);
    }

    static void clear() {
        CURRENT.clear();
    }
}

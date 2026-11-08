package com.cnethuka.kegare.innate_technique.time_manipulation;

import com.cnethuka.kegare.innatetechnique.AbilityContext;
import com.cnethuka.kegare.innatetechnique.CastType;
import com.cnethuka.kegare.innatetechnique.visual.AbilityVisuals;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Standstill (the ultimate, V, INSTANT; no special): time stops round you for {@code seconds} ({@link TimeStops}). One per level at a
 * time: nobody (an immune Time user included) starts another while one stands. Afterwards {@code burnoutSeconds} of Burnout
 * (DESIGN §3.25) on top of the cooldown.
 */
public final class StandstillAbility extends TimeAbility {
    private static final AbilityVisuals VISUALS = AbilityVisuals.builder().cast(TimeIds.ANIM_STANDSTILL_CAST, null).build();

    StandstillAbility() {
        super("standstill", CastType.INSTANT);
    }

    @Override public double cost(ServerPlayer p) { return TimeConfig.STANDSTILL_COST.get(); }
    @Override public int cooldownTicks(ServerPlayer p) { return TimeConfig.ticks(TimeConfig.STANDSTILL_COOLDOWN); }
    @Override public AbilityVisuals visuals() { return VISUALS; }
    @Override public boolean hasNoTarget() { return true; }

    @Override
    public boolean canCast(AbilityContext ctx) {
        return !TimeStops.active(ctx.player().level());
    }

    @Override
    public Component blockedMessage(ServerPlayer p) {
        return Component.translatable("message.kegare.time.already_stopped");
    }

    @Override
    public void execute(AbilityContext ctx) {
        ServerPlayer p = ctx.player();
        TimeStops.start(p, p.position(), TimeConfig.STANDSTILL_RADIUS.get(), TimeConfig.ticks(TimeConfig.STANDSTILL_SECONDS));
    }
}

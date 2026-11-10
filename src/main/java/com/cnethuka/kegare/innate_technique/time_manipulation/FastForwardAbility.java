package com.cnethuka.kegare.innate_technique.time_manipulation;

import com.cnethuka.kegare.innatetechnique.AbilityContext;
import com.cnethuka.kegare.innatetechnique.CastManager;
import com.cnethuka.kegare.innatetechnique.CastType;
import com.cnethuka.kegare.innatetechnique.EndReason;
import com.cnethuka.kegare.innatetechnique.visual.AbilityVisuals;
import com.cnethuka.kegare.sound.KegareSounds;
import java.util.List;
import net.minecraft.server.level.ServerPlayer;

/**
 * Fast Forward (special of Accelerate, INSTANT): {@code seconds} off every OTHER cooldown of your technique at once, but never the
 * ultimate's, Burnout (not a cooldown) or a shared ability's. Switches Accelerate off if it's on and puts it on their shared cooldown.
 * Clock hands whirl forward round you.
 */
public final class FastForwardAbility extends TimeAbility.Special {
    private static final AbilityVisuals VISUALS = AbilityVisuals.builder().cast(TimeIds.ANIM_FAST_FORWARD_CAST, null).build();

    FastForwardAbility() {
        super("fast_forward", CastType.INSTANT, () -> TimeManipulation.ACCELERATE);
    }

    @Override public double cost(ServerPlayer p) { return TimeConfig.FAST_FORWARD_COST.get(); }
    @Override
    public Object[] descriptionArgs() {
        return new Object[] {com.cnethuka.kegare.config.ConfigText.number(TimeConfig.FAST_FORWARD_SECONDS)};
    }
    @Override public int cooldownTicks(ServerPlayer p) { return TimeConfig.ticks(TimeConfig.FAST_FORWARD_COOLDOWN); }
    @Override public AbilityVisuals visuals() { return VISUALS; }
    @Override public boolean hasNoTarget() { return true; }

    @Override
    public void execute(AbilityContext ctx) {
        ServerPlayer p = ctx.player();
        if (TimeManipulation.ACCELERATE.isActive(p)) CastManager.switchOff(p, TimeManipulation.ACCELERATE, EndReason.CONSUMED);
        AccelerateAbility.fastForward(p, ctx.innateTechnique(), TimeConfig.ticks(TimeConfig.FAST_FORWARD_SECONDS));
        TimeManipulation.sound(p, KegareSounds.TIME_WHIRL, 1f, 1f);
        TimeManipulation.fx(p, TimeManipulation.fx(TimeNet.Fx.Kind.FAST_FORWARD, p.getId(), List.of(), 0f, 0));
    }
}

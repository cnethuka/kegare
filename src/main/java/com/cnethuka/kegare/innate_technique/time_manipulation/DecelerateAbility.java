package com.cnethuka.kegare.innate_technique.time_manipulation;

import com.cnethuka.kegare.innatetechnique.Ability;
import com.cnethuka.kegare.innatetechnique.AbilityContext;
import com.cnethuka.kegare.innatetechnique.CastType;
import com.cnethuka.kegare.innatetechnique.visual.AbilityVisuals;
import com.cnethuka.kegare.sound.KegareSounds;
import java.util.Optional;
import net.minecraft.server.level.ServerPlayer;

/** Decelerate (INSTANT): throws a spinning clock hand ({@link DecelerateEntity}). Special: Stasis Field. */
public final class DecelerateAbility extends TimeAbility {
    private static final AbilityVisuals VISUALS = AbilityVisuals.builder().cast(TimeIds.ANIM_DECELERATE_CAST, null).build();

    DecelerateAbility() {
        super("decelerate", CastType.INSTANT);
    }

    @Override public double cost(ServerPlayer p) { return TimeConfig.DECELERATE_COST.get(); }
    @Override public int cooldownTicks(ServerPlayer p) { return TimeConfig.ticks(TimeConfig.DECELERATE_COOLDOWN); }
    @Override public AbilityVisuals visuals() { return VISUALS; }
    @Override public Optional<Ability> special() { return Optional.of(TimeManipulation.STASIS_FIELD); }

    @Override
    public void execute(AbilityContext ctx) {
        ServerPlayer p = ctx.player();
        DecelerateEntity hand = new DecelerateEntity(p.level(), p);
        hand.shootFromRotation(p, p.getXRot(), p.getYRot(), 0f, TimeConfig.DECELERATE_SPEED.get().floatValue(), 0.4f);
        p.level().addFreshEntity(hand);
        TimeManipulation.sound(p, KegareSounds.TIME_THROW, 0.9f, 1f);
    }
}

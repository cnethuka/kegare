package com.cnethuka.kegare.innate_technique.time_manipulation;

import com.cnethuka.kegare.innatetechnique.AbilityContext;
import com.cnethuka.kegare.innatetechnique.CastType;
import com.cnethuka.kegare.innatetechnique.visual.AbilityVisuals;
import com.cnethuka.kegare.sound.KegareSounds;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Stasis Field (special of Decelerate, INSTANT): a field ({@code radius}, {@code seconds}) where the block you're looking at is
 * ({@link StasisFields}). Fizzles for free with no block in range.
 */
public final class StasisFieldAbility extends TimeAbility.Special {
    private static final AbilityVisuals VISUALS = AbilityVisuals.builder().cast(TimeIds.ANIM_STASIS_FIELD_CAST, null).build();

    StasisFieldAbility() {
        super("stasis_field", CastType.INSTANT, () -> TimeManipulation.DECELERATE);
    }

    @Override public double cost(ServerPlayer p) { return TimeConfig.STASIS_COST.get(); }
    @Override
    public Object[] descriptionArgs() {
        return new Object[] {com.cnethuka.kegare.config.ConfigText.number(TimeConfig.STASIS_RADIUS),
                com.cnethuka.kegare.config.ConfigText.number(TimeConfig.STASIS_SECONDS),
                com.cnethuka.kegare.config.ConfigText.percent(TimeConfig.STASIS_PROJECTILE_SPEED)};
    }
    @Override public int cooldownTicks(ServerPlayer p) { return TimeConfig.ticks(TimeConfig.STASIS_COOLDOWN); }
    @Override public AbilityVisuals visuals() { return VISUALS; }

    @Override
    public boolean canCast(AbilityContext ctx) {
        return TimeTargeting.lookedAtBlock(ctx.player(), TimeConfig.STASIS_RANGE.get()).isPresent();
    }

    @Override
    public Component blockedMessage(ServerPlayer p) {
        return Component.translatable("message.kegare.time.no_block_field", Long.toString(Math.round(TimeConfig.STASIS_RANGE.get())));
    }

    @Override
    public void execute(AbilityContext ctx) {
        ServerPlayer p = ctx.player();
        BlockHitResult hit = TimeTargeting.lookedAtBlock(p, TimeConfig.STASIS_RANGE.get()).orElseThrow();
        Vec3 centre = TimeTargeting.topOf(p, hit.getBlockPos());
        StasisFields.open(p, centre, TimeConfig.STASIS_RADIUS.get(), TimeConfig.ticks(TimeConfig.STASIS_SECONDS));
        p.level().playSound(null, centre.x, centre.y, centre.z, KegareSounds.TIME_STASIS.value(), net.minecraft.sounds.SoundSource.PLAYERS, 1f, 1f);
    }
}

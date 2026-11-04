package com.cnethuka.kegare.innate_technique.rime;

import com.cnethuka.kegare.innatetechnique.AbilityContext;
import com.cnethuka.kegare.innatetechnique.CastType;
import com.cnethuka.kegare.innatetechnique.visual.AbilityVisuals;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/**
 * CHARGED: hold up to the max charge, release to throw a big, slow lance whose damage scales with charge (two freeze
 * stacks). Releasing before the minimum charge fizzles: no cost, no cooldown (CastManager).
 */
public final class GlacialLanceAbility extends RimeAbility {
    private static final AbilityVisuals VISUALS = AbilityVisuals.builder()
            .chargeLoop(RimeIds.ANIM_GLACIAL_LANCE_CHARGE, RimeIds.FX_GLACIAL_LANCE_CHARGE)
            .release(RimeIds.ANIM_GLACIAL_LANCE_RELEASE, null)
            .projectileTrail(RimeIds.FX_GLACIAL_LANCE_TRAIL)
            .impact(RimeIds.FX_GLACIAL_LANCE_IMPACT)
            .build();

    GlacialLanceAbility() {
        super("glacial_lance", CastType.CHARGED);
    }

    @Override public double cost(ServerPlayer p) { return RimeConfig.LANCE_COST.get() * environment(p); }
    @Override public int cooldownTicks(ServerPlayer p) { return RimeConfig.ticks(RimeConfig.LANCE_COOLDOWN); }
    @Override public int minChargeTicks(ServerPlayer p) { return RimeConfig.ticks(RimeConfig.LANCE_MIN_CHARGE); }
    @Override public int maxChargeTicks(ServerPlayer p) { return Math.max(1, RimeConfig.ticks(RimeConfig.LANCE_MAX_CHARGE)); }
    @Override public AbilityVisuals visuals() { return VISUALS; }

    @Override
    public void tick(AbilityContext ctx) {
        if (ctx.activeTicks() == 1) {
            ServerPlayer p = ctx.player();
            p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1f, 0.7f);
        }
    }

    @Override
    public void execute(AbilityContext ctx) {
        ServerPlayer player = ctx.player();
        GlacialLanceEntity lance = new GlacialLanceEntity(player.level(), player, ctx.charge());
        lance.shootFromRotation(player, player.getXRot(), player.getYRot(), 0f, RimeConfig.LANCE_SPEED.get().floatValue(), 0f);
        player.level().addFreshEntity(lance);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.TRIDENT_THROW, SoundSource.PLAYERS, 1f, 0.8f);
        onCast(player);
    }
}

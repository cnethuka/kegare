package com.cnethuka.kegare.innate_technique.rime;

import com.cnethuka.kegare.innatetechnique.AbilityContext;
import com.cnethuka.kegare.innatetechnique.CastType;
import com.cnethuka.kegare.innatetechnique.visual.AbilityVisuals;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/** INSTANT: a fast ice shard. Light damage, short Slowness, one freeze stack. */
public final class FrostShardAbility extends RimeAbility {
    private static final AbilityVisuals VISUALS = AbilityVisuals.builder()
            .cast(RimeIds.ANIM_FROST_SHARD_CAST, null)
            .projectileTrail(RimeIds.FX_FROST_SHARD_TRAIL)
            .impact(RimeIds.FX_FROST_SHARD_IMPACT)
            .build();

    FrostShardAbility() {
        super("frost_shard", CastType.INSTANT);
    }

    @Override public double cost(ServerPlayer p) { return RimeConfig.SHARD_COST.get() * environment(p); }
    @Override public int cooldownTicks(ServerPlayer p) { return RimeConfig.ticks(RimeConfig.SHARD_COOLDOWN); }
    @Override public AbilityVisuals visuals() { return VISUALS; }

    @Override
    public void execute(AbilityContext ctx) {
        ServerPlayer player = ctx.player();
        FrostShardEntity shard = new FrostShardEntity(player.level(), player);
        shard.shootFromRotation(player, player.getXRot(), player.getYRot(), 0f, RimeConfig.SHARD_SPEED.get().floatValue(), 0f);
        player.level().addFreshEntity(shard);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_HIT, SoundSource.PLAYERS, 1f, 1.6f);
        onCast(player);
    }
}

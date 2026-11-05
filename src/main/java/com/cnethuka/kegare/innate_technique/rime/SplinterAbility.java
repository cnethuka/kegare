package com.cnethuka.kegare.innate_technique.rime;

import com.cnethuka.kegare.innatetechnique.AbilityContext;
import com.cnethuka.kegare.innatetechnique.CastType;
import com.cnethuka.kegare.innatetechnique.visual.AbilityVisuals;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/**
 * Special of Frost Shard (INSTANT): three smaller shards in a fan. Each deals less damage and adds a freeze stack; all
 * three can hit the same target (sibling shards skip each other's hurt cooldown, see {@link SplinterShardEntity}).
 */
public final class SplinterAbility extends RimeSpecial {
    public static final int SHARDS = 3;
    private static final AbilityVisuals VISUALS = AbilityVisuals.builder()
            .cast(RimeIds.ANIM_SPLINTER_CAST, RimeIds.FX_SPLINTER_CAST)
            .projectileTrail(RimeIds.FX_SPLINTER_TRAIL)
            .impact(RimeIds.FX_FROST_SHARD_IMPACT)
            .build();

    SplinterAbility() {
        super("splinter", CastType.INSTANT, () -> Rime.FROST_SHARD);
    }

    @Override public double cost(ServerPlayer p) { return RimeConfig.SPLINTER_COST.get() * environment(p); }
    @Override public int cooldownTicks(ServerPlayer p) { return RimeConfig.ticks(RimeConfig.SPLINTER_COOLDOWN); }
    @Override public AbilityVisuals visuals() { return VISUALS; }

    @Override
    public void execute(AbilityContext ctx) {
        ServerPlayer player = ctx.player();
        int cast = player.getData(RimeRegistry.TRACKER).nextSplinterCast();
        float spread = RimeConfig.SPLINTER_SPREAD.get().floatValue();
        float speed = RimeConfig.SPLINTER_SPEED.get().floatValue();
        for (int i = 0; i < SHARDS; i++) {
            float yaw = player.getYRot() + (i - (SHARDS - 1) / 2f) * spread;
            SplinterShardEntity shard = new SplinterShardEntity(player.level(), player, cast);
            shard.shootFromRotation(player, player.getXRot(), yaw, 0f, speed, 0f);
            player.level().addFreshEntity(shard);
        }
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.PLAYERS, 1f, 1.8f);
        onCast(player);
    }
}

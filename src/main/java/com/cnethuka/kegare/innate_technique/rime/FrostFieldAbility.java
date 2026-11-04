package com.cnethuka.kegare.innate_technique.rime;

import com.cnethuka.kegare.innatetechnique.AbilityContext;
import com.cnethuka.kegare.innatetechnique.CastType;
import com.cnethuka.kegare.innatetechnique.visual.AbilityVisuals;
import java.util.List;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** CHANNELED: a cone of frost in front of you while held. Slows, and adds a freeze stack every second. */
public final class FrostFieldAbility extends RimeAbility {
    private static final int SLOW_REFRESH_TICKS = 4;
    private static final AbilityVisuals VISUALS = AbilityVisuals.builder()
            .channelLoop(RimeIds.ANIM_FROST_FIELD_CHANNEL, RimeIds.FX_FROST_FIELD_CONE)
            .build();

    FrostFieldAbility() {
        super("frost_field", CastType.CHANNELED);
    }

    @Override public double cost(ServerPlayer p) { return RimeConfig.FIELD_COST.get() * environment(p); }
    @Override public double upkeepPerSecond(ServerPlayer p) { return RimeConfig.FIELD_UPKEEP.get() * environment(p); }
    @Override public int cooldownTicks(ServerPlayer p) { return RimeConfig.ticks(RimeConfig.FIELD_COOLDOWN); }
    @Override public int maxChannelTicks(ServerPlayer p) { return Math.max(1, RimeConfig.ticks(RimeConfig.FIELD_MAX_DURATION)); }
    @Override public AbilityVisuals visuals() { return VISUALS; }

    @Override
    public void execute(AbilityContext ctx) {
        ServerPlayer p = ctx.player();
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.POWDER_SNOW_STEP, SoundSource.PLAYERS, 1.2f, 0.6f);
        onCast(p);
    }

    @Override
    public void tick(AbilityContext ctx) {
        ServerPlayer player = ctx.player();
        boolean slowTick = ctx.activeTicks() % SLOW_REFRESH_TICKS == 0;
        boolean stackTick = ctx.activeTicks() % 20 == 0;
        if (!slowTick && !stackTick) return;
        int amplifier = RimeConfig.FIELD_SLOWNESS_AMPLIFIER.get();
        int stacks = RimeConfig.FIELD_FREEZE_STACKS_PER_SECOND.get();
        for (LivingEntity target : targets(player)) {
            if (slowTick) target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, SLOW_REFRESH_TICKS + 16, amplifier), player);
            if (stackTick) FreezeService.addStacks(target, stacks, player);
        }
        if (ctx.activeTicks() % 10 == 0) {
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.POWDER_SNOW_STEP, SoundSource.PLAYERS, 0.6f, 0.8f);
        }
    }

    /** Living things in the cone: within range, inside the half-angle of your look, and in line of sight. */
    static List<LivingEntity> targets(ServerPlayer player) {
        double range = RimeConfig.FIELD_RANGE.get();
        double cos = Math.cos(Math.toRadians(RimeConfig.FIELD_HALF_ANGLE.get()));
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        AABB box = player.getBoundingBox().inflate(range);
        return player.level().getEntitiesOfClass(LivingEntity.class, box, e -> {
            if (e == player || !e.isAlive() || e.isSpectator()) return false;
            Vec3 to = e.getBoundingBox().getCenter().subtract(eye);
            double dist = to.length();
            if (dist > range + e.getBbWidth() / 2 || dist < 1.0e-3) return dist < 1.0e-3;
            return to.normalize().dot(look) >= cos && player.hasLineOfSight(e);
        });
    }
}

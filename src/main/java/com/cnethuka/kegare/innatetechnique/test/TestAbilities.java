package com.cnethuka.kegare.innatetechnique.test;

import com.cnethuka.kegare.Kegare;
import com.cnethuka.kegare.config.KegareServerConfig;
import com.cnethuka.kegare.innatetechnique.Ability;
import com.cnethuka.kegare.innatetechnique.AbilityContext;
import com.cnethuka.kegare.innatetechnique.BaseAbility;
import com.cnethuka.kegare.innatetechnique.CastType;
import com.cnethuka.kegare.innatetechnique.EndReason;
import com.cnethuka.kegare.innatetechnique.Requirement;
import com.cnethuka.kegare.innatetechnique.SpecialAbility;
import com.cnethuka.kegare.trait.KegareTraits;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * TEST CONTENT. Placeholder abilities that only exist to prove the cast pipeline
 * (one per cast type, plus an ultimate, and two specials: one needing an active toggle, one needing a trait). Not balanced, not lore. Delete when real innate techniques exist.
 * All numbers come from the {@code [test_abilities]} server config section.
 */
public final class TestAbilities {
    private TestAbilities() {}

    /** INSTANT: launches the caster horizontally in the direction they face. Special: {@link #BLINK}. */
    public static final BaseAbility DASH = new BaseAbility(Kegare.id("test_dash"), CastType.INSTANT) {
        @Override public Optional<Ability> special() { return Optional.of(BLINK); }
        @Override public double cost(ServerPlayer p) { return KegareServerConfig.DASH_COST.get(); }
        @Override public int cooldownTicks(ServerPlayer p) { return KegareServerConfig.ticks(KegareServerConfig.DASH_COOLDOWN); }

        @Override
        public void execute(AbilityContext ctx) {
            ServerPlayer p = ctx.player();
            float yaw = p.getYRot() * Mth.DEG_TO_RAD;
            double strength = KegareServerConfig.DASH_STRENGTH.get();
            p.setDeltaMovement(-Mth.sin(yaw) * strength, KegareServerConfig.DASH_LIFT.get(), Mth.cos(yaw) * strength);
            p.hurtMarked = true; // push the new velocity to the client
            p.resetFallDistance();
            particles(ctx.level(), ParticleTypes.CLOUD, p, 12, 0.3);
            sound(ctx.level(), p, SoundEvents.BREEZE_JUMP, 1.0f);
        }
    };

    /** CHARGED: knockback burst whose radius and strength scale with charge. */
    public static final BaseAbility BURST = new BaseAbility(Kegare.id("test_burst"), CastType.CHARGED) {
        @Override public double cost(ServerPlayer p) { return KegareServerConfig.BURST_COST.get(); }
        @Override public int cooldownTicks(ServerPlayer p) { return KegareServerConfig.ticks(KegareServerConfig.BURST_COOLDOWN); }
        @Override public int minChargeTicks(ServerPlayer p) { return KegareServerConfig.ticks(KegareServerConfig.BURST_MIN_CHARGE); }
        @Override public int maxChargeTicks(ServerPlayer p) { return Math.max(1, KegareServerConfig.ticks(KegareServerConfig.BURST_MAX_CHARGE)); }

        @Override
        public void tick(AbilityContext ctx) {
            if (ctx.activeTicks() % 4 == 0) particles(ctx.level(), ParticleTypes.WITCH, ctx.player(), 3 + (int) (ctx.charge() * 6), 0.4);
        }

        @Override
        public void execute(AbilityContext ctx) {
            double radius = Mth.lerp(ctx.charge(), KegareServerConfig.BURST_MIN_RADIUS.get(), KegareServerConfig.BURST_MAX_RADIUS.get());
            double knockback = Mth.lerp(ctx.charge(), KegareServerConfig.BURST_MIN_KNOCKBACK.get(), KegareServerConfig.BURST_MAX_KNOCKBACK.get());
            pushAway(ctx.player(), radius, knockback);
            ring(ctx.level(), ParticleTypes.GUST, ctx.player(), radius);
            sound(ctx.level(), ctx.player(), SoundEvents.WIND_CHARGE_BURST, 0.8f + ctx.charge() * 0.4f);
        }
    };

    /** CHANNELED: levitate while the key is held. Upkeep drains per second. Special: {@link #UPDRAFT}. */
    public static final BaseAbility HOVER = new BaseAbility(Kegare.id("test_hover"), CastType.CHANNELED) {
        @Override public Optional<Ability> special() { return Optional.of(UPDRAFT); }
        @Override public double cost(ServerPlayer p) { return KegareServerConfig.HOVER_COST.get(); }
        @Override public int cooldownTicks(ServerPlayer p) { return KegareServerConfig.ticks(KegareServerConfig.HOVER_COOLDOWN); }
        @Override public double upkeepPerSecond(ServerPlayer p) { return KegareServerConfig.HOVER_UPKEEP.get(); }
        @Override public int maxChannelTicks(ServerPlayer p) { return Math.max(1, KegareServerConfig.ticks(KegareServerConfig.HOVER_MAX_DURATION)); }

        @Override
        public void execute(AbilityContext ctx) {
            // Vanilla Levitation is client-predicted and exempt from the server's anti-flight kick.
            int duration = maxChannelTicks(ctx.player()) + 5;
            ctx.player().addEffect(new MobEffectInstance(MobEffects.LEVITATION, duration, KegareServerConfig.HOVER_LEVITATION_LEVEL.get(), false, false, true));
            sound(ctx.level(), ctx.player(), SoundEvents.ELYTRA_FLYING, 1.6f);
        }

        @Override
        public void tick(AbilityContext ctx) {
            if (ctx.activeTicks() % 2 == 0) particles(ctx.level(), ParticleTypes.END_ROD, ctx.player(), 1, 0.2);
        }

        @Override
        public void onEnd(AbilityContext ctx, EndReason reason) {
            removeOwnEffect(ctx.player(), MobEffects.LEVITATION, maxChannelTicks(ctx.player()) + 5);
            // Soften the landing so testing doesn't kill you.
            int slowFall = KegareServerConfig.ticks(KegareServerConfig.HOVER_LANDING_SLOW_FALL);
            if (reason != EndReason.DEATH && slowFall > 0) {
                ctx.player().addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, slowFall, 0, false, false, true));
            }
        }
    };

    /** TOGGLE: Speed while on, upkeep drains per second. */
    public static final BaseAbility AURA = new BaseAbility(Kegare.id("test_aura"), CastType.TOGGLE) {
        private static final int EFFECT_TICKS = 40;

        @Override public double cost(ServerPlayer p) { return KegareServerConfig.AURA_COST.get(); }
        @Override public int cooldownTicks(ServerPlayer p) { return KegareServerConfig.ticks(KegareServerConfig.AURA_COOLDOWN); }
        @Override public double upkeepPerSecond(ServerPlayer p) { return KegareServerConfig.AURA_UPKEEP.get(); }

        @Override
        public void execute(AbilityContext ctx) {
            applySpeed(ctx.player());
            sound(ctx.level(), ctx.player(), SoundEvents.BEACON_ACTIVATE, 1.5f);
        }

        @Override
        public void tick(AbilityContext ctx) {
            if (ctx.activeTicks() % 20 == 0) applySpeed(ctx.player());
            if (ctx.activeTicks() % 5 == 0) particles(ctx.level(), ParticleTypes.SOUL_FIRE_FLAME, ctx.player(), 2, 0.4);
        }

        @Override
        public void onEnd(AbilityContext ctx, EndReason reason) {
            removeOwnEffect(ctx.player(), MobEffects.MOVEMENT_SPEED, EFFECT_TICKS);
            sound(ctx.level(), ctx.player(), SoundEvents.BEACON_DEACTIVATE, 1.5f);
        }

        private void applySpeed(ServerPlayer p) {
            p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, EFFECT_TICKS, KegareServerConfig.AURA_SPEED_LEVEL.get(), true, false, true));
        }
    };

    /** Special of Dash (INSTANT): a short teleport forward. Requires Aura to be switched on (a condition requirement). */
    public static final SpecialAbility BLINK = new SpecialAbility(Kegare.id("test_blink"), CastType.INSTANT, () -> DASH,
            Requirement.toggleActive(AURA)) {
        @Override public double cost(ServerPlayer p) { return KegareServerConfig.BLINK_COST.get(); }

        @Override
        public void execute(AbilityContext ctx) {
            ServerPlayer p = ctx.player();
            Vec3 look = p.getLookAngle();
            Vec3 step = new Vec3(look.x, 0, look.z).normalize().scale(0.25);
            if (step.lengthSqr() < 1.0e-6) return;
            Vec3 last = Vec3.ZERO;
            int steps = (int) (KegareServerConfig.BLINK_DISTANCE.get() / 0.25);
            for (int i = 1; i <= steps; i++) {
                Vec3 offset = step.scale(i);
                if (!ctx.level().noCollision(p, p.getBoundingBox().move(offset))) break;
                last = offset;
            }
            particles(ctx.level(), ParticleTypes.REVERSE_PORTAL, p, 16, 0.3);
            p.teleportTo(p.getX() + last.x, p.getY(), p.getZ() + last.z);
            p.resetFallDistance();
            particles(ctx.level(), ParticleTypes.REVERSE_PORTAL, p, 16, 0.3);
            sound(ctx.level(), p, SoundEvents.ENDERMAN_TELEPORT, 1.4f);
        }
    };

    /** Special of Hover (CHARGED): launch straight up, higher with more charge. Requires a trait. */
    public static final SpecialAbility UPDRAFT = new SpecialAbility(Kegare.id("test_updraft"), CastType.CHARGED, () -> HOVER,
            Requirement.trait(KegareTraits.SIX_EYES)) {
        @Override public double cost(ServerPlayer p) { return KegareServerConfig.UPDRAFT_COST.get(); }
        @Override public int minChargeTicks(ServerPlayer p) { return KegareServerConfig.ticks(KegareServerConfig.UPDRAFT_MIN_CHARGE); }
        @Override public int maxChargeTicks(ServerPlayer p) { return Math.max(1, KegareServerConfig.ticks(KegareServerConfig.UPDRAFT_MAX_CHARGE)); }

        @Override
        public void tick(AbilityContext ctx) {
            if (ctx.activeTicks() % 3 == 0) particles(ctx.level(), ParticleTypes.CLOUD, ctx.player(), 2 + (int) (ctx.charge() * 4), 0.3);
        }

        @Override
        public void execute(AbilityContext ctx) {
            ServerPlayer p = ctx.player();
            double lift = Mth.lerp(ctx.charge(), KegareServerConfig.UPDRAFT_MIN_LAUNCH.get(), KegareServerConfig.UPDRAFT_MAX_LAUNCH.get());
            p.setDeltaMovement(p.getDeltaMovement().x, lift, p.getDeltaMovement().z);
            p.hurtMarked = true;
            p.resetFallDistance();
            p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 60, 0, false, false, true));
            particles(ctx.level(), ParticleTypes.GUST, p, 3, 0.4);
            sound(ctx.level(), p, SoundEvents.BREEZE_JUMP, 0.7f);
        }
    };

    /** INSTANT ultimate: big knockback shockwave. Stands in for future dominions on the ultimate key. */
    public static final BaseAbility SHOCKWAVE = new BaseAbility(Kegare.id("test_shockwave"), CastType.INSTANT) {
        @Override public double cost(ServerPlayer p) { return KegareServerConfig.SHOCKWAVE_COST.get(); }
        @Override public int cooldownTicks(ServerPlayer p) { return KegareServerConfig.ticks(KegareServerConfig.SHOCKWAVE_COOLDOWN); }

        @Override
        public void execute(AbilityContext ctx) {
            double radius = KegareServerConfig.SHOCKWAVE_RADIUS.get();
            pushAway(ctx.player(), radius, KegareServerConfig.SHOCKWAVE_KNOCKBACK.get());
            ring(ctx.level(), ParticleTypes.SONIC_BOOM, ctx.player(), radius);
            particles(ctx.level(), ParticleTypes.EXPLOSION, ctx.player(), 6, 1.0);
            sound(ctx.level(), ctx.player(), SoundEvents.GENERIC_EXPLODE, 0.8f);
        }
    };

    // ------------------------------------------------------------------ helpers

    private static void pushAway(ServerPlayer caster, double radius, double strength) {
        double radiusSqr = radius * radius;
        for (LivingEntity target : caster.level().getEntitiesOfClass(LivingEntity.class, caster.getBoundingBox().inflate(radius),
                e -> e != caster && e.isAlive() && !e.isSpectator() && e.distanceToSqr(caster) <= radiusSqr)) {
            double dx = caster.getX() - target.getX();
            double dz = caster.getZ() - target.getZ();
            if (dx * dx + dz * dz < 1.0e-4) {
                dx = caster.getRandom().nextDouble() - 0.5;
                dz = caster.getRandom().nextDouble() - 0.5;
            }
            target.knockback(strength, dx, dz);
            target.hurtMarked = true;
        }
    }

    /** Removes an effect only if it looks like ours (short enough), so longer potion effects survive. */
    private static void removeOwnEffect(ServerPlayer player, Holder<MobEffect> effect, int maxOwnDuration) {
        MobEffectInstance current = player.getEffect(effect);
        if (current != null && current.getDuration() <= maxOwnDuration) player.removeEffect(effect);
    }

    private static void particles(ServerLevel level, ParticleOptions type, ServerPlayer p, int count, double spread) {
        level.sendParticles(type, p.getX(), p.getY() + 1.0, p.getZ(), count, spread, 0.5, spread, 0.02);
    }

    private static void ring(ServerLevel level, ParticleOptions type, ServerPlayer p, double radius) {
        int points = Math.max(8, (int) (radius * 6));
        for (int i = 0; i < points; i++) {
            double angle = Math.PI * 2 * i / points;
            level.sendParticles(type, p.getX() + Math.cos(angle) * radius, p.getY() + 0.5, p.getZ() + Math.sin(angle) * radius, 1, 0, 0, 0, 0);
        }
    }

    private static void sound(ServerLevel level, ServerPlayer p, SoundEvent sound, float pitch) {
        level.playSound(null, p.getX(), p.getY(), p.getZ(), sound, SoundSource.PLAYERS, 1.0f, pitch);
    }

    private static void sound(ServerLevel level, ServerPlayer p, Holder<SoundEvent> sound, float pitch) {
        sound(level, p, sound.value(), pitch);
    }
}

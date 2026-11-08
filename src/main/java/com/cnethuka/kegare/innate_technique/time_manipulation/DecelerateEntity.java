package com.cnethuka.kegare.innate_technique.time_manipulation;

import com.cnethuka.kegare.innatetechnique.CooldownRates;
import com.cnethuka.kegare.innatetechnique.InnateTechniqueService;
import com.cnethuka.kegare.sound.KegareSounds;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Decelerate's projectile: a silver clock hand thrown like a blade, spinning end over end (the client draws it,
 * {@code DecelerateRenderer}). A real projectile: blocks stop it, and so does a Stasis Field or a Standstill like any other. On a living
 * target: damage (type {@code kegare:time_manipulation}, the caster credited); then, unless it's a boss (DESIGN §4), Slowness, Mining
 * Fatigue and (if {@link TimeTargetEvent} allows it) the target's ability cooldowns running at {@code cooldownRate} for its time, with a
 * small clock face over it, its hands dragging.
 */
public class DecelerateEntity extends ThrowableProjectile {
    private int lifetime = 30;
    private float damage;
    /** Client only: how far it has turned end over end (degrees), now and a tick ago. It turns with its speed, so it slows in a
     * Stasis Field and hangs in a Standstill. */
    public float spin, spinO;
    /** Degrees a tick at its thrown speed. */
    private static final float SPIN_PER_TICK = 38f;

    public DecelerateEntity(EntityType<? extends DecelerateEntity> type, Level level) {
        super(type, level);
    }

    DecelerateEntity(Level level, LivingEntity caster) {
        super(TimeRegistry.DECELERATE.get(), caster, level);
        this.damage = TimeConfig.DECELERATE_DAMAGE.get().floatValue();
        this.lifetime = Math.max(1, TimeConfig.ticks(TimeConfig.DECELERATE_RANGE_SECONDS));
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {}

    @Override
    protected double getDefaultGravity() {
        return 0.0;
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            spinO = spin;
            spin += SPIN_PER_TICK * (float) Math.min(1.5, getDeltaMovement().length() / 2.0);
        } else if (isAlive() && tickCount >= lifetime) {
            discard();
        }
    }

    @Override
    protected void onHitEntity(EntityHitResult hit) {
        super.onHitEntity(hit);
        if (level().isClientSide || !(hit.getEntity() instanceof LivingEntity target)) return;
        ServerPlayer caster = getOwner() instanceof ServerPlayer p ? p : null;
        if (target != caster) strike(this, caster, target, damage);
    }

    /** The hand landing on {@code target}. Package-visible for the tests. */
    static void strike(net.minecraft.world.entity.Entity hand, @Nullable ServerPlayer caster, LivingEntity target, float damage) {
        boolean wasCooling = target instanceof ServerPlayer player && cooling(player);
        // A target frozen in a Standstill has the hit held for the resume; the slow still goes on now.
        if (!target.hurt(TimeDamage.source(hand.level(), hand, caster), damage) && !TimeStops.holding(target)) return;
        TimeManipulation.sound(target, KegareSounds.TIME_TICK, 1f, 0.55f);
        if (TimeTargeting.immune(target)) return; // a boss takes the damage, never the slow
        int ticks = TimeConfig.ticks(TimeConfig.DECELERATE_SECONDS);
        if (ticks <= 0) return;
        target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ticks, TimeConfig.DECELERATE_SLOWNESS.get()), caster);
        target.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, ticks, TimeConfig.DECELERATE_FATIGUE.get()), caster);
        if (target instanceof ServerPlayer player && caster != null
                && !NeoForge.EVENT_BUS.post(new TimeTargetEvent(caster, player, TimeManipulation.DECELERATE)).isCanceled()) {
            CooldownRates.set(player, new CooldownRates.Rate("decelerate", TimeConfig.DECELERATE_COOLDOWN_RATE.get(), key -> true,
                    player.level().getGameTime() + ticks));
        }
        TimeManipulation.fx(target, TimeManipulation.fx(TimeNet.Fx.Kind.SLOWED, target.getId(), List.of(), 0f, ticks));
        if (caster != null && target instanceof ServerPlayer && wasCooling) TimeAdvancements.award(caster, TimeAdvancements.SLOW_MOTION);
    }

    /** Any of their abilities on cooldown right now. */
    private static boolean cooling(ServerPlayer player) {
        long now = player.level().getGameTime();
        return InnateTechniqueService.get(player).cooldowns().values().stream().anyMatch(c -> c.endTick() > now);
    }

    @Override
    protected void onHit(HitResult result) {
        super.onHit(result);
        if (level().isClientSide) return;
        level().playSound(null, getX(), getY(), getZ(), KegareSounds.TIME_CLICK.value(), SoundSource.PLAYERS, 0.6f, 1.4f);
        discard();
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Lifetime", lifetime);
        tag.putFloat("Damage", damage);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        lifetime = tag.getInt("Lifetime");
        damage = tag.getFloat("Damage");
    }

    @Nullable
    ServerPlayer caster() {
        return getOwner() instanceof ServerPlayer p ? p : null;
    }
}

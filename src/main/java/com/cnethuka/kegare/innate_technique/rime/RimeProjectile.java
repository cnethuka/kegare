package com.cnethuka.kegare.innate_technique.rime;

import com.cnethuka.kegare.innatetechnique.Ability;
import com.cnethuka.kegare.innatetechnique.visual.VisualHooks;
import com.cnethuka.kegare.innatetechnique.visual.VisualService;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * A real ice projectile: a vanilla throwable entity with velocity. Damage type {@code kegare:rime}: the caster is the
 * source entity, the projectile the direct entity. Melts after {@code lifetime} ticks. Its trail is a client effect
 * started on its first client tick; impacts are sent from the server.
 */
public abstract class RimeProjectile extends ThrowableItemProjectile {
    protected int lifetime = 40;
    protected float damage;
    private boolean trailStarted;

    protected RimeProjectile(EntityType<? extends RimeProjectile> type, Level level) {
        super(type, level);
    }

    protected RimeProjectile(EntityType<? extends RimeProjectile> type, LivingEntity caster, Level level) {
        super(type, caster, level);
    }

    protected abstract Ability ability();

    protected abstract ResourceLocation trailEffect();

    /** How big its trail is drawn. */
    protected float trailScale() {
        return 1f;
    }

    /** How far from its thrower's own first-person camera its trail starts (0: at once). */
    protected double trailClearOfOwnEyes() {
        return 0;
    }

    /** The projectile damaged a living target. */
    protected abstract void onLivingHit(LivingEntity target, @Nullable ServerPlayer caster);

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            if (!trailStarted) trailStarted = VisualHooks.startTrail(this, trailEffect(), trailScale(), trailClearOfOwnEyes());
        } else if (isAlive() && tickCount >= lifetime) {
            impactVisual(position(), null);
            discard();
        }
    }

    @Override
    protected void onHitEntity(EntityHitResult hit) {
        super.onHitEntity(hit);
        if (level().isClientSide || !(hit.getEntity() instanceof LivingEntity target)) return;
        ServerPlayer caster = getOwner() instanceof ServerPlayer p ? p : null;
        beforeHurt(target, caster);
        if (target.hurt(RimeDamage.source(level(), this, getOwner()), damage)) onLivingHit(target, caster);
    }

    /** Just before the hit's damage (e.g. Splinter clearing a sibling shard's hurt cooldown). */
    protected void beforeHurt(LivingEntity target, @Nullable ServerPlayer caster) {}

    @Override
    protected void onHit(HitResult result) {
        super.onHit(result);
        if (level().isClientSide) return;
        impactVisual(result.getLocation(), result);
        level().playSound(null, getX(), getY(), getZ(), SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 0.6f, 1.6f);
        discard();
    }

    /** What clients see where it ends: on whatever it hit, or ({@code hit} null) where it melted away. */
    protected void impactVisual(Vec3 at, @Nullable HitResult hit) {
        VisualService.impact((ServerLevel) level(), ability(), at);
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
}

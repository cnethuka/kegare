package com.cnethuka.kegare.innate_technique.rime;

import com.cnethuka.kegare.innatetechnique.Ability;
import com.cnethuka.kegare.innatetechnique.visual.Visual;
import com.cnethuka.kegare.innatetechnique.visual.VisualService;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Glacial Lance's projectile: bigger and slower; damage scales with charge; two freeze stacks. The charge is synced so every client
 * draws the lance as big as it was charged.
 */
public class GlacialLanceEntity extends RimeProjectile implements com.cnethuka.kegare.impact.ChargedHit {
    private static final EntityDataAccessor<Float> DATA_CHARGE = SynchedEntityData.defineId(GlacialLanceEntity.class, EntityDataSerializers.FLOAT);

    public GlacialLanceEntity(EntityType<? extends GlacialLanceEntity> type, Level level) {
        super(type, level);
    }

    GlacialLanceEntity(Level level, LivingEntity caster, float charge) {
        super(RimeRegistry.GLACIAL_LANCE.get(), caster, level);
        entityData.set(DATA_CHARGE, Mth.clamp(charge, 0f, 1f));
        this.damage = (float) Mth.lerp(charge(), RimeConfig.LANCE_MIN_DAMAGE.get(), RimeConfig.LANCE_MAX_DAMAGE.get());
        this.lifetime = Math.max(1, RimeConfig.ticks(RimeConfig.LANCE_RANGE_SECONDS));
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_CHARGE, 0f);
    }

    @Override
    public float hitCharge() {
        return charge();
    }

    /** How far it was charged, from 0 to 1 (a full charge). */
    public float charge() {
        return entityData.get(DATA_CHARGE);
    }

    @Override
    protected Item getDefaultItem() {
        return RimeRegistry.GLACIAL_LANCE_ITEM.get();
    }

    @Override
    protected double getDefaultGravity() {
        return 0.012;
    }

    @Override
    protected Ability ability() {
        return Rime.GLACIAL_LANCE;
    }

    @Override
    protected ResourceLocation trailEffect() {
        return RimeIds.FX_GLACIAL_LANCE_TRAIL;
    }

    /** A bigger lance leaves a bigger trail. */
    @Override
    protected float trailScale() {
        return 0.7f + 0.45f * charge();
    }

    /** In your own first person the trail starts where the lance comes into view (see its renderer), not between your eyes. */
    @Override
    protected double trailClearOfOwnEyes() {
        return 3.5;
    }

    /**
     * A lance that hits a block sticks there for a moment before it shatters; one that hits something living, or runs out of range,
     * bursts. Either way the clients draw it from this entity (its charge, its heading), so the effect names it.
     */
    @Override
    protected void impactVisual(Vec3 at, @Nullable HitResult hit) {
        ResourceLocation effect = hit instanceof BlockHitResult ? RimeIds.FX_GLACIAL_LANCE_STICK : RimeIds.FX_GLACIAL_LANCE_IMPACT;
        // An entity hit reports the target's feet, and the lance is still where this tick's move began (up to a whole move short),
        // so it bursts where its path enters the target instead.
        Vec3 where = hit instanceof EntityHitResult e ? struck(e.getEntity()) : at;
        VisualService.playAt((ServerLevel) level(), where, Visual.effect(effect, Visual.Anchor.BODY), getId());
    }

    /** Where this tick's move takes the lance's middle into {@code target}'s box, or the box's middle when it was already inside. */
    private Vec3 struck(Entity target) {
        Vec3 from = getBoundingBox().getCenter();
        AABB box = target.getBoundingBox();
        return box.contains(from) ? box.getCenter() : box.clip(from, from.add(getDeltaMovement())).orElse(box.getCenter());
    }

    @Override
    protected void onLivingHit(LivingEntity target, @Nullable ServerPlayer caster) {
        int slow = RimeConfig.ticks(RimeConfig.LANCE_SLOWNESS_SECONDS);
        if (slow > 0) target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, slow, RimeConfig.LANCE_SLOWNESS_AMPLIFIER.get()), caster);
        FreezeService.addStacks(target, RimeConfig.LANCE_FREEZE_STACKS.get(), caster);
        if (caster != null && target instanceof ServerPlayer && target != caster && charge() >= 0.999f) {
            RimeAdvancements.award(caster, RimeAdvancements.FULL_CHARGE);
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putFloat("Charge", charge());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        entityData.set(DATA_CHARGE, Mth.clamp(tag.getFloat("Charge"), 0f, 1f));
    }
}

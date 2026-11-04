package com.cnethuka.kegare.innate_technique.rime;

import com.cnethuka.kegare.innatetechnique.Ability;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

/** Glacial Lance's projectile: bigger and slower; damage scales with charge; two freeze stacks. */
public class GlacialLanceEntity extends RimeProjectile {
    private float charge;

    public GlacialLanceEntity(EntityType<? extends GlacialLanceEntity> type, Level level) {
        super(type, level);
    }

    GlacialLanceEntity(Level level, LivingEntity caster, float charge) {
        super(RimeRegistry.GLACIAL_LANCE.get(), caster, level);
        this.charge = Mth.clamp(charge, 0f, 1f);
        this.damage = (float) Mth.lerp(this.charge, RimeConfig.LANCE_MIN_DAMAGE.get(), RimeConfig.LANCE_MAX_DAMAGE.get());
        this.lifetime = Math.max(1, RimeConfig.ticks(RimeConfig.LANCE_RANGE_SECONDS));
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

    @Override
    protected void onLivingHit(LivingEntity target, @Nullable ServerPlayer caster) {
        int slow = RimeConfig.ticks(RimeConfig.LANCE_SLOWNESS_SECONDS);
        if (slow > 0) target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, slow, RimeConfig.LANCE_SLOWNESS_AMPLIFIER.get()), caster);
        FreezeService.addStacks(target, RimeConfig.LANCE_FREEZE_STACKS.get(), caster);
        if (caster != null && target instanceof ServerPlayer && target != caster && charge >= 0.999f) {
            RimeAdvancements.award(caster, RimeAdvancements.FULL_CHARGE);
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putFloat("Charge", charge);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        charge = tag.getFloat("Charge");
    }
}

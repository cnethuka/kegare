package com.cnethuka.kegare.innate_technique.rime;

import com.cnethuka.kegare.innatetechnique.Ability;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

/** Frost Shard's projectile: fast, light damage, short Slowness and one freeze stack. */
public class FrostShardEntity extends RimeProjectile {
    public FrostShardEntity(EntityType<? extends FrostShardEntity> type, Level level) {
        super(type, level);
    }

    FrostShardEntity(Level level, LivingEntity caster) {
        super(RimeRegistry.FROST_SHARD.get(), caster, level);
        this.damage = RimeConfig.SHARD_DAMAGE.get().floatValue();
        this.lifetime = Math.max(1, RimeConfig.ticks(RimeConfig.SHARD_RANGE_SECONDS));
    }

    @Override
    protected Item getDefaultItem() {
        return RimeRegistry.FROST_SHARD_ITEM.get();
    }

    @Override
    protected double getDefaultGravity() {
        return 0.005;
    }

    @Override
    protected Ability ability() {
        return Rime.FROST_SHARD;
    }

    @Override
    protected ResourceLocation trailEffect() {
        return RimeIds.FX_FROST_SHARD_TRAIL;
    }

    @Override
    protected void onLivingHit(LivingEntity target, @Nullable ServerPlayer caster) {
        int slow = RimeConfig.ticks(RimeConfig.SHARD_SLOWNESS_SECONDS);
        if (slow > 0) target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, slow, RimeConfig.SHARD_SLOWNESS_AMPLIFIER.get()), caster);
        FreezeService.addStacks(target, RimeConfig.SHARD_FREEZE_STACKS.get(), caster);
        if (caster != null && target instanceof ServerPlayer && target != caster) {
            RimeAdvancements.award(caster, RimeAdvancements.COLD_OPEN);
            RimeLaunch.onShardHit(caster, target);
        }
    }
}

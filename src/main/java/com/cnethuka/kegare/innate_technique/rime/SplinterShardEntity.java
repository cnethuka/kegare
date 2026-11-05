package com.cnethuka.kegare.innate_technique.rime;

import com.cnethuka.kegare.innatetechnique.Ability;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

/**
 * One of Splinter's three shards. Shards of the same cast skip the target's hurt cooldown left by a sibling, so all
 * three land (other attacks still respect it). Each hit adds a freeze stack.
 */
public class SplinterShardEntity extends RimeProjectile {
    /** Which Splinter cast of its caster this shard belongs to. */
    private int cast;

    public SplinterShardEntity(EntityType<? extends SplinterShardEntity> type, Level level) {
        super(type, level);
    }

    SplinterShardEntity(Level level, LivingEntity caster, int cast) {
        super(RimeRegistry.SPLINTER_SHARD.get(), caster, level);
        this.cast = cast;
        this.damage = RimeConfig.SPLINTER_DAMAGE.get().floatValue();
        this.lifetime = Math.max(1, RimeConfig.ticks(RimeConfig.SPLINTER_RANGE_SECONDS));
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
        return Rime.SPLINTER;
    }

    @Override
    protected ResourceLocation trailEffect() {
        return RimeIds.FX_SPLINTER_TRAIL;
    }

    @Override
    protected void beforeHurt(LivingEntity target, @Nullable ServerPlayer caster) {
        // A sibling shard just hit this target: its hurt cooldown would swallow this one.
        if (caster != null && caster.getData(RimeRegistry.TRACKER).splinterHits(cast, target.getUUID()) > 0) target.invulnerableTime = 0;
    }

    @Override
    protected void onLivingHit(LivingEntity target, @Nullable ServerPlayer caster) {
        FreezeService.addStacks(target, RimeConfig.SPLINTER_FREEZE_STACKS.get(), caster);
        if (caster == null || target == caster) return;
        int hits = caster.getData(RimeRegistry.TRACKER).recordSplinterHit(cast, target.getUUID());
        if (target instanceof ServerPlayer) {
            if (hits >= SplinterAbility.SHARDS) RimeAdvancements.award(caster, RimeAdvancements.SPLINTER);
            RimeLaunch.onShardHit(caster, target);
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Cast", cast);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        cast = tag.getInt("Cast");
    }
}

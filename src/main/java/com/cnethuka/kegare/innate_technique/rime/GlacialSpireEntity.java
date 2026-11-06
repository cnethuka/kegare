package com.cnethuka.kegare.innate_technique.rime;

import com.cnethuka.kegare.combat.CombatHelper;
import com.cnethuka.kegare.innatetechnique.visual.Visual;
import com.cnethuka.kegare.innatetechnique.visual.VisualService;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Glacial Spire on the ground: a marker entity (no blocks, never saved). It shows a frost warning circle, erupts after
 * the warning time (damage, freeze stacks, launch), stands as a spike for a moment, then shatters and is gone.
 * Its visuals are loops on this entity, so players who arrive late still see the right stage.
 */
public class GlacialSpireEntity extends Entity {
    @Nullable
    private UUID caster;
    private float charge;
    private int warningTicks;
    private int standTicks;
    private boolean started;
    private boolean erupted;

    public GlacialSpireEntity(EntityType<? extends GlacialSpireEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    GlacialSpireEntity(Level level, ServerPlayer caster, Vec3 at, float charge) {
        this(RimeRegistry.GLACIAL_SPIRE.get(), level);
        this.caster = caster.getUUID();
        this.charge = Mth.clamp(charge, 0f, 1f);
        this.warningTicks = RimeConfig.ticks(RimeConfig.SPIRE_DELAY);
        this.standTicks = Math.max(1, RimeConfig.ticks(RimeConfig.SPIRE_STANDS_SECONDS));
        setPos(at.x, at.y, at.z);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {}

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) return;
        if (caster == null) { // only ever made by the ability; anything else is stray
            discard();
            return;
        }
        if (!started) {
            started = true;
            VisualService.startLoop(this, RimeIds.SPIRE_WARNING_KEY, RimeIds.SPIRE_WARNING, 0);
            level().playSound(null, getX(), getY(), getZ(), SoundEvents.POWDER_SNOW_BREAK, SoundSource.PLAYERS, 1f, 0.6f);
        }
        if (!erupted && tickCount >= warningTicks) erupt();
        if (erupted && tickCount >= warningTicks + standTicks) {
            VisualService.stopLoop(this, RimeIds.SPIRE_SPIKE_KEY);
            VisualService.playAt((ServerLevel) level(), position().add(0, 1.0, 0), Visual.effect(RimeIds.FX_FREEZE_SHATTER, Visual.Anchor.BODY));
            level().playSound(null, getX(), getY(), getZ(), SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 0.8f, 1.2f);
            discard();
        }
    }

    private void erupt() {
        erupted = true;
        ServerLevel level = (ServerLevel) level();
        VisualService.stopLoop(this, RimeIds.SPIRE_WARNING_KEY);
        VisualService.startLoop(this, RimeIds.SPIRE_SPIKE_KEY, RimeIds.SPIRE_SPIKE, 0);
        VisualService.impact(level, Rime.GLACIAL_SPIRE, position());
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.PLAYERS, 1.4f, 0.6f);
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 1f, 0.5f);

        ServerPlayer owner = caster == null ? null : level.getServer().getPlayerList().getPlayer(caster);
        double radius = RimeConfig.SPIRE_RADIUS.get();
        double launch = Mth.lerp(charge, RimeConfig.SPIRE_MIN_LAUNCH.get(), RimeConfig.SPIRE_MAX_LAUNCH.get());
        float damage = RimeConfig.SPIRE_DAMAGE.get().floatValue();
        int stacks = RimeConfig.SPIRE_FREEZE_STACKS.get();
        AABB area = new AABB(getX() - radius, getY() - 0.5, getZ() - radius, getX() + radius, getY() + RimeConfig.SPIRE_HEIGHT.get(), getZ() + radius);
        com.cnethuka.kegare.melee.CleanDodge.areaWentOff(level, owner, area::intersects);
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, area, e -> e.isAlive() && !e.isSpectator()
                && (owner == null || e != owner) && horizontalDistance(e) <= radius + e.getBbWidth() / 2)) {
            if (target.hurt(RimeDamage.source(level, this, owner), damage)) FreezeService.addStacks(target, stacks, owner);
            if (CombatHelper.isBoss(target)) continue; // bosses take the damage but are never launched (DESIGN §4)
            Vec3 v = target.getDeltaMovement();
            target.setDeltaMovement(v.x * 0.2, launch, v.z * 0.2);
            target.hurtMarked = true;
            target.hasImpulse = true;
            if (owner != null && target instanceof ServerPlayer victim) RimeLaunch.mark(victim, owner);
        }
    }

    private double horizontalDistance(Entity e) {
        double dx = e.getX() - getX();
        double dz = e.getZ() - getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {}

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {}
}

package com.cnethuka.kegare.innate_technique.swap;

import com.cnethuka.kegare.innatetechnique.visual.VisualHooks;
import com.cnethuka.kegare.innatetechnique.visual.VisualService;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Anchor's marker: thrown like a pearl, passes through creatures, and sticks to the first block it hits. It waits there
 * (glowing) until its caster uses it, or until its lifetime runs out. Never saved.
 */
public class AnchorEntity extends ThrowableItemProjectile {
    private static final EntityDataAccessor<Boolean> STUCK = SynchedEntityData.defineId(AnchorEntity.class, EntityDataSerializers.BOOLEAN);

    private int lifetime = 400;
    /** The face of the block it stuck to (UP = it landed on the ground). */
    private Direction face = Direction.UP;
    private boolean trailStarted;

    public AnchorEntity(EntityType<? extends AnchorEntity> type, Level level) {
        super(type, level);
    }

    public AnchorEntity(LivingEntity caster, Level level, int lifetime) {
        super(SwapRegistry.ANCHOR.get(), caster, level);
        this.lifetime = lifetime;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(STUCK, false);
    }

    @Override
    protected Item getDefaultItem() {
        return SwapRegistry.ANCHOR_ITEM.get();
    }

    public boolean isStuck() {
        return entityData.get(STUCK);
    }

    @Override
    public void tick() {
        if (isStuck()) {
            // Stuck: no flight, no gravity; just wait (and time out).
            setDeltaMovement(Vec3.ZERO);
            baseTick();
        } else {
            super.tick();
        }
        if (level().isClientSide) {
            if (!trailStarted && !isStuck()) {
                trailStarted = true;
                VisualHooks.startTrail(this, SwapIds.FX_ANCHOR_TRAIL);
            }
        } else if (isAlive() && tickCount >= lifetime) {
            Anchors.expire(this);
        }
    }

    /** Creatures don't stop it. */
    @Override
    protected boolean canHitEntity(Entity target) {
        return false;
    }

    @Override
    protected void onHit(HitResult result) {
        if (level().isClientSide || isStuck() || !(result instanceof BlockHitResult block)) return;
        face = block.getDirection();
        Vec3 at = block.getLocation().add(Vec3.atLowerCornerOf(face.getNormal()).scale(0.08));
        setPos(at.x, at.y, at.z);
        setDeltaMovement(Vec3.ZERO);
        setNoGravity(true);
        entityData.set(STUCK, true);
        VisualService.startLoop(this, SwapIds.ANCHOR_MARKER_KEY, SwapIds.ANCHOR_MARKER, 0);
        level().playSound(null, getX(), getY(), getZ(), SoundEvents.AMETHYST_BLOCK_PLACE, SoundSource.PLAYERS, 0.9f, 1.4f);
    }

    /**
     * Where a player taken to this anchor stands: on the ground it landed on, out from the wall it stuck to, or hanging
     * under the ceiling; nudged up out of blocks if it must be.
     */
    public Vec3 arrivalFor(ServerPlayer player) {
        Vec3 at = position();
        double halfWidth = player.getBbWidth() / 2.0 + 0.05;
        Vec3 spot = switch (face) {
            case UP -> at;
            case DOWN -> at.subtract(0, player.getBbHeight() + 0.05, 0);
            default -> at.add(face.getStepX() * halfWidth, -player.getBbHeight() / 2.0, face.getStepZ() * halfWidth);
        };
        AABB box = player.getDimensions(player.getPose()).makeBoundingBox(spot);
        for (int i = 0; i < 8 && !level().noCollision(player, box); i++) {
            spot = spot.add(0, 0.25, 0);
            box = box.move(0, 0.25, 0);
        }
        return spot;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Lifetime", lifetime);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        lifetime = tag.getInt("Lifetime");
    }
}

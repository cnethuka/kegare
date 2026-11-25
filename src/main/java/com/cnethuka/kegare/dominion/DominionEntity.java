package com.cnethuka.kegare.dominion;

import com.cnethuka.kegare.api.dominion.DominionContext;
import com.cnethuka.kegare.api.dominion.DominionLook;
import com.cnethuka.kegare.innatetechnique.CastIntent;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * A Dominion's barrier (DESIGN §3.35): a marker at its centre, never saved and with no hitbox, whose synced data carries the barrier to
 * every client in range: its size, state, how far it has risen, how much strength is left and what its inside looks like. The server
 * runs it ({@link Dominions}); the clients only draw it and keep the local player on their side of it.
 */
public class DominionEntity extends Entity {
    public enum State {
        /** Rising round the caster: anyone can still leave. */
        OPENING,
        /** Closed: whoever was inside is trapped, nobody crosses, projectiles stop at it. */
        OPEN,
        /** Contested by another Dominion (DESIGN §3.35, clashes): half-formed, trapping nobody, its sure-hit cancelled. */
        CLASH
    }

    private static final EntityDataAccessor<Byte> DATA_STATE = SynchedEntityData.defineId(DominionEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Boolean> DATA_COMPLETE = SynchedEntityData.defineId(DominionEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Float> DATA_RADIUS = SynchedEntityData.defineId(DominionEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DATA_OPEN_TICKS = SynchedEntityData.defineId(DominionEntity.class, EntityDataSerializers.INT);
    /** The tick (of this entity's clock) it closed, or -1 while it hasn't. */
    private static final EntityDataAccessor<Integer> DATA_CLOSED_AT = SynchedEntityData.defineId(DominionEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_DURATION = SynchedEntityData.defineId(DominionEntity.class, EntityDataSerializers.INT);
    /** Strength left, as a share of the whole, 0 to 1. */
    private static final EntityDataAccessor<Float> DATA_BARRIER = SynchedEntityData.defineId(DominionEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DATA_CASTER = SynchedEntityData.defineId(DominionEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<String> DATA_ABILITY = SynchedEntityData.defineId(DominionEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Integer> DATA_SKY = SynchedEntityData.defineId(DominionEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_FOG = SynchedEntityData.defineId(DominionEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DATA_FOG_END = SynchedEntityData.defineId(DominionEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DATA_SHELL = SynchedEntityData.defineId(DominionEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<String> DATA_GROUND = SynchedEntityData.defineId(DominionEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Boolean> DATA_RUINS = SynchedEntityData.defineId(DominionEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> DATA_DUST = SynchedEntityData.defineId(DominionEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_CRACKED = SynchedEntityData.defineId(DominionEntity.class, EntityDataSerializers.BOOLEAN);
    /** The entity id of the Dominion it's clashing with, or -1. */
    private static final EntityDataAccessor<Integer> DATA_CLASH_WITH = SynchedEntityData.defineId(DominionEntity.class, EntityDataSerializers.INT);

    /** Every barrier in a level, per side, for the checks that run on every move (kept as they're added to and removed from a level). */
    private static final List<DominionEntity> SERVER = new CopyOnWriteArrayList<>();
    private static final List<DominionEntity> CLIENT = new CopyOnWriteArrayList<>();

    // ---- server only
    @Nullable UUID caster;
    @Nullable DominionSpec spec;
    /** Whoever was inside when it closed (bosses never). */
    final Set<UUID> trapped = new HashSet<>();
    double barrierLeft;
    /** The key that opened it, and whether it's still held (a clash needs it held). */
    CastIntent releaseIntent = CastIntent.CAST_RELEASE;
    boolean keyHeld = true;
    boolean ended;
    /** Where the caster stood when a clash began: they can't move far from it while they hold it. */
    @Nullable Vec3 holdAt;

    // ---- client only: whether the local player is on the inside of it
    boolean localTrapped;

    /** Client: whether the local player is trapped inside it (the server says, as it closes). */
    public boolean localTrapped() {
        return localTrapped;
    }

    public void setLocalTrapped(boolean trapped) {
        this.localTrapped = trapped;
    }

    public DominionEntity(EntityType<? extends DominionEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    DominionEntity(ServerLevel level, ServerPlayer caster, DominionSpec spec) {
        this(DominionRegistry.DOMINION.get(), level);
        this.caster = caster.getUUID();
        this.spec = spec;
        this.barrierLeft = spec.barrierStrength();
        setPos(caster.getX(), caster.getY(), caster.getZ());
        DominionLook look = spec.look();
        entityData.set(DATA_COMPLETE, spec.complete());
        entityData.set(DATA_RADIUS, (float) spec.radius());
        entityData.set(DATA_OPEN_TICKS, DominionConfig.ticks(DominionConfig.OPEN_SECONDS));
        entityData.set(DATA_DURATION, spec.durationTicks());
        entityData.set(DATA_CASTER, caster.getId());
        entityData.set(DATA_ABILITY, spec.ability().toString());
        entityData.set(DATA_SKY, look.skyColor());
        entityData.set(DATA_FOG, look.fogColor());
        entityData.set(DATA_FOG_END, look.fogEnd());
        entityData.set(DATA_SHELL, look.shellColor());
        entityData.set(DATA_GROUND, look.groundPattern().map(ResourceLocation::toString).orElse(""));
        entityData.set(DATA_RUINS, look.ruins());
        entityData.set(DATA_DUST, look.dustColor());
        entityData.set(DATA_CRACKED, look.cracked());
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_STATE, (byte) State.OPENING.ordinal());
        builder.define(DATA_COMPLETE, false);
        builder.define(DATA_RADIUS, 8f);
        builder.define(DATA_OPEN_TICKS, 20);
        builder.define(DATA_CLOSED_AT, -1);
        builder.define(DATA_DURATION, 400);
        builder.define(DATA_BARRIER, 1f);
        builder.define(DATA_CASTER, -1);
        builder.define(DATA_ABILITY, "");
        builder.define(DATA_SKY, 0x6E6A66);
        builder.define(DATA_FOG, 0x6E6A66);
        builder.define(DATA_FOG_END, 24f);
        builder.define(DATA_SHELL, 0x2A2730);
        builder.define(DATA_GROUND, "");
        builder.define(DATA_RUINS, false);
        builder.define(DATA_DUST, -1);
        builder.define(DATA_CRACKED, false);
        builder.define(DATA_CLASH_WITH, -1);
    }

    // ---------------------------------------------------------------------------------------------- both sides

    public State state() {
        return State.values()[Math.floorMod(entityData.get(DATA_STATE), State.values().length)];
    }

    void setState(State state) {
        entityData.set(DATA_STATE, (byte) state.ordinal());
    }

    /** Closed and trapping: nobody crosses it. */
    public boolean isClosed() {
        return state() == State.OPEN;
    }

    public boolean complete() {
        return entityData.get(DATA_COMPLETE);
    }

    public double radius() {
        return entityData.get(DATA_RADIUS);
    }

    public Vec3 centre() {
        return position();
    }

    public int openTicks() {
        return entityData.get(DATA_OPEN_TICKS);
    }

    /** This entity's clock tick it closed at, or -1. */
    public int closedAt() {
        return entityData.get(DATA_CLOSED_AT);
    }

    /** It closes now, as if it had been closed {@code usedTicks} already. */
    void markClosed(int usedTicks) {
        entityData.set(DATA_CLOSED_AT, tickCount - Math.max(0, usedTicks));
    }

    public int duration() {
        return entityData.get(DATA_DURATION);
    }

    /** Barrier strength left as a share of the whole, 0 to 1. */
    public float barrier() {
        return entityData.get(DATA_BARRIER);
    }

    void setBarrierShare(float share) {
        entityData.set(DATA_BARRIER, Math.max(0f, Math.min(1f, share)));
    }

    public int casterEntityId() {
        return entityData.get(DATA_CASTER);
    }

    public ResourceLocation abilityId() {
        ResourceLocation id = ResourceLocation.tryParse(entityData.get(DATA_ABILITY));
        return id == null ? com.cnethuka.kegare.Kegare.id("dominion") : id;
    }

    public DominionLook look() {
        String ground = entityData.get(DATA_GROUND);
        return new DominionLook(entityData.get(DATA_SKY), entityData.get(DATA_FOG), entityData.get(DATA_FOG_END), entityData.get(DATA_SHELL),
                ground.isEmpty() ? Optional.empty() : Optional.ofNullable(ResourceLocation.tryParse(ground)), entityData.get(DATA_RUINS),
                entityData.get(DATA_DUST), entityData.get(DATA_CRACKED));
    }

    public int clashWith() {
        return entityData.get(DATA_CLASH_WITH);
    }

    void setClashWith(int id) {
        entityData.set(DATA_CLASH_WITH, id);
    }

    /** How far the barrier has risen, 0 to 1. */
    public float risen(float partialTick) {
        int closed = closedAt();
        if (closed >= 0 && state() == State.OPEN) return 1f;
        return Math.min(1f, (tickCount + partialTick) / Math.max(1, openTicks()));
    }

    /** From the centre to this point. */
    public double distanceTo(Vec3 point) {
        return point.distanceTo(centre());
    }

    /** Whether this point is inside the barrier's sphere. */
    public boolean contains(Vec3 point) {
        return distanceTo(point) < radius();
    }

    /** The middle of an entity's box: what the barrier keeps on one side of it. */
    public static Vec3 middle(Entity entity) {
        return entity.getBoundingBox().getCenter();
    }

    /** How far from the shell an entity's middle is kept. */
    public static double margin(Entity entity) {
        return entity.getBbWidth() / 2.0 + 0.2;
    }

    /** Whether a segment from {@code a} to {@code b} crosses the shell (one end in, the other out). */
    public boolean crosses(Vec3 a, Vec3 b) {
        return contains(a) != contains(b);
    }

    // ---------------------------------------------------------------------------------------------- the per-side registry

    @Override
    public void onAddedToLevel() {
        super.onAddedToLevel();
        (level().isClientSide ? CLIENT : SERVER).add(this);
    }

    @Override
    public void onRemovedFromLevel() {
        super.onRemovedFromLevel();
        (level().isClientSide ? CLIENT : SERVER).remove(this);
    }

    /** Every barrier in this level (on its side). */
    public static List<DominionEntity> in(Level level) {
        List<DominionEntity> all = level.isClientSide ? CLIENT : SERVER;
        if (all.isEmpty()) return List.of();
        List<DominionEntity> out = new ArrayList<>(2);
        for (DominionEntity d : all) {
            if (d.level() == level && !d.isRemoved()) out.add(d);
        }
        return out;
    }

    /** Every barrier on the server, in every level. */
    static List<DominionEntity> allServer() {
        return SERVER;
    }

    // ---------------------------------------------------------------------------------------------- ticking, drawing, saving

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide) Dominions.tick(this);
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        double r = radius() + 1;
        Vec3 c = centre();
        return new AABB(c.x - r, c.y - r, c.z - r, c.x + r, c.y + r, c.z + r);
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        double far = radius() + 192;
        return distance < far * far;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean canBeCollidedWith() {
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

    /** What its {@link com.cnethuka.kegare.api.dominion.DominionUltimate} sees of it (server side). */
    public DominionContext context() {
        DominionEntity self = this;
        return new DominionContext() {
            @Override public ServerPlayer caster() { return Dominions.caster(self); }
            @Override public ServerLevel level() { return (ServerLevel) self.level(); }
            @Override public Vec3 centre() { return self.centre(); }
            @Override public double radius() { return self.radius(); }
            @Override public int ticksOpen() { return self.closedAt() < 0 ? 0 : self.tickCount - self.closedAt(); }
            @Override public List<LivingEntity> inside() { return Dominions.inside(self); }
            @Override public List<LivingEntity> sureHitTargets() { return Dominions.sureHitTargets(self); }
        };
    }
}

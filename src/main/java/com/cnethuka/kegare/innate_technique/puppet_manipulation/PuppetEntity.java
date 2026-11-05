package com.cnethuka.kegare.innate_technique.puppet_manipulation;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import java.util.EnumSet;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * A puppet: a half-size copy of its owner (their skin, drawn by {@code PuppetRenderer}), charged with Malice at a
 * {@link PuppetTier} that sets its health and glow. It lasts until killed: it survives its owner's death and logout
 * (while the owner is offline it stands idle and never attacks) and is saved with its chunk. It dissolves if its owner
 * rerolls away from Puppet Manipulation, even in an unloaded chunk ({@link PuppetSavedData}'s generations).
 * <p>
 * Standing still, it spins ("kuru kuru"), decided here and synced ({@link #isSpinning}) so every client agrees.
 */
public class PuppetEntity extends PathfinderMob implements Puppet {
    private static final EntityDataAccessor<Optional<UUID>> OWNER = SynchedEntityData.defineId(PuppetEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Byte> TIER = SynchedEntityData.defineId(PuppetEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Integer> COLOR = SynchedEntityData.defineId(PuppetEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> SPINNING = SynchedEntityData.defineId(PuppetEntity.class, EntityDataSerializers.BOOLEAN);
    /** Someone is inside it (Possess): hidden, untouchable, carried along with them. Never saved. */
    private static final EntityDataAccessor<Boolean> POSSESSED = SynchedEntityData.defineId(PuppetEntity.class, EntityDataSerializers.BOOLEAN);
    /** The owner's name and skin property, so the owner's look is drawn even while they are offline. */
    private static final EntityDataAccessor<String> OWNER_NAME = SynchedEntityData.defineId(PuppetEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> SKIN_VALUE = SynchedEntityData.defineId(PuppetEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> SKIN_SIGNATURE = SynchedEntityData.defineId(PuppetEntity.class, EntityDataSerializers.STRING);

    private int generation;
    private PuppetOrder order = PuppetOrder.FOLLOW;
    /** Go: where it is walking to (then it stays there). */
    @Nullable
    private BlockPos goTarget;
    private int stillTicks;
    /** Checked once, on the first server tick after it is loaded (or made). */
    private boolean validated;
    /** Who is inside it, while possessed (server side, runtime only). */
    @Nullable
    private UUID possessor;
    /** Client: when the current spin started (tickCount), for the spin keyframes. */
    private int spinStartTick;

    /** Set by the client: whether a full puppet shows its outline to this client (line of sight; never through walls). */
    private static Predicate<PuppetEntity> outlineHook = p -> false;

    public static void setOutlineHook(Predicate<PuppetEntity> hook) {
        outlineHook = hook;
    }

    public PuppetEntity(EntityType<? extends PuppetEntity> type, Level level) {
        super(type, level);
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 8.0)
                .add(Attributes.MOVEMENT_SPEED, 0.32)
                .add(Attributes.ATTACK_DAMAGE, 1.0)
                .add(Attributes.FOLLOW_RANGE, 32.0)
                .add(Attributes.SCALE, 0.5);
    }

    /** A new puppet of {@code owner}'s, at {@code tier} (health, glow), not yet added to the level. */
    static PuppetEntity create(ServerPlayer owner, PuppetTier tier, int maliceColor, int generation) {
        PuppetEntity puppet = new PuppetEntity(PuppetRegistry.PUPPET.get(), owner.level());
        puppet.entityData.set(OWNER, Optional.of(owner.getUUID()));
        puppet.entityData.set(TIER, (byte) tier.ordinal());
        puppet.entityData.set(COLOR, maliceColor & 0xFFFFFF);
        puppet.setOwnerProfile(owner.getGameProfile());
        puppet.generation = generation;
        puppet.getAttribute(Attributes.MAX_HEALTH).setBaseValue(tier.health());
        puppet.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(PuppetConfig.MOVEMENT_SPEED.get());
        puppet.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(PuppetConfig.MELEE_DAMAGE.get());
        puppet.getAttribute(Attributes.SCALE).setBaseValue(PuppetConfig.SCALE.get());
        puppet.refreshDimensions(); // the scale attribute sets the hitbox, too
        puppet.setHealth(tier.health());
        puppet.validated = true;
        return puppet;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(OWNER, Optional.empty());
        builder.define(TIER, (byte) 0);
        builder.define(COLOR, PuppetManipulation.MALICE_COLOR);
        builder.define(SPINNING, false);
        builder.define(POSSESSED, false);
        builder.define(OWNER_NAME, "");
        builder.define(SKIN_VALUE, "");
        builder.define(SKIN_SIGNATURE, "");
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(3, new FollowOwnerGoal());
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8f) {
            @Override
            public boolean canUse() {
                return !isSpinning() && super.canUse();
            }
        });
    }

    // ---------------------------------------------------------------- Puppet

    @Override
    public boolean hasSoul() {
        return false;
    }

    @Override
    @Nullable
    public UUID ownerId() {
        return entityData.get(OWNER).orElse(null);
    }

    @Override
    public int maliceColor() {
        return entityData.get(COLOR);
    }

    @Override
    public PuppetTier tier() {
        return PuppetTier.byId(entityData.get(TIER));
    }

    public boolean isOwnedBy(Entity entity) {
        return entity.getUUID().equals(ownerId());
    }

    public boolean isSpinning() {
        return entityData.get(SPINNING);
    }

    /** Someone is inside it right now (it waits hidden inside them; see {@link Possession}). */
    public boolean isPossessed() {
        return entityData.get(POSSESSED);
    }

    /** Possession starts ({@code possessor} set) or ends (null). */
    void setPossessed(@Nullable ServerPlayer player) {
        possessor = player == null ? null : player.getUUID();
        entityData.set(POSSESSED, player != null);
        entityData.set(SPINNING, false);
        stillTicks = 0;
        getNavigation().stop();
        setTarget(null);
        setDeltaMovement(Vec3.ZERO);
        noPhysics = player != null;
        setNoGravity(player != null);
    }

    /** Client: seconds into the current spin. */
    public float spinSeconds(float partialTick) {
        return Math.max(0, tickCount - spinStartTick + partialTick) / 20f;
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (SPINNING.equals(key) && level().isClientSide && isSpinning()) spinStartTick = tickCount;
    }

    /** A full puppet's outline (client side, see the hook). */
    @Override
    public boolean isCurrentlyGlowing() {
        return super.isCurrentlyGlowing() || level().isClientSide && outlineHook.test(this);
    }

    /** The outline is drawn in the puppet's glow color. */
    @Override
    public int getTeamColor() {
        return getTeam() != null ? super.getTeamColor() : maliceColor();
    }

    public PuppetOrder order() {
        return order;
    }

    @Nullable
    public BlockPos goTarget() {
        return goTarget;
    }

    int generation() {
        return generation;
    }

    /** The owner, if they are online, alive and in this puppet's level (else the puppet stands idle). */
    @Nullable
    public ServerPlayer owner() {
        UUID id = ownerId();
        if (id == null || level().isClientSide) return null;
        return level().getPlayerByUUID(id) instanceof ServerPlayer player && player.isAlive() && !player.isSpectator() ? player : null;
    }

    // ---------------------------------------------------------------- owner's look (client side)

    public String ownerName() {
        return entityData.get(OWNER_NAME);
    }

    /** A profile carrying the owner's skin property (if the server had one), for the client to load the skin from. */
    public GameProfile ownerProfile() {
        UUID id = ownerId();
        GameProfile profile = new GameProfile(id == null ? getUUID() : id, ownerName());
        String value = entityData.get(SKIN_VALUE);
        if (!value.isEmpty()) {
            String signature = entityData.get(SKIN_SIGNATURE);
            profile.getProperties().put("textures", new Property("textures", value, signature.isEmpty() ? null : signature));
        }
        return profile;
    }

    private void setOwnerProfile(GameProfile profile) {
        entityData.set(OWNER_NAME, profile.getName());
        Property textures = profile.getProperties().get("textures").stream().findFirst().orElse(null);
        entityData.set(SKIN_VALUE, textures == null ? "" : textures.value());
        entityData.set(SKIN_SIGNATURE, textures == null || textures.signature() == null ? "" : textures.signature());
    }

    // ---------------------------------------------------------------- ticking

    @Override
    public void tick() {
        if (!level().isClientSide && isPossessed()) {
            // Inside its possessor: no AI, no physics, just carried along (until they let go).
            ServerPlayer inside = Possession.possessorOf(this, possessor);
            if (inside == null) {
                setPossessed(null);
            } else {
                setPos(inside.getX(), inside.getY(), inside.getZ());
                setYRot(inside.getYRot());
                return;
            }
        }
        super.tick();
        if (level().isClientSide) return;
        if (!validated) {
            validated = true;
            if (!Puppets.isCurrent(this)) { // its owner rerolled away while it was unloaded
                Puppets.dissolve(this);
                return;
            }
        }
        ServerPlayer owner = owner();
        if (owner == null) { // offline, dead or elsewhere: stand idle, never attack
            getNavigation().stop();
            setTarget(null);
        }
        updateSpin();
    }

    /** Spins after standing still for {@code spinDelay}: no path, no target, barely moving. */
    private void updateSpin() {
        Vec3 v = getDeltaMovement();
        boolean still = getNavigation().isDone() && getTarget() == null && v.x * v.x + v.z * v.z < 1.0e-4;
        stillTicks = still ? stillTicks + 1 : 0;
        boolean spin = stillTicks > PuppetConfig.ticks(PuppetConfig.SPIN_DELAY);
        if (spin != isSpinning()) entityData.set(SPINNING, spin);
    }

    // ---------------------------------------------------------------- death and removal

    /** No corpse: a puppet dissolves the moment it dies. */
    @Override
    protected void tickDeath() {
        if (!level().isClientSide && !isRemoved()) {
            Puppets.dissolveEffect(this);
            remove(RemovalReason.KILLED);
        }
    }

    @Override
    public void remove(RemovalReason reason) {
        if (!level().isClientSide && reason.shouldDestroy()) Puppets.forget(this);
        super.remove(reason);
    }

    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        // While possessed, the possessor's body takes the hits; the hidden puppet takes none.
        if (isPossessed() && !source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) return true;
        return super.isInvulnerableTo(source);
    }

    @Override
    public boolean isPickable() {
        return !isPossessed() && super.isPickable();
    }

    @Override
    public boolean isPushable() {
        return !isPossessed() && super.isPushable();
    }

    @Override
    protected boolean shouldDropLoot() {
        return false;
    }

    @Override
    public boolean shouldDropExperience() {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public boolean requiresCustomPersistence() {
        return true;
    }

    @Override
    public boolean canUsePortal(boolean allowPassengers) {
        return false;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.ARMOR_STAND_HIT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.ARMOR_STAND_BREAK;
    }

    @Override
    public float getVoicePitch() {
        return 1.4f;
    }

    // ---------------------------------------------------------------- saving

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        UUID owner = ownerId();
        if (owner != null) tag.putUUID("Owner", owner);
        tag.putString("OwnerName", ownerName());
        tag.putString("SkinValue", entityData.get(SKIN_VALUE));
        tag.putString("SkinSignature", entityData.get(SKIN_SIGNATURE));
        tag.putByte("Tier", entityData.get(TIER));
        tag.putInt("Color", maliceColor());
        tag.putInt("Generation", generation);
        tag.putString("Order", order.serializedName());
        if (goTarget != null) tag.putLong("GoTarget", goTarget.asLong());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        entityData.set(OWNER, tag.hasUUID("Owner") ? Optional.of(tag.getUUID("Owner")) : Optional.empty());
        entityData.set(OWNER_NAME, tag.getString("OwnerName"));
        entityData.set(SKIN_VALUE, tag.getString("SkinValue"));
        entityData.set(SKIN_SIGNATURE, tag.getString("SkinSignature"));
        entityData.set(TIER, tag.getByte("Tier"));
        if (tag.contains("Color")) entityData.set(COLOR, tag.getInt("Color"));
        generation = tag.getInt("Generation");
        order = PuppetOrder.FOLLOW;
        for (PuppetOrder o : PuppetOrder.values()) if (o.serializedName().equals(tag.getString("Order"))) order = o;
        goTarget = tag.contains("GoTarget") ? BlockPos.of(tag.getLong("GoTarget")) : null;
    }

    // ---------------------------------------------------------------- goals

    /** Follow (and Guard): walk back to the owner when too far, catch up at once when left far behind. */
    private final class FollowOwnerGoal extends Goal {
        @Nullable
        private ServerPlayer owner;
        private int repath;

        FollowOwnerGoal() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        private boolean follows() {
            return order == PuppetOrder.FOLLOW || order == PuppetOrder.GUARD;
        }

        @Override
        public boolean canUse() {
            if (!follows() || getTarget() != null) return false;
            owner = owner();
            double near = PuppetConfig.FOLLOW_DISTANCE.get();
            return owner != null && distanceToSqr(owner) > near * near;
        }

        @Override
        public boolean canContinueToUse() {
            if (!follows() || getTarget() != null || owner == null || owner != owner()) return false;
            double stop = PuppetConfig.FOLLOW_DISTANCE.get() * 0.6;
            return distanceToSqr(owner) > stop * stop;
        }

        @Override
        public void start() {
            repath = 0;
        }

        @Override
        public void stop() {
            owner = null;
            getNavigation().stop();
        }

        @Override
        public void tick() {
            if (owner == null) return;
            getLookControl().setLookAt(owner, 10f, getMaxHeadXRot());
            double far = PuppetConfig.FOLLOW_TELEPORT_DISTANCE.get();
            if (distanceToSqr(owner) > far * far) {
                Puppets.catchUp(PuppetEntity.this, owner);
                return;
            }
            if (--repath <= 0) {
                repath = 10;
                getNavigation().moveTo(owner, 1.0);
            }
        }
    }

    @Override
    public boolean isAlliedTo(Entity other) {
        if (other instanceof PuppetEntity puppet && ownerId() != null && ownerId().equals(puppet.ownerId())) return true;
        if (other instanceof LivingEntity && isOwnedBy(other)) return true;
        return super.isAlliedTo(other);
    }
}

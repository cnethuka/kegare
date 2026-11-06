package com.cnethuka.kegare.innate_technique.puppet_manipulation;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.NonNullList;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * A possessing player's body, left standing where they were: their skin and name, motionless and defenceless. Any
 * damage to it snaps them back at once, and they take the hit ({@link Possession#onShellHit}). Never saved: if the
 * server stops mid-possession, the player's saved {@link PossessionData} puts them back on their next login.
 */
public class PuppetShellEntity extends LivingEntity implements com.cnethuka.kegare.progression.OwnedEntity {
    private static final EntityDataAccessor<Optional<UUID>> OWNER = SynchedEntityData.defineId(PuppetShellEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<String> SKIN_VALUE = SynchedEntityData.defineId(PuppetShellEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> SKIN_SIGNATURE = SynchedEntityData.defineId(PuppetShellEntity.class, EntityDataSerializers.STRING);

    private final NonNullList<ItemStack> armor = NonNullList.withSize(4, ItemStack.EMPTY);

    public PuppetShellEntity(EntityType<? extends PuppetShellEntity> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return LivingEntity.createLivingAttributes().add(Attributes.MAX_HEALTH, 20.0);
    }

    /** The body {@code player} leaves behind, standing where they are, facing where they face. Not yet added. */
    static PuppetShellEntity create(ServerPlayer player) {
        PuppetShellEntity shell = new PuppetShellEntity(PuppetRegistry.SHELL.get(), player.level());
        shell.entityData.set(OWNER, Optional.of(player.getUUID()));
        Property textures = player.getGameProfile().getProperties().get("textures").stream().findFirst().orElse(null);
        shell.entityData.set(SKIN_VALUE, textures == null ? "" : textures.value());
        shell.entityData.set(SKIN_SIGNATURE, textures == null || textures.signature() == null ? "" : textures.signature());
        shell.setCustomName(player.getName());
        shell.setCustomNameVisible(true);
        shell.getAttribute(Attributes.MAX_HEALTH).setBaseValue(Math.max(1f, player.getMaxHealth()));
        shell.setHealth(Math.max(1f, player.getHealth()));
        // It wears what the body wears (copies, for looks: the real armor waits in the possession record).
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (slot.getType() == EquipmentSlot.Type.HUMANOID_ARMOR) shell.setItemSlot(slot, player.getItemBySlot(slot).copy());
        }
        shell.moveTo(player.getX(), player.getY(), player.getZ(), player.getYRot(), 0f);
        shell.setYHeadRot(player.getYRot());
        shell.setYBodyRot(player.getYRot());
        return shell;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(OWNER, Optional.empty());
        builder.define(SKIN_VALUE, "");
        builder.define(SKIN_SIGNATURE, "");
    }

    @Nullable
    @Override
    public UUID ownerId() {
        return entityData.get(OWNER).orElse(null);
    }

    /** A profile carrying the owner's skin property, for the client to draw them. */
    public GameProfile ownerProfile() {
        UUID id = ownerId();
        GameProfile profile = new GameProfile(id == null ? getUUID() : id, getName().getString());
        String value = entityData.get(SKIN_VALUE);
        if (!value.isEmpty()) {
            String signature = entityData.get(SKIN_SIGNATURE);
            profile.getProperties().put("textures", new Property("textures", value, signature.isEmpty() ? null : signature));
        }
        return profile;
    }

    @Override
    public void tick() {
        super.tick();
        // Nobody inside any more (they returned some other way, or logged out): the shell goes.
        if (!level().isClientSide && tickCount > 1 && !Possession.isShell(this)) discard();
    }

    /** Any damage at all snaps its owner back, and they take the hit. */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (level().isClientSide || isRemoved() || isInvulnerableTo(source)) return false;
        return Possession.onShellHit(this, source, amount);
    }

    @Override
    public void knockback(double strength, double x, double z) {
        // motionless
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean isAffectedByPotions() {
        return false;
    }

    @Override
    public Iterable<ItemStack> getArmorSlots() {
        return armor;
    }

    @Override
    public ItemStack getItemBySlot(EquipmentSlot slot) {
        return slot.getType() == EquipmentSlot.Type.HUMANOID_ARMOR ? armor.get(slot.getIndex()) : ItemStack.EMPTY;
    }

    @Override
    public void setItemSlot(EquipmentSlot slot, ItemStack stack) {
        if (slot.getType() == EquipmentSlot.Type.HUMANOID_ARMOR) armor.set(slot.getIndex(), stack);
    }

    @Override
    public HumanoidArm getMainArm() {
        return HumanoidArm.RIGHT;
    }

    @Override
    public boolean shouldShowName() {
        return true;
    }

    /** Nothing to drop: the owner's items wait in their possession record. */
    @Override
    protected void dropAllDeathLoot(net.minecraft.server.level.ServerLevel level, DamageSource source) {
    }
}

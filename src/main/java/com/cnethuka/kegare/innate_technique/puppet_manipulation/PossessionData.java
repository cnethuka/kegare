package com.cnethuka.kegare.innate_technique.puppet_manipulation;

import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.attachment.IAttachmentHolder;
import net.neoforged.neoforge.attachment.IAttachmentSerializer;

/**
 * On a player while they possess a puppet ({@code kegare:possession}, saved): which puppet and shell, and everything
 * returning restores (health, where the body stands, the stashed inventory). Saved so that if the server stops
 * mid-possession, the next login puts it all back ({@link Possession#restoreOnLogin}).
 */
public final class PossessionData {
    @Nullable
    UUID puppet;
    @Nullable
    UUID shell;
    float realHealth;
    float realMaxHealth = 20f;
    Vec3 bodyPos = Vec3.ZERO;
    float bodyYaw;
    float bodyPitch;
    @Nullable
    ResourceKey<Level> dimension;
    ListTag inventory = new ListTag();
    int selectedSlot;
    PuppetTier tier = PuppetTier.TAP;

    public boolean active() {
        return puppet != null;
    }

    @Nullable
    public UUID puppet() {
        return puppet;
    }

    @Nullable
    public UUID shell() {
        return shell;
    }

    public PuppetTier tier() {
        return tier;
    }

    void clear() {
        puppet = null;
        shell = null;
        inventory = new ListTag();
        dimension = null;
    }

    public static final IAttachmentSerializer<CompoundTag, PossessionData> SERIALIZER = new IAttachmentSerializer<>() {
        @Override
        public PossessionData read(IAttachmentHolder holder, CompoundTag tag, HolderLookup.Provider provider) {
            PossessionData d = new PossessionData();
            if (!tag.hasUUID("Puppet")) return d;
            d.puppet = tag.getUUID("Puppet");
            d.shell = tag.hasUUID("Shell") ? tag.getUUID("Shell") : null;
            d.realHealth = tag.getFloat("RealHealth");
            d.realMaxHealth = tag.contains("RealMaxHealth") ? tag.getFloat("RealMaxHealth") : 20f;
            d.bodyPos = new Vec3(tag.getDouble("X"), tag.getDouble("Y"), tag.getDouble("Z"));
            d.bodyYaw = tag.getFloat("Yaw");
            d.bodyPitch = tag.getFloat("Pitch");
            ResourceLocation dim = ResourceLocation.tryParse(tag.getString("Dimension"));
            d.dimension = dim == null ? null : ResourceKey.create(Registries.DIMENSION, dim);
            d.inventory = tag.getList("Inventory", Tag.TAG_COMPOUND);
            d.selectedSlot = tag.getInt("Selected");
            d.tier = PuppetTier.byId(tag.getInt("Tier"));
            return d;
        }

        @Override
        @Nullable
        public CompoundTag write(PossessionData d, HolderLookup.Provider provider) {
            if (!d.active()) return null;
            CompoundTag tag = new CompoundTag();
            tag.putUUID("Puppet", d.puppet);
            if (d.shell != null) tag.putUUID("Shell", d.shell);
            tag.putFloat("RealHealth", d.realHealth);
            tag.putFloat("RealMaxHealth", d.realMaxHealth);
            tag.putDouble("X", d.bodyPos.x);
            tag.putDouble("Y", d.bodyPos.y);
            tag.putDouble("Z", d.bodyPos.z);
            tag.putFloat("Yaw", d.bodyYaw);
            tag.putFloat("Pitch", d.bodyPitch);
            if (d.dimension != null) tag.putString("Dimension", d.dimension.location().toString());
            tag.put("Inventory", d.inventory);
            tag.putInt("Selected", d.selectedSlot);
            tag.putInt("Tier", d.tier.ordinal());
            return tag;
        }
    };
}

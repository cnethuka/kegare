package com.cnethuka.kegare.innate_technique.rime;

import com.cnethuka.kegare.Kegare;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/** Rime's registry entries: projectile entities, the items they render as, and attachments. */
public final class RimeRegistry {
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, Kegare.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Kegare.MODID);
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, Kegare.MODID);

    /** How the projectiles look in flight (not obtainable, not in any creative tab). */
    public static final DeferredItem<Item> FROST_SHARD_ITEM = ITEMS.registerSimpleItem("frost_shard");
    public static final DeferredItem<Item> GLACIAL_LANCE_ITEM = ITEMS.registerSimpleItem("glacial_lance");

    public static final DeferredHolder<EntityType<?>, EntityType<FrostShardEntity>> FROST_SHARD = ENTITIES.register("frost_shard",
            () -> EntityType.Builder.<FrostShardEntity>of(FrostShardEntity::new, MobCategory.MISC)
                    .sized(0.3f, 0.3f).clientTrackingRange(6).updateInterval(5).build("frost_shard"));
    public static final DeferredHolder<EntityType<?>, EntityType<GlacialLanceEntity>> GLACIAL_LANCE = ENTITIES.register("glacial_lance",
            () -> EntityType.Builder.<GlacialLanceEntity>of(GlacialLanceEntity::new, MobCategory.MISC)
                    .sized(0.6f, 0.6f).clientTrackingRange(6).updateInterval(5).build("glacial_lance"));
    public static final DeferredHolder<EntityType<?>, EntityType<SplinterShardEntity>> SPLINTER_SHARD = ENTITIES.register("splinter_shard",
            () -> EntityType.Builder.<SplinterShardEntity>of(SplinterShardEntity::new, MobCategory.MISC)
                    .sized(0.25f, 0.25f).clientTrackingRange(6).updateInterval(5).build("splinter_shard"));
    /** Glacial Spire's marker on the ground (warning circle, then the spike). Never saved. */
    public static final DeferredHolder<EntityType<?>, EntityType<GlacialSpireEntity>> GLACIAL_SPIRE = ENTITIES.register("glacial_spire",
            () -> EntityType.Builder.<GlacialSpireEntity>of(GlacialSpireEntity::new, MobCategory.MISC)
                    .sized(1.0f, 0.25f).noSave().fireImmune().clientTrackingRange(6).updateInterval(20).build("glacial_spire"));

    public static final Supplier<AttachmentType<FreezeData>> FREEZE = ATTACHMENTS.register("freeze",
            () -> AttachmentType.builder(() -> new FreezeData()).build());
    public static final Supplier<AttachmentType<RimeStats>> STATS = ATTACHMENTS.register("rime_stats",
            () -> AttachmentType.builder(() -> new RimeStats()).serialize(RimeStats.CODEC).copyOnDeath().build());
    public static final Supplier<AttachmentType<RimeTracker>> TRACKER = ATTACHMENTS.register("rime_tracker",
            () -> AttachmentType.builder(() -> new RimeTracker()).build());
    /** On a victim: who launched them with Glacial Spire (Sky High). Runtime only. */
    public static final Supplier<AttachmentType<RimeLaunch.Mark>> LAUNCH = ATTACHMENTS.register("rime_launch",
            () -> AttachmentType.builder(() -> new RimeLaunch.Mark()).build());
    /** On a level: Ice Path trails. Runtime only. */
    public static final Supplier<AttachmentType<IcePaths.Store>> ICE_PATHS = ATTACHMENTS.register("ice_paths",
            () -> AttachmentType.builder(() -> new IcePaths.Store()).build());

    private RimeRegistry() {}

    static void register(IEventBus modBus) {
        ENTITIES.register(modBus);
        ITEMS.register(modBus);
        ATTACHMENTS.register(modBus);
    }
}

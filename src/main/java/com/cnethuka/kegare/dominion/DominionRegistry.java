package com.cnethuka.kegare.dominion;

import com.cnethuka.kegare.Kegare;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/** Dominions' registrations (DESIGN §3.35). */
public final class DominionRegistry {
    private static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, Kegare.MODID);
    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, Kegare.MODID);

    /**
     * The barrier: a marker at its centre that carries it to every client in range (its size, state and look). Never saved, so a crash or
     * a stop leaves nothing behind; it has no hitbox of its own.
     */
    public static final DeferredHolder<EntityType<?>, EntityType<DominionEntity>> DOMINION = ENTITIES.register("dominion",
            () -> EntityType.Builder.<DominionEntity>of(DominionEntity::new, MobCategory.MISC)
                    .sized(0.5f, 0.5f).noSave().fireImmune().clientTrackingRange(12).updateInterval(20).build("dominion"));

    /** The Dominion Meter (saved, kept on death). Postgres storage covers it through the attachment registry (DESIGN §3.33). */
    public static final Supplier<AttachmentType<DominionData>> METER = ATTACHMENTS.register("dominion",
            () -> AttachmentType.builder(() -> new DominionData()).serialize(DominionData.CODEC).copyOnDeath().build());

    private DominionRegistry() {}

    public static void register(IEventBus modBus) {
        ENTITIES.register(modBus);
        ATTACHMENTS.register(modBus);
    }
}

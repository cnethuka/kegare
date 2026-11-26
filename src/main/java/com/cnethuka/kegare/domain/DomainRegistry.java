package com.cnethuka.kegare.domain;

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

/** Domains' registrations (DESIGN §3.35). */
public final class DomainRegistry {
    private static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, Kegare.MODID);
    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, Kegare.MODID);

    /**
     * The barrier: a marker at its centre that carries it to every client in range (its size, state and look). Never saved, so a crash or
     * a stop leaves nothing behind; it has no hitbox of its own.
     */
    public static final DeferredHolder<EntityType<?>, EntityType<DomainEntity>> DOMAIN = ENTITIES.register("domain",
            () -> EntityType.Builder.<DomainEntity>of(DomainEntity::new, MobCategory.MISC)
                    .sized(0.5f, 0.5f).noSave().fireImmune().clientTrackingRange(12).updateInterval(20).build("domain"));

    /** The Domain Meter (saved, kept on death). Postgres storage covers it through the attachment registry (DESIGN §3.33). */
    public static final Supplier<AttachmentType<DomainData>> METER = ATTACHMENTS.register("domain",
            () -> AttachmentType.builder(() -> new DomainData()).serialize(DomainData.CODEC).copyOnDeath().build());

    /** The meter as it was saved before Dominions became Domains: read once, moved to {@link #METER} on login, never written again. */
    public static final Supplier<AttachmentType<DomainData>> OLD_METER = ATTACHMENTS.register("dominion",
            () -> AttachmentType.builder(() -> new DomainData()).serialize(DomainData.CODEC).copyOnDeath().build());

    /**
     * Where a player taken into a Domain's inner space came from (saved, not kept on death: a death inside respawns as usual), so they're
     * sent back however it ends, a crash included.
     */
    public static final Supplier<AttachmentType<InnerReturn>> INNER_RETURN = ATTACHMENTS.register("inner_return",
            () -> AttachmentType.builder(() -> InnerReturn.NONE).serialize(InnerReturn.CODEC).build());

    private DomainRegistry() {}

    public static void register(IEventBus modBus) {
        ENTITIES.register(modBus);
        ATTACHMENTS.register(modBus);
    }
}

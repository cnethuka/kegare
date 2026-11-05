package com.cnethuka.kegare.innate_technique.puppet_manipulation;

import com.cnethuka.kegare.Kegare;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/** Puppet Manipulation's registry entries: the puppet and shell entities, and the attachments. */
public final class PuppetRegistry {
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, Kegare.MODID);
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, Kegare.MODID);

    /** Player-sized type; the scale attribute makes it half size (PuppetConfig.SCALE). Saved with its chunk. */
    public static final DeferredHolder<EntityType<?>, EntityType<PuppetEntity>> PUPPET = ENTITIES.register("puppet",
            () -> EntityType.Builder.<PuppetEntity>of(PuppetEntity::new, MobCategory.MISC)
                    .sized(0.6f, 1.8f).eyeHeight(1.62f).clientTrackingRange(10).build("puppet"));
    /** A possessing player's body. Never saved (PossessionData restores the player instead). */
    public static final DeferredHolder<EntityType<?>, EntityType<PuppetShellEntity>> SHELL = ENTITIES.register("puppet_shell",
            () -> EntityType.Builder.<PuppetShellEntity>of(PuppetShellEntity::new, MobCategory.MISC)
                    .sized(0.6f, 1.8f).eyeHeight(1.62f).noSave().clientTrackingRange(10).build("puppet_shell"));

    /** On a player while possessing: the puppet, the shell, and what returning restores. Saved. */
    public static final Supplier<AttachmentType<PossessionData>> POSSESSION = ATTACHMENTS.register("possession",
            () -> AttachmentType.builder(() -> new PossessionData()).serialize(PossessionData.SERIALIZER).build());
    /** On a Puppet Manipulation player: the current Directive order. Saved, kept on death. */
    public static final Supplier<AttachmentType<PuppetOwnerData>> OWNER = ATTACHMENTS.register("puppet_owner",
            () -> AttachmentType.builder(() -> new PuppetOwnerData()).serialize(PuppetOwnerData.CODEC).copyOnDeath().build());
    /** What the owner's HUD was last sent. Runtime only. */
    public static final Supplier<AttachmentType<PuppetHud.State>> HUD_STATE = ATTACHMENTS.register("puppet_hud",
            () -> AttachmentType.builder(() -> new PuppetHud.State()).build());

    private PuppetRegistry() {}

    static void register(IEventBus modBus) {
        ENTITIES.register(modBus);
        ATTACHMENTS.register(modBus);
        modBus.addListener(PuppetRegistry::attributes);
    }

    private static void attributes(EntityAttributeCreationEvent event) {
        event.put(PUPPET.get(), PuppetEntity.createAttributes().build());
        event.put(SHELL.get(), PuppetShellEntity.createAttributes().build());
    }
}

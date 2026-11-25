package com.cnethuka.kegare.dominion;

import com.cnethuka.kegare.Kegare;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/** Dominions' registrations (DESIGN §3.35). */
public final class DominionRegistry {
    private static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, Kegare.MODID);
    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, Kegare.MODID);
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Kegare.MODID);

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

    /**
     * Where a player taken into a Dominion's inner space came from (saved, not kept on death: a death inside respawns as usual), so they're
     * sent back however it ends, a crash included.
     */
    public static final Supplier<AttachmentType<InnerReturn>> INNER_RETURN = ATTACHMENTS.register("inner_return",
            () -> AttachmentType.builder(() -> InnerReturn.NONE).serialize(InnerReturn.CODEC).build());

    // The Incomplete Dominion's inner space, built of real blocks (DESIGN §3.35). None has an item or drops anything: it's only ever
    // part of an inner space, gone with it.
    /** Grey dust, cracked: the wasteland's floor (four looks, each turned at random). Breakable. */
    public static final DeferredBlock<Block> WASTELAND_GROUND = BLOCKS.registerSimpleBlock("wasteland_ground", BlockBehaviour.Properties.of()
            .mapColor(MapColor.COLOR_GRAY).strength(1.5f, 6f).sound(SoundType.TUFF).noLootTable().pushReaction(PushReaction.BLOCK));
    /** Cracked grey stone bricks: the wasteland's ruins. Breakable. */
    public static final DeferredBlock<Block> WASTELAND_RUIN = BLOCKS.registerSimpleBlock("wasteland_ruin", BlockBehaviour.Properties.of()
            .mapColor(MapColor.STONE).strength(2f, 6f).sound(SoundType.DEEPSLATE_BRICKS).noLootTable().pushReaction(PushReaction.BLOCK));
    /** Under every inner space's floor: unbreakable, so nobody digs out of it. */
    public static final DeferredBlock<Block> DOMINION_FOUNDATION = BLOCKS.registerSimpleBlock("dominion_foundation", BlockBehaviour.Properties.of()
            .mapColor(MapColor.COLOR_BLACK).strength(-1f, 3_600_000f).noLootTable().isValidSpawn((state, level, pos, type) -> false)
            .pushReaction(PushReaction.BLOCK));

    private DominionRegistry() {}

    public static void register(IEventBus modBus) {
        ENTITIES.register(modBus);
        ATTACHMENTS.register(modBus);
        BLOCKS.register(modBus);
    }
}

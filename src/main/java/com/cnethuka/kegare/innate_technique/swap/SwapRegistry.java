package com.cnethuka.kegare.innate_technique.swap;

import com.cnethuka.kegare.Kegare;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Swap's registry entries: the anchor entity and the item it renders as. */
public final class SwapRegistry {
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, Kegare.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Kegare.MODID);

    /** How the anchor looks (not obtainable, not in any creative tab). */
    public static final DeferredItem<Item> ANCHOR_ITEM = ITEMS.registerSimpleItem("swap_anchor");

    /** Anchor's marker: thrown, sticks where it lands, never saved. */
    public static final DeferredHolder<EntityType<?>, EntityType<AnchorEntity>> ANCHOR = ENTITIES.register("swap_anchor",
            () -> EntityType.Builder.<AnchorEntity>of(AnchorEntity::new, MobCategory.MISC)
                    .sized(0.25f, 0.25f).noSave().fireImmune().clientTrackingRange(8).updateInterval(2).build("swap_anchor"));

    private SwapRegistry() {}

    static void register(IEventBus modBus) {
        ENTITIES.register(modBus);
        ITEMS.register(modBus);
    }
}

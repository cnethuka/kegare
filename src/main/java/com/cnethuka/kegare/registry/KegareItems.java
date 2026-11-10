package com.cnethuka.kegare.registry;

import com.cnethuka.kegare.Kegare;
import com.cnethuka.kegare.item.PurificationTalismanItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class KegareItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Kegare.MODID);

    /** Innate technique reroll item (display name in lang). */
    public static final DeferredItem<PurificationTalismanItem> PURIFICATION_TALISMAN = ITEMS.registerItem(
            "purification_talisman", PurificationTalismanItem::new, new Item.Properties().stacksTo(16).rarity(Rarity.UNCOMMON));

    /** Eyewear for Curios' "eyes" slot (DESIGN §3.32): anyone can wear them; they mean nothing on someone without the eyes. */
    public static final DeferredItem<Item> BLINDFOLD = ITEMS.registerSimpleItem("blindfold", new Item.Properties().stacksTo(1));
    public static final DeferredItem<Item> SUNGLASSES = ITEMS.registerSimpleItem("sunglasses", new Item.Properties().stacksTo(1));

    static {
        // Existing "tinted_glasses" items, made or worn, load as these.
        ITEMS.addAlias(Kegare.id("tinted_glasses"), SUNGLASSES.getId());
    }

    private KegareItems() {}
}

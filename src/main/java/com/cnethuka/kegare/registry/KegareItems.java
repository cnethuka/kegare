package com.cnethuka.kegare.registry;

import com.cnethuka.kegare.Kegare;
import com.cnethuka.kegare.item.PurificationTalismanItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class KegareItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Kegare.MODID);

    /** Innate technique reroll item (display name in lang). */
    public static final DeferredItem<PurificationTalismanItem> PURIFICATION_TALISMAN = ITEMS.registerItem(
            "purification_talisman", PurificationTalismanItem::new, new Item.Properties().stacksTo(16).rarity(Rarity.UNCOMMON));

    private KegareItems() {}

    public static void addToCreativeTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) event.accept(PURIFICATION_TALISMAN);
    }
}

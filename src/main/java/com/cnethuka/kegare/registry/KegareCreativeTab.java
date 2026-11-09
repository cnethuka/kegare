package com.cnethuka.kegare.registry;

import com.cnethuka.kegare.Kegare;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Kegare's own creative tab (DESIGN §3.10), after the vanilla tabs, filled from {@link KegareItems#ITEMS}, so a new item appears here by itself. */
public final class KegareCreativeTab {
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Kegare.MODID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> KEGARE = TABS.register("kegare", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.kegare"))
            .icon(() -> new ItemStack(KegareItems.PURIFICATION_TALISMAN.get()))
            .withTabsBefore(CreativeModeTabs.SPAWN_EGGS)
            .displayItems((parameters, output) -> KegareItems.ITEMS.getEntries().forEach(item -> output.accept(item.get())))
            .build());

    private KegareCreativeTab() {}
}

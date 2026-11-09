package com.cnethuka.kegare.registry;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cnethuka.kegare.TestServer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/** Kegare's creative tab (DESIGN §3.10). */
@ExtendWith(EphemeralTestServerProvider.class)
class KegareCreativeTabTest {
    private static CreativeModeTab built(MinecraftServer server, CreativeModeTab tab) {
        tab.buildContents(new CreativeModeTab.ItemDisplayParameters(FeatureFlags.DEFAULT_FLAGS, false, server.registryAccess()));
        return tab;
    }

    @Test
    void everyKegareItemIsInTheTabAndNoVanillaOne(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            TestServer.ensureOverworld(server);
            CreativeModeTab tab = built(server, KegareCreativeTab.KEGARE.get());
            CreativeModeTab tools = built(server, BuiltInRegistries.CREATIVE_MODE_TAB.getOrThrow(CreativeModeTabs.TOOLS_AND_UTILITIES));
            assertFalse(KegareItems.ITEMS.getEntries().isEmpty(), "setup: there are items");
            for (var item : KegareItems.ITEMS.getEntries()) {
                ItemStack stack = new ItemStack(item.get());
                assertTrue(tab.contains(stack), item.getId() + " is in the tab");
                assertFalse(tools.contains(stack), item.getId() + " is no longer in Tools & Utilities");
            }
            assertTrue(tab.getIconItem().is(KegareItems.PURIFICATION_TALISMAN.get()), "its icon is the talisman");
        });
    }
}

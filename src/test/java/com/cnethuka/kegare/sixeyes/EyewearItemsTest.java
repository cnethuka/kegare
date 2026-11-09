package com.cnethuka.kegare.sixeyes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cnethuka.kegare.Kegare;
import com.cnethuka.kegare.TestServer;
import com.cnethuka.kegare.registry.KegareItems;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/** Eyewear's items (DESIGN §3.32): the rename from Tinted Glasses, and Curios' Eyes tag. */
@ExtendWith(EphemeralTestServerProvider.class)
class EyewearItemsTest {
    private static final TagKey<Item> EYES = TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("curios", "eyes"));

    @Test
    void theOldNameStillFindsTheSunglasses() {
        assertEquals(Kegare.id("sunglasses"), BuiltInRegistries.ITEM.getKey(KegareItems.SUNGLASSES.get()), "named as they're shown");
        assertEquals(KegareItems.SUNGLASSES.get(), BuiltInRegistries.ITEM.get(Kegare.id("tinted_glasses")), "the alias");
    }

    @Test
    void anyAlreadyMadeOrWornSurvive(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            TestServer.ensureOverworld(server);
            CompoundTag saved = new CompoundTag();
            saved.putString("id", "kegare:tinted_glasses");
            saved.putInt("count", 1);
            ItemStack loaded = ItemStack.parseOptional(server.registryAccess(), saved);
            assertTrue(loaded.is(KegareItems.SUNGLASSES.get()), "a stack saved under the old name loads as Sunglasses");
            assertEquals(Eyewear.SUNGLASSES, Eyewear.of(loaded), "and still counts as covering the eyes");
        });
    }

    @Test
    void bothGoInTheEyesSlot(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            TestServer.ensureOverworld(server);
            assertTrue(new ItemStack(KegareItems.BLINDFOLD.get()).is(EYES), "the Blindfold");
            assertTrue(new ItemStack(KegareItems.SUNGLASSES.get()).is(EYES), "the Sunglasses");
        });
    }
}

package com.cnethuka.kegare.player;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cnethuka.kegare.TestServer;
import com.cnethuka.kegare.advancement.KegareAdvancements;
import com.cnethuka.kegare.database.PlayerDataCodec;
import com.cnethuka.kegare.innatetechnique.InnateTechnique;
import com.cnethuka.kegare.innatetechnique.InnateTechniqueService;
import com.cnethuka.kegare.loadout.LoadoutService;
import com.cnethuka.kegare.progression.RefinementService;
import com.cnethuka.kegare.registry.KegareRegistries;
import com.google.gson.JsonParser;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.attachment.AttachmentHolder;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * A save holding a removed technique (DESIGN §3.3): a real player file, written by a build that still had the test techniques, holding
 * {@code test_apex} with its loadout, cooldowns, Refinement and Assessment history. It loads, and on login the player gets a free reroll
 * through the normal pipeline; nothing else of theirs changes.
 */
@ExtendWith(EphemeralTestServerProvider.class)
public class RemovedTechniqueTest {
    /** Gamma holding [Test] Apex (Legendary), Refinement 3, one Assessment, [Test] Aura cooling down. */
    public static final Path OLD_SAVE = Path.of(System.getProperty("kegare.projectDir", ".")).resolve("src/test/resources/oldsave/test_apex_player.dat");
    /** What a reroll changes, as any reroll does: the technique, its loadout, Refinement's level (Peak stays) and what was tied to the technique. */
    public static final Set<String> A_REROLL_CHANGES = Set.of("kegare:innate_technique", "kegare:loadout", "kegare:refinement", "kegare:unlocks");

    public static CompoundTag oldSave() throws Exception {
        return NbtIo.readCompressed(OLD_SAVE, NbtAccounter.unlimitedHeap());
    }

    /** Checks a player who logged in with the old save, against what it held. */
    public static void checkRerolled(ServerPlayer player, Map<String, String> before) {
        InnateTechnique now = InnateTechniqueService.current(player).orElseThrow(() -> new AssertionError("no technique after login"));
        assertTrue(KegareRegistries.INNATE_TECHNIQUE.containsKey(now.id()), "a real technique: " + now.id());
        assertNotEquals("test_apex", now.id().getPath());
        assertEquals(compact("{\"rolls_since_legendary\":1,\"total_rolls\":1}"), compact(before.get("kegare:roll")), "setup: the old roll counts");
        Map<String, String> after = PlayerDataCodec.snapshot(player, player.registryAccess());
        assertEquals(compact(before.get("kegare:roll")), compact(after.get("kegare:roll")), "free: no pity, no roll total");
        for (String attachment : before.keySet()) {
            if (A_REROLL_CHANGES.contains(attachment)) continue;
            assertEquals(compact(before.get(attachment)), compact(after.get(attachment)), attachment + " is as it was");
        }
        assertEquals(0, RefinementService.level(player), "Refinement starts again for the new technique, as on any reroll");
        assertEquals(10, RefinementService.peak(player), "Peak Refinement stays");
        assertTrue(LoadoutService.slots(player).stream().flatMap(java.util.Optional::stream).noneMatch(RemovedTechniqueTest::removed), "nothing removed on the bar");
        assertTrue(InnateTechniqueService.get(player).cooldowns().keySet().stream().noneMatch(RemovedTechniqueTest::removed), "nor cooling down");
        assertFalse(TestServer.hasAdvancement(player.server, player, KegareAdvancements.REAWAKENING), "it's not a talisman's reroll");
    }

    private static boolean removed(ResourceLocation id) {
        return id.getPath().startsWith("test_");
    }

    /** Parsed, so key order doesn't matter (Postgres's JSONB keeps its own). */
    static com.google.gson.JsonElement compact(String json) {
        return json == null ? null : JsonParser.parseString(json);
    }

    @Test
    void anOldSaveFromTheWorldGetsAFreeRerollAndKeepsTheRest(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            TestServer.ensureOverworld(server);
            try {
                CompoundTag file = oldSave();
                assertEquals("kegare:test_apex", file.getCompound(AttachmentHolder.ATTACHMENTS_NBT_KEY).getCompound("kegare:innate_technique").getString("innate_technique"), "setup: the old save");
                Map<String, String> before = new HashMap<>(PlayerDataCodec.fromPlayerFile(file));
                file.remove("UUID");
                file.remove("Pos");

                ServerPlayer player = TestServer.playerWithAdvancements(server, "old_save");
                player.load(file); // Minecraft's own load: every attachment through its own serializer
                assertEquals("kegare:test_apex", InnateTechniqueService.get(player).innateTechniqueId().orElseThrow().toString(), "it loaded as it was");
                assertEquals(9, LoadoutService.slots(player).size(), "the old bar loaded");
                PlayerEvents.handleLogin(player);
                checkRerolled(player, before);

                // Logging in again doesn't roll again.
                ResourceLocation rolled = InnateTechniqueService.current(player).orElseThrow().id();
                PlayerEvents.handleLogin(player);
                assertEquals(rolled, InnateTechniqueService.current(player).orElseThrow().id(), "once only");
            } catch (Exception e) {
                throw new RuntimeException(e);
            } finally {
                TestServer.removePlayers(server);
            }
        });
    }
}

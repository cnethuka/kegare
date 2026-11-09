package com.cnethuka.kegare.database;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cnethuka.kegare.Kegare;
import com.cnethuka.kegare.TestServer;
import com.cnethuka.kegare.innate_technique.rime.Rime;
import com.cnethuka.kegare.innatetechnique.InnateTechniqueService;
import com.cnethuka.kegare.malice.MaliceService;
import com.cnethuka.kegare.mixin.AttachmentTypeAccessor;
import com.cnethuka.kegare.player.BirthService;
import com.cnethuka.kegare.progression.Grade;
import com.cnethuka.kegare.progression.GradeService;
import com.cnethuka.kegare.rmt.BrainDamage;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.attachment.AttachmentHolder;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/** Every saved attachment, to JSON and back (DESIGN §3.33). */
@ExtendWith(EphemeralTestServerProvider.class)
class PlayerDataCodecTest {
    @SuppressWarnings("unchecked")
    private static void giveEverything(ServerPlayer player) {
        for (ResourceLocation id : PlayerDataCodec.savedAttachments()) {
            player.getData((AttachmentType<Object>) NeoForgeRegistries.ATTACHMENT_TYPES.get(id));
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void everyAttachmentWithASerializerOnAPlayerIsCovered(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            TestServer.ensureOverworld(server);
            ServerPlayer player = TestServer.damageable(server, "db_cover");
            try {
                giveEverything(player);
                Map<String, String> encoded = PlayerDataCodec.snapshot(player, server.registryAccess());
                List<String> missing = new ArrayList<>();
                int serialized = 0;
                for (var entry : NeoForgeRegistries.ATTACHMENT_TYPES.entrySet()) {
                    ResourceLocation id = entry.getKey().location();
                    if (!id.getNamespace().equals(Kegare.MODID)) continue;
                    var serializer = (net.neoforged.neoforge.attachment.IAttachmentSerializer<?, Object>) ((AttachmentTypeAccessor) (Object) entry.getValue()).kegare$serializer();
                    if (serializer == null) continue;
                    serialized++;
                    // (A serializer may write nothing for an empty value, as Possession's does when nobody's possessing: then there's nothing to store.)
                    Object value = player.getData((AttachmentType<Object>) entry.getValue());
                    if (serializer.write(value, server.registryAccess()) != null && !encoded.containsKey(id.toString())) missing.add(id.toString());
                }
                assertTrue(serialized >= 20, "setup: the registry was really read, " + serialized);
                assertTrue(missing.isEmpty(), "attachments with a serializer that aren't stored: " + missing);
                assertTrue(encoded.keySet().stream().allMatch(k -> k.startsWith(Kegare.MODID + ":")), "and nothing that isn't Kegare's");
            } finally {
                TestServer.removePlayers(server);
            }
        });
    }

    @Test
    void realDataSurvivesTheRoundTrip(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            TestServer.ensureOverworld(server);
            ServerPlayer from = TestServer.damageable(server, "db_from");
            ServerPlayer to = TestServer.damageable(server, "db_to");
            try {
                BirthService.rebirth(from, OptionalInt.of(1234));
                InnateTechniqueService.setInnateTechnique(from, Rime.RIME.get());
                GradeService.set(from, Grade.SPECIAL_GRADE);
                BrainDamage.set(from, 42.5);
                giveEverything(from);
                Map<String, String> encoded = PlayerDataCodec.snapshot(from, server.registryAccess());
                for (String json : encoded.values()) JsonParser.parseString(json); // every one is real JSON
                PlayerDataCodec.apply(to, encoded, server.registryAccess());
                assertEquals(encoded, PlayerDataCodec.snapshot(to, server.registryAccess()), "the same data out as went in");
                assertEquals(1234, MaliceService.get(to).capacity(), "Malice");
                assertTrue(MaliceService.get(to).isBorn(), "born: nothing will roll for them");
                assertEquals(Rime.RIME.get(), InnateTechniqueService.current(to).orElseThrow(), "technique");
                assertEquals(Grade.SPECIAL_GRADE, GradeService.grade(to), "Grade");
                assertEquals(42.5, BrainDamage.get(to), 1e-6, "brain meter");
            } catch (PlayerDataCodec.DecodeException e) {
                throw new AssertionError(e);
            } finally {
                TestServer.removePlayers(server);
            }
        });
    }

    @Test
    void bigWholeNumbersStayExact() {
        long big = (1L << 60) + 3;
        CompoundTag tag = new CompoundTag();
        tag.putLong("big", big);
        tag.putInt("small", 7);
        tag.putDouble("half", 0.5);
        tag.putString("text", "12");
        CompoundTag back = (CompoundTag) PlayerDataCodec.fromJson(JsonParser.parseString(PlayerDataCodec.toJson(tag)));
        assertEquals(big, back.getLong("big"), "past what a double can hold");
        assertEquals(7, back.getInt("small"));
        assertEquals(0.5, back.getDouble("half"));
        assertEquals("12", back.getString("text"), "a string stays a string");
        assertTrue(back.get("big") instanceof LongTag);
    }

    @Test
    void aRowThatWontDecodeChangesNothing(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            TestServer.ensureOverworld(server);
            ServerPlayer player = TestServer.damageable(server, "db_bad");
            try {
                BirthService.rebirth(player, OptionalInt.of(777));
                Map<String, String> good = PlayerDataCodec.snapshot(player, server.registryAccess());
                Map<String, String> bad = new java.util.HashMap<>(good);
                bad.put(Kegare.MODID + ":malice", "{\"tier\":\"no such tier\",\"capacity\":\"not a number\"}");
                assertThrows(PlayerDataCodec.DecodeException.class, () -> PlayerDataCodec.apply(player, bad, server.registryAccess()));
                assertEquals(777, MaliceService.get(player).capacity(), "what they had is untouched");
            } finally {
                TestServer.removePlayers(server);
            }
        });
    }

    @Test
    void kegaresKeysComeOutOfAPlayerFileAndNobodyElses() {
        CompoundTag attachments = new CompoundTag();
        attachments.put("kegare:malice", new CompoundTag());
        attachments.put("kegare:traits", new CompoundTag());
        attachments.put("curios:inventory", new CompoundTag());
        CompoundTag file = new CompoundTag();
        file.put(AttachmentHolder.ATTACHMENTS_NBT_KEY, attachments);
        file.putString("Dimension", "minecraft:overworld");
        assertEquals(2, PlayerDataCodec.stripFromPlayerFile(file));
        assertFalse(file.getCompound(AttachmentHolder.ATTACHMENTS_NBT_KEY).contains("kegare:malice"));
        assertTrue(file.getCompound(AttachmentHolder.ATTACHMENTS_NBT_KEY).contains("curios:inventory"), "another mod's stays with the world");
        assertTrue(file.contains("Dimension"), "vanilla's stays too");
    }
}

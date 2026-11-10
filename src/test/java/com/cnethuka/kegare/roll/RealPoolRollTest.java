package com.cnethuka.kegare.roll;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cnethuka.kegare.TestServer;
import com.cnethuka.kegare.client.gui.RevealScript;
import com.cnethuka.kegare.innate_technique.puppet_manipulation.PuppetManipulation;
import com.cnethuka.kegare.innate_technique.rime.Rime;
import com.cnethuka.kegare.innate_technique.swap.Swap;
import com.cnethuka.kegare.innate_technique.time_manipulation.TimeManipulation;
import com.cnethuka.kegare.innatetechnique.InnateTechnique;
import com.cnethuka.kegare.innatetechnique.InnateTechniqueRarity;
import com.cnethuka.kegare.innatetechnique.InnateTechniqueService;
import com.cnethuka.kegare.network.RevealPayload;
import com.cnethuka.kegare.player.BirthService;
import com.cnethuka.kegare.registry.KegareRegistries;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.netty.buffer.Unpooled;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Rolling with the real pool alone (DESIGN §3.3, §3.4): one technique per rarity, so holding one leaves its rarity with nothing
 * to roll.
 */
@ExtendWith(EphemeralTestServerProvider.class)
class RealPoolRollTest {
    private static final Path PROJECT = Path.of(System.getProperty("kegare.projectDir", "."));

    private static Map<InnateTechniqueRarity, InnateTechnique> real() {
        Map<InnateTechniqueRarity, InnateTechnique> out = new EnumMap<>(InnateTechniqueRarity.class);
        out.put(InnateTechniqueRarity.COMMON, Swap.SWAP.get());
        out.put(InnateTechniqueRarity.RARE, Rime.RIME.get());
        out.put(InnateTechniqueRarity.EPIC, PuppetManipulation.PUPPET_MANIPULATION.get());
        out.put(InnateTechniqueRarity.LEGENDARY, TimeManipulation.TIME_MANIPULATION.get());
        return out;
    }

    /** Every player animation the mod ships, by name (the files group them: {@code {"animations": {name: ...}}}). */
    private static Set<String> animations() {
        Set<String> out = new java.util.HashSet<>();
        try (var files = Files.list(PROJECT.resolve("src/main/resources/assets/kegare/player_animations"))) {
            for (Path file : files.filter(f -> f.toString().endsWith(".json")).toList()) {
                JsonObject json = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
                if (json.has("animations")) out.addAll(json.getAsJsonObject("animations").keySet());
            }
        } catch (java.io.IOException e) {
            throw new AssertionError(e);
        }
        return out;
    }

    private static ServerPlayer player(MinecraftServer server, String name) {
        TestServer.ensureOverworld(server);
        ServerPlayer p = TestServer.player(server, name);
        BirthService.rebirth(p, OptionalInt.of(1000));
        return p;
    }

    @Test
    void thePoolIsTheRealTechniquesOnePerRarity(MinecraftServer server) {
        Map<InnateTechniqueRarity, InnateTechnique> real = real();
        assertEquals(Set.copyOf(real.values()), KegareRegistries.INNATE_TECHNIQUE.stream().collect(Collectors.toSet()), "nothing else registered");
        real.forEach((rarity, technique) -> assertEquals(rarity, technique.rarity(), technique.id().toString()));
    }

    @Test
    void aRollNeverLandsOnWhatTheyHoldEvenWhenTheirRarityHasNothingElse(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            ServerPlayer p = player(server, "real_pool");
            for (InnateTechnique held : real().values()) {
                Set<InnateTechniqueRarity> seen = EnumSet.noneOf(InnateTechniqueRarity.class);
                for (int i = 0; i < 60; i++) {
                    InnateTechniqueService.setInnateTechnique(p, held);
                    RollService.get(p).set(new PityState(0, 0));
                    RollService.Outcome outcome = RollService.rollInnateTechnique(p, RollReason.ADMIN).orElseThrow(() -> new AssertionError("nothing to roll from " + held.id()));
                    assertNotEquals(held, outcome.innateTechnique(), "never into yourself");
                    assertNotEquals(held.rarity(), outcome.rarity(), "their rarity has nothing else left");
                    seen.add(outcome.rarity());
                }
                assertFalse(seen.isEmpty(), "rolled");
            }
            TestServer.removePlayers(server);
        });
    }

    @Test
    void hardPityWaitsWhileTheyHoldTheOnlyLegendary(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            ServerPlayer p = player(server, "real_pity");
            InnateTechniqueService.setInnateTechnique(p, TimeManipulation.TIME_MANIPULATION.get());
            RollService.get(p).set(new PityState(49, 60)); // the next counted roll is hard pity
            RollService.Outcome outcome = RollService.rollInnateTechnique(p, RollReason.ADMIN).orElseThrow();
            assertNotEquals(InnateTechniqueRarity.LEGENDARY, outcome.rarity(), "no other Legendary to give");
            assertFalse(outcome.byHardPity());
            assertEquals(new PityState(50, 61), RollService.get(p).state(), "the counter keeps climbing");

            InnateTechniqueService.setInnateTechnique(p, Rime.RIME.get());
            outcome = RollService.rollInnateTechnique(p, RollReason.ADMIN).orElseThrow();
            assertEquals(TimeManipulation.TIME_MANIPULATION.get(), outcome.innateTechnique(), "once there's a Legendary to give, hard pity gives it");
            assertTrue(outcome.byHardPity());
            assertEquals(new PityState(0, 62), RollService.get(p).state());
            TestServer.removePlayers(server);
        });
    }

    @Test
    void aReplacementRollCanLandOnEveryRarityAndCostsNothing(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            ServerPlayer p = player(server, "real_replacement");
            PityState pity = new PityState(12, 34);
            Set<InnateTechniqueRarity> seen = EnumSet.noneOf(InnateTechniqueRarity.class);
            for (int i = 0; i < 400 && seen.size() < 4; i++) {
                InnateTechniqueService.setInnateTechnique(p, null); // what a removed technique leaves: nothing held
                RollService.get(p).set(pity);
                RollService.Outcome outcome = RollService.rollInnateTechnique(p, RollReason.REPLACEMENT).orElseThrow();
                assertEquals(pity, RollService.get(p).state(), "free");
                seen.add(outcome.rarity());
            }
            assertEquals(EnumSet.allOf(InnateTechniqueRarity.class), seen, "nothing held: every rarity can come up");
            TestServer.removePlayers(server);
        });
    }

    @Test
    void eachRaritysRevealWithARealTechnique(MinecraftServer server) throws Exception {
        JsonObject lang = JsonParser.parseString(Files.readString(PROJECT.resolve("src/generated/resources/assets/kegare/lang/en_us.json"))).getAsJsonObject();
        TestServer.onServerThread(server, () -> {
            ServerPlayer p = player(server, "real_reveal");
            for (Map.Entry<InnateTechniqueRarity, InnateTechnique> entry : real().entrySet()) {
                InnateTechniqueRarity rarity = entry.getKey();
                InnateTechnique technique = entry.getValue();
                InnateTechniqueService.setInnateTechnique(p, rarity == InnateTechniqueRarity.COMMON ? Rime.RIME.get() : Swap.SWAP.get());
                RollService.Outcome outcome = RollService.rollInnateTechnique(p, RollReason.ADMIN, technique).orElseThrow();
                assertEquals(technique, outcome.innateTechnique());

                // What the client's reveal is sent, through the wire.
                RevealPayload sent = new RevealPayload(outcome.reason(), Optional.of(technique.id()), outcome.rollNumber(), outcome.byHardPity(), false, 0, 0, false, List.of());
                FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
                RevealPayload.STREAM_CODEC.encode(buf, sent);
                assertEquals(sent, RevealPayload.STREAM_CODEC.decode(buf), rarity + ": the reveal arrives as sent");

                // And what it plays and shows for that rarity and that technique.
                RevealScript script = RevealScript.of(new RevealScript.Input(rarity, false, false, false, true));
                List<RevealScript.Scene> scenes = script.steps().stream().map(RevealScript.Step::scene).toList();
                assertEquals(RevealScript.Scene.OMEN, scenes.get(0), rarity + " opens on the omen");
                assertEquals(List.of(RevealScript.Scene.NAME, RevealScript.Scene.RESULT), scenes.subList(scenes.size() - 2, scenes.size()), rarity + " ends on the name, then holds");
                assertEquals(rarity.ordinal() + 1, RevealScript.stars(rarity), rarity + "'s stars");
                for (String key : List.of(technique.descriptionId(), technique.descriptionId() + ".desc", technique.epithetId(), rarity.translationKey())) {
                    assertTrue(lang.has(key) && !lang.get(key).getAsString().isBlank(), rarity + ": " + key);
                }
                String animation = technique.signatureAnimation().orElseThrow(() -> new AssertionError(technique.id() + " has no signature animation")).getPath();
                assertTrue(animations().contains(animation), rarity + ": its animation " + animation + " is in player_animations");
            }
            TestServer.removePlayers(server);
        });
    }
}

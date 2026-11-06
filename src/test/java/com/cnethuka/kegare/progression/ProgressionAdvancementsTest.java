package com.cnethuka.kegare.progression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cnethuka.kegare.TestServer;
import com.cnethuka.kegare.advancement.KegareAdvancements;
import com.cnethuka.kegare.innate_technique.puppet_manipulation.PuppetManipulation;
import com.cnethuka.kegare.innate_technique.rime.Rime;
import com.cnethuka.kegare.innate_technique.swap.Swap;
import com.cnethuka.kegare.innatetechnique.InnateTechniqueService;
import com.cnethuka.kegare.player.BirthService;
import java.util.List;
import java.util.OptionalInt;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/** The five progression advancements: silent, hidden, and earned when they should be. The rule tables are what datagen enforces. */
@ExtendWith(EphemeralTestServerProvider.class)
class ProgressionAdvancementsTest {
    private static final List<ResourceLocation> ALL = List.of(KegareAdvancements.MOVING_UP, KegareAdvancements.PROVEN,
            KegareAdvancements.RECOGNIZED, KegareAdvancements.OUTSIDE_THE_LADDER, KegareAdvancements.FULL_KIT);

    @AfterEach
    void cleanUp(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> TestServer.removePlayers(server));
    }

    private static ServerPlayer sorcerer(MinecraftServer server, String name) {
        TestServer.ensureOverworld(server);
        ServerPlayer p = TestServer.damageable(server, name);
        server.overworld().addNewPlayer(p);
        BirthService.rebirth(p, OptionalInt.of(5000));
        InnateTechniqueService.setInnateTechnique(p, Rime.RIME.get());
        return p;
    }

    private static boolean earned(ServerPlayer p, ResourceLocation id) {
        return p.getAdvancements().getOrStartProgress(p.server.getAdvancements().get(id)).isDone();
    }

    @Test
    void theyAreAllSilentAndHidden() {
        assertTrue(KegareAdvancements.SILENT.containsAll(ALL));
        assertTrue(KegareAdvancements.SILENT_HIDDEN.containsAll(ALL));
        assertEquals(5, Set.copyOf(ALL).size());
    }

    @Test
    void theGeneratedOnesAnnounceNothingAndAreHidden(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            for (ResourceLocation id : ALL) {
                var holder = server.getAdvancements().get(id);
                assertTrue(holder != null, id + " wasn't generated");
                var display = holder.value().display().orElseThrow();
                assertFalse(display.shouldAnnounceChat(), id + " would announce");
                assertTrue(display.isHidden(), id + " isn't hidden");
                assertEquals(KegareAdvancements.AWAKENING, holder.value().parent().orElseThrow(), id + " isn't on the Base tab");
            }
        });
    }

    @Test
    void aPromotionAwardsItsGradeAndNothingAbove(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            ServerPlayer p = sorcerer(server, "adv_ladder");
            assertFalse(earned(p, KegareAdvancements.MOVING_UP));
            GradeService.addXp(p, 200);
            assertTrue(earned(p, KegareAdvancements.MOVING_UP));
            assertFalse(earned(p, KegareAdvancements.PROVEN));
            GradeService.set(p, Grade.SPECIAL_GRADE);
            assertTrue(earned(p, KegareAdvancements.PROVEN));
            assertTrue(earned(p, KegareAdvancements.RECOGNIZED));
            assertTrue(earned(p, KegareAdvancements.OUTSIDE_THE_LADDER));
        });
    }

    @Test
    void fullKitComesWithTheLastSpecial(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            ServerPlayer rime = sorcerer(server, "adv_kit_rime");
            RefinementService.setLevel(rime, 6);
            assertFalse(earned(rime, KegareAdvancements.FULL_KIT));
            RefinementService.setLevel(rime, 7);
            assertTrue(earned(rime, KegareAdvancements.FULL_KIT), "Rime's last is the special of ability 4");

            ServerPlayer puppets = sorcerer(server, "adv_kit_puppets");
            InnateTechniqueService.setInnateTechnique(puppets, PuppetManipulation.PUPPET_MANIPULATION.get());
            RefinementService.setLevel(puppets, 8);
            assertFalse(earned(puppets, KegareAdvancements.FULL_KIT));
            RefinementService.setLevel(puppets, 9);
            assertTrue(earned(puppets, KegareAdvancements.FULL_KIT), "Zip and Leash are the last");

            ServerPlayer swap = sorcerer(server, "adv_kit_swap");
            InnateTechniqueService.setInnateTechnique(swap, Swap.SWAP.get());
            RefinementService.setLevel(swap, 7);
            assertTrue(earned(swap, KegareAdvancements.FULL_KIT), "the ultimate doesn't count");
        });
    }
}

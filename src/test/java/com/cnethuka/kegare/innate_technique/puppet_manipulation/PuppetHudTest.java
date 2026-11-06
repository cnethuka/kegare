package com.cnethuka.kegare.innate_technique.puppet_manipulation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cnethuka.kegare.TestServer;
import com.cnethuka.kegare.innate_technique.swap.Swap;
import com.cnethuka.kegare.innatetechnique.InnateTechniqueService;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/** What the owner's HUD gets told, and when: it's only ever sent on a change, so a missed one sticks on their screen. */
@ExtendWith(EphemeralTestServerProvider.class)
class PuppetHudTest {
    private static PuppetHud.Payload last(List<PuppetHud.Payload> sent) {
        return sent.get(sent.size() - 1);
    }

    @Test
    void rerollingAwayClearsThePuppetCount(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            ServerLevel level = PossessionTest.setUp(server);
            ServerPlayer p = PossessionTest.owner(server, level, "hud_reroll");
            List<PuppetHud.Payload> sent = new ArrayList<>();
            try {
                PuppetHud.update(p, sent::add);
                assertTrue(last(sent).holds());
                assertEquals(0, last(sent).puppets());

                PuppetTest.create(server, p, 0);
                PuppetHud.update(p, sent::add);
                assertEquals(1, last(sent).puppets(), "the new puppet is on the panel");
                int count = sent.size();
                PuppetHud.update(p, sent::add);
                assertEquals(count, sent.size(), "nothing changed, nothing sent");

                InnateTechniqueService.setInnateTechnique(p, Swap.SWAP.get());
                PuppetHud.update(p, sent::add);
                assertEquals(count + 1, sent.size(), "the panel is told, even though the reroll just marked it dirty");
                assertFalse(last(sent).holds(), "and it clears");
                assertEquals(0, last(sent).puppets());

                PuppetHud.update(p, sent::add);
                assertEquals(count + 1, sent.size(), "only the once");
            } finally {
                PossessionTest.cleanUp(server, level, p);
            }
        });
    }

    @Test
    void markingItDirtyResendsWithoutAChange(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            ServerLevel level = PossessionTest.setUp(server);
            ServerPlayer p = PossessionTest.owner(server, level, "hud_dirty");
            List<PuppetHud.Payload> sent = new ArrayList<>();
            try {
                PuppetHud.update(p, sent::add);
                int count = sent.size();
                PuppetHud.markDirty(p);
                PuppetHud.update(p, sent::add);
                assertEquals(count + 1, sent.size(), "a holder hears again");
                assertEquals(sent.get(count - 1), last(sent), "the same numbers");
            } finally {
                PossessionTest.cleanUp(server, level, p);
            }
        });
    }

    @Test
    void someoneWhoNeverHeldItIsNeverSentAnything(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            ServerLevel level = PossessionTest.setUp(server);
            ServerPlayer p = PossessionTest.owner(server, level, "hud_never");
            List<PuppetHud.Payload> sent = new ArrayList<>();
            try {
                InnateTechniqueService.setInnateTechnique(p, Swap.SWAP.get());
                PuppetHud.update(p, sent::add);
                PuppetHud.markDirty(p);
                PuppetHud.update(p, sent::add);
                assertTrue(sent.isEmpty(), "their HUD has nothing to clear");
            } finally {
                PossessionTest.cleanUp(server, level, p);
            }
        });
    }
}

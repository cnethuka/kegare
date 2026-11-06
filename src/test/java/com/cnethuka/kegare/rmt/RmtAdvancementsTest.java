package com.cnethuka.kegare.rmt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cnethuka.kegare.TestServer;
import com.cnethuka.kegare.advancement.KegareAdvancements;
import com.cnethuka.kegare.config.KegareServerConfig;
import com.cnethuka.kegare.innate_technique.rime.Rime;
import com.cnethuka.kegare.innatetechnique.CastIntent;
import com.cnethuka.kegare.innatetechnique.CastManager;
import com.cnethuka.kegare.innatetechnique.InnateTechniqueService;
import com.cnethuka.kegare.loadout.LoadoutResult;
import com.cnethuka.kegare.loadout.LoadoutService;
import com.cnethuka.kegare.malice.MaliceService;
import com.cnethuka.kegare.player.BirthService;
import java.util.Optional;
import java.util.OptionalInt;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/** a_green_light (taking in the orb) and its_been_a_while (RMT at 6 HP or less within 10 s of a player's hit). */
@ExtendWith(EphemeralTestServerProvider.class)
class RmtAdvancementsTest {
    @Test
    void advancements(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            TestServer.ensureOverworld(server);
            ServerPlayer p = TestServer.playerWithAdvancements(server, "rmt_advancements");
            BirthService.rebirth(p, OptionalInt.of(500));
            InnateTechniqueService.setInnateTechnique(p, Rime.RIME.get());
            p.moveTo(0.5, 100, 0.5, -90f, 0f);
            TestServer.advanceGameTime(server, KegareServerConfig.ticks(KegareServerConfig.COMBAT_TAG_SECONDS) + 1);

            // A Green Light: take in the orb.
            assertFalse(TestServer.hasAdvancement(server, p, KegareAdvancements.A_GREEN_LIGHT));
            GreenOrbService.force(p);
            assertNotNull(GreenOrbService.state(p).orb());
            for (int i = 0; GreenOrbService.state(p).hasOrb() && i <= RmtConfig.ticks(RmtConfig.ORB_FOLLOW_SECONDS); i++) {
                p.lookAt(EntityAnchorArgument.Anchor.EYES, GreenOrbService.state(p).orb());
                GreenOrbService.tick(p);
            }
            assertTrue(TestServer.hasAdvancement(server, p, KegareAdvancements.A_GREEN_LIGHT), "unlocking awards A Green Light");

            assertEquals(LoadoutResult.OK, LoadoutService.edit(p, LoadoutService.Action.SET, 0, 0, Optional.of(RmtIds.ABILITY)));
            InnateTechniqueService.select(p, 0);

            // Hit by a player just now, but not on the brink (8 HP): no.
            p.setHealth(8f);
            GreenOrbService.onHitByPlayer(p);
            cast(p);
            assertFalse(TestServer.hasAdvancement(server, p, KegareAdvancements.ITS_BEEN_A_WHILE), "8 HP isn't the brink");

            // On the brink, but the player's hit was more than 10 s ago: no.
            p.setHealth(5f);
            TestServer.advanceGameTime(server, (int) RmtAdvancements.BRINK_WINDOW_TICKS + 1);
            cast(p);
            assertFalse(TestServer.hasAdvancement(server, p, KegareAdvancements.ITS_BEEN_A_WHILE), "the hit was too long ago");

            // On the brink and just hit by a player: yes.
            p.setHealth(5f);
            GreenOrbService.onHitByPlayer(p);
            cast(p);
            assertTrue(TestServer.hasAdvancement(server, p, KegareAdvancements.ITS_BEEN_A_WHILE), "It's been a while, huh?");
        });
    }

    /** Starts and releases an RMT cast with full Malice and no cooldown. */
    private static void cast(ServerPlayer p) {
        InnateTechniqueService.clearCooldowns(p);
        MaliceService.refill(p);
        CastManager.handleIntent(p, CastIntent.CAST_PRESS);
        assertNotNull(CastManager.state(p).casting(), "setup: RMT started");
        CastManager.handleIntent(p, CastIntent.CAST_RELEASE);
    }
}

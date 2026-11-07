package com.cnethuka.kegare.innatetechnique;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cnethuka.kegare.TestServer;
import com.cnethuka.kegare.combat.CombatHelper;
import com.cnethuka.kegare.innatetechnique.test.TestAbilities;
import com.cnethuka.kegare.innatetechnique.test.TestInnateTechniques;
import com.cnethuka.kegare.malice.MaliceService;
import com.cnethuka.kegare.player.BirthService;
import com.cnethuka.kegare.trait.KegareTraits;
import com.cnethuka.kegare.trait.Trait;
import com.cnethuka.kegare.trait.TraitService;
import java.util.OptionalInt;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/** What Instant Cast's keys send, in the order they send it, on the real cast pipeline: each type, base and special. */
@ExtendWith(EphemeralTestServerProvider.class)
class InstantCastPipelineTest {
    private static final int DASH = 0, BURST = 1, HOVER = 2, AURA = 3;

    private static ServerPlayer player(MinecraftServer server, String name) {
        TestServer.ensureOverworld(server);
        ServerPlayer p = TestServer.player(server, name);
        BirthService.rebirth(p, OptionalInt.of(5000));
        InnateTechniqueService.setInnateTechnique(p, TestInnateTechniques.APEX.get()); // Dash, Burst, Hover, Aura
        MaliceService.refill(p);
        return p;
    }

    /** A number key going down. */
    private static void number(ServerPlayer p, int slot) {
        InnateTechniqueService.select(p, slot);
        CastManager.handleIntent(p, CastIntent.CAST_PRESS);
    }

    /** R held, and a number key going down. */
    private static void rNumber(ServerPlayer p, int slot) {
        InnateTechniqueService.select(p, slot);
        CastManager.handleIntent(p, CastIntent.SPECIAL_PRESS);
    }

    @Test
    void instantFiresOnThePressAndTheReleaseDoesNothing(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            ServerPlayer p = player(server, "ic_instant");
            InnateTechniqueService.select(p, HOVER); // whatever was selected before doesn't matter
            number(p, DASH);
            assertTrue(InnateTechniqueService.cooldownRemaining(p, TestAbilities.DASH) > 0, "fired at once");
            double after = MaliceService.get(p).current();
            CastManager.handleIntent(p, CastIntent.CAST_RELEASE);
            assertEquals(after, MaliceService.get(p).current(), 1e-6);
        });
    }

    @Test
    void chargedChargesOnThePressAndFiresOnTheRelease(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            ServerPlayer p = player(server, "ic_charged");
            number(p, BURST);
            assertSame(TestAbilities.BURST, CastManager.state(p).casting().ability());
            assertEquals(0, InnateTechniqueService.cooldownRemaining(p, TestAbilities.BURST), "nothing's gone off yet");
            TestServer.advanceGameTime(server, 40);
            CastManager.handleIntent(p, CastIntent.CAST_RELEASE);
            assertNull(CastManager.state(p).casting());
            assertTrue(InnateTechniqueService.cooldownRemaining(p, TestAbilities.BURST) > 0, "it fired");
        });
    }

    @Test
    void channeledRunsFromThePressToTheRelease(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            ServerPlayer p = player(server, "ic_channeled");
            number(p, HOVER);
            assertSame(TestAbilities.HOVER, CastManager.state(p).casting().ability());
            CastManager.handleIntent(p, CastIntent.CAST_RELEASE);
            assertNull(CastManager.state(p).casting());
        });
    }

    @Test
    void toggleSwitchesOnEachPressAndTheReleaseDoesNothing(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            ServerPlayer p = player(server, "ic_toggle");
            number(p, AURA);
            CastManager.handleIntent(p, CastIntent.CAST_RELEASE);
            assertTrue(CastManager.isToggleActive(p, TestAbilities.AURA), "letting go leaves it on");
            number(p, AURA);
            CastManager.handleIntent(p, CastIntent.CAST_RELEASE);
            assertFalse(CastManager.isToggleActive(p, TestAbilities.AURA));
        });
    }

    @Test
    void aSpecialIsCastFromAnotherSlotInOneGo(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            ServerPlayer p = player(server, "ic_special");
            number(p, AURA); // what Blink needs
            CastManager.handleIntent(p, CastIntent.CAST_RELEASE);
            rNumber(p, DASH);
            assertEquals(DASH, InnateTechniqueService.get(p).selectedSlot());
            assertTrue(InnateTechniqueService.cooldownRemaining(p, TestAbilities.BLINK) > 0, "Blink fired");
            CastManager.handleIntent(p, CastIntent.SPECIAL_RELEASE);
        });
    }

    @Test
    void aChargedSpecialFiresOnItsOwnReleaseOnly(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            ServerPlayer p = player(server, "ic_charged_special");
            number(p, AURA);
            CastManager.handleIntent(p, CastIntent.CAST_RELEASE);
            TraitService.add(p, KegareTraits.SIX_EYES.get(), Trait.AddReason.ADMIN);
            rNumber(p, HOVER);
            assertSame(TestAbilities.UPDRAFT, CastManager.state(p).casting().ability());
            TestServer.advanceGameTime(server, 20);
            CastManager.handleIntent(p, CastIntent.CAST_RELEASE);
            assertNotNull(CastManager.state(p).casting(), "a base release doesn't touch a special's charge");
            CastManager.handleIntent(p, CastIntent.SPECIAL_RELEASE);
            assertNull(CastManager.state(p).casting());
            assertTrue(InnateTechniqueService.cooldownRemaining(p, TestAbilities.UPDRAFT) > 0, "it fired");
            TraitService.remove(p, KegareTraits.SIX_EYES.get(), Trait.RemoveReason.ADMIN);
        });
    }

    @Test
    void aStunLeavesNothingStuck(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            ServerPlayer p = player(server, "ic_stun");
            number(p, BURST);
            CombatHelper.stun(p, 10);
            CastManager.tick(p);
            assertNull(CastManager.state(p).casting(), "interrupted");
            double malice = MaliceService.get(p).current();
            CastManager.handleIntent(p, CastIntent.CAST_RELEASE); // the key coming up later
            assertNull(CastManager.state(p).casting());
            assertEquals(0, InnateTechniqueService.cooldownRemaining(p, TestAbilities.BURST), "no cooldown, as ever");
            assertEquals(malice, MaliceService.get(p).current(), 1e-6, "and nothing paid");
        });
    }

    @Test
    void anEmptySlotCastsNothing(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            ServerPlayer p = player(server, "ic_empty");
            double malice = MaliceService.get(p).current();
            number(p, 8);
            assertEquals(8, InnateTechniqueService.get(p).selectedSlot());
            assertNull(CastManager.state(p).casting());
            assertEquals(malice, MaliceService.get(p).current(), 1e-6);
            rNumber(p, 8);
            assertNull(CastManager.state(p).casting());
            assertEquals(malice, MaliceService.get(p).current(), 1e-6);
        });
    }
}

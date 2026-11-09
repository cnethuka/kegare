package com.cnethuka.kegare.rmt;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cnethuka.kegare.TestServer;
import com.cnethuka.kegare.advancement.KegareAdvancements;
import com.cnethuka.kegare.player.BirthService;
import java.util.OptionalInt;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementType;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/** "Are They High?": explaining Reverse Malice Technique, in your own words, to the player you're fighting. */
@ExtendWith(EphemeralTestServerProvider.class)
class AreTheyHighTest {
    static final String FIRST = "so malice is negative energy right";
    static final String SECOND = "you multiply it against itself and it turns positive";

    static ServerPlayer player(MinecraftServer server, String name, double x) {
        TestServer.ensureOverworld(server);
        ServerPlayer p = TestServer.damageable(server, name);
        server.overworld().addNewPlayer(p);
        BirthService.rebirth(p, OptionalInt.of(1000));
        p.moveTo(x, 120, 0.5, 0f, 0f);
        p.setHealth(p.getMaxHealth());
        return p;
    }

    /** Two players in a fight, the first having "It's been a while". */
    static ServerPlayer[] fight(MinecraftServer server, String name) {
        server.setPvpAllowed(true);
        ServerPlayer p = player(server, name + "_p", 0.5);
        ServerPlayer q = player(server, name + "_q", 6.5);
        KegareAdvancements.award(p, KegareAdvancements.ITS_BEEN_A_WHILE);
        q.invulnerableTime = 0;
        q.hurt(p.damageSources().playerAttack(p), 1f); // a hit between them
        q.setHealth(q.getMaxHealth());
        return new ServerPlayer[] {p, q};
    }

    private static boolean has(MinecraftServer server, ServerPlayer p) {
        return TestServer.hasAdvancement(server, p, KegareAdvancements.ARE_THEY_HIGH);
    }

    @Test
    void itsAHiddenAnnouncedTaskUnderItsBeenAWhile(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            AdvancementHolder holder = server.getAdvancements().get(KegareAdvancements.ARE_THEY_HIGH);
            assertNotNull(holder, "loads");
            DisplayInfo display = holder.value().display().orElseThrow();
            assertTrue(display.isHidden(), "hidden");
            assertTrue(display.shouldAnnounceChat(), "announced in chat");
            assertTrue(display.getType() == AdvancementType.TASK, "a task");
            assertTrue(holder.value().parent().filter(KegareAdvancements.ITS_BEEN_A_WHILE::equals).isPresent(), "under It's been a while");
        });
    }

    @Test
    void twoMessagesAwardOnTheOneThatCompletesIt(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            try {
                ServerPlayer p = fight(server, "high_two")[0];
                assertFalse(AreTheyHigh.onChat(p, FIRST), "half of it");
                assertFalse(has(server, p), "not yet");
                assertTrue(AreTheyHigh.onChat(p, SECOND), "the message that completes it");
                assertTrue(has(server, p), "awarded at once");
            } finally {
                AreTheyHigh.clear();
                TestServer.removePlayers(server);
            }
        });
    }

    @Test
    void oneMessageInTheirOwnWordsWithTyposAndExtraText(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            try {
                ServerPlayer p = fight(server, "high_one")[0];
                assertTrue(AreTheyHigh.onChat(p, "OK listen. You take your MALISE, the negitive enrgy, and multipy it by itself, "
                        + "so the result comes out postive, which heals. Simple math really."), "typos, caps and extra text");
                assertTrue(has(server, p));
            } finally {
                AreTheyHigh.clear();
                TestServer.removePlayers(server);
            }
        });
    }

    @Test
    void whatNeverPasses(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            try {
                // "lol rmt", however often.
                ServerPlayer a = fight(server, "high_lol")[0];
                for (int i = 0; i < 12; i++) assertFalse(AreTheyHigh.onChat(a, "lol rmt"), "lol rmt");

                // All four ideas in too few words.
                ServerPlayer b = fight(server, "high_short")[0];
                assertFalse(AreTheyHigh.onChat(b, "malice negative multiply positive"), "under 12 words");

                // No fight: nobody hit.
                ServerPlayer c = player(server, "high_calm", 0.5);
                player(server, "high_calm_q", 4.5);
                KegareAdvancements.award(c, KegareAdvancements.ITS_BEEN_A_WHILE);
                AreTheyHigh.onChat(c, FIRST);
                assertFalse(AreTheyHigh.onChat(c, SECOND), "no fight");

                // The hit was over 10 s ago.
                ServerPlayer d = fight(server, "high_stale")[0];
                TestServer.advanceGameTime(server, (int) AreTheyHigh.FIGHT_TICKS + 1);
                AreTheyHigh.onChat(d, FIRST);
                assertFalse(AreTheyHigh.onChat(d, SECOND), "the fight was too long ago");

                // The one they fought is past 16 blocks.
                ServerPlayer[] far = fight(server, "high_far");
                far[1].moveTo(30.5, 120, 0.5, 0f, 0f);
                AreTheyHigh.onChat(far[0], FIRST);
                assertFalse(AreTheyHigh.onChat(far[0], SECOND), "too far");

                // Too slow: more than 20 s between the halves.
                ServerPlayer e = fight(server, "high_slow")[0];
                AreTheyHigh.onChat(e, FIRST);
                TestServer.advanceGameTime(server, (int) AreTheyHigh.WINDOW_TICKS + 1);
                AreTheyHigh.hit(e, fight(server, "high_slow2")[1]); // (a fresh hit, so only the timing fails)
                assertFalse(AreTheyHigh.onChat(e, SECOND), "too slow");

                // Someone else's messages never count for them.
                ServerPlayer[] pair = fight(server, "high_other");
                KegareAdvancements.award(pair[1], KegareAdvancements.ITS_BEEN_A_WHILE);
                AreTheyHigh.onChat(pair[1], FIRST);
                assertFalse(AreTheyHigh.onChat(pair[0], SECOND), "half was someone else's");

                // Without It's been a while.
                ServerPlayer[] fresh = fight(server, "high_fresh");
                server.getPlayerList().getPlayerAdvancements(fresh[0]).revoke(server.getAdvancements().get(KegareAdvancements.ITS_BEEN_A_WHILE), "granted");
                AreTheyHigh.onChat(fresh[0], FIRST);
                assertFalse(AreTheyHigh.onChat(fresh[0], SECOND), "it requires It's been a while");

                for (ServerPlayer x : new ServerPlayer[] {a, b, c, d, far[0], e, pair[0], fresh[0]}) assertFalse(has(server, x), x.getScoreboardName());
            } finally {
                AreTheyHigh.clear();
                TestServer.removePlayers(server);
            }
        });
    }

    @Test
    void typosAreSmall(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            assertTrue(AreTheyHigh.close("postive", "positive"), "one off");
            assertTrue(AreTheyHigh.close("negitve", "negative"), "two off on a long word");
            assertTrue(AreTheyHigh.close("enrgy", "energy"), "one off on a short word");
            assertFalse(AreTheyHigh.close("enemy", "energy"), "two off on a short word: no");
            assertFalse(AreTheyHigh.close("rmt", "malice"), "not close");
        });
    }
}

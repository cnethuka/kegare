package com.cnethuka.kegare.progression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cnethuka.kegare.TestServer;
import com.cnethuka.kegare.innate_technique.rime.Rime;
import com.cnethuka.kegare.innatetechnique.InnateTechniqueService;
import com.cnethuka.kegare.network.ProgressionSnapshot;
import com.cnethuka.kegare.network.SyncPayload;
import com.cnethuka.kegare.player.BirthService;
import io.netty.buffer.Unpooled;
import java.util.OptionalInt;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/** What the owning client is told, and that it survives the trip. */
@ExtendWith(EphemeralTestServerProvider.class)
class ProgressionSnapshotTest {
    @AfterEach
    void cleanUp(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            ProgressionConfig.REQUIRE_REFINEMENT.set(false);
            TestServer.removePlayers(server);
        });
    }

    private static ServerPlayer sorcerer(MinecraftServer server, String name) {
        TestServer.ensureOverworld(server);
        ProgressionConfig.REQUIRE_REFINEMENT.set(true);
        ServerPlayer p = TestServer.damageable(server, name);
        server.overworld().addNewPlayer(p);
        BirthService.rebirth(p, OptionalInt.of(5000));
        InnateTechniqueService.setInnateTechnique(p, Rime.RIME.get());
        return p;
    }

    private static SyncPayload roundTrip(SyncPayload payload) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        SyncPayload.STREAM_CODEC.encode(buf, payload);
        return SyncPayload.STREAM_CODEC.decode(buf);
    }

    @Test
    void aFreshPlayerSeesTheirLocksAndNoAssessmentYet(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            ServerPlayer p = sorcerer(server, "snapshot_fresh");
            ProgressionSnapshot s = roundTrip(SyncPayload.of(p)).progression();
            assertEquals(Grade.GRADE_4.ordinal(), s.grade());
            assertEquals(200f, s.gradeXpNeeded());
            assertTrue(s.assessment().isEmpty(), "Grade 3 is XP only");
            assertEquals(0, s.refinement());
            assertEquals(30f, s.refinementXpNeeded());
            assertEquals(1, s.lockedLevel(Rime.SPLINTER.id()));
            assertEquals(7, s.lockedLevel(Rime.SHATTER_SHELL.id()));
            assertEquals(-1, s.lockedLevel(Rime.FROST_SHARD.id()));
        });
    }

    @Test
    void theAssessmentGoesAlongWhereOneApplies(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            ServerPlayer p = sorcerer(server, "snapshot_assessment");
            GradeService.set(p, Grade.SEMI_GRADE_1);
            RefinementService.setLevel(p, 3);
            Insight.add(p, InsightCategory.WARD, 7);
            AssessmentService.addFight(p, 60, 45, java.util.UUID.nameUUIDFromBytes(new byte[] {1}));
            ProgressionSnapshot s = roundTrip(SyncPayload.of(p)).progression();
            ProgressionSnapshot.Assessment a = s.assessment().orElseThrow();
            assertEquals(70f, a.averageNeeded());
            assertEquals(60f, a.average());
            assertEquals(45f, a.technique());
            assertEquals(60f, a.techniqueNeeded());
            assertEquals(1, a.fights());
            assertEquals(10, a.fightsNeeded());
            assertEquals(1, a.opponents());
            assertEquals(3, a.opponentsNeeded());
            assertEquals(3, a.peak());
            assertEquals(5, a.peakNeeded());
            assertEquals(3, s.refinement());
            assertEquals(3, s.peakRefinement());
            assertEquals(7, s.ward());
            assertEquals(-1, s.lockedLevel(Rime.GLACIAL_SPIRE.id()), "level 3 unlocked it");
        });
    }

    @Test
    void theTopOfTheLadderAsksForNothing(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            ServerPlayer p = sorcerer(server, "snapshot_top");
            GradeService.set(p, Grade.SPECIAL_GRADE);
            RefinementService.setLevel(p, 10);
            ProgressionSnapshot s = roundTrip(SyncPayload.of(p)).progression();
            assertEquals(0f, s.gradeXpNeeded());
            assertTrue(s.assessment().isEmpty());
            assertEquals(0f, s.refinementXpNeeded());
            assertTrue(s.locked().isEmpty());
        });
    }

    @Test
    void withTheRequirementOffNothingIsLocked(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            ServerPlayer p = sorcerer(server, "snapshot_casual");
            ProgressionConfig.REQUIRE_REFINEMENT.set(false);
            assertTrue(SyncPayload.of(p).progression().locked().isEmpty());
        });
    }
}

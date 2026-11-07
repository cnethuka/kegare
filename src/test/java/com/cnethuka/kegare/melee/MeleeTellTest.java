package com.cnethuka.kegare.melee;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cnethuka.kegare.TestServer;
import com.cnethuka.kegare.innate_technique.rime.Rime;
import com.cnethuka.kegare.innate_technique.swap.Disguises;
import com.cnethuka.kegare.innate_technique.swap.Swap;
import com.cnethuka.kegare.innatetechnique.InnateTechnique;
import com.cnethuka.kegare.innatetechnique.InnateTechniqueService;
import com.cnethuka.kegare.innatetechnique.visual.Visual;
import com.cnethuka.kegare.malice.MaliceService;
import com.cnethuka.kegare.melee.MeleeTell.Kind;
import com.cnethuka.kegare.melee.MeleeTell.Look;
import com.cnethuka.kegare.player.BirthService;
import io.netty.buffer.Unpooled;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.OptionalInt;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/** The ready fist: what color it is, and who gets told what. */
@ExtendWith(EphemeralTestServerProvider.class)
class MeleeTellTest {
    private static final Path RESOURCES = Path.of(System.getProperty("kegare.projectDir", "."), "src/main/resources/assets/kegare");
    private static final Path FX = RESOURCES.resolve("fx/melee");
    private static final Path MODELS = RESOURCES.resolve("models/fx");

    private static ServerPlayer player(MinecraftServer server, String name, InnateTechnique technique) {
        TestServer.ensureOverworld(server);
        ServerPlayer p = TestServer.damageable(server, name);
        BirthService.rebirth(p, OptionalInt.of(technique == null ? 0 : 1000));
        if (technique != null) {
            InnateTechniqueService.setInnateTechnique(p, technique);
            MaliceService.refill(p);
        }
        return p;
    }

    @Test
    void theColorIsTheInnatesOwn(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            ServerPlayer rime = player(server, "RimeOne", Rime.RIME.get());
            ServerPlayer swap = player(server, "SwapOne", Swap.SWAP.get());
            assertEquals(Look.glow(Rime.RIME.get().maliceColor()), MeleeTell.of(rime));
            assertEquals(Look.glow(Swap.SWAP.get().maliceColor()), MeleeTell.of(swap));
            assertNotEquals(MeleeTell.of(rime), MeleeTell.of(swap));
        });
    }

    @Test
    void severedHaveAPhysicalTellNotAGlow(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            ServerPlayer severed = player(server, "SeveredOne", null);
            assertTrue(MaliceService.isSevered(severed));
            assertEquals(Kind.PHYSICAL, MeleeTell.of(severed).kind());
            assertEquals(Kind.PHYSICAL, MeleeTell.seenByOthers(severed).kind());
        });
    }

    /** A face has to be found on the server for its color to be read, so it goes in the world. */
    private static ServerPlayer face(MinecraftServer server, String name, InnateTechnique technique) {
        ServerPlayer p = player(server, name, technique);
        server.overworld().addNewPlayer(p);
        return p;
    }

    private static void leave(ServerPlayer p) {
        p.serverLevel().removePlayerImmediately(p, Entity.RemovalReason.DISCARDED);
    }

    @Test
    void aFaceThatCantBeFoundShowsTheNeutralColorNotTheRealOne(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            ServerPlayer wearer = player(server, "WearerThree", Swap.SWAP.get());
            ServerPlayer gone = player(server, "FaceThree", Rime.RIME.get());
            Disguises.apply(wearer, gone, 200, Disguises.Kind.MIMIC, wearer);
            assertEquals(Look.glow(InnateTechnique.DEFAULT_MALICE_COLOR), MeleeTell.seenByOthers(wearer));
            Disguises.endInvolving(wearer);
        });
    }

    @Test
    void aDisguiseShowsTheFacesColorToEveryoneElse(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            ServerPlayer wearer = player(server, "WearerOne", Swap.SWAP.get());
            ServerPlayer face = face(server, "FaceOne", Rime.RIME.get());
            assertEquals(MeleeTell.of(wearer), MeleeTell.seenByOthers(wearer), "undisguised, everyone sees the real color");
            Disguises.apply(wearer, face, 200, Disguises.Kind.MIMIC, wearer);
            assertEquals(Look.glow(Rime.RIME.get().maliceColor()), MeleeTell.seenByOthers(wearer), "others see the face's color");
            assertEquals(Look.glow(Swap.SWAP.get().maliceColor()), MeleeTell.of(wearer), "the wearer sees their own");
            Disguises.endInvolving(wearer);
            assertEquals(MeleeTell.of(wearer), MeleeTell.seenByOthers(wearer), "and it ends with the disguise");
            leave(face);
        });
    }

    @Test
    void wearingASeveredFaceShowsThePhysicalTell(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            ServerPlayer wearer = player(server, "WearerTwo", Swap.SWAP.get());
            ServerPlayer face = face(server, "FaceTwo", null);
            Disguises.apply(wearer, face, 200, Disguises.Kind.MIMIC, wearer);
            assertEquals(Kind.PHYSICAL, MeleeTell.seenByOthers(wearer).kind());
            assertEquals(Kind.GLOW, MeleeTell.of(wearer).kind());
            Disguises.endInvolving(wearer);
            leave(face);
        });
    }

    @Test
    void theTellFollowsTheStrike(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            ServerPlayer p = player(server, "TellOne", Rime.RIME.get());
            MeleeState state = MeleeLayer.state(p);
            MeleeTell.sync(p, state, true);
            assertEquals(MeleeTell.of(p), state.tellOwn);
            assertEquals(MeleeTell.seenByOthers(p), state.tellOthers);
            MeleeTell.sync(p, state, false);
            assertEquals(Look.NONE, state.tellOwn);
            assertEquals(Look.NONE, state.tellOthers);
        });
    }

    @Test
    void brightKeepsTheHueAndReachesFullBrightness() {
        int rime = MeleeTell.bright(0x1E3A8A);
        assertEquals(0xFF, rime >>> 24, "opaque");
        assertEquals(0xFF, rime & 0xFF, "the strongest channel is full");
        assertTrue((rime >> 16 & 0xFF) < (rime >> 8 & 0xFF) && (rime >> 8 & 0xFF) < (rime & 0xFF), "still blue: red < green < blue");
        assertEquals(0xFFFFFFFF, MeleeTell.bright(0), "black has no hue to keep");
    }

    @Test
    void thePayloadCarriesTheLook() {
        for (Look look : List.of(Look.NONE, Look.PHYSICAL, Look.glow(0x14B8A6))) {
            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
            MeleeTellPayload.STREAM_CODEC.encode(buf, new MeleeTellPayload(42, look));
            assertEquals(new MeleeTellPayload(42, look), MeleeTellPayload.STREAM_CODEC.decode(buf));
        }
    }

    @Test
    void aTintStaysOnTheVisual() {
        Visual plain = Visual.effect(MeleeIds.FX_STRIKE_IMPACT, Visual.Anchor.BODY);
        assertEquals(0, plain.tint(), "0 means as authored");
        assertEquals(0xFF14B8A6, plain.tinted(0xFF14B8A6).tint());
        assertEquals(plain.effect(), plain.tinted(0xFF14B8A6).effect());
    }

    @Test
    void everyNewEffectIsThere() {
        for (String name : List.of("fist_steam", "fist_steam_self", "strike_impact", "strike_burst", "strike_impact_physical")) {
            assertTrue(Files.isRegularFile(FX.resolve(name + ".fx")), name);
        }
        assertTrue(!Files.exists(FX.resolve("strike_ready.fx")), "the gold motes are gone");
        assertTrue(!Files.exists(FX.resolve("fist_wisps.fx")), "the fist's curls are drawn with its flames now");
    }

    @Test
    void slimAndRegularShellsDifferOnlyInWidth() throws IOException {
        List<double[]> regular = vertices(MODELS.resolve("fist_shell_regular.obj"));
        List<double[]> slim = vertices(MODELS.resolve("fist_shell_slim.obj"));
        assertEquals(regular.size(), slim.size());
        assertTrue(width(regular) > width(slim), "a slim arm is narrower");
        for (int i = 0; i < regular.size(); i++) assertEquals(regular.get(i)[1], slim.get(i)[1], 1.0e-6, "same height");
    }

    private static double width(List<double[]> vertices) {
        return vertices.stream().mapToDouble(v -> v[0]).max().orElseThrow() - vertices.stream().mapToDouble(v -> v[0]).min().orElseThrow();
    }

    private static List<double[]> vertices(Path obj) throws IOException {
        return Files.readAllLines(obj).stream().filter(l -> l.startsWith("v ")).map(l -> {
            String[] p = l.trim().split("\\s+");
            return new double[] {Double.parseDouble(p[1]), Double.parseDouble(p[2]), Double.parseDouble(p[3])};
        }).toList();
    }
}

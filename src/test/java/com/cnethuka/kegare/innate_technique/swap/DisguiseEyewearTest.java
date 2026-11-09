package com.cnethuka.kegare.innate_technique.swap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cnethuka.kegare.TestServer;
import com.cnethuka.kegare.player.BirthService;
import com.cnethuka.kegare.registry.KegareItems;
import com.cnethuka.kegare.sixeyes.Eyewear;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import top.theillusivec4.curios.api.CuriosApi;

/** Eyewear never gives a disguise away (DESIGN §3.19, §3.32): a disguise wears the face's eyewear, never the wearer's own. */
@ExtendWith(EphemeralTestServerProvider.class)
class DisguiseEyewearTest {
    private static ServerPlayer player(MinecraftServer server, String name) {
        TestServer.ensureOverworld(server);
        ServerPlayer p = TestServer.damageable(server, name);
        server.overworld().addNewPlayer(p);
        BirthService.rebirth(p, OptionalInt.of(1000));
        return p;
    }

    private static void wear(ServerPlayer p, ItemStack stack) {
        CuriosApi.getCuriosInventory(p).orElseThrow().setEquippedCurio(Eyewear.SLOT, 0, stack);
    }

    private static Eyewear sentFor(Disguises.StatePayload state, ServerPlayer wearer) {
        return state.entries().stream().filter(e -> e.wearer().equals(wearer.getUUID())).findFirst().orElseThrow().faceEyewear();
    }

    @Test
    void whatEachClientDraws() {
        UUID wearer = UUID.randomUUID(), face = UUID.randomUUID();
        Disguises.Entry withGlasses = new Disguises.Entry(1, wearer, face, 100, Eyewear.SUNGLASSES);
        Disguises.Entry bare = new Disguises.Entry(1, wearer, face, 100, Eyewear.NONE);
        assertEquals(Eyewear.SUNGLASSES, Disguises.eyewearShown(withGlasses, 50, false), "everyone else: the face's");
        assertEquals(Eyewear.NONE, Disguises.eyewearShown(bare, 50, false), "the face wears none: none, the wearer's own hidden too");
        assertNull(Disguises.eyewearShown(withGlasses, 50, true), "the wearer sees their own");
        assertNull(Disguises.eyewearShown(withGlasses, 100, false), "it ends with the disguise: their own again");
        assertNull(Disguises.eyewearShown(null, 50, false), "no disguise: their own");
    }

    @Test
    void theFacesEyewearIsSentAndFollowsTheFace(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            ServerPlayer wearer = player(server, "dis_eyes_wearer"), face = player(server, "dis_eyes_face");
            List<Disguises.StatePayload> sent = new ArrayList<>();
            try {
                Disguises.sentHook = sent::add;
                wear(wearer, new ItemStack(KegareItems.BLINDFOLD.get()));
                Disguises.apply(wearer, face, 400, Disguises.Kind.MIMIC, wearer);
                assertEquals(Eyewear.NONE, sentFor(sent.get(sent.size() - 1), wearer), "the face wears none: none, whatever the wearer has on");

                sent.clear();
                Disguises.tick(server);
                assertTrue(sent.isEmpty(), "nothing changed: nothing sent");
                wear(face, new ItemStack(KegareItems.SUNGLASSES.get()));
                Disguises.tick(server);
                assertEquals(1, sent.size(), "the face put some on: sent at once");
                assertEquals(Eyewear.SUNGLASSES, sentFor(sent.get(0), wearer), "the eyewear the face wears right now");
                wear(face, ItemStack.EMPTY);
                Disguises.tick(server);
                assertEquals(Eyewear.NONE, sentFor(sent.get(sent.size() - 1), wearer), "and took it off");

                // Through the wire.
                wear(face, new ItemStack(KegareItems.BLINDFOLD.get()));
                Disguises.StatePayload state = Disguises.state(server);
                FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
                Disguises.StatePayload.STREAM_CODEC.encode(buf, state);
                assertEquals(state, Disguises.StatePayload.STREAM_CODEC.decode(buf));
                assertEquals(Eyewear.BLINDFOLD, sentFor(state, wearer));

                // Awareness swaps both ways: each wears the other's.
                Disguises.endInvolving(wearer);
                wear(face, new ItemStack(KegareItems.SUNGLASSES.get()));
                Disguises.apply(wearer, face, 400, Disguises.Kind.AWARENESS, wearer);
                Disguises.apply(face, wearer, 400, Disguises.Kind.AWARENESS, wearer);
                state = Disguises.state(server);
                assertEquals(Eyewear.SUNGLASSES, sentFor(state, wearer), "the caster wears the target's sunglasses");
                assertEquals(Eyewear.BLINDFOLD, sentFor(state, face), "and the target the caster's blindfold");
            } finally {
                Disguises.sentHook = null;
                Disguises.endInvolving(wearer);
                wear(wearer, ItemStack.EMPTY);
                wear(face, ItemStack.EMPTY);
                TestServer.removePlayers(server);
            }
        });
    }
}

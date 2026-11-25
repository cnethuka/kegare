package com.cnethuka.kegare.dominion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cnethuka.kegare.TestServer;
import com.cnethuka.kegare.innatetechnique.CastIntent;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * A Dominion's inner space (DESIGN §3.35): built of real blocks in a dimension of its own, everyone inside taken in as it seals and let
 * out as it ends, the world under its shell never touched, and a player stranded inside sent back when they log in.
 */
@ExtendWith(EphemeralTestServerProvider.class)
class InnerSpaceTest {
    private static final double EPS = 1.0e-6;

    static DominionEntity open(ServerPlayer caster) {
        DominionMeter.set(caster, 100);
        DominionEntity d = Dominions.open(caster, IncompleteDominions.spec(caster), CastIntent.CAST_RELEASE);
        assertNotNull(d, "setup: it opens");
        return d;
    }

    /** Ends it and lets everyone out now (the delay is the clients' to see it go). */
    static void endNow(DominionEntity d) {
        Dominions.end(d, Dominions.EndReason.COMMAND);
        for (InnerSpace s : InnerSpaces.open()) if (s.collapsing) InnerSpaces.letOut(s);
    }

    private static void run(MinecraftServer server, Consumer<MinecraftServer> test) throws Exception {
        TestServer.onServerThread(server, () -> {
            try {
                test.accept(server);
            } finally {
                DominionTest.cleanUp(server);
            }
        });
    }

    @Test
    void itsDimensionIsThere(MinecraftServer server) throws Exception {
        run(server, s -> {
            DominionTest.level(s);
            assertNotNull(InnerSpaces.level(s), "the inner space dimension is registered");
        });
    }

    @Test
    void sealingTakesEveryoneInsideIntoItsInnerSpaceBuiltOfRealBlocks(MinecraftServer server) throws Exception {
        run(server, s -> {
            ServerLevel world = DominionTest.level(s);
            ServerPlayer a = DominionTest.at(s, "inner_a", 8, 8);
            ServerPlayer b = DominionTest.at(s, "inner_b", 12, 8);
            ServerPlayer out = DominionTest.at(s, "inner_out", 30, 8);
            DominionEntity d = open(a);
            assertNotNull(d.space, "a plot taken as it opens");
            DominionTest.closed(d);
            InnerSpace space = d.space;
            ServerLevel inner = InnerSpaces.level(s);
            assertTrue(a.level() == inner && b.level() == inner, "both taken in");
            assertTrue(out.level() == world, "outside stays outside");
            Vec3 m = space.middle();
            assertEquals(m.x, a.getX(), 1.0e-3, "the caster at its middle");
            assertEquals(m.x + 4 * space.spread(), b.getX(), 1.0e-3, "the other where they stood, spread out by how much bigger it is");
            assertEquals(m.y, b.getY(), 1.0e-3, "on its floor");
            assertTrue(space.spread() > 1, "bigger inside than out");
            assertEquals(DominionRegistry.WASTELAND_GROUND.get(), inner.getBlockState(space.centre.below()).getBlock(), "its ground, real");
            assertEquals(DominionRegistry.DOMINION_FOUNDATION.get(), inner.getBlockState(space.centre.below(2)).getBlock(), "on its foundation");
            assertTrue(a.getData(DominionRegistry.INNER_RETURN).present(), "where they came from, kept");
            assertTrue(Dominions.trapping(b).isPresent());
            assertNotNull(space.inner, "its sky stands in it");

            Vec3 bAt = b.getData(DominionRegistry.INNER_RETURN).pos();
            endNow(d);
            assertTrue(a.level() == world && b.level() == world, "let out");
            assertEquals(bAt.x, b.getX(), EPS, "to where they were");
            assertFalse(b.getData(DominionRegistry.INNER_RETURN).present(), "nothing left to remember");
            assertTrue(InnerSpaces.open().isEmpty(), "the plot let go");
        });
    }

    @Test
    void mobsAndWhatWasDroppedComeBackToo(MinecraftServer server) throws Exception {
        run(server, s -> {
            ServerLevel world = DominionTest.level(s);
            ServerPlayer a = DominionTest.at(s, "inner_mobs", 8, 8);
            Pig pig = new Pig(EntityType.PIG, world);
            pig.moveTo(11, DominionTest.Y, 8, 0, 0);
            pig.setNoGravity(true);
            world.addFreshEntity(pig);
            DominionEntity d = open(a);
            DominionTest.closed(d);
            ServerLevel inner = InnerSpaces.level(s);
            TestServer.entitiesTicking(inner, d.space.centre, 1);
            assertNotNull(inner.getEntity(pig.getUUID()), "the pig taken in");
            ItemEntity dropped = new ItemEntity(inner, d.space.middle().x + 3, d.space.middle().y + 1, d.space.middle().z, new ItemStack(Items.DIAMOND));
            inner.addFreshEntity(dropped);
            endNow(d);
            assertTrue(world.getEntity(pig.getUUID()) instanceof Pig, "the pig let out");
            assertEquals(11, world.getEntity(pig.getUUID()).getX(), EPS, "to where it was");
            assertTrue(world.getEntitiesOfClass(ItemEntity.class, d.getBoundingBoxForCulling().inflate(4),
                    e -> e.getItem().is(Items.DIAMOND)).size() == 1, "and what was dropped inside, near the shell");
        });
    }

    @Test
    void theWorldUnderItIsNeverTouched(MinecraftServer server) throws Exception {
        run(server, s -> {
            ServerLevel world = DominionTest.level(s);
            BlockPos marker = BlockPos.containing(9, DominionTest.Y - 1, 8);
            world.setBlock(marker, Blocks.GOLD_BLOCK.defaultBlockState(), 3);
            ServerPlayer a = DominionTest.at(s, "inner_world", 8, 8);
            DominionEntity d = open(a);
            DominionTest.closed(d);
            assertEquals(Blocks.GOLD_BLOCK, world.getBlockState(marker).getBlock(), "while it's sealed");
            endNow(d);
            assertEquals(Blocks.GOLD_BLOCK, world.getBlockState(marker).getBlock(), "and after");
            world.setBlock(marker, Blocks.AIR.defaultBlockState(), 3);
        });
    }

    @Test
    void aPlotIsClearedBeforeItsUsedAgain(MinecraftServer server) throws Exception {
        run(server, s -> {
            ServerPlayer a = DominionTest.at(s, "inner_reuse", 8, 8);
            DominionEntity d = open(a);
            DominionTest.closed(d);
            ServerLevel inner = InnerSpaces.level(s);
            BlockPos leftover = d.space.centre.above(5).east(3);
            inner.setBlock(leftover, Blocks.STONE.defaultBlockState(), 3); // say a crash left it
            BlockPos centre = d.space.centre;
            endNow(d);
            com.cnethuka.kegare.innatetechnique.Burnout.clear(a);
            DominionEntity again = open(a);
            assertEquals(centre, again.space.centre, "the same plot");
            DominionTest.closed(again);
            assertTrue(inner.getBlockState(leftover).isAir(), "what was left in it is gone");
        });
    }

    @Test
    void nothingIsBuiltInAnInnerSpaceAndNothingOpensThereButAClash(MinecraftServer server) throws Exception {
        run(server, s -> {
            ServerPlayer a = DominionTest.at(s, "inner_build", 8, 8);
            ServerPlayer b = DominionTest.at(s, "inner_build_b", 11, 8);
            DominionEntity d = open(a);
            DominionTest.closed(d);
            DominionMeter.set(b, 100);
            assertTrue(Dominions.refusal(b, IncompleteDominions.spec(b)).isEmpty(), "over its sky: a clash");
            d.space.inner.discard(); // as if its sky were gone
            var refused = Dominions.refusal(b, IncompleteDominions.spec(b));
            assertTrue(refused.isPresent() && refused.get().getContents().toString().contains("message.kegare.dominion.inner_space"), "nowhere else: " + refused);
        });
    }

    @Test
    void openingOverASealedOneFromOutsideIsRefused(MinecraftServer server) throws Exception {
        run(server, s -> {
            ServerPlayer a = DominionTest.at(s, "inner_sealed", 8, 8);
            ServerPlayer c = DominionTest.at(s, "inner_sealed_c", 26, 8);
            DominionEntity d = open(a);
            DominionTest.closed(d);
            DominionMeter.set(c, 100);
            var refused = Dominions.refusal(c, IncompleteDominions.spec(c));
            assertTrue(refused.isPresent() && refused.get().getContents().toString().contains("message.kegare.dominion.sealed"), "sealed: " + refused);
            assertEquals(100, DominionMeter.get(c), EPS, "nothing paid");
        });
    }

    @Test
    void aPlayerLeftInsideIsSentBackWhenTheyLogIn(MinecraftServer server) throws Exception {
        run(server, s -> {
            ServerLevel world = DominionTest.level(s);
            ServerPlayer a = DominionTest.at(s, "inner_crash", 8, 8);
            ServerPlayer b = DominionTest.at(s, "inner_crash_b", 12, 8);
            DominionEntity d = open(a);
            DominionTest.closed(d);
            Vec3 came = b.getData(DominionRegistry.INNER_RETURN).pos();
            Dominions.forgetAllAsIfCrashed(); // nothing ends: b is still in the inner space
            assertTrue(InnerSpaces.isInnerSpace(b.level()), "setup: left inside");
            InnerSpaces.onLogin(b);
            assertTrue(b.level() == world, "back in the world");
            assertEquals(came.x, b.getX(), EPS, "where they came from");
            assertFalse(b.getData(DominionRegistry.INNER_RETURN).present());
        });
    }

    @Test
    void someonePutInAnInnerSpaceNoDominionHoldsIsSentBack(MinecraftServer server) throws Exception {
        run(server, s -> {
            ServerLevel world = DominionTest.level(s);
            ServerPlayer stray = DominionTest.at(s, "inner_stray", 8, 8);
            ServerPlayer held = DominionTest.at(s, "inner_held", 30, 8);
            DominionEntity d = open(held);
            DominionTest.closed(d);
            InnerSpaces.move(stray, InnerSpaces.level(s), new Vec3(5000, 64, 5000), 0f, 0f); // a command put them there
            InnerSpaces.sendStraysBack();
            assertTrue(stray.level() == world, "nothing holds them: sent back");
            assertTrue(InnerSpaces.isInnerSpace(held.level()), "one its Dominion holds stays");
        });
    }

    @Test
    void takingItsCasterInDoesntEndIt(MinecraftServer server) throws Exception {
        run(server, s -> {
            ServerPlayer a = DominionTest.at(s, "inner_caster", 8, 8);
            DominionEntity d = open(a);
            DominionTest.closed(d);
            DominionTest.step(d, 5);
            assertFalse(d.isRemoved(), "the caster changed dimension, as Kegare: it stands");
            assertTrue(Dominions.ownedBy(a).isPresent());
        });
    }
}

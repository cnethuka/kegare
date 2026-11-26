package com.cnethuka.kegare.domain;

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
 * A Domain's inner space (DESIGN §3.35): built of real blocks in a dimension of its own, everyone inside taken in as it seals and let
 * out as it ends, the world under its shell never touched, and a player stranded inside sent back when they log in.
 */
@ExtendWith(EphemeralTestServerProvider.class)
class InnerSpaceTest {
    private static final double EPS = 1.0e-6;

    static DomainEntity open(ServerPlayer caster) {
        DomainMeter.set(caster, 100);
        DomainEntity d = Domains.open(caster, IncompleteDomains.spec(caster), CastIntent.CAST_RELEASE);
        assertNotNull(d, "setup: it opens");
        return d;
    }

    /** Ends it and lets everyone out now (the delay is the clients' to see it go). */
    static void endNow(DomainEntity d) {
        Domains.end(d, Domains.EndReason.COMMAND);
        for (InnerSpace s : InnerSpaces.open()) if (s.collapsing) InnerSpaces.letOut(s);
    }

    private static void run(MinecraftServer server, Consumer<MinecraftServer> test) throws Exception {
        TestServer.onServerThread(server, () -> {
            try {
                test.accept(server);
            } finally {
                DomainTest.cleanUp(server);
            }
        });
    }

    @Test
    void itsDimensionIsThere(MinecraftServer server) throws Exception {
        run(server, s -> {
            DomainTest.level(s);
            assertNotNull(InnerSpaces.level(s), "the inner space dimension is registered");
        });
    }

    @Test
    void sealingTakesEveryoneInsideIntoItsInnerSpaceBuiltOfRealBlocks(MinecraftServer server) throws Exception {
        run(server, s -> {
            ServerLevel world = DomainTest.level(s);
            ServerPlayer a = DomainTest.at(s, "inner_a", 8, 8);
            ServerPlayer b = DomainTest.at(s, "inner_b", 12, 8);
            ServerPlayer out = DomainTest.at(s, "inner_out", 30, 8);
            DomainEntity d = open(a);
            assertNotNull(d.space, "a plot taken as it opens");
            DomainTest.closed(d);
            InnerSpace space = d.space;
            ServerLevel inner = InnerSpaces.level(s);
            assertTrue(a.level() == inner && b.level() == inner, "both taken in");
            assertTrue(out.level() == world, "outside stays outside");
            Vec3 m = space.middle();
            assertEquals(m.x, a.getX(), 1.0e-3, "the caster at its middle");
            assertEquals(m.x + 4 * space.spread(), b.getX(), 1.0e-3, "the other where they stood, spread out by how much bigger it is");
            assertEquals(m.y, b.getY(), 1.0e-3, "on its floor");
            assertTrue(space.spread() > 1, "bigger inside than out");
            assertFalse(inner.getBlockState(space.centre.below()).isAir(), "its ground, real");
            assertEquals(Blocks.BEDROCK, inner.getBlockState(space.centre.below(3)).getBlock(), "on bedrock");
            assertTrue(a.getData(DomainRegistry.INNER_RETURN).present(), "where they came from, kept");
            assertTrue(Domains.trapping(b).isPresent());
            assertNotNull(space.inner, "its sky stands in it");

            Vec3 bAt = b.getData(DomainRegistry.INNER_RETURN).pos();
            endNow(d);
            assertTrue(a.level() == world && b.level() == world, "let out");
            assertEquals(bAt.x, b.getX(), EPS, "to where they were");
            assertFalse(b.getData(DomainRegistry.INNER_RETURN).present(), "nothing left to remember");
            assertTrue(InnerSpaces.open().isEmpty(), "the plot let go");
        });
    }

    @Test
    void theWastelandIsVanillaBlocksOnBedrockAndNothingInItDrops(MinecraftServer server) throws Exception {
        run(server, s -> {
            ServerPlayer a = DomainTest.at(s, "inner_vanilla", 8, 8);
            DomainEntity d = open(a);
            DomainTest.closed(d);
            InnerSpace space = d.space;
            ServerLevel inner = InnerSpaces.level(s);
            BlockPos c = space.centre;
            int reach = space.radius + InnerSpace.FLOOR_MARGIN, standing = 0;
            java.util.Set<net.minecraft.world.level.block.Block> ground = new java.util.HashSet<>();
            BlockPos ruin = null;
            for (int dx = -reach; dx <= reach; dx++) {
                for (int dz = -reach; dz <= reach; dz++) {
                    if (dx * dx + dz * dz > reach * reach) continue;
                    for (int y = c.getY() - 3; y <= c.getY() + space.radius; y++) {
                        BlockPos pos = new BlockPos(c.getX() + dx, y, c.getZ() + dz);
                        var state = inner.getBlockState(pos);
                        if (state.isAir()) continue;
                        assertEquals("minecraft", net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock()).getNamespace(),
                                "vanilla blocks only: " + state + " at " + pos);
                        assertFalse(state.getBlock() instanceof net.minecraft.world.level.block.FallingBlock, "nothing that falls: " + state);
                        if (y == c.getY() - 3) assertEquals(Blocks.BEDROCK, state.getBlock(), "bedrock under it all");
                        if (y == c.getY() - 1) ground.add(state.getBlock());
                        if (y >= c.getY()) {
                            standing++;
                            double h = y + 1 - c.getY();
                            assertTrue(Math.sqrt(dx * dx + dz * dz + h * h) <= space.radius - 1, "nothing stands right under its sky: " + pos);
                            if (state.is(Blocks.STONE_BRICKS) || state.is(Blocks.CRACKED_STONE_BRICKS)) ruin = pos;
                        }
                    }
                    assertEquals(Blocks.BEDROCK, inner.getBlockState(new BlockPos(c.getX() + dx, c.getY() - 3, c.getZ() + dz)).getBlock(), "bedrock under it all");
                }
            }
            assertTrue(ground.size() >= 4, "its ground a patchwork: " + ground);
            assertTrue(standing > 40, "ruins and rubble stand on it: " + standing);
            assertNotNull(ruin, "ruins of stone bricks");

            // Broken by hand, with the right tool: nothing drops.
            a.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
            a.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_PICKAXE));
            assertTrue(a.gameMode.destroyBlock(ruin), "setup: broken");
            assertTrue(inner.getBlockState(ruin).isAir());
            BlockPos under = c.below().east(2);
            assertTrue(a.gameMode.destroyBlock(under), "setup: the ground broken");
            // Nor by a blast, though it still breaks what it reaches.
            BlockPos blast = c.east(6);
            for (int up = 0; up < 8; up++) inner.setBlock(blast.above(up), Blocks.AIR.defaultBlockState(), 3); // no ruin to shield it
            inner.setBlock(blast.below(), Blocks.COBBLESTONE.defaultBlockState(), 3);
            inner.explode(null, blast.getX() + 0.5, blast.getY(), blast.getZ() + 0.5, 3f, net.minecraft.world.level.Level.ExplosionInteraction.TNT);
            assertTrue(inner.getBlockState(blast.below()).isAir(), "the blast broke the ground");
            assertEquals(Blocks.BEDROCK, inner.getBlockState(blast.below(3)).getBlock(), "but never the bedrock");
            assertTrue(inner.getEntitiesOfClass(ItemEntity.class, new net.minecraft.world.phys.AABB(c).inflate(reach)).isEmpty(), "nothing dropped");
        });
    }

    @Test
    void mobsAndWhatWasDroppedComeBackToo(MinecraftServer server) throws Exception {
        run(server, s -> {
            ServerLevel world = DomainTest.level(s);
            ServerPlayer a = DomainTest.at(s, "inner_mobs", 8, 8);
            Pig pig = new Pig(EntityType.PIG, world);
            pig.moveTo(11, DomainTest.Y, 8, 0, 0);
            pig.setNoGravity(true);
            world.addFreshEntity(pig);
            DomainEntity d = open(a);
            DomainTest.closed(d);
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
            ServerLevel world = DomainTest.level(s);
            BlockPos marker = BlockPos.containing(9, DomainTest.Y - 1, 8);
            world.setBlock(marker, Blocks.GOLD_BLOCK.defaultBlockState(), 3);
            ServerPlayer a = DomainTest.at(s, "inner_world", 8, 8);
            DomainEntity d = open(a);
            DomainTest.closed(d);
            assertEquals(Blocks.GOLD_BLOCK, world.getBlockState(marker).getBlock(), "while it's sealed");
            endNow(d);
            assertEquals(Blocks.GOLD_BLOCK, world.getBlockState(marker).getBlock(), "and after");
            world.setBlock(marker, Blocks.AIR.defaultBlockState(), 3);
        });
    }

    @Test
    void aPlotIsClearedBeforeItsUsedAgain(MinecraftServer server) throws Exception {
        run(server, s -> {
            ServerPlayer a = DomainTest.at(s, "inner_reuse", 8, 8);
            DomainEntity d = open(a);
            DomainTest.closed(d);
            ServerLevel inner = InnerSpaces.level(s);
            BlockPos leftover = d.space.centre.above(5).east(3);
            inner.setBlock(leftover, Blocks.STONE.defaultBlockState(), 3); // say a crash left it
            BlockPos centre = d.space.centre;
            endNow(d);
            com.cnethuka.kegare.innatetechnique.Burnout.clear(a);
            DomainEntity again = open(a);
            assertEquals(centre, again.space.centre, "the same plot");
            DomainTest.closed(again);
            assertTrue(inner.getBlockState(leftover).isAir(), "what was left in it is gone");
        });
    }

    @Test
    void nothingIsBuiltInAnInnerSpaceAndNothingOpensThereButAClash(MinecraftServer server) throws Exception {
        run(server, s -> {
            ServerPlayer a = DomainTest.at(s, "inner_build", 8, 8);
            ServerPlayer b = DomainTest.at(s, "inner_build_b", 11, 8);
            DomainEntity d = open(a);
            DomainTest.closed(d);
            DomainMeter.set(b, 100);
            assertTrue(Domains.refusal(b, IncompleteDomains.spec(b)).isEmpty(), "over its sky: a clash");
            d.space.inner.discard(); // as if its sky were gone
            var refused = Domains.refusal(b, IncompleteDomains.spec(b));
            assertTrue(refused.isPresent() && refused.get().getContents().toString().contains("message.kegare.domain.inner_space"), "nowhere else: " + refused);
        });
    }

    @Test
    void openingOverASealedOneFromOutsideIsRefused(MinecraftServer server) throws Exception {
        run(server, s -> {
            ServerPlayer a = DomainTest.at(s, "inner_sealed", 8, 8);
            ServerPlayer c = DomainTest.at(s, "inner_sealed_c", 26, 8);
            DomainEntity d = open(a);
            DomainTest.closed(d);
            DomainMeter.set(c, 100);
            var refused = Domains.refusal(c, IncompleteDomains.spec(c));
            assertTrue(refused.isPresent() && refused.get().getContents().toString().contains("message.kegare.domain.sealed"), "sealed: " + refused);
            assertEquals(100, DomainMeter.get(c), EPS, "nothing paid");
        });
    }

    @Test
    void aPlayerLeftInsideIsSentBackWhenTheyLogIn(MinecraftServer server) throws Exception {
        run(server, s -> {
            ServerLevel world = DomainTest.level(s);
            ServerPlayer a = DomainTest.at(s, "inner_crash", 8, 8);
            ServerPlayer b = DomainTest.at(s, "inner_crash_b", 12, 8);
            DomainEntity d = open(a);
            DomainTest.closed(d);
            Vec3 came = b.getData(DomainRegistry.INNER_RETURN).pos();
            Domains.forgetAllAsIfCrashed(); // nothing ends: b is still in the inner space
            assertTrue(InnerSpaces.isInnerSpace(b.level()), "setup: left inside");
            InnerSpaces.onLogin(b);
            assertTrue(b.level() == world, "back in the world");
            assertEquals(came.x, b.getX(), EPS, "where they came from");
            assertFalse(b.getData(DomainRegistry.INNER_RETURN).present());
        });
    }

    @Test
    void someonePutInAnInnerSpaceNoDomainHoldsIsSentBack(MinecraftServer server) throws Exception {
        run(server, s -> {
            ServerLevel world = DomainTest.level(s);
            ServerPlayer stray = DomainTest.at(s, "inner_stray", 8, 8);
            ServerPlayer held = DomainTest.at(s, "inner_held", 30, 8);
            DomainEntity d = open(held);
            DomainTest.closed(d);
            InnerSpaces.move(stray, InnerSpaces.level(s), new Vec3(5000, 64, 5000), 0f, 0f); // a command put them there
            InnerSpaces.sendStraysBack();
            assertTrue(stray.level() == world, "nothing holds them: sent back");
            assertTrue(InnerSpaces.isInnerSpace(held.level()), "one its Domain holds stays");
        });
    }

    @Test
    void theWorldItStandsInKeepsTickingWithEveryoneInside(MinecraftServer server) throws Exception {
        run(server, s -> {
            ServerLevel world = DomainTest.level(s);
            ServerPlayer a = DomainTest.at(s, "inner_awake", 8, 8);
            DomainEntity d = open(a);
            DomainTest.closed(d);
            assertTrue(InnerSpaces.isInnerSpace(a.level()), "setup: nobody left in the world");
            setEmptyTime(world, 10_000); // long past the 300 ticks after which an empty level stops ticking its entities
            InnerSpaces.keepAwake();
            assertEquals(0, emptyTime(world), "kept awake while it's open");
            endNow(d);
            setEmptyTime(world, 10_000);
            InnerSpaces.keepAwake();
            assertEquals(10_000, emptyTime(world), "and left alone once it's over");
            setEmptyTime(world, 0);
        });
    }

    private static java.lang.reflect.Field emptyTimeField() throws ReflectiveOperationException {
        java.lang.reflect.Field f = ServerLevel.class.getDeclaredField("emptyTime");
        f.setAccessible(true);
        return f;
    }

    private static int emptyTime(ServerLevel level) {
        try {
            return emptyTimeField().getInt(level);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void setEmptyTime(ServerLevel level, int ticks) {
        try {
            emptyTimeField().setInt(level, ticks);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void takingItsCasterInDoesntEndIt(MinecraftServer server) throws Exception {
        run(server, s -> {
            ServerPlayer a = DomainTest.at(s, "inner_caster", 8, 8);
            DomainEntity d = open(a);
            DomainTest.closed(d);
            DomainTest.step(d, 5);
            assertFalse(d.isRemoved(), "the caster changed dimension, as Kegare: it stands");
            assertTrue(Domains.ownedBy(a).isPresent());
        });
    }
}

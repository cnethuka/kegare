package com.cnethuka.kegare.battledamage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cnethuka.kegare.TestServer;
import com.cnethuka.kegare.battledamage.BattleDamageConfig.Mode;
import com.cnethuka.kegare.impact.ImpactKind;
import com.cnethuka.kegare.impact.ImpactTier;
import com.cnethuka.kegare.impact.Impacts;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Battle damage (DESIGN §3.24) on a throwaway server: the block rule, what never breaks, the protected list, spilling, the caps, a
 * cancelled break event and each mode. Everything happens high over chunk (0,0), which stays loaded, and every test puts the config
 * back as it found it.
 */
@ExtendWith(EphemeralTestServerProvider.class)
class BattleDamageTest {
    private static final BlockPos AT = new BlockPos(8, 200, 8);
    /** Cancels the block-break event at this spot, while it's set. */
    private static @Nullable BlockPos refuseAt;
    private static boolean listening;

    private static ServerLevel world(MinecraftServer server) {
        TestServer.ensureOverworld(server);
        ServerLevel level = server.overworld();
        if (!listening) {
            NeoForge.EVENT_BUS.addListener((BlockEvent.BreakEvent e) -> {
                if (e.getPos().equals(refuseAt)) e.setCanceled(true);
            });
            listening = true;
        }
        return level;
    }

    /** Clears the space round {@link #AT} and the items in it. */
    private static void clear(ServerLevel level) {
        for (BlockPos p : BlockPos.betweenClosed(AT.offset(-8, -8, -8), AT.offset(8, 8, 8))) level.setBlock(p, Blocks.AIR.defaultBlockState(), 2 | 16);
        level.getEntitiesOfClass(ItemEntity.class, new AABB(AT).inflate(12)).forEach(ItemEntity::discard);
        BattleDamage.forgetRecent();
    }

    private static List<ItemEntity> items(ServerLevel level) {
        return level.getEntitiesOfClass(ItemEntity.class, new AABB(AT).inflate(12));
    }

    /** Runs {@code body} with the config set for it, and sets it back after. */
    private static void with(Mode mode, int perHit, int perSecond, boolean spill, List<String> protectedList, Runnable body) {
        Mode m = BattleDamageConfig.MODE.get();
        int h = BattleDamageConfig.MAX_PER_HIT.get(), s = BattleDamageConfig.MAX_PER_SECOND.get();
        boolean sp = BattleDamageConfig.SPILL_CONTENTS.get();
        List<? extends String> pr = BattleDamageConfig.PROTECTED.get();
        BattleDamageConfig.MODE.set(mode);
        BattleDamageConfig.MAX_PER_HIT.set(perHit);
        BattleDamageConfig.MAX_PER_SECOND.set(perSecond);
        BattleDamageConfig.SPILL_CONTENTS.set(spill);
        BattleDamageConfig.PROTECTED.set(protectedList);
        try {
            body.run();
        } finally {
            BattleDamageConfig.MODE.set(m);
            BattleDamageConfig.MAX_PER_HIT.set(h);
            BattleDamageConfig.MAX_PER_SECOND.set(s);
            BattleDamageConfig.SPILL_CONTENTS.set(sp);
            BattleDamageConfig.PROTECTED.set(pr);
        }
    }

    private static void permanent(Runnable body) {
        with(Mode.PERMANENT, 4096, 100_000, true, List.of(), body);
    }

    /** A hit of this power over the centre of {@link #AT}, reaching {@code radius}. */
    private static BattleDamage.Result blast(ServerLevel level, double strength) {
        return BattleDamage.hit(level, Vec3.atCenterOf(AT), strength, ImpactTier.HEAVY, null);
    }

    @Test
    void oneRuleBlastResistanceAgainstTheHitsPower(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            ServerLevel level = world(server);
            clear(level);
            BlockState glass = Blocks.GLASS.defaultBlockState(), dirt = Blocks.DIRT.defaultBlockState();
            BlockState stone = Blocks.STONE.defaultBlockState(), obsidian = Blocks.OBSIDIAN.defaultBlockState();
            assertTrue(BattleDamage.breaks(glass, level, AT, 1), "glass goes easily");
            assertTrue(BattleDamage.breaks(dirt, level, AT, 1), "so does dirt");
            assertFalse(BattleDamage.breaks(stone, level, AT, 5), "stone takes a solid hit...");
            assertTrue(BattleDamage.breaks(stone, level, AT, 7), "...and goes to one");
            assertFalse(BattleDamage.breaks(obsidian, level, AT, 1000), "obsidian holds against anything short of the ceiling");
            assertTrue(BattleDamage.breaks(obsidian, level, AT, BattleDamageConfig.TOP_POWER.getDefault()), "and breaks to the top tier");
            assertEquals(0, BattleDamage.powerAt(10, 2, 2), 1e-9, "nothing left at the radius");
            assertEquals(5, BattleDamage.powerAt(10, 2, 1), 1e-9, "half of it halfway");
        });
    }

    @Test
    void whatVanillaMakesUnbreakableNeverBreaks(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            ServerLevel level = world(server);
            for (var block : List.of(Blocks.BEDROCK, Blocks.BARRIER, Blocks.END_PORTAL_FRAME, Blocks.COMMAND_BLOCK, Blocks.STRUCTURE_BLOCK,
                    Blocks.JIGSAW, Blocks.END_PORTAL, Blocks.LIGHT)) {
                assertFalse(BattleDamage.breaks(block.defaultBlockState(), level, AT, 1.0e9), block + " never breaks");
            }
            assertFalse(BattleDamage.breaks(Blocks.WATER.defaultBlockState(), level, AT, 1.0e9), "nor does a fluid");
            permanent(() -> {
                clear(level);
                level.setBlock(AT.below(), Blocks.BEDROCK.defaultBlockState(), 2);
                level.setBlock(AT.east(), Blocks.BARRIER.defaultBlockState(), 2);
                blast(level, 1.0e6);
                assertSame(Blocks.BEDROCK, level.getBlockState(AT.below()).getBlock());
                assertSame(Blocks.BARRIER, level.getBlockState(AT.east()).getBlock());
            });
        });
    }

    @Test
    void theProtectedListNeverBreaksAndTakesTags(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            ServerLevel level = world(server);
            with(Mode.PERMANENT, 4096, 100_000, true, List.of("minecraft:glass", "#minecraft:logs"), () -> {
                clear(level);
                level.setBlock(AT.below(), Blocks.GLASS.defaultBlockState(), 2);
                level.setBlock(AT.east(), Blocks.OAK_LOG.defaultBlockState(), 2);
                level.setBlock(AT.west(), Blocks.DIRT.defaultBlockState(), 2);
                blast(level, 3);
                assertSame(Blocks.GLASS, level.getBlockState(AT.below()).getBlock(), "on the list by id");
                assertSame(Blocks.OAK_LOG, level.getBlockState(AT.east()).getBlock(), "on the list by tag");
                assertTrue(level.getBlockState(AT.west()).isAir(), "not on it: breaks");
            });
            permanent(() -> assertFalse(BattleDamage.isProtected(Blocks.GLASS.defaultBlockState()), "empty by default: nothing protected"));
        });
    }

    @Test
    void brokenBlocksDropNothingButAContainerSpills(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            ServerLevel level = world(server);
            permanent(() -> {
                clear(level);
                level.setBlock(AT.below(), Blocks.DIRT.defaultBlockState(), 3);
                level.setBlock(AT.north(), Blocks.DIRT.defaultBlockState(), 3);
                level.setBlock(AT, Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, Direction.SOUTH), 3);
                level.setBlock(AT.east(), Blocks.CHEST.defaultBlockState(), 3);
                ((ChestBlockEntity) level.getBlockEntity(AT.east())).setItem(0, new ItemStack(Items.DIAMOND, 5));
                blast(level, 3);
                assertTrue(level.getBlockState(AT.east()).isAir(), "the chest broke");
                assertTrue(level.getBlockState(AT.north()).isAir() && level.getBlockState(AT).isAir(), "the dirt and the torch on it");
                List<ItemEntity> dropped = items(level);
                assertEquals(1, dropped.size(), "only what was in the chest: no dirt, no torch, no chest");
                assertTrue(dropped.get(0).getItem().is(Items.DIAMOND) && dropped.get(0).getItem().getCount() == 5);
            });
            with(Mode.PERMANENT, 4096, 100_000, false, List.of(), () -> {
                clear(level);
                level.setBlock(AT.east(), Blocks.CHEST.defaultBlockState(), 3);
                ((ChestBlockEntity) level.getBlockEntity(AT.east())).setItem(0, new ItemStack(Items.DIAMOND, 5));
                blast(level, 3);
                assertTrue(level.getBlockState(AT.east()).isAir());
                assertTrue(items(level).isEmpty(), "spillContents off: its contents go with it");
            });
        });
    }

    @Test
    void theCapsHoldPerHitAndPerSecond(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            ServerLevel level = world(server);
            with(Mode.PERMANENT, 3, 100_000, true, List.of(), () -> {
                clear(level);
                for (Direction d : Direction.values()) level.setBlock(AT.relative(d), Blocks.DIRT.defaultBlockState(), 2);
                level.setBlock(AT.relative(Direction.UP, 2), Blocks.DIRT.defaultBlockState(), 2);
                BattleDamage.Result r = blast(level, 3);
                assertEquals(3, r.broken().size(), "no more than maxBlocksPerHit");
                assertFalse(level.getBlockState(AT.relative(Direction.UP, 2)).isAir(), "the nearest go first");
            });
            with(Mode.PERMANENT, 4096, 5, true, List.of(), () -> {
                clear(level);
                for (Direction d : Direction.values()) level.setBlock(AT.relative(d), Blocks.DIRT.defaultBlockState(), 2);
                int first = blast(level, 3).broken().size();
                for (Direction d : Direction.values()) level.setBlock(AT.relative(d), Blocks.DIRT.defaultBlockState(), 2);
                int second = blast(level, 3).broken().size();
                assertEquals(5, first + second, "no more than maxBlocksPerSecond in a dimension");
                TestServer.advanceGameTime(server, 20);
                for (Direction d : Direction.values()) level.setBlock(AT.relative(d), Blocks.DIRT.defaultBlockState(), 2);
                assertEquals(5, blast(level, 3).broken().size(), "a second later there's room again");
            });
        });
    }

    @Test
    void aCancelledBreakEventKeepsTheBlock(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            ServerLevel level = world(server);
            permanent(() -> {
                clear(level);
                level.setBlock(AT.below(), Blocks.DIRT.defaultBlockState(), 2);
                level.setBlock(AT.east(), Blocks.DIRT.defaultBlockState(), 2);
                refuseAt = AT.east();
                try {
                    blast(level, 3);
                } finally {
                    refuseAt = null;
                }
                assertSame(Blocks.DIRT, level.getBlockState(AT.east()).getBlock(), "a claim mod said no");
                assertTrue(level.getBlockState(AT.below()).isAir(), "the rest still breaks");
            });
        });
    }

    @Test
    void cosmeticBreaksNothing(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            ServerLevel level = world(server);
            with(Mode.COSMETIC, 4096, 100_000, true, List.of(), () -> {
                clear(level);
                level.setBlock(AT.below(), Blocks.DIRT.defaultBlockState(), 2);
                BattleDamage.Result r = blast(level, 3);
                assertTrue(r.broken().isEmpty());
                assertEquals(List.of(AT.below()), r.wouldBreak(), "what would have broken goes to the clients for their debris");
                assertSame(Blocks.DIRT, level.getBlockState(AT.below()).getBlock());
            });
        });
    }

    @Test
    void restoringRebuildsWithContentsNeverSpillsAndSurvivesARestart(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            ServerLevel level = world(server);
            double delay = BattleDamageConfig.RESTORE_DELAY.get();
            int interval = BattleDamageConfig.RESTORE_INTERVAL.get();
            with(Mode.RESTORING, 4096, 100_000, true, List.of(), () -> {
                try {
                    clear(level);
                    BattleScars scars = BattleScars.get(level);
                    scars.forget();
                    level.setBlock(AT.below(), Blocks.DIRT.defaultBlockState(), 3);
                    level.setBlock(AT.north(), Blocks.DIRT.defaultBlockState(), 3);
                    level.setBlock(AT, Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, Direction.SOUTH), 3);
                    level.setBlock(AT.east(), Blocks.CHEST.defaultBlockState(), 3);
                    ((ChestBlockEntity) level.getBlockEntity(AT.east())).setItem(0, new ItemStack(Items.DIAMOND, 5));
                    blast(level, 3);
                    assertTrue(level.getBlockState(AT.east()).isAir(), "it breaks like any other mode");
                    assertTrue(items(level).isEmpty(), "but a container never spills: it comes back full");
                    assertEquals(4, scars.pending(), "the dirt, the chest, and the torch that came down with them");

                    // A restart: the scars are saved with the dimension and read back.
                    CompoundTag saved = scars.save(new CompoundTag(), level.registryAccess());
                    BattleScars reloaded = BattleScars.load(saved, level.registryAccess());
                    assertEquals(4, reloaded.pending(), "nothing lost in a restart");

                    BattleDamageConfig.RESTORE_DELAY.set(5.0);
                    BattleDamageConfig.RESTORE_INTERVAL.set(1);
                    reloaded.tick(level);
                    assertTrue(level.getBlockState(AT.below()).isAir(), "nothing comes back while the fight's recent");
                    TestServer.advanceGameTime(server, 100);
                    for (int i = 0; i < 10; i++) {
                        TestServer.advanceGameTime(server, 1);
                        reloaded.tick(level);
                    }
                    assertEquals(0, reloaded.pending());
                    assertSame(Blocks.DIRT, level.getBlockState(AT.below()).getBlock());
                    assertSame(Blocks.WALL_TORCH, level.getBlockState(AT).getBlock(), "the torch is back on its wall");
                    ChestBlockEntity chest = (ChestBlockEntity) level.getBlockEntity(AT.east());
                    assertTrue(chest != null && chest.getItem(0).is(Items.DIAMOND) && chest.getItem(0).getCount() == 5, "with what it held");
                    assertTrue(items(level).isEmpty(), "and nothing dropped on the way");
                } finally {
                    BattleScars.get(level).forget(); // the original copy: the reloaded one did its work
                    BattleDamageConfig.RESTORE_DELAY.set(delay);
                    BattleDamageConfig.RESTORE_INTERVAL.set(interval);
                }
            });
        });
    }

    @Test
    void aBlockSomeonePutThereSinceIsNeverOverwritten(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            ServerLevel level = world(server);
            with(Mode.RESTORING, 4096, 100_000, true, List.of(), () -> {
                clear(level);
                level.setBlock(AT.east(), Blocks.CHEST.defaultBlockState(), 3);
                ((ChestBlockEntity) level.getBlockEntity(AT.east())).setItem(0, new ItemStack(Items.EMERALD, 3));
                BattleScars scars = new BattleScars();
                scars.add(level, Vec3.atCenterOf(AT), List.of(new BattleScars.Piece(AT.east(), level.getBlockState(AT.east()),
                        level.getBlockEntity(AT.east()).saveWithFullMetadata(level.registryAccess()))));
                ((ChestBlockEntity) level.getBlockEntity(AT.east())).clearContent();
                level.setBlock(AT.east(), Blocks.STONE.defaultBlockState(), 3); // a player built over the hole
                scars.restoreNow(level);
                for (int i = 0; i < 10; i++) {
                    TestServer.advanceGameTime(server, 1);
                    scars.tick(level);
                }
                assertSame(Blocks.STONE, level.getBlockState(AT.east()).getBlock(), "what's there now stays");
                List<ItemEntity> dropped = items(level);
                assertEquals(1, dropped.size(), "and the chest's contents come out instead of being lost");
                assertTrue(dropped.get(0).getItem().is(Items.EMERALD));
            });
        });
    }

    @Test
    void someoneStandingInTheCraterOnlyHoldsUpTheBlocksRoundThem(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            ServerLevel level = world(server);
            with(Mode.RESTORING, 4096, 100_000, true, List.of(), () -> {
                clear(level);
                BlockPos far = AT.east(4);
                BattleScars scars = new BattleScars();
                scars.add(level, Vec3.atCenterOf(AT), List.of(new BattleScars.Piece(AT, Blocks.STONE.defaultBlockState(), null),
                        new BattleScars.Piece(far, Blocks.STONE.defaultBlockState(), null)));
                net.minecraft.world.entity.decoration.ArmorStand stand =
                        new net.minecraft.world.entity.decoration.ArmorStand(level, AT.getX() + 0.5, AT.getY(), AT.getZ() + 0.5);
                stand.setNoGravity(true);
                level.addFreshEntity(stand);
                try {
                    scars.restoreNow(level);
                    for (int i = 0; i < 10; i++) {
                        TestServer.advanceGameTime(server, 1);
                        scars.tick(level);
                    }
                    assertTrue(level.getBlockState(AT).isAir(), "never over someone standing in it");
                    assertSame(Blocks.STONE, level.getBlockState(far).getBlock(), "but the rest of the scar isn't held up");
                    assertEquals(1, scars.pending());
                    stand.discard();
                    for (int i = 0; i < 30; i++) {
                        TestServer.advanceGameTime(server, 1);
                        scars.tick(level);
                    }
                    assertSame(Blocks.STONE, level.getBlockState(AT).getBlock(), "once they've moved, it comes back");
                } finally {
                    stand.discard();
                }
            });
        });
    }

    @Test
    void swapNeverBreaksAndLightHitsNeverDo(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            ServerLevel level = world(server);
            permanent(() -> {
                clear(level);
                ServerPlayer a = TestServer.damageable(server, "bd_attacker"), b = TestServer.damageable(server, "bd_victim");
                a.moveTo(AT.getX() + 0.5, AT.getY(), AT.getZ() - 2.5);
                b.moveTo(AT.getX() + 0.5, AT.getY(), AT.getZ() + 0.5);
                level.setBlock(AT.below(), Blocks.GLASS.defaultBlockState(), 2);
                try {
                    Impacts.clear();
                    Impacts.hit(a, b, ImpactKind.TECHNIQUE, 2.0, 0, 1, false); // a Total Swap's worth, from Swap
                    Impacts.flush();
                    assertSame(Blocks.GLASS, level.getBlockState(AT.below()).getBlock(), "Swap is a clean magic trick");
                    b.invulnerableTime = 0;
                    b.hurt(a.damageSources().playerAttack(a), 1f); // a punch
                    Impacts.flush();
                    assertSame(Blocks.GLASS, level.getBlockState(AT.below()).getBlock(), "a light hit never breaks anything");
                } finally {
                    Impacts.clear();
                    TestServer.removePlayers(server);
                }
            });
        });
    }

    @Test
    void anAreaBlastBreaksTheGroundAtItsCentreEvenWhenItHitsNobody(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            ServerLevel level = world(server);
            permanent(() -> {
                clear(level);
                level.setBlock(AT.below(), Blocks.DIRT.defaultBlockState(), 2);
                // A Full Curio Burst's worth (12 damage, a 0.7 push, a full charge, a Full puppet's 180 Malice) with nobody near.
                Impacts.blast(level, Vec3.atCenterOf(AT), null, null, 12, 0.7, 0, 1, 180, true);
                assertTrue(level.getBlockState(AT.below()).isAir(), "it breaks the ground where it went off");

                level.setBlock(AT.below(), Blocks.DIRT.defaultBlockState(), 2);
                Impacts.blast(level, Vec3.atCenterOf(AT), null, null, 12, 0.7, 0, 1, 180, false);
                assertSame(Blocks.DIRT, level.getBlockState(AT.below()).getBlock(), "a blast that never breaks blocks doesn't");
                Impacts.blast(level, Vec3.atCenterOf(AT), null, null, 1, 0.4, 0, 0, 0, true);
                assertSame(Blocks.DIRT, level.getBlockState(AT.below()).getBlock(), "nor does a light one");
            });
        });
    }

    @Test
    void aSlamMeetsTheWallAtTheEdgeOfTheBody() {
        AABB box = new AABB(0, 0, 0, 0.6, 1.8, 0.6);
        Vec3 c = WallSlams.contact(box, new Vec3(1, 0, 0));
        assertEquals(0.65, c.x, 1e-9, "the side it was going: just past its edge");
        assertEquals(0.9, c.y, 1e-9);
        Vec3 down = WallSlams.contact(box, new Vec3(0, -1, 0));
        assertEquals(-0.05, down.y, 1e-9, "into the floor: at its feet");
    }
}

package com.cnethuka.kegare.battledamage;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cnethuka.kegare.TestServer;
import com.cnethuka.kegare.fists.BlackSpark;
import com.cnethuka.kegare.fists.FistsAdvancements;
import com.cnethuka.kegare.impact.Impacts;
import com.cnethuka.kegare.player.BirthService;
import java.util.OptionalInt;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/** "Lights Out" (DESIGN §3.28): a Black Spark slamming a player into a wall, with battle damage on or off; never a mob. */
@ExtendWith(EphemeralTestServerProvider.class)
class LightsOutTest {
    private static final double Y = 120;

    private static ServerPlayer player(MinecraftServer server, String name) {
        ServerPlayer p = TestServer.damageable(server, name);
        server.overworld().addNewPlayer(p);
        BirthService.rebirth(p, OptionalInt.of(1000));
        return p;
    }

    /** A forced Black Spark on {@code target}, its knockback, and the tick's impact resolved (the slam watch starts there). */
    private static void spark(ServerPlayer attacker, LivingEntity target) {
        BlackSpark.force(attacker);
        NeoForge.EVENT_BUS.post(new AttackEntityEvent(attacker, target));
        target.invulnerableTime = 0;
        target.hurt(attacker.damageSources().playerAttack(attacker), 0.01f);
        BlackSpark.knockBack();
        Impacts.flush();
    }

    /** Flown along +x at 1.2 blocks a tick into the wall at x = 3, and stopped dead against it. */
    private static void flyIntoTheWall(ServerLevel level, LivingEntity victim, double z) {
        for (double x : new double[] {0.3, 1.5, 2.7, 2.7}) {
            victim.setPos(x, Y, z);
            WallSlams.tick(level);
        }
    }

    @Test
    void aSparkIntoAWallIsLightsOutWithBattleDamageOnOrOff(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            TestServer.ensureOverworld(server);
            ServerLevel level = server.overworld();
            server.setPvpAllowed(true);
            boolean rule = level.getGameRules().getBoolean(BattleDamage.RULE);
            BlockPos[] wall = {new BlockPos(3, (int) Y, 8), new BlockPos(3, (int) Y + 1, 8), new BlockPos(3, (int) Y, 12), new BlockPos(3, (int) Y + 1, 12),
                    new BlockPos(3, (int) Y, 4), new BlockPos(3, (int) Y + 1, 4)};
            Zombie zombie = new Zombie(EntityType.ZOMBIE, level);
            try {
                for (BlockPos at : wall) level.setBlock(at, Blocks.STONE.defaultBlockState(), 3);
                ServerPlayer p = player(server, "lights_p");
                ServerPlayer q = player(server, "lights_q");
                ServerPlayer o = player(server, "lights_o");
                p.moveTo(-1.5, Y, 8.5, -90f, 0f);

                // A mob slammed: never.
                zombie.moveTo(0.3, Y, 4.5, 0f, 0f);
                zombie.setNoGravity(true);
                zombie.setNoAi(true);
                level.addFreshEntity(zombie);
                spark(p, zombie);
                flyIntoTheWall(level, zombie, 4.5);
                assertFalse(TestServer.hasAdvancement(server, p, FistsAdvancements.LIGHTS_OUT), "a mob never counts");

                // Battle damage off: the same slam counts, and breaks nothing.
                level.getGameRules().getRule(BattleDamage.RULE).set(false, server);
                q.moveTo(0.3, Y, 8.5, 90f, 0f);
                spark(p, q);
                flyIntoTheWall(level, q, 8.5);
                assertTrue(TestServer.hasAdvancement(server, p, FistsAdvancements.LIGHTS_OUT), "Lights Out, with battle damage off");
                assertSame(Blocks.STONE, level.getBlockState(wall[0]).getBlock(), "and no crater");

                // On: a crater, and it counts for anyone.
                level.getGameRules().getRule(BattleDamage.RULE).set(true, server);
                ServerPlayer p2 = player(server, "lights_p2");
                p2.moveTo(-1.5, Y, 12.5, -90f, 0f);
                o.moveTo(0.3, Y, 12.5, 90f, 0f);
                spark(p2, o);
                flyIntoTheWall(level, o, 12.5);
                assertTrue(TestServer.hasAdvancement(server, p2, FistsAdvancements.LIGHTS_OUT), "Lights Out");
            } finally {
                zombie.discard();
                level.getGameRules().getRule(BattleDamage.RULE).set(rule, server);
                for (BlockPos at : wall) level.setBlock(at, Blocks.AIR.defaultBlockState(), 3);
                WallSlams.clear();
                BlackSpark.clear();
                com.cnethuka.kegare.fists.Zone.clear();
                FistsAdvancements.clear();
                TestServer.removePlayers(server);
            }
        });
    }
}

package com.cnethuka.kegare.malice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cnethuka.kegare.TestServer;
import com.cnethuka.kegare.combat.CombatHelper;
import com.cnethuka.kegare.config.KegareServerConfig;
import com.cnethuka.kegare.innate_technique.rime.RimeConfig;
import com.cnethuka.kegare.innate_technique.puppet_manipulation.PuppetConfig;
import com.cnethuka.kegare.rmt.RmtConfig;
import java.util.List;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * The Malice scale in the shipped defaults, and regen on a real player. Defaults are read from the spec, not the live values,
 * so a locally edited kegare-server.toml can't change what this checks.
 */
@ExtendWith(EphemeralTestServerProvider.class)
class MaliceScaleTest {
    private static Object tierDefault(MaliceTier tier, String key) {
        ModConfigSpec.ConfigValue<?> value = KegareServerConfig.SPEC.getValues().get(List.of("malice", "tiers", tier.serializedName(), key));
        return value.getDefault();
    }

    @Test
    void theTiersShipWithTheSpecsNumbers() {
        assertEquals(2, tierDefault(MaliceTier.ZERO, "weight"));
        assertEquals(30, tierDefault(MaliceTier.LOW, "weight"));
        assertEquals(45, tierDefault(MaliceTier.AVERAGE, "weight"));
        assertEquals(20, tierDefault(MaliceTier.HIGH, "weight"));
        assertEquals(3, tierDefault(MaliceTier.VAST, "weight"));
        assertEquals(250, tierDefault(MaliceTier.LOW, "base"));
        assertEquals(600, tierDefault(MaliceTier.AVERAGE, "base"));
        assertEquals(1500, tierDefault(MaliceTier.HIGH, "base"));
        assertEquals(10_000, tierDefault(MaliceTier.VAST, "base"));
        assertEquals(3.0, tierDefault(MaliceTier.LOW, "regenPercentPerSecond"));
        assertEquals(2.0, tierDefault(MaliceTier.AVERAGE, "regenPercentPerSecond"));
        assertEquals(1.5, tierDefault(MaliceTier.HIGH, "regenPercentPerSecond"));
        assertEquals(0.75, tierDefault(MaliceTier.VAST, "regenPercentPerSecond"));
        assertEquals(10.0, KegareServerConfig.CAPACITY_VARIATION_PERCENT.getDefault());
        assertEquals(5.0, KegareServerConfig.REGEN_PAUSE_SECONDS.getDefault(), "the combat pause stays");
    }

    /** Low was 60 with a 10-cost Frost Shard and a 24/s RMT: everything is x4, so a Low player's budget buys about the same. */
    @Test
    void lowKeepsItsTightBudget() {
        int low = (Integer) tierDefault(MaliceTier.LOW, "base");
        assertEquals(6.0, low / RimeConfig.SHARD_COST.getDefault(), 0.5, "about six Frost Shards, as before");
        assertEquals(2.5, low / RmtConfig.UPKEEP.getDefault(), 0.2, "about two and a half seconds of RMT, as before");
        assertEquals(40.0, PuppetConfig.TIER_COST[0].getDefault(), "a weak puppet: about six of those too");
        assertEquals(240.0, com.cnethuka.kegare.innate_technique.swap.SwapConfig.TOTAL_SWAP_COST.getDefault(), "Total Swap still takes nearly all of it");
    }

    @Test
    void regenFollowsTheTierAndStopsInCombat(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            TestServer.ensureOverworld(server);
            ServerLevel level = server.overworld();
            ServerPlayer player = TestServer.player(server, "malice_regen");
            level.addNewPlayer(player);
            try {
                for (int capacity : new int[] {250, 600, 1500, 10_000}) {
                    MaliceRoller.Result birth = MaliceService.adminBirth(player, capacity);
                    double perSecond = capacity * KegareServerConfig.regenPercent(birth.tier()) / 100.0;
                    MaliceService.setCurrent(player, 0.0);
                    for (int t = 0; t < 20; t++) MaliceService.tickRegen(player);
                    assertEquals(perSecond, MaliceService.get(player).current(), 1e-6, birth.tier() + ": a second's regen");
                }

                // It never overshoots a full reserve.
                MaliceService.setCurrent(player, 10_000.0 - 1.0);
                for (int t = 0; t < 20; t++) MaliceService.tickRegen(player);
                assertEquals(10_000.0, MaliceService.get(player).current(), 1e-6);

                // In combat it stops, for regenPauseSeconds, and then comes back.
                MaliceService.setCurrent(player, 100.0);
                CombatHelper.markCombat(player);
                assertTrue(CombatHelper.isRegenPaused(player));
                MaliceService.tickRegen(player);
                assertEquals(100.0, MaliceService.get(player).current(), 1e-6, "paused");
                TestServer.advanceGameTime(server, KegareServerConfig.ticks(KegareServerConfig.REGEN_PAUSE_SECONDS) - 1);
                MaliceService.tickRegen(player);
                assertEquals(100.0, MaliceService.get(player).current(), 1e-6, "still paused just before it ends");
                TestServer.advanceGameTime(server, 1);
                assertFalse(CombatHelper.isRegenPaused(player));
                MaliceService.tickRegen(player);
                assertTrue(MaliceService.get(player).current() > 100.0, "regen is back");

                // Severed: nothing to regenerate.
                MaliceRoller.Result severed = MaliceService.adminBirth(player, 0);
                assertEquals(MaliceTier.ZERO, severed.tier());
                assertEquals(0.0, KegareServerConfig.regenPercent(MaliceTier.ZERO), 1e-9);
                MaliceService.tickRegen(player);
                assertEquals(0.0, MaliceService.get(player).current(), 1e-9);
            } finally {
                level.removePlayerImmediately(player, Entity.RemovalReason.DISCARDED);
            }
        });
    }
}

package com.cnethuka.kegare.rmt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cnethuka.kegare.TestServer;
import com.cnethuka.kegare.innate_technique.rime.Rime;
import com.cnethuka.kegare.innatetechnique.CastIntent;
import com.cnethuka.kegare.innatetechnique.CastManager;
import com.cnethuka.kegare.innatetechnique.InnateTechniqueService;
import com.cnethuka.kegare.loadout.LoadoutService;
import com.cnethuka.kegare.loadout.SharedAbilityService;
import com.cnethuka.kegare.malice.MaliceService;
import com.cnethuka.kegare.player.BirthService;
import com.cnethuka.kegare.progression.Grade;
import com.cnethuka.kegare.progression.GradeService;
import com.cnethuka.kegare.progression.Insight;
import com.cnethuka.kegare.progression.InsightCategory;
import com.cnethuka.kegare.progression.Unlocks;
import com.cnethuka.kegare.trait.Trait;
import com.cnethuka.kegare.trait.TraitService;
import java.util.Optional;
import java.util.OptionalInt;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * RMT's special, healing another player (DESIGN §3.17): refused without knowing how; the moment rolls only for the eligible and only
 * when someone near drops to 3 hearts; healing them in it teaches it for good; it heals at the caster's output (not theirs), Severed
 * targets too; leaving range or sight ends it; and it shares RMT's cooldown.
 */
@ExtendWith(EphemeralTestServerProvider.class)
class RmtOnAnotherTest {
    private static final double Y = 120;

    private static ServerPlayer player(MinecraftServer server, ServerLevel level, String name, int capacity) {
        ServerPlayer p = TestServer.damageable(server, name);
        level.addNewPlayer(p);
        BirthService.rebirth(p, OptionalInt.of(capacity));
        TraitService.removeAll(p, Trait.RemoveReason.ADMIN);
        Insight.reset(p);
        GradeService.set(p, Grade.GRADE_4);
        if (capacity > 0) InnateTechniqueService.setInnateTechnique(p, Rime.RIME.get());
        p.setHealth(p.getMaxHealth());
        return p;
    }

    /** RMT unlocked and on slot 2, selected, looking along +X. */
    private static ServerPlayer healer(MinecraftServer server, ServerLevel level, String name) {
        ServerPlayer p = player(server, level, name, 3000);
        SharedAbilityService.unlock(p, Rmt.REVERSE_MALICE_TECHNIQUE); // unlocked_rmt: Output 5
        LoadoutService.edit(p, LoadoutService.Action.SET, 1, 0, Optional.of(Rmt.REVERSE_MALICE_TECHNIQUE.id()));
        InnateTechniqueService.select(p, 1);
        MaliceService.refill(p);
        p.moveTo(0.5, Y, 0.5, -90f, 0f);
        p.setYHeadRot(-90f);
        return p;
    }

    private static String blocked(ServerPlayer p) {
        return ((TranslatableContents) Rmt.ON_ANOTHER.blockedMessage(p).getContents()).getKey();
    }

    @Test
    void learnedInAMomentThenHealsOthers(MinecraftServer server) throws Exception {
        double chance = RmtConfig.MOMENT_CHANCE.get();
        TestServer.onServerThread(server, () -> {
            TestServer.ensureOverworld(server);
            ServerLevel level = server.overworld();
            BlockPos wall = new BlockPos(1, (int) Y + 1, 0);
            try {
                ServerPlayer healer = healer(server, level, "rmt_other_healer");
                ServerPlayer hurt = player(server, level, "rmt_other_hurt", 500);
                hurt.moveTo(2.5, Y, 0.5, 90f, 0f);

                // Not eligible, never taught: refused, nothing paid.
                double malice = MaliceService.get(healer).current();
                CastManager.handleIntent(healer, CastIntent.SPECIAL_PRESS);
                assertNull(CastManager.state(healer).casting(), "refused");
                assertEquals(malice, MaliceService.get(healer).current(), 1e-6, "free");
                assertEquals("message.kegare.rmt.dont_know", blocked(healer), "\"You don't know how to do that yet.\"");

                // The moment rolls only for the eligible, and only when someone near drops to 3 hearts or less.
                RmtConfig.MOMENT_CHANCE.set(1.0);
                hurt.invulnerableTime = 0;
                hurt.hurt(hurt.damageSources().generic(), 15f); // to 5
                assertNull(GreenOrbService.state(healer).momentTarget, "not eligible: no moment");
                Insight.add(healer, InsightCategory.OUTPUT, RmtConfig.OTHERS_INSIGHT.get() - Insight.get(healer, InsightCategory.OUTPUT));
                assertTrue(Unlocks.eligible(healer, RmtIds.ON_ANOTHER_UNLOCK), "setup: eligible now");
                assertFalse(Unlocks.has(healer, RmtIds.ON_ANOTHER_UNLOCK), "but eligible isn't knowing (no auto-grant)");
                CastManager.handleIntent(healer, CastIntent.SPECIAL_PRESS);
                assertNull(CastManager.state(healer).casting(), "eligible but not taught: still refused");
                hurt.setHealth(10f);
                hurt.invulnerableTime = 0;
                hurt.hurt(hurt.damageSources().generic(), 1f); // to 9: not low enough
                assertNull(GreenOrbService.state(healer).momentTarget, "above 3 hearts: no roll");
                hurt.invulnerableTime = 0;
                hurt.hurt(hurt.damageSources().generic(), 4f); // to 5: crossing it
                assertEquals(hurt.getUUID(), GreenOrbService.state(healer).momentTarget, "the moment: they glow for the healer");

                // Healing them in it teaches it, for good.
                CastManager.handleIntent(healer, CastIntent.SPECIAL_PRESS);
                assertNotNull(CastManager.state(healer).casting(), "healing them");
                assertTrue(Unlocks.has(healer, RmtIds.ON_ANOTHER_UNLOCK), "learned");
                assertEquals(malice - RmtConfig.COST.get() * RmtConfig.OTHERS_COST.get(), MaliceService.get(healer).current(), 1e-6,
                        "othersCost times RMT's start cost");

                // At the healer's output (theirs at Grade 4: 1x), times othersOutput. The healed one is paid healed_by_other.
                float before = hurt.getHealth();
                CastManager.tick(healer);
                assertEquals(before + RmtConfig.HEAL_PER_SECOND.get() * RmtConfig.OTHERS_OUTPUT.get() / 20.0, hurt.getHealth(), 1e-4, "0.75 of 1x");
                assertTrue(Insight.hasFirst(hurt, "healed_by_other"), "healed_by_other paid to the healed");
                GradeService.set(healer, Grade.SPECIAL_GRADE); // the healer's output counts, not theirs
                before = hurt.getHealth();
                CastManager.tick(healer);
                assertEquals(before + RmtConfig.HEAL_PER_SECOND.get() * RmtOutput.of(healer) * RmtConfig.OTHERS_OUTPUT.get() / 20.0,
                        hurt.getHealth(), 1e-4, "at the healer's output (1.3x now)");

                // Out of range: it ends, and the cooldown is RMT's own.
                hurt.moveTo(5.5, Y, 0.5);
                CastManager.tick(healer);
                assertNull(CastManager.state(healer).casting(), "out of range: over");
                assertTrue(InnateTechniqueService.cooldownRemaining(healer, Rmt.REVERSE_MALICE_TECHNIQUE) > 0, "shares RMT's cooldown");
                CastManager.handleIntent(healer, CastIntent.CAST_PRESS);
                assertNull(CastManager.state(healer).casting(), "RMT itself is on that cooldown too");

                // Out of sight: it ends.
                InnateTechniqueService.clearCooldowns(healer);
                MaliceService.refill(healer);
                hurt.moveTo(2.5, Y, 0.5, 90f, 0f);
                CastManager.handleIntent(healer, CastIntent.SPECIAL_PRESS);
                assertNotNull(CastManager.state(healer).casting(), "known now: no moment needed");
                level.setBlock(wall, Blocks.STONE.defaultBlockState(), 3);
                level.setBlock(wall.below(), Blocks.STONE.defaultBlockState(), 3);
                CastManager.tick(healer);
                assertNull(CastManager.state(healer).casting(), "a wall between: over");
                level.setBlock(wall, Blocks.AIR.defaultBlockState(), 3);
                level.setBlock(wall.below(), Blocks.AIR.defaultBlockState(), 3);

                // A Severed player is healed like anyone; it keeps running at their full health.
                ServerPlayer severed = player(server, level, "rmt_other_severed", 0);
                hurt.moveTo(0.5, Y, 30.5);
                severed.moveTo(2.5, Y, 0.5, 90f, 0f);
                severed.setHealth(10f);
                InnateTechniqueService.clearCooldowns(healer);
                MaliceService.refill(healer);
                CastManager.handleIntent(healer, CastIntent.SPECIAL_PRESS);
                assertNotNull(CastManager.state(healer).casting(), "on a Severed player");
                for (int i = 0; i < 200; i++) CastManager.tick(healer);
                assertEquals(severed.getMaxHealth(), severed.getHealth(), 1e-4, "healed up, and no more");
                assertNotNull(CastManager.state(healer).casting(), "still running at their full health");
                CastManager.handleIntent(healer, CastIntent.SPECIAL_RELEASE);
            } finally {
                RmtConfig.MOMENT_CHANCE.set(chance);
                level.setBlock(wall, Blocks.AIR.defaultBlockState(), 3);
                level.setBlock(wall.below(), Blocks.AIR.defaultBlockState(), 3);
                TestServer.removePlayers(server);
            }
        });
    }
}

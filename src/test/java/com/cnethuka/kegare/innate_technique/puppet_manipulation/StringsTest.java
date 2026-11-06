package com.cnethuka.kegare.innate_technique.puppet_manipulation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cnethuka.kegare.TestServer;
import com.cnethuka.kegare.innatetechnique.CastIntent;
import com.cnethuka.kegare.innatetechnique.CastManager;
import com.cnethuka.kegare.innatetechnique.InnateTechniqueService;
import com.cnethuka.kegare.malice.MaliceService;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Strings: the two stages, all four variations on players, mobs and the caster's puppets, the free fizzles, snapping,
 * expiring, bosses, the cancelled event and everything that ends a string. Everything hovers at Y=120 inside chunk (0,0).
 */
@ExtendWith(EphemeralTestServerProvider.class)
class StringsTest {
    private static final double Y = PuppetTest.Y;
    private static final double EPS = 1.0e-3;

    private static final Set<LivingEntity> REFUSE = new HashSet<>();
    private static final List<StringsTargetEvent> SEEN = new ArrayList<>();
    private static boolean listening;

    private static void onTarget(StringsTargetEvent event) {
        SEEN.add(event);
        if (REFUSE.contains(event.getTarget())) event.setCanceled(true);
    }

    @Test
    void firingAndReelingIn(MinecraftServer server) throws Exception {
        arena(server, "strings_reel", (level, caster) -> {
            // Nothing in sight: free, no cooldown, no string. R with no string: the same.
            caster.setXRot(-60f);
            assertFreeFizzle(caster, CastIntent.CAST_PRESS);
            assertFreeFizzle(caster, CastIntent.SPECIAL_PRESS);
            MaliceService.setCurrent(caster, 0.0);
            assertFreeFizzle(caster, CastIntent.SPECIAL_PRESS); // free even at no Malice

            // Beyond 24 blocks: out of range. Armor stands aren't targets.
            MaliceService.refill(caster);
            Cow far = cow(level, 0.5, Y + PuppetConfig.STRINGS_RANGE.get() + 3, 0.5);
            PossessionTest.aimAt(caster, far);
            assertFreeFizzle(caster, CastIntent.CAST_PRESS);
            far.discard();
            ArmorStand stand = new ArmorStand(EntityType.ARMOR_STAND, level);
            stand.moveTo(6.5, Y, 0.5, 0f, 0f);
            level.addFreshEntity(stand);
            PossessionTest.aimAt(caster, stand);
            assertFreeFizzle(caster, CastIntent.CAST_PRESS);
            stand.discard();

            // Stage 1: the fire cost and only a moment's cooldown.
            Cow cow = cow(level, 12.5, Y, 0.5);
            Vec3 at = cow.position();
            press(caster, cow, CastIntent.CAST_PRESS);
            assertTrue(Strings.isAttached(caster), "attached");
            assertPaid(caster, PuppetConfig.STRINGS_FIRE_COST.get());
            assertEquals(PuppetConfig.ticks(PuppetConfig.STRINGS_FIRE_COOLDOWN), InnateTechniqueService.cooldownRemaining(caster, PuppetManipulation.STRINGS));
            run(server, 3, cow);
            assertEquals(0.0, cow.position().distanceTo(at), 0.3, "attaching doesn't move it");

            // A double press is stopped by that moment's cooldown, not read as stage 2.
            MaliceService.refill(caster);
            CastManager.handleIntent(caster, CastIntent.CAST_PRESS);
            assertEquals(Strings.Phase.ATTACHED, Strings.phase(caster), "still attached");
            InnateTechniqueService.clearCooldowns(caster);

            // Stage 2, looking at nothing: Reel In. The stage's cost, the full cooldown, and the cow comes.
            caster.setXRot(-60f);
            assertEquals(PuppetConfig.STRINGS_REEL_COST.get(), PuppetManipulation.STRINGS.cost(caster), EPS, "the HUD shows Reel's cost");
            CastManager.handleIntent(caster, CastIntent.CAST_PRESS);
            assertPaid(caster, PuppetConfig.STRINGS_REEL_COST.get());
            assertEquals(Strings.Phase.REEL_IN, Strings.phase(caster));
            assertEquals(PuppetConfig.ticks(PuppetConfig.STRINGS_COOLDOWN), InnateTechniqueService.cooldownRemaining(caster, PuppetManipulation.STRINGS));
            run(server, 20, cow);
            assertNull(Strings.phase(caster), "it arrived: the string is done");
            assertTrue(cow.distanceTo(caster) <= PuppetConfig.STRINGS_ARRIVE.get() + PuppetConfig.STRINGS_PULL_SPEED.get(), "reeled in: " + cow.distanceTo(caster));
            assertEquals(PuppetConfig.STRINGS_FIRE_COST.get(), PuppetManipulation.STRINGS.cost(caster), EPS, "the HUD is back to the fire cost");

            // Aiming at the attached target is Reel In too.
            InnateTechniqueService.clearCooldowns(caster);
            cow.moveTo(10.5, Y, 0.5, 0f, 0f);
            press(caster, cow, CastIntent.CAST_PRESS);
            InnateTechniqueService.clearCooldowns(caster);
            press(caster, cow, CastIntent.CAST_PRESS);
            assertEquals(Strings.Phase.REEL_IN, Strings.phase(caster), "same target: Reel In");
            Strings.cut(caster);

            // Your own puppet comes too.
            PuppetEntity puppet = puppet(server, caster, 9.5, 0.5);
            press(caster, puppet, CastIntent.CAST_PRESS);
            InnateTechniqueService.clearCooldowns(caster);
            caster.setXRot(-60f);
            CastManager.handleIntent(caster, CastIntent.CAST_PRESS);
            run(server, 20, puppet);
            assertTrue(puppet.distanceTo(caster) <= PuppetConfig.STRINGS_ARRIVE.get() + PuppetConfig.STRINGS_PULL_SPEED.get(), "the puppet is reeled in: " + puppet.distanceTo(caster));
            cow.discard();
        });
    }

    @Test
    void tieCollidesThenTethers(MinecraftServer server) throws Exception {
        arena(server, "strings_tie", (level, caster) -> {
            Cow a = cow(level, 12.5, Y, 0.5);
            Cow b = cow(level, 0.5, Y, 12.5);
            press(caster, a, CastIntent.CAST_PRESS);
            InnateTechniqueService.clearCooldowns(caster);
            PossessionTest.aimAt(caster, b);
            assertEquals(StringsVariation.TIE, Strings.variation(caster, false));
            assertEquals(PuppetConfig.STRINGS_TIE_COST.get(), PuppetManipulation.STRINGS.cost(caster), EPS, "the HUD shows Tie's cost");
            MaliceService.refill(caster);
            CastManager.handleIntent(caster, CastIntent.CAST_PRESS);
            assertPaid(caster, PuppetConfig.STRINGS_TIE_COST.get());
            assertEquals(Strings.Phase.TIE_PULL, Strings.phase(caster));
            assertEquals(PuppetConfig.ticks(PuppetConfig.STRINGS_COOLDOWN), InnateTechniqueService.cooldownRemaining(caster, PuppetManipulation.STRINGS));
            float full = a.getMaxHealth();
            int ticks = 0;
            while (Strings.phase(caster) == Strings.Phase.TIE_PULL && ticks < 12) {
                run(server, 1, a, b);
                ticks++;
            }
            assertEquals(Strings.Phase.TETHER, Strings.phase(caster), "they met");
            assertEquals(full - PuppetConfig.STRINGS_TIE_DAMAGE.get(), a.getHealth(), EPS, "collision damage");
            assertEquals(full - PuppetConfig.STRINGS_TIE_DAMAGE.get(), b.getHealth(), EPS, "to both");
            assertTrue(a.distanceTo(b) <= PuppetConfig.STRINGS_COLLIDE.get() && a.distanceTo(b) > 0.9, "they meet, not overlap: " + a.distanceTo(b));
            assertEquals(0.0, a.getDeltaMovement().horizontalDistance(), EPS, "the collision stops them");
            assertEquals(0.0, b.getDeltaMovement().horizontalDistance(), EPS, "both");

            // One is carried off: the tether drags them back within 3 blocks of each other, both ways.
            Vec3 before = b.position();
            a.moveTo(a.getX() + 9, Y, a.getZ(), 0f, 0f);
            assertTrue(a.distanceTo(b) > 8);
            run(server, 12, a, b);
            assertTrue(b.position().distanceTo(before) > 0.5, "the other is dragged too");
            assertTrue(a.distanceTo(b) <= PuppetConfig.STRINGS_TETHER_DISTANCE.get() + 0.5, "back within the tether: " + a.distanceTo(b));

            // The caster walking away doesn't matter (the string runs between the two); 4 seconds end it.
            caster.moveTo(0.5, Y + 50, 0.5, 0f, 0f);
            run(server, PuppetConfig.ticks(PuppetConfig.STRINGS_TETHER_DURATION) - 14, a, b); // 12 of its ticks have gone
            assertEquals(Strings.Phase.TETHER, Strings.phase(caster), "not snapped by the caster's distance");
            run(server, 3, a, b);
            assertNull(Strings.phase(caster), "over after 4 seconds");
            a.discard();
            b.discard();
        });
    }

    @Test
    void zipAndLeash(MinecraftServer server) throws Exception {
        arena(server, "strings_zip", (level, caster) -> {
            // Zip to a mob: the caster moves, the target doesn't.
            Cow cow = cow(level, 12.5, Y, 0.5);
            Vec3 at = cow.position();
            press(caster, cow, CastIntent.CAST_PRESS);
            InnateTechniqueService.clearCooldowns(caster);
            caster.setXRot(-60f);
            assertEquals(StringsVariation.ZIP, Strings.variation(caster, true));
            assertEquals(PuppetConfig.STRINGS_ZIP_COST.get(), PuppetManipulation.ZIP_LEASH.cost(caster), EPS, "the HUD shows Zip's cost");
            MaliceService.refill(caster);
            CastManager.handleIntent(caster, CastIntent.SPECIAL_PRESS);
            assertPaid(caster, PuppetConfig.STRINGS_ZIP_COST.get());
            assertEquals(Strings.Phase.ZIP, Strings.phase(caster));
            assertEquals(PuppetConfig.ticks(PuppetConfig.STRINGS_COOLDOWN), InnateTechniqueService.cooldownRemaining(caster, PuppetManipulation.ZIP_LEASH), "shares Strings' cooldown");
            run(server, 20);
            assertNull(Strings.phase(caster));
            assertTrue(caster.distanceTo(cow) <= PuppetConfig.STRINGS_ARRIVE.get() + PuppetConfig.STRINGS_PULL_SPEED.get(), "zipped: " + caster.distanceTo(cow));
            assertEquals(0.0, cow.position().distanceTo(at), EPS, "the target stays put");
            caster.moveTo(0.5, Y, 0.5, 0f, 0f);

            // Zip to your own puppet.
            PuppetEntity puppet = puppet(server, caster, 10.5, 6.5);
            press(caster, puppet, CastIntent.CAST_PRESS);
            InnateTechniqueService.clearCooldowns(caster);
            press(caster, puppet, CastIntent.SPECIAL_PRESS);
            run(server, 20);
            assertTrue(caster.distanceTo(puppet) <= PuppetConfig.STRINGS_ARRIVE.get() + PuppetConfig.STRINGS_PULL_SPEED.get(), "zipped to the puppet");
            caster.moveTo(0.5, Y, 0.5, 0f, 0f);

            // Leash: a mob and a puppet. Both held within 6 blocks; the caster slowed, until it ends.
            InnateTechniqueService.clearCooldowns(caster);
            double speed = caster.getAttributeValue(Attributes.MOVEMENT_SPEED);
            puppet.moveTo(12.5, Y, 0.5, 0f, 0f);
            cow.moveTo(0.5, Y, 13.5, 0f, 0f);
            press(caster, puppet, CastIntent.CAST_PRESS);
            InnateTechniqueService.clearCooldowns(caster);
            PossessionTest.aimAt(caster, cow);
            assertEquals(StringsVariation.LEASH, Strings.variation(caster, true));
            assertEquals(PuppetConfig.STRINGS_LEASH_COST.get(), PuppetManipulation.ZIP_LEASH.cost(caster), EPS, "the HUD shows Leash's cost");
            MaliceService.refill(caster);
            CastManager.handleIntent(caster, CastIntent.SPECIAL_PRESS);
            assertPaid(caster, PuppetConfig.STRINGS_LEASH_COST.get());
            assertEquals(Strings.Phase.LEASH, Strings.phase(caster));
            assertEquals(speed * (1.0 - PuppetConfig.STRINGS_LEASH_SLOW.get()), caster.getAttributeValue(Attributes.MOVEMENT_SPEED), EPS, "slowed");
            run(server, 20, puppet, cow);
            double limit = PuppetConfig.STRINGS_LEASH_DISTANCE.get() + 0.5;
            assertTrue(puppet.distanceTo(caster) <= limit, "the puppet is held: " + puppet.distanceTo(caster));
            assertTrue(cow.distanceTo(caster) <= limit, "the cow is held: " + cow.distanceTo(caster));
            run(server, PuppetConfig.ticks(PuppetConfig.STRINGS_LEASH_DURATION) - 20, puppet, cow);
            assertNull(Strings.phase(caster), "over after 5 seconds");
            assertEquals(speed, caster.getAttributeValue(Attributes.MOVEMENT_SPEED), EPS, "no longer slowed");
            cow.discard();
        });
    }

    @Test
    void worksOnPlayers(MinecraftServer server) throws Exception {
        arena(server, "strings_players", (level, caster) -> {
            server.setPvpAllowed(true);
            ServerPlayer a = player(server, level, "strings_player_a", 12.5, Y, 0.5);
            ServerPlayer b = player(server, level, "strings_player_b", 0.5, Y, 12.5);
            try {
                // Reel In.
                press(caster, a, CastIntent.CAST_PRESS);
                InnateTechniqueService.clearCooldowns(caster);
                caster.setXRot(-60f);
                CastManager.handleIntent(caster, CastIntent.CAST_PRESS);
                run(server, 20);
                assertNull(Strings.phase(caster));
                assertTrue(a.distanceTo(caster) <= PuppetConfig.STRINGS_ARRIVE.get() + PuppetConfig.STRINGS_PULL_SPEED.get(), "a player is reeled in: " + a.distanceTo(caster));

                // Tie: collision damage to both, and the tether.
                InnateTechniqueService.clearCooldowns(caster);
                a.moveTo(12.5, Y, 0.5, 0f, 0f);
                float health = a.getHealth();
                press(caster, a, CastIntent.CAST_PRESS);
                InnateTechniqueService.clearCooldowns(caster);
                press(caster, b, CastIntent.CAST_PRESS);
                run(server, 12);
                assertEquals(Strings.Phase.TETHER, Strings.phase(caster), "two players met");
                assertEquals(health - PuppetConfig.STRINGS_TIE_DAMAGE.get(), a.getHealth(), 0.5, "collision damage");
                assertEquals(health - PuppetConfig.STRINGS_TIE_DAMAGE.get(), b.getHealth(), 0.5, "to both");
                b.moveTo(b.getX() + 8, Y, b.getZ(), 0f, 0f);
                run(server, 12);
                assertTrue(a.distanceTo(b) <= PuppetConfig.STRINGS_TETHER_DISTANCE.get() + 0.5, "tethered: " + a.distanceTo(b));
                Strings.cut(caster);

                // Leash: both held; a leashed player can still cast.
                InnateTechniqueService.clearCooldowns(caster);
                a.moveTo(12.5, Y, 0.5, 0f, 0f);
                b.moveTo(0.5, Y, 13.5, 0f, 0f);
                press(caster, a, CastIntent.CAST_PRESS);
                InnateTechniqueService.clearCooldowns(caster);
                press(caster, b, CastIntent.SPECIAL_PRESS);
                assertEquals(Strings.Phase.LEASH, Strings.phase(caster));
                run(server, 15);
                double limit = PuppetConfig.STRINGS_LEASH_DISTANCE.get() + 0.5;
                assertTrue(a.distanceTo(caster) <= limit && b.distanceTo(caster) <= limit, "both held: " + a.distanceTo(caster) + ", " + b.distanceTo(caster));
                PossessionTest.prepare(a);
                a.moveTo(4.5, Y, 0.5, 0f, 0f);
                InnateTechniqueService.select(a, 1); // Directive
                CastManager.handleIntent(a, CastIntent.CAST_PRESS);
                assertTrue(InnateTechniqueService.cooldownRemaining(a, PuppetManipulation.DIRECTIVE) > 0, "a leashed target can still cast");
                assertEquals(Strings.Phase.LEASH, Strings.phase(caster), "and is still leashed");
                Strings.cut(caster);

                // Zip to a player.
                InnateTechniqueService.clearCooldowns(caster);
                a.moveTo(12.5, Y, 0.5, 0f, 0f);
                press(caster, a, CastIntent.CAST_PRESS);
                InnateTechniqueService.clearCooldowns(caster);
                press(caster, a, CastIntent.SPECIAL_PRESS);
                run(server, 20);
                assertTrue(caster.distanceTo(a) <= PuppetConfig.STRINGS_ARRIVE.get() + PuppetConfig.STRINGS_PULL_SPEED.get(), "zipped to a player");
            } finally {
                PuppetTest.cleanUp(server, level, a);
                PuppetTest.cleanUp(server, level, b);
            }
        });
    }

    @Test
    void expiresAndSnaps(MinecraftServer server) throws Exception {
        arena(server, "strings_snap", (level, caster) -> {
            // Expires after 8 seconds, and only then does the full cooldown start.
            Cow cow = cow(level, 8.5, Y, 0.5);
            press(caster, cow, CastIntent.CAST_PRESS);
            run(server, PuppetConfig.ticks(PuppetConfig.STRINGS_FIRE_COOLDOWN) + 1);
            assertEquals(0, InnateTechniqueService.cooldownRemaining(caster, PuppetManipulation.STRINGS), "no cooldown while it holds");
            run(server, PuppetConfig.ticks(PuppetConfig.STRINGS_LIFE) - PuppetConfig.ticks(PuppetConfig.STRINGS_FIRE_COOLDOWN) - 3);
            assertTrue(Strings.isAttached(caster), "still holding just before 8 seconds");
            assertEquals(0, InnateTechniqueService.cooldownRemaining(caster, PuppetManipulation.STRINGS));
            run(server, 3);
            assertFalse(Strings.isAttached(caster), "expired");
            assertEquals(PuppetConfig.ticks(PuppetConfig.STRINGS_COOLDOWN), InnateTechniqueService.cooldownRemaining(caster, PuppetManipulation.STRINGS), 1, "the cooldown started");

            // Stretched beyond 32 blocks: it snaps, and the cooldown starts.
            InnateTechniqueService.clearCooldowns(caster);
            press(caster, cow, CastIntent.CAST_PRESS);
            InnateTechniqueService.clearCooldowns(caster);
            cow.moveTo(0.5, Y + PuppetConfig.STRINGS_SNAP_RANGE.get() - 1, 0.5, 0f, 0f);
            run(server, 1);
            assertTrue(Strings.isAttached(caster), "31 blocks: holds (further than it could be fired)");
            cow.moveTo(0.5, Y + PuppetConfig.STRINGS_SNAP_RANGE.get() + 1, 0.5, 0f, 0f);
            run(server, 1);
            assertFalse(Strings.isAttached(caster), "33 blocks: snapped");
            assertEquals(PuppetConfig.ticks(PuppetConfig.STRINGS_COOLDOWN), InnateTechniqueService.cooldownRemaining(caster, PuppetManipulation.STRINGS), "the cooldown started");
            cow.discard();
        });
    }

    @Test
    void bossesAreAttachedToButNeverMoved(MinecraftServer server) throws Exception {
        arena(server, "strings_boss", (level, caster) -> {
            WitherBoss boss = new WitherBoss(EntityType.WITHER, level);
            boss.setNoAi(true);
            boss.moveTo(12.5, Y, 0.5, 0f, 0f);
            level.addFreshEntity(boss);
            Vec3 at = boss.position();
            press(caster, boss, CastIntent.CAST_PRESS);
            assertTrue(Strings.isAttached(caster), "a boss can be attached to");
            InnateTechniqueService.clearCooldowns(caster);

            // Reel In: it won't budge. Nothing paid, no cooldown, and the string stays for a Zip.
            caster.setXRot(-60f);
            MaliceService.refill(caster);
            double before = PuppetTest.malice(caster);
            CastManager.handleIntent(caster, CastIntent.CAST_PRESS);
            assertEquals(before, PuppetTest.malice(caster), EPS, "nothing paid");
            assertEquals(0, InnateTechniqueService.cooldownRemaining(caster, PuppetManipulation.STRINGS));
            assertTrue(Strings.isAttached(caster), "the string stays");
            assertUnmoved(boss, at);

            // Zip: you go to it.
            CastManager.handleIntent(caster, CastIntent.SPECIAL_PRESS);
            assertEquals(Strings.Phase.ZIP, Strings.phase(caster));
            run(server, 20);
            assertTrue(caster.distanceTo(boss) <= PuppetConfig.STRINGS_ARRIVE.get() + PuppetConfig.STRINGS_PULL_SPEED.get(), "zipped to the boss");
            assertUnmoved(boss, at);
            assertNotPulled(boss);
            caster.moveTo(0.5, Y, 0.5, 0f, 0f);

            // Tie a mob to it: the mob goes to the boss. Both take the collision.
            InnateTechniqueService.clearCooldowns(caster);
            Cow cow = cow(level, 0.5, Y, 8.5);
            press(caster, boss, CastIntent.CAST_PRESS);
            InnateTechniqueService.clearCooldowns(caster);
            press(caster, cow, CastIntent.CAST_PRESS);
            assertEquals(Strings.Phase.TIE_PULL, Strings.phase(caster));
            float bossHealth = boss.getHealth();
            run(server, 2, cow);
            assertNotPulled(boss);
            run(server, 23, cow);
            assertEquals(Strings.Phase.TETHER, Strings.phase(caster), "the mob reached the boss: " + cow.distanceTo(boss));
            assertTrue(boss.getHealth() < bossHealth, "bosses still take the damage");
            assertUnmoved(boss, at);
            run(server, 10, cow);
            assertUnmoved(boss, at);
            Strings.cut(caster);

            // Leash a boss and a mob: only the mob is held.
            InnateTechniqueService.clearCooldowns(caster);
            cow.moveTo(0.5, Y, 13.5, 0f, 0f);
            boss.setDeltaMovement(Vec3.ZERO);
            press(caster, boss, CastIntent.CAST_PRESS);
            InnateTechniqueService.clearCooldowns(caster);
            press(caster, cow, CastIntent.SPECIAL_PRESS);
            assertEquals(Strings.Phase.LEASH, Strings.phase(caster));
            run(server, 20, cow);
            assertTrue(cow.distanceTo(caster) <= PuppetConfig.STRINGS_LEASH_DISTANCE.get() + 0.5, "the mob is held");
            assertUnmoved(boss, at);
            assertNotPulled(boss);
            Strings.cut(caster);

            // Two bosses: nothing to move, so Tie and Leash fizzle for free.
            InnateTechniqueService.clearCooldowns(caster);
            cow.discard();
            WitherBoss other = new WitherBoss(EntityType.WITHER, level);
            other.setNoAi(true);
            other.moveTo(0.5, Y, 12.5, 0f, 0f);
            level.addFreshEntity(other);
            press(caster, boss, CastIntent.CAST_PRESS);
            InnateTechniqueService.clearCooldowns(caster);
            PossessionTest.aimAt(caster, other);
            MaliceService.refill(caster);
            before = PuppetTest.malice(caster);
            CastManager.handleIntent(caster, CastIntent.CAST_PRESS);
            CastManager.handleIntent(caster, CastIntent.SPECIAL_PRESS);
            assertEquals(before, PuppetTest.malice(caster), EPS, "nothing paid");
            assertTrue(Strings.isAttached(caster), "the string stays");
            boss.discard();
            other.discard();
        });
    }

    @Test
    void aCancelledEventKeepsThatTargetInPlace(MinecraftServer server) throws Exception {
        if (!listening) {
            listening = true;
            NeoForge.EVENT_BUS.addListener(StringsTest::onTarget);
        }
        REFUSE.clear();
        SEEN.clear();
        arena(server, "strings_event", (level, caster) -> {
            try {
                // Reel In refused: nothing moves, the Malice stays spent, no cooldown, and the string is gone.
                Cow cow = cow(level, 12.5, Y, 0.5);
                press(caster, cow, CastIntent.CAST_PRESS);
                InnateTechniqueService.clearCooldowns(caster);
                REFUSE.add(cow);
                Vec3 at = cow.position();
                press(caster, cow, CastIntent.CAST_PRESS);
                assertEquals(1, SEEN.size(), "the event fired once");
                assertEquals(caster, SEEN.get(0).getCaster());
                assertEquals(cow, SEEN.get(0).getTarget());
                assertEquals(StringsVariation.REEL_IN, SEEN.get(0).getVariation());
                assertPaid(caster, PuppetConfig.STRINGS_REEL_COST.get());
                assertEquals(0, InnateTechniqueService.cooldownRemaining(caster, PuppetManipulation.STRINGS), "no cooldown");
                assertNull(Strings.phase(caster), "the string is gone");
                run(server, 10, cow);
                assertEquals(0.0, cow.position().distanceTo(at), 0.2, "not moved");

                // Zip refused: the caster stays.
                SEEN.clear();
                Vec3 casterAt = caster.position();
                press(caster, cow, CastIntent.CAST_PRESS);
                InnateTechniqueService.clearCooldowns(caster);
                press(caster, cow, CastIntent.SPECIAL_PRESS);
                assertEquals(StringsVariation.ZIP, SEEN.get(0).getVariation());
                run(server, 5);
                assertEquals(0.0, caster.position().distanceTo(casterAt), EPS, "the caster isn't moved");

                // Tie, one refused: it stays, the other is pulled to it. Each target gets its own event.
                SEEN.clear();
                InnateTechniqueService.clearCooldowns(caster);
                Cow free = cow(level, 0.5, Y, 8.5);
                press(caster, cow, CastIntent.CAST_PRESS);
                InnateTechniqueService.clearCooldowns(caster);
                press(caster, free, CastIntent.CAST_PRESS);
                assertEquals(2, SEEN.size(), "one event per target");
                assertEquals(StringsVariation.TIE, SEEN.get(0).getVariation());
                at = cow.position();
                run(server, 18, cow, free);
                assertEquals(Strings.Phase.TETHER, Strings.phase(caster), "the free one reached it");
                assertEquals(0.0, cow.position().distanceTo(at), 0.3, "the refused one isn't moved");
                Strings.cut(caster);

                // Leash, one refused: only the other is held.
                SEEN.clear();
                InnateTechniqueService.clearCooldowns(caster);
                free.moveTo(0.5, Y, 13.5, 0f, 0f);
                cow.moveTo(12.5, Y, 0.5, 0f, 0f);
                at = cow.position();
                press(caster, cow, CastIntent.CAST_PRESS);
                InnateTechniqueService.clearCooldowns(caster);
                press(caster, free, CastIntent.SPECIAL_PRESS);
                assertEquals(2, SEEN.size());
                assertEquals(StringsVariation.LEASH, SEEN.get(0).getVariation());
                run(server, 15, cow, free);
                assertTrue(free.distanceTo(caster) <= PuppetConfig.STRINGS_LEASH_DISTANCE.get() + 0.5, "held");
                assertEquals(0.0, cow.position().distanceTo(at), 0.3, "the refused one isn't moved");
                cow.discard();
                free.discard();
            } finally {
                REFUSE.clear();
            }
        });
    }

    @Test
    void endsWithAnyoneOnIt(MinecraftServer server) throws Exception {
        arena(server, "strings_ends", (level, caster) -> {
            ServerLevel nether = server.getLevel(Level.NETHER);
            // The target dies.
            Cow cow = cow(level, 8.5, Y, 0.5);
            press(caster, cow, CastIntent.CAST_PRESS);
            cow.kill();
            run(server, 1);
            assertNull(Strings.phase(caster), "target died");
            assertTrue(InnateTechniqueService.cooldownRemaining(caster, PuppetManipulation.STRINGS) > 0, "the cooldown starts");

            // The second target of a Leash goes away; a slowed caster recovers.
            InnateTechniqueService.clearCooldowns(caster);
            double speed = caster.getAttributeValue(Attributes.MOVEMENT_SPEED);
            Cow a = cow(level, 8.5, Y, 0.5);
            Cow b = cow(level, 0.5, Y, 8.5);
            press(caster, a, CastIntent.CAST_PRESS);
            InnateTechniqueService.clearCooldowns(caster);
            press(caster, b, CastIntent.SPECIAL_PRESS);
            assertEquals(Strings.Phase.LEASH, Strings.phase(caster));
            b.discard();
            run(server, 1);
            assertNull(Strings.phase(caster), "a target left");
            assertEquals(speed, caster.getAttributeValue(Attributes.MOVEMENT_SPEED), EPS, "no longer slowed");

            // A target player logs out (removed from the level), and changes dimension.
            InnateTechniqueService.clearCooldowns(caster);
            ServerPlayer p = player(server, level, "strings_target", 8.5, Y, 2.5);
            try {
                press(caster, p, CastIntent.CAST_PRESS);
                p.setServerLevel(nether);
                run(server, 1);
                assertNull(Strings.phase(caster), "the target changed dimension");
                p.setServerLevel(level);
            } finally {
                level.removePlayerImmediately(p, Entity.RemovalReason.DISCARDED);
            }
            InnateTechniqueService.clearCooldowns(caster);
            ServerPlayer gone = player(server, level, "strings_target2", 8.5, Y, 2.5);
            press(caster, gone, CastIntent.CAST_PRESS);
            level.removePlayerImmediately(gone, Entity.RemovalReason.DISCARDED);
            run(server, 1);
            assertNull(Strings.phase(caster), "the target logged out");

            // The caster changes dimension.
            InnateTechniqueService.clearCooldowns(caster);
            press(caster, a, CastIntent.CAST_PRESS);
            caster.setServerLevel(nether);
            run(server, 1);
            assertNull(Strings.phase(caster), "the caster changed dimension");
            caster.setServerLevel(level);

            // The caster logs out, or possesses a puppet, or rerolls away.
            InnateTechniqueService.clearCooldowns(caster);
            press(caster, a, CastIntent.CAST_PRESS);
            NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent(caster));
            assertNull(Strings.phase(caster), "the caster logged out");

            InnateTechniqueService.clearCooldowns(caster);
            InnateTechniqueService.select(caster, 0);
            caster.setYRot(-90f);
            PuppetEntity puppet = PossessionTest.puppetInFront(server, caster, 0);
            InnateTechniqueService.select(caster, 4);
            press(caster, a, CastIntent.CAST_PRESS);
            InnateTechniqueService.select(caster, 0);
            Possession.possess(caster, puppet);
            assertNull(Strings.phase(caster), "possessing cuts it");
            Possession.release(caster, Possession.Return.RETURNED);

            InnateTechniqueService.clearCooldowns(caster);
            InnateTechniqueService.select(caster, 4);
            press(caster, a, CastIntent.CAST_PRESS);
            InnateTechniqueService.setInnateTechnique(caster, null);
            assertNull(Strings.phase(caster), "rerolling away cuts it");
            InnateTechniqueService.setInnateTechnique(caster, PuppetManipulation.PUPPET_MANIPULATION.get());
            InnateTechniqueService.select(caster, 4);

            // The caster dies.
            InnateTechniqueService.clearCooldowns(caster);
            press(caster, a, CastIntent.CAST_PRESS);
            caster.setHealth(0f);
            run(server, 1);
            assertNull(Strings.phase(caster), "the caster died");
            caster.setHealth(20f);
            a.discard();
        });
    }

    // ---------------------------------------------------------------- helpers

    @FunctionalInterface
    private interface Scenario {
        void run(ServerLevel level, ServerPlayer caster);
    }

    private static void arena(MinecraftServer server, String name, Scenario scenario) throws Exception {
        TestServer.onServerThread(server, () -> {
            ServerLevel level = PossessionTest.setUp(server);
            sweep(level);
            ServerPlayer caster = PossessionTest.damageable(server, name);
            level.addNewPlayer(caster);
            PossessionTest.prepare(caster);
            InnateTechniqueService.select(caster, 4);
            try {
                scenario.run(level, caster);
            } finally {
                Strings.cut(caster);
                PossessionTest.cleanUp(server, level, caster);
                sweep(level);
            }
        });
    }

    /** No mob outlives a test here (a failing one leaves its cows hovering in the next one's line of sight). */
    private static void sweep(ServerLevel level) {
        for (Entity e : level.getEntitiesOfClass(Entity.class, new net.minecraft.world.phys.AABB(-64, -64, -64, 64, 400, 64), e -> !(e instanceof ServerPlayer))) e.discard();
    }

    /** A puppet of the caster's, hovering at (x, Y, z); the caster faces +x first, so it's made inside the arena's chunk. */
    private static PuppetEntity puppet(MinecraftServer server, ServerPlayer caster, double x, double z) {
        InnateTechniqueService.clearCooldowns(caster);
        InnateTechniqueService.select(caster, 0);
        caster.setYRot(-90f);
        PuppetEntity puppet = PuppetTest.create(server, caster, 0);
        InnateTechniqueService.clearCooldowns(caster);
        InnateTechniqueService.select(caster, 4);
        puppet.setNoGravity(true);
        puppet.moveTo(x, Y, z, 0f, 0f);
        return puppet;
    }

    private static Cow cow(ServerLevel level, double x, double y, double z) {
        Cow cow = new Cow(EntityType.COW, level);
        cow.setNoGravity(true);
        cow.moveTo(x, y, z, 0f, 0f);
        level.addFreshEntity(cow);
        return cow;
    }

    private static ServerPlayer player(MinecraftServer server, ServerLevel level, String name, double x, double y, double z) {
        ServerPlayer p = PossessionTest.damageable(server, name);
        level.addNewPlayer(p);
        p.moveTo(x, y, z, 0f, 0f);
        return p;
    }

    /** Aims at {@code target}, tops up the Malice (so what's paid can be asserted) and presses. */
    private static void press(ServerPlayer caster, LivingEntity target, CastIntent intent) {
        PossessionTest.aimAt(caster, target);
        MaliceService.refill(caster);
        CastManager.handleIntent(caster, intent);
    }

    private static void assertFreeFizzle(ServerPlayer caster, CastIntent intent) {
        double before = PuppetTest.malice(caster);
        CastManager.handleIntent(caster, intent);
        assertEquals(before, PuppetTest.malice(caster), EPS, "nothing paid");
        assertEquals(0, InnateTechniqueService.cooldownRemaining(caster, PuppetManipulation.STRINGS), "no cooldown");
        assertNull(Strings.phase(caster), "no string");
    }

    /** The caster was topped up, so what's missing is what the cast cost. */
    private static void assertPaid(ServerPlayer caster, double cost) {
        assertEquals(MaliceService.get(caster).capacity() - cost, PuppetTest.malice(caster), EPS, "paid");
    }

    private static void assertUnmoved(LivingEntity e, Vec3 at) {
        assertEquals(0.0, e.position().distanceTo(at), EPS, "not moved");
    }

    /** Nothing has told it to move (a mob pressed right up against it does nudge it: that's vanilla, not a pull). */
    private static void assertNotPulled(LivingEntity e) {
        assertEquals(0.0, e.getDeltaMovement().length(), EPS, "not pulled");
    }

    /** Advances the world {@code ticks} ticks: the mobs in {@code mobs} move, and the strings run. */
    private static void run(MinecraftServer server, int ticks, LivingEntity... mobs) {
        for (int i = 0; i < ticks; i++) {
            TestServer.advanceGameTime(server, 1);
            for (LivingEntity mob : mobs) mob.tick();
            Strings.tick(server);
        }
    }
}

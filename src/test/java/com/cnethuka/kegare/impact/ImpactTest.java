package com.cnethuka.kegare.impact;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cnethuka.kegare.TestServer;
import com.cnethuka.kegare.innate_technique.rime.RimeDamage;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/** The Impact System's strength rule (DESIGN §3.23), and real hits resolving into impacts on a throwaway server. */
@ExtendWith(EphemeralTestServerProvider.class)
class ImpactTest {
    /** The shipped defaults, so these read the rule as it ships whatever a test server's file holds. On a 20-health player. */
    private static ImpactTier tier(double damage, double knockback, double launch, double charge, double malice) {
        return ImpactStrength.tier(strength(damage, knockback, launch, charge, malice), ImpactConfig.MEDIUM.getDefault(),
                ImpactConfig.HEAVY.getDefault(), ImpactConfig.IMPACT_FRAME.getDefault());
    }

    private static double strength(double damage, double knockback, double launch, double charge, double malice) {
        return ImpactStrength.of(damage / 20.0, knockback, launch, charge, malice, ImpactConfig.DAMAGE_WEIGHT.getDefault(),
                ImpactConfig.KNOCKBACK_WEIGHT.getDefault(), ImpactConfig.BASE_KNOCKBACK.getDefault(), ImpactConfig.LAUNCH_WEIGHT.getDefault(),
                ImpactConfig.CHARGE_WEIGHT.getDefault(), ImpactConfig.MALICE_WEIGHT.getDefault());
    }

    private static double stack(double... pushes) {
        double k = 0;
        for (double push : pushes) k = ImpactStrength.stack(k, push);
        return k;
    }

    /** What the rule must produce (DESIGN §3.23): the shipped weights put each of these in its tier from what it did, no minimums. */
    @Test
    void whatHitsDoMapsOntoTheTiers() {
        // Vanilla gives every hit a 0.4 knockback; a sprint adds 0.5; a Strike 0.6 before it (a Severed player's 0.9).
        assertEquals(ImpactTier.LIGHT, tier(1, 0.4, 0, 0, 0), "a punch stays Light");
        assertEquals(ImpactTier.MEDIUM, tier(0, 1.4, 0, 0, 88), "Shatter Shell's push with no shell up (88 Malice) is Medium");
        assertEquals(ImpactTier.MEDIUM, tier(0, 1.4, 0, 0, 32), "and with the shell up (32)");
        assertEquals(ImpactTier.HEAVY, tier(9, stack(0.6, 0.4), 0, 1, 0), "an iron-sword Reinforced Strike is Heavy (no cast behind it)");
        assertEquals(ImpactTier.HEAVY, tier(12, 0.4, 0, 1, 100), "a full Glacial Lance is Heavy");
        assertEquals(ImpactTier.IMPACT_FRAME, tier(6, 0.4, 1.2, 1, 120), "a full Glacial Spire reaches the impact frame");
        assertEquals(ImpactTier.IMPACT_FRAME, tier(10.4, 0.54, 0, 1, 32 + 180), "so does a Full Curio Burst a block away (its cast and its puppet)");
        assertEquals(ImpactTier.IMPACT_FRAME, tier(0, 0, 0, 1, 240), "and a Total Swap, even between two at the same health");
        assertNotEquals(ImpactTier.TOP, tier(1000, 50, 50, 1, 100_000), "the top tier is never reached by strength: it's Black Spark's to ask for");
        // Ordinary hits and a Frost Shard stay in the low tiers.
        assertEquals(ImpactTier.LIGHT, tier(6, 0.4, 0, 0, 0), "an iron sword's normal hit");
        assertEquals(ImpactTier.MEDIUM, tier(6, stack(0.4, 0.5), 0, 0, 0), "a sprinting sword hit");
        assertEquals(ImpactTier.MEDIUM, tier(9, 0.4, 0, 0, 0), "a critical one");
        assertEquals(ImpactTier.LIGHT, tier(3, 0.4, 0, 0, 40), "a Frost Shard");
    }

    /**
     * A Severed player has no Malice, so that input is always 0 for them, and no Reinforced Strike; their hits still reach the same tiers
     * through damage and knockback, and Closing Line's momentum hit through the rush it carries (DESIGN §3.27): its speed as damage and
     * knockback, and as its charge. An iron-sword one reaches the impact frame with no crit.
     */
    @Test
    void severedHitsReachTheSameTiersWithoutMalice() {
        double strength = com.cnethuka.kegare.severed.SeveredConfig.MELEE_DAMAGE.getDefault();
        double speed = com.cnethuka.kegare.severed.SeveredConfig.CLOSING_SPEED.getDefault();
        double momentum = com.cnethuka.kegare.severed.SeveredConfig.MOMENTUM_DAMAGE.getDefault() * speed;
        double carried = com.cnethuka.kegare.severed.SeveredConfig.CLOSING_CARRY.getDefault() * speed;
        assertEquals(ImpactTier.LIGHT, tier(1 * strength, 0.4, 0, 0, 0), "a punch");
        assertEquals(ImpactTier.MEDIUM, tier(8 * strength, stack(0.4, 0.5), 0, 0, 0), "a sprinting netherite sword hit");
        assertEquals(ImpactTier.HEAVY, tier(1 * strength + momentum, stack(carried, 0.4), 0, 1, 0), "a bare-fisted momentum hit");
        assertEquals(ImpactTier.IMPACT_FRAME, tier(6 * strength + momentum, stack(carried, 0.4), 0, 1, 0), "an iron-sword one, with no crit");
        assertEquals(ImpactTier.IMPACT_FRAME, tier(8 * strength + momentum, stack(carried, 0.4), 0, 1, 0), "a netherite one");
        assertTrue(strength(6 * strength + momentum, stack(carried, 0.4), 0, 1, 0) > ImpactConfig.IMPACT_FRAME.getDefault() + 0.05,
                "with room to spare: " + strength(6 * strength + momentum, stack(carried, 0.4), 0, 1, 0));
    }

    @Test
    void onlyBigHitsHaveASoundOfTheirOwn() {
        assertFalse(ImpactSounds.booms(ImpactTier.LIGHT), "a plain hit sounds like vanilla only");
        assertFalse(ImpactSounds.booms(ImpactTier.MEDIUM));
        for (ImpactTier tier : ImpactTier.values()) {
            if (tier.ordinal() >= ImpactTier.HEAVY.ordinal()) assertTrue(ImpactSounds.booms(tier), tier + ": the boom under it");
        }
    }

    @Test
    void stackingKnockbackIsVanillas() {
        assertEquals(0.4, stack(0.4), 1e-9);
        assertEquals(0.7, stack(0.4, 0.5), 1e-9, "each push halves what was there");
    }

    private static ServerPlayer player(MinecraftServer server, String name, double x) {
        TestServer.ensureOverworld(server);
        ServerLevel level = server.overworld();
        ServerPlayer p = TestServer.damageable(server, name);
        p.moveTo(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING, 8, 8), 8.5, -90f, 0f);
        p.setHealth(p.getMaxHealth());
        return p;
    }

    @Test
    void realHitsResolveIntoOneImpactEach(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> realHits(server));
    }

    private static void realHits(MinecraftServer server) {
        ServerPlayer a = player(server, "impact_attacker", 2.5), b = player(server, "impact_victim", 4.0), c = player(server, "impact_bystander", 7.0);
        List<Impact> seen = new ArrayList<>();
        Consumer<Impact> listener = seen::add;
        Impacts.listen(listener);
        try {
            Impacts.clear();
            a.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_SWORD));
            b.hurt(a.damageSources().playerAttack(a), 6f);
            Impacts.flush();
            assertEquals(1, seen.size(), "one hit, one impact");
            Impact hit = seen.get(0);
            assertEquals(ImpactTier.LIGHT, hit.tier());
            assertEquals(ImpactKind.MELEE, hit.kind(), "a body or a weapon: white");
            assertSame(a, hit.attacker());
            assertSame(b, hit.victim());
            assertTrue(b.getBoundingBox().inflate(1.0e-3).contains(hit.contact()), "it meets the victim's body");
            assertTrue(hit.breaksBlocks());
            assertTrue(hit.melee(), "struck by the attacker's own hand");
            assertTrue(hit.weapon().is(net.minecraft.world.item.Items.IRON_SWORD), "with what was in it");
            a.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, net.minecraft.world.item.ItemStack.EMPTY);

            seen.clear();
            b.invulnerableTime = 0;
            b.hurt(RimeDamage.source(server.overworld(), null, a), 12f);
            Impacts.charge(b, 1.0);
            Impacts.flush();
            assertEquals(1, seen.size());
            assertEquals(ImpactKind.TECHNIQUE, seen.get(0).kind(), "a technique's damage flashes in its Malice color");
            assertEquals(0, seen.get(0).malice(), 1e-9, "no cast traced: no Malice");
            assertEquals(ImpactTier.MEDIUM, seen.get(0).tier(), "12 damage at full charge, with no Malice behind it");

            // Inside a paid cast (a Glacial Lance at its base cost of 100): that cast's base cost goes in, whatever the surroundings made
            // it cost.
            seen.clear();
            b.invulnerableTime = 0;
            com.cnethuka.kegare.innatetechnique.InnateTechniqueService.setInnateTechnique(a, com.cnethuka.kegare.innate_technique.rime.Rime.RIME.get());
            var lance = com.cnethuka.kegare.innate_technique.rime.Rime.GLACIAL_LANCE;
            double multiplier = com.cnethuka.kegare.innatetechnique.InnateTechniqueService.current(a).orElseThrow().environment(a).multiplier();
            CastMalice.paid(a, lance, 100 * multiplier);
            com.cnethuka.kegare.progression.CastScope.run(a, lance, () -> b.hurt(RimeDamage.source(server.overworld(), null, a), 12f));
            Impacts.charge(b, 1.0); // a real lance carries its charge on itself
            Impacts.flush();
            assertEquals(1, seen.size());
            assertEquals(100, seen.get(0).malice(), 1e-6, "its base cost");
            assertEquals(ImpactTier.HEAVY, seen.get(0).tier(), "the same hit as a full Glacial Lance's");
            CastMalice.clear();

            seen.clear();
            Impacts.charge(c, 1.0); // a hint with no hit behind it
            Impacts.flush();
            assertTrue(seen.isEmpty(), "nothing landed, so nothing plays");

            seen.clear();
            Impacts.hit(a, c, ImpactKind.TECHNIQUE, 0, 0, 0, false);
            Impacts.flush();
            assertEquals(1, seen.size(), "a hit with no damage (a swap) still lands");
            assertFalse(seen.get(0).breaksBlocks(), "and Swap never breaks the world");
        } finally {
            Impacts.remove(listener);
            Impacts.clear();
            TestServer.removePlayers(server);
        }
    }
}

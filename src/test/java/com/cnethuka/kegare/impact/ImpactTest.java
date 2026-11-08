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
    /** The shipped defaults, so these read the rule as it ships whatever a test server's file holds. */
    private static ImpactTier tier(double damage, double knockback, double launch, double charge) {
        double strength = ImpactStrength.of(damage / 20.0, knockback, launch, charge, ImpactConfig.DAMAGE_WEIGHT.getDefault(),
                ImpactConfig.KNOCKBACK_WEIGHT.getDefault(), ImpactConfig.BASE_KNOCKBACK.getDefault(), ImpactConfig.LAUNCH_WEIGHT.getDefault(),
                ImpactConfig.CHARGE_WEIGHT.getDefault());
        return ImpactStrength.tier(strength, ImpactConfig.MEDIUM.getDefault(), ImpactConfig.HEAVY.getDefault(), ImpactConfig.IMPACT_FRAME.getDefault());
    }

    private static double stack(double... pushes) {
        double k = 0;
        for (double push : pushes) k = ImpactStrength.stack(k, push);
        return k;
    }

    @Test
    void whatHitsDoMapsOntoTheTiers() {
        // On a 20-health player. Vanilla gives every hit a 0.4 knockback; a sprint adds 0.5; a Strike 0.6 before it.
        assertEquals(ImpactTier.LIGHT, tier(1, 0.4, 0, 0), "a punch barely registers");
        assertEquals(ImpactTier.LIGHT, tier(6, 0.4, 0, 0), "an iron sword's normal hit is a normal hit");
        assertEquals(ImpactTier.LIGHT, tier(3, 0.4, 0, 0), "a Frost Shard");
        assertEquals(ImpactTier.MEDIUM, tier(6, stack(0.4, 0.5), 0, 0), "a sprinting sword hit is a solid one");
        assertEquals(ImpactTier.MEDIUM, tier(9, 0.4, 0, 0), "so is a critical one");
        assertEquals(ImpactTier.HEAVY, tier(1.5, stack(0.6, 0.4), 0, 1), "a Reinforced Strike, even with a fist");
        assertEquals(ImpactTier.HEAVY, tier(9, stack(0.6, 0.4), 0, 1), "a Reinforced Strike with an iron sword");
        assertEquals(ImpactTier.HEAVY, tier(12, 0.4, 0, 1), "a full Glacial Lance");
        assertEquals(ImpactTier.MEDIUM, tier(6, 0.4, 0.6, 0), "a Glacial Spire at its least");
        assertEquals(ImpactTier.IMPACT_FRAME, tier(6, 0.4, 1.2, 1), "a full Glacial Spire");
        assertEquals(ImpactTier.IMPACT_FRAME, tier(12, stack(0.4, 0.7), 0, 1), "a Full Curio Burst at its centre");
        assertEquals(ImpactTier.LIGHT, tier(4, stack(0.4, 0.1), 0, 0), "a Tap Curio Burst at its edge");
        assertEquals(ImpactTier.MEDIUM, tier(0, 1.4, 0, 0), "Shatter Shell's push");
        assertEquals(ImpactTier.IMPACT_FRAME, tier(28, 0, 0, 1), "Total Swap trading 4/20 and 18/20 (it moves 14 each way)");
        assertEquals(ImpactTier.MEDIUM, tier(0, 0, 0, 1), "Total Swap between two at the same health");
        assertNotEquals(ImpactTier.TOP, tier(1000, 50, 50, 1), "the top tier is never reached by strength: it's Black Spark's to ask for");
    }

    @Test
    void aMeleeHitSoundsLikeWhatItWasHitWith(MinecraftServer server) { // the server binds the item tags
        assertSame(com.cnethuka.kegare.sound.KegareSounds.IMPACT_PUNCH.value(), ImpactSounds.weaponSound(net.minecraft.world.item.ItemStack.EMPTY), "a fist");
        assertSame(com.cnethuka.kegare.sound.KegareSounds.IMPACT_BLADE.value(),
                ImpactSounds.weaponSound(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_SWORD)), "a sword");
        assertSame(com.cnethuka.kegare.sound.KegareSounds.IMPACT_HEAVY.value(),
                ImpactSounds.weaponSound(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND_AXE)), "an axe");
        assertSame(com.cnethuka.kegare.sound.KegareSounds.IMPACT_HEAVY.value(),
                ImpactSounds.weaponSound(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.MACE)), "a mace");
        assertSame(com.cnethuka.kegare.sound.KegareSounds.IMPACT_PUNCH.value(),
                ImpactSounds.weaponSound(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STICK)), "anything else lands like a fist");
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
            assertEquals(ImpactTier.HEAVY, seen.get(0).tier(), "12 damage at full charge");

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

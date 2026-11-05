package com.cnethuka.kegare.rmt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cnethuka.kegare.TestServer;
import com.cnethuka.kegare.innate_technique.rime.Rime;
import com.cnethuka.kegare.innatetechnique.InnateTechniqueService;
import com.cnethuka.kegare.loadout.SharedAbilityService;
import com.cnethuka.kegare.player.BirthService;
import java.util.OptionalInt;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/** The green orb: who rolls and when, where it appears, the server-side look check, the unlock and its healing. */
@ExtendWith(EphemeralTestServerProvider.class)
class GreenOrbTest {
    @Test
    void greenOrb(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            TestServer.ensureOverworld(server);
            ServerPlayer p = born(server, "orb_finder");
            int lookTicks = RmtConfig.ticks(RmtConfig.ORB_LOOK_SECONDS);

            // Above the health threshold: no roll.
            p.setHealth(15f);
            GreenOrbService.onHitByPlayer(p);
            assertEquals(Long.MIN_VALUE, SharedAbilityService.get(p).lastOrbRoll(), "healthy enough: no roll");
            // At or below it: a roll (chance decides the orb), then none until the cooldown passes.
            p.setHealth(9f);
            GreenOrbService.state(p).orb = null;
            GreenOrbService.onHitByPlayer(p);
            long rolled = SharedAbilityService.get(p).lastOrbRoll();
            assertEquals(p.level().getGameTime(), rolled, "rolled");
            GreenOrbService.state(p).orb = null;
            TestServer.advanceGameTime(server, 20);
            GreenOrbService.onHitByPlayer(p);
            assertEquals(rolled, SharedAbilityService.get(p).lastOrbRoll(), "one roll per cooldown");
            TestServer.advanceGameTime(server, RmtConfig.ticks(RmtConfig.ORB_ROLL_COOLDOWN));
            GreenOrbService.onHitByPlayer(p);
            assertTrue(SharedAbilityService.get(p).lastOrbRoll() > rolled, "rolls again after the cooldown");

            // Forced orb: a few blocks in front, near eye level.
            p.setXRot(0f);
            p.setYRot(-90f); // facing +X
            assertEquals(GreenOrbService.ForceResult.SPAWNED, GreenOrbService.force(p));
            Vec3 orb = GreenOrbService.state(p).orb();
            assertNotNull(orb);
            assertEquals(RmtConfig.ORB_DISTANCE.get(), orb.distanceTo(p.getEyePosition()), 0.05, "at the configured distance");

            // Looking away resets the count; a full second looking at it takes it in.
            p.lookAt(EntityAnchorArgument.Anchor.EYES, orb);
            for (int i = 0; i < lookTicks - 1; i++) GreenOrbService.tick(p);
            p.lookAt(EntityAnchorArgument.Anchor.EYES, orb.add(0, 0, 5)); // away
            GreenOrbService.tick(p);
            p.lookAt(EntityAnchorArgument.Anchor.EYES, orb);
            for (int i = 0; i < lookTicks - 1; i++) GreenOrbService.tick(p);
            assertFalse(SharedAbilityService.isUnlocked(p, RmtIds.ABILITY), "not yet: the look was broken");
            p.setHealth(9f);
            GreenOrbService.tick(p);
            assertTrue(SharedAbilityService.isUnlocked(p, RmtIds.ABILITY), "taken in");
            assertEquals(9f + RmtConfig.ORB_UNLOCK_HEAL.get(), p.getHealth(), 1e-4, "real healing");
            assertFalse(GreenOrbService.state(p).hasOrb());
            // Never again.
            assertEquals(GreenOrbService.ForceResult.ALREADY_UNLOCKED, GreenOrbService.force(p));
            assertFalse(GreenOrbService.eligible(p));

            // The heal never goes above max health.
            ServerPlayer q = born(server, "orb_nearly_full");
            q.setHealth(q.getMaxHealth() - 1f);
            GreenOrbService.force(q);
            q.lookAt(EntityAnchorArgument.Anchor.EYES, GreenOrbService.state(q).orb());
            for (int i = 0; i < lookTicks; i++) GreenOrbService.tick(q);
            assertEquals(q.getMaxHealth(), q.getHealth(), 1e-4);

            // It fades on time.
            ServerPlayer r = born(server, "orb_fades");
            GreenOrbService.force(r);
            TestServer.advanceGameTime(server, RmtConfig.ticks(RmtConfig.ORB_LIFETIME));
            GreenOrbService.tick(r);
            assertFalse(GreenOrbService.state(r).hasOrb(), "faded");

            // Severed players never get it, forced or rolled.
            ServerPlayer severed = TestServer.player(server, "orb_severed");
            BirthService.rebirth(severed, OptionalInt.of(0));
            assertEquals(GreenOrbService.ForceResult.SEVERED, GreenOrbService.force(severed));
            severed.setHealth(4f);
            GreenOrbService.onHitByPlayer(severed);
            assertEquals(Long.MIN_VALUE, SharedAbilityService.get(severed).lastOrbRoll(), "no roll for Severed");
            assertFalse(GreenOrbService.state(severed).hasOrb());
        });
    }

    private static ServerPlayer born(MinecraftServer server, String name) {
        ServerPlayer p = TestServer.player(server, name);
        BirthService.rebirth(p, OptionalInt.of(500));
        InnateTechniqueService.setInnateTechnique(p, Rime.RIME.get());
        p.moveTo(0.5, 100, 0.5, -90f, 0f); // open air: nothing between the eyes and the orb
        return p;
    }
}

package com.cnethuka.kegare.innate_technique.puppet_manipulation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cnethuka.kegare.TestServer;
import com.cnethuka.kegare.innate_technique.swap.SwapTargetEvent;
import com.cnethuka.kegare.innatetechnique.CastIntent;
import com.cnethuka.kegare.innatetechnique.CastManager;
import com.cnethuka.kegare.innatetechnique.InnateTechniqueService;
import com.cnethuka.kegare.malice.MaliceService;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/** Puppet Manipulation, part 4: Understudy (through SwapTargetEvent) and Stand-In. */
@ExtendWith(EphemeralTestServerProvider.class)
class UnderstudyTest {
    private static final double EPS = 1.0e-3;

    @Test
    void understudy(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            ServerLevel level = PossessionTest.setUp(server);
            int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING, 0, 0);
            ServerPlayer owner = PossessionTest.damageable(server, "understudy_owner");
            level.addNewPlayer(owner);
            PossessionTest.prepare(owner);
            owner.moveTo(0.5, ground, 0.5, -90f, 0f);
            List<SwapTargetEvent> seen = new ArrayList<>();
            boolean[] cancel = {false};
            Consumer<SwapTargetEvent> listener = e -> {
                seen.add(e);
                if (cancel[0]) e.setCanceled(true);
            };
            NeoForge.EVENT_BUS.addListener(listener);
            try {
                PuppetEntity puppet = PossessionTest.puppetInFront(server, owner, 0);
                InnateTechniqueService.select(owner, 3);
                Vec3 ownerAt = owner.position();
                Vec3 puppetAt = puppet.position();
                double before = PuppetTest.malice(owner);
                CastManager.handleIntent(owner, CastIntent.CAST_PRESS);
                assertEquals(1, seen.size(), "SwapTargetEvent fired");
                assertSame(puppet, seen.get(0).getTarget(), "with the puppet as the target");
                assertSame(PuppetManipulation.UNDERSTUDY, seen.get(0).getAbility());
                assertEquals(puppetAt.x, owner.getX(), EPS, "the owner is where the puppet was");
                assertEquals(ownerAt.x, puppet.getX(), EPS, "and the puppet where the owner was");
                assertEquals(before - PuppetConfig.UNDERSTUDY_COST.get(), PuppetTest.malice(owner), EPS);

                // A deflection blocks it: nobody moves, the Malice stays spent, no cooldown.
                InnateTechniqueService.clearCooldowns(owner);
                MaliceService.refill(owner);
                PossessionTest.aimAt(owner, puppet);
                cancel[0] = true;
                Vec3 here = owner.position();
                before = PuppetTest.malice(owner);
                CastManager.handleIntent(owner, CastIntent.CAST_PRESS);
                assertEquals(here.x, owner.getX(), EPS, "blocked: nobody moved");
                assertEquals(before - PuppetConfig.UNDERSTUDY_COST.get(), PuppetTest.malice(owner), EPS, "the Malice stays spent");
                assertEquals(0, InnateTechniqueService.cooldownRemaining(owner, PuppetManipulation.UNDERSTUDY), "no cooldown");
            } finally {
                NeoForge.EVENT_BUS.unregister(listener);
                PossessionTest.cleanUp(server, level, owner);
            }
        });
    }

    @Test
    void standIn(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            ServerLevel level = PossessionTest.setUp(server);
            int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING, 0, 0);
            ServerPlayer owner = PossessionTest.damageable(server, "stand_in_owner");
            level.addNewPlayer(owner);
            PossessionTest.prepare(owner);
            owner.moveTo(6.5, ground, 6.5, -90f, 0f); // well inside the loaded test chunk, whichever way puppets appear
            owner.setYHeadRot(-90f);
            try {
                owner.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.DIAMOND_SWORD));
                owner.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.GOLDEN_HELMET));
                PuppetEntity puppet = PossessionTest.puppetInFront(server, owner, 0);
                InnateTechniqueService.select(owner, 3);
                double before = PuppetTest.malice(owner);
                CastManager.handleIntent(owner, CastIntent.SPECIAL_PRESS);
                assertTrue(puppet.isStandIn(), "standing in");
                assertEquals(before - PuppetConfig.STAND_IN_COST.get(), PuppetTest.malice(owner), EPS);
                assertTrue(puppet.getMainHandItem().is(Items.DIAMOND_SWORD), "the owner's sword (a copy)");
                assertTrue(puppet.getItemBySlot(EquipmentSlot.HEAD).is(Items.GOLDEN_HELMET), "and helmet");
                assertTrue(owner.getMainHandItem().is(Items.DIAMOND_SWORD), "the owner keeps theirs");
                assertNotNull(puppet.getCustomName());
                assertEquals(owner.getName().getString(), puppet.getCustomName().getString(), "the owner's name");
                assertTrue(puppet.isCustomNameVisible());

                // It grows to full size.
                int grow = PuppetConfig.ticks(PuppetConfig.STAND_IN_GROW);
                for (int i = 0; i < grow + 1; i++) DirectiveTest.tick(server, puppet);
                assertEquals(1.0, puppet.getAttributeValue(Attributes.SCALE), EPS, "full size");
                assertEquals(0.6, puppet.getBbWidth(), EPS, "a player's hitbox");

                // It copies the owner's moves and look.
                Vec3 from = puppet.position();
                owner.setOldPosAndRot();
                owner.moveTo(owner.getX(), ground, owner.getZ() + 1.0, 30f, 10f);
                owner.setYHeadRot(30f);
                owner.swing(InteractionHand.MAIN_HAND);
                DirectiveTest.tick(server, puppet);
                assertEquals(from.z + 1.0, puppet.getZ(), 0.05, "moved the same step");
                assertEquals(30f, puppet.getYRot(), EPS, "faces where the owner faces");
                assertTrue(puppet.swinging, "swings when the owner swings");
                assertFalse(puppet.attack(owner), "never attacks");
                owner.setShiftKeyDown(true);
                owner.setPose(net.minecraft.world.entity.Pose.CROUCHING);
                owner.setOldPosAndRot();
                DirectiveTest.tick(server, puppet);
                assertTrue(puppet.isCrouching(), "crouches with them");
                owner.setShiftKeyDown(false);
                owner.setPose(net.minecraft.world.entity.Pose.STANDING);

                // Killed mid Stand-In: nothing it borrowed drops.
                InnateTechniqueService.select(owner, 0);
                InnateTechniqueService.clearCooldowns(owner);
                PuppetEntity second = PuppetTest.create(server, owner, 0);
                InnateTechniqueService.clearCooldowns(owner);
                second.startStandIn(owner, 100);
                DirectiveTest.tick(server, second);
                second.hurt(level.damageSources().genericKill(), 1000f);
                second.tick();
                assertTrue(level.getEntitiesOfClass(ItemEntity.class, owner.getBoundingBox().inflate(8)).isEmpty(), "no drops");

                // Then it shrinks back and gives everything back.
                for (int i = 0; i < PuppetConfig.ticks(PuppetConfig.STAND_IN_DURATION); i++) DirectiveTest.tick(server, puppet);
                assertFalse(puppet.isStandIn(), "over");
                assertEquals(PuppetConfig.SCALE.get(), puppet.getAttributeValue(Attributes.SCALE), EPS, "half size again");
                assertTrue(puppet.getMainHandItem().isEmpty(), "nothing held");
                assertTrue(puppet.getItemBySlot(EquipmentSlot.HEAD).isEmpty(), "no helmet");
                assertNull(puppet.getCustomName(), "no name");
            } finally {
                PossessionTest.cleanUp(server, level, owner);
            }
        });
    }
}

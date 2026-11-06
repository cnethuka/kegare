package com.cnethuka.kegare.innate_technique.puppet_manipulation;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.cnethuka.kegare.TestServer;
import com.cnethuka.kegare.innatetechnique.InnateTechniqueService;
import com.cnethuka.kegare.player.BirthService;
import java.util.OptionalInt;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/** Directive's Go aims at the ground like Glacial Spire: a look along flat ground at a target has to count. */
@ExtendWith(EphemeralTestServerProvider.class)
class DirectiveGoAimTest {
    @Test
    void goesToTheGroundUnderWhatTheCrosshairIsOn(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            TestServer.ensureOverworld(server);
            ServerLevel level = server.overworld();
            int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING, 7, 8);
            ServerPlayer owner = TestServer.player(server, "go_aim");
            BirthService.rebirth(owner, OptionalInt.of(1000));
            InnateTechniqueService.setInnateTechnique(owner, PuppetManipulation.PUPPET_MANIPULATION.get());
            owner.moveTo(1.5, ground, 8.5, -90f, 0f);

            Villager target = new Villager(EntityType.VILLAGER, level);
            target.setNoAi(true);
            target.moveTo(7.5, ground, 8.5, 0f, 0f);
            level.addFreshEntity(target);
            owner.lookAt(EntityAnchorArgument.Anchor.EYES, target.getEyePosition());
            assertEquals(new BlockPos(7, ground, 8), DirectiveAbility.goTarget(owner), "the ground under the villager");

            // The owner's own puppet doesn't count: a look aimed down through it points at the ground past it.
            target.discard();
            PuppetEntity puppet = Puppets.create(owner, PuppetTier.TAP);
            puppet.setNoAi(true);
            puppet.moveTo(4.5, ground, 8.5, 0f, 0f);
            owner.lookAt(EntityAnchorArgument.Anchor.EYES, new Vec3(7.5, ground, 8.5));
            assertEquals(new BlockPos(7, ground, 8), DirectiveAbility.goTarget(owner), "past the puppet, where the crosshair meets the ground");
            puppet.discard();
        });
    }
}

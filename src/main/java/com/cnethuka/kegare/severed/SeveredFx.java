package com.cnethuka.kegare.severed;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * What Severed moments look and sound like to everyone (DESIGN §3.27). All of it physical: dust and grit off the ground, never a
 * glow or an Innate color. Vanilla particles, so it reads the same at every effects setting.
 */
final class SeveredFx {
    private SeveredFx() {}

    /** The block under them, for its dust (stone gives stone grit, sand sand). */
    static BlockState ground(ServerPlayer player) {
        BlockPos below = BlockPos.containing(player.getX(), player.getY() - 0.2, player.getZ());
        BlockState state = player.level().getBlockState(below);
        return state.isAir() ? player.level().getBlockState(below.below()) : state;
    }

    static void dust(ServerPlayer player, Vec3 at, int count, double spread, double speed) {
        BlockState ground = ground(player);
        if (ground.isAir()) return;
        player.serverLevel().sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground), at.x, at.y + 0.1, at.z, count, spread, 0.05, spread, speed);
    }

    static void blurOn(ServerPlayer player) {
        dust(player, player.position(), 10, 0.35, 0.08);
    }

    static void wind(ServerPlayer player) {}

    static void winded(ServerPlayer player) {}

    /** Closing Line's launch: a crack and a burst of dust and grit off the ground where they left. */
    static void launch(ServerPlayer player, Vec3 direction) {
        Vec3 at = player.position();
        dust(player, at, 28, 0.45, 0.22);
        player.serverLevel().sendParticles(ParticleTypes.POOF, at.x - direction.x * 0.4, at.y + 0.15, at.z - direction.z * 0.4, 6, 0.25, 0.05, 0.25, 0.04);
    }

    /** Its arrival: a skid and dust where they stop. */
    static void arrive(ServerPlayer player, Vec3 direction) {
        Vec3 at = player.position();
        dust(player, at.add(direction.x * 0.3, 0, direction.z * 0.3), 16, 0.35, 0.12);
    }

    static void wallCrash(ServerPlayer player, double lost) {
        Vec3 ahead = player.position().add(player.getLookAngle().multiply(1, 0, 1).normalize().scale(0.5)).add(0, 1.0, 0);
        player.serverLevel().sendParticles(ParticleTypes.POOF, ahead.x, ahead.y, ahead.z, (int) Math.min(12, 3 + lost * 6), 0.2, 0.4, 0.2, 0.02);
        dust(player, player.position(), (int) Math.min(24, 6 + lost * 10), 0.3, 0.12);
    }
}

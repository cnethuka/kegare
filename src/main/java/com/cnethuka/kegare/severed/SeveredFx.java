package com.cnethuka.kegare.severed;

import com.cnethuka.kegare.innatetechnique.visual.Visual;
import com.cnethuka.kegare.innatetechnique.visual.VisualService;
import com.cnethuka.kegare.sound.KegareSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
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

    static void sound(ServerPlayer player, Holder<SoundEvent> sound, float volume, float pitch) {
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), sound, SoundSource.PLAYERS, volume, pitch);
    }

    static void blurOn(ServerPlayer player) {
        dust(player, player.position(), 10, 0.35, 0.08);
        VisualService.playOnce(player, Visual.animation(SeveredIds.ANIM_BLUR_ON));
        sound(player, KegareSounds.SEVERED_BLUR_ON, 0.9f, 1f);
    }

    static void wind(ServerPlayer player) { // heard where each listener sees them (too fast for the eye, §3.30)
        com.cnethuka.kegare.sight.DelayedSight.playSound(player, KegareSounds.SEVERED_BLUR_WIND, SoundSource.PLAYERS, 0.5f,
                0.9f + player.getRandom().nextFloat() * 0.2f);
    }

    static void winded(ServerPlayer player) {
        VisualService.playOnce(player, Visual.animation(SeveredIds.ANIM_WINDED));
        sound(player, KegareSounds.SEVERED_WINDED, 0.8f, 1f);
    }

    /** Closing Line's launch: a crack and a burst of dust and grit off the ground where they left. */
    static void launch(ServerPlayer player, Vec3 direction) {
        Vec3 at = player.position();
        dust(player, at, 28, 0.45, 0.22);
        player.serverLevel().sendParticles(ParticleTypes.POOF, at.x - direction.x * 0.4, at.y + 0.15, at.z - direction.z * 0.4, 6, 0.25, 0.05, 0.25, 0.04);
        VisualService.playOnce(player, Visual.animation(SeveredIds.ANIM_CLOSING_LINE_LAUNCH));
        sound(player, KegareSounds.SEVERED_CLOSING_LINE_LAUNCH, 2.0f, 1f); // heavy, and heard well off
    }

    /** Its arrival: a skid and dust where they stop. */
    static void arrive(ServerPlayer player, Vec3 direction) {
        Vec3 at = player.position();
        dust(player, at.add(direction.x * 0.3, 0, direction.z * 0.3), 16, 0.35, 0.12);
        VisualService.playOnce(player, Visual.animation(SeveredIds.ANIM_CLOSING_LINE_ARRIVE));
        sound(player, KegareSounds.SEVERED_CLOSING_LINE_ARRIVE, 1.0f, 1f);
    }

    static void wallCrash(ServerPlayer player, double lost) {
        Vec3 ahead = player.position().add(player.getLookAngle().multiply(1, 0, 1).normalize().scale(0.5)).add(0, 1.0, 0);
        player.serverLevel().sendParticles(ParticleTypes.POOF, ahead.x, ahead.y, ahead.z, (int) Math.min(12, 3 + lost * 6), 0.2, 0.4, 0.2, 0.02);
        dust(player, player.position(), (int) Math.min(24, 6 + lost * 10), 0.3, 0.12);
        VisualService.playOnce(player, Visual.animation(SeveredIds.ANIM_WALL_CRASH));
        sound(player, KegareSounds.SEVERED_WALL_CRASH, (float) Math.min(1.6, 0.6 + lost * 0.4), 1f);
    }
}

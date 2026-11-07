package com.cnethuka.kegare.innate_technique.swap;

import com.cnethuka.kegare.sound.KegareSounds;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * Swap's claps (DESIGN §3.19, made in {@code tools/sounds/swap.py}). Every swap claps, heard at both ends of it, so whoever lands
 * where it happened hears it close: each move has its own clap or pitch, so the moves tell apart by ear too.
 */
public final class SwapSounds {
    /** A sound and the pitch it's played at. */
    public record Clap(Holder<SoundEvent> sound, float volume, float pitch) {
        public void at(ServerLevel level, Vec3 pos) {
            level.playSound(null, pos.x, pos.y, pos.z, sound, SoundSource.PLAYERS, volume, pitch);
        }

        public void at(Entity entity) {
            at((ServerLevel) entity.level(), entity.position().add(0, entity.getBbHeight() * 0.6, 0));
        }
    }

    /** Positions, and the recall to an anchor. */
    public static final Clap POSITIONS = new Clap(KegareSounds.SWAP_CLAP, 1f, 1f);

    private SwapSounds() {}
}

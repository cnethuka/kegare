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
    /** Switchback's cast: two claps, a beat apart. */
    public static final Clap SWITCHBACK = new Clap(KegareSounds.SWAP_DOUBLE_CLAP, 1f, 1f);
    /** Awareness's cast: the clap, a little higher. */
    public static final Clap AWARENESS = new Clap(KegareSounds.SWAP_CLAP, 0.9f, 1.12f);
    /** Mimic's cast: a hollow clap of cupped hands, at the caster only (the target stays as themselves). */
    public static final Clap MIMIC = new Clap(KegareSounds.SWAP_CUPPED_CLAP, 1f, 1f);
    /** Anchor's throw: the token flicked off the thumb, ringing as it spins away. */
    public static final Clap FLICK = new Clap(KegareSounds.SWAP_FLICK, 0.9f, 1f);
    /** The token sticking where it lands. */
    public static final Clap TOKEN_LAND = new Clap(KegareSounds.SWAP_TOKEN_LAND, 0.9f, 1f);
    /** Anchor's recall: the clap, a shade lower than Positions', at both ends. */
    public static final Clap ANCHOR_RECALL = new Clap(KegareSounds.SWAP_CLAP, 1f, 0.94f);
    /** A disguise landing (Awareness, Total Swap): a card riffled over. */
    public static final Clap CARD_FLIP = new Clap(KegareSounds.SWAP_FLIP, 0.9f, 1f);
    /** Switchback's swap back: a finger snap. */
    public static final Clap SWITCHBACK_BACK = new Clap(KegareSounds.SWAP_SNAP, 1f, 1f);

    private SwapSounds() {}
}

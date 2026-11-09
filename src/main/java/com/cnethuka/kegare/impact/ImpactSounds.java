package com.cnethuka.kegare.impact;

import com.cnethuka.kegare.sound.KegareSounds;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

/**
 * The hit sound (DESIGN §3.23, tools/sounds/impact.py), at the point of contact, with vanilla's own hurt sound (never instead of it).
 * A plain hit sounds like vanilla only. Big hits of any kind (Heavy and up) get a deep boom under it, louder and lower as the tier
 * grows. Curio Burst's thump is its own (§3.20).
 */
final class ImpactSounds {
    private static final float[] BOOM_VOLUME = {0f, 0f, 0.6f, 1.0f, 1.4f};
    private static final float[] BOOM_PITCH = {1f, 1f, 1.1f, 0.9f, 0.75f};

    private ImpactSounds() {}

    static void play(Impact impact) {
        int t = impact.tier().ordinal();
        if (!booms(impact.tier())) return;
        Vec3 at = impact.contact();
        impact.level().playSound(null, at.x, at.y, at.z, KegareSounds.IMPACT_BOOM.value(), SoundSource.PLAYERS, BOOM_VOLUME[t], BOOM_PITCH[t]);
    }

    /** Whether a hit of this tier has a sound of Kegare's own (the boom); below Heavy it's vanilla's alone. */
    static boolean booms(ImpactTier tier) {
        return BOOM_VOLUME[tier.ordinal()] > 0;
    }
}

package com.cnethuka.kegare.impact;

import com.cnethuka.kegare.sound.KegareSounds;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * The hit sounds (DESIGN §3.23, tools/sounds/impact.py), at the point of contact, with vanilla's own hurt sound (never instead of it).
 * A melee hit sounds like what it was hit with: an empty hand (or anything that isn't a weapon) a punch, a sword a blade, an axe or a
 * mace a heavy blow; louder the harder it lands. Big hits of any kind (Heavy and up) get a deep boom under them, louder and lower as
 * the tier grows.
 */
final class ImpactSounds {
    private static final float[] HIT_VOLUME = {0.55f, 0.8f, 1.0f, 1.2f, 1.4f};
    private static final float[] BOOM_VOLUME = {0f, 0f, 0.6f, 1.0f, 1.4f};
    private static final float[] BOOM_PITCH = {1f, 1f, 1.1f, 0.9f, 0.75f};

    private ImpactSounds() {}

    static void play(Impact impact) {
        int t = impact.tier().ordinal();
        Vec3 at = impact.contact();
        float jitter = 0.94f + 0.12f * impact.level().getRandom().nextFloat();
        if (impact.melee()) {
            impact.level().playSound(null, at.x, at.y, at.z, weaponSound(impact.weapon()), SoundSource.PLAYERS, HIT_VOLUME[t], jitter * (1.05f - 0.05f * t));
        }
        if (BOOM_VOLUME[t] > 0) {
            impact.level().playSound(null, at.x, at.y, at.z, KegareSounds.IMPACT_BOOM.value(), SoundSource.PLAYERS, BOOM_VOLUME[t], BOOM_PITCH[t]);
        }
    }

    /** What a melee hit sounds like, by what it was hit with. */
    static SoundEvent weaponSound(ItemStack weapon) {
        if (weapon.is(ItemTags.SWORDS)) return KegareSounds.IMPACT_BLADE.value();
        if (weapon.is(ItemTags.AXES) || weapon.is(Items.MACE)) return KegareSounds.IMPACT_HEAVY.value();
        return KegareSounds.IMPACT_PUNCH.value();
    }
}

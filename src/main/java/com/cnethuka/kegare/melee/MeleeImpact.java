package com.cnethuka.kegare.melee;

import com.cnethuka.kegare.innatetechnique.visual.Visual;
import com.cnethuka.kegare.innatetechnique.visual.VisualService;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * The layer's named impact moments, fired on the server tick the effect is applied. Effects and sounds hang off these so they
 * land with it, and the coming Impact System hooks in through {@link #listen} (screen shake and hit-stop would go there;
 * there's none yet).
 */
public enum MeleeImpact {
    /** A dash starts: {@code at} is where they left from. */
    DASH_BURST(SoundEvents.BREEZE_CHARGE, 0.7f, 1.5f),
    /** Something aimed at where they were has just missed them. */
    CLEAN_DODGE(SoundEvents.AMETHYST_BLOCK_CHIME, 1.2f, 1.6f),
    /** A perfect parry cancelled a melee hit: {@code at} is the point of contact, {@code who} the defender. */
    PARRY_PERFECT(SoundEvents.TRIDENT_HIT, 1.1f, 1.45f),
    /** A guard took some of a hit: {@code who} is the defender. */
    GUARD_HIT(SoundEvents.SHIELD_BLOCK, 0.8f, 1.2f),
    /** An attacker was staggered. */
    STAGGER(SoundEvents.ANVIL_LAND, 0.25f, 1.9f),
    /** A Reinforced Strike landed: {@code at} is the target. */
    STRIKE_HIT(SoundEvents.PLAYER_ATTACK_CRIT, 1.3f, 0.65f);

    @FunctionalInterface
    public interface Listener {
        void onImpact(MeleeImpact moment, ServerLevel level, Vec3 at, @Nullable Entity who);
    }

    private static final List<Listener> LISTENERS = new CopyOnWriteArrayList<>();

    private final SoundEvent sound;
    private final float volume;
    private final float pitch;

    MeleeImpact(SoundEvent sound, float volume, float pitch) {
        this.sound = sound;
        this.volume = volume;
        this.pitch = pitch;
    }

    public static void listen(Listener listener) {
        LISTENERS.add(listener);
    }

    static void remove(Listener listener) {
        LISTENERS.remove(listener);
    }

    void fire(ServerLevel level, Vec3 at, @Nullable Entity who) {
        level.playSound(null, at.x, at.y, at.z, sound, SoundSource.PLAYERS, volume, pitch);
        switch (this) {
            case DASH_BURST -> VisualService.playAt(level, at, Visual.effect(MeleeIds.FX_DASH_BURST, Visual.Anchor.FEET));
            // Ride the defender's guard, not a point in the world: the point is too near both fighters' cameras to be drawn.
            case PARRY_PERFECT -> VisualService.playOnce(who, Visual.effect(MeleeIds.FX_PARRY_SPARK, Visual.Anchor.EYES));
            case GUARD_HIT -> VisualService.playOnce(who, Visual.effect(MeleeIds.FX_GUARD_HIT, Visual.Anchor.EYES));
            case STRIKE_HIT -> VisualService.playAt(level, at, Visual.effect(MeleeIds.FX_STRIKE_IMPACT, Visual.Anchor.BODY));
            default -> { }
        }
        LISTENERS.forEach(listener -> listener.onImpact(this, level, at, who));
    }
}

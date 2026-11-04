package com.cnethuka.kegare.client.visual;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Vanilla particles standing in for a Photon effect: when the effect can't load, or when the client chose
 * {@code vfxQuality = VANILLA}. Each effect can register a lookalike; otherwise a neutral default is used.
 */
public final class VanillaFallback {
    /**
     * @param perTick particles per tick while a loop plays (trails, auras, cones)
     * @param burst   particles for a one-shot effect (casts, impacts)
     * @param spread  random offset radius around the anchor
     * @param speed   random velocity magnitude
     * @param directional particles fly along the anchor's look direction at {@code speed} (cones, breath)
     */
    public record Spec(ParticleOptions particle, int perTick, int burst, double spread, double speed, boolean directional) {
        public Spec(ParticleOptions particle, int perTick, int burst, double spread, double speed) {
            this(particle, perTick, burst, spread, speed, false);
        }
    }

    private static final Spec DEFAULT = new Spec(ParticleTypes.END_ROD, 1, 8, 0.3, 0.05);
    private static final Map<ResourceLocation, Spec> SPECS = new ConcurrentHashMap<>();

    private VanillaFallback() {}

    public static void register(ResourceLocation effect, Spec spec) {
        SPECS.put(effect, spec);
    }

    static Spec spec(ResourceLocation effect) {
        return SPECS.getOrDefault(effect, DEFAULT);
    }

    /** @param direction for directional specs: where the particles fly (unit vector), else ignored */
    static void spawn(Level level, Vec3 at, Vec3 direction, Spec spec, int count) {
        RandomSource random = level.random;
        for (int i = 0; i < count; i++) {
            double x = at.x + (random.nextDouble() - 0.5) * 2 * spec.spread();
            double y = at.y + (random.nextDouble() - 0.5) * 2 * spec.spread();
            double z = at.z + (random.nextDouble() - 0.5) * 2 * spec.spread();
            double jitter = spec.directional() ? spec.speed() * 0.35 : spec.speed();
            double vx = (random.nextDouble() - 0.5) * 2 * jitter;
            double vy = (random.nextDouble() - 0.5) * 2 * jitter;
            double vz = (random.nextDouble() - 0.5) * 2 * jitter;
            if (spec.directional()) {
                vx += direction.x * spec.speed();
                vy += direction.y * spec.speed();
                vz += direction.z * spec.speed();
            }
            level.addParticle(spec.particle(), x, y, z, vx, vy, vz);
        }
    }
}

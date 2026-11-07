package com.cnethuka.kegare.innatetechnique.visual;

import com.cnethuka.kegare.innatetechnique.InnateTechnique;
import com.cnethuka.kegare.registry.KegareRegistries;
import java.util.LinkedHashSet;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;

/**
 * Bridges common code (entities) to the client's visual system without loading client classes on a server.
 * Also lists every known effect so the client can preload them.
 */
public final class VisualHooks {
    /** Starts a projectile's trail at a size; says false to be asked again next tick (not clear of its owner's eyes yet). */
    @FunctionalInterface
    public interface TrailStarter {
        boolean start(Entity projectile, ResourceLocation effect, float scale, double clearOfOwnEyes);
    }

    /** Set by the client entry point; stays null on a dedicated server. */
    private static TrailStarter trailStarter;
    private static final Set<ResourceLocation> EXTRA_EFFECTS = new LinkedHashSet<>();

    private VisualHooks() {}

    public static void setTrailStarter(TrailStarter starter) {
        trailStarter = starter;
    }

    /** Client side only (projectile entities call this on their first client tick). */
    public static void startTrail(Entity projectile, ResourceLocation effect) {
        startTrail(projectile, effect, 1f, 0);
    }

    /**
     * The same, for a projectile that comes in sizes and, if {@code clearOfOwnEyes} is more than 0, whose thrower in first person
     * shouldn't have its trail start in their face: it starts once it's that far from their camera.
     *
     * @return false if it hasn't started yet: call again next tick
     */
    public static boolean startTrail(Entity projectile, ResourceLocation effect, float scale, double clearOfOwnEyes) {
        if (!projectile.level().isClientSide || trailStarter == null) return true;
        return trailStarter.start(projectile, effect, scale, clearOfOwnEyes);
    }

    /** Effects used outside ability phases (status effects such as freezing), for preloading. */
    public static void registerEffect(ResourceLocation effect) {
        EXTRA_EFFECTS.add(effect);
    }

    /** Every effect any ability or status uses. */
    public static Set<ResourceLocation> allEffects() {
        Set<ResourceLocation> out = new LinkedHashSet<>(EXTRA_EFFECTS);
        for (InnateTechnique innateTechnique : KegareRegistries.INNATE_TECHNIQUE) {
            innateTechnique.everyAbility().forEach(a -> out.addAll(a.visuals().effects()));
        }
        for (com.cnethuka.kegare.innatetechnique.Ability shared : com.cnethuka.kegare.loadout.SharedAbilities.all()) {
            out.addAll(shared.visuals().effects());
            shared.special().ifPresent(special -> out.addAll(special.visuals().effects()));
        }
        return out;
    }
}

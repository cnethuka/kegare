package com.cnethuka.kegare.innatetechnique.visual;

import com.cnethuka.kegare.innatetechnique.InnateTechnique;
import com.cnethuka.kegare.registry.KegareRegistries;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.BiConsumer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;

/**
 * Bridges common code (entities) to the client's visual system without loading client classes on a server.
 * Also lists every known effect so the client can preload them.
 */
public final class VisualHooks {
    /** Set by the client entry point; stays null on a dedicated server. */
    private static BiConsumer<Entity, ResourceLocation> trailStarter;
    private static final Set<ResourceLocation> EXTRA_EFFECTS = new LinkedHashSet<>();

    private VisualHooks() {}

    public static void setTrailStarter(BiConsumer<Entity, ResourceLocation> starter) {
        trailStarter = starter;
    }

    /** Client side only (projectile entities call this on their first client tick). */
    public static void startTrail(Entity projectile, ResourceLocation effect) {
        if (projectile.level().isClientSide && trailStarter != null) trailStarter.accept(projectile, effect);
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
        return out;
    }
}

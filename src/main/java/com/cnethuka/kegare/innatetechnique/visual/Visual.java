package com.cnethuka.kegare.innatetechnique.visual;

import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;

/**
 * One cosmetic: a player animation ({@code assets/<ns>/player_animations}, Player Animation Library) and/or a VFX
 * effect ({@code assets/<ns>/fx/<path>.fx}, Photon). Either may be absent. Never affects gameplay.
 *
 * @param anchor where an entity-attached effect sits on its entity
 */
public record Visual(Optional<ResourceLocation> animation, Optional<ResourceLocation> effect, Anchor anchor) {
    public static final Visual NONE = new Visual(Optional.empty(), Optional.empty(), Anchor.EYES);

    /** Where the effect's origin is on the entity, and whether it turns with the entity's look. */
    public enum Anchor {
        /** At the eyes, rotated with the look direction (casts, cones, beams). */
        EYES,
        /** At the middle of the body, not rotated (auras, shells, status effects). */
        BODY,
        /** At the feet, not rotated (ground rings). */
        FEET,
        /** At the entity's position, rotated with its motion (projectile trails). */
        MOTION
    }

    public static Visual of(@Nullable ResourceLocation animation, @Nullable ResourceLocation effect, Anchor anchor) {
        return new Visual(Optional.ofNullable(animation), Optional.ofNullable(effect), anchor);
    }

    public static Visual animation(ResourceLocation animation) {
        return of(animation, null, Anchor.EYES);
    }

    public static Visual effect(ResourceLocation effect, Anchor anchor) {
        return of(null, effect, anchor);
    }

    public boolean isEmpty() {
        return animation.isEmpty() && effect.isEmpty();
    }
}

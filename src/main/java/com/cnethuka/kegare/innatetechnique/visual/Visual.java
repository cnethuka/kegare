package com.cnethuka.kegare.innatetechnique.visual;

import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;

/**
 * One cosmetic: a player animation ({@code assets/<ns>/player_animations}, Player Animation Library) and/or a VFX
 * effect ({@code assets/<ns>/fx/<path>.fx}, Photon). Either may be absent. Never affects gameplay.
 *
 * @param anchor where an entity-attached effect sits on its entity
 * @param linger loops only: fade out instead of vanishing when stopped (see {@link #lingering})
 */
public record Visual(Optional<ResourceLocation> animation, Optional<ResourceLocation> effect, Anchor anchor, boolean linger) {
    public static final Visual NONE = new Visual(Optional.empty(), Optional.empty(), Anchor.EYES);

    public Visual(Optional<ResourceLocation> animation, Optional<ResourceLocation> effect, Anchor anchor) {
        this(animation, effect, anchor, false);
    }

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

    /**
     * A loop that leaves something in the world (a world-space trail on the ground): when it stops, or its entity goes
     * away, it stops emitting and what is already there fades out over its own lifetime, instead of vanishing at once
     * like state loops (shells, cones, charges) must.
     */
    public static Visual lingering(ResourceLocation effect, Anchor anchor) {
        return new Visual(Optional.empty(), Optional.of(effect), anchor, true);
    }

    public boolean isEmpty() {
        return animation.isEmpty() && effect.isEmpty();
    }
}

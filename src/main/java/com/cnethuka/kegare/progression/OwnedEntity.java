package com.cnethuka.kegare.progression;

import java.util.UUID;
import javax.annotation.Nullable;

/**
 * An entity that belongs to a player (a puppet, a possessed player's empty body). Its hits are its owner's, and its
 * owner gets nothing for hitting it. Anything a cast spawns already counts through {@link CastOrigin}; this covers
 * entities made outside a cast or before they could carry that tag.
 */
public interface OwnedEntity {
    @Nullable
    UUID ownerId();

    /** It picks its own fights (a puppet), so a hit of its own isn't its owner doing anything: see {@link Landings#landOnItsOwn}. */
    default boolean actsOnItsOwn() {
        return false;
    }
}

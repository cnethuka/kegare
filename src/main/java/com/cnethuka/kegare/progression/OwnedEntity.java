package com.cnethuka.kegare.progression;

import java.util.UUID;
import javax.annotation.Nullable;

/**
 * An entity that belongs to a player (a puppet, a possessed player's empty body). Its hits are its owner's, and its
 * owner gets nothing for hitting it. Anything a cast spawns already counts through {@link CastOrigin}; this is for what
 * has to stay true of entities older than that tag, or made outside a cast.
 */
public interface OwnedEntity {
    @Nullable
    UUID ownerId();
}

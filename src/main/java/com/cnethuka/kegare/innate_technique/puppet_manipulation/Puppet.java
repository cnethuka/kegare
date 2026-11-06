package com.cnethuka.kegare.innate_technique.puppet_manipulation;

import java.util.UUID;
import javax.annotation.Nullable;

/**
 * What future systems may ask of a puppet (e.g. a technique that only works on living souls, or one that reads who
 * pulls the strings). {@link PuppetEntity} is the only implementation.
 */
public interface Puppet extends com.cnethuka.kegare.progression.OwnedEntity {
    /** Never: a puppet is a curio charged with Malice, not a being. */
    boolean hasSoul();

    /** The player who made it (null only for a puppet that lost its data). */
    @Override
    @Nullable
    UUID ownerId();

    /** RGB of its owner's Malice color when it was made (its glow). */
    int maliceColor();

    PuppetTier tier();
}

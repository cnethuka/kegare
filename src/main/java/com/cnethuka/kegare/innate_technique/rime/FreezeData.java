package com.cnethuka.kegare.innate_technique.rime;

import java.util.UUID;
import javax.annotation.Nullable;

/** Runtime-only freeze state of any living entity (attachment {@code kegare:freeze}). Game-time ticks. */
public final class FreezeData {
    int stacks;
    /** Frozen solid until {@link #frozenUntil}; cleared when it breaks free. */
    boolean frozen;
    long lastChange;
    long frozenUntil;
    long immuneUntil;
    /** Who froze it (for kill credit checks); null when not frozen. */
    @Nullable
    UUID frozenBy;

    public int stacks() { return stacks; }
    public long frozenUntil() { return frozenUntil; }
}

package com.cnethuka.kegare.network;

/** Runtime-only flag: the owning client needs a fresh {@link SyncPayload}. */
public final class SyncState {
    boolean dirty = true;
    /** Last cost environment sent; weather or moving can change it without any other event. */
    com.cnethuka.kegare.innatetechnique.CostEnvironment lastEnvironment;
    boolean lastInCombat;
}

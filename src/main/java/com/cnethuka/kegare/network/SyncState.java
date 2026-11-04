package com.cnethuka.kegare.network;

/** Runtime-only flag: the owning client needs a fresh {@link SyncPayload}. */
public final class SyncState {
    boolean dirty = true;
}

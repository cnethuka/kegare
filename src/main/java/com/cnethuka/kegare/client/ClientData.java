package com.cnethuka.kegare.client;

import com.cnethuka.kegare.network.SyncPayload;
import javax.annotation.Nullable;

/**
 * Client-side cache of the last {@link SyncPayload} from the server. Display only: the client
 * never decides anything from it. Uses no client-only classes, so referencing it from common
 * network code is safe on a dedicated server (it is simply never written there).
 */
public final class ClientData {
    @Nullable
    private static SyncPayload latest;

    private ClientData() {}

    public static void apply(SyncPayload payload) {
        latest = payload;
    }

    public static void reset() {
        latest = null;
    }

    @Nullable
    public static SyncPayload get() {
        return latest;
    }
}

package com.cnethuka.kegare.network;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Payload registration. Bump the version when payload formats change: mismatched clients are refused.
 * Handlers run on the main thread (NeoForge default).
 */
public final class KegareNetwork {
    public static final String VERSION = "1";

    private KegareNetwork() {}

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(VERSION);
        registrar.playToServer(IntentPayload.TYPE, IntentPayload.STREAM_CODEC, IntentPayload::handle);
        registrar.playToClient(SyncPayload.TYPE, SyncPayload.STREAM_CODEC, SyncPayload::handle);
    }
}

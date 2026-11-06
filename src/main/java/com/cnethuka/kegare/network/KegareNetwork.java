package com.cnethuka.kegare.network;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Payload registration. Bump the version when payload formats change: mismatched clients are refused.
 * Handlers run on the main thread (NeoForge default).
 * <p>
 * Client to server payloads are requests only (key intents, "open my Sorcerer screen", "confirm reroll",
 * "select slot N"); the server validates every one. Server to client payloads carry results that are already
 * decided and saved; screens are cosmetic.
 */
public final class KegareNetwork {
    public static final String VERSION = "13";

    private KegareNetwork() {}

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(VERSION);
        // client -> server (requests)
        registrar.playToServer(IntentPayload.TYPE, IntentPayload.STREAM_CODEC, IntentPayload::handle);
        registrar.playToServer(SelectAbilityPayload.TYPE, SelectAbilityPayload.STREAM_CODEC, SelectAbilityPayload::handle);
        registrar.playToServer(SorcererRequestPayload.TYPE, SorcererRequestPayload.STREAM_CODEC, SorcererRequestPayload::handle);
        registrar.playToServer(TalismanConfirmPayload.TYPE, TalismanConfirmPayload.STREAM_CODEC, TalismanConfirmPayload::handle);
        registrar.playToServer(LoadoutEditPayload.TYPE, LoadoutEditPayload.STREAM_CODEC, LoadoutEditPayload::handle);
        // server -> client (state and decided results)
        registrar.playToClient(SyncPayload.TYPE, SyncPayload.STREAM_CODEC, SyncPayload::handle);
        registrar.playToClient(RevealPayload.TYPE, RevealPayload.STREAM_CODEC, RevealPayload::handle);
        registrar.playToClient(SorcererInfoPayload.TYPE, SorcererInfoPayload.STREAM_CODEC, SorcererInfoPayload::handle);
        registrar.playToClient(TalismanPromptPayload.TYPE, TalismanPromptPayload.STREAM_CODEC, TalismanPromptPayload::handle);
        registrar.playToClient(ProgressionToastPayload.TYPE, ProgressionToastPayload.STREAM_CODEC, ProgressionToastPayload::handle);
        registrar.playToClient(LoadoutResultPayload.TYPE, LoadoutResultPayload.STREAM_CODEC, LoadoutResultPayload::handle);
        registrar.playToClient(com.cnethuka.kegare.rmt.OrbPayload.TYPE, com.cnethuka.kegare.rmt.OrbPayload.STREAM_CODEC,
                com.cnethuka.kegare.rmt.OrbPayload::handle);
        registrar.playToClient(com.cnethuka.kegare.innate_technique.swap.SwapLink.Payload.TYPE,
                com.cnethuka.kegare.innate_technique.swap.SwapLink.Payload.STREAM_CODEC, com.cnethuka.kegare.innate_technique.swap.SwapLink.Payload::handle);
        registrar.playToClient(com.cnethuka.kegare.innate_technique.swap.Disguises.StatePayload.TYPE,
                com.cnethuka.kegare.innate_technique.swap.Disguises.StatePayload.STREAM_CODEC,
                com.cnethuka.kegare.innate_technique.swap.Disguises.StatePayload::handle);
        registrar.playToClient(com.cnethuka.kegare.innate_technique.puppet_manipulation.PuppetHud.Payload.TYPE,
                com.cnethuka.kegare.innate_technique.puppet_manipulation.PuppetHud.Payload.STREAM_CODEC,
                com.cnethuka.kegare.innate_technique.puppet_manipulation.PuppetHud.Payload::handle);
        registrar.playToClient(com.cnethuka.kegare.innate_technique.puppet_manipulation.StringsSync.Payload.TYPE,
                com.cnethuka.kegare.innate_technique.puppet_manipulation.StringsSync.Payload.STREAM_CODEC,
                com.cnethuka.kegare.innate_technique.puppet_manipulation.StringsSync.Payload::handle);
        registrar.playToClient(com.cnethuka.kegare.innate_technique.puppet_manipulation.PossessionLinks.StatePayload.TYPE,
                com.cnethuka.kegare.innate_technique.puppet_manipulation.PossessionLinks.StatePayload.STREAM_CODEC,
                com.cnethuka.kegare.innate_technique.puppet_manipulation.PossessionLinks.StatePayload::handle);
        // server -> clients tracking an entity (cosmetic only: animations and VFX)
        registrar.playToClient(VisualPayload.TYPE, VisualPayload.STREAM_CODEC, VisualPayload::handle);
    }
}

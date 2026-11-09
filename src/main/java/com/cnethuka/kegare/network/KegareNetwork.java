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
    public static final String VERSION = "23";

    private KegareNetwork() {}

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(VERSION);
        // Sub-tick timing (DESIGN §3.29): handled on the network thread as they arrive, so a tick's wait never gets into a time.
        PayloadRegistrar timing = event.registrar(VERSION).executesOn(net.neoforged.neoforge.network.registration.HandlerThread.NETWORK);
        timing.playToServer(com.cnethuka.kegare.timing.TimingNet.Stamp.TYPE, com.cnethuka.kegare.timing.TimingNet.Stamp.STREAM_CODEC,
                com.cnethuka.kegare.timing.TimingNet.Stamp::handle);
        timing.playToClient(com.cnethuka.kegare.timing.TimingNet.Ping.TYPE, com.cnethuka.kegare.timing.TimingNet.Ping.STREAM_CODEC,
                com.cnethuka.kegare.timing.TimingNet.Ping::handle);
        timing.playToServer(com.cnethuka.kegare.timing.TimingNet.Pong.TYPE, com.cnethuka.kegare.timing.TimingNet.Pong.STREAM_CODEC,
                com.cnethuka.kegare.timing.TimingNet.Pong::handle);
        // client -> server (requests)
        registrar.playToServer(IntentPayload.TYPE, IntentPayload.STREAM_CODEC, IntentPayload::handle);
        registrar.playToServer(SelectAbilityPayload.TYPE, SelectAbilityPayload.STREAM_CODEC, SelectAbilityPayload::handle);
        registrar.playToServer(SorcererRequestPayload.TYPE, SorcererRequestPayload.STREAM_CODEC, SorcererRequestPayload::handle);
        registrar.playToServer(TalismanConfirmPayload.TYPE, TalismanConfirmPayload.STREAM_CODEC, TalismanConfirmPayload::handle);
        registrar.playToServer(LoadoutEditPayload.TYPE, LoadoutEditPayload.STREAM_CODEC, LoadoutEditPayload::handle);
        registrar.playToServer(com.cnethuka.kegare.melee.MeleeIntentPayload.TYPE, com.cnethuka.kegare.melee.MeleeIntentPayload.STREAM_CODEC,
                com.cnethuka.kegare.melee.MeleeIntentPayload::handle);
        // server -> client (state and decided results)
        registrar.playToClient(com.cnethuka.kegare.melee.MeleeDashPayload.TYPE, com.cnethuka.kegare.melee.MeleeDashPayload.STREAM_CODEC,
                com.cnethuka.kegare.melee.MeleeDashPayload::handle);
        registrar.playToClient(com.cnethuka.kegare.melee.MeleeTellPayload.TYPE, com.cnethuka.kegare.melee.MeleeTellPayload.STREAM_CODEC,
                com.cnethuka.kegare.melee.MeleeTellPayload::handle);
        registrar.playToClient(com.cnethuka.kegare.melee.MeleeHudPayload.TYPE, com.cnethuka.kegare.melee.MeleeHudPayload.STREAM_CODEC,
                com.cnethuka.kegare.melee.MeleeHudPayload::handle);
        registrar.playToClient(com.cnethuka.kegare.severed.RushPayload.TYPE, com.cnethuka.kegare.severed.RushPayload.STREAM_CODEC,
                com.cnethuka.kegare.severed.RushPayload::handle);
        registrar.playToClient(com.cnethuka.kegare.rmt.RmtNet.Link.TYPE, com.cnethuka.kegare.rmt.RmtNet.Link.STREAM_CODEC,
                com.cnethuka.kegare.rmt.RmtNet.Link::handle);
        registrar.playToClient(com.cnethuka.kegare.rmt.RmtNet.Moment.TYPE, com.cnethuka.kegare.rmt.RmtNet.Moment.STREAM_CODEC,
                com.cnethuka.kegare.rmt.RmtNet.Moment::handle);
        registrar.playToClient(com.cnethuka.kegare.rmt.RmtOutputPayload.TYPE, com.cnethuka.kegare.rmt.RmtOutputPayload.STREAM_CODEC,
                com.cnethuka.kegare.rmt.RmtOutputPayload::handle);
        registrar.playToClient(com.cnethuka.kegare.severed.BlurPayload.TYPE, com.cnethuka.kegare.severed.BlurPayload.STREAM_CODEC,
                com.cnethuka.kegare.severed.BlurPayload::handle);
        registrar.playToClient(com.cnethuka.kegare.severed.SensedPositionsPayload.TYPE, com.cnethuka.kegare.severed.SensedPositionsPayload.STREAM_CODEC,
                com.cnethuka.kegare.severed.SensedPositionsPayload::handle);
        registrar.playToClient(com.cnethuka.kegare.severed.SensesPayload.TYPE, com.cnethuka.kegare.severed.SensesPayload.STREAM_CODEC,
                com.cnethuka.kegare.severed.SensesPayload::handle);
        registrar.playToClient(SyncPayload.TYPE, SyncPayload.STREAM_CODEC, SyncPayload::handle);
        registrar.playToClient(RevealPayload.TYPE, RevealPayload.STREAM_CODEC, RevealPayload::handle);
        registrar.playToClient(SorcererInfoPayload.TYPE, SorcererInfoPayload.STREAM_CODEC, SorcererInfoPayload::handle);
        registrar.playToClient(TalismanPromptPayload.TYPE, TalismanPromptPayload.STREAM_CODEC, TalismanPromptPayload::handle);
        registrar.playToClient(ProgressionToastPayload.TYPE, ProgressionToastPayload.STREAM_CODEC, ProgressionToastPayload::handle);
        registrar.playToClient(LoadoutResultPayload.TYPE, LoadoutResultPayload.STREAM_CODEC, LoadoutResultPayload::handle);
        registrar.playToClient(com.cnethuka.kegare.fists.BlackSparkPayload.TYPE, com.cnethuka.kegare.fists.BlackSparkPayload.STREAM_CODEC,
                com.cnethuka.kegare.fists.BlackSparkPayload::handle);
        registrar.playToClient(com.cnethuka.kegare.fists.ZonePayload.TYPE, com.cnethuka.kegare.fists.ZonePayload.STREAM_CODEC,
                com.cnethuka.kegare.fists.ZonePayload::handle);
        registrar.playToClient(com.cnethuka.kegare.fists.FistsPayload.TYPE, com.cnethuka.kegare.fists.FistsPayload.STREAM_CODEC,
                com.cnethuka.kegare.fists.FistsPayload::handle);
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
        registrar.playToClient(com.cnethuka.kegare.impact.ImpactPayload.TYPE, com.cnethuka.kegare.impact.ImpactPayload.STREAM_CODEC,
                com.cnethuka.kegare.impact.ImpactPayload::handle);
        registrar.playToClient(com.cnethuka.kegare.battledamage.BattleDamagePayload.TYPE,
                com.cnethuka.kegare.battledamage.BattleDamagePayload.STREAM_CODEC, com.cnethuka.kegare.battledamage.BattleDamagePayload::handle);
        registrar.playToClient(com.cnethuka.kegare.impact.ScreenEffects.Payload.TYPE, com.cnethuka.kegare.impact.ScreenEffects.Payload.STREAM_CODEC,
                com.cnethuka.kegare.impact.ScreenEffects.Payload::handle);
        registrar.playToClient(com.cnethuka.kegare.innate_technique.time_manipulation.TimeNet.Fx.TYPE,
                com.cnethuka.kegare.innate_technique.time_manipulation.TimeNet.Fx.STREAM_CODEC,
                com.cnethuka.kegare.innate_technique.time_manipulation.TimeNet.Fx::handle);
        registrar.playToClient(com.cnethuka.kegare.innate_technique.time_manipulation.TimeNet.Mark.TYPE,
                com.cnethuka.kegare.innate_technique.time_manipulation.TimeNet.Mark.STREAM_CODEC,
                com.cnethuka.kegare.innate_technique.time_manipulation.TimeNet.Mark::handle);
        registrar.playToClient(com.cnethuka.kegare.innate_technique.time_manipulation.TimeNet.Stop.TYPE,
                com.cnethuka.kegare.innate_technique.time_manipulation.TimeNet.Stop.STREAM_CODEC,
                com.cnethuka.kegare.innate_technique.time_manipulation.TimeNet.Stop::handle);
        // server -> clients tracking an entity (cosmetic only: animations and VFX)
        registrar.playToClient(VisualPayload.TYPE, VisualPayload.STREAM_CODEC, VisualPayload::handle);
    }
}

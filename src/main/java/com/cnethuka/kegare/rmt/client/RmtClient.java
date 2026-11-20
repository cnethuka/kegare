package com.cnethuka.kegare.rmt.client;

import com.cnethuka.kegare.client.visual.ClientVisuals;
import com.cnethuka.kegare.client.visual.PlayerAnimations;
import com.cnethuka.kegare.client.visual.VanillaFallback;
import com.cnethuka.kegare.innatetechnique.visual.VisualHooks;
import com.cnethuka.kegare.rmt.OrbPayload;
import com.cnethuka.kegare.rmt.RmtIds;
import net.neoforged.neoforge.common.NeoForge;
import net.minecraft.core.particles.ParticleTypes;

/**
 * Reverse Malice Technique's client side: the green orb, its channel's light kept on the fist held to the chest, quieter versions of its
 * effects on your own body in first person, and vanilla stand-ins for its Photon effects.
 */
public final class RmtClient {
    /** Their output and injury fatigue as the server last said (the Sorcerer screen's RMT details). */
    static com.cnethuka.kegare.rmt.RmtOutputPayload output = com.cnethuka.kegare.rmt.RmtOutputPayload.NONE;

    private RmtClient() {}

    public static void init() {
        OrbPayload.setClientHandler(GreenOrbClient::handle);
        com.cnethuka.kegare.rmt.RmtOutputPayload.setClientHandler(payload -> output = payload);
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingOut e) ->
                output = com.cnethuka.kegare.rmt.RmtOutputPayload.NONE);
        // Under its stats on the Sorcerer screen: how strongly it heals right now.
        com.cnethuka.kegare.client.gui.SorcererScreen.addDetailLines(ability -> ability.id().equals(RmtIds.ABILITY)
                ? java.util.List.of(outputLine(output.output(), output.fatigue()), net.minecraft.network.chat.Component.translatable(
                        output.knowsOthers() ? "gui.kegare.rmt.knows_others" : "gui.kegare.rmt.not_others"))
                : java.util.List.of());
        RmtLinkClient.init();
        NeoForge.EVENT_BUS.addListener(GreenOrbClient::onClientTick);
        NeoForge.EVENT_BUS.addListener(GreenOrbClient::onLogout);
        // While it channels: white light off the fist held to the chest, turning with the body so it stays on that fist (at Vanilla, white
        // dust there). Each heal: white sparkles round the body.
        VanillaFallback.register(RmtIds.FX_CHANNEL_AURA, new VanillaFallback.Spec(
                new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f(1f, 1f, 1f), 0.7f), 1, 4, 0.08, 0.01));
        VanillaFallback.register(RmtIds.FX_HEAL_PULSE, new VanillaFallback.Spec(ParticleTypes.END_ROD, 1, 10, 0.6, 0.03));
        VanillaFallback.register(RmtIds.FX_ORB, new VanillaFallback.Spec(ParticleTypes.HAPPY_VILLAGER, 1, 4, 0.2, 0.0));
        VanillaFallback.register(RmtIds.FX_TRAIL_DOT, new VanillaFallback.Spec(ParticleTypes.HAPPY_VILLAGER, 1, 1, 0.0, 0.0));
        VanillaFallback.register(RmtIds.FX_UNLOCK_BURST, new VanillaFallback.Spec(ParticleTypes.HAPPY_VILLAGER, 1, 24, 0.5, 0.2));
        ClientVisuals.turnWithBody(RmtIds.FX_CHANNEL_AURA, RmtFxAuthoring.FIST);
        ClientVisuals.turnWithBody(RmtIds.FX_CHANNEL_AURA_SELF, RmtFxAuthoring.FIST);
        // Your own first person: the arm raised to the chest would only cross the bottom of the view, so your hands stay vanilla's (and
        // whatever you hold stays where it was). What's at the chest is just under your camera, bright in the middle of the view as you
        // look down: you get a few small licks there that don't bloom, and of each heal only the rings on the ground. At Vanilla, nothing
        // (the health bar says it).
        PlayerAnimations.keepVanillaHands(java.util.List.of(RmtIds.ANIM_CHANNEL));
        ClientVisuals.setFirstPersonVariant(RmtIds.FX_CHANNEL_AURA, RmtIds.FX_CHANNEL_AURA_SELF);
        ClientVisuals.setFirstPersonVariant(RmtIds.FX_HEAL_PULSE, RmtIds.FX_HEAL_PULSE_SELF);
        VanillaFallback.register(RmtIds.FX_CHANNEL_AURA_SELF, new VanillaFallback.Spec(ParticleTypes.END_ROD, 0, 0, 0.08, 0.0));
        VanillaFallback.register(RmtIds.FX_HEAL_PULSE_SELF, new VanillaFallback.Spec(ParticleTypes.END_ROD, 0, 0, 0.6, 0.0));
        VisualHooks.registerEffect(RmtIds.FX_CHANNEL_AURA_SELF);
        VisualHooks.registerEffect(RmtIds.FX_HEAL_PULSE_SELF);
    }

    /** "Output 1.36x, injuries -20%" (the injuries only when there are any). */
    public static net.minecraft.network.chat.Component outputLine(float output, float fatigue) {
        String x = String.format(java.util.Locale.ROOT, "%.2f", output);
        int injuries = Math.round(fatigue * 100);
        return injuries > 0 ? net.minecraft.network.chat.Component.translatable("gui.kegare.rmt.output_injured", x, "-" + injuries + "%")
                : net.minecraft.network.chat.Component.translatable("gui.kegare.rmt.output", x);
    }
}

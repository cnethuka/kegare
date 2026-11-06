package com.cnethuka.kegare.rmt.client;

import com.cnethuka.kegare.client.visual.VanillaFallback;
import com.cnethuka.kegare.rmt.OrbPayload;
import com.cnethuka.kegare.rmt.RmtIds;
import net.neoforged.neoforge.common.NeoForge;
import net.minecraft.core.particles.ParticleTypes;

/** Reverse Malice Technique's client side: the green orb, and vanilla stand-ins for its Photon effects. */
public final class RmtClient {
    private RmtClient() {}

    public static void init() {
        OrbPayload.setClientHandler(GreenOrbClient::handle);
        NeoForge.EVENT_BUS.addListener(GreenOrbClient::onClientTick);
        NeoForge.EVENT_BUS.addListener(GreenOrbClient::onLogout);
        VanillaFallback.register(RmtIds.FX_CHANNEL_AURA, new VanillaFallback.Spec(ParticleTypes.HAPPY_VILLAGER, 1, 6, 0.5, 0.0));
        VanillaFallback.register(RmtIds.FX_HEAL_PULSE, new VanillaFallback.Spec(ParticleTypes.HAPPY_VILLAGER, 1, 12, 0.6, 0.05));
        VanillaFallback.register(RmtIds.FX_ORB, new VanillaFallback.Spec(ParticleTypes.HAPPY_VILLAGER, 1, 4, 0.2, 0.0));
        VanillaFallback.register(RmtIds.FX_TRAIL_DOT, new VanillaFallback.Spec(ParticleTypes.HAPPY_VILLAGER, 1, 1, 0.0, 0.0));
        VanillaFallback.register(RmtIds.FX_UNLOCK_BURST, new VanillaFallback.Spec(ParticleTypes.HAPPY_VILLAGER, 1, 24, 0.5, 0.2));
    }
}

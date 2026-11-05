package com.cnethuka.kegare.rmt.client;

import com.cnethuka.kegare.client.visual.VanillaFallback;
import com.cnethuka.kegare.rmt.RmtIds;
import net.minecraft.core.particles.ParticleTypes;

/** Reverse Malice Technique's client side: vanilla stand-ins for its Photon effects. */
public final class RmtClient {
    private RmtClient() {}

    public static void init() {
        VanillaFallback.register(RmtIds.FX_CHANNEL_AURA, new VanillaFallback.Spec(ParticleTypes.HAPPY_VILLAGER, 1, 6, 0.5, 0.0));
        VanillaFallback.register(RmtIds.FX_HEAL_PULSE, new VanillaFallback.Spec(ParticleTypes.HAPPY_VILLAGER, 1, 12, 0.6, 0.05));
        VanillaFallback.register(RmtIds.FX_ORB, new VanillaFallback.Spec(ParticleTypes.HAPPY_VILLAGER, 1, 4, 0.2, 0.0));
        VanillaFallback.register(RmtIds.FX_UNLOCK_BURST, new VanillaFallback.Spec(ParticleTypes.HAPPY_VILLAGER, 1, 24, 0.5, 0.2));
    }
}

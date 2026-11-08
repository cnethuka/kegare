package com.cnethuka.kegare.severed.client;

import com.cnethuka.kegare.client.ClientData;
import com.cnethuka.kegare.client.TechniqueMode;
import com.cnethuka.kegare.melee.MeleeIntentPayload;
import com.cnethuka.kegare.network.SyncPayload;
import net.neoforged.neoforge.network.PacketDistributor;

/** G (DESIGN §3.5, §3.27): Blur for a Severed player, Technique Mode for everyone else, unchanged. */
public final class SeveredKeys {
    private SeveredKeys() {}

    /** Whether G toggles Blur rather than Technique Mode. */
    public static boolean gTogglesBlur(boolean born, boolean severed) {
        return born && severed;
    }

    public static void pressG() {
        SyncPayload data = ClientData.get();
        if (data != null && gTogglesBlur(data.born(), data.severed())) {
            PacketDistributor.sendToServer(new MeleeIntentPayload(MeleeIntentPayload.Action.BLUR, (byte) 0, (byte) 0));
        } else {
            TechniqueMode.toggle();
        }
    }
}

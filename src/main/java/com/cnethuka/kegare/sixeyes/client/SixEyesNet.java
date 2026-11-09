package com.cnethuka.kegare.sixeyes.client;

/** Client to server for the eyes: what the bearer pins (Attunement grows with use, DESIGN §3.32). */
final class SixEyesNet {
    private SixEyesNet() {}

    static void pinned(int entityId) {
        net.neoforged.neoforge.network.PacketDistributor.sendToServer(new com.cnethuka.kegare.sixeyes.Attunement.PinPayload(entityId));
    }
}

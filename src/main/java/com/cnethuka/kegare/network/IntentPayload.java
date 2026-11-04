package com.cnethuka.kegare.network;

import com.cnethuka.kegare.Kegare;
import com.cnethuka.kegare.technique.CastIntent;
import com.cnethuka.kegare.technique.CastManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Client to server: a key went down or up. Carries no values the server has to trust. */
public record IntentPayload(CastIntent intent) implements CustomPacketPayload {
    public static final Type<IntentPayload> TYPE = new Type<>(Kegare.id("intent"));

    public static final StreamCodec<FriendlyByteBuf, IntentPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> buf.writeEnum(payload.intent()),
            buf -> new IntentPayload(buf.readEnum(CastIntent.class)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** Runs on the server main thread. */
    static void handle(IntentPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) CastManager.handleIntent(player, payload.intent());
    }
}

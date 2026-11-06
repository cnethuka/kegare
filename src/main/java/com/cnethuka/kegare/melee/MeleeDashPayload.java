package com.cnethuka.kegare.melee;

import com.cnethuka.kegare.Kegare;
import java.util.function.Consumer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Server to client, only to its owner: the server has allowed a dash, and this is which way, how fast and how far. The only way a
 * dash starts on a client. {@link #STOP}: something stopped it.
 */
public record MeleeDashPayload(float dirX, float dirZ, float speed, float distance) implements CustomPacketPayload {
    public static final MeleeDashPayload STOP = new MeleeDashPayload(0, 0, 0, 0);
    public static final Type<MeleeDashPayload> TYPE = new Type<>(Kegare.id("melee_dash"));
    public static final StreamCodec<FriendlyByteBuf, MeleeDashPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeFloat(p.dirX());
                buf.writeFloat(p.dirZ());
                buf.writeFloat(p.speed());
                buf.writeFloat(p.distance());
            },
            buf -> new MeleeDashPayload(buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat()));

    /** Set by the client entry point; null on a dedicated server. */
    private static Consumer<MeleeDashPayload> clientHandler;

    public static void setClientHandler(Consumer<MeleeDashPayload> handler) {
        clientHandler = handler;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(MeleeDashPayload payload, IPayloadContext context) {
        if (clientHandler != null) clientHandler.accept(payload);
    }
}

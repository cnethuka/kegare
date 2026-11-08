package com.cnethuka.kegare.severed;

import com.cnethuka.kegare.Kegare;
import java.util.function.Consumer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Server to client, only to its owner (DESIGN §3.27): a Closing Line the server has allowed, which way, how fast and how far; the
 * client drives it ({@code client/RushDrive}). {@link #STOP}: something stopped it.
 */
public record RushPayload(float dirX, float dirZ, float speed, float distance) implements CustomPacketPayload {
    public static final RushPayload STOP = new RushPayload(0, 0, 0, 0);
    public static final Type<RushPayload> TYPE = new Type<>(Kegare.id("closing_line"));
    public static final StreamCodec<FriendlyByteBuf, RushPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeFloat(p.dirX());
                buf.writeFloat(p.dirZ());
                buf.writeFloat(p.speed());
                buf.writeFloat(p.distance());
            },
            buf -> new RushPayload(buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat()));

    /** Set by the client entry point; null on a dedicated server. */
    private static Consumer<RushPayload> clientHandler;

    public static void setClientHandler(Consumer<RushPayload> handler) {
        clientHandler = handler;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(RushPayload payload, IPayloadContext context) {
        if (clientHandler != null) clientHandler.accept(payload);
    }
}

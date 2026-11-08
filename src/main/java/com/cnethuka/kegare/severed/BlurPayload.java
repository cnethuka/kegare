package com.cnethuka.kegare.severed;

import com.cnethuka.kegare.Kegare;
import java.util.function.Consumer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Server to client, only to its owner (DESIGN §3.27): whether Blur is on, and how it moves (the client drives it); until when they're
 * winded and how long that is (for the HUD). Sent whenever any of it changes.
 */
public record BlurPayload(boolean on, float speed, float turnDegrees, float acceleration, float skid, long windedUntil, int windedTicks)
        implements CustomPacketPayload {
    public static final BlurPayload OFF = new BlurPayload(false, 0, 0, 0, 0, 0, 0);
    public static final Type<BlurPayload> TYPE = new Type<>(Kegare.id("blur"));
    public static final StreamCodec<FriendlyByteBuf, BlurPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeBoolean(p.on());
                buf.writeFloat(p.speed());
                buf.writeFloat(p.turnDegrees());
                buf.writeFloat(p.acceleration());
                buf.writeFloat(p.skid());
                buf.writeVarLong(p.windedUntil());
                buf.writeVarInt(p.windedTicks());
            },
            buf -> new BlurPayload(buf.readBoolean(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readVarLong(), buf.readVarInt()));

    /** Set by the client entry point; null on a dedicated server. */
    private static Consumer<BlurPayload> clientHandler;

    public static void setClientHandler(Consumer<BlurPayload> handler) {
        clientHandler = handler;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(BlurPayload payload, IPayloadContext context) {
        if (clientHandler != null) clientHandler.accept(payload);
    }
}

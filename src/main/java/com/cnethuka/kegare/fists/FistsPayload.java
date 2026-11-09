package com.cnethuka.kegare.fists;

import com.cnethuka.kegare.Kegare;
import java.util.function.Consumer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Server to client: what's round a player's fists as whoever this is sent to should see it ({@link FistsTell}). {@code color} is the
 * Malice color (RGB) of the face they show that viewer; {@code flags} say what's there ({@link #BURN}: Fists are on; {@link #ZONE}: in the zone).
 */
public record FistsPayload(int entityId, int color, int flags) implements CustomPacketPayload {
    public static final int BURN = 1;
    /** In the zone: a faint flicker of black sparks round both fists. */
    public static final int ZONE = 2;
    public static final Type<FistsPayload> TYPE = new Type<>(Kegare.id("fists"));
    public static final StreamCodec<FriendlyByteBuf, FistsPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeVarInt(p.entityId());
                buf.writeInt(p.color());
                buf.writeByte(p.flags());
            },
            buf -> new FistsPayload(buf.readVarInt(), buf.readInt(), buf.readByte()));

    /** Set by the client entry point; null on a dedicated server. */
    private static Consumer<FistsPayload> clientHandler;

    public static void setClientHandler(Consumer<FistsPayload> handler) {
        clientHandler = handler;
    }

    public boolean has(int flag) {
        return (flags & flag) != 0;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(FistsPayload payload, IPayloadContext context) {
        if (clientHandler != null) context.enqueueWork(() -> clientHandler.accept(payload));
    }
}

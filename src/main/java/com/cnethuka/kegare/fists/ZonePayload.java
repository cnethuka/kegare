package com.cnethuka.kegare.fists;

import com.cnethuka.kegare.Kegare;
import java.util.function.Consumer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Server to client, to its owner: in the zone until {@code until} (game time) of {@code ticks} in all, or out of it (0), for the HUD cue. */
public record ZonePayload(long until, int ticks) implements CustomPacketPayload {
    public static final Type<ZonePayload> TYPE = new Type<>(Kegare.id("zone"));
    public static final StreamCodec<FriendlyByteBuf, ZonePayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeVarLong(p.until());
                buf.writeVarInt(p.ticks());
            },
            buf -> new ZonePayload(buf.readVarLong(), buf.readVarInt()));

    private static Consumer<ZonePayload> clientHandler;

    public static void setClientHandler(Consumer<ZonePayload> handler) {
        clientHandler = handler;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ZonePayload payload, IPayloadContext context) {
        if (clientHandler != null) clientHandler.accept(payload);
    }
}

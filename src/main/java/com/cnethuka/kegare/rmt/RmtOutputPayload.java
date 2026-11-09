package com.cnethuka.kegare.rmt;

import com.cnethuka.kegare.Kegare;
import java.util.function.Consumer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Server to client, only to its owner (DESIGN §3.17): their Reverse Malice Technique output right now and how much injuries take off it,
 * for the Sorcerer screen, and whether they know how to heal others (its special). Sent when any of it changes.
 */
public record RmtOutputPayload(float output, float fatigue, boolean knowsOthers) implements CustomPacketPayload {
    public static final RmtOutputPayload NONE = new RmtOutputPayload(1f, 0f, false);
    public static final Type<RmtOutputPayload> TYPE = new Type<>(Kegare.id("rmt_output"));
    public static final StreamCodec<FriendlyByteBuf, RmtOutputPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeFloat(p.output());
                buf.writeFloat(p.fatigue());
                buf.writeBoolean(p.knowsOthers());
            },
            buf -> new RmtOutputPayload(buf.readFloat(), buf.readFloat(), buf.readBoolean()));

    private static Consumer<RmtOutputPayload> clientHandler;

    public static void setClientHandler(Consumer<RmtOutputPayload> handler) {
        clientHandler = handler;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(RmtOutputPayload payload, IPayloadContext context) {
        if (clientHandler != null) clientHandler.accept(payload);
    }
}

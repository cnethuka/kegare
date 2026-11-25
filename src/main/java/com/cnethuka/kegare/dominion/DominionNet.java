package com.cnethuka.kegare.dominion;

import com.cnethuka.kegare.Kegare;
import java.util.function.Consumer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Dominions' payloads (DESIGN §3.35): all server to client, all decided already. */
public final class DominionNet {
    private DominionNet() {}

    /** The owner's Dominion Meter (0 to 100) and whether to show it: only when they have a Dominion to open with it. */
    public record Meter(float meter, boolean shown) implements CustomPacketPayload {
        public static final Type<Meter> TYPE = new Type<>(Kegare.id("dominion_meter"));
        public static final StreamCodec<FriendlyByteBuf, Meter> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> {
                    buf.writeFloat(p.meter());
                    buf.writeBoolean(p.shown());
                },
                buf -> new Meter(buf.readFloat(), buf.readBoolean()));
        public static Consumer<Meter> clientHandler;

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        public static void handle(Meter p, IPayloadContext context) {
            if (clientHandler != null) context.enqueueWork(() -> clientHandler.accept(p));
        }
    }

    /**
     * To a player, as a barrier closes (or a clash changes it): whether they're trapped inside it ({@code inside}), so their own client
     * keeps them on that side of it. Everyone not told they're inside stays outside.
     */
    public record Trapped(int dominion, boolean inside) implements CustomPacketPayload {
        public static final Type<Trapped> TYPE = new Type<>(Kegare.id("dominion_trapped"));
        public static final StreamCodec<FriendlyByteBuf, Trapped> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> {
                    buf.writeVarInt(p.dominion());
                    buf.writeBoolean(p.inside());
                },
                buf -> new Trapped(buf.readVarInt(), buf.readBoolean()));
        public static Consumer<Trapped> clientHandler;

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        public static void handle(Trapped p, IPayloadContext context) {
            if (clientHandler != null) context.enqueueWork(() -> clientHandler.accept(p));
        }
    }

    /**
     * A moment to draw and hear where it happened: the barrier closing (with its name, for those inside), a hit on it, it breaking, it
     * ending. {@code strength} is the hit's (or 0); {@code complete} and {@code cracked} say which kind of shell.
     */
    public record Moment(int kind, int dominion, double x, double y, double z, float radius, float strength, boolean complete, boolean cracked,
                         int shellColor, String ability) implements CustomPacketPayload {
        public static final int CLOSED = 0, HIT = 1, BROKE = 2, ENDED = 3, OPENING = 4;
        public static final Type<Moment> TYPE = new Type<>(Kegare.id("dominion_moment"));
        public static final StreamCodec<FriendlyByteBuf, Moment> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> {
                    buf.writeByte(p.kind());
                    buf.writeVarInt(p.dominion());
                    buf.writeDouble(p.x());
                    buf.writeDouble(p.y());
                    buf.writeDouble(p.z());
                    buf.writeFloat(p.radius());
                    buf.writeFloat(p.strength());
                    buf.writeBoolean(p.complete());
                    buf.writeBoolean(p.cracked());
                    buf.writeInt(p.shellColor());
                    buf.writeUtf(p.ability(), 256);
                },
                buf -> new Moment(buf.readByte(), buf.readVarInt(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readFloat(),
                        buf.readFloat(), buf.readBoolean(), buf.readBoolean(), buf.readInt(), buf.readUtf(256)));
        public static Consumer<Moment> clientHandler;

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        public static void handle(Moment p, IPayloadContext context) {
            if (clientHandler != null) context.enqueueWork(() -> clientHandler.accept(p));
        }
    }
}

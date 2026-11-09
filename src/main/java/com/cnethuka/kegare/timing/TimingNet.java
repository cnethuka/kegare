package com.cnethuka.kegare.timing;

import com.cnethuka.kegare.Kegare;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Sub-tick timing's packets (DESIGN §3.29). All of them are handled on the network thread, as they arrive, so a tick's wait never gets
 * into a time: what needs the server's state is queued for its thread.
 */
public final class TimingNet {
    private TimingNet() {}

    /** C→S: an input, stamped with the client's monotonic clock (ms), sent just before the packet that does it. */
    public record Stamp(int input, long clientMs) implements CustomPacketPayload {
        public static final Type<Stamp> TYPE = new Type<>(Kegare.id("timing_stamp"));
        public static final StreamCodec<FriendlyByteBuf, Stamp> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> {
                    buf.writeByte(p.input());
                    buf.writeLong(p.clientMs());
                },
                buf -> new Stamp(buf.readByte(), buf.readLong()));

        public Stamp(TimedInput input, long clientMs) {
            this(input.ordinal(), clientMs);
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        public static void handle(Stamp payload, IPayloadContext context) {
            double arrival = SubTick.nowMs();
            TimedInput input = TimedInput.byId(payload.input());
            if (input != null && context.player() != null) {
                SubTick.arrived(context.player().getUUID(), input, payload.clientMs(), arrival);
                SubTick.notifyArrival(context.player(), input);
            }
        }
    }

    /** S→C: a ping, answered at once with the client's clock. */
    public record Ping(int id) implements CustomPacketPayload {
        public static final Type<Ping> TYPE = new Type<>(Kegare.id("timing_ping"));
        public static final StreamCodec<FriendlyByteBuf, Ping> STREAM_CODEC = StreamCodec.of((buf, p) -> buf.writeVarInt(p.id()), buf -> new Ping(buf.readVarInt()));
        /** Set by the client entry point: the client's clock, in ms. */
        private static java.util.function.LongSupplier clientClock;

        public static void setClientClock(java.util.function.LongSupplier clock) {
            clientClock = clock;
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        public static void handle(Ping payload, IPayloadContext context) {
            if (clientClock != null) context.reply(new Pong(payload.id(), clientClock.getAsLong()));
        }
    }

    /** C→S: the answer to a ping, with the client's clock as it answered. */
    public record Pong(int id, long clientMs) implements CustomPacketPayload {
        public static final Type<Pong> TYPE = new Type<>(Kegare.id("timing_pong"));
        public static final StreamCodec<FriendlyByteBuf, Pong> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> {
                    buf.writeVarInt(p.id());
                    buf.writeLong(p.clientMs());
                },
                buf -> new Pong(buf.readVarInt(), buf.readLong()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        public static void handle(Pong payload, IPayloadContext context) {
            double arrival = SubTick.nowMs();
            if (context.player() != null) ClockSync.answered(context.player().getUUID(), payload.id(), payload.clientMs(), arrival);
        }
    }
}

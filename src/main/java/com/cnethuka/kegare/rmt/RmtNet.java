package com.cnethuka.kegare.rmt;

import com.cnethuka.kegare.Kegare;
import java.util.function.Consumer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Heal-others' payloads (DESIGN §3.17), server to client. */
public final class RmtNet {
    private RmtNet() {}

    /**
     * To everyone near: {@code caster} is healing {@code target} (the white-and-green link between them, the tell), or has stopped. Sent
     * again every second while it lasts, so someone who comes in range sees it too; a link not heard of for a while goes out.
     */
    public record Link(int caster, int target, boolean on) implements CustomPacketPayload {
        public static final Type<Link> TYPE = new Type<>(Kegare.id("rmt_link"));
        public static final StreamCodec<FriendlyByteBuf, Link> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> {
                    buf.writeVarInt(p.caster());
                    buf.writeVarInt(p.target());
                    buf.writeBoolean(p.on());
                },
                buf -> new Link(buf.readVarInt(), buf.readVarInt(), buf.readBoolean()));
        private static Consumer<Link> clientHandler;

        public static void setClientHandler(Consumer<Link> handler) {
            clientHandler = handler;
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        public static void handle(Link payload, IPayloadContext context) {
            if (clientHandler != null) clientHandler.accept(payload);
        }
    }

    /** Only to the eligible player: {@code target} glows soft green for them until {@code until} (game time). Nobody else is told. */
    public record Moment(int target, long until) implements CustomPacketPayload {
        public static final Moment NONE = new Moment(-1, 0);
        public static final Type<Moment> TYPE = new Type<>(Kegare.id("rmt_moment"));
        public static final StreamCodec<FriendlyByteBuf, Moment> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> {
                    buf.writeVarInt(p.target());
                    buf.writeVarLong(p.until());
                },
                buf -> new Moment(buf.readVarInt(), buf.readVarLong()));
        private static Consumer<Moment> clientHandler;

        public static void setClientHandler(Consumer<Moment> handler) {
            clientHandler = handler;
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        public static void handle(Moment payload, IPayloadContext context) {
            if (clientHandler != null) clientHandler.accept(payload);
        }
    }
}

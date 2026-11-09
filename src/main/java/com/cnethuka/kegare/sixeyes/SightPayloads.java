package com.cnethuka.kegare.sixeyes;

import com.cnethuka.kegare.Kegare;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** What the eyes see, server to the bearer only (DESIGN §3.32). Nothing here is ever sent to anyone else. */
public final class SightPayloads {
    private SightPayloads() {}

    /** Hands each payload to the client, once its entry point has set one. */
    public static final class Handlers {
        public static Consumer<Players> players;
        public static Consumer<Mobs> mobs;
        public static Consumer<Pulse> pulse;
    }

    /**
     * Every other player in the bearer's dimension: who (the eyes know them by their energy, whatever face they wear), where, their Malice
     * color and capacity, or a void. {@code bearer} false: the eyes are gone, forget everything.
     */
    public record Players(boolean bearer, List<Dot> dots) implements CustomPacketPayload {
        public record Dot(UUID uuid, int entityId, String name, double x, double y, double z, int color, int capacity, boolean severed) {
            static final StreamCodec<FriendlyByteBuf, Dot> CODEC = StreamCodec.of((buf, d) -> {
                buf.writeUUID(d.uuid());
                buf.writeVarInt(d.entityId());
                buf.writeUtf(d.name(), 64);
                buf.writeDouble(d.x());
                buf.writeDouble(d.y());
                buf.writeDouble(d.z());
                buf.writeInt(d.color());
                buf.writeVarInt(d.capacity());
                buf.writeBoolean(d.severed());
            }, buf -> new Dot(buf.readUUID(), buf.readVarInt(), buf.readUtf(64), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readInt(),
                    buf.readVarInt(), buf.readBoolean()));
        }

        public static final Type<Players> TYPE = new Type<>(Kegare.id("sight_players"));
        public static final StreamCodec<FriendlyByteBuf, Players> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.BOOL, Players::bearer, Dot.CODEC.apply(ByteBufCodecs.list()), Players::dots, Players::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        public static void handle(Players p, IPayloadContext context) {
            if (Handlers.players != null) context.enqueueWork(() -> Handlers.players.accept(p));
        }
    }

    /** Every living mob loaded in the bearer's dimension: where, what, and whether its flow is hostile. */
    public record Mobs(List<Dot> dots) implements CustomPacketPayload {
        public record Dot(int entityId, int type, float x, float y, float z, boolean hostile) {
            static final StreamCodec<FriendlyByteBuf, Dot> CODEC = StreamCodec.of((buf, d) -> {
                buf.writeVarInt(d.entityId());
                buf.writeVarInt(d.type());
                buf.writeFloat(d.x());
                buf.writeFloat(d.y());
                buf.writeFloat(d.z());
                buf.writeBoolean(d.hostile());
            }, buf -> new Dot(buf.readVarInt(), buf.readVarInt(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readBoolean()));
        }

        public static final Type<Mobs> TYPE = new Type<>(Kegare.id("sight_mobs"));
        public static final StreamCodec<FriendlyByteBuf, Mobs> STREAM_CODEC = StreamCodec.composite(
                Dot.CODEC.apply(ByteBufCodecs.list()), Mobs::dots, Mobs::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        public static void handle(Mobs p, IPayloadContext context) {
            if (Handlers.mobs != null) context.enqueueWork(() -> Handlers.mobs.accept(p));
        }
    }

    /** An outline pulses: a player started a cast ({@link #CAST}), or a hostile mob is about to act (the other kinds). */
    public record Pulse(int entityId, int kind) implements CustomPacketPayload {
        public static final int CAST = 0, LOOSE = 1, SWELL = 2, SWING = 3, CHARGE = 4, SPELL = 5, BEAM = 6, SONIC_BOOM = 7, DRAGON = 8,
                SKULL = 9, THROW = 10, BULLET = 11, LOCK = 12, LAST_KIND = LOCK;
        public static final Type<Pulse> TYPE = new Type<>(Kegare.id("sight_pulse"));
        public static final StreamCodec<FriendlyByteBuf, Pulse> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Pulse::entityId, ByteBufCodecs.VAR_INT, Pulse::kind, Pulse::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        public static void handle(Pulse p, IPayloadContext context) {
            if (Handlers.pulse != null) context.enqueueWork(() -> Handlers.pulse.accept(p));
        }
    }
}

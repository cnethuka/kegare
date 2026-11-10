package com.cnethuka.kegare.innate_technique.time_manipulation;

import com.cnethuka.kegare.Kegare;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Time Manipulation's packets (DESIGN §3.26), all server to client and cosmetic: the server has decided everything already. Handlers are
 * set by the client entry point and null on a dedicated server.
 */
public final class TimeNet {
    private TimeNet() {}

    private static void writeVec(FriendlyByteBuf buf, Vec3 v) {
        buf.writeDouble(v.x);
        buf.writeDouble(v.y);
        buf.writeDouble(v.z);
    }

    private static Vec3 readVec(FriendlyByteBuf buf) {
        return new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
    }

    /**
     * One of the technique's moments, to everyone tracking it.
     *
     * @param entity RETURN / REWIND: who moved; FAST_FORWARD: the caster; SLOWED: who was hit; STASIS / STASIS_END: the field's id
     * @param path   RETURN / REWIND: where they were, oldest first (drawn backward); STASIS: its centre
     * @param value  STASIS: its radius
     * @param ticks  SLOWED / STASIS: how long it lasts
     */
    public record Fx(Kind kind, int entity, List<Vec3> path, float value, int ticks) implements CustomPacketPayload {
        public enum Kind { RETURN, REWIND, FAST_FORWARD, SLOWED, STASIS, STASIS_END }

        public static final Type<Fx> TYPE = new Type<>(Kegare.id("time_fx"));
        public static final StreamCodec<FriendlyByteBuf, Fx> STREAM_CODEC = StreamCodec.ofMember(Fx::write, Fx::read);
        private static Consumer<Fx> handler;

        public static void setClientHandler(Consumer<Fx> h) {
            handler = h;
        }

        private void write(FriendlyByteBuf buf) {
            buf.writeEnum(kind);
            buf.writeVarInt(entity);
            buf.writeVarInt(path.size());
            for (Vec3 v : path) writeVec(buf, v);
            buf.writeFloat(value);
            buf.writeVarInt(ticks);
        }

        private static Fx read(FriendlyByteBuf buf) {
            Kind kind = buf.readEnum(Kind.class);
            int entity = buf.readVarInt();
            int n = Math.min(buf.readVarInt(), 512);
            List<Vec3> path = new ArrayList<>(n);
            for (int i = 0; i < n; i++) path.add(readVec(buf));
            return new Fx(kind, entity, path, buf.readFloat(), buf.readVarInt());
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        public static void handle(Fx payload, IPayloadContext context) {
            if (handler != null) handler.accept(payload);
        }
    }

    /**
     * Point Maker's mark, to its caster only (nobody else sees it). {@code block} is the marked block (its top face); the hands are
     * where they stood when it was marked (radians, from twelve, clockwise); it lasts until {@code until} (level game time). An absent
     * mark clears it.
     */
    public record Mark(boolean present, BlockPos block, float hourHand, float minuteHand, long until) implements CustomPacketPayload {
        public static final Type<Mark> TYPE = new Type<>(Kegare.id("time_mark"));
        public static final StreamCodec<FriendlyByteBuf, Mark> STREAM_CODEC = StreamCodec.ofMember(Mark::write, Mark::read);
        private static Consumer<Mark> handler;

        public static Mark none() {
            return new Mark(false, BlockPos.ZERO, 0f, 0f, 0L);
        }

        public static void setClientHandler(Consumer<Mark> h) {
            handler = h;
        }

        private void write(FriendlyByteBuf buf) {
            buf.writeBoolean(present);
            buf.writeBlockPos(block);
            buf.writeFloat(hourHand);
            buf.writeFloat(minuteHand);
            buf.writeVarLong(until);
        }

        private static Mark read(FriendlyByteBuf buf) {
            return new Mark(buf.readBoolean(), buf.readBlockPos(), buf.readFloat(), buf.readFloat(), buf.readVarLong());
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        public static void handle(Mark payload, IPayloadContext context) {
            if (handler != null) handler.accept(payload);
        }
    }

    /**
     * Standstill's state, to every player in the level: whether time is stopped, round where, until when, which entities inside still
     * move (the caster and Time Manipulation holders), and how many ticks a projectile they throw flies before it hangs (the client
     * counts those itself). Everything else inside the radius is frozen. Resent on every change and once more when it ends.
     */
    public record Stop(boolean active, int caster, Vec3 centre, float radius, long until, List<Integer> free, int flightTicks)
            implements CustomPacketPayload {
        public static final Type<Stop> TYPE = new Type<>(Kegare.id("time_stop"));
        public static final StreamCodec<FriendlyByteBuf, Stop> STREAM_CODEC = StreamCodec.ofMember(Stop::write, Stop::read);
        private static Consumer<Stop> handler;

        public static Stop none() {
            return new Stop(false, -1, Vec3.ZERO, 0f, 0L, List.of(), 0);
        }

        public static void setClientHandler(Consumer<Stop> h) {
            handler = h;
        }

        private void write(FriendlyByteBuf buf) {
            buf.writeBoolean(active);
            buf.writeVarInt(caster + 1);
            writeVec(buf, centre);
            buf.writeFloat(radius);
            buf.writeVarLong(until);
            buf.writeVarInt(free.size());
            for (int id : free) buf.writeVarInt(id);
            buf.writeVarInt(flightTicks);
        }

        private static Stop read(FriendlyByteBuf buf) {
            boolean active = buf.readBoolean();
            int caster = buf.readVarInt() - 1;
            Vec3 centre = readVec(buf);
            float radius = buf.readFloat();
            long until = buf.readVarLong();
            int n = Math.min(buf.readVarInt(), 4096);
            List<Integer> free = new ArrayList<>(n);
            for (int i = 0; i < n; i++) free.add(buf.readVarInt());
            return new Stop(active, caster, centre, radius, until, free, buf.readVarInt());
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        public static void handle(Stop payload, IPayloadContext context) {
            if (handler != null) handler.accept(payload);
        }
    }
}

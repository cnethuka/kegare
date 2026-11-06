package com.cnethuka.kegare.rmt;

import com.cnethuka.kegare.Kegare;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Server to ONE client (the orb's finder only): show the green orb and the route the server rolled, say how far along it is,
 * hide it, or play its burst because it was taken in. Cosmetic: the server alone decides whether it's being followed.
 *
 * @param path SHOW: the route's points. TAKEN: where it was taken in. Otherwise empty.
 * @param value SHOW: how many ticks the whole route takes. PROGRESS: how many of them are done.
 */
public record OrbPayload(Action action, List<Vec3> path, int value) implements CustomPacketPayload {
    public enum Action { SHOW, PROGRESS, HIDE, TAKEN }

    public static final Type<OrbPayload> TYPE = new Type<>(Kegare.id("green_orb"));
    public static final StreamCodec<FriendlyByteBuf, OrbPayload> STREAM_CODEC = StreamCodec.ofMember(OrbPayload::write, OrbPayload::read);

    /** Set by the client entry point; null on a dedicated server. */
    private static Consumer<OrbPayload> clientHandler;

    public static void setClientHandler(Consumer<OrbPayload> handler) {
        clientHandler = handler;
    }

    static OrbPayload show(OrbPath path, int totalTicks) {
        return new OrbPayload(Action.SHOW, path.points(), totalTicks);
    }

    static OrbPayload progress(int ticks) {
        return new OrbPayload(Action.PROGRESS, List.of(), ticks);
    }

    static OrbPayload hide() {
        return new OrbPayload(Action.HIDE, List.of(), 0);
    }

    static OrbPayload taken(Vec3 at) {
        return new OrbPayload(Action.TAKEN, List.of(at), 0);
    }

    private void write(FriendlyByteBuf buf) {
        buf.writeEnum(action);
        buf.writeVarInt(path.size());
        for (Vec3 point : path) {
            buf.writeDouble(point.x);
            buf.writeDouble(point.y);
            buf.writeDouble(point.z);
        }
        buf.writeVarInt(value);
    }

    private static OrbPayload read(FriendlyByteBuf buf) {
        Action action = buf.readEnum(Action.class);
        int size = buf.readVarInt();
        List<Vec3> path = new ArrayList<>(size);
        for (int i = 0; i < size; i++) path.add(new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()));
        return new OrbPayload(action, path, buf.readVarInt());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(OrbPayload payload, IPayloadContext context) {
        if (clientHandler != null) clientHandler.accept(payload);
    }
}

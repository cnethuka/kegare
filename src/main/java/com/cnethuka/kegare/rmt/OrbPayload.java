package com.cnethuka.kegare.rmt;

import com.cnethuka.kegare.Kegare;
import java.util.function.Consumer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Server to ONE client (the orb's finder only): show the green orb at a position the server picked, hide it, or play
 * its burst because it was taken in. Cosmetic: the server alone decides whether it was looked at.
 */
public record OrbPayload(Action action, Vec3 pos, int lifetimeTicks) implements CustomPacketPayload {
    public enum Action { SHOW, HIDE, TAKEN }

    public static final Type<OrbPayload> TYPE = new Type<>(Kegare.id("green_orb"));
    public static final StreamCodec<FriendlyByteBuf, OrbPayload> STREAM_CODEC = StreamCodec.ofMember(OrbPayload::write, OrbPayload::read);

    /** Set by the client entry point; null on a dedicated server. */
    private static Consumer<OrbPayload> clientHandler;

    public static void setClientHandler(Consumer<OrbPayload> handler) {
        clientHandler = handler;
    }

    private void write(FriendlyByteBuf buf) {
        buf.writeEnum(action);
        buf.writeDouble(pos.x);
        buf.writeDouble(pos.y);
        buf.writeDouble(pos.z);
        buf.writeVarInt(lifetimeTicks);
    }

    private static OrbPayload read(FriendlyByteBuf buf) {
        return new OrbPayload(buf.readEnum(Action.class), new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()), buf.readVarInt());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(OrbPayload payload, IPayloadContext context) {
        if (clientHandler != null) clientHandler.accept(payload);
    }
}

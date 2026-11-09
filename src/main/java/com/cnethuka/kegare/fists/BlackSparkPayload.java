package com.cnethuka.kegare.fists;

import com.cnethuka.kegare.Kegare;
import java.util.function.Consumer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Server to client: a Black Spark went off ({@link BlackSparkFx}): where it met the body, which way the hit went, the Malice color its
 * fringe is drawn in for whoever this is sent to, and who struck and who was struck.
 */
public record BlackSparkPayload(Vec3 at, Vec3 direction, int color, int attackerId, int victimId) implements CustomPacketPayload {
    public static final Type<BlackSparkPayload> TYPE = new Type<>(Kegare.id("black_spark"));
    public static final StreamCodec<FriendlyByteBuf, BlackSparkPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeDouble(p.at().x);
                buf.writeDouble(p.at().y);
                buf.writeDouble(p.at().z);
                buf.writeFloat((float) p.direction().x);
                buf.writeFloat((float) p.direction().y);
                buf.writeFloat((float) p.direction().z);
                buf.writeInt(p.color());
                buf.writeVarInt(p.attackerId());
                buf.writeVarInt(p.victimId());
            },
            buf -> new BlackSparkPayload(new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()),
                    new Vec3(buf.readFloat(), buf.readFloat(), buf.readFloat()), buf.readInt(), buf.readVarInt(), buf.readVarInt()));

    private static Consumer<BlackSparkPayload> clientHandler;

    public static void setClientHandler(Consumer<BlackSparkPayload> handler) {
        clientHandler = handler;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(BlackSparkPayload payload, IPayloadContext context) {
        if (clientHandler != null) clientHandler.accept(payload);
    }
}

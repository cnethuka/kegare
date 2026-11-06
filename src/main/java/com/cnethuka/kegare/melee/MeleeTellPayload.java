package com.cnethuka.kegare.melee;

import com.cnethuka.kegare.Kegare;
import java.util.function.Consumer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Server to client: what a player's ready Reinforced Strike looks like to whoever this is sent to ({@link MeleeTell}), or that it's over. */
public record MeleeTellPayload(int entityId, MeleeTell.Look look) implements CustomPacketPayload {
    public static final Type<MeleeTellPayload> TYPE = new Type<>(Kegare.id("melee_tell"));
    public static final StreamCodec<FriendlyByteBuf, MeleeTellPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeVarInt(p.entityId());
                buf.writeEnum(p.look().kind());
                buf.writeInt(p.look().color());
            },
            buf -> new MeleeTellPayload(buf.readVarInt(), new MeleeTell.Look(buf.readEnum(MeleeTell.Kind.class), buf.readInt())));

    /** Set by the client entry point; null on a dedicated server. */
    private static Consumer<MeleeTellPayload> clientHandler;

    public static void setClientHandler(Consumer<MeleeTellPayload> handler) {
        clientHandler = handler;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(MeleeTellPayload payload, IPayloadContext context) {
        if (clientHandler != null) clientHandler.accept(payload);
    }
}

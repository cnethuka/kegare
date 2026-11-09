package com.cnethuka.kegare.reinforcement;

import com.cnethuka.kegare.Kegare;
import java.util.function.Consumer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Server to client: what flows round a player's body as whoever this is sent to should see it ({@link AuraTell}). {@code color} is the
 * Malice color (RGB) of the face they show that viewer; {@code flags} say what's there.
 */
public record AuraPayload(int entityId, int color, int flags) implements CustomPacketPayload {
    /** The Reinforcement ability is on: the full aura. */
    public static final int FULL = 1;
    /** Background Reinforcement alone: the same aura, much calmer and fainter. */
    public static final int CALM = 2;
    /** In a fight: fuller and faster. */
    public static final int FIGHT = 4;
    /** Background RMT is running on them and this viewer bears the Six Eyes: a faint green shimmer (DESIGN §3.31). */
    public static final int SHIMMER = 8;
    public static final Type<AuraPayload> TYPE = new Type<>(Kegare.id("aura"));
    public static final StreamCodec<FriendlyByteBuf, AuraPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeVarInt(p.entityId());
                buf.writeInt(p.color());
                buf.writeByte(p.flags());
            },
            buf -> new AuraPayload(buf.readVarInt(), buf.readInt(), buf.readByte()));

    /** Set by the client entry point; null on a dedicated server. */
    private static Consumer<AuraPayload> clientHandler;

    public static void setClientHandler(Consumer<AuraPayload> handler) {
        clientHandler = handler;
    }

    public boolean has(int flag) {
        return (flags & flag) != 0;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(AuraPayload payload, IPayloadContext context) {
        if (clientHandler != null) context.enqueueWork(() -> clientHandler.accept(payload));
    }
}

package com.cnethuka.kegare.severed;

import com.cnethuka.kegare.Kegare;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Server to client, only to a Severed player: the entities they sense right now (DESIGN §3.27). Empty: none. */
public record SensesPayload(List<Integer> ids) implements CustomPacketPayload {
    public static final Type<SensesPayload> TYPE = new Type<>(Kegare.id("senses"));
    public static final StreamCodec<FriendlyByteBuf, SensesPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeVarInt(p.ids().size());
                for (int id : p.ids()) buf.writeVarInt(id);
            },
            buf -> {
                int n = Math.min(buf.readVarInt(), 1024);
                List<Integer> ids = new ArrayList<>(n);
                for (int i = 0; i < n; i++) ids.add(buf.readVarInt());
                return new SensesPayload(ids);
            });

    /** Set by the client entry point; null on a dedicated server. */
    private static Consumer<SensesPayload> clientHandler;

    public static void setClientHandler(Consumer<SensesPayload> handler) {
        clientHandler = handler;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SensesPayload payload, IPayloadContext context) {
        if (clientHandler != null) clientHandler.accept(payload);
    }
}

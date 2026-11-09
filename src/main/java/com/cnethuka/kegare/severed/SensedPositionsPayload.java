package com.cnethuka.kegare.severed;

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
 * Server to client, only to a Severed player, every tick: the real positions of what they sense that everyone's eyes see behind where it is
 * (too fast for the eye, DESIGN §3.30). Sensing isn't seeing, so the outline goes where they really are. Empty: none any more.
 */
public record SensedPositionsPayload(List<Entry> entries) implements CustomPacketPayload {
    public record Entry(int id, Vec3 pos) {}

    public static final Type<SensedPositionsPayload> TYPE = new Type<>(Kegare.id("sensed_positions"));
    public static final StreamCodec<FriendlyByteBuf, SensedPositionsPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeVarInt(p.entries().size());
                for (Entry e : p.entries()) {
                    buf.writeVarInt(e.id());
                    buf.writeDouble(e.pos().x);
                    buf.writeDouble(e.pos().y);
                    buf.writeDouble(e.pos().z);
                }
            },
            buf -> {
                int n = Math.min(buf.readVarInt(), 1024);
                List<Entry> entries = new ArrayList<>(n);
                for (int i = 0; i < n; i++) entries.add(new Entry(buf.readVarInt(), new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble())));
                return new SensedPositionsPayload(entries);
            });

    private static Consumer<SensedPositionsPayload> clientHandler;

    public static void setClientHandler(Consumer<SensedPositionsPayload> handler) {
        clientHandler = handler;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SensedPositionsPayload payload, IPayloadContext context) {
        if (clientHandler != null) clientHandler.accept(payload);
    }
}

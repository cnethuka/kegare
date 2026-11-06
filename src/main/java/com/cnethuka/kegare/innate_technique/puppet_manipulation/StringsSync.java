package com.cnethuka.kegare.innate_technique.puppet_manipulation;

import com.cnethuka.kegare.Kegare;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * What everyone sees of a string (cosmetic only; the string IS the tell, so it goes to everyone in the dimension). The
 * whole list goes out on any change, and to a player arriving in the dimension.
 */
public final class StringsSync {
    private StringsSync() {}

    static void broadcast(MinecraftServer server) {
        for (ServerLevel level : server.getAllLevels()) {
            PacketDistributor.sendToPlayersInDimension(level, new Payload(Strings.entries(level)));
        }
    }

    static void sendTo(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new Payload(Strings.entries(player.serverLevel())));
    }

    /**
     * One string, by entity network IDs. {@code second} is -1 until a Tie or Leash. {@code limit} is how far it stretches
     * before it's taut: the snap range while waiting, the tether or leash's reach after, 0 while it's pulling (always taut).
     */
    public record Entry(int caster, int first, int second, Strings.Phase phase, float limit) {}

    public record Payload(List<Entry> entries) implements CustomPacketPayload {
        public static final Type<Payload> TYPE = new Type<>(Kegare.id("strings_state"));
        public static final StreamCodec<FriendlyByteBuf, Payload> STREAM_CODEC = StreamCodec.ofMember(Payload::write, Payload::read);

        /** Set by the client entry point; null on a dedicated server. */
        private static Consumer<Payload> clientHandler;

        public static void setClientHandler(Consumer<Payload> handler) {
            clientHandler = handler;
        }

        private void write(FriendlyByteBuf buf) {
            buf.writeCollection(entries, (b, e) -> {
                b.writeVarInt(e.caster());
                b.writeVarInt(e.first());
                b.writeVarInt(e.second() + 1);
                b.writeEnum(e.phase());
                b.writeFloat(e.limit());
            });
        }

        private static Payload read(FriendlyByteBuf buf) {
            return new Payload(buf.readList(b -> new Entry(b.readVarInt(), b.readVarInt(), b.readVarInt() - 1, b.readEnum(Strings.Phase.class), b.readFloat())));
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        public static void handle(Payload payload, IPayloadContext context) {
            if (clientHandler != null) clientHandler.accept(payload);
        }
    }
}

package com.cnethuka.kegare.innate_technique.puppet_manipulation;

import com.cnethuka.kegare.Kegare;
import com.cnethuka.kegare.innate_technique.swap.SwapLink;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * What everyone sees of a possession (cosmetic only): the thread from the shell to the possessed player
 * ({@code possess_link}), the puppet's glow on the possessing player, and no name tag over them (they look like a
 * puppet). The server broadcasts who is possessing on every change, and sends it to each player who logs in.
 */
public final class PossessionLinks {
    /** How long Possess's thread takes to reach the puppet. */
    private static final int TRAVEL_TICKS = 5;

    private PossessionLinks() {}

    /** Possess's tell: a thread shoots from the body to the puppet. */
    static void travel(ServerLevel level, Vec3 from, Vec3 to) {
        SwapLink.travel(level, PuppetIds.FX_POSSESS_LINK, from, to, TRAVEL_TICKS);
    }

    static void broadcast(MinecraftServer server) {
        PacketDistributor.sendToAllPlayers(state(server));
    }

    static void sendTo(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, state(player.server));
    }

    private static StatePayload state(MinecraftServer server) {
        List<Entry> entries = new ArrayList<>();
        for (ServerPlayer p : Possession.possessing(server)) {
            PossessionData d = Possession.data(p);
            entries.add(new Entry(p.getUUID(), Possession.shellId(p), d.tier.ordinal(), PuppetManipulation.MALICE_COLOR));
        }
        return new StatePayload(entries);
    }

    /** One possession: who, their shell (entity network ID, -1 if unknown), and how their puppet glows. */
    public record Entry(UUID player, int shellId, int tier, int color) {}

    /** Server to every client: everyone possessing right now. */
    public record StatePayload(List<Entry> entries) implements CustomPacketPayload {
        public static final Type<StatePayload> TYPE = new Type<>(Kegare.id("possession_state"));
        public static final StreamCodec<FriendlyByteBuf, StatePayload> STREAM_CODEC = StreamCodec.ofMember(StatePayload::write, StatePayload::read);

        /** Set by the client entry point; null on a dedicated server. */
        private static Consumer<StatePayload> clientHandler;

        public static void setClientHandler(Consumer<StatePayload> handler) {
            clientHandler = handler;
        }

        private void write(FriendlyByteBuf buf) {
            buf.writeCollection(entries, (b, e) -> {
                b.writeUUID(e.player());
                b.writeVarInt(e.shellId() + 1);
                b.writeVarInt(e.tier());
                b.writeInt(e.color());
            });
        }

        private static StatePayload read(FriendlyByteBuf buf) {
            return new StatePayload(buf.readList(b -> new Entry(b.readUUID(), b.readVarInt() - 1, b.readVarInt(), b.readInt())));
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        public static void handle(StatePayload payload, IPayloadContext context) {
            if (clientHandler != null) clientHandler.accept(payload);
        }
    }
}

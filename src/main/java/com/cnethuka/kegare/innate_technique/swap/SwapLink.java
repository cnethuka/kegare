package com.cnethuka.kegare.innate_technique.swap;

import com.cnethuka.kegare.Kegare;
import java.util.function.Consumer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Effects drawn along a line between two points (cosmetic only). The client plays the effect at points spaced along
 * the line: all at once for the swap link (a brief beam between the two players), or one after another over
 * {@code travelTicks} for something that travels (Exchange's orbs).
 */
public final class SwapLink {
    /** Players this far beyond the line's ends still get it. */
    private static final double RANGE = 64.0;

    private SwapLink() {}

    /** The swap link: a brief beam between where the two players stood. */
    public static void play(ServerLevel level, Vec3 from, Vec3 to) {
        send(level, new Payload(SwapIds.FX_SWAP_LINK, from, to, 0));
    }

    /** Something travelling from {@code from} to {@code to} over {@code travelTicks}. */
    public static void travel(ServerLevel level, ResourceLocation effect, Vec3 from, Vec3 to, int travelTicks) {
        send(level, new Payload(effect, from, to, Math.max(1, travelTicks)));
    }

    private static void send(ServerLevel level, Payload payload) {
        Vec3 mid = payload.from().add(payload.to()).scale(0.5);
        double range = RANGE + payload.from().distanceTo(payload.to()) / 2.0;
        PacketDistributor.sendToPlayersNear(level, null, mid.x, mid.y, mid.z, range, payload);
    }

    /** Server to nearby clients: draw {@code effect} along the line. */
    public record Payload(ResourceLocation effect, Vec3 from, Vec3 to, int travelTicks) implements CustomPacketPayload {
        public static final Type<Payload> TYPE = new Type<>(Kegare.id("swap_link"));
        public static final StreamCodec<FriendlyByteBuf, Payload> STREAM_CODEC = StreamCodec.ofMember(Payload::write, Payload::read);

        /** Set by the client entry point; null on a dedicated server. */
        private static Consumer<Payload> clientHandler;

        public static void setClientHandler(Consumer<Payload> handler) {
            clientHandler = handler;
        }

        private void write(FriendlyByteBuf buf) {
            buf.writeResourceLocation(effect);
            buf.writeDouble(from.x);
            buf.writeDouble(from.y);
            buf.writeDouble(from.z);
            buf.writeDouble(to.x);
            buf.writeDouble(to.y);
            buf.writeDouble(to.z);
            buf.writeVarInt(travelTicks);
        }

        private static Payload read(FriendlyByteBuf buf) {
            return new Payload(buf.readResourceLocation(), new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()),
                    new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()), buf.readVarInt());
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

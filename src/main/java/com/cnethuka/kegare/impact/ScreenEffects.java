package com.cnethuka.kegare.impact;

import com.cnethuka.kegare.Kegare;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * The screen shader hook (DESIGN §3.23): a whole-screen effect on one player's screen for a set time, from the server. The effect is
 * a post-processing chain the client loads from {@code assets/<namespace>/shaders/post/<path>.json}; it fades in and out over a few
 * ticks. Time Manipulation's Standstill grays out the players it freezes with {@link #GRAYSCALE} (§3.26); {@code /kegare debug screen}
 * plays any of them.
 */
public final class ScreenEffects {
    public static final ResourceLocation GRAYSCALE = Kegare.id("grayscale");
    /** Every effect that ships, for the debug command's suggestions. */
    public static final List<ResourceLocation> ALL = List.of(GRAYSCALE, Kegare.id("impact_frame"), Kegare.id("invert"));

    private ScreenEffects() {}

    /** Plays {@code effect} on the player's screen for {@code ticks}; 0 stops it. */
    public static void play(ServerPlayer player, ResourceLocation effect, int ticks) {
        PacketDistributor.sendToPlayer(player, new Payload(effect, Math.max(0, ticks)));
    }

    public record Payload(ResourceLocation effect, int ticks) implements CustomPacketPayload {
        public static final Type<Payload> TYPE = new Type<>(Kegare.id("screen_effect"));
        public static final StreamCodec<FriendlyByteBuf, Payload> STREAM_CODEC = StreamCodec.ofMember(
                (p, buf) -> {
                    buf.writeResourceLocation(p.effect);
                    buf.writeVarInt(p.ticks);
                },
                buf -> new Payload(buf.readResourceLocation(), buf.readVarInt()));

        /** Set by the client entry point; null on a dedicated server. */
        private static Consumer<Payload> clientHandler;

        public static void setClientHandler(Consumer<Payload> handler) {
            clientHandler = handler;
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

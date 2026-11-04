package com.cnethuka.kegare.network;

import com.cnethuka.kegare.Kegare;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Client to server: "send me my Sorcerer screen data" (keybind K). Carries nothing; the server computes it all. */
public record SorcererRequestPayload() implements CustomPacketPayload {
    public static final SorcererRequestPayload INSTANCE = new SorcererRequestPayload();
    public static final Type<SorcererRequestPayload> TYPE = new Type<>(Kegare.id("sorcerer_request"));
    public static final StreamCodec<FriendlyByteBuf, SorcererRequestPayload> STREAM_CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    static void handle(SorcererRequestPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) PacketDistributor.sendToPlayer(player, SorcererInfoPayload.of(player));
    }
}

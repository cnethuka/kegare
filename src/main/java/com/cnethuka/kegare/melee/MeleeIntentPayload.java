package com.cnethuka.kegare.melee;

import com.cnethuka.kegare.Kegare;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client to server: a request. The server times and validates all of it. {@code forward} and {@code left} are the movement keys
 * held, clamped on arrival: which way to dash is the player's choice anyway, and how far and how often aren't in here.
 */
public record MeleeIntentPayload(Action action, byte forward, byte left) implements CustomPacketPayload {
    public enum Action { DASH, PARRY_PRESS, PARRY_RELEASE }

    public static final Type<MeleeIntentPayload> TYPE = new Type<>(Kegare.id("melee_intent"));
    public static final StreamCodec<FriendlyByteBuf, MeleeIntentPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeEnum(p.action());
                buf.writeByte(p.forward());
                buf.writeByte(p.left());
            },
            buf -> new MeleeIntentPayload(buf.readEnum(Action.class), buf.readByte(), buf.readByte()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(MeleeIntentPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) apply(player, payload);
    }

    /** Runs on the server main thread. */
    public static void apply(ServerPlayer player, MeleeIntentPayload payload) {
        switch (payload.action()) {
            case DASH -> Dash.request(player, payload.forward(), payload.left());
            case PARRY_PRESS -> Parry.press(player);
            case PARRY_RELEASE -> {
                if (player.hasData(MeleeLayer.ATTACHMENT)) Parry.release(player);
            }
        }
    }
}

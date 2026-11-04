package com.cnethuka.kegare.network;

import com.cnethuka.kegare.Kegare;
import com.cnethuka.kegare.item.PurificationTalismanItem;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client to server: "I confirmed the reroll". A request only: the server re-checks that the talisman is still in
 * that hand, combat and Severed status, then rolls (see {@link PurificationTalismanItem#confirm}).
 */
public record TalismanConfirmPayload(InteractionHand hand) implements CustomPacketPayload {
    public static final Type<TalismanConfirmPayload> TYPE = new Type<>(Kegare.id("talisman_confirm"));
    public static final StreamCodec<FriendlyByteBuf, TalismanConfirmPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> buf.writeEnum(p.hand()),
            buf -> new TalismanConfirmPayload(buf.readEnum(InteractionHand.class)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    static void handle(TalismanConfirmPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) PurificationTalismanItem.confirm(player, payload.hand());
    }
}

package com.cnethuka.kegare.network;

import com.cnethuka.kegare.Kegare;
import com.cnethuka.kegare.client.gui.ClientGuiHooks;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Server to one client: a small private toast. Promotions and unlocks are nobody else's business, so this never goes to
 * anyone else, and nothing goes in chat.
 *
 * @param id a Grade's ID, or the ability's or unlock's resource location
 */
public record ProgressionToastPayload(Kind kind, String id) implements CustomPacketPayload {
    public enum Kind { PROMOTION, ABILITY, SPECIAL, ULTIMATE, UNLOCK }

    public static final Type<ProgressionToastPayload> TYPE = new Type<>(Kegare.id("progression_toast"));
    public static final StreamCodec<FriendlyByteBuf, ProgressionToastPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeEnum(payload.kind);
                buf.writeUtf(payload.id, 256);
            },
            buf -> new ProgressionToastPayload(buf.readEnum(Kind.class), buf.readUtf(256)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    static void handle(ProgressionToastPayload payload, IPayloadContext context) {
        ClientGuiHooks.onProgressionToast(payload);
    }
}

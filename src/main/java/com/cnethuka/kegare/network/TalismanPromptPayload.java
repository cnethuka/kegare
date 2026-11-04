package com.cnethuka.kegare.network;

import com.cnethuka.kegare.Kegare;
import com.cnethuka.kegare.client.gui.ClientGuiHooks;
import java.util.Optional;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Server to client: open the Purification Talisman confirmation (nothing has been consumed or rolled). */
public record TalismanPromptPayload(InteractionHand hand, Optional<ResourceLocation> current, int rollsSinceLegendary, int hardPity)
        implements CustomPacketPayload {
    public static final Type<TalismanPromptPayload> TYPE = new Type<>(Kegare.id("talisman_prompt"));
    public static final StreamCodec<FriendlyByteBuf, TalismanPromptPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeEnum(p.hand());
                buf.writeOptional(p.current(), FriendlyByteBuf::writeResourceLocation);
                buf.writeVarInt(p.rollsSinceLegendary());
                buf.writeVarInt(p.hardPity());
            },
            buf -> new TalismanPromptPayload(buf.readEnum(InteractionHand.class), buf.readOptional(FriendlyByteBuf::readResourceLocation),
                    buf.readVarInt(), buf.readVarInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    static void handle(TalismanPromptPayload payload, IPayloadContext context) {
        ClientGuiHooks.onTalismanPrompt(payload);
    }
}

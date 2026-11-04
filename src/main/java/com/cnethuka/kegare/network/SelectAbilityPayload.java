package com.cnethuka.kegare.network;

import com.cnethuka.kegare.Kegare;
import com.cnethuka.kegare.innatetechnique.InnateTechniqueService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Client to server: select ability slot N (radial menu). The server validates the slot. */
public record SelectAbilityPayload(int slot) implements CustomPacketPayload {
    public static final Type<SelectAbilityPayload> TYPE = new Type<>(Kegare.id("select_ability"));
    public static final StreamCodec<FriendlyByteBuf, SelectAbilityPayload> STREAM_CODEC =
            ByteBufCodecs.VAR_INT.map(SelectAbilityPayload::new, SelectAbilityPayload::slot).cast();

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    static void handle(SelectAbilityPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) InnateTechniqueService.select(player, payload.slot());
    }
}

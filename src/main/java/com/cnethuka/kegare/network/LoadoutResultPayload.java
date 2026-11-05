package com.cnethuka.kegare.network;

import com.cnethuka.kegare.Kegare;
import com.cnethuka.kegare.client.gui.ClientGuiHooks;
import com.cnethuka.kegare.loadout.LoadoutResult;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Server to client: a loadout edit was refused (shown on the Loadout page; the action bar is hidden behind it). */
public record LoadoutResultPayload(LoadoutResult result) implements CustomPacketPayload {
    public static final Type<LoadoutResultPayload> TYPE = new Type<>(Kegare.id("loadout_result"));
    public static final StreamCodec<FriendlyByteBuf, LoadoutResultPayload> STREAM_CODEC = StreamCodec.ofMember(
            (p, buf) -> buf.writeEnum(p.result()), buf -> new LoadoutResultPayload(buf.readEnum(LoadoutResult.class)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    static void handle(LoadoutResultPayload payload, IPayloadContext context) {
        ClientGuiHooks.onLoadoutResult(payload.result());
    }
}

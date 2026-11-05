package com.cnethuka.kegare.network;

import com.cnethuka.kegare.Kegare;
import com.cnethuka.kegare.loadout.LoadoutResult;
import com.cnethuka.kegare.loadout.LoadoutService;
import java.util.Optional;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client to server: a loadout edit request from the Loadout page (put an ability in a slot, swap two slots, empty one,
 * or reset to the default). A request only: {@link LoadoutService#edit} checks everything, and a refusal comes back
 * as a {@link LoadoutResultPayload} plus an action-bar message.
 */
public record LoadoutEditPayload(LoadoutService.Action action, int slot, int other, Optional<ResourceLocation> ability)
        implements CustomPacketPayload {
    public static final Type<LoadoutEditPayload> TYPE = new Type<>(Kegare.id("loadout_edit"));
    public static final StreamCodec<FriendlyByteBuf, LoadoutEditPayload> STREAM_CODEC = StreamCodec.ofMember(LoadoutEditPayload::write, LoadoutEditPayload::read);

    private void write(FriendlyByteBuf buf) {
        buf.writeEnum(action);
        buf.writeVarInt(slot);
        buf.writeVarInt(other);
        buf.writeOptional(ability, FriendlyByteBuf::writeResourceLocation);
    }

    private static LoadoutEditPayload read(FriendlyByteBuf buf) {
        return new LoadoutEditPayload(buf.readEnum(LoadoutService.Action.class), buf.readVarInt(), buf.readVarInt(),
                buf.readOptional(FriendlyByteBuf::readResourceLocation));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    static void handle(LoadoutEditPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        LoadoutResult result = LoadoutService.edit(player, payload.action(), payload.slot(), payload.other(), payload.ability());
        if (result != LoadoutResult.OK) {
            player.displayClientMessage(Component.translatable(result.translationKey()), true);
            PacketDistributor.sendToPlayer(player, new LoadoutResultPayload(result));
        }
    }
}

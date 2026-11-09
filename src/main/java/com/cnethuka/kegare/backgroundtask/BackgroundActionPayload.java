package com.cnethuka.kegare.backgroundtask;

import com.cnethuka.kegare.Kegare;
import java.util.Optional;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Client to server: a request from the Background view (DESIGN §3.31). The server checks everything ({@link BackgroundTaskService}). */
public record BackgroundActionPayload(int action, int slot, Optional<ResourceLocation> task) implements CustomPacketPayload {
    public static final int SET = 0, CLEAR = 1, ON = 2, OFF = 3;
    public static final Type<BackgroundActionPayload> TYPE = new Type<>(Kegare.id("background_action"));
    public static final StreamCodec<FriendlyByteBuf, BackgroundActionPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, BackgroundActionPayload::action,
            ByteBufCodecs.VAR_INT, BackgroundActionPayload::slot,
            ResourceLocation.STREAM_CODEC.apply(ByteBufCodecs::optional), BackgroundActionPayload::task, BackgroundActionPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(BackgroundActionPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        BackgroundTaskResult result = switch (payload.action()) {
            case SET -> payload.task().isPresent() ? BackgroundTaskService.set(player, payload.slot(), payload.task().get()) : BackgroundTaskResult.UNKNOWN;
            case CLEAR -> BackgroundTaskService.set(player, payload.slot(), null);
            case ON -> BackgroundTaskService.setOn(player, payload.slot(), true);
            case OFF -> BackgroundTaskService.setOn(player, payload.slot(), false);
            default -> BackgroundTaskResult.UNKNOWN;
        };
        BackgroundTaskSync.answer(player, result);
    }
}

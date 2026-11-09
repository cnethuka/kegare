package com.cnethuka.kegare.backgroundtask;

import com.cnethuka.kegare.Kegare;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Server to client, to the owner only (DESIGN §3.31): their Background Task slots (as many as they have, so the count is never sent to
 * anyone else), what's in each, on or off and paused, every task with whether it's unlocked and its drain, and the last refusal, if any.
 */
public record BackgroundSyncPayload(int slotCount, List<SlotView> slots, List<TaskView> tasks, String refusal) implements CustomPacketPayload {
    public record SlotView(Optional<ResourceLocation> task, boolean on, boolean paused) {
        static final StreamCodec<FriendlyByteBuf, SlotView> CODEC = StreamCodec.composite(
                ResourceLocation.STREAM_CODEC.apply(ByteBufCodecs::optional), SlotView::task,
                ByteBufCodecs.BOOL, SlotView::on,
                ByteBufCodecs.BOOL, SlotView::paused, SlotView::new);
    }

    public record TaskView(ResourceLocation id, boolean unlocked, double drain) {
        static final StreamCodec<FriendlyByteBuf, TaskView> CODEC = StreamCodec.composite(
                ResourceLocation.STREAM_CODEC, TaskView::id,
                ByteBufCodecs.BOOL, TaskView::unlocked,
                ByteBufCodecs.DOUBLE, TaskView::drain, TaskView::new);
    }

    public static final BackgroundSyncPayload NONE = new BackgroundSyncPayload(0, List.of(), List.of(), "");
    public static final Type<BackgroundSyncPayload> TYPE = new Type<>(Kegare.id("background_sync"));
    public static final StreamCodec<FriendlyByteBuf, BackgroundSyncPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, BackgroundSyncPayload::slotCount,
            SlotView.CODEC.apply(ByteBufCodecs.list()), BackgroundSyncPayload::slots,
            TaskView.CODEC.apply(ByteBufCodecs.list()), BackgroundSyncPayload::tasks,
            ByteBufCodecs.STRING_UTF8, BackgroundSyncPayload::refusal, BackgroundSyncPayload::new);

    private static Consumer<BackgroundSyncPayload> clientHandler;

    public static void setClientHandler(Consumer<BackgroundSyncPayload> handler) {
        clientHandler = handler;
    }

    public BackgroundSyncPayload withRefusal(String key) {
        return new BackgroundSyncPayload(slotCount, slots, tasks, key);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(BackgroundSyncPayload payload, IPayloadContext context) {
        if (clientHandler != null) context.enqueueWork(() -> clientHandler.accept(payload));
    }
}

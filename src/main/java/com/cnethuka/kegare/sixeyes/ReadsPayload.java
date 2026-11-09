package com.cnethuka.kegare.sixeyes;

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
 * Six Eyes' Reads, server to the bearer only (DESIGN §3.32): every player near enough to be in view, at a glance (their innate technique
 * and current Malice, or a void), and full detail on the one nearest the bearer's crosshair. Never Grade, never traits.
 */
public record ReadsPayload(List<Glance> glances, Optional<Focus> focus) implements CustomPacketPayload {
    public record Glance(int entityId, Optional<ResourceLocation> technique, int malice, boolean severed) {
        static final StreamCodec<FriendlyByteBuf, Glance> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Glance::entityId,
                ResourceLocation.STREAM_CODEC.apply(ByteBufCodecs::optional), Glance::technique,
                ByteBufCodecs.VAR_INT, Glance::malice,
                ByteBufCodecs.BOOL, Glance::severed, Glance::new);
    }

    /** What they're charging or channelling, and how far along (0 to 1; a channel with no end counts up in seconds, as -seconds). */
    public record Cast(ResourceLocation ability, int castType, float progress) {
        static final StreamCodec<FriendlyByteBuf, Cast> CODEC = StreamCodec.composite(
                ResourceLocation.STREAM_CODEC, Cast::ability, ByteBufCodecs.VAR_INT, Cast::castType, ByteBufCodecs.FLOAT, Cast::progress, Cast::new);
    }

    public record Cooldown(ResourceLocation ability, float seconds) {
        static final StreamCodec<FriendlyByteBuf, Cooldown> CODEC = StreamCodec.composite(
                ResourceLocation.STREAM_CODEC, Cooldown::ability, ByteBufCodecs.FLOAT, Cooldown::seconds, Cooldown::new);
    }

    /** Full detail. A void's is empty: there's nothing to read. */
    public record Focus(int entityId, boolean severed, Optional<Cast> cast, List<Cooldown> cooldowns, List<ResourceLocation> toggles) {
        static final StreamCodec<FriendlyByteBuf, Focus> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Focus::entityId,
                ByteBufCodecs.BOOL, Focus::severed,
                Cast.CODEC.apply(ByteBufCodecs::optional), Focus::cast,
                Cooldown.CODEC.apply(ByteBufCodecs.list()), Focus::cooldowns,
                ResourceLocation.STREAM_CODEC.apply(ByteBufCodecs.list()), Focus::toggles, Focus::new);
    }

    public static final Type<ReadsPayload> TYPE = new Type<>(Kegare.id("six_eyes_reads"));
    public static final StreamCodec<FriendlyByteBuf, ReadsPayload> STREAM_CODEC = StreamCodec.composite(
            Glance.CODEC.apply(ByteBufCodecs.list()), ReadsPayload::glances,
            Focus.CODEC.apply(ByteBufCodecs::optional), ReadsPayload::focus, ReadsPayload::new);

    public static Consumer<ReadsPayload> clientHandler;

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ReadsPayload p, IPayloadContext context) {
        if (clientHandler != null) context.enqueueWork(() -> clientHandler.accept(p));
    }
}

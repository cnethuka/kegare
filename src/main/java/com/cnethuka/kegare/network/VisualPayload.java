package com.cnethuka.kegare.network;

import com.cnethuka.kegare.Kegare;
import com.cnethuka.kegare.innatetechnique.visual.Visual;
import java.util.Optional;
import java.util.function.Consumer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Server to clients (the caster and everyone tracking them): play or stop a cosmetic. VISUAL ONLY: sent after the
 * server already acted; nothing ever waits for it, and a client that can't show it just skips it.
 *
 * @param entityId  the entity the visual is attached to; for {@link Action#PLAY_AT}, the entity it came from or -1
 * @param key       identifies a loop so it can be stopped (e.g. {@code kegare:glacial_lance/charge_loop})
 * @param pos       {@link Action#PLAY_AT} only: where the effect plays
 * @param growTicks loops only: the effect grows from small to full size over this many ticks (0 = full size)
 * @param lifetimeTicks loops only: lifetime of the effect's {@code persist*} emitters' particles (0 = as authored), so
 *                      a lingering trail lasts exactly as long as the server's config says
 */
public record VisualPayload(Action action, int entityId, String key, Visual visual, Vec3 pos, int growTicks, int lifetimeTicks,
                            int elapsedTicks) implements CustomPacketPayload {
    public VisualPayload(Action action, int entityId, String key, Visual visual, Vec3 pos, int growTicks) {
        this(action, entityId, key, visual, pos, growTicks, 0, 0);
    }

    public VisualPayload(Action action, int entityId, String key, Visual visual, Vec3 pos, int growTicks, int lifetimeTicks) {
        this(action, entityId, key, visual, pos, growTicks, lifetimeTicks, 0);
    }

    public enum Action {
        /** Play once on the entity. */
        PLAY_ONCE,
        /** Start (or restart) the loop {@code key} on the entity, {@code elapsedTicks} into it (a viewer who missed its start). */
        START_LOOP,
        /** Stop the loop {@code key} on the entity. */
        STOP,
        /** Stop every loop and animation on the entity. */
        STOP_ALL,
        /** Play once at {@code pos}. */
        PLAY_AT
    }

    public static final Type<VisualPayload> TYPE = new Type<>(Kegare.id("visual"));
    public static final StreamCodec<FriendlyByteBuf, VisualPayload> STREAM_CODEC = StreamCodec.ofMember(VisualPayload::write, VisualPayload::read);

    /** Set by the client entry point; null on a dedicated server (which never receives this payload). */
    private static Consumer<VisualPayload> clientHandler;

    public static void setClientHandler(Consumer<VisualPayload> handler) {
        clientHandler = handler;
    }

    private void write(FriendlyByteBuf buf) {
        buf.writeEnum(action);
        buf.writeVarInt(entityId);
        buf.writeUtf(key, 256);
        buf.writeOptional(visual.animation(), FriendlyByteBuf::writeResourceLocation);
        buf.writeOptional(visual.effect(), FriendlyByteBuf::writeResourceLocation);
        buf.writeEnum(visual.anchor());
        buf.writeBoolean(visual.linger());
        buf.writeInt(visual.tint());
        buf.writeDouble(pos.x);
        buf.writeDouble(pos.y);
        buf.writeDouble(pos.z);
        buf.writeVarInt(growTicks);
        buf.writeVarInt(lifetimeTicks);
        buf.writeVarInt(elapsedTicks);
    }

    private static VisualPayload read(FriendlyByteBuf buf) {
        Action action = buf.readEnum(Action.class);
        int entityId = buf.readVarInt();
        String key = buf.readUtf(256);
        Optional<ResourceLocation> animation = buf.readOptional(FriendlyByteBuf::readResourceLocation);
        Optional<ResourceLocation> effect = buf.readOptional(FriendlyByteBuf::readResourceLocation);
        Visual.Anchor anchor = buf.readEnum(Visual.Anchor.class);
        boolean linger = buf.readBoolean();
        int tint = buf.readInt();
        Vec3 pos = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
        return new VisualPayload(action, entityId, key, new Visual(animation, effect, anchor, linger, tint), pos, buf.readVarInt(), buf.readVarInt(),
                buf.readVarInt());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    static void handle(VisualPayload payload, IPayloadContext context) {
        if (clientHandler != null) clientHandler.accept(payload);
    }
}

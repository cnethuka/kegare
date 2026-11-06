package com.cnethuka.kegare.melee;

import com.cnethuka.kegare.Kegare;
import java.util.function.Consumer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Server to client, only to its owner: when their cooldowns end (game time) and whether they were just staggered. For display.
 *
 * @param resetAttack a parry staggered them: their client restarts its own copy of the attack cooldown
 */
public record MeleeHudPayload(long dashReadyAt, int dashCooldown, long recoveryUntil, int recovery, long strikeUntil, int strikeTicks, boolean resetAttack)
        implements CustomPacketPayload {
    public static final MeleeHudPayload NONE = new MeleeHudPayload(0, 1, 0, 1, 0, 1, false);
    public static final Type<MeleeHudPayload> TYPE = new Type<>(Kegare.id("melee_hud"));
    public static final StreamCodec<FriendlyByteBuf, MeleeHudPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeLong(p.dashReadyAt());
                buf.writeVarInt(p.dashCooldown());
                buf.writeLong(p.recoveryUntil());
                buf.writeVarInt(p.recovery());
                buf.writeLong(p.strikeUntil());
                buf.writeVarInt(p.strikeTicks());
                buf.writeBoolean(p.resetAttack());
            },
            buf -> new MeleeHudPayload(buf.readLong(), buf.readVarInt(), buf.readLong(), buf.readVarInt(), buf.readLong(), buf.readVarInt(), buf.readBoolean()));

    /** Set by the client entry point; null on a dedicated server. */
    private static Consumer<MeleeHudPayload> clientHandler;

    public static void setClientHandler(Consumer<MeleeHudPayload> handler) {
        clientHandler = handler;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(MeleeHudPayload payload, IPayloadContext context) {
        if (clientHandler != null) clientHandler.accept(payload);
    }
}

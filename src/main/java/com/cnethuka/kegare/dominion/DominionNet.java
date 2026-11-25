package com.cnethuka.kegare.dominion;

import com.cnethuka.kegare.Kegare;
import java.util.function.Consumer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Dominions' payloads (DESIGN §3.35). Server to client, all decided already: the meter, which side of a barrier you're on, its moments and
 * a clash's state and prompts. Client to server, only a clash's caster's inputs: whether they hold their key, and a prompt's key going down.
 */
public final class DominionNet {
    private DominionNet() {}

    /** The owner's Dominion Meter (0 to 100) and whether to show it: only when they have a Dominion to open with it. */
    public record Meter(float meter, boolean shown) implements CustomPacketPayload {
        public static final Type<Meter> TYPE = new Type<>(Kegare.id("dominion_meter"));
        public static final StreamCodec<FriendlyByteBuf, Meter> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> {
                    buf.writeFloat(p.meter());
                    buf.writeBoolean(p.shown());
                },
                buf -> new Meter(buf.readFloat(), buf.readBoolean()));
        public static Consumer<Meter> clientHandler;

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        public static void handle(Meter p, IPayloadContext context) {
            if (clientHandler != null) context.enqueueWork(() -> clientHandler.accept(p));
        }
    }

    /**
     * To a player, as a barrier closes (or a clash changes it): whether they're trapped inside it ({@code inside}), so their own client
     * keeps them on that side of it. Everyone not told they're inside stays outside.
     */
    public record Trapped(int dominion, boolean inside) implements CustomPacketPayload {
        public static final Type<Trapped> TYPE = new Type<>(Kegare.id("dominion_trapped"));
        public static final StreamCodec<FriendlyByteBuf, Trapped> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> {
                    buf.writeVarInt(p.dominion());
                    buf.writeBoolean(p.inside());
                },
                buf -> new Trapped(buf.readVarInt(), buf.readBoolean()));
        public static Consumer<Trapped> clientHandler;

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        public static void handle(Trapped p, IPayloadContext context) {
            if (clientHandler != null) context.enqueueWork(() -> clientHandler.accept(p));
        }
    }

    /**
     * A moment to draw and hear where it happened: the barrier closing (with its name, for those inside), a hit on it, it breaking, it
     * ending, or a clash starting (where the two barriers meet). {@code strength}, for a hit, is the share of the barrier's whole strength it took (or 0); {@code complete} and {@code cracked} say which kind of shell.
     */
    public record Moment(int kind, int dominion, double x, double y, double z, float radius, float strength, boolean complete, boolean cracked,
                         int shellColor, String ability) implements CustomPacketPayload {
        public static final int CLOSED = 0, HIT = 1, BROKE = 2, ENDED = 3, OPENING = 4, CLASH = 5;
        public static final Type<Moment> TYPE = new Type<>(Kegare.id("dominion_moment"));
        public static final StreamCodec<FriendlyByteBuf, Moment> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> {
                    buf.writeByte(p.kind());
                    buf.writeVarInt(p.dominion());
                    buf.writeDouble(p.x());
                    buf.writeDouble(p.y());
                    buf.writeDouble(p.z());
                    buf.writeFloat(p.radius());
                    buf.writeFloat(p.strength());
                    buf.writeBoolean(p.complete());
                    buf.writeBoolean(p.cracked());
                    buf.writeInt(p.shellColor());
                    buf.writeUtf(p.ability(), 256);
                },
                buf -> new Moment(buf.readByte(), buf.readVarInt(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readFloat(),
                        buf.readFloat(), buf.readBoolean(), buf.readBoolean(), buf.readInt(), buf.readUtf(256)));
        public static Consumer<Moment> clientHandler;

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        public static void handle(Moment p, IPayloadContext context) {
            if (clientHandler != null) context.enqueueWork(() -> clientHandler.accept(p));
        }
    }
    /**
     * To a clash's two casters, every tick while it runs: its {@code phase} (GRIP 0, MAINTAIN 1, FIGHT 2; -1 over, with {@code result}
     * 1 won, 2 lost, 3 a tie), their own barrier and the other's, the bar from their side (1: they've pushed the other out), the ticks
     * left in this phase, whether the server sees them holding their key, their barrier's strength left, both scores in the Fight, the
     * key to hold (a bar slot 0 to 8, or -1 for V), and where they're held in Maintain and how far they may move from it.
     */
    public record ClashState(int phase, int mine, int theirs, float bar, int ticksLeft, boolean holding, float barrier, float myScore,
                             float theirScore, int holdSlot, double holdX, double holdY, double holdZ, float leash, int result)
            implements CustomPacketPayload {
        public static final Type<ClashState> TYPE = new Type<>(Kegare.id("dominion_clash_state"));
        public static final StreamCodec<FriendlyByteBuf, ClashState> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> {
                    buf.writeByte(p.phase());
                    buf.writeVarInt(p.mine());
                    buf.writeVarInt(p.theirs());
                    buf.writeFloat(p.bar());
                    buf.writeVarInt(p.ticksLeft());
                    buf.writeBoolean(p.holding());
                    buf.writeFloat(p.barrier());
                    buf.writeFloat(p.myScore());
                    buf.writeFloat(p.theirScore());
                    buf.writeByte(p.holdSlot());
                    buf.writeDouble(p.holdX());
                    buf.writeDouble(p.holdY());
                    buf.writeDouble(p.holdZ());
                    buf.writeFloat(p.leash());
                    buf.writeByte(p.result());
                },
                buf -> new ClashState(buf.readByte(), buf.readVarInt(), buf.readVarInt(), buf.readFloat(), buf.readVarInt(), buf.readBoolean(),
                        buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readByte(), buf.readDouble(), buf.readDouble(), buf.readDouble(),
                        buf.readFloat(), buf.readByte()));
        public static Consumer<ClashState> clientHandler;

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        public static void handle(ClashState p, IPayloadContext context) {
            if (clientHandler != null) context.enqueueWork(() -> clientHandler.accept(p));
        }
    }

    /**
     * To a clash's caster: a timing prompt ({@code id}, which {@code key}: 0 jump, 1 sneak, 2 sprint), its beat on their own clock
     * ({@code beatClientMs}, NaN when the server has no estimate of it yet: then {@code leadMs} from when it arrives), and the window
     * round it. Sent again once judged, with {@code result} 1 hit or 2 miss (0 while it waits).
     */
    public record ClashPrompt(int id, int key, double beatClientMs, int leadMs, int windowMs, int result) implements CustomPacketPayload {
        public static final int JUMP = 0, SNEAK = 1, SPRINT = 2;
        public static final int PENDING = 0, HIT = 1, MISS = 2;
        public static final Type<ClashPrompt> TYPE = new Type<>(Kegare.id("dominion_clash_prompt"));
        public static final StreamCodec<FriendlyByteBuf, ClashPrompt> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> {
                    buf.writeVarInt(p.id());
                    buf.writeByte(p.key());
                    buf.writeDouble(p.beatClientMs());
                    buf.writeVarInt(p.leadMs());
                    buf.writeVarInt(p.windowMs());
                    buf.writeByte(p.result());
                },
                buf -> new ClashPrompt(buf.readVarInt(), buf.readByte(), buf.readDouble(), buf.readVarInt(), buf.readVarInt(), buf.readByte()));
        public static Consumer<ClashPrompt> clientHandler;

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        public static void handle(ClashPrompt p, IPayloadContext context) {
            if (clientHandler != null) context.enqueueWork(() -> clientHandler.accept(p));
        }
    }

    /** From a clash's caster: whether they're holding the key that opens their Dominion now (sent when it changes, and as the clash starts). */
    public record ClashHold(boolean held) implements CustomPacketPayload {
        public static final Type<ClashHold> TYPE = new Type<>(Kegare.id("dominion_clash_hold"));
        public static final StreamCodec<FriendlyByteBuf, ClashHold> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> buf.writeBoolean(p.held()), buf -> new ClashHold(buf.readBoolean()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        public static void handle(ClashHold p, IPayloadContext context) {
            context.enqueueWork(() -> {
                if (context.player() instanceof net.minecraft.server.level.ServerPlayer player) Clashes.onHold(player, p.held());
            });
        }
    }

    /** From a clash's caster: a prompt key went down (stamped for sub-tick timing just before, as {@code TimedInput.CLASH}). */
    public record ClashPress(int key) implements CustomPacketPayload {
        public static final Type<ClashPress> TYPE = new Type<>(Kegare.id("dominion_clash_press"));
        public static final StreamCodec<FriendlyByteBuf, ClashPress> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> buf.writeByte(p.key()), buf -> new ClashPress(buf.readByte()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        public static void handle(ClashPress p, IPayloadContext context) {
            context.enqueueWork(() -> {
                if (context.player() instanceof net.minecraft.server.level.ServerPlayer player) {
                    Clashes.onPress(player, p.key(), com.cnethuka.kegare.timing.SubTick.mark(player, com.cnethuka.kegare.timing.TimedInput.CLASH));
                }
            });
        }
    }
}

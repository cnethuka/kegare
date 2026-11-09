package com.cnethuka.kegare.sixeyes;

import com.cnethuka.kegare.Kegare;
import com.cnethuka.kegare.progression.Grade;
import com.cnethuka.kegare.progression.GradeService;
import com.cnethuka.kegare.timing.SubTick;
import com.cnethuka.kegare.timing.TimedInput;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * The uncovered edge (DESIGN §3.32): only uncovered, against a top-tier opponent, the bearer sees the spark before a cast. The cast key's
 * sub-tick stamp (§3.29) is handled on the network thread the moment it reaches the server, ahead of the intent itself and of the cast's
 * own tell, and the cue goes out there and then. Covered, or with the edge lost to max Strain, they see the cast when it starts, like
 * everyone. For 1.0.0 "top-tier" is a Special Grade player; {@link #addTopTier} is where the Shrine's bearer, the Director and a Dominion
 * clash come in later.
 * <p>
 * The network thread can't read a player's state safely, so each server tick leaves a snapshot here: who can read whom.
 */
public final class Edge {
    /** Who a bearer can read this way, with where each stands. */
    private record Reader(ServerPlayer bearer, Set<UUID> topTier) {}

    private static final Map<UUID, Reader> READERS = new ConcurrentHashMap<>();
    private static final List<BiPredicate<ServerPlayer, ServerPlayer>> TOP_TIER = new CopyOnWriteArrayList<>();
    /** Told who each spark goes to (tests). */
    static java.util.function.BiConsumer<ServerPlayer, Spark> sentHook;

    private Edge() {}

    static void init() {
        addTopTier((bearer, caster) -> GradeService.grade(caster) == Grade.SPECIAL_GRADE);
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post e) -> snapshot(e.getServer()));
        SubTick.addArrivalListener((player, input) -> {
            if (input == TimedInput.CAST) spark(player);
        });
    }

    /** Who counts as top-tier for a bearer: (bearer, caster). The Shrine's bearer, the Director and a Dominion clash plug in here later. */
    public static void addTopTier(BiPredicate<ServerPlayer, ServerPlayer> rule) {
        TOP_TIER.add(rule);
    }

    public static boolean topTier(ServerPlayer bearer, ServerPlayer caster) {
        for (BiPredicate<ServerPlayer, ServerPlayer> rule : TOP_TIER) if (rule.test(bearer, caster)) return true;
        return false;
    }

    /** Every tick on the server thread: who each bearer could read right now. */
    static void snapshot(MinecraftServer server) {
        Map<UUID, Reader> fresh = new HashMap<>();
        for (ServerPlayer bearer : SixEyes.bearers(server)) {
            if (!Strain.edgeAvailable(bearer)) continue;
            ServerLevel level = bearer.serverLevel();
            double range = SixEyesConfig.EDGE_RANGE.get();
            Set<UUID> top = new java.util.HashSet<>();
            for (ServerPlayer other : level.players()) {
                if (other != bearer && other.distanceTo(bearer) <= range && topTier(bearer, other)) top.add(other.getUUID());
            }
            if (!top.isEmpty()) fresh.put(bearer.getUUID(), new Reader(bearer, top));
        }
        READERS.keySet().retainAll(fresh.keySet());
        READERS.putAll(fresh);
    }

    /** A cast key's stamp just arrived (on the network thread): the cue, there and then. */
    static void spark(net.minecraft.world.entity.player.Player caster) {
        for (Reader reader : READERS.values()) {
            if (!reader.topTier().contains(caster.getUUID())) continue;
            Spark spark = new Spark(caster.getId());
            if (sentHook != null) sentHook.accept(reader.bearer(), spark);
            PacketDistributor.sendToPlayer(reader.bearer(), spark);
        }
    }

    static void clear() {
        READERS.clear();
    }

    /** Server to the bearer: the spark before someone's cast. */
    public record Spark(int entityId) implements CustomPacketPayload {
        public static final Type<Spark> TYPE = new Type<>(Kegare.id("six_eyes_spark"));
        public static final StreamCodec<FriendlyByteBuf, Spark> STREAM_CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, Spark::entityId, Spark::new);
        public static Consumer<Spark> clientHandler;

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        public static void handle(Spark p, IPayloadContext context) {
            if (clientHandler != null) context.enqueueWork(() -> clientHandler.accept(p));
        }
    }
}

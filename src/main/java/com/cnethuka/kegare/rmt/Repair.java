package com.cnethuka.kegare.rmt;

import com.cnethuka.kegare.Kegare;
import com.cnethuka.kegare.innatetechnique.Burnout;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Repairing a burnt-out technique (DESIGN §3.17), canon's way: channelling Reverse Malice Technique while burnt out destroys and heals
 * the part of the brain where the technique is stored. It repairs the technique first: the Burnout counts down faster, by
 * {@code repairRate} x the current RMT output a second (Grade, Peak Refinement, injury fatigue and Black Spark's zone all count), and
 * the body doesn't heal until it's gone. It costs: {@code repairDamage} a second that armour doesn't stop, and the brain meter
 * ({@link BrainDamage}); full, "Your brain can't take another repair." and the Burnout runs its full length (then RMT heals the body
 * again). No extra key. Others see a nosebleed while it goes on (the tell, {@link Payload}). Every RMT user, Six Eyes included.
 */
public final class Repair {
    public static final ResourceKey<DamageType> DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE, Kegare.id("brain_repair"));
    /** "Your brain can't take another repair." at most this often. */
    static final int REFUSAL_TICKS = 60;

    private static final class State {
        double pendingTicks;
        int damageClock;
        long refusedAt = Long.MIN_VALUE;
    }

    private static final Map<UUID, State> STATES = new HashMap<>();
    /** Who's repairing right now (the tell is on). */
    private static final Set<UUID> REPAIRING = new HashSet<>();

    private Repair() {}

    /**
     * One tick of RMT's channel. Returns whether it repaired this tick (then the body isn't healed). Not burnt out, or the brain full:
     * nothing repaired, the channel heals the body as always.
     */
    static boolean tick(ServerPlayer player) {
        if (!Burnout.isBurntOut(player)) {
            stop(player);
            return false;
        }
        State state = STATES.computeIfAbsent(player.getUUID(), k -> new State());
        if (BrainDamage.isFull(player)) {
            refuse(player, state);
            stop(player);
            return false;
        }
        state.pendingTicks += RmtConfig.REPAIR_RATE.get() * RmtOutput.of(player); // seconds a second = ticks a tick
        int whole = Math.min((int) state.pendingTicks, Burnout.remaining(player));
        state.pendingTicks -= (int) state.pendingTicks;
        if (whole > 0) {
            double allowed = BrainDamage.take(player, whole / 20.0, Burnout.length(player) / 20.0);
            int ticks = (int) Math.floor(allowed * 20.0 + 1.0e-6);
            Burnout.shorten(player, ticks);
            if (ticks < whole) refuse(player, state); // it filled up on this one
        }
        if (++state.damageClock >= 20) {
            state.damageClock = 0;
            float damage = RmtConfig.REPAIR_DAMAGE.get().floatValue();
            if (damage > 0) {
                // Past the hurt cooldown (so a real hit's cooldown never swallows it), and it leaves none behind: a repair never shields
                // anyone from the next real hit.
                int invulnerable = player.invulnerableTime;
                player.hurt(source(player), damage);
                player.invulnerableTime = invulnerable;
            }
        }
        start(player);
        return true;
    }

    static DamageSource source(ServerPlayer player) {
        return new DamageSource(player.level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(DAMAGE));
    }

    private static void refuse(ServerPlayer player, State state) {
        long now = player.level().getGameTime();
        if (now - state.refusedAt < REFUSAL_TICKS) return;
        state.refusedAt = now;
        player.displayClientMessage(Component.translatable("message.kegare.rmt.brain_full"), true);
    }

    public static boolean isRepairing(ServerPlayer player) {
        return REPAIRING.contains(player.getUUID());
    }

    private static void start(ServerPlayer player) {
        if (REPAIRING.add(player.getUUID())) PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, new Payload(player.getId(), true));
    }

    /** The channel ended, the Burnout's gone, or the brain refused. */
    static void stop(ServerPlayer player) {
        State state = STATES.get(player.getUUID());
        if (state != null) {
            state.pendingTicks = 0;
            state.damageClock = 0;
        }
        if (REPAIRING.remove(player.getUUID())) PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, new Payload(player.getId(), false));
    }

    static void onStartTracking(ServerPlayer viewer, Entity target) {
        if (target instanceof ServerPlayer player && isRepairing(player)) PacketDistributor.sendToPlayer(viewer, new Payload(player.getId(), true));
    }

    static void forget(UUID player) {
        STATES.remove(player);
        REPAIRING.remove(player);
    }

    public static void clear() {
        STATES.clear();
        REPAIRING.clear();
    }

    /** Server to client: someone started or stopped repairing (a nosebleed while it goes on). */
    public record Payload(int entityId, boolean on) implements CustomPacketPayload {
        public static final Type<Payload> TYPE = new Type<>(Kegare.id("rmt_repair"));
        public static final StreamCodec<FriendlyByteBuf, Payload> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> {
                    buf.writeVarInt(p.entityId());
                    buf.writeBoolean(p.on());
                },
                buf -> new Payload(buf.readVarInt(), buf.readBoolean()));
        private static Consumer<Payload> clientHandler;

        public static void setClientHandler(Consumer<Payload> handler) {
            clientHandler = handler;
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        public static void handle(Payload payload, IPayloadContext context) {
            if (clientHandler != null) context.enqueueWork(() -> clientHandler.accept(payload));
        }
    }
}

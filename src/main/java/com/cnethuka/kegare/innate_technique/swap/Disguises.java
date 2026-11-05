package com.cnethuka.kegare.innate_technique.swap;

import com.cnethuka.kegare.Kegare;
import com.cnethuka.kegare.network.VisualPayload;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Swap's disguises: VISUAL ONLY. A disguised player looks like someone else to everyone but themselves (name tag,
 * skin, visible armor, held items, tab-list name); inventories, health and data never move.
 * <p>
 * The server keeps the map (wearer -> who they look like, until when) and sends all of it to every client whenever it
 * changes ({@link StatePayload}). A disguise ends when it expires, or when the wearer or the player they look like
 * dies, logs out or changes dimension.
 * <p>
 * <b>API</b> for other systems (works on either side): {@link #isDisguised}, {@link #getRealIdentity},
 * {@link #getDisguisedAs}. Anything that must see through disguises should ask here rather than trust what is drawn.
 */
public final class Disguises {
    /** What made the disguise (advancements care). */
    public enum Kind { AWARENESS, MIMIC, TOTAL_SWAP }

    /** One disguise: {@code wearer} looks like {@code as} until {@code endTick} (game time). */
    public record Disguise(UUID wearer, UUID as, long endTick, Kind kind, UUID caster) {}

    /** One synced entry: entity ID -> disguised-as UUID and end tick (plus the wearer's UUID, for the tab list). */
    public record Entry(int entityId, UUID wearer, UUID as, long endTick) {}

    /** Server: by wearer. */
    private static final Map<UUID, Disguise> SERVER = new HashMap<>();
    /** Client copy (only ever filled on a client): by wearer UUID. */
    private static volatile Map<UUID, Entry> client = Map.of();

    private Disguises() {}

    // ---------------------------------------------------------------- API (either side)

    /** Whether this entity currently looks like someone else. */
    public static boolean isDisguised(Entity entity) {
        return getDisguisedAs(entity).isPresent();
    }

    /** Who this entity really is, whatever it looks like: always its own UUID. */
    public static UUID getRealIdentity(Entity entity) {
        return entity.getUUID();
    }

    /** Who this entity looks like to other players right now, if anyone. */
    public static Optional<UUID> getDisguisedAs(Entity entity) {
        if (entity.level().isClientSide) {
            Entry entry = client.get(entity.getUUID());
            return entry == null || entry.endTick() <= entity.level().getGameTime() ? Optional.empty() : Optional.of(entry.as());
        }
        Disguise disguise = SERVER.get(entity.getUUID());
        return disguise == null ? Optional.empty() : Optional.of(disguise.as());
    }

    /** Server: the full disguise (who made it, how), if this player wears one. */
    public static Optional<Disguise> get(ServerPlayer wearer) {
        return Optional.ofNullable(SERVER.get(wearer.getUUID()));
    }

    /** Client: the synced entry for a player UUID (expired ones included; callers check the end tick). */
    @Nullable
    public static Entry clientEntry(UUID wearer) {
        return client.get(wearer);
    }

    // ---------------------------------------------------------------- server

    /** {@code wearer} looks like {@code as} for {@code ticks}. Replaces any disguise the wearer had. */
    public static void apply(ServerPlayer wearer, ServerPlayer as, int ticks, Kind kind, ServerPlayer caster) {
        long end = wearer.serverLevel().getGameTime() + ticks;
        boolean had = SERVER.containsKey(wearer.getUUID());
        SERVER.put(wearer.getUUID(), new Disguise(wearer.getUUID(), as.getUUID(), end, kind, caster.getUUID()));
        if (!had) shimmer(wearer, true);
        sync(wearer.server);
    }

    /** Ends every disguise this player wears or is the face of (death, logout, dimension change). */
    public static void endInvolving(ServerPlayer player) {
        boolean changed = false;
        for (Iterator<Disguise> it = SERVER.values().iterator(); it.hasNext(); ) {
            Disguise d = it.next();
            if (d.wearer().equals(player.getUUID()) || d.as().equals(player.getUUID())) {
                it.remove();
                ServerPlayer wearer = Switchback.find(player.server, d.wearer());
                if (wearer != null) shimmer(wearer, false);
                changed = true;
            }
        }
        if (changed) sync(player.server);
    }

    /** Every server tick: expired disguises end. */
    static void tick(MinecraftServer server) {
        if (SERVER.isEmpty()) return;
        long now = server.overworld().getGameTime();
        boolean changed = false;
        for (Iterator<Disguise> it = SERVER.values().iterator(); it.hasNext(); ) {
            Disguise d = it.next();
            if (now >= d.endTick()) {
                it.remove();
                ServerPlayer wearer = Switchback.find(server, d.wearer());
                if (wearer != null) shimmer(wearer, false);
                changed = true;
            }
        }
        if (changed) sync(server);
    }

    /** A player joined: send them the current map. */
    static void sendTo(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, state(player.server));
    }

    static void clear() {
        SERVER.clear();
    }

    private static void sync(MinecraftServer server) {
        PacketDistributor.sendToAllPlayers(state(server));
    }

    private static StatePayload state(MinecraftServer server) {
        List<Entry> entries = new ArrayList<>();
        for (Disguise d : SERVER.values()) {
            ServerPlayer wearer = Switchback.find(server, d.wearer());
            if (wearer != null) entries.add(new Entry(wearer.getId(), d.wearer(), d.as(), d.endTick()));
        }
        return new StatePayload(entries);
    }

    /** The shimmer that tells a disguised player it's on: sent to that player only, never to anyone else. */
    private static void shimmer(ServerPlayer wearer, boolean on) {
        PacketDistributor.sendToPlayer(wearer, on
                ? new VisualPayload(VisualPayload.Action.START_LOOP, wearer.getId(), SwapIds.AWARENESS_SHIMMER_KEY, SwapIds.AWARENESS_SHIMMER, Vec3.ZERO, 0)
                : new VisualPayload(VisualPayload.Action.STOP, wearer.getId(), SwapIds.AWARENESS_SHIMMER_KEY,
                        com.cnethuka.kegare.innatetechnique.visual.Visual.NONE, Vec3.ZERO, 0));
    }

    // ---------------------------------------------------------------- sync

    /** Server to every client: the whole disguise map (entity ID -> disguised-as UUID, end tick). */
    public record StatePayload(List<Entry> entries) implements CustomPacketPayload {
        public static final Type<StatePayload> TYPE = new Type<>(Kegare.id("disguise_state"));
        public static final StreamCodec<FriendlyByteBuf, StatePayload> STREAM_CODEC = StreamCodec.ofMember(StatePayload::write, StatePayload::read);

        private void write(FriendlyByteBuf buf) {
            buf.writeVarInt(entries.size());
            for (Entry e : entries) {
                buf.writeVarInt(e.entityId());
                UUIDUtil.STREAM_CODEC.encode(buf, e.wearer());
                UUIDUtil.STREAM_CODEC.encode(buf, e.as());
                buf.writeVarLong(e.endTick());
            }
        }

        private static StatePayload read(FriendlyByteBuf buf) {
            int n = buf.readVarInt();
            List<Entry> entries = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                entries.add(new Entry(buf.readVarInt(), UUIDUtil.STREAM_CODEC.decode(buf), UUIDUtil.STREAM_CODEC.decode(buf), buf.readVarLong()));
            }
            return new StatePayload(entries);
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        public static void handle(StatePayload payload, IPayloadContext context) {
            Map<UUID, Entry> map = new HashMap<>();
            for (Entry e : payload.entries()) map.put(e.wearer(), e);
            client = Map.copyOf(map);
        }
    }

    /** Client: forget everything (leaving a server). */
    public static void clearClient() {
        client = Map.of();
    }
}

package com.cnethuka.kegare.reinforcement;

import com.cnethuka.kegare.combat.CombatHelper;
import com.cnethuka.kegare.innatetechnique.InnateTechnique;
import com.cnethuka.kegare.melee.MeleeTell;
import com.cnethuka.kegare.mixin.ChunkMapAccessor;
import com.cnethuka.kegare.mixin.TrackedEntityAccessor;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerPlayerConnection;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * What flows round a player's body, sent to each client as that client should see it: Reinforcement's aura (DESIGN §3.16) is open,
 * so everyone sees it, the wearer in their own Malice color and everyone else in the color of the face they wear, as for the ready
 * fist ({@link MeleeTell}), so a disguise is never given away. Each viewer is sent their own copy, since some things show only to some
 * eyes ({@link #addShimmer}). Sent when it changes, checked every tick while there's something to show.
 */
public final class AuraTell {
    private record Sent(int color, int flags) {
        static final Sent NONE = new Sent(0, 0);
    }

    private static final Map<UUID, Sent> OWN = new HashMap<>();
    /** By player, what each viewer was last sent about them. */
    private static final Map<UUID, Map<UUID, Sent>> SEEN = new HashMap<>();
    /** Things only some viewers see on a player: (viewer, player) → shimmer. */
    private static final List<BiPredicate<ServerPlayer, ServerPlayer>> SHIMMERS = new CopyOnWriteArrayList<>();
    /** Anything that might make a player worth checking every tick. */
    private static final List<Predicate<ServerPlayer>> ACTIVE = new CopyOnWriteArrayList<>();

    private AuraTell() {}

    /** A shimmer some viewers see on a player ({@link AuraPayload#SHIMMER}), and when there may be one to see. */
    public static void addShimmer(BiPredicate<ServerPlayer, ServerPlayer> seenBy, Predicate<ServerPlayer> mayShow) {
        SHIMMERS.add(seenBy);
        ACTIVE.add(mayShow);
    }

    /** What's on them for everyone: the full aura or the calm one, and whether they're in a fight. */
    static int flags(ServerPlayer player) {
        int flags = 0;
        if (ReinforcementAbility.isOn(player)) flags |= AuraPayload.FULL;
        else if (Reinforcement.backgroundStrength(player) > 0) flags |= AuraPayload.CALM;
        if (flags != 0 && CombatHelper.isInCombat(player)) flags |= AuraPayload.FIGHT;
        return flags;
    }

    private static int withShimmer(int flags, ServerPlayer viewer, ServerPlayer player) {
        for (BiPredicate<ServerPlayer, ServerPlayer> shimmer : SHIMMERS) {
            if (shimmer.test(viewer, player)) return flags | AuraPayload.SHIMMER;
        }
        return flags;
    }

    /** Should it be checked this tick? */
    static boolean active(ServerPlayer player) {
        if (OWN.containsKey(player.getUUID()) || SEEN.containsKey(player.getUUID()) || flags(player) != 0) return true;
        for (Predicate<ServerPlayer> p : ACTIVE) {
            if (p.test(player)) return true;
        }
        return false;
    }

    static void sync(ServerPlayer player) {
        int base = flags(player);
        int ownFlags = withShimmer(base, player, player);
        Sent own = ownFlags == 0 ? Sent.NONE : new Sent(color(MeleeTell.of(player)), ownFlags);
        if (!own.equals(OWN.getOrDefault(player.getUUID(), Sent.NONE))) {
            PacketDistributor.sendToPlayer(player, new AuraPayload(player.getId(), own.color(), own.flags()));
        }
        if (own == Sent.NONE) OWN.remove(player.getUUID());
        else OWN.put(player.getUUID(), own);

        int seenColor = color(MeleeTell.seenByOthers(player));
        Map<UUID, Sent> seen = SEEN.computeIfAbsent(player.getUUID(), k -> new HashMap<>());
        List<UUID> watching = new ArrayList<>();
        for (ServerPlayer viewer : viewers(player)) {
            watching.add(viewer.getUUID());
            int f = withShimmer(base, viewer, player);
            Sent sent = f == 0 ? Sent.NONE : new Sent(seenColor, f);
            if (!sent.equals(seen.getOrDefault(viewer.getUUID(), Sent.NONE))) {
                PacketDistributor.sendToPlayer(viewer, new AuraPayload(player.getId(), sent.color(), sent.flags()));
            }
            if (sent == Sent.NONE) seen.remove(viewer.getUUID());
            else seen.put(viewer.getUUID(), sent);
        }
        seen.keySet().retainAll(watching);
        if (seen.isEmpty()) SEEN.remove(player.getUUID());
    }

    /** Whoever is watching them now. */
    static List<ServerPlayer> viewers(ServerPlayer player) {
        Object tracked = ((ChunkMapAccessor) player.serverLevel().getChunkSource().chunkMap).kegare$entityMap().get(player.getId());
        if (!(tracked instanceof TrackedEntityAccessor accessor)) return List.of();
        List<ServerPlayer> out = new ArrayList<>();
        for (ServerPlayerConnection connection : accessor.kegare$seenBy()) out.add(connection.getPlayer());
        return out;
    }

    private static int color(MeleeTell.Look look) {
        return look.kind() == MeleeTell.Kind.GLOW ? look.color() : InnateTechnique.DEFAULT_MALICE_COLOR;
    }

    /** A new watcher: what they should see now. */
    static void onStartTracking(ServerPlayer viewer, Entity target) {
        if (!(target instanceof ServerPlayer player)) return;
        int f = withShimmer(flags(player), viewer, player);
        Map<UUID, Sent> seen = SEEN.computeIfAbsent(player.getUUID(), k -> new HashMap<>());
        if (f == 0) {
            seen.remove(viewer.getUUID());
            return;
        }
        Sent sent = new Sent(color(MeleeTell.seenByOthers(player)), f);
        seen.put(viewer.getUUID(), sent);
        PacketDistributor.sendToPlayer(viewer, new AuraPayload(player.getId(), sent.color(), sent.flags()));
    }

    /** A new body (respawn) or a new world: nothing on it until it's sent again. */
    static void forget(ServerPlayer player) {
        OWN.remove(player.getUUID());
        SEEN.remove(player.getUUID());
        PacketDistributor.sendToPlayer(player, new AuraPayload(player.getId(), 0, 0));
    }

    static void forget(UUID player) {
        OWN.remove(player);
        SEEN.remove(player);
        for (Map<UUID, Sent> seen : SEEN.values()) seen.remove(player);
    }

    /** What a viewer was last sent about a player (tests): its flags, or 0. */
    static int sentTo(ServerPlayer viewer, ServerPlayer player) {
        if (viewer == player) return OWN.getOrDefault(player.getUUID(), Sent.NONE).flags();
        return SEEN.getOrDefault(player.getUUID(), Map.of()).getOrDefault(viewer.getUUID(), Sent.NONE).flags();
    }

    public static void clear() {
        OWN.clear();
        SEEN.clear();
    }
}

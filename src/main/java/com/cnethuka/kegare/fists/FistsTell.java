package com.cnethuka.kegare.fists;

import com.cnethuka.kegare.melee.MeleeTell;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * What's round a player's fists, sent to each client as that client should see it (DESIGN §3.28): the wearer their own Malice color,
 * everyone else the color of the face they wear, as for the ready fist ({@link MeleeTell}), so a disguise is never given away. Sent when
 * it changes, checked every tick while there's something to show (a disguise can begin or end while it's on).
 */
public final class FistsTell {
    private record Sent(int color, int flags) {
        static final Sent NONE = new Sent(0, 0);
    }

    private static final Map<UUID, Sent> OWN = new HashMap<>();
    private static final Map<UUID, Sent> OTHERS = new HashMap<>();

    private FistsTell() {}

    /** What's there now: {@link FistsPayload#BURN} while Fists are on. */
    static int flags(ServerPlayer player, boolean on) {
        return on ? FistsPayload.BURN : 0;
    }

    static void sync(ServerPlayer player) {
        sync(player, ReinforcedFists.isOn(player));
    }

    static void sync(ServerPlayer player, boolean on) {
        int flags = flags(player, on);
        Sent own = flags == 0 ? Sent.NONE : new Sent(color(MeleeTell.of(player)), flags);
        if (!own.equals(OWN.getOrDefault(player.getUUID(), Sent.NONE))) {
            OWN.put(player.getUUID(), own);
            PacketDistributor.sendToPlayer(player, new FistsPayload(player.getId(), own.color(), own.flags()));
        }
        Sent others = flags == 0 ? Sent.NONE : new Sent(color(MeleeTell.seenByOthers(player)), flags);
        if (!others.equals(OTHERS.getOrDefault(player.getUUID(), Sent.NONE))) {
            OTHERS.put(player.getUUID(), others);
            PacketDistributor.sendToPlayersTrackingEntity(player, new FistsPayload(player.getId(), others.color(), others.flags()));
        }
        if (flags == 0) {
            OWN.remove(player.getUUID());
            OTHERS.remove(player.getUUID());
        }
    }

    /** Is anything being shown on them? (Ticked only then.) */
    static boolean showing(ServerPlayer player) {
        return OWN.containsKey(player.getUUID());
    }

    private static int color(MeleeTell.Look look) {
        return look.kind() == MeleeTell.Kind.GLOW ? look.color() : com.cnethuka.kegare.innatetechnique.InnateTechnique.DEFAULT_MALICE_COLOR;
    }

    static void onStartTracking(ServerPlayer tracker, Entity target) {
        if (!(target instanceof ServerPlayer player)) return;
        Sent others = OTHERS.get(player.getUUID());
        if (others != null) PacketDistributor.sendToPlayer(tracker, new FistsPayload(player.getId(), others.color(), others.flags()));
    }

    /** A new body (respawn) or a new world: nothing on it until it's sent again. */
    static void forget(ServerPlayer player) {
        OWN.remove(player.getUUID());
        OTHERS.remove(player.getUUID());
        PacketDistributor.sendToPlayer(player, new FistsPayload(player.getId(), 0, 0));
    }

    static void forget(UUID player) {
        OWN.remove(player);
        OTHERS.remove(player);
    }

    public static void clear() {
        OWN.clear();
        OTHERS.clear();
    }
}

package com.cnethuka.kegare.sixeyes.client;

import com.cnethuka.kegare.sixeyes.SightPayloads;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * What the eyes see, as the server last sent it (DESIGN §3.32). Only a bearer is ever sent any of it, so without the eyes every query
 * here answers "nothing". Positions are eased between updates.
 */
public final class SightClient {
    /** A player the eyes see. {@code severed}: a void. */
    public static final class Seen {
        public final UUID uuid;
        public int entityId;
        public String name;
        Vec3 pos, prev;
        long at;
        public int color, capacity;
        public boolean severed;

        Seen(SightPayloads.Players.Dot d, long now) {
            uuid = d.uuid();
            pos = prev = new Vec3(d.x(), d.y(), d.z());
            at = now;
            update(d, now);
        }

        void update(SightPayloads.Players.Dot d, long now) {
            Vec3 next = new Vec3(d.x(), d.y(), d.z());
            prev = position(now);
            if (prev.distanceToSqr(next) > 64 * 64) prev = next; // a teleport isn't a glide
            pos = next;
            at = now;
            entityId = d.entityId();
            name = d.name();
            color = d.color() & 0xFFFFFF;
            capacity = d.capacity();
            severed = d.severed();
        }

        /** Where they are now, eased from the last update to this one. */
        public Vec3 position(long nowMs) {
            float t = Math.min(1f, (nowMs - at) / (float) STEP_MS);
            return prev.lerp(pos, t);
        }
    }

    /** A mob the eyes see. */
    public record Mob(int entityId, int type, Vec3 pos, boolean hostile) {}

    /** Players update every 2 ticks: the ease spans that. */
    static final long STEP_MS = 100;
    /** How long an outline pulses. */
    static final long PULSE_MS = 450;

    private static boolean bearer;
    private static final Map<UUID, Seen> PLAYERS = new HashMap<>();
    private static final Map<Integer, UUID> PLAYER_IDS = new HashMap<>();
    private static final Map<Integer, Mob> MOBS = new HashMap<>();
    private static final Map<Integer, Long> PULSES = new HashMap<>();
    private static final Set<UUID> PINNED_PLAYERS = new HashSet<>();
    private static final Set<Integer> PINNED_MOBS = new HashSet<>();

    private SightClient() {}

    public static void init() {
        SightPayloads.Handlers.players = SightClient::players;
        SightPayloads.Handlers.mobs = SightClient::mobs;
        SightPayloads.Handlers.pulse = p -> PULSES.put(p.entityId(), now() + PULSE_MS);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                (net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingOut e) -> clear());
    }

    static long now() {
        return net.minecraft.Util.getMillis();
    }

    private static void players(SightPayloads.Players p) {
        if (!p.bearer()) {
            clear();
            return;
        }
        bearer = true;
        long now = now();
        Set<UUID> present = new HashSet<>();
        PLAYER_IDS.clear();
        for (SightPayloads.Players.Dot d : p.dots()) {
            present.add(d.uuid());
            Seen seen = PLAYERS.get(d.uuid());
            if (seen == null) PLAYERS.put(d.uuid(), new Seen(d, now));
            else seen.update(d, now);
            PLAYER_IDS.put(d.entityId(), d.uuid());
        }
        PLAYERS.keySet().retainAll(present);
        PULSES.values().removeIf(until -> until <= now); // spent pulses don't pile up
    }

    private static void mobs(SightPayloads.Mobs p) {
        MOBS.clear();
        for (SightPayloads.Mobs.Dot d : p.dots()) MOBS.put(d.entityId(), new Mob(d.entityId(), d.type(), new Vec3(d.x(), d.y(), d.z()), d.hostile()));
        PINNED_MOBS.retainAll(MOBS.keySet()); // a mob gone from the world can't be pinned
    }

    public static void clear() {
        bearer = false;
        PLAYERS.clear();
        PLAYER_IDS.clear();
        MOBS.clear();
        PULSES.clear();
        PINNED_PLAYERS.clear();
        PINNED_MOBS.clear();
    }

    public static boolean isBearer() {
        return bearer;
    }

    public static List<Seen> players() {
        return new ArrayList<>(PLAYERS.values());
    }

    public static List<Mob> mobs() {
        return new ArrayList<>(MOBS.values());
    }

    @Nullable
    public static Seen player(int entityId) {
        UUID uuid = PLAYER_IDS.get(entityId);
        return uuid == null ? null : PLAYERS.get(uuid);
    }

    @Nullable
    public static Mob mob(int entityId) {
        return MOBS.get(entityId);
    }

    public static boolean pulsing(int entityId) {
        Long until = PULSES.get(entityId);
        return until != null && until > now();
    }

    /** The neutral flow color of mobs: dim when passive, stronger when hostile. */
    public static final int MOB_PASSIVE = 0x6F8798, MOB_HOSTILE = 0xD8E4F0, MOB_PULSE = 0xFF3A28;

    /**
     * The outline color the eyes draw on this entity (a crisp line through walls, in their Malice color; mobs in the neutral flow color),
     * or null for none: not a bearer, yourself, a void (voids get a distortion instead), or something the eyes don't see. A pulse flashes
     * it white.
     */
    @Nullable
    public static Integer outlineColor(Entity entity) {
        if (!bearer) return null;
        Minecraft mc = Minecraft.getInstance();
        if (entity == mc.player || entity == mc.getCameraEntity()) return null;
        Integer color = null;
        if (entity instanceof Player) {
            Seen seen = player(entity.getId());
            if (seen != null && !seen.severed) color = bright(seen.color);
        } else {
            Mob mob = MOBS.get(entity.getId());
            if (mob != null) color = mob.hostile() ? MOB_HOSTILE : MOB_PASSIVE;
        }
        if (color == null) return null;
        if (StrainClient.sparks().containsKey(entity.getId())) return 0xFFC030; // the spark before a cast: gold, unlike a cast's white
        // A pulse flashes: white on a player, danger red on a mob (a hostile mob's outline is near white already).
        if (pulsing(entity.getId()) && (now() / 75) % 2 == 0) return entity instanceof Player ? 0xFFFFFF : MOB_PULSE;
        return color;
    }

    /** A Malice color brought to full brightness, so a dark one still draws a clear line. */
    public static int bright(int rgb) {
        return com.cnethuka.kegare.melee.MeleeTell.bright(rgb) & 0xFFFFFF;
    }

    // ---------------------------------------------------------------- pins

    public static boolean pinned(Seen seen) {
        return PINNED_PLAYERS.contains(seen.uuid);
    }

    public static boolean pinned(Mob mob) {
        return PINNED_MOBS.contains(mob.entityId());
    }

    /** Pins or unpins; returns whether it's pinned now. */
    public static boolean togglePin(Seen seen) {
        if (!PINNED_PLAYERS.remove(seen.uuid)) {
            PINNED_PLAYERS.add(seen.uuid);
            SixEyesNet.pinned(seen.entityId);
            return true;
        }
        return false;
    }

    public static boolean togglePin(Mob mob) {
        if (!PINNED_MOBS.remove(mob.entityId())) {
            PINNED_MOBS.add(mob.entityId());
            SixEyesNet.pinned(mob.entityId());
            return true;
        }
        return false;
    }

    /** Everything pinned, with where it is now: name (or null for a mob), position, color, void. */
    public record Pin(@Nullable String name, int entityId, int type, Vec3 pos, int color, boolean severed) {}

    public static List<Pin> pins() {
        List<Pin> out = new ArrayList<>();
        long now = now();
        for (UUID uuid : PINNED_PLAYERS) {
            Seen s = PLAYERS.get(uuid);
            if (s != null) out.add(new Pin(s.name, s.entityId, -1, s.position(now), s.color, s.severed));
        }
        for (int id : PINNED_MOBS) {
            Mob m = MOBS.get(id);
            if (m == null) continue;
            Entity e = Minecraft.getInstance().level == null ? null : Minecraft.getInstance().level.getEntity(id);
            out.add(new Pin(null, id, m.type(), e != null ? e.position() : m.pos(), m.hostile() ? MOB_HOSTILE : MOB_PASSIVE, false));
        }
        return out;
    }
}

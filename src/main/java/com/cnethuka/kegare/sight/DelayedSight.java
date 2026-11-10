package com.cnethuka.kegare.sight;

import com.cnethuka.kegare.combat.CombatHelper;
import com.cnethuka.kegare.innate_technique.time_manipulation.TimeManipulation;
import com.cnethuka.kegare.mixin.ChunkMapAccessor;
import com.cnethuka.kegare.mixin.ServerEntityAccessor;
import com.cnethuka.kegare.mixin.TrackedEntityAccessor;
import com.cnethuka.kegare.severed.Severed;
import com.cnethuka.kegare.trait.KegareTraits;
import com.cnethuka.kegare.trait.TraitService;
import io.netty.buffer.Unpooled;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.core.Holder;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.minecraft.network.protocol.game.ClientboundMoveEntityPacket;
import net.minecraft.network.protocol.game.ClientboundRotateHeadPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerPlayerConnection;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Too fast for the eye (DESIGN §3.30). While a Severed player is Blurring, or a Time Manipulation player has Accelerate on, everyone else
 * sees them {@code blurDelay} or {@code accelerateDelay} behind, so the faster they go, the further behind the image is. Server-side: every
 * player's position and rotation are recorded each tick; while they're delayed, viewers who shouldn't see the real position get none of
 * their movement packets, only the delayed position every tick (an absolute teleport and the head's turn). Head, body, animations and
 * afterimages all follow it on the viewer's client. The delay eases in and out over {@code easeTicks} so the image never snaps; once it
 * has caught up, every viewer goes back on vanilla's own updates together.
 * <p>
 * Who sees the real position: the player themself, a Six Eyes bearer, a Severed player's Senses (sensing isn't seeing:
 * {@link com.cnethuka.kegare.severed.Senses}) and bosses. Mobs see the delayed one: they turn to it, path to it and swing at it. Hits are
 * always judged against the real position; no reach is widened.
 */
public final class DelayedSight {
    record Sample(long tick, Vec3 pos, float yRot, float xRot, float headYaw, boolean onGround) {}

    static final class Track {
        static final int KEEP = 64;
        final Sample[] ring = new Sample[KEEP];
        int next;
        int count;
        /** The delay being applied now, in ms: eased toward the state's. */
        double shownMs;
        /** The state's delay, kept through the ease out. */
        double fullMs;
        boolean hidden;

        void record(Sample s) {
            ring[next] = s;
            next = (next + 1) % KEEP;
            count = Math.min(KEEP, count + 1);
        }

        /** Where they were {@code ticksAgo} (fractional) before the latest sample. */
        @Nullable
        Sample at(double ticksAgo) {
            if (count == 0) return null;
            ticksAgo = Math.max(0, Math.min(count - 1, ticksAgo));
            int whole = (int) Math.floor(ticksAgo);
            double frac = ticksAgo - whole;
            Sample a = get(whole), b = get(Math.min(count - 1, whole + 1));
            if (frac < 1.0e-9 || a == b) return a;
            // b is older than a: from a back toward b by frac
            Vec3 pos = a.pos().lerp(b.pos(), frac);
            return new Sample(a.tick(), pos, Mth.rotLerp((float) frac, a.yRot(), b.yRot()), (float) Mth.lerp(frac, a.xRot(), b.xRot()),
                    Mth.rotLerp((float) frac, a.headYaw(), b.headYaw()), frac < 0.5 ? a.onGround() : b.onGround());
        }

        /** 0 = the latest. */
        Sample get(int back) {
            return ring[Math.floorMod(next - 1 - back, KEEP)];
        }
    }

    private static final Map<UUID, Track> TRACKS = new HashMap<>();

    private DelayedSight() {}

    /** The delay their state calls for, in ms: Blur's or Accelerate's, or 0. */
    static double stateDelayMs(ServerPlayer player) {
        double ms = 0;
        if (Severed.blurring(player)) ms = Math.max(ms, SightConfig.BLUR_DELAY.get());
        if (TimeManipulation.ACCELERATE.isActive(player)) ms = Math.max(ms, SightConfig.ACCELERATE_DELAY.get());
        return ms;
    }

    /** Does this viewer see the real position of this player? */
    public static boolean seesReal(@Nullable Entity viewer, Entity mover) {
        if (viewer == null || viewer == mover) return true;
        if (viewer instanceof Player p && TraitService.has(p, KegareTraits.SIX_EYES.get())) return true;
        return viewer instanceof LivingEntity l && CombatHelper.isBoss(l);
    }

    /** Is this player being shown behind where they are right now (to anyone)? */
    public static boolean hidden(Entity mover) {
        Track track = TRACKS.get(mover.getUUID());
        return track != null && track.hidden;
    }

    /** The delay applied to them right now, ms (eased). */
    public static double delayMs(Entity mover) {
        Track track = TRACKS.get(mover.getUUID());
        return track == null || !track.hidden ? 0 : track.shownMs;
    }

    /** Where this viewer sees the mover's body: the delayed position, or the real one if they see it (or nothing's delayed). */
    public static Vec3 seenAt(@Nullable Entity viewer, Entity mover) {
        Track track = TRACKS.get(mover.getUUID());
        if (track == null || !track.hidden || seesReal(viewer, mover)) return mover.position();
        Sample s = track.at(track.shownMs / 50.0);
        return s == null ? mover.position() : s.pos();
    }

    /** The mover's hitbox where this viewer sees it. */
    public static AABB seenHitbox(@Nullable Entity viewer, LivingEntity mover) {
        Vec3 at = seenAt(viewer, mover);
        return mover.getHitbox().move(at.subtract(mover.position()));
    }

    /** A mob sees a delayed player where they were (bosses see the real one). */
    public static boolean misleads(Mob mob, Entity target) {
        return hidden(target) && !seesReal(mob, target);
    }

    // ------------------------------------------------------------------ every tick

    /** The end of every server tick: record everyone, ease the delays, and send the delayed image to each viewer who should get it. */
    static void tick(MinecraftServer server) {
        int ease = Math.max(1, SightConfig.EASE_TICKS.get());
        for (ServerPlayer player : players(server)) {
            Track track = TRACKS.computeIfAbsent(player.getUUID(), id -> new Track());
            long now = player.level().getGameTime();
            track.record(new Sample(now, player.position(), player.getYRot(), player.getXRot(), player.getYHeadRot(), player.onGround()));
            double target = player.isAlive() && !player.isSpectator() ? stateDelayMs(player) : 0;
            if (target > 0) track.fullMs = target;
            double step = Math.max(1.0e-6, track.fullMs / ease);
            boolean was = track.hidden;
            if (target > track.shownMs) track.shownMs = Math.min(target, track.shownMs + step);
            else if (target < track.shownMs) track.shownMs = Math.max(target, track.shownMs - step);
            track.hidden = track.shownMs > 1.0e-6;
            if (!player.isAlive()) track.hidden = false;
            if (track.hidden) sendImage(player, track);
            else if (was) resync(player); // caught up: everyone back on vanilla's own updates, together
        }
    }

    private static java.util.List<ServerPlayer> players(MinecraftServer server) {
        java.util.List<ServerPlayer> out = new java.util.ArrayList<>();
        for (ServerLevel level : server.getAllLevels()) out.addAll(level.players());
        return out;
    }

    /** Each viewer who shouldn't see the real position gets where the player was, absolutely, and the head's turn. */
    private static void sendImage(ServerPlayer player, Track track) {
        Set<ServerPlayerConnection> viewers = viewers(player);
        if (viewers == null || viewers.isEmpty()) return;
        Sample s = track.at(track.shownMs / 50.0);
        if (s == null) return;
        Packet<?> teleport = teleport(player.getId(), s.pos(), s.yRot(), s.xRot(), s.onGround());
        Packet<?> head = new ClientboundRotateHeadPacket(player, (byte) Mth.floor(s.headYaw() * 256.0F / 360.0F));
        for (ServerPlayerConnection connection : viewers) {
            if (seesReal(connection.getPlayer(), player)) continue; // they're on vanilla's own packets
            connection.send(teleport);
            connection.send(head);
        }
    }

    /** Every viewer gets the real position at once, and the tracker counts from it again, so vanilla's relative moves line up for all. */
    private static void resync(ServerPlayer player) {
        Object tracked = tracked(player);
        if (!(tracked instanceof TrackedEntityAccessor accessor)) return;
        Packet<?> teleport = new ClientboundTeleportEntityPacket(player);
        Packet<?> head = new ClientboundRotateHeadPacket(player, (byte) Mth.floor(player.getYHeadRot() * 256.0F / 360.0F));
        for (ServerPlayerConnection connection : accessor.kegare$seenBy()) {
            connection.send(teleport);
            connection.send(head);
        }
        ((ServerEntityAccessor) accessor.kegare$serverEntity()).kegare$positionCodec().setBase(player.trackingPosition());
    }

    @Nullable
    private static Object tracked(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        return ((ChunkMapAccessor) level.getChunkSource().chunkMap).kegare$entityMap().get(player.getId());
    }

    @Nullable
    private static Set<ServerPlayerConnection> viewers(ServerPlayer player) {
        return tracked(player) instanceof TrackedEntityAccessor accessor ? accessor.kegare$seenBy() : null;
    }

    /** A teleport packet to anywhere (vanilla's only takes an entity's own position). */
    static ClientboundTeleportEntityPacket teleport(int id, Vec3 pos, float yRot, float xRot, boolean onGround) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeVarInt(id);
        buf.writeDouble(pos.x);
        buf.writeDouble(pos.y);
        buf.writeDouble(pos.z);
        buf.writeByte(Mth.floor(yRot * 256.0F / 360.0F));
        buf.writeByte(Mth.floor(xRot * 256.0F / 360.0F));
        buf.writeBoolean(onGround);
        try {
            return ClientboundTeleportEntityPacket.STREAM_CODEC.decode(buf);
        } finally {
            buf.release();
        }
    }

    /** Vanilla's own movement updates for an entity: what a viewer of a delayed player mustn't get. */
    public static boolean isMovement(Packet<?> packet) {
        return packet instanceof ClientboundMoveEntityPacket || packet instanceof ClientboundTeleportEntityPacket
                || packet instanceof ClientboundRotateHeadPacket || packet instanceof ClientboundSetEntityMotionPacket;
    }

    /** The image's sample right now (for pairing a new viewer, and the tests). */
    @Nullable
    static Sample image(Entity mover) {
        Track track = TRACKS.get(mover.getUUID());
        return track == null || !track.hidden ? null : track.at(track.shownMs / 50.0);
    }

    /** For a viewer starting to watch a delayed player: the spawn packet at the delayed position, or null to send vanilla's own. */
    @Nullable
    public static Packet<ClientGamePacketListener> spawnPacket(Entity mover, ServerPlayer viewer) {
        if (!hidden(mover) || seesReal(viewer, mover)) return null;
        Sample s = image(mover);
        if (s == null) return null;
        return new ClientboundAddEntityPacket(mover.getId(), mover.getUUID(), s.pos().x, s.pos().y, s.pos().z, s.xRot(), s.yRot(), mover.getType(), 0,
                Vec3.ZERO, s.headYaw());
    }

    /**
     * A sound a delayed player makes as they go (Blur's wind, Accelerate's ticking), heard where each listener sees them: a sound's
     * position would give the real one away. Everyone in range gets it from the image, unless they see the real position.
     */
    public static void playSound(ServerPlayer mover, Holder<SoundEvent> sound, SoundSource source, float volume, float pitch) {
        if (!hidden(mover)) {
            mover.level().playSound(null, mover.getX(), mover.getY(), mover.getZ(), sound, source, volume, pitch);
            return;
        }
        long seed = mover.getRandom().nextLong();
        double range = sound.value().getRange(volume);
        for (ServerPlayer listener : mover.serverLevel().players()) {
            Vec3 at = seenAt(listener, mover);
            if (listener.distanceToSqr(at) > range * range || listener.connection == null) continue;
            listener.connection.send(new ClientboundSoundPacket(sound, source, at.x, at.y, at.z, volume, pitch, seed));
        }
    }

    public static void init() {
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, (ServerTickEvent.Post e) -> tick(e.getServer()));
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> forget(e.getEntity().getUUID()));
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerChangedDimensionEvent e) -> forget(e.getEntity().getUUID()));
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerRespawnEvent e) -> forget(e.getEntity().getUUID()));
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent e) -> clear());
    }

    static void forget(UUID player) {
        TRACKS.remove(player);
    }

    public static void clear() {
        TRACKS.clear();
    }
}

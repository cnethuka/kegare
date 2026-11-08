package com.cnethuka.kegare.innate_technique.time_manipulation;

import com.cnethuka.kegare.combat.CombatHelper;
import com.cnethuka.kegare.impact.ScreenEffects;
import com.cnethuka.kegare.innatetechnique.Burnout;
import com.cnethuka.kegare.progression.InsightHooks;
import com.cnethuka.kegare.sound.KegareSounds;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.WeakHashMap;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Standstill's stops (DESIGN §3.26): at most one per level, runtime only (never saved). Not vanilla's /tick freeze: everything inside
 * the radius is frozen by cancelling its tick ({@link EntityTickEvent.Pre}), except the caster, players holding Time Manipulation
 * (immune: they move, attack and cast as normal, and damage between them and the caster lands at once), bosses (DESIGN §4: they take
 * the damage, but time never stops for them), and projectiles the caster and the immune throw,
 * for their first {@code projectileFlightTicks}, after which they hang where they are, their speed kept. A frozen player's own physics
 * runs from their connection, so they're also stunned, their attacks, interactions, item use and block breaking are refused, and they're
 * put back if they move. Damage to anything frozen is held, and when time resumes each one takes it as ONE hit through its normal
 * {@code hurt} (armour, shields and everything else apply once, to the sum), at most {@code damageCap}, so the Impact System and battle
 * damage see it as one blow. Ends early if the caster dies, logs out or leaves the level. A frozen player who logs out or leaves the
 * level takes what was held for them there and then, as that same one hit (it can kill them); one who dies during it has nothing
 * left to land. Afterwards the caster burns out.
 */
public final class TimeStops {
    /** A frozen player: where they're held, and the stun they had before. */
    static final class Held {
        final Vec3 at;
        final long stunBefore;

        Held(Vec3 at, long stunBefore) {
            this.at = at;
            this.stunBefore = stunBefore;
        }
    }

    /** What was dealt to someone frozen: the total, and the biggest single source of it (which the one hit is dealt as). */
    static final class Queued {
        float total;
        float biggest;
        DamageSource source;
        int hits;
    }

    static final class Stop {
        final ServerLevel level;
        final ServerPlayer caster;
        final Vec3 centre;
        final double radius;
        final long started, until;
        /** Who moves in it besides the caster (players holding Time Manipulation, inside it), by entity id, as last sent. */
        List<Integer> free = List.of();
        final Map<ServerPlayer, Held> held = new IdentityHashMap<>();
        final Map<LivingEntity, Queued> queued = new IdentityHashMap<>();
        /** Projectiles thrown into it by the caster or the immune: ticks they still fly. */
        final Map<Entity, Integer> flight = new WeakHashMap<>();
        /** Projectiles of the caster or the immune that hung in it, and whose they are (for Pre-Planned). */
        final Map<Entity, UUID> hung = new WeakHashMap<>();

        Stop(ServerLevel level, ServerPlayer caster, Vec3 centre, double radius, long until) {
            this.level = level;
            this.caster = caster;
            this.centre = centre;
            this.radius = radius;
            this.started = level.getGameTime();
            this.until = until;
        }

        boolean inside(Entity e) {
            return e.level() == level && e.position().distanceToSqr(centre) <= radius * radius;
        }

        /** Moves in it: the caster, a player holding Time Manipulation, or something carrying one. */
        boolean free(Entity e) {
            if (e == caster) return true;
            if (e instanceof Player p) return p.isSpectator() || TimeManipulation.holds(p);
            for (Entity passenger : e.getPassengers()) if (passenger instanceof Player p && free(p)) return true;
            return false;
        }

        boolean frozen(Entity e) {
            if (!inside(e) || free(e) || CombatHelper.isBoss(e)) return false; // a boss is never stopped (DESIGN §4)
            Integer flying = flight.get(e);
            return flying == null || flying <= 0;
        }
    }

    /** Projectiles that hung in a stop now over, whose they are, and until when a hit on a player still counts (Pre-Planned). */
    record Volley(UUID owner, long until) {}

    private static final Map<ResourceKey<Level>, Stop> STOPS = new HashMap<>();
    private static final Map<Entity, Volley> VOLLEYS = new WeakHashMap<>();
    private static final Map<UUID, Integer> VOLLEY_HITS = new HashMap<>();
    /** The one being dealt their held hit right now: that hit is let through, not held again. */
    @Nullable private static LivingEntity landing;
    /** Grayscale is asked for this much longer than the stop, so it never fades out early: the end snaps it off. */
    private static final int GRAY_SLACK = 40;

    private TimeStops() {}

    public static boolean active(Level level) {
        return STOPS.containsKey(level.dimension());
    }

    static Optional<Stop> get(Level level) {
        return Optional.ofNullable(STOPS.get(level.dimension()));
    }

    /** Whether time is stopped for this entity right now (server side). */
    public static boolean frozen(Entity e) {
        if (e.level().isClientSide) return false;
        Stop stop = STOPS.get(e.level().dimension());
        return stop != null && stop.frozen(e);
    }

    // ---------------------------------------------------------------- starting and ending

    static Stop start(ServerPlayer caster, Vec3 centre, double radius, int ticks) {
        ServerLevel level = caster.serverLevel();
        Stop stop = new Stop(level, caster, centre, radius, level.getGameTime() + ticks);
        STOPS.put(level.dimension(), stop);
        refresh(stop, true);
        // The world hears it go: a sharp click, then nothing.
        float volume = (float) Math.max(1.0, radius / 16.0);
        level.playSound(null, centre.x, centre.y, centre.z, KegareSounds.TIME_STOP.value(), SoundSource.PLAYERS, volume, 1f);
        TimeAdvancements.award(caster, TimeAdvancements.THE_WORLD_STOOD_STILL);
        return stop;
    }

    /** Time resumes: everyone let go, every held hit dealt, the caster burnt out. {@code early}: the caster died, left, or went. */
    static void end(Stop stop, boolean early) {
        if (STOPS.get(stop.level.dimension()) != stop) return;
        STOPS.remove(stop.level.dimension());
        long now = stop.level.getGameTime();
        PacketDistributor.sendToPlayersInDimension(stop.level, TimeNet.Stop.none());
        for (Map.Entry<ServerPlayer, Held> e : stop.held.entrySet()) release(e.getKey(), e.getValue(), stop, now);
        float volume = (float) Math.max(1.0, stop.radius / 16.0);
        stop.level.playSound(null, stop.centre.x, stop.centre.y, stop.centre.z, KegareSounds.TIME_RESUME.value(), SoundSource.PLAYERS, volume, 1f);
        // Everything lands at once.
        for (Map.Entry<LivingEntity, Queued> e : stop.queued.entrySet()) land(e.getKey(), e.getValue());
        for (ServerPlayer p : stop.held.keySet()) if (p.isAlive() && !p.isRemoved()) InsightHooks.survivedStandstill(p);
        for (UUID owner : stop.hung.values()) VOLLEY_HITS.remove(owner); // each stop's volley counts on its own
        for (Map.Entry<Entity, UUID> e : stop.hung.entrySet()) {
            if (e.getKey() != null && e.getKey().isAlive()) VOLLEYS.put(e.getKey(), new Volley(e.getValue(), now + TimeAdvancements.PRE_PLANNED_WINDOW_TICKS));
        }
        ServerPlayer caster = stop.caster;
        if (caster.isAlive()) Burnout.apply(caster, TimeConfig.ticks(TimeConfig.STANDSTILL_BURNOUT));
    }

    /**
     * What was held for {@code victim}, dealt as ONE hit through its normal {@code hurt} (armour, shields and the rest apply once, to
     * the sum; the Impact System and battle damage see one blow), as the biggest single source of it, at most {@code damageCap}.
     */
    private static void land(LivingEntity victim, Queued q) {
        if (!victim.isAlive() || victim.isRemoved() || q.total <= 0f || q.source == null) return;
        landing = victim;
        try {
            victim.invulnerableTime = 0;
            victim.hurt(q.source, (float) Math.min(q.total, TimeConfig.STANDSTILL_DAMAGE_CAP.get()));
            victim.invulnerableTime = 0; // what hung in the air lands right after, whole
        } finally {
            landing = null;
        }
    }

    private static void release(ServerPlayer p, Held held, Stop stop, long now) {
        // The stun was ours if nothing has stretched it past the stop; then the one they had before is all that's left.
        long stunEnd = now + CombatHelper.stunTicksRemaining(p);
        if (stunEnd <= stop.until) {
            CombatHelper.clearStun(p);
            if (held.stunBefore > now) CombatHelper.stun(p, (int) (held.stunBefore - now));
        }
        if (p.connection != null) ScreenEffects.play(p, ScreenEffects.GRAYSCALE, 0);
    }

    private static void hold(ServerPlayer p, Stop stop, long now) {
        long stunBefore = now + CombatHelper.stunTicksRemaining(p);
        stop.held.put(p, new Held(p.position(), stunBefore));
        CombatHelper.stun(p, (int) Math.max(1, stop.until - now));
        if (p.connection != null) ScreenEffects.play(p, ScreenEffects.GRAYSCALE, (int) (stop.until - now) + GRAY_SLACK);
    }

    /** Who's held and who's free, again: anyone who walked in, changed technique or left. Tells the level when that changed. */
    private static void refresh(Stop stop, boolean first) {
        long now = stop.level.getGameTime();
        List<Integer> free = new ArrayList<>();
        free.add(stop.caster.getId());
        for (ServerPlayer p : stop.level.players()) {
            if (p == stop.caster || !p.isAlive() || !stop.inside(p)) continue;
            if (stop.free(p)) {
                if (!p.isSpectator()) {
                    free.add(p.getId());
                    TimeAdvancements.award(p, TimeAdvancements.SAME_CLOCK);
                }
            } else if (!stop.held.containsKey(p)) {
                hold(p, stop, now);
            }
        }
        for (Iterator<Map.Entry<ServerPlayer, Held>> it = stop.held.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<ServerPlayer, Held> e = it.next();
            ServerPlayer p = e.getKey();
            if (p.isRemoved() || !p.isAlive() || !stop.frozen(p)) {
                it.remove();
                release(p, e.getValue(), stop, now);
            }
        }
        if (first || !free.equals(stop.free)) {
            stop.free = List.copyOf(free);
            PacketDistributor.sendToPlayersInDimension(stop.level, payload(stop));
        }
    }

    private static TimeNet.Stop payload(Stop stop) {
        return new TimeNet.Stop(true, stop.caster.getId(), stop.centre, (float) stop.radius, stop.until, stop.free,
                TimeConfig.STANDSTILL_FLIGHT_TICKS.get());
    }

    /** What a player joining the level is told: the stop there, or that there's none. */
    static void sendTo(ServerPlayer player) {
        Stop stop = STOPS.get(player.level().dimension());
        PacketDistributor.sendToPlayer(player, stop == null ? TimeNet.Stop.none() : payload(stop));
    }

    // ---------------------------------------------------------------- every tick

    /** Each level's tick, after its entities: runs out, ends early, holds frozen players where they are. */
    static void tick(ServerLevel level) {
        Stop stop = STOPS.get(level.dimension());
        long now = level.getGameTime();
        if (stop != null) {
            ServerPlayer caster = stop.caster;
            if (caster.isRemoved() || !caster.isAlive() || caster.level() != level || caster.hasDisconnected()) {
                end(stop, true);
            } else if (now >= stop.until) {
                end(stop, false);
            } else {
                refresh(stop, false);
                for (Map.Entry<ServerPlayer, Held> e : stop.held.entrySet()) {
                    ServerPlayer p = e.getKey();
                    Vec3 at = e.getValue().at;
                    if (p.position().distanceToSqr(at) > 1.0e-4) {
                        if (p instanceof FakePlayer) p.setPos(at);
                        else p.connection.teleport(at.x, at.y, at.z, p.getYRot(), p.getXRot());
                    }
                    p.setDeltaMovement(Vec3.ZERO);
                    p.resetFallDistance();
                }
                // The caster's (or someone immune's) projectiles hanging in it, thrown before it or into it (Pre-Planned).
                for (Projectile p : level.getEntitiesOfClass(Projectile.class, new AABB(stop.centre, stop.centre).inflate(stop.radius),
                        p -> p.isAlive() && p.getOwner() != null && stop.free(p.getOwner()) && stop.frozen(p))) {
                    stop.hung.putIfAbsent(p, p.getOwner().getUUID());
                }
            }
        }
        if (!VOLLEYS.isEmpty() && now % 20 == 0) VOLLEYS.values().removeIf(v -> v.until() < now);
    }

    /** Server side: a frozen entity's tick is skipped, and its age with it (lifetimes counted by it don't run). */
    static void onEntityTick(EntityTickEvent.Pre event) {
        Entity e = event.getEntity();
        if (e.level().isClientSide) return;
        Stop stop = STOPS.get(e.level().dimension());
        if (stop == null) return;
        Integer flying = stop.flight.get(e);
        if (flying != null && flying > 0) {
            stop.flight.put(e, flying - 1);
            return;
        }
        if (!stop.frozen(e)) return;
        event.setCanceled(true);
        e.tickCount--;
    }

    /** A projectile the caster or someone immune throws into it flies its first few ticks, then hangs too. */
    static void onJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide || !(event.getEntity() instanceof Projectile p)) return;
        Stop stop = STOPS.get(event.getLevel().dimension());
        if (stop == null || p.getOwner() == null || !stop.free(p.getOwner())) return;
        stop.flight.put(p, TimeConfig.STANDSTILL_FLIGHT_TICKS.get());
    }

    /**
     * Damage to anything frozen is held for the resume, at its full incoming amount (first of all, so nothing else changes it twice: it
     * all goes through once, on the sum). Hurt with nothing behind it (fire, falling, drowning) doesn't happen in stopped time; the void
     * and /kill still do.
     */
    static void onIncomingDamage(LivingIncomingDamageEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide) return;
        Stop stop = STOPS.get(victim.level().dimension());
        if (stop == null || victim == landing || !stop.frozen(victim)) return;
        DamageSource source = event.getSource();
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return;
        event.setCanceled(true);
        if (source.getEntity() == null && source.getDirectEntity() == null) return;
        float amount = event.getAmount();
        if (amount <= 0f) return;
        Queued q = stop.queued.computeIfAbsent(victim, v -> new Queued());
        q.total += amount;
        q.hits++;
        if (q.source == null || amount > q.biggest) {
            q.biggest = amount;
            q.source = source;
        }
    }

    /** Pre-Planned: a projectile that hung in a stop hitting a player once time runs again. */
    static void onImpact(ProjectileImpactEvent event) {
        Entity projectile = event.getProjectile();
        if (projectile.level().isClientSide || !(event.getRayTraceResult() instanceof EntityHitResult hit)) return;
        Volley volley = VOLLEYS.remove(projectile);
        if (volley == null || !(hit.getEntity() instanceof ServerPlayer victim) || victim.getUUID().equals(volley.owner())) return;
        if (projectile.level().getGameTime() > volley.until()) return;
        int hits = VOLLEY_HITS.merge(volley.owner(), 1, Integer::sum);
        if (projectile instanceof Projectile p && p.getOwner() instanceof ServerPlayer owner && hits >= TimeAdvancements.PRE_PLANNED_HITS) {
            TimeAdvancements.award(owner, TimeAdvancements.PRE_PLANNED);
        }
    }

    /**
     * Logging out, or gone to another level: a caster's stop ends (what was held lands); a frozen player is let go and takes what was
     * held for them right now, before they're gone, as the one hit (it can kill them). Never a way out of it.
     */
    static void onLeaving(ServerPlayer player) {
        for (Stop stop : List.copyOf(STOPS.values())) {
            if (stop.caster == player) {
                end(stop, true);
                continue;
            }
            letGo(player, stop, true);
        }
    }

    /**
     * About to travel to another level (before it happens, so the hit lands where they were held). The caster's own stop ends once
     * they've actually gone ({@link #onLeaving}), in case the trip is called off.
     */
    static void onTravel(ServerPlayer player) {
        for (Stop stop : List.copyOf(STOPS.values())) if (stop.caster != player) letGo(player, stop, true);
    }

    /** Died: a caster's stop ends (what was held lands on the rest); a frozen player who died has nothing left to land. */
    static void onDied(ServerPlayer player) {
        for (Stop stop : List.copyOf(STOPS.values())) {
            if (stop.caster == player) {
                end(stop, true);
                continue;
            }
            letGo(player, stop, false);
        }
    }

    private static void letGo(ServerPlayer player, Stop stop, boolean landIt) {
        Held held = stop.held.remove(player);
        if (held != null) release(player, held, stop, stop.level.getGameTime());
        Queued q = stop.queued.remove(player);
        if (landIt && q != null) land(player, q);
    }

    /** Moved by the caster's own technique (Rewind) while frozen: held there instead. */
    static void moved(Entity e) {
        if (!(e instanceof ServerPlayer p)) return;
        Stop stop = STOPS.get(p.level().dimension());
        if (stop == null) return;
        Held held = stop.held.get(p);
        if (held != null) stop.held.put(p, new Held(p.position(), held.stunBefore));
    }

    /** Whether a hit on this entity was just held for the resume (so a technique's own rider, like Decelerate's slow, still lands). */
    static boolean holding(LivingEntity victim) {
        Stop stop = STOPS.get(victim.level().dimension());
        return stop != null && stop.queued.containsKey(victim) && stop.frozen(victim);
    }

    @Nullable
    static ServerPlayer caster(Level level) {
        Stop stop = STOPS.get(level.dimension());
        return stop == null ? null : stop.caster;
    }

    static void clear() {
        STOPS.clear();
        VOLLEYS.clear();
        VOLLEY_HITS.clear();
    }
}

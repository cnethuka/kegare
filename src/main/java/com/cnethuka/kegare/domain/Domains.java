package com.cnethuka.kegare.domain;

import com.cnethuka.kegare.combat.CombatHelper;
import com.cnethuka.kegare.flow.Flow;
import com.cnethuka.kegare.flow.FlowConfig;
import com.cnethuka.kegare.impact.ImpactStrength;
import com.cnethuka.kegare.impact.Impacts;
import com.cnethuka.kegare.innatetechnique.Burnout;
import com.cnethuka.kegare.innatetechnique.CastIntent;
import com.cnethuka.kegare.malice.MaliceService;
import com.cnethuka.kegare.progression.InsightHooks;
import com.cnethuka.kegare.rmt.BrainDamage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;
import javax.annotation.Nullable;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Every open Domain (DESIGN §3.35), server side. A Domain opens round its caster: its barrier rises for {@code openSeconds}, then
 * closes, and whatever is alive inside is trapped until it ends or breaks. Nobody crosses it either way: everyone's own movement stops at
 * it (both sides run the same check, {@link DomainBounds}), and anything that gets across another way (a teleport) is put back the next
 * tick. Projectiles stop at it, and hits across it land on it instead. It takes the Impact System's strength of every hit on it, from
 * inside and out, and breaks when that runs out. It ends when its time is up, when it breaks, or when its caster dies, logs out or
 * changes dimension; then its caster burns out.
 * <p>
 * The world is never changed: the barrier is a rule here and a drawing on the clients, and its marker ({@link DomainEntity}) is never
 * saved, so a crash or a stop mid-Domain leaves nothing behind. Battle damage inside follows its usual mode.
 */
public final class Domains {
    public enum EndReason {
        EXPIRED, BROKEN, CASTER_GONE, COMMAND, CLASH_LOST, CLASH_TIE, SERVER_STOP;

        boolean broken() {
            return this == BROKEN || this == CLASH_LOST || this == CLASH_TIE;
        }
    }

    /** Hooks for the clash (DESIGN §3.35): who decides what happens when a Domain opens over another. */
    public interface OpenRule {
        /** Empty: open normally. A refusal: say it and pay nothing. */
        Optional<Component> refuse(ServerPlayer caster, DomainSpec spec, List<DomainEntity> overlapping);
    }

    private static final List<OpenRule> OPEN_RULES = new CopyOnWriteArrayList<>();
    /** Told when a Domain opens over others: the clash starts here. */
    private static final List<BiConsumer<DomainEntity, List<DomainEntity>>> OVERLAP_LISTENERS = new CopyOnWriteArrayList<>();
    /** Told when a Domain ends, with why. */
    private static final List<BiConsumer<DomainEntity, EndReason>> END_LISTENERS = new CopyOnWriteArrayList<>();
    /** When each player last hit a barrier with a swing (their own cooldown for it: an air swing doesn't reset vanilla's). */
    private static final Map<UUID, Long> LAST_SWING = new HashMap<>();

    private Domains() {}

    public static void init() {
        InnerSpaces.init();
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post e) -> serverTick());
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, Domains::onIncoming);
        NeoForge.EVENT_BUS.addListener(Domains::onProjectileImpact);
        NeoForge.EVENT_BUS.addListener((LivingDeathEvent e) -> onDeath(e.getEntity()));
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer p) onGone(p, true);
        });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerChangedDimensionEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer p) onGone(p, true);
        });
        NeoForge.EVENT_BUS.addListener((ServerStoppingEvent e) -> {
            for (DomainEntity d : List.copyOf(DomainEntity.allServer())) end(d, EndReason.SERVER_STOP);
            LAST_SWING.clear();
        });
        Impacts.listenBlasts(Domains::onBlast);
        // Key-ups pass through untouched; a clash only needs to know the key that opened a Domain has been let go.
        com.cnethuka.kegare.innatetechnique.CastManager.addInterceptor((player, intent) -> {
            if (intent == CastIntent.CAST_RELEASE || intent == CastIntent.SPECIAL_RELEASE || intent == CastIntent.ULTIMATE_RELEASE) keyReleased(player, intent);
            return false;
        });
        // Nothing is swapped, rewound or pulled across a closed barrier.
        NeoForge.EVENT_BUS.addListener((com.cnethuka.kegare.innate_technique.swap.SwapTargetEvent e) -> {
            if (separated(e.getCaster(), e.getTarget())) e.setCanceled(true);
        });
        NeoForge.EVENT_BUS.addListener((com.cnethuka.kegare.innate_technique.time_manipulation.TimeTargetEvent e) -> {
            if (separated(e.getCaster(), e.getTarget())) e.setCanceled(true);
        });
        NeoForge.EVENT_BUS.addListener((com.cnethuka.kegare.innate_technique.puppet_manipulation.StringsTargetEvent e) -> {
            if (separated(e.getCaster(), e.getTarget())) e.setCanceled(true);
        });
    }

    public static void addOpenRule(OpenRule rule) {
        OPEN_RULES.add(rule);
    }

    public static void addOverlapListener(BiConsumer<DomainEntity, List<DomainEntity>> listener) {
        OVERLAP_LISTENERS.add(listener);
    }

    public static void addEndListener(BiConsumer<DomainEntity, EndReason> listener) {
        END_LISTENERS.add(listener);
    }

    // ---------------------------------------------------------------------------------------------- opening

    /** Why this caster can't open this Domain now, or empty if they can. Nothing is paid when there's a reason. */
    public static Optional<Component> refusal(ServerPlayer caster, DomainSpec spec) {
        if (MaliceService.isSevered(caster)) return Optional.of(Component.translatable("message.kegare.domain.severed"));
        if (BrainDamage.refusesDomain(caster)) return Optional.of(Component.translatable("message.kegare.domain.brain_full"));
        // Burnout is what stands between one Domain and the next (DESIGN §3.17), an Incomplete one too, though it isn't the technique's.
        if (Burnout.isBurntOut(caster)) {
            return Optional.of(Component.translatable("message.kegare.cast.burnt_out", com.cnethuka.kegare.innatetechnique.CastManager.seconds(Burnout.remaining(caster))));
        }
        if (ownedBy(caster).isPresent()) return Optional.of(Component.translatable("message.kegare.domain.already_open"));
        double needed = spec.meterNeeded() * DomainData.MAX;
        if (DomainMeter.get(caster) + 1.0e-6 < needed) {
            return Optional.of(Component.translatable(spec.meterNeeded() >= 1.0 ? "message.kegare.domain.meter_full" : "message.kegare.domain.meter_part",
                    Math.round(needed)));
        }
        List<DomainEntity> overlapping = overlapping((ServerLevel) caster.level(), caster.position(), spec.radius(), null);
        if (InnerSpaces.isInnerSpace(caster.level())) {
            // In an inner space, only over its own sky: a clash with the Domain that took them in.
            if (overlapping.stream().noneMatch(DomainEntity::isInner)) return Optional.of(Component.translatable("message.kegare.domain.inner_space"));
        } else if (overlapping.stream().anyMatch(o -> o.closedAt() >= 0 && o.state() != DomainEntity.State.CLASH)) {
            // One that has sealed is a separate space now: break in, or be taken in and open yours there.
            return Optional.of(Component.translatable("message.kegare.domain.sealed"));
        }
        if (!overlapping.isEmpty()) {
            if (OPEN_RULES.isEmpty()) return Optional.of(Component.translatable("message.kegare.domain.in_the_way"));
            for (OpenRule rule : OPEN_RULES) {
                Optional<Component> refused = rule.refuse(caster, spec, overlapping);
                if (refused.isPresent()) return refused;
            }
        }
        return Optional.empty();
    }

    /**
     * Opens a Domain round {@code caster} (its cost is already paid by the cast). {@code releaseIntent} is the key-up that would let go of
     * the key that opened it: a clash needs that key held. Returns the barrier, or null if it can't open (nothing taken then).
     */
    @Nullable
    public static DomainEntity open(ServerPlayer caster, DomainSpec spec, CastIntent releaseIntent) {
        if (refusal(caster, spec).isPresent()) return null;
        ServerLevel level = caster.serverLevel();
        DomainMeter.take(caster, spec.meterNeeded() * DomainData.MAX);
        DomainEntity d = new DomainEntity(level, caster, spec);
        d.releaseIntent = releaseIntent;
        level.addFreshEntity(d);
        if (!InnerSpaces.isInnerSpace(level)) d.space = InnerSpaces.take(d, spec); // built while its barrier spreads
        List<DomainEntity> overlapping = overlapping(level, d.centre(), spec.radius(), d);
        moment(d, DomainNet.Moment.OPENING, d.centre(), 0f);
        if (!overlapping.isEmpty()) OVERLAP_LISTENERS.forEach(l -> l.accept(d, overlapping));
        return d;
    }

    /** The barriers whose spheres overlap one of this size here (other than {@code except}). */
    public static List<DomainEntity> overlapping(ServerLevel level, Vec3 centre, double radius, @Nullable DomainEntity except) {
        List<DomainEntity> out = new ArrayList<>();
        for (DomainEntity d : DomainEntity.in(level)) {
            if (d != except && !d.ended && d.centre().distanceTo(centre) < d.radius() + radius) out.add(d);
        }
        return out;
    }

    // ---------------------------------------------------------------------------------------------- ticking

    static void tick(DomainEntity d) {
        if (d.ended) return;
        if (d.isInner()) { // its shell runs it; this only keeps everyone inside
            if (d.anchor == null || d.anchor.ended || d.anchor.isRemoved()) d.discard();
            else keepInside(d);
            return;
        }
        if (d.spec == null || d.caster == null) { // only ever made by open(); anything else is stray
            d.discard();
            return;
        }
        ServerPlayer caster = caster(d);
        boolean present = caster != null && (caster.level() == d.level() || d.space != null && caster.level() == d.space.level);
        if (caster == null || !caster.isAlive() || !present) {
            end(d, EndReason.CASTER_GONE);
            return;
        }
        switch (d.state()) {
            case OPENING -> {
                if (d.space != null) d.space.buildSome();
                if (d.tickCount < d.openTicks()) break;
                if (InnerSpaces.isInnerSpace(d.level())) endQuietly(d); // one opened in an inner space only ever clashes
                else close(d);
            }
            case OPEN -> {
                keepSides(d);
                d.spec.hooks().tickInside(d.context());
                if (d.tickCount - d.closedAt() >= d.duration()) end(d, EndReason.EXPIRED);
            }
            case CLASH -> {} // its clash runs it (Clashes, DESIGN §3.35)
        }
    }

    /** The barrier closes: whatever is alive inside is trapped. */
    public static void close(DomainEntity d) {
        close(d, 0, true);
    }

    /**
     * Closes it, as if it had already been closed for {@code usedTicks} (a clash's winner closing again keeps the time it had used), and
     * runs its {@code onOpen} only if {@code firstTime}.
     */
    static void close(DomainEntity d, int usedTicks, boolean firstTime) {
        ServerLevel level = (ServerLevel) d.level();
        d.setState(DomainEntity.State.OPEN);
        d.markClosed(usedTicks);
        if (d.space != null && d.space.inner != null) { // it had sealed before (a clash it won): everyone is still in its inner space
            d.space.inner.markClosed(usedTicks);
            return;
        }
        d.trapped.clear();
        ServerPlayer caster = caster(d);
        List<LivingEntity> in = level.getEntitiesOfClass(LivingEntity.class, box(d, 1), e -> constrained(e) && d.contains(DomainEntity.middle(e)));
        for (LivingEntity e : in) d.trapped.add(e.getUUID());
        if (d.space != null) {
            // Sealed: its inner space is finished, its sky stands in it, and everyone inside is taken in.
            d.space.finishBuilding();
            d.space.outerCentre = d.centre();
            d.space.outerRadius = d.radius();
            d.space.inner = DomainEntity.innerOf(d, d.space);
            d.space.level.addFreshEntity(d.space.inner);
            InnerSpaces.takeIn(d.space, d.centre(), in);
        } else {
            // No inner space could be made (no dimension for it): they're held in the world instead, each client told its side.
            for (ServerPlayer p : level.players()) {
                boolean inside = d.trapped.contains(p.getUUID());
                if (inside || p.distanceToSqr(d) < Math.pow(d.radius() + 96, 2)) PacketDistributor.sendToPlayer(p, new DomainNet.Trapped(d.getId(), inside));
            }
        }
        for (LivingEntity e : in) {
            if (e instanceof ServerPlayer p && p != caster) InsightHooks.caughtInDomain(p, d.spec.ability());
        }
        moment(d, DomainNet.Moment.CLOSED, d.centre(), 0f);
        if (firstTime) d.spec.hooks().onOpen(d.context());
    }

    /**
     * A clash in its inner space went to the Domain opened there: it takes the space over. The loser's shell breaks (they burn out as
     * usual) and the winner's stands in the world where it did; the inner space is rebuilt as the winner's round everyone still in it, its
     * time whole. Returns the winner's shell.
     */
    static DomainEntity takeOver(DomainEntity loser, DomainEntity winner) {
        InnerSpace space = loser.space;
        ServerPlayer caster = caster(winner);
        if (space == null || caster == null || winner.spec == null) {
            end(loser, EndReason.CLASH_LOST);
            endQuietly(winner);
            return loser;
        }
        java.util.Set<UUID> held = new java.util.HashSet<>(loser.trapped);
        DomainEntity oldSky = space.inner;
        if (oldSky != null) send(oldSky, DomainNet.Moment.BROKE, oldSky.centre(), 0f); // its sky shatters for everyone inside
        loser.space = null; // the space isn't let go: it's the winner's now
        end(loser, EndReason.CLASH_LOST);
        if (oldSky != null) {
            oldSky.ended = true;
            oldSky.discard();
        }
        space.inner = null;
        endQuietly(winner); // its opening in the inner space is over: its shell is in the world now
        DomainEntity shell = new DomainEntity(space.outerLevel, caster, winner.spec, space.outerCentre);
        shell.releaseIntent = winner.releaseIntent;
        shell.keyHeld = winner.keyHeld;
        space.outerLevel.addFreshEntity(shell);
        shell.space = space;
        InnerSpaces.rebuild(space, winner.spec, shell.centre(), shell.radius());
        shell.setState(DomainEntity.State.OPEN);
        shell.markClosed(0);
        shell.trapped.addAll(held);
        shell.trapped.add(caster.getUUID());
        space.inner = DomainEntity.innerOf(shell, space);
        space.level.addFreshEntity(space.inner);
        for (UUID id : held) {
            if (space.level.getEntity(id) instanceof ServerPlayer p && p != caster) InsightHooks.caughtInDomain(p, winner.spec.ability());
        }
        moment(shell, DomainNet.Moment.CLOSED, shell.centre(), 0f);
        winner.spec.hooks().onOpen(shell.context());
        return shell;
    }

    /** Gone with no ending of its own: no Burnout, no hooks, nobody told (one opened in an inner space that the space outlived). */
    static void endQuietly(DomainEntity d) {
        if (d.ended) return;
        d.ended = true;
        d.trapped.clear();
        if (d.space != null) {
            InnerSpaces.collapse(d.space, 0);
            d.space = null;
        }
        d.discard();
    }

    /** Its inner side: nobody gets past its sky (anyone who does another way is put back). */
    private static void keepInside(DomainEntity d) {
        ServerLevel level = (ServerLevel) d.level();
        Vec3 c = d.centre();
        double r = d.radius();
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box(d, 6), Domains::constrained)) {
            double margin = DomainEntity.margin(e);
            if (DomainEntity.middle(e).distanceTo(c) > r - margin + 0.3) put(e, c, r - margin - 0.05);
        }
    }

    /** Whoever is on the wrong side of a closed barrier (a teleport, a swap that wasn't stopped) is put back on their own. */
    private static void keepSides(DomainEntity d) {
        ServerLevel level = (ServerLevel) d.level();
        Vec3 c = d.centre();
        double r = d.radius();
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box(d, 4), Domains::constrained)) {
            Vec3 m = DomainEntity.middle(e);
            double dist = m.distanceTo(c), margin = DomainEntity.margin(e);
            boolean inside = d.space == null && d.trapped.contains(e.getUUID()); // with an inner space, nobody's held in the world
            if (inside && dist > r - margin + 0.3) put(e, c, r - margin - 0.05);
            else if (!inside && dist < r + margin - 0.3) put(e, c, r + margin + 0.05);
        }
        if (d.space != null) return; // its inner side keeps them in (keepInside)
        // Trapped and somewhere else entirely (another level, or far off after a teleport): back inside.
        for (UUID id : List.copyOf(d.trapped)) {
            Entity e = level.getEntity(id);
            if (e instanceof LivingEntity living && constrained(living) && DomainEntity.middle(living).distanceTo(c) > r + 4) {
                put(living, c, r - DomainEntity.margin(living) - 0.05);
            }
        }
    }

    /** Moves an entity's middle onto the sphere of {@code dist} round {@code c}, along the line it's on, clear of blocks if it can. */
    private static void put(LivingEntity e, Vec3 c, double dist) {
        Vec3 m = DomainEntity.middle(e);
        Vec3 dir = m.subtract(c);
        dir = dir.lengthSqr() < 1.0e-8 ? new Vec3(1, 0, 0) : dir.normalize();
        Vec3 target = c.add(dir.scale(Math.max(0, dist)));
        double half = e.getBbHeight() / 2.0;
        double x = target.x, y = target.y - half, z = target.z;
        for (int up = 0; up < 6; up++) {
            AABB box = e.getBoundingBox().move(x - e.getX(), y + up * 0.5 - e.getY(), z - e.getZ());
            if (e.level().noCollision(e, box)) {
                y += up * 0.5;
                break;
            }
        }
        Vec3 v = e.getDeltaMovement();
        double out = v.dot(dir);
        e.setDeltaMovement(v.subtract(dir.scale(out)));
        if (e instanceof ServerPlayer p) {
            p.moveTo(x, y, z, p.getYRot(), p.getXRot()); // where the server has them now (and all a test's player takes)
            p.connection.teleport(x, y, z, p.getYRot(), p.getXRot()); // and their client too
        } else {
            e.teleportTo(x, y, z);
        }
        e.hurtMarked = true;
    }

    /** Living, not a spectator, not a boss (DESIGN §4: never trapped or moved): what a barrier holds to one side. */
    static boolean constrained(LivingEntity e) {
        return e.isAlive() && !e.isSpectator() && !CombatHelper.isBoss(e);
    }

    private static AABB box(DomainEntity d, double extra) {
        double r = d.radius() + extra;
        Vec3 c = d.centre();
        return new AABB(c.x - r, c.y - r, c.z - r, c.x + r, c.y + r, c.z + r);
    }

    private static void serverTick() {
        if (DomainEntity.allServer().isEmpty()) return;
        for (DomainEntity d : List.copyOf(DomainEntity.allServer())) {
            if (d.ended || d.isRemoved() || !d.isClosed()) continue;
            stopProjectiles(d);
            swingsAtTheShell(d);
        }
    }

    // ---------------------------------------------------------------------------------------------- ending

    /** Ends it now: everyone inside is released and its caster burns out (a Domain Expansion for domainBurnout, an Incomplete one less). */
    public static void end(DomainEntity d, EndReason reason) {
        if (d.isInner() && d.anchor != null) {
            end(d.anchor, reason);
            return;
        }
        if (d.ended) return;
        d.ended = true;
        ServerPlayer caster = caster(d);
        if (d.spec != null && caster != null) d.spec.hooks().onClose(d.context(), reason.broken());
        END_LISTENERS.forEach(l -> l.accept(d, reason));
        if (caster != null && d.spec != null && reason != EndReason.CLASH_TIE && reason != EndReason.SERVER_STOP && caster.isAlive()) {
            Burnout.apply(caster, d.spec.burnoutTicks());
        }
        moment(d, momentFor(reason), d.centre(), 0f);
        letOut(d, reason == EndReason.SERVER_STOP);
        d.trapped.clear();
        d.discard();
    }

    /**
     * How everyone sees it end: broken (its cracks racing over it, the white shatter, its breaking sound) when it's broken, lost in a
     * clash, or held as long as its caster could hold it; going out quietly when it's ended any other way (a command, its caster gone).
     */
    static int momentFor(EndReason reason) {
        return reason.broken() || reason == EndReason.EXPIRED ? DomainNet.Moment.BROKE : DomainNet.Moment.ENDED;
    }

    /** Its inner space ends: everyone in it sees it go, then they're let out (at once as the server stops). */
    private static void letOut(DomainEntity d, boolean now) {
        if (d.space == null) return;
        InnerSpaces.collapse(d.space, now ? 0 : DomainConfig.ticks(DomainConfig.RETURN_DELAY));
        d.space = null;
    }

    /** The caster's barrier, if they have one open, opening or clashing. */
    public static Optional<DomainEntity> ownedBy(ServerPlayer caster) {
        for (DomainEntity d : DomainEntity.allServer()) {
            if (!d.ended && !d.isRemoved() && !d.isInner() && caster.getUUID().equals(d.caster)) return Optional.of(d);
        }
        return Optional.empty();
    }

    /** The closed barrier this entity is trapped in, if any. */
    public static Optional<DomainEntity> trapping(LivingEntity e) {
        for (DomainEntity d : DomainEntity.allServer()) {
            if (!d.ended && !d.isInner() && d.isClosed() && d.trapped.contains(e.getUUID())) return Optional.of(d);
        }
        return Optional.empty();
    }

    /** The Domain holding this player in (sealed in its inner space, or contested in a clash), if any. */
    public static Optional<DomainEntity> holding(Entity e) {
        for (DomainEntity d : DomainEntity.allServer()) {
            if (!d.ended && !d.isInner() && d.closedAt() >= 0 && d.trapped.contains(e.getUUID())) return Optional.of(d);
        }
        return Optional.empty();
    }

    /** Its caster, wherever they are (in its inner space, or still in the world). */
    @Nullable
    public static ServerPlayer caster(DomainEntity d) {
        if (d.caster == null || d.getServer() == null) return null;
        return com.cnethuka.kegare.progression.Attribution.player(d.getServer(), d.caster);
    }

    /** Everything living it holds right now, in its inner space (the caster included). */
    public static List<LivingEntity> inside(DomainEntity d) {
        DomainEntity p = d.primary();
        List<LivingEntity> out = new ArrayList<>();
        if (!p.isClosed()) return out;
        ServerLevel level = p.space != null ? p.space.level : (ServerLevel) p.level();
        for (UUID id : p.trapped) {
            if (level.getEntity(id) instanceof LivingEntity e && e.isAlive()) out.add(e);
        }
        return out;
    }

    /**
     * Who a sure-hit reaches (DESIGN §3.35): everything trapped inside but the caster and what the caster owns; never a Severed player,
     * who has no Malice for it to find. Nothing while it isn't closed, or for a Domain with no sure-hit.
     */
    public static List<LivingEntity> sureHitTargets(DomainEntity d) {
        if (d.spec == null || !d.spec.sureHit() || !d.isClosed()) return List.of();
        ServerPlayer caster = caster(d);
        List<LivingEntity> out = new ArrayList<>();
        for (LivingEntity e : inside(d)) {
            if (e == caster) continue;
            if (e instanceof Player p && MaliceService.isSevered(p)) continue;
            if (caster != null && com.cnethuka.kegare.progression.Attribution.isOwn(caster, e)) continue;
            out.add(e);
        }
        return out;
    }

    /** A closed barrier with one of these on each side of it, if any. */
    public static Optional<DomainEntity> between(Entity a, Entity b) {
        if (a.level() != b.level()) return Optional.empty();
        return between(a.level(), DomainEntity.middle(a), DomainEntity.middle(b));
    }

    static Optional<DomainEntity> between(net.minecraft.world.level.Level level, Vec3 a, Vec3 b) {
        for (DomainEntity d : DomainEntity.in(level)) {
            if (!d.ended && d.isClosed() && d.contains(a) != d.contains(b)) return Optional.of(d);
        }
        return Optional.empty();
    }

    private static boolean separated(Entity a, Entity b) {
        return between(a, b).isPresent();
    }

    private static void onDeath(LivingEntity dead) {
        if (dead instanceof ServerPlayer p) onGone(p, false);
        // A dead body is trapped no longer (a player respawns elsewhere under the same id).
        for (DomainEntity d : DomainEntity.allServer()) d.trapped.remove(dead.getUUID());
    }

    /** A player died, logged out or changed dimension: their own Domain ends, and they're trapped in none. */
    private static void onGone(ServerPlayer p, boolean burnsOut) {
        if (InnerSpaces.MOVING.contains(p.getUUID())) return; // taken in or let out: that's no leaving
        for (DomainEntity d : List.copyOf(DomainEntity.allServer())) {
            if (d.isInner()) continue;
            d.trapped.remove(p.getUUID());
            if (p.getUUID().equals(d.caster) && !d.ended) {
                if (burnsOut && d.spec != null) Burnout.apply(p, d.spec.burnoutTicks());
                endWithoutBurnout(d);
            }
        }
        LAST_SWING.remove(p.getUUID());
    }

    /** Ends it, its caster's Burnout already dealt with (they're gone). */
    private static void endWithoutBurnout(DomainEntity d) {
        if (d.ended) return;
        d.ended = true;
        ServerPlayer caster = caster(d);
        if (d.spec != null && caster != null) d.spec.hooks().onClose(d.context(), false);
        END_LISTENERS.forEach(l -> l.accept(d, EndReason.CASTER_GONE));
        moment(d, DomainNet.Moment.ENDED, d.centre(), 0f);
        letOut(d, false);
        d.trapped.clear();
        d.discard();
    }

    // ---------------------------------------------------------------------------------------------- the barrier taking hits

    /** A hit on the barrier of this strength (the Impact System's), at this point on it, by {@code by} (its caster's own never count). */
    public static void hit(DomainEntity d, double strength, Vec3 at, @Nullable Entity by) {
        DomainEntity p = d.primary(); // its shell in the world holds its strength, whichever side was hit
        if (p.ended || d.ended || strength <= 0) return;
        if (by != null && by.getUUID().equals(p.caster)) return;
        p.barrierLeft -= strength;
        double whole = p.spec == null ? 1 : p.spec.barrierStrength();
        p.setBarrierShare((float) (p.barrierLeft / whole));
        moment(d, DomainNet.Moment.HIT, at, (float) (strength / whole)); // the share it took: how far its cracks spread
        if (p.barrierLeft <= 0) end(p, EndReason.BROKEN);
    }

    /** Takes a share of its strength off directly (a clash's missed prompt cracks it), heard as a hit at its top. Run out, it breaks. */
    public static void crack(DomainEntity d, double share) {
        DomainEntity p = d.primary();
        if (p.ended || p.spec == null || share <= 0) return;
        p.barrierLeft -= share * p.spec.barrierStrength();
        p.setBarrierShare((float) (p.barrierLeft / p.spec.barrierStrength()));
        moment(d, DomainNet.Moment.HIT, d.centre().add(0, d.radius(), 0), (float) share);
        if (p.barrierLeft <= 1.0e-9) end(p, EndReason.BROKEN);
    }

    /** A hit across a closed barrier lands on it instead (and so does any harm done from one side to the other). */
    private static void onIncoming(LivingIncomingDamageEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide || DomainEntity.in(victim.level()).isEmpty()) return;
        DamageSource source = event.getSource();
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return;
        Entity from = source.getEntity() != null ? source.getEntity() : source.getDirectEntity();
        Vec3 fromAt = from != null ? DomainEntity.middle(from) : source.getSourcePosition();
        if (fromAt == null) return;
        Optional<DomainEntity> barrier = between(victim.level(), fromAt, DomainEntity.middle(victim));
        if (barrier.isEmpty()) return;
        event.setCanceled(true);
        DomainEntity d = barrier.get();
        Vec3 at = shellPoint(d, fromAt, DomainEntity.middle(victim));
        hit(d, ImpactStrength.of(event.getAmount() / 20.0, 0.4, 0, 0, 0), at, from);
    }

    /** A projectile stops where its path meets a closed barrier: it's gone, and the barrier takes the hit. */
    static void onProjectileImpact(ProjectileImpactEvent event) {
        Projectile p = event.getProjectile();
        if (p.level().isClientSide || DomainEntity.in(p.level()).isEmpty()) return;
        HitResult hit = event.getRayTraceResult();
        Vec3 from = new Vec3(p.xo, p.yo, p.zo), to = hit.getLocation();
        for (DomainEntity d : DomainEntity.in(p.level())) {
            if (!d.ended && d.isClosed() && d.crosses(from, to)) {
                event.setCanceled(true);
                stop(p, d, from, to);
                return;
            }
        }
    }

    /** Projectiles that crossed a barrier this tick without hitting anything. */
    static void stopProjectiles(DomainEntity d) {
        for (Projectile p : d.level().getEntitiesOfClass(Projectile.class, box(d, 8), p -> !p.isRemoved())) {
            Vec3 from = new Vec3(p.xo, p.yo, p.zo), to = p.position();
            if (d.crosses(from, to)) stop(p, d, from, to);
        }
    }

    private static void stop(Projectile p, DomainEntity d, Vec3 from, Vec3 to) {
        double strength = DomainConfig.PROJECTILE_STRENGTH.get();
        if (p instanceof AbstractArrow arrow) {
            strength = Math.max(strength, ImpactStrength.of(arrow.getBaseDamage() * p.getDeltaMovement().length() / 20.0, 0.4, 0, 0, 0));
        }
        Vec3 at = shellPoint(d, from, to);
        p.discard();
        hit(d, strength, at, p.getOwner());
    }

    /** An area blast near the shell, inside or out, hits it: fully at the shell, less further off. */
    private static void onBlast(Impacts.Blast blast) {
        for (DomainEntity d : DomainEntity.in(blast.level())) {
            if (d.ended || !d.isClosed()) continue;
            double off = Math.abs(blast.centre().distanceTo(d.centre()) - d.radius());
            double reach = DomainConfig.BLAST_REACH.get();
            if (off > reach) continue;
            double share = reach <= 0 ? 1 : 1 - off / reach;
            Vec3 dir = blast.centre().subtract(d.centre());
            Vec3 at = d.centre().add(dir.lengthSqr() < 1.0e-8 ? new Vec3(0, d.radius(), 0) : dir.normalize().scale(d.radius()));
            hit(d, blast.strength() * share, at, blast.attacker());
        }
    }

    /**
     * Swings at the shell: a player who starts a swing with the shell within reach along their look, and no block or body nearer, hits it
     * with their weapon (Reinforced Fists and Flow's Offense count). The charge is their own for the barrier: an air swing doesn't reset
     * vanilla's attack timer on the server.
     */
    static void swingsAtTheShell(DomainEntity d) {
        ServerLevel level = (ServerLevel) d.level();
        double reach = DomainConfig.MELEE_REACH.get();
        long now = level.getGameTime();
        for (ServerPlayer p : level.players()) {
            if (!p.swinging || p.swingTime > 0 || p.isSpectator() || !p.isAlive()) continue;
            if (Math.abs(p.getEyePosition().distanceTo(d.centre()) - d.radius()) > reach + 1) continue;
            Long last = LAST_SWING.get(p.getUUID());
            if (last != null && now - last < 3) continue; // one swing, seen over two ticks
            Vec3 eye = p.getEyePosition(), look = p.getLookAngle();
            double t = shellAlong(d, eye, look);
            if (t < 0 || t > reach) continue;
            Vec3 at = eye.add(look.scale(t));
            if (level.clip(new ClipContext(eye, at, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p)).getType() == HitResult.Type.BLOCK) continue;
            EntityHitResult body = ProjectileUtil.getEntityHitResult(level, p, eye, at, new AABB(eye, at).inflate(1), e -> e != p && !e.isSpectator()
                    && e.isPickable());
            if (body != null) continue;
            int delay = Math.max(1, Math.round(p.getCurrentItemAttackStrengthDelay()));
            float charge = last == null ? 1f : Math.min(1f, (now - last) / (float) delay);
            LAST_SWING.put(p.getUUID(), now);
            double damage = p.getAttributeValue(Attributes.ATTACK_DAMAGE) * (0.2 + charge * charge * 0.8);
            if (com.cnethuka.kegare.fists.ReinforcedFists.isOn(p)) damage *= 1 + com.cnethuka.kegare.fists.FistsEvents.bonus(p);
            damage *= 1 + FlowConfig.OFFENSE.get() * Flow.strength(p);
            hit(d, ImpactStrength.of(damage / 20.0, 0.4, 0, 0, 0), at, p);
        }
    }

    /** Along a ray from {@code from}: how far to where it meets the shell (going out if it starts inside), or -1 if it never does. */
    static double shellAlong(DomainEntity d, Vec3 from, Vec3 dir) {
        Vec3 oc = from.subtract(d.centre());
        double b = oc.dot(dir), c = oc.lengthSqr() - d.radius() * d.radius();
        double disc = b * b - c;
        if (disc < 0) return -1;
        double s = Math.sqrt(disc);
        double t1 = -b - s, t2 = -b + s;
        if (c < 0) return t2; // inside: where it goes out
        return t1 >= 0 ? t1 : -1;
    }

    /** Where a segment from {@code a} to {@code b} meets the shell (its middle if it doesn't). */
    static Vec3 shellPoint(DomainEntity d, Vec3 a, Vec3 b) {
        Vec3 dir = b.subtract(a);
        double len = dir.length();
        if (len < 1.0e-6) return a;
        dir = dir.scale(1 / len);
        double t = shellAlong(d, a, dir);
        return t < 0 || t > len ? a.add(b).scale(0.5) : a.add(dir.scale(t));
    }

    // ---------------------------------------------------------------------------------------------- telling the clients

    /** A moment of this barrier's, to every player near enough to see it. */
    static void moment(DomainEntity view, int kind, Vec3 at, float strength) {
        DomainEntity shell = view.primary();
        Vec3 dir = at.subtract(view.centre());
        send(shell, kind, view == shell ? at : onSide(shell, dir), strength);
        DomainEntity sky = shell.space != null ? shell.space.inner : null;
        if (sky != null && !sky.isRemoved()) send(sky, kind, view == sky ? at : onSide(sky, dir), strength);
    }

    /** The same place on another side of the barrier: its centre, or the point on it in the same direction. */
    private static Vec3 onSide(DomainEntity side, Vec3 dir) {
        return dir.lengthSqr() < 1.0e-8 ? side.centre() : side.centre().add(dir.normalize().scale(side.radius()));
    }

    /** A moment to every player near one side of the barrier. */
    static void send(DomainEntity d, int kind, Vec3 at, float strength) {
        ServerLevel level = (ServerLevel) d.level();
        DomainNet.Moment payload = new DomainNet.Moment(kind, d.getId(), at.x, at.y, at.z, (float) d.radius(), strength, d.complete(),
                d.look().cracked(), d.look().shellColor(), d.abilityId().toString());
        double range = d.radius() + 96;
        for (ServerPlayer p : level.players()) {
            if (p.position().distanceToSqr(d.centre()) < range * range) PacketDistributor.sendToPlayer(p, payload);
        }
    }

    /** Test and command hook: forgets every barrier as a crash would, ending nothing and applying nothing. */
    public static void forgetAllAsIfCrashed() {
        for (DomainEntity d : List.copyOf(DomainEntity.allServer())) {
            d.ended = true;
            d.space = null;
            d.discard();
        }
        InnerSpaces.forgetAll();
        LAST_SWING.clear();
    }

    /** Whether this player is holding the key that opened their Domain (a clash needs it held). */
    public static boolean keyHeld(DomainEntity d) {
        return d.keyHeld;
    }

    /** A key went up: if it's the one that opened this player's Domain, they've let go of it. */
    public static void keyReleased(ServerPlayer player, CastIntent intent) {
        ownedBy(player).ifPresent(d -> {
            if (d.releaseIntent == intent) d.keyHeld = false;
        });
    }
}

package com.cnethuka.kegare.dominion;

import com.cnethuka.kegare.Kegare;
import java.util.BitSet;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Dominions' inner spaces (DESIGN §3.35): one dimension ({@code kegare:inner_space}) holds them all, each in its own plot. A plot is taken
 * as a Dominion opens and built while its barrier spreads; as it seals, everything alive inside is taken in; as it ends, everyone and
 * everything in it is let out again. The world under the shell is never touched. Nothing here is saved but where each player came from
 * ({@code kegare:inner_return}), so a player who logs in inside an inner space that's no longer open is simply sent back.
 */
public final class InnerSpaces {
    public static final ResourceKey<Level> LEVEL = ResourceKey.create(Registries.DIMENSION, Kegare.id("inner_space"));
    /** Plots are this far apart (and an inner space reaches at most 96). */
    public static final int SPACING = 1024;
    /** Where an inner space's floor is stood on. */
    static final int FLOOR_Y = 64;
    /** Holds a plot's chunks, and its shell's in the world, loaded while it's open (a ticket is never saved). */
    static final TicketType<ChunkPos> TICKET = TicketType.create("kegare_inner_space", Comparator.comparingLong(ChunkPos::toLong));
    private static final BitSet USED = new BitSet();
    private static final List<InnerSpace> OPEN = new CopyOnWriteArrayList<>();
    /** Being moved by Kegare right now: a change of dimension that isn't theirs (so it ends no Dominion). */
    static final Set<UUID> MOVING = ConcurrentHashMap.newKeySet();

    private record Collapse(InnerSpace space, long at) {}

    private static final List<Collapse> COLLAPSES = new CopyOnWriteArrayList<>();

    private InnerSpaces() {}

    public static void init() {
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post e) -> tick());
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer p) onLogin(p);
        });
        // Nothing is built in someone's inner space: it's gone when it ends, and whatever was placed with it.
        NeoForge.EVENT_BUS.addListener((BlockEvent.EntityPlaceEvent e) -> {
            if (e.getLevel() instanceof Level level && isInnerSpace(level)) e.setCanceled(true);
        });
        // Nor taken out of it: what it's built of drops nothing when broken, by hand or by a blast (or it would be let out with everyone).
        NeoForge.EVENT_BUS.addListener((BlockDropsEvent e) -> {
            if (isInnerSpace(e.getLevel())) e.setCanceled(true);
        });
        NeoForge.EVENT_BUS.addListener((ExplosionEvent.Detonate e) -> {
            if (!isInnerSpace(e.getLevel())) return;
            for (BlockPos pos : e.getAffectedBlocks()) {
                if (!e.getLevel().getBlockState(pos).isAir()) e.getLevel().removeBlock(pos, false);
            }
            e.getAffectedBlocks().clear();
        });
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent e) -> forgetAll());
    }

    @Nullable
    public static ServerLevel level(MinecraftServer server) {
        return server.getLevel(LEVEL);
    }

    public static boolean isInnerSpace(Level level) {
        return level.dimension() == LEVEL;
    }

    // ---------------------------------------------------------------------------------------------- a plot

    /** A plot for this Dominion, its building begun, or null with no inner space dimension (a broken datapack). */
    @Nullable
    static InnerSpace take(DominionEntity d, DominionSpec spec) {
        ServerLevel pocket = d.getServer() == null ? null : level(d.getServer());
        if (pocket == null) {
            Kegare.LOGGER.warn("No inner space dimension ({}): Dominions can't seal", LEVEL.location());
            return null;
        }
        int slot = USED.nextClearBit(0);
        USED.set(slot);
        BlockPos centre = new BlockPos(slot * SPACING + SPACING / 2, FLOOR_Y, SPACING / 2);
        InnerSpace s = new InnerSpace(slot, pocket, centre, spec.innerRadius(), (ServerLevel) d.level(), d.centre(), d.radius());
        s.build(spec);
        hold(s);
        OPEN.add(s);
        return s;
    }

    private static int heldReach(InnerSpace s) {
        return ((s.radius + InnerSpace.FLOOR_MARGIN) >> 4) + 2;
    }

    /** Its chunks, and its shell's in the world, held loaded. */
    static void hold(InnerSpace s) {
        s.heldReach = heldReach(s);
        ChunkPos pocket = new ChunkPos(s.centre), outer = new ChunkPos(BlockPos.containing(s.outerCentre));
        s.level.getChunkSource().addRegionTicket(TICKET, pocket, s.heldReach, pocket);
        s.outerLevel.getChunkSource().addRegionTicket(TICKET, outer, 2, outer);
        s.heldOuter = outer;
    }

    static void letGo(InnerSpace s) {
        ChunkPos pocket = new ChunkPos(s.centre);
        if (s.heldReach > 0) s.level.getChunkSource().removeRegionTicket(TICKET, pocket, s.heldReach, pocket);
        if (s.heldOuter != null) s.outerLevel.getChunkSource().removeRegionTicket(TICKET, s.heldOuter, 2, s.heldOuter);
        s.heldReach = 0;
        s.heldOuter = null;
    }

    /** Rebuilds it, now, for another Dominion (one that won it in a clash), round everyone still in it. */
    static void rebuild(InnerSpace s, DominionSpec spec, Vec3 outerCentre, double outerRadius) {
        letGo(s);
        s.outerCentre = outerCentre;
        s.outerRadius = outerRadius;
        s.build(spec);
        hold(s);
        s.finishBuilding();
        for (Entity e : s.level.getEntitiesOfClass(Entity.class, box(s), e -> !(e instanceof DominionEntity))) liftClear(e);
    }

    private static AABB box(InnerSpace s) {
        Vec3 m = s.middle();
        double r = s.radius + InnerSpace.FLOOR_MARGIN + 6;
        return new AABB(m.x - r, m.y - 8, m.z - r, m.x + r, m.y + r + 8, m.z + r);
    }

    // ---------------------------------------------------------------------------------------------- taking in, letting out

    /**
     * Everything alive inside the shell as it seals goes into the inner space: at the same place relative to the caster, spread out by how
     * much bigger the inside is, on its floor (and lifted clear of a ruin). Players keep where they came from; the space keeps its mobs'.
     */
    static void takeIn(InnerSpace s, Vec3 shellCentre, List<LivingEntity> who) {
        double k = s.spread();
        Vec3 m = s.middle();
        for (LivingEntity e : who) {
            Vec3 off = e.position().subtract(shellCentre);
            double hx = off.x * k, hz = off.z * k, len = Math.sqrt(hx * hx + hz * hz), max = s.radius - 1.5;
            if (len > max) {
                hx *= max / len;
                hz *= max / len;
            }
            Vec3 to = new Vec3(m.x + hx, m.y + Mth.clamp(off.y, 0, s.radius * 0.5), m.z + hz);
            clearStanding(s, e, to); // nothing stands where someone lands
            if (e instanceof ServerPlayer p) {
                p.setData(DominionRegistry.INNER_RETURN, new InnerReturn(Optional.of(p.level().dimension()), p.position(), p.getYRot(), p.getXRot()));
            } else {
                s.mobOrigins.put(e.getUUID(), e.position());
            }
            Entity moved = move(e, s.level, to, e.getYRot(), e.getXRot());
            if (moved != null) liftClear(moved);
        }
    }

    /** Whatever stands on the floor where an entity will land (a ruin, rubble, a bush), gone right up, so nothing's left hanging over them. */
    private static void clearStanding(InnerSpace s, Entity e, Vec3 at) {
        AABB box = e.getDimensions(e.getPose()).makeBoundingBox(at);
        int floor = s.centre.getY(), top = Math.max(floor + STANDS_UP_TO, BlockPos.containing(0, box.maxY, 0).getY());
        for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(box.minX, floor, box.minZ), BlockPos.containing(box.maxX, top, box.maxZ))) {
            if (!s.level.getBlockState(pos).isAir()) s.place(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
        }
    }

    /** No ruin stands taller than this. */
    private static final int STANDS_UP_TO = 8;

    /** It's over: everyone and everything in it goes back to the world {@code delayTicks} from now (at once for 0), and the plot goes. */
    static void collapse(InnerSpace s, int delayTicks) {
        if (s.collapsing) return;
        s.collapsing = true;
        if (s.inner != null) {
            s.inner.ended = true;
            s.inner.discard();
            s.inner = null;
        }
        if (delayTicks <= 0) letOut(s);
        else COLLAPSES.add(new Collapse(s, s.level.getGameTime() + delayTicks));
    }

    /**
     * Everyone and everything in it back to the world: a player to the spot they were taken from, a mob it took in to where it was, and
     * anything else (an item, experience, a mob that was made inside) to its place inside, drawn back in to the shell's size.
     */
    static void letOut(InnerSpace s) {
        MinecraftServer server = s.level.getServer();
        for (Entity e : s.level.getEntitiesOfClass(Entity.class, box(s), e -> !(e instanceof DominionEntity) && !e.isPassenger())) {
            if (e.isRemoved()) continue;
            if (e instanceof ServerPlayer p) {
                sendBack(p, server);
                continue;
            }
            Vec3 origin = s.mobOrigins.get(e.getUUID());
            Entity moved = move(e, s.outerLevel, origin != null ? origin : drawnBack(s, e.position()), e.getYRot(), e.getXRot());
            if (moved != null && origin == null) liftClear(moved);
        }
        release(s);
    }

    /** A player back where they came from: the spot they were taken from, or the world's spawn if that's lost. */
    static void sendBack(ServerPlayer p, MinecraftServer server) {
        InnerReturn back = p.getData(DominionRegistry.INNER_RETURN);
        ServerLevel level = back.level().map(server::getLevel).orElse(null);
        if (level != null) {
            move(p, level, back.pos(), back.yRot(), back.xRot());
        } else {
            ServerLevel overworld = server.overworld();
            move(p, overworld, Vec3.atBottomCenterOf(overworld.getSharedSpawnPos()), p.getYRot(), p.getXRot());
            liftClear(p);
        }
        p.removeData(DominionRegistry.INNER_RETURN);
    }

    private static Vec3 drawnBack(InnerSpace s, Vec3 pos) {
        Vec3 off = pos.subtract(s.middle());
        double k = s.spread();
        return s.outerCentre.add(off.x / k, Math.max(0, off.y), off.z / k);
    }

    private static void release(InnerSpace s) {
        letGo(s);
        OPEN.remove(s);
        USED.clear(s.slot);
    }

    private static void tick() {
        for (Collapse c : COLLAPSES) {
            if (c.space().level.getGameTime() >= c.at()) {
                COLLAPSES.remove(c);
                letOut(c.space());
            }
        }
        if (++strayCheck >= 20) {
            strayCheck = 0;
            sendStraysBack();
        }
    }

    private static int strayCheck;

    /**
     * Once a second: a player in the inner space dimension that no Dominion holds and no plot is letting out (put there by a command, say)
     * is sent back where they came from.
     */
    static void sendStraysBack() {
        MinecraftServer server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        ServerLevel level = server == null ? null : level(server);
        if (level != null && !level.players().isEmpty()) strays(level);
    }

    private static void strays(ServerLevel level) {
        for (ServerPlayer p : List.copyOf(level.players())) {
            if (Dominions.holding(p).isPresent()) continue;
            boolean beingLetOut = false;
            for (InnerSpace s : OPEN) if (s.collapsing && s.level == level && box(s).contains(p.position())) beingLetOut = true;
            if (!beingLetOut) sendBack(p, level.getServer());
        }
    }

    /**
     * Moves an entity to a level and a spot, as Kegare (no Dominion ends for it). A player is moved as themselves; anything else crosses
     * levels as a copy with the same identity. Returns what's there now (null if it couldn't go).
     */
    @Nullable
    static Entity move(Entity e, ServerLevel to, Vec3 pos, float yRot, float xRot) {
        MOVING.add(e.getUUID());
        try {
            e.stopRiding();
            e.ejectPassengers();
            if (e instanceof net.neoforged.neoforge.common.util.FakePlayer p) {
                // A player with no client (another mod's, or a test's) has no one to tell about a new level: it's moved as an entity of it.
                if (p.level() != to) {
                    p.serverLevel().removePlayerImmediately(p, Entity.RemovalReason.CHANGED_DIMENSION);
                    unsetRemoved(p);
                    p.setServerLevel(to);
                    p.moveTo(pos.x, pos.y, pos.z, yRot, xRot);
                    to.addDuringTeleport(p);
                } else {
                    p.moveTo(pos.x, pos.y, pos.z, yRot, xRot);
                }
                p.setDeltaMovement(Vec3.ZERO);
                p.fallDistance = 0;
                return p;
            }
            if (e instanceof ServerPlayer p) {
                p.teleportTo(to, pos.x, pos.y, pos.z, yRot, xRot);
                // A test's player ignores the half of a teleport its client would do.
                if (p.level() == to && p.position().distanceToSqr(pos) > 1.0e-4) p.moveTo(pos.x, pos.y, pos.z, yRot, xRot);
                p.setDeltaMovement(Vec3.ZERO);
                p.fallDistance = 0;
                return p;
            }
            if (e.level() == to) {
                e.teleportTo(pos.x, pos.y, pos.z);
                e.setDeltaMovement(Vec3.ZERO);
                return e;
            }
            Entity moved = e.changeDimension(new DimensionTransition(to, pos, Vec3.ZERO, yRot, xRot, DimensionTransition.DO_NOTHING));
            if (moved != null) moved.fallDistance = 0;
            return moved;
        } finally {
            MOVING.remove(e.getUUID());
        }
    }

    private static java.lang.reflect.Method unsetRemoved;

    /** As a dimension change does: the entity left one level and is about to be added to another, so it isn't removed after all. */
    private static void unsetRemoved(Entity e) {
        try {
            if (unsetRemoved == null) {
                unsetRemoved = Entity.class.getDeclaredMethod("unsetRemoved");
                unsetRemoved.setAccessible(true);
            }
            unsetRemoved.invoke(e);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("couldn't move " + e + " to another level", ex);
        }
    }

    /** Up out of anything solid it's in (a ruin, a wall), a block at a time, eight at most. */
    static void liftClear(Entity e) {
        for (int i = 0; i < 8 && !e.level().noCollision(e); i++) {
            if (e instanceof ServerPlayer p) {
                p.moveTo(p.getX(), p.getY() + 1, p.getZ(), p.getYRot(), p.getXRot());
                p.connection.teleport(p.getX(), p.getY(), p.getZ(), p.getYRot(), p.getXRot());
            } else {
                e.setPos(e.getX(), e.getY() + 1, e.getZ());
            }
        }
    }

    /** Logged in inside an inner space no Dominion holds them in (a crash, a stop, logging out inside): back where they came from. */
    static void onLogin(ServerPlayer p) {
        if (isInnerSpace(p.level()) && Dominions.holding(p).isEmpty()) sendBack(p, p.server);
        else if (!isInnerSpace(p.level())) p.removeData(DominionRegistry.INNER_RETURN);
    }

    /** Every plot open now (tests). */
    static List<InnerSpace> open() {
        return List.copyOf(OPEN);
    }

    /** Forgets every plot, letting nobody out (a stop, or a test's crash). */
    static void forgetAll() {
        OPEN.clear();
        USED.clear();
        COLLAPSES.clear();
    }
}

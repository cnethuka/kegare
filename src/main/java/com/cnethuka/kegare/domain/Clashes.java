package com.cnethuka.kegare.domain;

import com.cnethuka.kegare.innatetechnique.Burnout;
import com.cnethuka.kegare.innatetechnique.CastIntent;
import com.cnethuka.kegare.innatetechnique.CastManager;
import com.cnethuka.kegare.loadout.LoadoutService;
import com.cnethuka.kegare.malice.MaliceService;
import com.cnethuka.kegare.progression.Attribution;
import com.cnethuka.kegare.progression.FightTracker;
import com.cnethuka.kegare.progression.InsightHooks;
import com.cnethuka.kegare.sixeyes.Edge;
import com.cnethuka.kegare.timing.SubTick;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.DoubleSupplier;
import javax.annotation.Nullable;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Domain clashes (DESIGN §3.35): a Domain opening over one that's opening or open. Two sides at most; a third is refused and pays
 * nothing. Both barriers go half-formed (trapping nobody, no sure-hit) and their time stops while it lasts.
 * <ol>
 *   <li><b>Grip</b> ({@code gripSeconds}): both casters have to be holding the key that opens their Domain (the one whose Domain was
 *   already open presses it again).</li>
 *   <li><b>Maintain</b> ({@code maintainSeconds}): held in place, unable to attack or cast, each pays {@code drainPerSecond} Malice and
 *   presses as a sweeping line crosses their mark, judged by sub-tick timing. A hit pushes the bar toward the other (less for an Incomplete Domain against a
 *   full one); a miss pushes it back and cracks their own barrier. Pushed all the way, letting go, running dry or cracking apart loses.</li>
 *   <li><b>Fight</b> ({@code fightSeconds}): free again. The damage each deals the other (after defences) is their score; the higher
 *   wins, and within {@code tieMargin} it's a tie.</li>
 * </ol>
 * The loser's barrier breaks and they burn out as usual; the winner's closes, with the time it had used kept. A tie breaks both, and both
 * burn out for {@code exhaustedBurnout}.
 */
public final class Clashes {
    /** Sides a clash can have. More would need the bar rethought, and nothing else. */
    public static final int MAX_SIDES = 2;
    private static final List<Clash> ACTIVE = new CopyOnWriteArrayList<>();
    /** The clock the sweeping line runs on: {@link SubTick#nowMs} (tests set their own). */
    static DoubleSupplier clock = SubTick::nowMs;
    /** Told when a clash is decided: its winner (null for a tie) and how. */
    private static final List<Decided> DECIDED = new CopyOnWriteArrayList<>();

    @FunctionalInterface
    public interface Decided {
        void decided(Clash clash, @Nullable Clash.Side winner, Clash.Result result);
    }

    private Clashes() {}

    public static void init() {
        Domains.addOpenRule(Clashes::refuse);
        Domains.addOverlapListener((d, overlapping) -> start(overlapping.get(0), d));
        Domains.addEndListener(Clashes::onEnded);
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post e) -> {
            for (Clash c : ACTIVE) tick(c);
        });
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, Clashes::onIncoming);
        NeoForge.EVENT_BUS.addListener(Clashes::onAttack);
        NeoForge.EVENT_BUS.addListener((ServerStoppingEvent e) -> ACTIVE.clear());
        CastManager.addInterceptor(Clashes::intercept);
        FightTracker.addDamageListener(Clashes::scored);
        // Clashing, each is top-tier to the other: Six Eyes on either side sees the other's casts coming (§3.32's uncovered edge).
        Edge.addTopTier(Clashes::opponents);
        addDecidedListener(DomainAdvancements::decided);
    }

    public static void addDecidedListener(Decided listener) {
        DECIDED.add(listener);
    }

    // ---------------------------------------------------------------------------------------------- starting

    /** The open rule: one Domain in the way, and not already clashing. Anything else is refused, and nothing is paid. */
    static Optional<Component> refuse(ServerPlayer caster, DomainSpec spec, List<DomainEntity> overlapping) {
        for (DomainEntity other : overlapping) {
            if (of(other) != null || other.state() == DomainEntity.State.CLASH) {
                return Optional.of(Component.translatable("message.kegare.domain.clash.full"));
            }
        }
        if (overlapping.size() >= MAX_SIDES) return Optional.of(Component.translatable("message.kegare.domain.clash.crowded"));
        return Optional.empty();
    }

    /** {@code second} opened over {@code first}: they clash. */
    static Clash start(DomainEntity first, DomainEntity second) {
        Clash c = new Clash(side(first), side(second), RandomSource.create());
        ServerLevel level = (ServerLevel) second.level();
        for (Clash.Side s : c.sides) {
            DomainEntity d = s.domain, shell = d.primary();
            boolean heldInWorld = shell.isClosed() && shell.space == null;
            shell.setState(DomainEntity.State.CLASH); // its inner space's sky too
            d.setState(DomainEntity.State.CLASH);
            if (shell.space == null) shell.trapped.clear(); // in the world, nobody's held while it's contested; an inner space keeps everyone
            ServerPlayer caster = Domains.caster(d);
            d.holdAt = caster == null ? null : caster.position();
            if (heldInWorld) { // nobody is held in it now: every client near lets go of its side
                double range = d.radius() + 96;
                for (ServerPlayer p : level.players()) {
                    if (p.distanceToSqr(d) < range * range) PacketDistributor.sendToPlayer(p, new DomainNet.Trapped(d.getId(), false));
                }
            }
        }
        first.setClashWith(second.getId());
        second.setClashWith(first.getId());
        ServerPlayer a = Domains.caster(first), b = Domains.caster(second);
        DomainSpec firstSpec = first.primary().spec, secondSpec = second.primary().spec;
        if (a != null && secondSpec != null) InsightHooks.clashedWithDomain(a, secondSpec.ability());
        if (b != null && firstSpec != null) InsightHooks.clashedWithDomain(b, firstSpec.ability());
        // Heard and seen where they meet.
        Vec3 dir = second.centre().subtract(first.centre());
        double gap = dir.length();
        Vec3 meet = gap < 1.0e-6 ? first.centre() : first.centre().add(dir.scale((first.radius() + gap - second.radius()) / 2.0 / gap));
        Domains.moment(second, DomainNet.Moment.CLASH, meet, 0f);
        ACTIVE.add(c);
        sync(c);
        return c;
    }

    private static Clash.Side side(DomainEntity d) {
        DomainEntity shell = d.primary(); // its time is its shell's
        return new Clash.Side(d, shell.isClosed() ? Math.max(0, shell.tickCount - shell.closedAt()) : -1);
    }

    // ---------------------------------------------------------------------------------------------- running it

    static void tick(Clash c) {
        if (c.phase == Clash.Phase.OVER) return;
        c.phaseTicks++;
        switch (c.phase) {
            case GRIP -> {
                if (c.phaseTicks < DomainConfig.ticks(DomainConfig.CLASH_GRIP)) break;
                boolean first = c.first().held, second = c.second().held;
                if (!first && !second) tie(c);
                else if (!first) win(c, c.second(), Clash.Result.MAINTAIN);
                else if (!second) win(c, c.first(), Clash.Result.MAINTAIN);
                else {
                    c.phase = Clash.Phase.MAINTAIN;
                    c.phaseTicks = 0;
                    c.sweepStartMs = clock.getAsDouble();
                    for (Clash.Side s : c.sides) {
                        s.mark = newMark(c, s);
                        sendSweep(c, s, DomainNet.ClashSweep.NONE, 0f);
                    }
                }
            }
            case MAINTAIN -> {
                for (Clash.Side s : c.sides) {
                    ServerPlayer caster = caster(s);
                    if (caster == null || !s.held || !MaliceService.trySpend(caster, DomainConfig.CLASH_DRAIN.get() / 20.0)) {
                        win(c, c.other(s), Clash.Result.MAINTAIN);
                        return;
                    }
                }
                if (c.phaseTicks >= DomainConfig.ticks(DomainConfig.CLASH_MAINTAIN)) {
                    c.phase = Clash.Phase.FIGHT;
                    c.phaseTicks = 0;
                    for (Clash.Side s : c.sides) s.domain.holdAt = null;
                }
            }
            case FIGHT -> {
                if (c.phaseTicks < DomainConfig.ticks(DomainConfig.CLASH_FIGHT)) break;
                double a = c.first().score, b = c.second().score;
                if (Math.abs(a - b) <= DomainConfig.CLASH_TIE_MARGIN.get() * Math.max(a, b)) tie(c);
                else win(c, a > b ? c.first() : c.second(), Clash.Result.FIGHT);
            }
            case OVER -> {}
        }
        if (c.phase != Clash.Phase.OVER) sync(c);
    }

    /** How long the line takes to cross the meter once, in ms. */
    static double sweepMs() {
        return DomainConfig.CLASH_SWEEP.get() * 1000.0;
    }

    /** Where the line is on the meter at a moment (0 to 1): it sweeps across and back, from the left end as the Maintain begins. */
    static double lineAt(Clash c, double atMs) {
        double u = ((atMs - c.sweepStartMs) / sweepMs()) % 2.0;
        if (u < 0) u += 2.0;
        return u <= 1.0 ? u : 2.0 - u;
    }

    /** Half the mark's width, as a share of the meter. */
    static double markHalf() {
        return DomainConfig.CLASH_MARK.get() / 2.0;
    }

    /** A new place for a side's mark: anywhere it fits on the meter, but not where it just was. */
    private static double newMark(Clash c, Clash.Side s) {
        double half = markHalf(), mark = 0.5;
        for (int i = 0; i < 8; i++) {
            mark = half + c.random.nextDouble() * (1.0 - 2.0 * half);
            if (s.mark < 0 || Math.abs(mark - s.mark) >= 0.2) break;
        }
        return mark;
    }

    /** How good a press at {@code atMs} is: 1 with the line on the middle of their mark, 0 at its edge, negative outside it. */
    static double precision(Clash c, Clash.Side s, double atMs) {
        return 1.0 - Math.abs(lineAt(c, atMs) - s.mark) / markHalf();
    }

    /** A press this precise or better (1: on the mark's middle) is perfect. */
    static double perfectFrom() {
        return 1.0 - DomainConfig.CLASH_PERFECT_ZONE.get();
    }

    /**
     * How far a hit of this precision (0 to 1) pushes the bar: from {@code edgeShare} of {@code pushPerHit} at the mark's edge up toward all
     * of it at the middle, and {@code perfectPush} times it for a perfect press (in the mark's middle {@code perfectZone}).
     */
    static double push(double precision) {
        if (precision >= perfectFrom()) return DomainConfig.CLASH_PUSH_PER_HIT.get() * DomainConfig.CLASH_PERFECT_PUSH.get();
        double edge = DomainConfig.CLASH_EDGE_SHARE.get();
        return DomainConfig.CLASH_PUSH_PER_HIT.get() * (edge + (1.0 - edge) * Math.max(0.0, Math.min(1.0, precision)));
    }

    /**
     * Their press ({@code atMs}: when, by sub-tick timing): with the line in their mark it's a hit, pushing the bar toward the other, further
     * the nearer the mark's middle; outside it, a miss, pushing it back and cracking their own barrier. All the way wins. Then their mark
     * moves.
     */
    public static void onPress(ServerPlayer player, double atMs) {
        Clash c = of(player);
        if (c == null || c.phase != Clash.Phase.MAINTAIN) return;
        Clash.Side s = c.sideOf(player.getUUID());
        if (s == null || s.mark < 0) return;
        double precision = precision(c, s, atMs);
        boolean hit = precision >= 0;
        double toward = s == c.first() ? 1 : -1;
        s.presses++;
        s.mark = newMark(c, s);
        if (hit) {
            c.bar += toward * push(precision) * handicap(c, s);
        } else {
            c.bar -= toward * DomainConfig.CLASH_PUSH_PER_MISS.get();
            Domains.crack(s.domain, DomainConfig.CLASH_CRACK_PER_MISS.get()); // cracked apart, it ends: the other wins (onEnded)
            if (c.phase == Clash.Phase.OVER) return;
        }
        sendSweep(c, s, hit ? DomainNet.ClashSweep.HIT : DomainNet.ClashSweep.MISS, (float) Math.max(0, precision));
        c.bar = Math.max(-1, Math.min(1, c.bar));
        if (c.bar >= 1 - 1.0e-9) win(c, c.first(), Clash.Result.MAINTAIN);
        else if (c.bar <= -1 + 1.0e-9) win(c, c.second(), Clash.Result.MAINTAIN);
        if (c.phase != Clash.Phase.OVER) sync(c);
    }

    /** An Incomplete Domain against a full one pushes less. */
    static double handicap(Clash c, Clash.Side s) {
        boolean mineComplete = s.domain.complete(), theirsComplete = c.other(s).domain.complete();
        return !mineComplete && theirsComplete ? 1.0 - DomainConfig.CLASH_INCOMPLETE_HANDICAP.get() : 1.0;
    }

    /** From a caster's client: whether they hold their key. */
    public static void onHold(ServerPlayer player, boolean held) {
        Clash c = of(player);
        if (c == null) return;
        Clash.Side s = c.sideOf(player.getUUID());
        if (s != null) s.held = held;
    }

    // ---------------------------------------------------------------------------------------------- deciding it

    private static void win(Clash c, Clash.Side winner, Clash.Result result) {
        Clash.Side loser = c.other(winner);
        over(c);
        DomainEntity lost = loser.domain.primary(), won = winner.domain.primary();
        if (lost.space != null && won.space == null && InnerSpaces.isInnerSpace(won.level())) {
            Domains.takeOver(lost, won); // opened in the loser's inner space, and won it
        } else {
            if (!lost.ended) Domains.end(lost, Domains.EndReason.CLASH_LOST); // they burn out as usual
            closeAgain(winner);
        }
        tell(c, winner, result);
    }

    private static void tie(Clash c) {
        over(c);
        // One opened in another's inner space first, so it's let out with everyone when that space ends.
        List<Clash.Side> order = new java.util.ArrayList<>(c.sides);
        order.sort(java.util.Comparator.comparing(s -> s.domain.primary().space != null));
        for (Clash.Side s : order) {
            if (!s.domain.primary().ended) Domains.end(s.domain.primary(), Domains.EndReason.CLASH_TIE);
            ServerPlayer caster = caster(s);
            if (caster != null && caster.isAlive()) Burnout.apply(caster, DomainConfig.ticks(DomainConfig.CLASH_EXHAUSTED_BURNOUT));
        }
        tell(c, null, Clash.Result.TIE);
    }

    private static void over(Clash c) {
        c.phase = Clash.Phase.OVER;
        ACTIVE.remove(c);
        for (Clash.Side s : c.sides) {
            s.domain.holdAt = null;
            s.domain.setClashWith(-1);
        }
    }

    /** The winner's barrier closes again, with the time it had used kept, on whoever is inside now. */
    private static void closeAgain(Clash.Side winner) {
        DomainEntity d = winner.domain.primary();
        if (d.ended || d.isRemoved()) return;
        Domains.close(d, Math.max(0, winner.usedTicks), winner.usedTicks < 0);
    }

    private static void tell(Clash c, @Nullable Clash.Side winner, Clash.Result result) {
        for (Clash.Side s : c.sides) {
            ServerPlayer caster = caster(s);
            int outcome = winner == null ? 3 : winner == s ? 1 : 2;
            if (caster != null) PacketDistributor.sendToPlayer(caster, state(c, s, outcome));
        }
        DECIDED.forEach(l -> l.decided(c, winner, result));
    }

    /** One of its barriers ended some other way (its caster died, logged out or left; a command): the other side wins. */
    private static void onEnded(DomainEntity d, Domains.EndReason reason) {
        Clash c = of(d);
        if (c == null || c.phase == Clash.Phase.OVER) return;
        Clash.Side gone = c.sideOf(d);
        if (gone == null) return;
        Clash.Side other = c.other(gone);
        over(c);
        if (reason == Domains.EndReason.SERVER_STOP) return;
        DomainEntity otherShell = other.domain.primary();
        if (gone.domain.primary().space != null && InnerSpaces.isInnerSpace(otherShell.level()) && otherShell.space == null) {
            // The inner space it was opened in is ending with everyone in it: it goes too, nothing won or lost.
            Domains.endQuietly(otherShell);
            tell(c, other, Clash.Result.FORFEIT);
            return;
        }
        closeAgain(other);
        // Cracked apart in the Maintain is still losing it there; anything else is a forfeit.
        tell(c, other, reason == Domains.EndReason.BROKEN ? Clash.Result.MAINTAIN : Clash.Result.FORFEIT);
    }

    // ---------------------------------------------------------------------------------------------- what a caster can't do meanwhile

    /** Holding on (Grip and Maintain): no casting, the key to their Domain included. Key-ups pass. */
    private static boolean intercept(ServerPlayer player, CastIntent intent) {
        if (intent != CastIntent.CAST_PRESS && intent != CastIntent.SPECIAL_PRESS && intent != CastIntent.ULTIMATE_PRESS) return false;
        if (!holding(player)) return false;
        player.displayClientMessage(Component.translatable("message.kegare.domain.clash.holding"), true);
        return true;
    }

    /** Holding on, they can't attack: not with a hand, a weapon, a projectile or a technique. */
    private static void onIncoming(LivingIncomingDamageEvent event) {
        if (ACTIVE.isEmpty() || !(event.getEntity().level() instanceof ServerLevel level)) return;
        Attribution.Hit hit = Attribution.of(event.getSource(), level.getServer());
        if (hit != null && holding(hit.attacker())) event.setCanceled(true);
    }

    private static void onAttack(AttackEntityEvent event) {
        if (!ACTIVE.isEmpty() && event.getEntity() instanceof ServerPlayer p && holding(p)) event.setCanceled(true);
    }

    /** In the Fight, the damage between the two casters is their score (after defences, as the fight record has it). */
    private static void scored(ServerPlayer attacker, ServerPlayer victim, double amount) {
        Clash c = of(attacker);
        if (c == null || c.phase != Clash.Phase.FIGHT) return;
        Clash.Side from = c.sideOf(attacker.getUUID());
        if (from != null && c.other(from).caster.equals(victim.getUUID())) from.score += amount;
    }

    // ---------------------------------------------------------------------------------------------- telling the casters

    private static void sync(Clash c) {
        for (Clash.Side s : c.sides) {
            ServerPlayer caster = caster(s);
            if (caster != null) PacketDistributor.sendToPlayer(caster, state(c, s, 0));
        }
    }

    static DomainNet.ClashState state(Clash c, Clash.Side s, int result) {
        Clash.Side other = c.other(s);
        int phase = result != 0 ? -1 : c.phase.ordinal();
        int left = switch (c.phase) {
            case GRIP -> DomainConfig.ticks(DomainConfig.CLASH_GRIP) - c.phaseTicks;
            case MAINTAIN -> DomainConfig.ticks(DomainConfig.CLASH_MAINTAIN) - c.phaseTicks;
            case FIGHT -> DomainConfig.ticks(DomainConfig.CLASH_FIGHT) - c.phaseTicks;
            case OVER -> 0;
        };
        Vec3 hold = s.domain.holdAt;
        return new DomainNet.ClashState(phase, s.domain.getId(), other.domain.getId(), (float) c.bar(s), Math.max(0, left), s.held,
                s.domain.barrier(), (float) s.score, (float) other.score, holdSlot(s), hold == null ? 0 : hold.x, hold == null ? 0 : hold.y,
                hold == null ? 0 : hold.z, hold == null ? -1f : DomainConfig.CLASH_LEASH.get().floatValue(), result);
    }

    /** The key that opens their Domain: its slot on the bar (0 to 8), -1 for V (a Domain Expansion), -2 if it's nowhere to hold. */
    private static int holdSlot(Clash.Side s) {
        if (s.domain.complete()) return -1;
        ServerPlayer caster = caster(s);
        if (caster == null) return -2;
        int slot = LoadoutService.slots(caster).indexOf(Optional.of(s.domain.abilityId()));
        return slot < 0 ? -2 : slot;
    }

    /** Their meter: when the line began sweeping (on their clock), how long a crossing takes, where their mark is, and their last press. */
    private static void sendSweep(Clash c, Clash.Side s, int result, float precision) {
        ServerPlayer caster = caster(s);
        if (caster == null) return;
        double startClient = SubTick.toClientMs(caster, c.sweepStartMs).orElse(Double.NaN);
        int elapsed = (int) Math.max(0, Math.round(clock.getAsDouble() - c.sweepStartMs));
        PacketDistributor.sendToPlayer(caster, new DomainNet.ClashSweep(s.presses, startClient, elapsed, (int) Math.round(sweepMs()),
                (float) s.mark, (float) markHalf(), (float) perfectFrom(), result, precision));
    }

    // ---------------------------------------------------------------------------------------------- queries

    /** The clash a barrier is in, if any. */
    @Nullable
    public static Clash of(DomainEntity d) {
        for (Clash c : ACTIVE) if (c.sideOf(d) != null) return c;
        return null;
    }

    /** The clash this player is a caster in, if any. */
    @Nullable
    public static Clash of(ServerPlayer player) {
        for (Clash c : ACTIVE) if (c.sideOf(player.getUUID()) != null) return c;
        return null;
    }

    /** Holding on in a clash (Grip or Maintain): held in place, no attacking, no casting. */
    public static boolean holding(ServerPlayer player) {
        Clash c = of(player);
        return c != null && (c.phase == Clash.Phase.GRIP || c.phase == Clash.Phase.MAINTAIN);
    }

    /** Whether these two are each other's side of a clash. */
    public static boolean opponents(ServerPlayer a, ServerPlayer b) {
        Clash c = of(a);
        if (c == null) return false;
        Clash.Side side = c.sideOf(a.getUUID());
        return side != null && c.other(side).caster.equals(b.getUUID());
    }

    @Nullable
    private static ServerPlayer caster(Clash.Side s) {
        return Domains.caster(s.domain);
    }

    /** Tests: every clash forgotten. */
    static void clear() {
        ACTIVE.clear();
    }
}

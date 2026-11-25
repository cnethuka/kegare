package com.cnethuka.kegare.dominion;

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
 * Dominion clashes (DESIGN §3.35): a Dominion opening over one that's opening or open. Two sides at most; a third is refused and pays
 * nothing. Both barriers go half-formed (trapping nobody, no sure-hit) and their time stops while it lasts.
 * <ol>
 *   <li><b>Grip</b> ({@code gripSeconds}): both casters have to be holding the key that opens their Dominion (the one whose Dominion was
 *   already open presses it again).</li>
 *   <li><b>Maintain</b> ({@code maintainSeconds}): held in place, unable to attack or cast, each pays {@code drainPerSecond} Malice and
 *   answers timing prompts judged by sub-tick timing. A hit pushes the bar toward the other (less for an Incomplete Dominion against a
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
    /** The clock prompts are timed on: {@link SubTick#nowMs} (tests set their own). */
    static DoubleSupplier clock = SubTick::nowMs;
    /** Told when a clash is decided: its winner (null for a tie) and how. */
    private static final List<Decided> DECIDED = new CopyOnWriteArrayList<>();

    @FunctionalInterface
    public interface Decided {
        void decided(Clash clash, @Nullable Clash.Side winner, Clash.Result result);
    }

    private Clashes() {}

    public static void init() {
        Dominions.addOpenRule(Clashes::refuse);
        Dominions.addOverlapListener((d, overlapping) -> start(overlapping.get(0), d));
        Dominions.addEndListener(Clashes::onEnded);
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
        addDecidedListener(DominionAdvancements::decided);
    }

    public static void addDecidedListener(Decided listener) {
        DECIDED.add(listener);
    }

    // ---------------------------------------------------------------------------------------------- starting

    /** The open rule: one Dominion in the way, and not already clashing. Anything else is refused, and nothing is paid. */
    static Optional<Component> refuse(ServerPlayer caster, DominionSpec spec, List<DominionEntity> overlapping) {
        if (overlapping.size() >= MAX_SIDES) return Optional.of(Component.translatable("message.kegare.dominion.clash.crowded"));
        DominionEntity other = overlapping.get(0);
        if (of(other) != null || other.state() == DominionEntity.State.CLASH) {
            return Optional.of(Component.translatable("message.kegare.dominion.clash.full"));
        }
        return Optional.empty();
    }

    /** {@code second} opened over {@code first}: they clash. */
    static Clash start(DominionEntity first, DominionEntity second) {
        Clash c = new Clash(side(first), side(second), RandomSource.create());
        ServerLevel level = (ServerLevel) second.level();
        for (Clash.Side s : c.sides) {
            DominionEntity d = s.dominion;
            boolean wasClosed = d.isClosed();
            d.setState(DominionEntity.State.CLASH);
            d.trapped.clear();
            ServerPlayer caster = Dominions.caster(d);
            d.holdAt = caster == null ? null : caster.position();
            if (wasClosed) { // nobody is trapped in it now: every client near lets go of its side
                double range = d.radius() + 96;
                for (ServerPlayer p : level.players()) {
                    if (p.distanceToSqr(d) < range * range) PacketDistributor.sendToPlayer(p, new DominionNet.Trapped(d.getId(), false));
                }
            }
        }
        first.setClashWith(second.getId());
        second.setClashWith(first.getId());
        ServerPlayer a = Dominions.caster(first), b = Dominions.caster(second);
        if (a != null && second.spec != null) InsightHooks.clashedWithDominion(a, second.spec.ability());
        if (b != null && first.spec != null) InsightHooks.clashedWithDominion(b, first.spec.ability());
        // Heard and seen where they meet.
        Vec3 dir = second.centre().subtract(first.centre());
        double gap = dir.length();
        Vec3 meet = gap < 1.0e-6 ? first.centre() : first.centre().add(dir.scale((first.radius() + gap - second.radius()) / 2.0 / gap));
        Dominions.moment(second, DominionNet.Moment.CLASH, meet, 0f);
        ACTIVE.add(c);
        sync(c);
        return c;
    }

    private static Clash.Side side(DominionEntity d) {
        return new Clash.Side(d, d.isClosed() ? Math.max(0, d.tickCount - d.closedAt()) : -1);
    }

    // ---------------------------------------------------------------------------------------------- running it

    static void tick(Clash c) {
        if (c.phase == Clash.Phase.OVER) return;
        c.phaseTicks++;
        switch (c.phase) {
            case GRIP -> {
                if (c.phaseTicks < DominionConfig.ticks(DominionConfig.CLASH_GRIP)) break;
                boolean first = c.first().held, second = c.second().held;
                if (!first && !second) tie(c);
                else if (!first) win(c, c.second(), Clash.Result.MAINTAIN);
                else if (!second) win(c, c.first(), Clash.Result.MAINTAIN);
                else {
                    c.phase = Clash.Phase.MAINTAIN;
                    c.phaseTicks = 0;
                    double now = clock.getAsDouble();
                    for (Clash.Side s : c.sides) s.nextPromptMs = now + gap(c);
                }
            }
            case MAINTAIN -> {
                double now = clock.getAsDouble();
                for (Clash.Side s : c.sides) {
                    ServerPlayer caster = caster(s);
                    if (caster == null || !s.held || !MaliceService.trySpend(caster, DominionConfig.CLASH_DRAIN.get() / 20.0)) {
                        win(c, c.other(s), Clash.Result.MAINTAIN);
                        return;
                    }
                    if (s.prompt == null && now >= s.nextPromptMs) prompt(c, s, now);
                    else if (s.prompt != null && now > s.prompt.beatMs() + DominionConfig.CLASH_PROMPT_WINDOW_MS.get() / 2.0) judge(c, s, false, now);
                    if (c.phase == Clash.Phase.OVER) return;
                }
                if (c.phaseTicks >= DominionConfig.ticks(DominionConfig.CLASH_MAINTAIN)) {
                    c.phase = Clash.Phase.FIGHT;
                    c.phaseTicks = 0;
                    for (Clash.Side s : c.sides) {
                        s.prompt = null;
                        s.dominion.holdAt = null;
                    }
                }
            }
            case FIGHT -> {
                if (c.phaseTicks < DominionConfig.ticks(DominionConfig.CLASH_FIGHT)) break;
                double a = c.first().score, b = c.second().score;
                if (Math.abs(a - b) <= DominionConfig.CLASH_TIE_MARGIN.get() * Math.max(a, b)) tie(c);
                else win(c, a > b ? c.first() : c.second(), Clash.Result.FIGHT);
            }
            case OVER -> {}
        }
        if (c.phase != Clash.Phase.OVER) sync(c);
    }

    private static double gap(Clash c) {
        double min = DominionConfig.CLASH_PROMPT_MIN.get(), max = Math.max(min, DominionConfig.CLASH_PROMPT_MAX.get());
        return (min + c.random.nextDouble() * (max - min)) * 1000.0;
    }

    private static void prompt(Clash c, Clash.Side s, double now) {
        s.prompt = new Clash.Prompt(++s.prompts, c.random.nextInt(3), now + DominionConfig.CLASH_PROMPT_LEAD_MS.get());
        sendPrompt(s, DominionNet.ClashPrompt.PENDING);
    }

    /** A prompt key went down ({@code atMs}: when, by sub-tick timing). Judged at once if a prompt waits; ignored if none does. */
    public static void onPress(ServerPlayer player, int key, double atMs) {
        Clash c = of(player);
        if (c == null || c.phase != Clash.Phase.MAINTAIN) return;
        Clash.Side s = c.sideOf(player.getUUID());
        if (s == null || s.prompt == null) return;
        boolean hit = key == s.prompt.key() && Math.abs(atMs - s.prompt.beatMs()) <= DominionConfig.CLASH_PROMPT_WINDOW_MS.get() / 2.0;
        judge(c, s, hit, clock.getAsDouble());
        if (c.phase != Clash.Phase.OVER) sync(c);
    }

    /** A hit pushes the bar toward the other side; a miss pushes it back and cracks their own barrier. All the way wins. */
    private static void judge(Clash c, Clash.Side s, boolean hit, double now) {
        sendPrompt(s, hit ? DominionNet.ClashPrompt.HIT : DominionNet.ClashPrompt.MISS);
        s.prompt = null;
        s.nextPromptMs = now + gap(c);
        double toward = s == c.first() ? 1 : -1;
        if (hit) {
            c.bar += toward * DominionConfig.CLASH_PUSH_PER_HIT.get() * handicap(c, s);
        } else {
            c.bar -= toward * DominionConfig.CLASH_PUSH_PER_MISS.get();
            Dominions.crack(s.dominion, DominionConfig.CLASH_CRACK_PER_MISS.get()); // cracked apart, it ends: the other wins (onEnded)
            if (c.phase == Clash.Phase.OVER) return;
        }
        c.bar = Math.max(-1, Math.min(1, c.bar));
        if (c.bar >= 1 - 1.0e-9) win(c, c.first(), Clash.Result.MAINTAIN);
        else if (c.bar <= -1 + 1.0e-9) win(c, c.second(), Clash.Result.MAINTAIN);
    }

    /** An Incomplete Dominion against a full one pushes less. */
    static double handicap(Clash c, Clash.Side s) {
        boolean mineComplete = s.dominion.complete(), theirsComplete = c.other(s).dominion.complete();
        return !mineComplete && theirsComplete ? 1.0 - DominionConfig.CLASH_INCOMPLETE_HANDICAP.get() : 1.0;
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
        if (!loser.dominion.ended) Dominions.end(loser.dominion, Dominions.EndReason.CLASH_LOST); // they burn out as usual
        closeAgain(winner);
        tell(c, winner, result);
    }

    private static void tie(Clash c) {
        over(c);
        for (Clash.Side s : c.sides) {
            if (!s.dominion.ended) Dominions.end(s.dominion, Dominions.EndReason.CLASH_TIE);
            ServerPlayer caster = caster(s);
            if (caster != null && caster.isAlive()) Burnout.apply(caster, DominionConfig.ticks(DominionConfig.CLASH_EXHAUSTED_BURNOUT));
        }
        tell(c, null, Clash.Result.TIE);
    }

    private static void over(Clash c) {
        c.phase = Clash.Phase.OVER;
        ACTIVE.remove(c);
        for (Clash.Side s : c.sides) {
            s.prompt = null;
            s.dominion.holdAt = null;
            s.dominion.setClashWith(-1);
        }
    }

    /** The winner's barrier closes again, with the time it had used kept, on whoever is inside now. */
    private static void closeAgain(Clash.Side winner) {
        DominionEntity d = winner.dominion;
        if (d.ended || d.isRemoved()) return;
        Dominions.close(d, Math.max(0, winner.usedTicks), winner.usedTicks < 0);
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
    private static void onEnded(DominionEntity d, Dominions.EndReason reason) {
        Clash c = of(d);
        if (c == null || c.phase == Clash.Phase.OVER) return;
        Clash.Side gone = c.sideOf(d);
        if (gone == null) return;
        Clash.Side other = c.other(gone);
        over(c);
        if (reason == Dominions.EndReason.SERVER_STOP) return;
        closeAgain(other);
        // Cracked apart in the Maintain is still losing it there; anything else is a forfeit.
        tell(c, other, reason == Dominions.EndReason.BROKEN ? Clash.Result.MAINTAIN : Clash.Result.FORFEIT);
    }

    // ---------------------------------------------------------------------------------------------- what a caster can't do meanwhile

    /** Holding on (Grip and Maintain): no casting, the key to their Dominion included. Key-ups pass. */
    private static boolean intercept(ServerPlayer player, CastIntent intent) {
        if (intent != CastIntent.CAST_PRESS && intent != CastIntent.SPECIAL_PRESS && intent != CastIntent.ULTIMATE_PRESS) return false;
        if (!holding(player)) return false;
        player.displayClientMessage(Component.translatable("message.kegare.dominion.clash.holding"), true);
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

    static DominionNet.ClashState state(Clash c, Clash.Side s, int result) {
        Clash.Side other = c.other(s);
        int phase = result != 0 ? -1 : c.phase.ordinal();
        int left = switch (c.phase) {
            case GRIP -> DominionConfig.ticks(DominionConfig.CLASH_GRIP) - c.phaseTicks;
            case MAINTAIN -> DominionConfig.ticks(DominionConfig.CLASH_MAINTAIN) - c.phaseTicks;
            case FIGHT -> DominionConfig.ticks(DominionConfig.CLASH_FIGHT) - c.phaseTicks;
            case OVER -> 0;
        };
        Vec3 hold = s.dominion.holdAt;
        return new DominionNet.ClashState(phase, s.dominion.getId(), other.dominion.getId(), (float) c.bar(s), Math.max(0, left), s.held,
                s.dominion.barrier(), (float) s.score, (float) other.score, holdSlot(s), hold == null ? 0 : hold.x, hold == null ? 0 : hold.y,
                hold == null ? 0 : hold.z, hold == null ? -1f : DominionConfig.CLASH_LEASH.get().floatValue(), result);
    }

    /** The key that opens their Dominion: its slot on the bar (0 to 8), -1 for V (a full Dominion), -2 if it's nowhere to hold. */
    private static int holdSlot(Clash.Side s) {
        if (s.dominion.complete()) return -1;
        ServerPlayer caster = caster(s);
        if (caster == null) return -2;
        int slot = LoadoutService.slots(caster).indexOf(Optional.of(s.dominion.abilityId()));
        return slot < 0 ? -2 : slot;
    }

    private static void sendPrompt(Clash.Side s, int result) {
        ServerPlayer caster = caster(s);
        Clash.Prompt prompt = s.prompt;
        if (caster == null || prompt == null) return;
        double beatClient = SubTick.toClientMs(caster, prompt.beatMs()).orElse(Double.NaN);
        int lead = (int) Math.max(0, Math.round(prompt.beatMs() - clock.getAsDouble()));
        PacketDistributor.sendToPlayer(caster, new DominionNet.ClashPrompt(prompt.id(), prompt.key(), beatClient, lead,
                DominionConfig.CLASH_PROMPT_WINDOW_MS.get(), result));
    }

    // ---------------------------------------------------------------------------------------------- queries

    /** The clash a barrier is in, if any. */
    @Nullable
    public static Clash of(DominionEntity d) {
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
        return Dominions.caster(s.dominion);
    }

    /** Tests: every clash forgotten. */
    static void clear() {
        ACTIVE.clear();
    }
}

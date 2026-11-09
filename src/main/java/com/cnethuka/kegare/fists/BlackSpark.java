package com.cnethuka.kegare.fists;

import com.cnethuka.kegare.combat.CombatHelper;
import com.cnethuka.kegare.impact.Impacts;
import com.cnethuka.kegare.malice.MaliceService;
import com.cnethuka.kegare.melee.MeleeConfig;
import com.cnethuka.kegare.melee.MeleeHits;
import com.cnethuka.kegare.melee.MeleeLayer;
import com.cnethuka.kegare.melee.MeleeNumbers;
import com.cnethuka.kegare.melee.ReinforcedStrike;
import com.cnethuka.kegare.progression.Attribution;
import com.cnethuka.kegare.progression.InsightHooks;
import com.cnethuka.kegare.timing.SubTick;
import com.cnethuka.kegare.timing.TimedInput;
import com.cnethuka.kegare.timing.TimingLog;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/**
 * Black Spark (DESIGN §3.28): Malice landing within a hair of the hit. Reinforced Fists turned on just before a melee hit lands on a living
 * target is a try (only the first hit after each turn-on, within {@code tryMs}). The gap is the sub-tick time from Fists going on to the
 * attack (§3.29). Never at will: each try the server secretly rolls where the spark sits, an offset in [0, jitterMs], never sent anywhere,
 * and the hit is a Black Spark if the gap falls in [offset, offset + window]; the window comes from {@link SparkWindow}'s factors.
 * Skill is keeping the gap short and steady; luck decides the rest.
 * <p>
 * Its power is canon's exponent with no ceiling: the hit's damage put on a scale that starts at 2 ({@code s = 2 + damage / scaleDamage}),
 * raised to the 2.5 and turned back into damage (times {@code s^1.5}). Added to the hit before the target's defences, which are what stop
 * it: armour, a guard, a perfect parry (it never gets here), Toughness, Rime Shell. It counts as fully charged in the Impact System, adds
 * {@code knockback} (never to a boss), and a black crack bursts from the point of contact ({@link BlackSparkFx}).
 */
public final class BlackSpark {
    @FunctionalInterface
    public interface Listener {
        /** @param chain Black Sparks in the attacker's chain with this one (1: it started the zone) */
        void onSpark(ServerPlayer attacker, LivingEntity target, int chain);
    }

    static final class State {
        /** This turn-on's try has been used (or there's been no turn-on). */
        boolean tried = true;
        /** {@code /kegare debug spark force}: the next melee hit is one. */
        boolean force;
    }

    /** Server-side only, and never sent anywhere: where each try's spark sits. */
    private static final RandomSource SECRET = RandomSource.create();
    private static final Map<UUID, State> STATES = new HashMap<>();
    private static final List<Listener> LISTENERS = new CopyOnWriteArrayList<>();
    /** The listeners, for tests to take theirs off again. */
    static final List<Listener> LISTENERS_FOR_TESTS = LISTENERS;
    /** Told before the listeners: the zone, so the chain they're told is the new one. */
    private static final List<java.util.function.Consumer<ServerPlayer>> FIRST = new CopyOnWriteArrayList<>();
    /** Victims a Black Spark is knocking back, once the attack is over (its own knockback done). */
    private static final Map<LivingEntity, ServerPlayer> KNOCK = new WeakHashMap<>();
    /** For tests: the next try's offset, instead of a secret roll. */
    static double testOffset = Double.NaN;

    private BlackSpark() {}

    public static void listen(Listener listener) {
        LISTENERS.add(listener);
    }

    static void listenFirst(java.util.function.Consumer<ServerPlayer> listener) {
        FIRST.add(listener);
    }

    static State state(ServerPlayer player) {
        return STATES.computeIfAbsent(player.getUUID(), id -> new State());
    }

    /** Fists went on: a fresh try. */
    static void onFistsOn(ServerPlayer player) {
        state(player).tried = false;
    }

    public static void force(ServerPlayer player) {
        state(player).force = true;
    }

    /** The multiplier on a hit of {@code damage}: s^(exponent - 1), s = 2 + damage / scaleDamage. No cap. */
    public static double multiplier(double damage) {
        return multiplier(damage, FistsConfig.SCALE_DAMAGE.get(), FistsConfig.EXPONENT.get());
    }

    public static double multiplier(double damage, double scaleDamage, double exponent) {
        double s = 2.0 + Math.max(0, damage) / scaleDamage;
        return Math.pow(s, exponent - 1.0);
    }

    /** Is the gap inside this try's window? */
    static boolean lands(double gap, double offset, double window) {
        return gap >= offset && gap <= offset + window;
    }

    /** After the Fists bonus (and before any zone), on a melee hit about to land. */
    static void onIncoming(LivingIncomingDamageEvent event) {
        LivingEntity target = event.getEntity();
        if (!(target.level() instanceof ServerLevel level) || !(event.getSource().getEntity() instanceof ServerPlayer attacker)) return;
        if (MeleeHits.of(event.getSource(), level) != MeleeHits.Kind.MELEE || !Attribution.isRealTarget(attacker, target) || !target.isAlive()) return;
        State state = state(attacker);
        boolean forced = state.force;
        if (forced) {
            state.force = false;
            log(attacker, target, "forced");
        } else {
            if (state.tried || !ReinforcedFists.isOn(attacker) || !MeleeLayer.swungThisTick(attacker)) return;
            state.tried = true;
            OptionalDouble gap = SubTick.gap(attacker, TimedInput.FISTS_ON, TimedInput.ATTACK);
            if (gap.isEmpty() || gap.getAsDouble() < 0 || gap.getAsDouble() > FistsConfig.TRY_MS.get()) {
                log(attacker, target, String.format(Locale.ROOT, "no try (gap %s)", gap.isEmpty() ? "none" : String.format(Locale.ROOT, "%.1f ms", gap.getAsDouble())));
                return;
            }
            double offset = Double.isNaN(testOffset) ? SECRET.nextDouble() * FistsConfig.JITTER_MS.get() : testOffset;
            testOffset = Double.NaN;
            SparkWindow window = SparkWindow.of(attacker, target, Zone.in(attacker));
            boolean hit = lands(gap.getAsDouble(), offset, window.ms());
            log(attacker, target, String.format(Locale.ROOT,
                    "gap %.1f ms, offset %.1f ms, window %.1f ms (Malice %.2f, opponent %.2f, body %.2f, weather %.2f, Grade %.2f, zone %.2f) -> %s",
                    gap.getAsDouble(), offset, window.ms(), window.malice(), window.opponent(), window.body(), window.weather(), window.grade(),
                    window.zone(), hit ? "BLACK SPARK" : "miss"));
            if (!hit) return;
        }
        land(event, attacker, target);
    }

    private static void land(LivingIncomingDamageEvent event, ServerPlayer attacker, LivingEntity target) {
        float amount = event.getAmount();
        double hit = amount;
        // A Strike about to go off is part of the hit: its bonus counts on the scale (it still multiplies after the defences, as always).
        MeleeNumbers numbers = MeleeNumbers.of(attacker);
        if (ReinforcedStrike.isReady(attacker) && MaliceService.canAfford(attacker, numbers.strikeCost())) hit *= 1.0 + numbers.strikeBonus();
        double multiplier = multiplier(hit);
        event.setAmount((float) (amount * multiplier));
        Impacts.charge(target, 1.0); // counts as fully charged
        if (!CombatHelper.isBoss(target)) KNOCK.put(target, attacker);
        BlackSparkFx.play(attacker, target);
        if (TimingLog.any()) {
            TimingLog.send(attacker.server, String.format(Locale.ROOT, "  power: hit %.2f, x%.2f -> %.2f before %s's defences",
                    hit, multiplier, amount * multiplier, target.getName().getString()));
        }
        InsightHooks.firstBlackSpark(attacker);
        FIRST.forEach(listener -> listener.accept(attacker));
        int chain = Zone.chain(attacker);
        LISTENERS.forEach(listener -> listener.onSpark(attacker, target, chain));
    }

    /**
     * The end of the tick the hit landed in, once the attack and every knockback of its own are done (a sprint's comes after the damage),
     * and before the Impact System adds the tick up: the Black Spark's knockback on top, so nothing halves it.
     */
    public static void knockBack() {
        if (KNOCK.isEmpty()) return;
        Map<LivingEntity, ServerPlayer> due = new HashMap<>(KNOCK);
        KNOCK.clear();
        double strength = FistsConfig.KNOCKBACK.get();
        if (strength <= 0) return;
        due.forEach((target, attacker) -> {
            if (!target.isAlive() || target.level() != attacker.level()) return;
            Vec3 away = target.position().subtract(attacker.position());
            target.knockback(strength, -away.x, -away.z);
            target.hurtMarked = true;
        });
    }

    private static void log(ServerPlayer attacker, LivingEntity target, String what) {
        if (TimingLog.any()) {
            TimingLog.send(attacker.server, "Black Spark try, " + attacker.getGameProfile().getName() + " -> " + target.getName().getString() + ": " + what);
        }
    }

    static void forget(UUID player) {
        STATES.remove(player);
    }

    public static void clear() {
        STATES.clear();
        KNOCK.clear();
        testOffset = Double.NaN;
    }

    /** For the power table (DESIGN §3.28): what a hit of {@code damage} becomes, with no defences. */
    public static double power(double damage) {
        return damage * multiplier(damage);
    }

    static double strikeShare() {
        return 1.0 + MeleeConfig.STRIKE_BONUS.get();
    }
}

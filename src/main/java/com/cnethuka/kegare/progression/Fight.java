package com.cnethuka.kegare.progression;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * One fight between two players: who damaged whom, how much, when, and what they used. Pure bookkeeping (no game
 * classes), so the rules can be tested without a server; {@link FightTracker} drives it.
 */
public final class Fight {
    /** One landed ability ({@code special}: it was an R special) or melee move, at {@code tick}. */
    public record Use(String id, boolean special, long tick) {}

    /** One player's side of the fight. */
    public static final class Side {
        public final UUID player;
        /** Damage dealt to the opponent. */
        double dealt;
        /** Malice spent on technique casts, at base cost (see Assessment's Efficiency). */
        double baseMalice;
        final List<Use> abilities = new ArrayList<>();
        final List<Use> melee = new ArrayList<>();

        Side(UUID player) {
            this.player = player;
        }

        public double dealt() {
            return dealt;
        }

        public double baseMalice() {
            return baseMalice;
        }

        public List<Use> abilities() {
            return List.copyOf(abilities);
        }

        public List<Use> melee() {
            return List.copyOf(melee);
        }
    }

    private final Side a;
    private final Side b;
    private final long startTick;
    private long lastDamageTick;

    public Fight(UUID a, UUID b, long startTick) {
        this.a = new Side(a);
        this.b = new Side(b);
        this.startTick = startTick;
        this.lastDamageTick = startTick;
    }

    public Side side(UUID player) {
        if (a.player.equals(player)) return a;
        if (b.player.equals(player)) return b;
        throw new IllegalArgumentException(player + " isn't in this fight");
    }

    public Side opponentOf(UUID player) {
        return side(player) == a ? b : a;
    }

    public boolean involves(UUID player) {
        return a.player.equals(player) || b.player.equals(player);
    }

    public void damage(UUID attacker, double amount, long now) {
        side(attacker).dealt += amount;
        lastDamageTick = now;
    }

    public void spend(UUID player, double baseMalice) {
        side(player).baseMalice += baseMalice;
    }

    public void landed(UUID caster, String ability, boolean special, long now) {
        side(caster).abilities.add(new Use(ability, special, now));
    }

    public void melee(UUID attacker, String move, long now) {
        side(attacker).melee.add(new Use(move, false, now));
    }

    public long startTick() {
        return startTick;
    }

    public long lastDamageTick() {
        return lastDamageTick;
    }

    /** No damage between them for {@code timeoutTicks}. */
    public boolean expired(long now, long timeoutTicks) {
        return now - lastDamageTick >= timeoutTicks;
    }

    /** First event to last damage: the 30 quiet seconds at the end of a fight that ran out are not part of it. */
    public long lengthTicks() {
        return lastDamageTick - startTick;
    }

    /**
     * What a death means for one side.
     *
     * @param share of everything the dead player took from players across their fights, what this side dealt to them
     */
    public static FightOutcome outcomeOnOpponentDeath(double dealtToThem, double takenFromPlayers, double winShare) {
        if (dealtToThem <= 0 || takenFromPlayers <= 0) return FightOutcome.SURVIVED;
        return dealtToThem / takenFromPlayers >= winShare ? FightOutcome.WIN : FightOutcome.SURVIVED;
    }
}

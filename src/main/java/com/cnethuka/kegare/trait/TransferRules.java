package com.cnethuka.kegare.trait;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.IntUnaryOperator;

/**
 * Pure succession and inactivity rules for unique traits (no Minecraft types, unit-tested).
 * {@code random.applyAsInt(bound)} must return a uniform int in {@code [0, bound)}.
 */
public final class TransferRules {
    /** An online player as seen by the succession logic. */
    public record Candidate(UUID id, boolean severed) {}

    private static final long MILLIS_PER_DAY = 86_400_000L;

    private TransferRules() {}

    /** Heirs must not be the previous bearer and must not be Severed. */
    public static boolean isEligibleHeir(Candidate candidate, UUID previousBearer) {
        return !candidate.severed() && !candidate.id().equals(previousBearer);
    }

    public static List<UUID> eligibleHeirs(Collection<Candidate> online, UUID previousBearer) {
        List<UUID> out = new ArrayList<>();
        for (Candidate c : online) {
            if (isEligibleHeir(c, previousBearer)) out.add(c.id());
        }
        return out;
    }

    /**
     * Picks the heir, or empty if nobody online is eligible (the trait then waits for the next eligible login).
     * KILLER mode gives it to the killer when they are online and eligible, else falls back to a random pick.
     *
     * @param killer the killing player, or null when the loss had no killer (release, inactivity)
     */
    public static Optional<UUID> pickHeir(TransferMode mode, Collection<Candidate> online, UUID previousBearer,
                                         UUID killer, IntUnaryOperator random) {
        List<UUID> eligible = eligibleHeirs(online, previousBearer);
        if (eligible.isEmpty()) return Optional.empty();
        if (mode == TransferMode.KILLER && killer != null && eligible.contains(killer)) return Optional.of(killer);
        return Optional.of(eligible.get(random.applyAsInt(eligible.size())));
    }

    /** True once a holder has been away for at least {@code inactiveDays} (0 or less disables the rule). */
    public static boolean isInactive(long lastSeenMillis, long nowMillis, double inactiveDays) {
        if (inactiveDays <= 0) return false;
        return nowMillis - lastSeenMillis >= (long) (inactiveDays * MILLIS_PER_DAY);
    }
}

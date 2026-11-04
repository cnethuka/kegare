package com.cnethuka.kegare.roll;

import com.cnethuka.kegare.technique.TechniqueRarity;
import java.util.Optional;
import java.util.function.IntUnaryOperator;

/**
 * Pure rarity-roll math with soft and hard pity. No Minecraft types, so it is covered by plain unit tests.
 * <p>
 * {@code rollsSinceLegendary} is the number of completed rolls since the last LEGENDARY, so the roll
 * being made is roll number {@code rollsSinceLegendary + 1}.
 * <ul>
 *   <li>Soft pity: roll number {@code n > softPityStart} adds {@code (n - softPityStart) * softPityIncrement}
 *       to the LEGENDARY weight (defaults: roll 31 is +5, roll 32 is +10, ...).</li>
 *   <li>Hard pity: roll number {@code n >= hardPity} is a guaranteed LEGENDARY (default: roll 50).</li>
 *   <li>Rarities with no eligible technique ({@code available[i] == false}) get weight 0. If LEGENDARY is
 *       unavailable, pity can't trigger and the counter keeps climbing until it is.</li>
 * </ul>
 * {@code random.applyAsInt(bound)} must return a uniform int in {@code [0, bound)}.
 */
public final class PityRoller {
    public record Settings(int[] baseWeights, int softPityStart, int softPityIncrement, int hardPity) {}

    /** @param byHardPity true when the LEGENDARY came from the hard-pity guarantee */
    public record Result(TechniqueRarity rarity, boolean byHardPity) {}

    private static final int LEGENDARY = TechniqueRarity.LEGENDARY.ordinal();

    private PityRoller() {}

    public static boolean isHardPity(Settings settings, int rollsSinceLegendary) {
        return settings.hardPity() > 0 && rollsSinceLegendary + 1 >= settings.hardPity();
    }

    /** Weights for the next roll (index = rarity ordinal), including soft pity. */
    public static int[] effectiveWeights(Settings settings, int rollsSinceLegendary, boolean[] available) {
        TechniqueRarity[] rarities = TechniqueRarity.values();
        int[] weights = new int[rarities.length];
        for (int i = 0; i < rarities.length; i++) {
            weights[i] = available[i] ? Math.max(0, settings.baseWeights()[i]) : 0;
        }
        int rollNumber = rollsSinceLegendary + 1;
        if (available[LEGENDARY] && rollNumber > settings.softPityStart()) {
            long bonus = (long) (rollNumber - settings.softPityStart()) * Math.max(0, settings.softPityIncrement());
            weights[LEGENDARY] = (int) Math.min(Integer.MAX_VALUE / 2, weights[LEGENDARY] + bonus);
        }
        return weights;
    }

    /** Picks a rarity, or empty if no rarity has any eligible technique. */
    public static Optional<Result> pick(Settings settings, int rollsSinceLegendary, boolean[] available, IntUnaryOperator random) {
        if (available[LEGENDARY] && isHardPity(settings, rollsSinceLegendary)) {
            return Optional.of(new Result(TechniqueRarity.LEGENDARY, true));
        }
        TechniqueRarity[] rarities = TechniqueRarity.values();
        int[] weights = effectiveWeights(settings, rollsSinceLegendary, available);
        long total = 0;
        for (int w : weights) total += w;

        if (total <= 0) {
            // Every eligible rarity has weight 0 in config: fall back to a uniform pick among the eligible ones.
            int count = 0;
            for (boolean a : available) if (a) count++;
            if (count == 0) return Optional.empty();
            int pick = random.applyAsInt(count);
            for (int i = 0; i < rarities.length; i++) {
                if (available[i] && pick-- == 0) return Optional.of(new Result(rarities[i], false));
            }
            return Optional.empty();
        }

        int pick = random.applyAsInt((int) Math.min(Integer.MAX_VALUE, total));
        for (int i = 0; i < rarities.length; i++) {
            pick -= weights[i];
            if (pick < 0) return Optional.of(new Result(rarities[i], false));
        }
        return Optional.empty();
    }

    /** Pity counter after a roll: resets on any LEGENDARY. */
    public static int nextCounter(int rollsSinceLegendary, TechniqueRarity result) {
        return result == TechniqueRarity.LEGENDARY ? 0 : rollsSinceLegendary + 1;
    }
}

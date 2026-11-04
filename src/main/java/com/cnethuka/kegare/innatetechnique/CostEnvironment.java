package com.cnethuka.kegare.innatetechnique;

import java.util.List;

/**
 * How where a player stands changes an innate technique's Malice costs (e.g. Rime is cheaper in the cold and dearer in
 * the heat). Computed on the server by {@link InnateTechnique#environment}; synced for the HUD icon and tooltips.
 *
 * @param multiplier applies to costs and upkeep (1 = unchanged)
 * @param icon       which icon the HUD shows next to the Malice bar
 * @param factors    what is active, for tooltips (lang key and its own multiplier)
 */
public record CostEnvironment(float multiplier, Icon icon, List<Factor> factors) {
    public static final CostEnvironment NEUTRAL = new CostEnvironment(1f, Icon.NONE, List.of());

    public enum Icon { NONE, COLD, HOT }

    public record Factor(String translationKey, float multiplier) {}

    public boolean isNeutral() {
        return Math.abs(multiplier - 1f) < 1.0e-4f && factors.isEmpty();
    }
}

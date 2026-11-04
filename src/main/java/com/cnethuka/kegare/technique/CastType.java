package com.cnethuka.kegare.technique;

/**
 * How an ability is cast. See {@link CastManager} for the exact lifecycle.
 * <ul>
 *   <li>INSTANT: fires on key press.</li>
 *   <li>CHARGED: hold to charge, fires on release with a 0..1 charge fraction.</li>
 *   <li>CHANNELED: runs while the key is held, draining upkeep per second.</li>
 *   <li>TOGGLE: press to switch on/off, draining upkeep per second while on.</li>
 * </ul>
 */
public enum CastType {
    INSTANT,
    CHARGED,
    CHANNELED,
    TOGGLE;

    public String translationKey() {
        return "ability.kegare.cast_type." + name().toLowerCase(java.util.Locale.ROOT);
    }
}

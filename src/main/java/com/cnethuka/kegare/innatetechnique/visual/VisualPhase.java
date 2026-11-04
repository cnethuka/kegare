package com.cnethuka.kegare.innatetechnique.visual;

/**
 * When an ability's visuals play. Each phase may name a player animation (Player Animation Library) and/or a VFX
 * effect (Photon). Loop phases keep playing until their cast ends; the rest play once.
 */
public enum VisualPhase {
    /** INSTANT fired, or a CHANNELED cast started. Once. */
    CAST(false),
    /** CHARGED: while charging. Loops; the effect grows from small to full size over the full charge time. */
    CHARGE_LOOP(true),
    /** CHARGED: fired on release (a too-short tap fizzles and shows nothing). Once. */
    RELEASE(false),
    /** CHANNELED: while channeling. Loops. */
    CHANNEL_LOOP(true),
    /** TOGGLE switched on. Once. */
    TOGGLE_ON(false),
    /** TOGGLE: while on. Loops. */
    TOGGLE_LOOP(true),
    /** TOGGLE switched off. Once. */
    TOGGLE_OFF(false),
    /** On the ability's projectile entity while it flies (the projectile starts it on the client). */
    PROJECTILE_TRAIL(true),
    /** Where the ability's projectile or effect hits. Once, at a position. */
    IMPACT(false);

    private final boolean loop;

    VisualPhase(boolean loop) {
        this.loop = loop;
    }

    public boolean loops() {
        return loop;
    }
}

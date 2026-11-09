package com.cnethuka.kegare.timing;

/** The inputs the client stamps with its own clock (DESIGN §3.29). Add one here and stamp it on the client to time it finer than a tick. */
public enum TimedInput {
    /** Reinforced Fists turned on (its number key in Technique Mode). */
    FISTS_ON,
    /** A melee attack on an entity (the attack button). */
    ATTACK,
    /** The guard going up (the Parry key). */
    PARRY;

    public static TimedInput byId(int id) {
        TimedInput[] all = values();
        return id >= 0 && id < all.length ? all[id] : null;
    }
}

package com.cnethuka.kegare.progression;

/** How a fight ended for one of the two players. Only Grade XP ever reads this: Assessment never does. */
public enum FightOutcome {
    /** The opponent died during it and you dealt enough of the damage they took from players. */
    WIN,
    /** You died during it. */
    LOSS,
    /** Nobody won: it ran out, the opponent left, or they died but you hadn't dealt your share. */
    SURVIVED,
    /** You logged out during it. */
    LEFT
}

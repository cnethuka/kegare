package com.cnethuka.kegare.client;

import com.cnethuka.kegare.innatetechnique.CastIntent;

/**
 * What Technique Mode's number keys and R mean, tick by tick (DESIGN §3.5). Plain logic with no game classes, so every order of
 * presses can be tested. It only turns keys into requests: the server decides what any of them does.
 * <ul>
 *   <li>A number key going down selects its slot and casts its base ability. With R held, its special.</li>
 *   <li>Only the key that started a cast sends its release, so letting go of some other key can't fire someone else's charge.</li>
 *   <li>While that key is really holding a charge or channel, other number keys do nothing but say so: there's one hold at a time.</li>
 *   <li>R on its own acts when it comes up, if no number key went down while it was held. Something R has to end, it ends at once.</li>
 *   <li>A key needs a real press to start anything. One still down as a screen closes, or auto-repeating, starts nothing.</li>
 * </ul>
 */
final class InstantCastKeys {
    static final int SLOTS = 9;
    /** How long a new hold counts as running before the server has had time to say whether it is. */
    static final int SYNC_GRACE_TICKS = 5;

    /** The keys since last tick. */
    static final class Frame {
        final boolean[] numberDown = new boolean[SLOTS];
        final boolean[] numberClicked = new boolean[SLOTS];
        boolean specialDown;
        boolean specialClicked;
        boolean modeOn;
        /** R has something to end, such as a possession: it works with the mode off, and on the press. */
        boolean specialNeeded;
        boolean screenOpen;
    }

    /** What only the game can answer. */
    interface World {
        /** That slot's base or special is a charge or channel that would start now. */
        boolean holds(int slot, boolean special);

        /** The server's last word: a charge or channel is running. */
        boolean casting();

        /** R on its own would start a charge or channel it can't hold, on the selected slot. */
        boolean selectedSpecialHolds();
    }

    interface Output {
        void select(int slot);

        void intent(CastIntent intent);

        /** A number key went down during another key's hold. */
        void alreadyCasting();

        /** R on its own, on a special that has to be held. */
        void specialNeedsHolding();
    }

    private final boolean[] wasDown = new boolean[SLOTS];
    private final boolean[] pressedInMode = new boolean[SLOTS];
    private int owner = -1;
    private boolean ownerSpecial;
    private boolean ownerHolds;
    private long ownerSince;
    private long tick;

    private boolean specialWasDown;
    private boolean specialLive;
    /** A number key went down while R was held, or R has already done what it had to. */
    private boolean specialActed;
    private boolean specialPressSent;

    void tick(Frame f, World world, Output out) {
        tick++;
        boolean rDown = f.specialDown || (f.specialClicked && !specialWasDown);
        if (rDown && !specialWasDown && f.specialClicked) {
            if (f.specialNeeded) {
                out.intent(CastIntent.SPECIAL_PRESS);
                specialLive = true;
                specialActed = true;
                specialPressSent = true;
            } else if (f.modeOn) {
                specialLive = true;
                specialActed = false;
            }
        }

        // Ups first: letting go of one key and pressing another in a tick fires the first, then starts the second.
        boolean[] down = new boolean[SLOTS];
        for (int i = 0; i < SLOTS; i++) {
            down[i] = f.numberDown[i] || (f.numberClicked[i] && !wasDown[i]); // a tap shorter than a tick, but never an auto-repeat
            if (!down[i] && wasDown[i]) {
                wasDown[i] = false;
                pressedInMode[i] = false;
                if (i == owner) {
                    owner = -1;
                    out.intent(ownerSpecial ? CastIntent.SPECIAL_RELEASE : CastIntent.CAST_RELEASE);
                }
            }
        }
        for (int i = 0; i < SLOTS; i++) {
            if (down[i] && !wasDown[i]) {
                wasDown[i] = true;
                if (f.numberClicked[i] && f.modeOn) press(i, world, out);
            }
        }

        if (!rDown && specialWasDown) {
            if (specialPressSent) {
                out.intent(CastIntent.SPECIAL_RELEASE);
            } else if (specialLive && !specialActed && f.modeOn && !f.screenOpen) { // a screen opening isn't letting go
                if (world.selectedSpecialHolds()) {
                    out.specialNeedsHolding();
                } else {
                    out.intent(CastIntent.SPECIAL_PRESS);
                    out.intent(CastIntent.SPECIAL_RELEASE);
                }
            }
            specialLive = false;
            specialPressSent = false;
        }
        specialWasDown = rDown;
    }

    private void press(int slot, World world, Output out) {
        boolean special = specialLive;
        if (special) specialActed = true;
        pressedInMode[slot] = true;
        if (holdRunning(world)) {
            out.alreadyCasting();
            return;
        }
        out.select(slot);
        out.intent(special ? CastIntent.SPECIAL_PRESS : CastIntent.CAST_PRESS);
        owner = slot;
        ownerSpecial = special;
        ownerHolds = world.holds(slot, special);
        ownerSince = tick;
    }

    private boolean holdRunning(World world) {
        return owner >= 0 && ownerHolds && (world.casting() || tick - ownerSince < SYNC_GRACE_TICKS);
    }

    /** This key went down in Technique Mode and hasn't come up, so vanilla doesn't get it, even if the mode has since ended. */
    boolean ownsKey(int slot) {
        return pressedInMode[slot];
    }

    /** Everything's forgotten, with nothing sent: the connection has gone. */
    void reset() {
        java.util.Arrays.fill(wasDown, false);
        java.util.Arrays.fill(pressedInMode, false);
        owner = -1;
        specialWasDown = false;
        specialLive = false;
        specialPressSent = false;
    }
}

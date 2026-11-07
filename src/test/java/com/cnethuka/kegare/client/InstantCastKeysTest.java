package com.cnethuka.kegare.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cnethuka.kegare.innatetechnique.CastIntent;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class InstantCastKeysTest {
    /** Plays key changes tick by tick and writes down everything sent. */
    private static final class Keys implements InstantCastKeys.World, InstantCastKeys.Output {
        final InstantCastKeys logic = new InstantCastKeys();
        final List<String> sent = new ArrayList<>();
        final boolean[] down = new boolean[InstantCastKeys.SLOTS];
        final boolean[] clicked = new boolean[InstantCastKeys.SLOTS];
        boolean r;
        boolean rClicked;
        boolean mode = true;
        boolean needed;
        boolean screen;
        boolean casting;
        boolean selectedSpecialHolds;
        final boolean[] holdsBase = new boolean[InstantCastKeys.SLOTS];
        final boolean[] holdsSpecial = new boolean[InstantCastKeys.SLOTS];

        Keys press(int key) {
            down[key - 1] = true;
            clicked[key - 1] = true;
            return tick();
        }

        Keys up(int key) {
            down[key - 1] = false;
            return tick();
        }

        Keys pressR() {
            r = true;
            rClicked = true;
            return tick();
        }

        Keys upR() {
            r = false;
            return tick();
        }

        Keys tick() {
            InstantCastKeys.Frame f = new InstantCastKeys.Frame();
            System.arraycopy(down, 0, f.numberDown, 0, down.length);
            System.arraycopy(clicked, 0, f.numberClicked, 0, clicked.length);
            f.specialDown = r;
            f.specialClicked = rClicked;
            f.modeOn = mode;
            f.specialNeeded = needed;
            f.screenOpen = screen;
            logic.tick(f, this, this);
            java.util.Arrays.fill(clicked, false);
            rClicked = false;
            return this;
        }

        Keys ticks(int n) {
            for (int i = 0; i < n; i++) tick();
            return this;
        }

        List<String> take() {
            List<String> out = List.copyOf(sent);
            sent.clear();
            return out;
        }

        @Override public boolean holds(int slot, boolean special) { return special ? holdsSpecial[slot] : holdsBase[slot]; }
        @Override public boolean casting() { return casting; }
        @Override public boolean selectedSpecialHolds() { return selectedSpecialHolds; }
        @Override public void select(int slot) { sent.add("select " + (slot + 1)); }
        @Override public void intent(CastIntent intent) { sent.add(intent.name()); }
        @Override public void alreadyCasting() { sent.add("busy"); }
        @Override public void specialNeedsHolding() { sent.add("hold it"); }
    }

    private static final String BASE_DOWN = CastIntent.CAST_PRESS.name();
    private static final String BASE_UP = CastIntent.CAST_RELEASE.name();
    private static final String SPECIAL_DOWN = CastIntent.SPECIAL_PRESS.name();
    private static final String SPECIAL_UP = CastIntent.SPECIAL_RELEASE.name();

    @Test
    void aNumberKeySelectsItsSlotAndCastsAtOnce() {
        Keys k = new Keys();
        assertEquals(List.of("select 3", BASE_DOWN), k.press(3).take(), "in that order, in one go");
        assertEquals(List.of(BASE_UP), k.up(3).take());
    }

    @Test
    void holdingAKeyDoesntCastAgain() {
        Keys k = new Keys().press(1);
        k.take();
        k.ticks(40);
        for (int i = 0; i < 10; i++) { // the keyboard's own auto-repeat
            k.clicked[0] = true;
            k.tick();
        }
        assertEquals(List.of(), k.take());
    }

    @Test
    void aRepeatCaughtInTheSameTickAsLettingGoIsntAPress() {
        Keys k = new Keys().press(1);
        k.take();
        k.down[0] = false;
        k.clicked[0] = true;
        assertEquals(List.of(BASE_UP), k.tick().take());
    }

    @Test
    void aTapShorterThanATickStillCounts() {
        Keys k = new Keys();
        k.clicked[1] = true;
        assertEquals(List.of("select 2", BASE_DOWN), k.tick().take());
        assertEquals(List.of(BASE_UP), k.tick().take());
    }

    @Test
    void chargedSpecial_holdRThenTheNumberAndLetGoOfTheNumberToFire() {
        Keys k = new Keys();
        k.holdsSpecial[1] = true;
        assertEquals(List.of(), k.pressR().take(), "R alone waits");
        assertEquals(List.of("select 2", SPECIAL_DOWN), k.press(2).take());
        k.casting = true;
        assertEquals(List.of(), k.ticks(30).take());
        assertEquals(List.of(SPECIAL_UP), k.up(2).take(), "the number key fires it");
        assertEquals(List.of(), k.upR().take(), "and R did its job as the modifier");
    }

    @Test
    void lettingGoOfRFirstDoesntFireAChargedSpecial() {
        Keys k = new Keys();
        k.holdsSpecial[1] = true;
        k.pressR().press(2).take();
        k.casting = true;
        assertEquals(List.of(), k.upR().take());
        assertEquals(List.of(SPECIAL_UP), k.up(2).take());
    }

    @Test
    void holdingRChainsSpecials() {
        Keys k = new Keys().pressR();
        assertEquals(List.of("select 1", SPECIAL_DOWN), k.press(1).take());
        assertEquals(List.of(SPECIAL_UP), k.up(1).take());
        assertEquals(List.of("select 4", SPECIAL_DOWN), k.press(4).take());
        k.up(4).take();
        assertEquals(List.of(), k.upR().take());
    }

    @Test
    void rAloneActsWhenItComesUp() {
        Keys k = new Keys();
        assertEquals(List.of(), k.pressR().ticks(10).take(), "nothing yet: a number key may still come");
        assertEquals(List.of(SPECIAL_DOWN, SPECIAL_UP), k.upR().take());
    }

    @Test
    void rAloneOnASpecialThatHasToBeHeldStartsNothing() {
        Keys k = new Keys();
        k.selectedSpecialHolds = true;
        assertEquals(List.of("hold it"), k.pressR().upR().take());
    }

    @Test
    void anotherKeysHoldKeepsItsOwnRelease() {
        Keys k = new Keys();
        k.holdsBase[0] = true;
        k.press(1).take();
        k.casting = true;
        k.ticks(10);
        assertEquals(List.of("busy"), k.press(2).take(), "no selection, no second cast");
        assertEquals(List.of(), k.up(2).take(), "letting go of the other key fires nothing");
        assertEquals(List.of(BASE_UP), k.up(1).take());
    }

    @Test
    void aSecondKeyCastsAtOnceWhenTheFirstWasntAHold() {
        Keys k = new Keys().press(1);
        k.take();
        assertEquals(List.of("select 2", BASE_DOWN), k.press(2).take());
        assertEquals(List.of(), k.up(1).take(), "the first key's release would end the second's cast");
        assertEquals(List.of(BASE_UP), k.up(2).take());
    }

    @Test
    void aHoldTheServerNeverStartedDoesntBlockForLong() {
        Keys k = new Keys();
        k.holdsBase[0] = true;
        k.press(1).take();
        assertEquals(List.of("busy"), k.press(2).take(), "just pressed: the server hasn't answered yet");
        k.up(2).ticks(InstantCastKeys.SYNC_GRACE_TICKS).take();
        assertEquals(List.of("select 3", BASE_DOWN), k.press(3).take(), "it has now, and nothing's running");
        assertEquals(List.of(), k.up(1).take());
    }

    @Test
    void anInterruptedChargeFreesTheKeys() {
        Keys k = new Keys();
        k.holdsBase[0] = true;
        k.press(1).take();
        k.casting = true;
        k.ticks(20);
        k.casting = false; // stunned
        assertEquals(List.of(), k.ticks(5).take(), "a held key doesn't start it again");
        assertEquals(List.of("select 2", BASE_DOWN), k.press(2).take());
    }

    @Test
    void theModeEndingUnderAHeldKeyStillGetsItsRelease() {
        Keys k = new Keys();
        k.holdsBase[0] = true;
        k.press(1).take();
        k.casting = true;
        k.mode = false; // G
        assertTrue(k.ticks(5).logic.ownsKey(0), "vanilla doesn't get its auto-repeat");
        assertEquals(List.of(BASE_UP), k.up(1).take());
        assertFalse(k.logic.ownsKey(0));
    }

    @Test
    void aScreenOpeningFiresAHoldButNeverAnRAlone() {
        Keys k = new Keys();
        k.holdsBase[0] = true;
        k.press(1).take();
        k.screen = true;
        k.down[0] = false; // what opening a screen does to every key
        assertEquals(List.of(BASE_UP), k.tick().take(), "so it can't get stuck");

        Keys r = new Keys().pressR();
        r.screen = true;
        r.r = false;
        assertEquals(List.of(), r.tick().take(), "nobody let go of R");
    }

    @Test
    void aKeyStillDownAsAScreenClosesStartsNothing() {
        Keys k = new Keys();
        k.down[0] = true; // no click: the game's just reread the keyboard
        k.r = true;
        assertEquals(List.of(), k.tick().take());
        k.clicked[0] = true; // and then it auto-repeats
        k.rClicked = true;
        assertEquals(List.of(), k.tick().take());
        assertEquals(List.of(), k.up(1).upR().take());
    }

    @Test
    void withTheModeOffNothingHappens() {
        Keys k = new Keys();
        k.mode = false;
        assertEquals(List.of(), k.press(1).up(1).pressR().upR().take());
        assertFalse(k.logic.ownsKey(0));
    }

    @Test
    void rPressedWithTheModeOffDoesntActOnceItsOn() {
        Keys k = new Keys();
        k.mode = false;
        k.pressR();
        k.mode = true;
        assertEquals(List.of(), k.upR().take());
    }

    @Test
    void theModeEndingWhileRIsHeldCancelsIt() {
        Keys k = new Keys().pressR();
        k.mode = false;
        assertEquals(List.of(), k.upR().take());
    }

    @Test
    void rEndsAPossessionAtOnceWithTheModeOnOrOff() {
        for (boolean mode : new boolean[] {true, false}) {
            Keys k = new Keys();
            k.mode = mode;
            k.needed = true;
            assertEquals(List.of(SPECIAL_DOWN), k.pressR().take(), "on the press, as ever");
            k.needed = false; // it's over
            assertEquals(List.of(SPECIAL_UP), k.upR().take(), "and doesn't then fire a special");
        }
    }

    @Test
    void forgottenOnLogout() {
        Keys k = new Keys().press(1).pressR();
        k.take();
        k.logic.reset();
        java.util.Arrays.fill(k.down, false);
        k.r = false;
        assertEquals(List.of(), k.tick().take());
        assertEquals(List.of("select 1", BASE_DOWN), k.press(1).take());
    }
}

package com.cnethuka.kegare.client.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cnethuka.kegare.client.gui.RevealScript.Input;
import com.cnethuka.kegare.client.gui.RevealScript.Scene;
import com.cnethuka.kegare.innatetechnique.InnateTechniqueRarity;
import java.util.List;
import org.junit.jupiter.api.Test;

class RevealScriptTest {
    private static List<Scene> scenes(Input input) {
        return RevealScript.of(input).steps().stream().map(RevealScript.Step::scene).toList();
    }

    private static Input roll(InnateTechniqueRarity rarity, boolean full) {
        return new Input(rarity, false, false, false, full);
    }

    @Test
    void eachRarityPlaysItsScenesInOrder() {
        assertEquals(List.of(Scene.OMEN, Scene.RARITY, Scene.NAME, Scene.RESULT), scenes(roll(InnateTechniqueRarity.COMMON, true)));
        assertEquals(List.of(Scene.OMEN, Scene.RARITY, Scene.STARS, Scene.NAME, Scene.RESULT), scenes(roll(InnateTechniqueRarity.RARE, true)));
        assertEquals(List.of(Scene.OMEN, Scene.RARITY, Scene.STARS, Scene.EPITHET, Scene.NAME, Scene.RESULT), scenes(roll(InnateTechniqueRarity.EPIC, true)));
        assertEquals(List.of(Scene.OMEN, Scene.RARITY, Scene.STARS, Scene.EPITHET, Scene.NAME, Scene.RESULT), scenes(roll(InnateTechniqueRarity.LEGENDARY, true)));
    }

    @Test
    void eachRarityRunsItsSpecifiedLength() {
        assertEquals(3000, RevealScript.of(roll(InnateTechniqueRarity.COMMON, true)).total());
        assertEquals(5000, RevealScript.of(roll(InnateTechniqueRarity.RARE, true)).total());
        assertEquals(7000, RevealScript.of(roll(InnateTechniqueRarity.EPIC, true)).total());
        assertEquals(10000, RevealScript.of(roll(InnateTechniqueRarity.LEGENDARY, true)).total());
    }

    @Test
    void aRepeatRollPlaysRarityThenName() {
        for (InnateTechniqueRarity rarity : InnateTechniqueRarity.values()) {
            assertEquals(List.of(Scene.RARITY, Scene.NAME, Scene.RESULT), scenes(roll(rarity, false)));
        }
    }

    @Test
    void firstRollIsFullAndLaterOnesAreShortUnlessAlwaysFull() {
        assertTrue(RevealScript.playsFull(false, false));
        assertFalse(RevealScript.playsFull(true, false));
        assertTrue(RevealScript.playsFull(true, true));
    }

    @Test
    void birthShowsMaliceAfterTheNameThenSixEyes() {
        assertEquals(List.of(Scene.OMEN, Scene.RARITY, Scene.NAME, Scene.MALICE, Scene.RESULT),
                scenes(new Input(InnateTechniqueRarity.COMMON, false, true, false, true)));
        assertEquals(List.of(Scene.OMEN, Scene.RARITY, Scene.NAME, Scene.MALICE, Scene.SIX_EYES, Scene.RESULT),
                scenes(new Input(InnateTechniqueRarity.COMMON, false, true, true, true)));
        // A repeat technique shortens the intro, not what's new about this life.
        assertEquals(List.of(Scene.RARITY, Scene.NAME, Scene.MALICE, Scene.SIX_EYES, Scene.RESULT),
                scenes(new Input(InnateTechniqueRarity.COMMON, false, true, true, false)));
    }

    @Test
    void severedBurnsOutThenSaysItsTwoLines() {
        assertEquals(List.of(Scene.BURN_OUT, Scene.HEAVEN, Scene.BORN_WITHOUT, Scene.RESULT), scenes(new Input(null, true, true, false, true)));
        assertEquals(List.of(Scene.BURN_OUT, Scene.HEAVEN, Scene.BORN_WITHOUT, Scene.RESULT), scenes(new Input(null, true, true, false, false)));
    }

    @Test
    void aBirthWithNothingEligibleStillShowsMalice() {
        assertEquals(List.of(Scene.MALICE, Scene.RESULT), scenes(new Input(null, false, true, false, true)));
        assertEquals(List.of(Scene.RESULT), scenes(new Input(null, false, false, false, true)));
    }

    @Test
    void theClockWalksTheStepsAndHoldsOnTheResult() {
        RevealScript script = RevealScript.of(roll(InnateTechniqueRarity.EPIC, true));
        long at = 0;
        for (RevealScript.Step step : script.steps()) {
            assertEquals(at, step.start());
            assertEquals(step.scene(), script.at(step.start()).scene());
            at = step.end();
        }
        assertEquals(Scene.RESULT, script.at(script.total()).scene());
        assertEquals(Scene.RESULT, script.at(script.total() + 600_000).scene());
        assertEquals(1f, script.at(script.total() + 5).progress(script.total() + 5));
    }

    @Test
    void starsFollowTheRarity() {
        assertEquals(1, RevealScript.stars(InnateTechniqueRarity.COMMON));
        assertEquals(2, RevealScript.stars(InnateTechniqueRarity.RARE));
        assertEquals(3, RevealScript.stars(InnateTechniqueRarity.EPIC));
        assertEquals(4, RevealScript.stars(InnateTechniqueRarity.LEGENDARY));
    }

    @Test
    void theFakeOutStartsPurpleAndFlaresToGoldHalfway() {
        InnateTechniqueRarity gold = InnateTechniqueRarity.LEGENDARY;
        assertEquals(InnateTechniqueRarity.EPIC, RevealScript.omenFlame(gold, true, 0f));
        assertEquals(InnateTechniqueRarity.EPIC, RevealScript.omenFlame(gold, true, 0.49f));
        assertEquals(gold, RevealScript.omenFlame(gold, true, 0.5f));
        assertEquals(gold, RevealScript.omenFlame(gold, true, 1f));
        assertEquals(gold, RevealScript.omenFlame(gold, false, 0f));
        for (InnateTechniqueRarity rarity : List.of(InnateTechniqueRarity.COMMON, InnateTechniqueRarity.RARE, InnateTechniqueRarity.EPIC)) {
            assertEquals(rarity, RevealScript.omenFlame(rarity, true, 0f)); // only a Legendary fakes out
        }
    }
}

package com.cnethuka.kegare.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cnethuka.kegare.config.KegareConfigMigration.Action;
import java.util.Map;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.junit.jupiter.api.Test;

class KegareConfigMigrationTest {
    private static double newDefault(String path) {
        ModConfigSpec.ConfigValue<?> value = KegareServerConfig.SPEC.getValues().get(path);
        assertNotNull(value, path);
        return ((Number) assertInstanceOf(Number.class, value.getDefault(), path)).doubleValue();
    }

    /** A real kegare-server.toml from a build that had the test techniques, with their keys. */
    private static final java.nio.file.Path OLD_TOML = java.nio.file.Path.of(System.getProperty("kegare.projectDir", "."))
            .resolve("src/test/resources/oldsave/kegare-server-old.toml");

    @Test
    void anOldTomlWithTheTestTechniquesInItStillLoads() {
        com.electronwill.nightconfig.core.CommentedConfig old = new com.electronwill.nightconfig.toml.TomlParser()
                .parse(OLD_TOML, com.electronwill.nightconfig.core.file.FileNotFoundAction.THROW_ERROR);
        assertTrue(old.contains("roll.includeTestInnateTechniques") && old.contains("test_abilities.dash.cost"), "setup: the old keys are in it");
        Object hardPity = old.get("roll.hardPity");
        assertFalse(KegareServerConfig.SPEC.isCorrect(old), "it has keys this version doesn't");
        java.util.List<String> removed = new java.util.ArrayList<>();
        KegareServerConfig.SPEC.correct(old, (action, path, incorrect, corrected) -> {
            if (action == com.electronwill.nightconfig.core.ConfigSpec.CorrectionAction.REMOVE) removed.add(String.join(".", path));
        });
        assertEquals(java.util.Set.of("roll.includeTestInnateTechniques", "test_abilities"), java.util.Set.copyOf(removed), "those keys go, and nothing else");
        assertTrue(KegareServerConfig.SPEC.isCorrect(old), "and then it's a correct file");
        assertFalse(old.contains("test_abilities"));
        assertEquals(hardPity, old.get("roll.hardPity"), "the rest as it was");
    }

    @Test
    void aValueStillAtItsOldDefaultMoves() {
        assertEquals(Action.MOVE_TO_NEW_DEFAULT, KegareConfigMigration.decide(250, 250, 10_000));
    }

    @Test
    void aHandEditedValueStays() {
        assertEquals(Action.KEEP_HAND_EDITED, KegareConfigMigration.decide(300, 250, 10_000));
    }

    @Test
    void aValueAlreadyAtTheNewDefaultNeedsNothing() {
        assertEquals(Action.NONE, KegareConfigMigration.decide(10_000, 250, 10_000));
    }

    @Test
    void aFileFromBeforeVersionsGetsMigrated() {
        assertTrue(KegareServerConfig.CONFIG_VERSION.getDefault() < KegareConfigMigration.CURRENT_VERSION);
    }

    @Test
    void theEfficiencyCurveStepCoversItsTwoWeights() {
        assertEquals(1.5, newDefault("six_eyes.efficiency.attunement"));
        assertEquals(0.052, newDefault("six_eyes.efficiency.rmtOutputWeight"));
        for (Map.Entry<String, Number> old : KegareConfigMigration.EFFICIENCY_CURVE_OLD_DEFAULTS.entrySet()) {
            assertNotEquals(old.getValue().doubleValue(), newDefault(old.getKey()), old.getKey());
        }
    }

    @Test
    void everyListedValueExistsAndReallyChanged() {
        assertEquals(43, KegareConfigMigration.RESCALE_OLD_DEFAULTS.size());
        for (Map.Entry<String, Number> old : KegareConfigMigration.RESCALE_OLD_DEFAULTS.entrySet()) {
            assertNotEquals(old.getValue().doubleValue(), newDefault(old.getKey()), old.getKey());
        }
    }

    @Test
    void everyCostAndUpkeepIsFourTimesItsOldDefault() {
        for (Map.Entry<String, Number> old : KegareConfigMigration.RESCALE_OLD_DEFAULTS.entrySet()) {
            if (old.getKey().startsWith("malice.tiers.")) continue;
            assertEquals(old.getValue().doubleValue() * 4, newDefault(old.getKey()), 1e-9, old.getKey());
        }
    }

    @Test
    void theTiersLandOnTheNewScale() {
        assertEquals(250, newDefault("malice.tiers.low.base"));
        assertEquals(600, newDefault("malice.tiers.average.base"));
        assertEquals(1500, newDefault("malice.tiers.high.base"));
        assertEquals(10_000, newDefault("malice.tiers.vast.base"));
    }

    @Test
    void theSeveredMeleeStepCoversValuesThatReallyChanged() {
        assertEquals(6, KegareConfigMigration.SEVERED_MELEE_OLD_DEFAULTS.size(), "the Severed Strike's two went with it");
        for (Map.Entry<String, Number> old : KegareConfigMigration.SEVERED_MELEE_OLD_DEFAULTS.entrySet()) {
            assertNotEquals(old.getValue().doubleValue(), newDefault(old.getKey()), old.getKey());
        }
    }

    @Test
    void theBlurStepMovesItsTopSpeedUp() {
        assertEquals(Map.of("severed.blur.speed", 0.56), KegareConfigMigration.BLUR_SPEED_OLD_DEFAULTS);
        assertEquals(0.7, newDefault("severed.blur.speed"), 1.0e-9, "about 2.5 times a sprint");
        assertEquals(KegareConfigMigration.Action.MOVE_TO_NEW_DEFAULT, KegareConfigMigration.decide(0.56, 0.56, 0.7), "an old file's 0.56 moves");
    }

    @Test
    void eachStepRunsOnceInOrderUpToTheCurrentVersion() {
        var steps = KegareConfigMigration.STEPS;
        for (int i = 1; i < steps.size(); i++) assertTrue(steps.get(i - 1).version() < steps.get(i).version());
        assertEquals(KegareConfigMigration.CURRENT_VERSION, steps.get(steps.size() - 1).version());
    }
}

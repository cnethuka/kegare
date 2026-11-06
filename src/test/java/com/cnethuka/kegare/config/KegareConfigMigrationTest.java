package com.cnethuka.kegare.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
    void everyListedValueExistsAndReallyChanged() {
        assertEquals(52, KegareConfigMigration.RESCALE_OLD_DEFAULTS.size());
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
}

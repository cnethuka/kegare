package com.cnethuka.kegare.registry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cnethuka.kegare.backgroundtask.BackgroundTaskData;
import com.cnethuka.kegare.loadout.LoadoutData;
import com.cnethuka.kegare.loadout.SharedAbilityData;
import com.cnethuka.kegare.progression.UnlockData;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

/** Saved data from before Reinforcement became Flow reads under the new names, in the world's files and Postgres alike (same codecs). */
class RenamedIdsTest {
    private static <T> String roundTrip(Codec<T> codec, String json) {
        T data = codec.parse(JsonOps.INSTANCE, JsonParser.parseString(json)).getOrThrow();
        JsonElement out = codec.encodeStart(JsonOps.INSTANCE, data).getOrThrow();
        return out.toString();
    }

    @Test
    void aLoadoutHoldingReinforcementNowHoldsFlow() {
        String out = roundTrip(LoadoutData.CODEC,
                "{\"slots\":[\"kegare:frost_shard\",\"kegare:reinforcement\",\"\"],\"known\":[\"kegare:reinforcement\"]}");
        assertTrue(out.contains("\"kegare:flow\""), out);
        assertTrue(out.contains("kegare:frost_shard"), "the rest as it was: " + out);
        assertFalse(out.contains("reinforcement"), out);
    }

    @Test
    void backgroundTaskSlotsAndUnlocksMoveToTheNewNames() {
        String slots = roundTrip(BackgroundTaskData.CODEC,
                "[{\"task\":\"kegare:background_reinforcement\",\"on\":true},{\"task\":\"kegare:sustained_flow\"},{\"task\":\"kegare:background_rmt\"}]");
        assertTrue(slots.contains("kegare:background_flow") && slots.contains("kegare:steady_output") && slots.contains("kegare:background_rmt"), slots);
        assertTrue(slots.contains("\"on\":true"), "still switched on: " + slots);
        String unlocks = roundTrip(UnlockData.CODEC, "[\"kegare:background_reinforcement\",\"kegare:sustained_flow\"]");
        assertTrue(unlocks.contains("kegare:background_flow") && unlocks.contains("kegare:steady_output"), unlocks);
        String shared = roundTrip(SharedAbilityData.CODEC, "{\"unlocked\":[\"kegare:reinforcement\",\"kegare:reverse_malice_technique\"]}");
        assertTrue(shared.contains("kegare:flow") && shared.contains("kegare:reverse_malice_technique"), shared);
    }

    @Test
    void onlyTheRenamedIdsChange() {
        assertEquals(3, RenamedIds.RENAMED.size());
        assertEquals(com.cnethuka.kegare.Kegare.id("flow"), RenamedIds.current(com.cnethuka.kegare.Kegare.id("reinforcement")));
        assertEquals(com.cnethuka.kegare.Kegare.id("reinforced_fists"), RenamedIds.current(com.cnethuka.kegare.Kegare.id("reinforced_fists")));
    }
}

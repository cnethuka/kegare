package com.cnethuka.kegare.innate_technique.time_manipulation.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cnethuka.kegare.innate_technique.time_manipulation.TimeIds;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.zigythebird.playeranimcore.animation.Animation;
import com.zigythebird.playeranimcore.loading.UniversalAnimLoader;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.junit.jupiter.api.Test;

/**
 * Time Manipulation's shipped animations (player_animations/time_manipulation.json, from tools/anims): Player Animation Library loads
 * every one the code plays, and each has exactly one first-person rule. And Point Maker's mark: its twelve hour marks go dark one by
 * one, and its hands are painted where they stood.
 */
class TimeVisualsTest {
    private static JsonObject file() throws IOException {
        Path path = Path.of(System.getProperty("kegare.projectDir", "."), "src/main/resources/assets/kegare/player_animations/time_manipulation.json");
        try (Reader reader = Files.newBufferedReader(path)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }

    @Test
    void palLoadsEveryAnimationTheCodePlays() throws IOException {
        Map<String, Animation> loaded = UniversalAnimLoader.loadAnimations(file());
        for (ResourceLocation id : TimeIds.ANIMATIONS) {
            Animation animation = loaded.get(id.getPath());
            assertNotNull(animation, id + " is in the file");
            assertTrue(animation.length() > 0f, id + " has a length");
            assertEquals(Animation.LoopType.PLAY_ONCE, animation.loopType(), id + " plays once");
        }
        assertEquals(TimeIds.ANIMATIONS.size(), loaded.size(), "nothing in the file the code doesn't play");
    }

    @Test
    void everyCastHasAFirstPersonRule() throws IllegalAccessException {
        int seen = 0;
        for (java.lang.reflect.Field field : TimeIds.class.getFields()) {
            if (!field.getName().startsWith("ANIM_")) continue;
            Object id = field.get(null);
            seen++;
            assertTrue(TimeIds.FIRST_PERSON_VANILLA.contains(id) ^ TimeIds.FIRST_PERSON_WHILE_LEVEL.contains(id),
                    field.getName() + " is on exactly one of the lists");
            assertTrue(TimeIds.ANIMATIONS.contains(id), field.getName() + " is listed");
        }
        assertEquals(8, seen);
    }

    @Test
    void theMarkRunsDownAndKeepsItsHands() {
        int[] argb = new int[256], glow = new int[256];
        MarkClient.paint(12, 0f, Mth.HALF_PI, argb, glow);
        int silver = count(argb, 0xFF000000 | TimeFx.SILVER);
        MarkClient.paint(6, 0f, Mth.HALF_PI, argb, glow);
        assertTrue(count(argb, 0xFF000000 | TimeFx.SILVER) < silver, "half the hour marks gone dark");
        MarkClient.paint(0, 0f, Mth.HALF_PI, argb, glow);
        assertEquals(0, count(argb, 0xFF000000 | TimeFx.SILVER), "all of them, at the end");
        // Hour hand at twelve (north, -Z), minute hand at three (east, +X), from the centre (7.5, 7.5).
        assertEquals(0xFF000000 | TimeFx.BRIGHT, argb[5 * 16 + 8], "the hour hand points north");
        assertEquals(0xFF000000 | TimeFx.BRIGHT, argb[8 * 16 + 11], "the minute hand points east");
        assertEquals(0, argb[0], "the corners stay bare");
    }

    private static int count(int[] argb, int c) {
        int n = 0;
        for (int v : argb) if (v == c) n++;
        return n;
    }
}

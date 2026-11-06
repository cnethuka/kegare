package com.cnethuka.kegare.innate_technique.puppet_manipulation.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cnethuka.kegare.innate_technique.puppet_manipulation.PuppetIds;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.zigythebird.playeranimcore.animation.Animation;
import com.zigythebird.playeranimcore.loading.UniversalAnimLoader;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

/**
 * Strings' Blockbench art, checked as it ships. These files get re-exported by hand (DESIGN §3.20), so this is what catches an
 * export the game can't use: a free rotation a 1.21.1 block model refuses, a segment no longer the length the renderer lays out,
 * or an animation the code plays gone missing.
 */
class StringsArtTest {
    private static final List<ResourceLocation> PLAYED = List.of(PuppetIds.ANIM_STRINGS_FIRE, PuppetIds.ANIM_STRINGS_REEL,
            PuppetIds.ANIM_STRINGS_ZIP, PuppetIds.ANIM_STRINGS_TIE, PuppetIds.ANIM_STRINGS_LEASH);
    /** The ones with a tell of their own at stage 2: no two may look alike. */
    private static final List<String> VARIATIONS = List.of("strings_reel", "strings_zip", "strings_tie", "strings_leash");
    private static final Set<Double> VALID_ANGLES = Set.of(0.0, 22.5, -22.5, 45.0, -45.0);

    private static Path project(String path) {
        return Path.of(System.getProperty("kegare.projectDir", "."), path);
    }

    private static JsonObject json(String path) throws IOException {
        try (Reader reader = Files.newBufferedReader(project(path))) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }

    @Test
    void theModelsAreOnesTheGameCanBake() throws IOException {
        for (String name : List.of("segment", "knot")) {
            JsonObject model = json("src/main/resources/assets/kegare/models/string/" + name + ".json");
            assertEquals("kegare:item/string_braid", model.getAsJsonObject("textures").get("0").getAsString(), name);
            JsonArray elements = model.getAsJsonArray("elements");
            assertFalse(elements.isEmpty(), name);
            for (JsonElement e : elements) {
                JsonObject rotation = e.getAsJsonObject().getAsJsonObject("rotation");
                if (rotation == null) continue;
                assertTrue(VALID_ANGLES.contains(rotation.get("angle").getAsDouble()), name + ": 1.21.1 only takes 22.5 degree steps: " + rotation);
                assertTrue(rotation.has("axis"), name + ": one axis only: " + rotation);
            }
        }
        assertTrue(Files.isRegularFile(project("src/main/resources/assets/kegare/textures/item/string_braid.png")), "the texture");
    }

    /** StringsClient lays segments end to end, half a block apart: a longer or shorter model would gap or overlap. */
    @Test
    void theSegmentIsHalfABlockAlongZ() throws IOException {
        double min = Double.MAX_VALUE;
        double max = -Double.MAX_VALUE;
        for (JsonElement e : json("src/main/resources/assets/kegare/models/string/segment.json").getAsJsonArray("elements")) {
            min = Math.min(min, e.getAsJsonObject().getAsJsonArray("from").get(2).getAsDouble());
            max = Math.max(max, e.getAsJsonObject().getAsJsonArray("to").get(2).getAsDouble());
        }
        assertEquals(0.0, min, 0.1, "starts at z=0");
        assertEquals(8.0, max, 0.1, "ends at z=8");
    }

    @Test
    void theBlockbenchSourcesAreInTheRepo() throws IOException {
        for (String name : List.of("string_segment", "string_knot", "string_animations")) {
            JsonObject model = json("art/blockbench/" + name + ".bbmodel");
            assertNotNull(model.get("meta"), name + " is a Blockbench project");
            assertFalse(model.getAsJsonArray("elements").isEmpty(), name);
        }
        JsonArray animations = json("art/blockbench/string_animations.bbmodel").getAsJsonArray("animations");
        List<String> names = new ArrayList<>();
        for (JsonElement a : animations) names.add(a.getAsJsonObject().get("name").getAsString());
        for (ResourceLocation id : PLAYED) assertTrue(names.contains(id.getPath()), id + " is in the source");
    }

    @Test
    void palLoadsEveryAnimationTheCodePlays() throws IOException {
        JsonObject file = json("src/main/resources/assets/kegare/player_animations/puppet_strings.json");
        Map<String, Animation> loaded = UniversalAnimLoader.loadAnimations(file);
        for (ResourceLocation id : PLAYED) {
            Animation animation = loaded.get(id.getPath());
            assertNotNull(animation, id + " is in the file");
            assertTrue(animation.length() > 0f, id + " has a length");
            assertTrue(animation.boneAnimations().size() >= 3, id + " moves the body");
        }
    }

    /** The game blends these in and out in code, and that can be switched off: every channel starts and ends at rest so it never jumps to a mid-pose. */
    @Test
    void everyAnimationStartsAndEndsAtRest() throws IOException {
        JsonObject animations = json("src/main/resources/assets/kegare/player_animations/puppet_strings.json").getAsJsonObject("animations");
        for (ResourceLocation id : PLAYED) {
            JsonObject animation = animations.getAsJsonObject(id.getPath());
            double length = animation.get("animation_length").getAsDouble();
            for (Map.Entry<String, JsonElement> bone : animation.getAsJsonObject("bones").entrySet()) {
                for (Map.Entry<String, JsonElement> channel : bone.getValue().getAsJsonObject().entrySet()) {
                    String where = id.getPath() + " " + bone.getKey() + " " + channel.getKey();
                    assertEquals(0.0, magnitude(sample(channel.getValue().getAsJsonObject(), 0.0)), 1e-3, where + " at the start");
                    assertEquals(0.0, magnitude(sample(channel.getValue().getAsJsonObject(), length)), 1e-3, where + " at the end");
                }
            }
        }
    }

    /** Each variation's pose is part of its tell: at their peaks, no two have both arms in about the same place. */
    @Test
    void noTwoVariationsLookAlike() throws IOException {
        JsonObject animations = json("src/main/resources/assets/kegare/player_animations/puppet_strings.json").getAsJsonObject("animations");
        for (int i = 0; i < VARIATIONS.size(); i++) {
            for (int j = i + 1; j < VARIATIONS.size(); j++) {
                double difference = difference(animations.getAsJsonObject(VARIATIONS.get(i)), animations.getAsJsonObject(VARIATIONS.get(j)));
                assertTrue(difference > 30.0, VARIATIONS.get(i) + " and " + VARIATIONS.get(j) + " differ by only " + difference + " degrees");
            }
        }
    }

    /** The most their arms ever differ, over each one's length as a fraction. */
    private static double difference(JsonObject a, JsonObject b) {
        double most = 0.0;
        for (int step = 1; step < 20; step++) {
            double f = step / 20.0;
            double right = distance(arm(a, "right_arm", f), arm(b, "right_arm", f));
            double left = distance(arm(a, "left_arm", f), arm(b, "left_arm", f));
            most = Math.max(most, Math.max(right, left));
        }
        return most;
    }

    private static double[] arm(JsonObject animation, String bone, double fraction) {
        JsonObject b = animation.getAsJsonObject("bones").getAsJsonObject(bone);
        if (b == null || !b.has("rotation")) return new double[3];
        return sample(b.getAsJsonObject("rotation"), fraction * animation.get("animation_length").getAsDouble());
    }

    private static double distance(double[] a, double[] b) {
        return Math.sqrt((a[0] - b[0]) * (a[0] - b[0]) + (a[1] - b[1]) * (a[1] - b[1]) + (a[2] - b[2]) * (a[2] - b[2]));
    }

    private static double magnitude(double[] v) {
        return distance(v, new double[3]);
    }

    /** Linear between keys: close enough to compare poses. */
    private static double[] sample(JsonObject frames, double time) {
        double beforeTime = Double.NEGATIVE_INFINITY;
        double afterTime = Double.POSITIVE_INFINITY;
        double[] before = null;
        double[] after = null;
        for (Map.Entry<String, JsonElement> e : frames.entrySet()) {
            double t = Double.parseDouble(e.getKey());
            JsonArray post = e.getValue().getAsJsonObject().getAsJsonArray("post");
            double[] v = {post.get(0).getAsDouble(), post.get(1).getAsDouble(), post.get(2).getAsDouble()};
            if (t <= time && t > beforeTime) {
                beforeTime = t;
                before = v;
            }
            if (t >= time && t < afterTime) {
                afterTime = t;
                after = v;
            }
        }
        if (before == null) return after;
        if (after == null || afterTime == beforeTime) return before;
        double f = (time - beforeTime) / (afterTime - beforeTime);
        return new double[] {before[0] + (after[0] - before[0]) * f, before[1] + (after[1] - before[1]) * f, before[2] + (after[2] - before[2]) * f};
    }
}

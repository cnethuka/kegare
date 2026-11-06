package com.cnethuka.kegare.innate_technique.puppet_manipulation.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cnethuka.kegare.innate_technique.puppet_manipulation.PuppetIds;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.zigythebird.playeranimcore.animation.Animation;
import com.zigythebird.playeranimcore.loading.UniversalAnimLoader;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

/**
 * The shipped player_animations/puppet_manipulation.json (written by tools/anims): Player Animation Library's own loader
 * reads every animation the code plays, and {@link PuppetPoses} samples the spin the same way on the puppet.
 */
class PuppetAnimationsTest {
    private static final List<ResourceLocation> PLAYED = List.of(PuppetIds.ANIM_CREATE_CHARGE, PuppetIds.ANIM_CREATE_RELEASE,
            PuppetIds.ANIM_POSSESS_CAST, PuppetIds.ANIM_POSSESS_RETURN, PuppetIds.ANIM_DIRECTIVE_CAST, PuppetIds.ANIM_FOCUS_POINT,
            PuppetIds.ANIM_CURIO_BURST_CAST, PuppetIds.ANIM_CHAIN_BURST_CAST, PuppetIds.ANIM_UNDERSTUDY_CAST,
            PuppetIds.ANIM_STAND_IN_CAST, PuppetIds.ANIM_PUPPET_IDLE_SPIN);

    private static JsonObject file() throws IOException {
        Path path = Path.of(System.getProperty("kegare.projectDir", "."), "src/main/resources/assets/kegare/player_animations/puppet_manipulation.json");
        try (Reader reader = Files.newBufferedReader(path)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }

    @Test
    void palLoadsEveryAnimationTheCodePlays() throws IOException {
        Map<String, Animation> loaded = UniversalAnimLoader.loadAnimations(file());
        for (ResourceLocation id : PLAYED) {
            Animation animation = loaded.get(id.getPath());
            assertNotNull(animation, id + " is in the file");
            assertTrue(animation.length() > 0f, id + " has a length");
            assertTrue(animation.boneAnimations().size() >= 3, id + " moves the body");
        }
        Animation charge = loaded.get(PuppetIds.ANIM_CREATE_CHARGE.getPath());
        assertNotEquals(Animation.LoopType.LOOP, charge.loopType(), "the charge's fade-in plays once, then it loops from its loop point");
        assertNotEquals(Animation.LoopType.PLAY_ONCE, charge.loopType());
        assertEquals(Animation.LoopType.LOOP, loaded.get(PuppetIds.ANIM_PUPPET_IDLE_SPIN.getPath()).loopType());
        assertEquals(Animation.LoopType.PLAY_ONCE, loaded.get(PuppetIds.ANIM_DIRECTIVE_CAST.getPath()).loopType());
        Object beginTick = loaded.get(PuppetIds.ANIM_DIRECTIVE_CAST.getPath()).data().getNullable("beginTick");
        assertTrue(beginTick instanceof Number, "casts fade in from whatever the player was doing: " + beginTick);
        assertEquals(3.0, ((Number) beginTick).doubleValue(), 1e-6);
    }

    @Test
    void thePuppetSamplesTheSpin() throws IOException {
        JsonObject animations = file().getAsJsonObject("animations");
        PuppetPoses.Clip spin = PuppetPoses.parse(animations.getAsJsonObject(PuppetIds.ANIM_PUPPET_IDLE_SPIN.getPath()));
        assertEquals(0.9f, spin.length(), 1e-4f);
        assertEquals(0f, spin.rotation("body", 0f).y, 1e-3f);
        assertEquals(90f, spin.rotation("body", 0.225f).y, 1e-3f, "a quarter turn");
        assertEquals(135f, spin.rotation("body", 0.3375f).y, 1e-3f, "at constant speed");
        assertEquals(45f, spin.rotation("body", 0.9f + 0.1125f).y, 1e-3f, "and it loops");
        Vector3f arm = spin.rotation("right_arm", 0f);
        assertEquals(72f, arm.z, 1e-3f, "arms out");
        assertEquals(-72f, spin.rotation("left_arm", 0f).z, 1e-3f);
    }

    @Test
    void bothKeyframeStylesAreSampled() {
        JsonObject bedrock = JsonParser.parseString("""
                {"animation_length": 3, "bones": {"a": {"rotation": {
                    "0.0": {"post": [0, 0, 0], "lerp_mode": "catmullrom"},
                    "1": {"post": [10, 0, 0], "lerp_mode": "catmullrom"},
                    "2": {"post": [20, 0, 0], "lerp_mode": "step"},
                    "3": {"post": [40, 0, 0], "lerp_mode": "linear"}}}}}""").getAsJsonObject();
        PuppetPoses.Clip clip = PuppetPoses.parse(bedrock);
        assertEquals(10f, clip.rotation("a", 1f).x, 1e-3f, "passes through its keys");
        assertEquals(14.375f, clip.rotation("a", 1.5f).x, 1e-3f, "a Catmull-Rom curve between them");
        assertEquals(20f, clip.rotation("a", 2.5f).x, 1e-3f, "step holds until the next key");

        JsonObject gecko = JsonParser.parseString("""
                {"animation_length": 1, "bones": {"a": {"rotation": {
                    "0.0": {"vector": [0, 0, 0]},
                    "1": {"vector": [10, 0, 0], "easing": "easeInQuad"}}}}}""").getAsJsonObject();
        assertEquals(2.5f, PuppetPoses.parse(gecko).rotation("a", 0.5f).x, 1e-3f, "GeckoLib easings belong to the key a segment ends on");
    }
}

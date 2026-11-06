package com.cnethuka.kegare.melee.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cnethuka.kegare.melee.MeleeIds;
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
import org.junit.jupiter.api.Test;

/** The shipped player_animations/melee.json (written by tools/anims/melee.py), read by Player Animation Library's own loader. */
class MeleeAnimationsTest {
    private static final List<ResourceLocation> PLAYED = List.of(MeleeIds.ANIM_DASH_FORWARD, MeleeIds.ANIM_DASH_BACK, MeleeIds.ANIM_DASH_LEFT,
            MeleeIds.ANIM_DASH_RIGHT, MeleeIds.ANIM_PARRY_GUARD, MeleeIds.ANIM_PARRY_PERFECT, MeleeIds.ANIM_PARRY_RECOVERY, MeleeIds.ANIM_STAGGER,
            MeleeIds.ANIM_REINFORCED_STRIKE);

    private static JsonObject file() throws IOException {
        Path path = Path.of(System.getProperty("kegare.projectDir", "."), "src/main/resources/assets/kegare/player_animations/melee.json");
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
    }

    @Test
    void theGuardComesBackRightAfterThePerfectReaction() throws IOException {
        JsonObject perfect = file().getAsJsonObject("animations").getAsJsonObject(MeleeIds.ANIM_PARRY_PERFECT.getPath());
        assertEquals(MeleeIds.PARRY_PERFECT_TICKS / 20.0, perfect.get("animation_length").getAsDouble(), 1e-6, "the server restarts the guard pose when it ends");
    }

    @Test
    void theFilesDeclareNoFadesOfTheirOwn() throws IOException {
        JsonObject animations = file().getAsJsonObject("animations");
        for (String name : animations.keySet()) {
            assertFalse(animations.getAsJsonObject(name).has("beginTick"), name + ": PlayerAnimations does the fades");
            assertFalse(animations.getAsJsonObject(name).has("endTick"), name + ": PlayerAnimations does the fades");
        }
    }
}

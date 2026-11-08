package com.cnethuka.kegare.severed.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cnethuka.kegare.severed.SeveredIds;
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
import org.junit.jupiter.api.Test;

/** Severed's shipped animations (tools/anims/severed.py): PAL loads every one the code plays, each with exactly one first-person rule. */
class SeveredAnimationsTest {
    @Test
    void palLoadsEveryAnimationAndEachHasOneFirstPersonRule() throws IOException {
        Path path = Path.of(System.getProperty("kegare.projectDir", "."), "src/main/resources/assets/kegare/player_animations/severed.json");
        JsonObject file;
        try (Reader reader = Files.newBufferedReader(path)) {
            file = JsonParser.parseReader(reader).getAsJsonObject();
        }
        Map<String, Animation> loaded = UniversalAnimLoader.loadAnimations(file);
        for (ResourceLocation id : SeveredIds.ANIMATIONS) {
            Animation animation = loaded.get(id.getPath());
            assertNotNull(animation, id + " is in the file");
            assertTrue(animation.length() > 0f, id + " has a length");
            assertEquals(Animation.LoopType.PLAY_ONCE, animation.loopType(), id + " plays once");
            assertTrue(SeveredIds.FIRST_PERSON_VANILLA.contains(id) ^ SeveredIds.FIRST_PERSON_WHILE_LEVEL.contains(id), id + " has one rule");
        }
        assertEquals(SeveredIds.ANIMATIONS.size(), loaded.size(), "nothing in the file the code doesn't play");
    }
}

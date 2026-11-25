package com.cnethuka.kegare.dominion.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cnethuka.kegare.dominion.DominionIds;
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

/** Dominions' shipped animations (player_animations/dominion.json): Player Animation Library loads every one the code plays, each with its first-person rule. */
class DominionVisualsTest {
    private static JsonObject file() throws IOException {
        Path path = Path.of(System.getProperty("kegare.projectDir", "."), "src/main/resources/assets/kegare/player_animations/dominion.json");
        try (Reader reader = Files.newBufferedReader(path)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }

    @Test
    void palLoadsEveryAnimationTheCodePlays() throws IOException {
        Map<String, Animation> loaded = UniversalAnimLoader.loadAnimations(file());
        for (ResourceLocation id : DominionIds.ANIMATIONS) {
            Animation animation = loaded.get(id.getPath());
            assertNotNull(animation, id + " is in the file");
            assertTrue(animation.length() > 0f, id + " has a length");
            assertEquals(Animation.LoopType.PLAY_ONCE, animation.loopType(), id + " plays once");
            assertTrue(DominionIds.FIRST_PERSON_VANILLA.contains(id), id + " keeps your own first-person hands vanilla's");
        }
        assertEquals(DominionIds.ANIMATIONS.size(), loaded.size(), "nothing in the file the code doesn't play");
    }

    @Test
    void theIconIsThere() {
        Path icon = Path.of(System.getProperty("kegare.projectDir", "."), "src/main/resources/assets/kegare/textures/gui/ability/incomplete_dominion.png");
        assertTrue(Files.isRegularFile(icon), "the Incomplete Dominion's icon");
    }
}

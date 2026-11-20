package com.cnethuka.kegare.rmt.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cnethuka.kegare.rmt.RmtIds;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.zigythebird.playeranimcore.animation.Animation;
import com.zigythebird.playeranimcore.loading.UniversalAnimLoader;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

/** Reverse Malice Technique's white flame sheet its light is drawn from, and its two poses: the fist held to the chest, the hand reaching out. */
class RmtArtTest {
    private static final Path ASSETS = Path.of(System.getProperty("kegare.projectDir", "."), "src/main/resources/assets/kegare");
    private static final int CELL = 16;
    private static final int COLUMNS = 8;
    private static final int ROWS = 2;

    private static BufferedImage licks() throws IOException {
        return ImageIO.read(ASSETS.resolve("textures/fx/rmt_licks.png").toFile());
    }

    private static JsonObject poseFile() throws IOException {
        try (Reader reader = Files.newBufferedReader(ASSETS.resolve("player_animations/rmt.json"))) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }

    @Test
    void theLicksArePixelArtWithHardEdgesInGreysOnly() throws IOException {
        BufferedImage sheet = licks();
        assertEquals(COLUMNS * CELL, sheet.getWidth());
        assertEquals(ROWS * CELL, sheet.getHeight());
        for (int y = 0; y < sheet.getHeight(); y++) {
            for (int x = 0; x < sheet.getWidth(); x++) {
                int argb = sheet.getRGB(x, y);
                int a = argb >>> 24, r = argb >> 16 & 0xFF, g = argb >> 8 & 0xFF, b = argb & 0xFF;
                assertTrue(a == 0 || a == 255, "no soft alpha at " + x + "," + y);
                if (a == 255) {
                    assertEquals(r, g, "grey at " + x + "," + y);
                    assertEquals(g, b, "grey at " + x + "," + y);
                }
            }
        }
    }

    private static String frame(BufferedImage sheet, int column, int row) {
        StringBuilder out = new StringBuilder();
        for (int y = row * CELL; y < (row + 1) * CELL; y++) {
            for (int x = column * CELL; x < (column + 1) * CELL; x++) out.append(Integer.toHexString(sheet.getRGB(x, y))).append(',');
        }
        return out.toString();
    }

    /** Photon steps each particle through one row: every frame has flame in it, mostly white, and each differs from the next, so it flickers. */
    @Test
    void everyFrameBurnsWhiteAndFlickers() throws IOException {
        BufferedImage sheet = licks();
        for (int row = 0; row < ROWS; row++) {
            for (int column = 0; column < COLUMNS; column++) {
                int lit = 0, bright = 0;
                for (int y = row * CELL; y < (row + 1) * CELL; y++) {
                    for (int x = column * CELL; x < (column + 1) * CELL; x++) {
                        int argb = sheet.getRGB(x, y);
                        if (argb >>> 24 == 0) continue;
                        lit++;
                        if ((argb & 0xFF) >= 190) bright++;
                    }
                }
                assertTrue(lit > 0, "row " + row + " frame " + column + " has flame");
                assertTrue(bright * 2 > lit, "row " + row + " frame " + column + " is mostly white");
                assertNotEquals(frame(sheet, column, row), frame(sheet, (column + 1) % COLUMNS, row), "row " + row + " frame " + column + " flickers");
            }
        }
    }

    @Test
    void palLoadsBothPosesAsLoops() throws IOException {
        Map<String, Animation> loaded = UniversalAnimLoader.loadAnimations(poseFile());
        for (var id : java.util.List.of(RmtIds.ANIM_CHANNEL, RmtIds.ANIM_ON_ANOTHER)) {
            Animation pose = loaded.get(id.getPath());
            assertNotNull(pose, id + " is in the file");
            assertTrue(pose.length() > 0f);
            assertNotEquals(Animation.LoopType.PLAY_ONCE, pose.loopType(), id + " is held for as long as the channel runs");
        }
    }

    /**
     * Only the right arm moves: the head keeps the aim, the left arm and the legs keep walking. It starts from rest (the game blends it in
     * and out, so the file declares no fades of its own).
     */
    @Test
    void onlyTheRightArmMovesFromRest() throws IOException {
        JsonObject animations = poseFile().getAsJsonObject("animations");
        assertEquals(Set.of(RmtIds.ANIM_CHANNEL.getPath(), RmtIds.ANIM_ON_ANOTHER.getPath()), animations.keySet());
        for (String name : animations.keySet()) {
            JsonObject pose = animations.getAsJsonObject(name);
            assertEquals(Set.of("right_arm"), pose.getAsJsonObject("bones").keySet(), name);
            assertFalse(pose.has("beginTick"), name + ": PlayerAnimations does the fades");
            assertFalse(pose.has("endTick"), name + ": PlayerAnimations does the fades");
            JsonObject arm = pose.getAsJsonObject("bones").getAsJsonObject("right_arm");
            for (String channel : arm.keySet()) {
                JsonArray first = arm.getAsJsonObject(channel).getAsJsonObject("0.0").getAsJsonArray("post");
                for (int i = 0; i < 3; i++) assertEquals(0.0, first.get(i).getAsDouble(), 1e-9, name + " " + channel + " starts at rest");
            }
        }
    }
}

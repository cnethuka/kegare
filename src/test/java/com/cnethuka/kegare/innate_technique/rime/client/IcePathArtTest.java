package com.cnethuka.kegare.innate_technique.rime.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cnethuka.kegare.innate_technique.rime.RimeIds;
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
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

/**
 * Ice Path's look: the pixel ice laid under the slider (a band as wide as the trail that slows, shining one way, melting when the
 * trail does) and the slide's pose. Visual only: nothing here touches what the slide does.
 */
class IcePathArtTest {
    /** The trail's slowing reach either side of its middle, by default (ice_path.trailRadius). */
    private static final double TRAIL_RADIUS = 0.9;

    private static Path project(String path) {
        return Path.of(System.getProperty("kegare.projectDir", "."), path);
    }

    private static BufferedImage mark(String name) throws IOException {
        return ImageIO.read(project("src/main/resources/assets/kegare/textures/marks/" + name + ".png").toFile());
    }

    private static JsonObject json(String path) throws IOException {
        try (Reader reader = Files.newBufferedReader(project(path))) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }

    // ------------------------------------------------------------------------------------------ the ice

    /** Each patch reaches about as far from the slider's path as the trail slows, so the band shows where it's slippery. */
    @Test
    void aPatchIsAsWideAsTheTrail() throws IOException {
        for (String name : new String[] {"path_ice", "path_ice_b"}) {
            BufferedImage art = mark(name);
            int c = art.getWidth() / 2;
            double furthest = 0;
            int glaze = 0;
            for (int y = 0; y < art.getHeight(); y++) {
                for (int x = 0; x < art.getWidth(); x++) {
                    int argb = art.getRGB(x, y);
                    if (argb >>> 24 == 0) continue;
                    furthest = Math.max(furthest, Math.hypot(x + 0.5 - c, y + 0.5 - c));
                    if (Math.hypot(x + 0.5 - c, y + 0.5 - c) < 8) glaze++;
                }
            }
            double blocks = furthest / BlockMarks.TEXELS;
            assertTrue(Math.abs(blocks - TRAIL_RADIUS) < 0.2, name + " reaches " + blocks + " blocks, the trail " + TRAIL_RADIUS);
            assertTrue(glaze > Math.PI * 64 * 0.9, name + " is a glaze over the block, not a scatter: " + glaze);
        }
    }

    /** Laid closer together than a patch is wide, the band never breaks while the slider keeps moving. */
    @Test
    void thePatchesRunTogether() {
        assertTrue(PathClient.SPACING < 0.5, "a patch every half a block at most");
        double smallest = TRAIL_RADIUS * PathClient.REACH_MIN;
        assertTrue(PathClient.SPACING < smallest, "each bit of the path under two patches, even at the smallest reach");
        assertTrue(0.65 < 2 * smallest, "at full slide (0.65 blocks a tick, one patch a tick) they still touch");
        assertTrue(PathClient.WANDER < 0.2, "the edges wander a little, the band stays on the path");
    }

    /** The shine is short streaks on one diagonal: every way a patch is laid keeps that diagonal, so the whole band shines one way. */
    @Test
    void theShineIsOneWay() {
        for (int t : PathClient.TURNS) {
            int[] a = BlockMarks.turn(10, 12, 32, 32, t), b = BlockMarks.turn(11, 11, 32, 32, t);
            int dx = b[0] - a[0], dy = b[1] - a[1];
            assertEquals(-1, dx * dy, "turn " + t + " moves the streak off its diagonal");
        }
        for (int n = 0; n < 16; n++) {
            int t = PathClient.turn(n);
            assertTrue(t == 0 || t == 3 || t == 4 || t == 7, "patch " + n + " turned " + t);
        }
    }

    /** It forms in a few ticks, holds, and melts from the edge in about when the server's trail there expires: no sooner, no later. */
    @Test
    void itMeltsWhenTheTrailExpires() {
        int trail = 80;
        BlockMarks.Timing timing = PathClient.timing(trail, 1f);
        assertTrue(timing.appear(255) <= 4, "formed in a few ticks");
        assertEquals(trail - PathClient.MELT / 2, timing.vanish(255, 0), "its edge goes first, a little before the trail's end");
        assertEquals(trail + PathClient.MELT / 2, timing.vanish(0, 0), "its middle last, a little after");
        assertTrue(PathClient.MELT <= 30, "within about a second and a half either side");
    }

    // ------------------------------------------------------------------------------------------ the body

    @Test
    void palLoadsTheSlide() throws IOException {
        Map<String, Animation> loaded = UniversalAnimLoader.loadAnimations(json("src/main/resources/assets/kegare/player_animations/ice_path.json"));
        Animation slide = loaded.get(RimeIds.ANIM_ICE_PATH_SLIDE.getPath());
        assertNotNull(slide);
        assertNotEquals(Animation.LoopType.PLAY_ONCE, slide.loopType(), "held for as long as the slide");
        JsonObject bones = json("src/main/resources/assets/kegare/player_animations/ice_path.json").getAsJsonObject("animations")
                .getAsJsonObject(RimeIds.ANIM_ICE_PATH_SLIDE.getPath()).getAsJsonObject("bones");
        for (String bone : new String[] {"right_leg", "left_leg", "right_arm", "left_arm", "torso"}) {
            assertTrue(bones.has(bone), "the slide keys the " + bone + " (a slide isn't walking)");
        }
        assertFalse(json("src/main/resources/assets/kegare/player_animations/rime.json").getAsJsonObject("animations")
                .has(RimeIds.ANIM_ICE_PATH_SLIDE.getPath()), "one source for it");
    }
}

package com.cnethuka.kegare.dominion.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cnethuka.kegare.dominion.DominionIds;
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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

/**
 * Dominions' shipped art (DESIGN §3.35): the animation (Player Animation Library loads every one the code plays, each with its
 * first-person rule), the icon and the wasteland as hard pixel art, the cracked shell smooth and high-res, and where a clash's seam goes.
 */
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

    private static Path asset(String path) {
        return Path.of(System.getProperty("kegare.projectDir", "."), "src/main/resources/assets/kegare/" + path);
    }

    @Test
    void theIconIsThere() throws IOException {
        BufferedImage icon = ImageIO.read(asset("textures/gui/ability/incomplete_dominion.png").toFile());
        assertEquals(16, icon.getWidth(), "16 x 16, like every ability's");
        assertEquals(16, icon.getHeight());
        hardPixels(icon, "the icon");
    }

    /** Pixel art: every pixel there or not, nothing half see-through (CLAUDE.md's visual rules). */
    private static void hardPixels(BufferedImage image, String what) {
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int a = image.getRGB(x, y) >>> 24;
                assertTrue(a == 0 || a == 255, what + ": a soft pixel at " + x + "," + y);
            }
        }
    }

    @Test
    void theWastelandIsPixelArtSixteenABlock() throws IOException {
        BufferedImage ground = ImageIO.read(asset("textures/marks/wasteland.png").toFile());
        assertEquals(16, ground.getHeight(), "one block high");
        assertEquals(64, ground.getWidth(), "four tiles side by side");
        hardPixels(ground, "the ground");
        for (int y = 0; y < 16; y++) for (int x = 0; x < 64; x++) assertEquals(255, ground.getRGB(x, y) >>> 24, "the ground covers its face");
        BufferedImage ruin = ImageIO.read(asset("textures/marks/ruin_stone.png").toFile());
        assertEquals(16, ruin.getHeight());
        assertEquals(32, ruin.getWidth(), "two tiles");
        hardPixels(ruin, "the ruins");
        // No Malice color: the wasteland is grey, whoever opens it.
        for (BufferedImage image : new BufferedImage[] {ground, ruin}) {
            for (int y = 0; y < image.getHeight(); y++) {
                for (int x = 0; x < image.getWidth(); x++) {
                    int rgb = image.getRGB(x, y), r = rgb >> 16 & 0xFF, g = rgb >> 8 & 0xFF, b = rgb & 0xFF;
                    assertTrue(Math.max(r, Math.max(g, b)) - Math.min(r, Math.min(g, b)) <= 16, "grey, not colored: " + Integer.toHexString(rgb));
                }
            }
        }
    }

    @Test
    void theCrackedShellIsSmoothAndHighRes() throws IOException {
        for (String name : new String[] {"shell_cracked", "shell_cracked_faint"}) {
            BufferedImage shell = ImageIO.read(asset("textures/entity/dominion/" + name + ".png").toFile());
            assertTrue(shell.getWidth() >= 1024 && shell.getHeight() >= 512, name + " is high-res");
            String meta = Files.readString(asset("textures/entity/dominion/" + name + ".png.mcmeta"));
            assertTrue(JsonParser.parseString(meta).getAsJsonObject().getAsJsonObject("texture").get("blur").getAsBoolean(),
                    name + " is drawn smooth (blur), not by hard pixels");
        }
    }

    @Test
    void aClashsSeamIsWhereTheTwoShellsMeet() {
        Vec3 a = new Vec3(0, 0, 0), b = new Vec3(10, 0, 0);
        double[] m = DominionRenderer.meeting(a, 10, b, 10).orElseThrow();
        assertEquals(5, m[0], 1.0e-9, "half way between two of a size");
        assertEquals(1, m[3], 1.0e-9, "facing along the line between them");
        assertEquals(Math.sqrt(75), m[6], 1.0e-9, "its radius");
        Vec3 onIt = new Vec3(m[0], m[1] + m[6], m[2]);
        assertEquals(10, onIt.distanceTo(a), 1.0e-9, "on the one shell");
        assertEquals(10, onIt.distanceTo(b), 1.0e-9, "and on the other");
        assertTrue(DominionRenderer.meeting(a, 4, new Vec3(20, 0, 0), 4).isEmpty(), "apart: nothing");
        assertTrue(DominionRenderer.meeting(a, 10, new Vec3(1, 0, 0), 2).isEmpty(), "one inside the other: nothing");
    }
}

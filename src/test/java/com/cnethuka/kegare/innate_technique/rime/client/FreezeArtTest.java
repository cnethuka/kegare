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
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

/**
 * Freeze's look on a body: frost as pixel art on the skin's own texels, creeping up from the feet and hands as the stacks build, all
 * of it frozen solid, and the frozen pose. Visual only: nothing here touches what Freeze does.
 */
class FreezeArtTest {
    /** The outer layer's boxes in the 64x64 skin: (u, v, w, h, d) for wide arms. */
    private static final int[][] OUTER = {{0, 32, 4, 12, 4}, {0, 48, 4, 12, 4}, {40, 32, 4, 12, 4}, {48, 48, 4, 12, 4},
            {16, 32, 8, 12, 4}, {32, 0, 8, 8, 8}};
    /** The inner layer's: never painted, it's under the outer one. */
    private static final int[][] INNER = {{0, 16, 4, 12, 4}, {16, 48, 4, 12, 4}, {40, 16, 4, 12, 4}, {32, 48, 4, 12, 4},
            {16, 16, 8, 12, 4}, {0, 0, 8, 8, 8}};

    private static Path project(String path) {
        return Path.of(System.getProperty("kegare.projectDir", "."), path);
    }

    private static JsonObject json(String path) throws IOException {
        try (Reader reader = Files.newBufferedReader(project(path))) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }

    private static FreezeClient.Art art(String name) throws IOException {
        String dir = "src/main/resources/assets/kegare/textures/entity/";
        BufferedImage art = ImageIO.read(project(dir + name + ".png").toFile());
        BufferedImage when = ImageIO.read(project(dir + name + "_order.png").toFile());
        assertEquals(64, art.getWidth(), "the skin's own size");
        assertEquals(64, art.getHeight());
        int[] argb = new int[64 * 64], order = new int[64 * 64];
        for (int y = 0; y < 64; y++) {
            for (int x = 0; x < 64; x++) {
                int c = art.getRGB(x, y);
                argb[y * 64 + x] = c >>> 24 == 0 ? 0 : c;
                order[y * 64 + x] = when.getRaster().getSample(x, y, 0);
            }
        }
        return new FreezeClient.Art(argb, order);
    }

    /** Whether a texel is on one of a box's six faces. */
    private static boolean onBox(int x, int y, int[] b) {
        int u = b[0], v = b[1], w = b[2], h = b[3], d = b[4];
        boolean caps = y >= v && y < v + d && x >= u + d && x < u + d + 2 * w;
        boolean sides = y >= v + d && y < v + d + h && x >= u && x < u + 2 * d + 2 * w;
        return caps || sides;
    }

    private static double meanOrder(FreezeClient.Art a, int[] box, int rowFrom, int rowTo) {
        double sum = 0;
        int n = 0;
        int v0 = box[1] + box[4];
        for (int y = v0 + rowFrom; y < v0 + rowTo; y++) {
            for (int x = box[0]; x < box[0] + 2 * box[4] + 2 * box[2]; x++) {
                sum += a.order()[y * 64 + x];
                n++;
            }
        }
        return sum / n;
    }

    // ------------------------------------------------------------------------------------------ the art

    /** Pixel art on the skin's texels, like frost on a block: no filtering, a few flat tones, on the outer layer only. */
    @Test
    void theFrostIsPixelArtOnTheOuterLayer() throws IOException {
        for (String name : new String[] {"freeze_frost", "freeze_frost_slim"}) {
            FreezeClient.Art a = art(name);
            assertFalse(Files.exists(project("src/main/resources/assets/kegare/textures/entity/" + name + ".png.mcmeta")),
                    "hard pixels: never blurred");
            Set<Integer> tones = new HashSet<>();
            int painted = 0;
            for (int y = 0; y < 64; y++) {
                for (int x = 0; x < 64; x++) {
                    int i = y * 64 + x;
                    boolean outer = false, inner = false;
                    for (int[] b : OUTER) outer |= onBox(x, y, b);
                    for (int[] b : INNER) inner |= onBox(x, y, b);
                    if (inner && !outer) {
                        assertEquals(0, a.argb()[i], name + ": painted under the outer layer at " + x + "," + y);
                        assertEquals(0, a.order()[i], name + ": ordered under the outer layer at " + x + "," + y);
                    }
                    if (a.argb()[i] != 0) {
                        tones.add(a.argb()[i]);
                        painted++;
                        assertTrue(a.order()[i] > 0, name + ": frost that never shows at " + x + "," + y);
                    }
                }
            }
            assertTrue(tones.size() <= 6, name + ": a few flat tones, not a gradient: " + tones.size());
            assertTrue(painted > 900, name + ": frost all over the body: " + painted);
        }
    }

    /** Up from the soles and the hands, the limbs, then the body, the head last. */
    @Test
    void itCreepsUpFromTheFeetAndHands() throws IOException {
        FreezeClient.Art a = art("freeze_frost");
        int[] leg = OUTER[0], arm = OUTER[2], body = OUTER[4], head = OUTER[5];
        assertTrue(meanOrder(a, leg, 9, 12) < meanOrder(a, leg, 0, 3), "the feet before the hips");
        assertTrue(meanOrder(a, arm, 9, 12) < meanOrder(a, arm, 0, 3), "the hands before the shoulders");
        assertTrue(meanOrder(a, leg, 0, 3) < meanOrder(a, body, 0, 3), "the legs before the chest");
        assertTrue(meanOrder(a, body, 9, 12) < meanOrder(a, body, 0, 3), "the waist before the chest");
        assertTrue(meanOrder(a, body, 0, 12) < meanOrder(a, head, 0, 8), "the head last");
        // One stack of five: some of the legs and hands, none of the head.
        int[] one = FreezeClient.frost(a, FreezeClient.quantize(1 / 5f), false);
        for (int y = head[1]; y < head[1] + 2 * head[4]; y++) {
            for (int x = head[0]; x < head[0] + 4 * head[2]; x++) assertEquals(0, one[y * 64 + x], "frost on the head at one stack");
        }
        long legs = 0;
        for (int y = leg[1] + leg[4]; y < leg[1] + leg[4] + leg[3]; y++) {
            for (int x = leg[0]; x < leg[0] + 16; x++) legs += one[y * 64 + x] != 0 ? 1 : 0;
        }
        assertTrue(legs > 20, "the feet frosting over at one stack: " + legs);
    }

    /** Each stack shows more, every texel once it's all over, a rim of rime on the creeping edge, all of it glazed frozen solid. */
    @Test
    void itGrowsWithEachStack() throws IOException {
        FreezeClient.Art a = art("freeze_frost");
        int last = -1;
        for (int step = 0; step <= FreezeClient.STEPS; step++) {
            int[] px = FreezeClient.frost(a, step, false);
            int shown = 0;
            for (int c : px) shown += c != 0 ? 1 : 0;
            assertTrue(shown >= last, "only grows: step " + step);
            last = shown;
        }
        int painted = 0;
        for (int c : a.argb()) painted += c != 0 ? 1 : 0;
        assertEquals(painted, last, "all of it at the end");
        int[] half = FreezeClient.frost(a, FreezeClient.STEPS / 2, false);
        int rim = 0;
        for (int c : half) rim += c == FreezeClient.RIME ? 1 : 0;
        assertTrue(rim > 20, "a rim of rime on the edge: " + rim);
        int[] frozen = FreezeClient.frost(a, FreezeClient.STEPS, true);
        int[] most = FreezeClient.frost(a, FreezeClient.STEPS, false);
        int solid = 0, outer = 0;
        for (int i = 0; i < frozen.length; i++) {
            if (a.order()[i] == 0) continue;
            outer++;
            assertNotEquals(0, frozen[i], "the whole outer layer iced frozen solid");
            solid += frozen[i] == FreezeClient.SOLID ? 1 : 0;
            if (frozen[i] != most[i]) assertTrue(frozen[i] >>> 24 > most[i] >>> 24, "frozen solid is thicker, never thinner");
        }
        assertTrue(solid > outer / 3, "frozen solid looks plainly unlike the most frost: " + solid + " of " + outer);
        int glaze = 0;
        for (int c : a.argb()) glaze += c == FreezeClient.GLAZE ? 1 : 0;
        assertTrue(glaze > 0, "the art's glaze is the tone FreezeClient thickens");
        assertEquals(0, FreezeClient.quantize(0f));
        assertEquals(1, FreezeClient.quantize(0.001f), "any frost at all shows");
        assertEquals(FreezeClient.STEPS, FreezeClient.quantize(1f));
    }

    /** It creeps to a new stack in about half a second, melts back slower, and covers the body fast when it freezes solid. */
    @Test
    void itCreepsAndMelts() {
        float shown = 0f;
        int ticks = 0;
        while (shown < 0.2f) {
            shown = FreezeClient.step(shown, 0.2f, false);
            ticks++;
        }
        assertEquals(0.2f, shown, 1e-6f, "never past the stacks");
        assertTrue(ticks >= 6 && ticks <= 12, "a stack's frost creeps in over about half a second: " + ticks);
        int melt = 0;
        while (shown > 0f) {
            shown = FreezeClient.step(shown, 0f, false);
            melt++;
        }
        assertTrue(melt > ticks, "melting is slower than freezing");
        int solid = 0;
        for (shown = 0.8f; shown < 1f; solid++) shown = FreezeClient.step(shown, 1f, true);
        assertTrue(solid <= 3, "frozen solid at once");
    }

    /** A faint glow, as Ice Path's ice has: the stacks read on a body at night, but the body doesn't look lit. */
    @Test
    void itGlowsFaintlyAtNight() {
        assertTrue(FreezeClient.GLOW >= 4 && FreezeClient.GLOW <= 7, "faint: " + FreezeClient.GLOW);
    }

    /**
     * Over armour the frost lies just past it on every part, so the stacks can still be counted in full armour; and a slim arm's frost
     * is stretched across to a chestplate's arm, which is a wide one, from its inner side.
     */
    @Test
    void overArmourTheFrostLiesJustPastIt() {
        float[] full = ArmourReach.of(true, true, true, true);
        for (int part = 0; part < 6; part++) {
            float armour = full[part], out = FreezeClient.overArmour(armour);
            assertTrue(out > armour && out <= armour + 0.5f, "just past the armour on part " + part + ": " + out);
            float layer = FreezeClient.meshInflation(part, armour) + (part == ArmourReach.HEAD ? 0.5f : 0.25f);
            assertEquals(out, layer, 1e-6f, "the player mesh's outer layer lands there on part " + part);
        }
        float out = FreezeClient.overArmour(1f);
        for (boolean right : new boolean[] {true, false}) {
            float[] w = FreezeClient.widen(right, out);
            // The slim arm at that inflation, from its own x; the wide one it has to cover.
            float slimFrom = right ? -2f - out : -1f - out, slimTo = right ? 1f + out : 2f + out;
            float wideFrom = right ? -3f - out : -1f - out, wideTo = right ? 1f + out : 3f + out;
            float from = w[0] + (slimFrom - w[0]) * w[1], to = w[0] + (slimTo - w[0]) * w[1];
            assertEquals(wideFrom, from, 1e-4f, (right ? "right" : "left") + " arm's outside");
            assertEquals(wideTo, to, 1e-4f, (right ? "right" : "left") + " arm's inside");
        }
    }

    @Test
    void everyStackCountHasItsOwnLoop() {
        Set<String> keys = new HashSet<>();
        for (int n = 1; n <= 32; n++) assertTrue(keys.add(RimeIds.freezeStackKey(n)));
        assertFalse(keys.contains(RimeIds.FROZEN_LOOP_KEY), "the stacks' loops and the frozen one are separate");
    }

    // ------------------------------------------------------------------------------------------ the body

    @Test
    void palLoadsTheFrozenPose() throws IOException {
        String path = "src/main/resources/assets/kegare/player_animations/freeze.json";
        Map<String, Animation> loaded = UniversalAnimLoader.loadAnimations(json(path));
        Animation frozen = loaded.get(RimeIds.ANIM_FROZEN.getPath());
        assertNotNull(frozen);
        assertNotEquals(Animation.LoopType.PLAY_ONCE, frozen.loopType(), "held while frozen");
        assertNotEquals(Animation.LoopType.LOOP, frozen.loopType(), "the seize plays once, then it holds from its loop point");
        JsonObject bones = json(path).getAsJsonObject("animations").getAsJsonObject(RimeIds.ANIM_FROZEN.getPath())
                .getAsJsonObject("bones");
        for (String bone : new String[] {"right_arm", "left_arm", "torso", "head"}) assertTrue(bones.has(bone), "it keys the " + bone);
        assertFalse(RimeJson.has(RimeIds.ANIM_FROZEN.getPath()), "one source for it");
        assertFalse(Files.exists(RimeJson.PATH), "every Rime animation has its own file now");
    }
}

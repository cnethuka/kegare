package com.cnethuka.kegare.innate_technique.rime.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.blaze3d.vertex.VertexConsumer;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import javax.imageio.ImageIO;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

/**
 * Rime's marks on blocks (tools/blockbench/build_block_marks.py writes the patterns, BlockMarks lays them): pixel art at the blocks'
 * own 16 texels a block, hard pixels, drawn exactly on the block's texel grid, and every texel melting away on its own.
 */
class BlockMarksTest {
    private static Path marks() {
        return Path.of(System.getProperty("kegare.projectDir", "."), "src/main/resources/assets/kegare/textures/marks");
    }

    private static List<String> patterns() throws IOException {
        try (Stream<Path> files = Files.list(marks())) {
            return files.map(p -> p.getFileName().toString()).filter(n -> n.endsWith("_order.png"))
                    .map(n -> n.substring(0, n.length() - "_order.png".length())).sorted().toList();
        }
    }

    // ------------------------------------------------------------------------------------------ the patterns

    /** Hard pixels: a handful of palette colours, never blended or smoothed, and nothing filters them soft. */
    @Test
    void everyPatternIsPixelArt() throws IOException {
        List<String> names = patterns();
        assertTrue(names.contains("lance_frost"), "the Lance's frost is there: " + names);
        Set<Integer> palette = new HashSet<>();
        for (String name : names) {
            BufferedImage art = ImageIO.read(marks().resolve(name + ".png").toFile());
            BufferedImage order = ImageIO.read(marks().resolve(name + "_order.png").toFile());
            assertEquals(art.getWidth(), order.getWidth(), name);
            assertEquals(art.getHeight(), order.getHeight(), name);
            assertTrue(art.getWidth() >= 16 && art.getWidth() <= 512, name + " is drawn at 16 texels a block, not finer");
            int drawn = 0;
            for (int y = 0; y < art.getHeight(); y++) {
                for (int x = 0; x < art.getWidth(); x++) {
                    int c = art.getRGB(x, y);
                    if (c >>> 24 == 0) continue;
                    palette.add(c);
                    drawn++;
                }
            }
            assertTrue(drawn > 50, name + " draws something");
            assertFalse(Files.exists(marks().resolve(name + ".png.mcmeta")), name + " must stay nearest-filtered");
        }
        assertTrue(palette.size() <= 10, "a small palette across all the marks, not blended shades: " + palette.size());
    }

    /**
     * Glow is a minimum light for a texel, so a mark reads in the dark: only where something's drawn, in whole levels. Ice Path's ice
     * glows faintly all over (it slows whoever steps on it, so it must be seen at night) but never like a light source; the frost
     * and ice of the rest are lit by the world alone.
     */
    @Test
    void glowIsFaintAndOnlyWhereItShouldBe() throws IOException {
        for (String name : patterns()) {
            Path file = marks().resolve(name + "_glow.png");
            boolean expected = name.startsWith("path_ice") || name.equals("spire_crack");
            assertEquals(expected, Files.exists(file), name + (expected ? " has no glow" : " should be lit by the world alone"));
            if (!expected) continue;
            BufferedImage art = ImageIO.read(marks().resolve(name + ".png").toFile());
            BufferedImage glow = ImageIO.read(file.toFile());
            for (int y = 0; y < art.getHeight(); y++) {
                for (int x = 0; x < art.getWidth(); x++) {
                    int g = glow.getRaster().getSample(x, y, 0);
                    assertEquals(0, g % 17, name + ": glow in whole levels");
                    boolean drawn = art.getRGB(x, y) >>> 24 > 0;
                    if (!drawn) assertEquals(0, g, name + ": glow where nothing's drawn");
                    if (name.startsWith("path_ice") && drawn) {
                        assertTrue(g / 17 >= 4 && g / 17 <= 7, name + ": a faint glow all over, not a light: " + g / 17);
                    }
                }
            }
        }
        assertEquals(0x00F000F0, BlockMarks.lit(0x00F000F0, 9), "brighter than the glow already: as it is");
        assertEquals(LightTexture.pack(9, 2), BlockMarks.lit(LightTexture.pack(3, 2), 9), "dark: up to the glow, the sky's kept");
        assertEquals(LightTexture.pack(3, 2), BlockMarks.lit(LightTexture.pack(3, 2), 0), "no glow: the world's light");
    }

    /** The inner part forms first: the middle of a pattern is early in its order, its far edge late. */
    @Test
    void aPatternGrowsFromItsMiddle() throws IOException {
        BufferedImage art = ImageIO.read(marks().resolve("lance_frost.png").toFile());
        BufferedImage order = ImageIO.read(marks().resolve("lance_frost_order.png").toFile());
        int cx = art.getWidth() / 2, cy = art.getHeight() / 2;
        double near = 0, far = 0;
        int nn = 0, nf = 0;
        for (int y = 0; y < art.getHeight(); y++) {
            for (int x = 0; x < art.getWidth(); x++) {
                if (art.getRGB(x, y) >>> 24 == 0) continue;
                double d = Math.hypot(x - cx, y - cy);
                int o = order.getRGB(x, y) & 0xFF;
                if (d < 4) {
                    near += o;
                    nn++;
                } else if (d > 16) {
                    far += o;
                    nf++;
                }
            }
        }
        assertTrue(nn > 0 && nf > 0);
        assertTrue(near / nn < 80 && far / nf > 150, "middle " + near / nn + ", edge " + far / nf);
    }

    // ------------------------------------------------------------------------------------------ on the blocks' grid

    /** Every quad drawn has its corners on the block's own texel grid (sixteenths), just off the face it's on. */
    @Test
    void marksAreDrawnOnTheBlocksTexelGrid() {
        int now = BlockMarks.clock();
        for (Direction face : Direction.values()) {
            BlockMarks.Face f = new BlockMarks.Face(new BlockPos(5, 64, -9), face);
            for (int i = 0; i < 256; i += 3) f.put(i, 0xC0D0E0FF - (i % 2) * 0x100, now - 10, now + 100, now);
            List<float[]> corners = new ArrayList<>();
            BlockMarks.drawFace(recorder(corners), Vec3.ZERO, f, 0xF000F0, 0.8f);
            assertFalse(corners.isEmpty(), face.toString());
            assertEquals(0, corners.size() % 4, "quads");
            Direction.Axis n = face.getAxis();
            double plane = f.solid.get(n) + (face.getAxisDirection().getStep() > 0 ? 1 : 0);
            for (float[] c : corners) {
                for (Direction.Axis axis : Direction.Axis.values()) {
                    double v = c[axis.ordinal()];
                    if (axis == n) {
                        assertEquals(plane, v, 0.02, face + ": on its face");
                        assertTrue(Math.abs(v - plane) > 0.001, face + ": lifted off it");
                        assertEquals(face.getAxisDirection().getStep(), Math.signum(v - plane), 0.0, face + ": on the open side");
                    } else {
                        assertEquals(Math.round(v * 16), v * 16, 1e-3, face + " " + axis + ": off the texel grid at " + v);
                        assertTrue(v >= f.solid.get(axis) - 1e-4 && v <= f.solid.get(axis) + 1 + 1e-4, "inside its block");
                    }
                }
            }
        }
    }

    /** Turning a pattern any of the eight ways moves every texel to its own place: nothing doubled up, nothing lost. */
    @Test
    void turningKeepsEveryTexel() {
        for (int variant = 0; variant < 8; variant++) {
            Set<Long> seen = new HashSet<>();
            for (int y = 0; y < 64; y++) {
                for (int x = 0; x < 64; x++) {
                    int[] d = BlockMarks.turn(x, y, 64, 64, variant);
                    assertTrue(Math.abs(d[0]) <= 32 && Math.abs(d[1]) <= 32);
                    assertTrue(seen.add((long) d[0] << 32 ^ (d[1] & 0xFFFFFFFFL)), "variant " + variant);
                }
            }
        }
    }

    /** A mark on a face sits on the block behind it, whichever way the face points. */
    @Test
    void aMarkIsOnTheBlockBehindItsFace() {
        assertEquals(99, BlockMarks.behind(new Vec3(3.3, 100.0, 7.1), Direction.UP));
        assertEquals(100, BlockMarks.behind(new Vec3(3.3, 100.0, 7.1), Direction.DOWN));
        assertEquals(16, BlockMarks.behind(new Vec3(16.0, 101.2, 0.4), Direction.WEST));
        assertEquals(15, BlockMarks.behind(new Vec3(16.0, 101.2, 0.4), Direction.EAST));
        assertEquals(new BlockPos(16, 101, -3), BlockMarks.cell(Direction.Axis.X, 16, Direction.Axis.Z, -3, Direction.Axis.Y, 101));
    }

    // ------------------------------------------------------------------------------------------ coming and going

    /** Two marks over the same texel share it: the stronger colour, in at the sooner, out at the later. */
    @Test
    void overlappingMarksShareTexels() {
        int now = 1000;
        BlockMarks.Face f = new BlockMarks.Face(BlockPos.ZERO, Direction.UP);
        f.put(7, 0x60AABBCC, now + 2, now + 50, now);
        f.put(7, 0xE0FFFFFF, now + 5, now + 80, now);
        assertEquals(0xE0FFFFFF, f.argb[7]);
        assertEquals(now + 2, f.appear[7]);
        assertEquals(now + 80, f.vanish[7]);
        f.put(7, 0x60AABBCC, now + 1, now + 20, now);
        assertEquals(0xE0FFFFFF, f.argb[7], "a fainter one doesn't dim it");
        // Once a texel has melted, a new mark starts it afresh.
        f.put(7, 0x60AABBCC, now + 100, now + 120, now + 90);
        assertEquals(0x60AABBCC, f.argb[7]);
        assertEquals(now + 100, f.appear[7]);
    }

    /** A texel shows nothing before it forms, is bright as it forms, thins in its last ticks, and then is gone. */
    @Test
    void aTexelFormsThinsAndGoes() {
        BlockMarks.Face f = new BlockMarks.Face(BlockPos.ZERO, Direction.UP);
        int colour = 0xA0CCE6FF;
        f.put(0, colour, 10, 40, 0);
        assertEquals(0, f.shown(0, 9), "not yet");
        int fresh = f.shown(0, 10);
        assertEquals(0xFF, fresh >>> 24, "the spreading edge is bright");
        assertEquals(colour, f.shown(0, 10 + BlockMarks.FRESH_TICKS));
        int thin = f.shown(0, 40 - BlockMarks.THIN_TICKS);
        assertTrue((thin >>> 24) < (colour >>> 24) && (thin >>> 24) > 0, "thinner as it melts");
        assertEquals(colour & 0xFFFFFF, thin & 0xFFFFFF, "the same colour, only thinner");
        assertEquals(0, f.shown(0, 40), "gone");
    }

    /** However a stamp is timed, every texel it lays forms before it melts, and all of it is gone by the stamp's end. */
    @Test
    void everythingMeltsOnItsOwn() {
        for (BlockMarks.Timing t : List.of(new BlockMarks.Timing(1f, 0, 5, 60, 24), new BlockMarks.Timing(0.4f, 15, 12, 0, 10),
                new BlockMarks.Timing(0.05f, 0, 0, 0, 1), new BlockMarks.Timing(1f, 3, 40, 200, 60))) {
            for (int order = 0; order <= 255; order++) {
                int appear = t.appear(order);
                if (appear < 0) {
                    assertTrue(order / 255f > t.reach(), "only past its reach is anything left out");
                    continue;
                }
                for (int jitter = -2; jitter <= 2; jitter++) {
                    int vanish = t.vanish(order, jitter);
                    assertTrue(vanish > appear, t + " at " + order);
                    assertTrue(vanish <= t.end(), t + " at " + order + ": still there after its end");
                }
            }
        }
    }

    private static VertexConsumer recorder(List<float[]> corners) {
        return new VertexConsumer() {
            @Override
            public VertexConsumer addVertex(float x, float y, float z) {
                corners.add(new float[] {x, y, z});
                return this;
            }

            @Override
            public VertexConsumer setColor(int r, int g, int b, int a) {
                return this;
            }

            @Override
            public VertexConsumer setUv(float u, float v) {
                return this;
            }

            @Override
            public VertexConsumer setUv1(int u, int v) {
                return this;
            }

            @Override
            public VertexConsumer setUv2(int u, int v) {
                return this;
            }

            @Override
            public VertexConsumer setNormal(float x, float y, float z) {
                return this;
            }
        };
    }
}

package com.cnethuka.kegare.innate_technique.rime.client;

import com.cnethuka.kegare.Kegare;
import com.cnethuka.kegare.config.KegareClientConfig;
import com.cnethuka.kegare.config.KegareClientConfig.VfxQuality;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.jetbrains.annotations.Nullable;

/**
 * What Rime paints on the world's blocks (frost, cracks, ice): pixel art on the blocks' own grid, 16 texels a block, so one texel of
 * a mark is exactly one texel of the block under it and the block itself looks frozen. All on the client; no block ever changes, and
 * every texel melts away on its own.
 * <p>
 * A {@link Pattern} ({@code textures/marks/}) is the art plus the order its texels come and go in. Stamping one lays it
 * on the faces of the blocks round a point, its centre snapped to their texel grid, and gives every texel the tick it forms and the
 * tick it melts: the inner ones form first and melt last. Each marked face keeps its own 16 x 16 texels, so marks that overlap
 * share texels (the stronger colour, the longer life) instead of flickering through each other. Drawn as one quad per run of like
 * texels in a row, in the world's light, except that a texel can glow: drawn at least as bright as its glow (0 to 15, a minimum
 * block light) so it reads in the dark, though it lights nothing round it.
 */
public final class BlockMarks {
    static final int TEXELS = 16;
    private static final ResourceLocation WHITE = Kegare.id("textures/marks/white.png");
    /** How far off the face the marks are drawn, so they never flicker into it. */
    private static final double LIFT = 0.008;
    /** A texel's last few ticks: thinner, then gone. */
    static final int THIN_TICKS = 4;
    /** A texel that has just formed is brighter for this long: the edge of the frost as it spreads. */
    static final int FRESH_TICKS = 2;
    /** No more marked faces than this at once (the oldest go first). */
    private static final int MAX_FACES = 3000;
    /** Past this, marks aren't drawn: a texel is far below a pixel by then. */
    private static final double DRAW_DISTANCE = 80;

    /**
     * The art ({@code argb}, row by row), when each texel comes and goes ({@code order}, 0 first in and last out, to 255), and how
     * much each glows ({@code glow}, 0 to 15, or null for none: {@code _glow.png}).
     */
    public record Pattern(int width, int height, int[] argb, int[] order, byte @Nullable [] glow) {
        public Pattern(int width, int height, int[] argb, int[] order) {
            this(width, height, argb, order, null);
        }

        /** A texel's glow, 0 to 15. */
        int glowAt(int i) {
            return glow == null ? 0 : glow[i];
        }
    }

    /**
     * When a stamp's texels come and go, in ticks from now. {@code reach} (0 to 1) is how much of the pattern's order is shown: less
     * for a lighter mark, which is the inner part of the same frost. Texels form over {@code spread} after {@code delay}, the
     * innermost first; the whole mark holds for {@code hold}; then over {@code melt} it goes from the edge in.
     */
    public record Timing(float reach, int delay, int spread, int hold, int melt) {
        /** The tick (from now) a texel at this order forms, or -1 if this stamp doesn't reach it. */
        int appear(int order) {
            float f = fraction(order);
            return f < 0 ? -1 : delay + Math.round(spread * f);
        }

        /** The tick (from now) a texel at this order is gone. */
        int vanish(int order, int jitter) {
            float f = Math.max(0f, fraction(order));
            return Math.max(appear(order) + 1, delay + spread + hold + Math.round(melt * (1f - f)) + jitter);
        }

        private float fraction(int order) {
            float o = order / 255f;
            if (o > reach + 1e-4f) return -1f;
            return reach <= 0f ? 0f : Mth.clamp(o / reach, 0f, 1f);
        }

        /** The last tick (from now) anything of this stamp shows. */
        int end() {
            return delay + spread + hold + melt + 2;
        }
    }

    /** One block face's texels: colour, and the ticks each forms and is gone. */
    static final class Face {
        final BlockPos solid;
        final Direction face;
        final int[] argb = new int[TEXELS * TEXELS];
        final int[] appear = new int[TEXELS * TEXELS];
        final int[] vanish = new int[TEXELS * TEXELS];
        final byte[] glow = new byte[TEXELS * TEXELS];
        int until;

        Face(BlockPos solid, Direction face) {
            this.solid = solid;
            this.face = face;
        }

        void put(int i, int colour, int appearAt, int vanishAt, int now) {
            put(i, colour, 0, appearAt, vanishAt, now);
        }

        /**
         * Puts a texel: where one is already there and alive, the two merge (sooner in, later out, the stronger colour, the brighter
         * glow).
         */
        void put(int i, int colour, int glowLevel, int appearAt, int vanishAt, int now) {
            if (argb[i] == 0 || vanish[i] <= now) {
                argb[i] = colour;
                glow[i] = (byte) glowLevel;
                appear[i] = appearAt;
                vanish[i] = vanishAt;
            } else {
                appear[i] = Math.min(appear[i], appearAt);
                vanish[i] = Math.max(vanish[i], vanishAt);
                if ((colour >>> 24) > (argb[i] >>> 24)) argb[i] = colour;
                glow[i] = (byte) Math.max(glow[i], glowLevel);
            }
            until = Math.max(until, vanish[i]);
        }

        /** What a texel shows at this tick: 0 for nothing; thinner in its last ticks, brighter in its first. */
        int shown(int i, int now) {
            int c = argb[i];
            if (c == 0 || now < appear[i] || now >= vanish[i]) return 0;
            if (vanish[i] - now <= THIN_TICKS) return (((c >>> 24) * 11 / 20) << 24) | (c & 0xFFFFFF);
            if (now - appear[i] < FRESH_TICKS) return 0xFF000000 | brighten(c);
            return c;
        }

        private static int brighten(int c) {
            int r = (c >> 16) & 255, g = (c >> 8) & 255, b = c & 255;
            return ((r + 255) / 2) << 16 | ((g + 255) / 2) << 8 | (b + 255) / 2;
        }
    }

    record Key(BlockPos solid, Direction face) {}

    private static final Map<Key, Face> FACES = new LinkedHashMap<>();
    private static final Map<ResourceLocation, Optional<Pattern>> PATTERNS = new HashMap<>();
    private static int clock;
    private static ClientLevel lastLevel;

    private BlockMarks() {}

    // ---------------------------------------------------------------------------------------------- patterns

    /** The pattern {@code textures/marks/<name>.png} with its {@code _order.png}, or null if it can't be read. */
    public static @Nullable Pattern pattern(ResourceLocation name) {
        return PATTERNS.computeIfAbsent(name, BlockMarks::load).orElse(null);
    }

    static void forget() {
        PATTERNS.clear();
    }

    private static Optional<Pattern> load(ResourceLocation name) {
        var manager = Minecraft.getInstance().getResourceManager();
        ResourceLocation art = name.withPath(p -> "textures/marks/" + p + ".png");
        ResourceLocation order = name.withPath(p -> "textures/marks/" + p + "_order.png");
        ResourceLocation glow = name.withPath(p -> "textures/marks/" + p + "_glow.png");
        try (InputStream a = manager.getResourceOrThrow(art).open(); InputStream o = manager.getResourceOrThrow(order).open();
                NativeImage ai = NativeImage.read(a); NativeImage oi = NativeImage.read(o)) {
            int w = ai.getWidth(), h = ai.getHeight();
            if (oi.getWidth() != w || oi.getHeight() != h) throw new IOException("the order isn't the art's size");
            int[] argb = new int[w * h], when = new int[w * h];
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    int abgr = ai.getPixelRGBA(x, y);
                    int alpha = abgr >>> 24;
                    argb[y * w + x] = alpha == 0 ? 0 : alpha << 24 | (abgr & 0xFF) << 16 | (abgr >> 8 & 0xFF) << 8 | (abgr >> 16 & 0xFF);
                    when[y * w + x] = oi.getPixelRGBA(x, y) & 0xFF;
                }
            }
            byte[] glows = null;
            var glowFile = manager.getResource(glow);
            if (glowFile.isPresent()) {
                try (InputStream g = glowFile.get().open(); NativeImage gi = NativeImage.read(g)) {
                    if (gi.getWidth() != w || gi.getHeight() != h) throw new IOException("the glow isn't the art's size");
                    glows = new byte[w * h];
                    for (int y = 0; y < h; y++) {
                        for (int x = 0; x < w; x++) glows[y * w + x] = (byte) Math.min(15, Math.round((gi.getPixelRGBA(x, y) & 0xFF) / 17f));
                    }
                }
            }
            return Optional.of(new Pattern(w, h, argb, when, glows));
        } catch (IOException | RuntimeException e) {
            Kegare.LOGGER.warn("Couldn't read the block mark {}: {}", name, e.toString());
            return Optional.empty();
        }
    }

    // ---------------------------------------------------------------------------------------------- placing

    /** The two axes across a face: the pattern's x runs along the first, its y along the second. */
    static Direction.Axis across(Direction.Axis normal) {
        return normal == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X;
    }

    static Direction.Axis down(Direction.Axis normal) {
        return normal == Direction.Axis.Y ? Direction.Axis.Z : Direction.Axis.Y;
    }

    /** Where texel (px, py) of a pattern lands, as world texel offsets from its centre, turned and mirrored one of eight ways. */
    static int[] turn(int px, int py, int width, int height, int variant) {
        int dx = px - width / 2, dy = py - height / 2;
        if ((variant & 1) != 0) dx = -dx - 1;
        if ((variant & 2) != 0) dy = -dy - 1;
        if ((variant & 4) != 0) {
            int s = dx;
            dx = dy;
            dy = s;
        }
        return new int[] {dx, dy};
    }

    /** The block a face mark at this point is on: the one just behind the face. */
    static int behind(Vec3 at, Direction face) {
        return Mth.floor(at.get(face.getAxis()) - face.getAxisDirection().getStep() * 0.01);
    }

    /**
     * Lays a pattern on the blocks' faces round {@code at} (a point on a face pointing {@code face}), turned one of eight ways
     * ({@code variant}), coming and going as {@code timing} says. Only where that face of the block is really there and open.
     */
    public static void stamp(ClientLevel level, Pattern p, Vec3 at, Direction face, int variant, Timing timing) {
        if (KegareClientConfig.VFX_QUALITY.get() == VfxQuality.VANILLA) return;
        sameLevel(level);
        Direction.Axis n = face.getAxis(), a = across(n), b = down(n);
        int back = behind(at, face);
        int ta = Mth.floor(at.get(a) * TEXELS), tb = Mth.floor(at.get(b) * TEXELS);
        Map<Long, Face> cells = new HashMap<>();
        for (int py = 0; py < p.height(); py++) {
            for (int px = 0; px < p.width(); px++) {
                int i = py * p.width() + px;
                int colour = p.argb()[i];
                if (colour == 0) continue;
                int appear = timing.appear(p.order()[i]);
                if (appear < 0) continue;
                int[] d = turn(px, py, p.width(), p.height(), variant);
                int wa = ta + d[0], wb = tb + d[1];
                int ca = Math.floorDiv(wa, TEXELS), cb = Math.floorDiv(wb, TEXELS);
                long cellKey = (long) ca << 32 ^ (cb & 0xFFFFFFFFL);
                Face f = cells.computeIfAbsent(cellKey, k -> faceAt(level, cell(n, back, a, ca, b, cb), face));
                if (f == BARE) continue;
                int local = Math.floorMod(wb, TEXELS) * TEXELS + Math.floorMod(wa, TEXELS);
                f.put(local, colour, p.glowAt(i), clock + appear, clock + timing.vanish(p.order()[i], jitter(wa, wb)), clock);
            }
        }
        while (FACES.size() > MAX_FACES) {
            Iterator<Key> it = FACES.keySet().iterator();
            it.next();
            it.remove();
        }
    }

    /** -2 to 2 ticks, fixed per texel, so a melting edge is ragged rather than a clean ring. */
    static int jitter(int wa, int wb) {
        int h = wa * 73856093 ^ wb * 19349663;
        return Math.floorMod(h >> 3, 5) - 2;
    }

    /** Where the face isn't there to mark. */
    private static final Face BARE = new Face(BlockPos.ZERO, Direction.UP);

    private static Face faceAt(ClientLevel level, BlockPos solid, Direction face) {
        if (!carries(level, solid, face)) return BARE;
        return FACES.computeIfAbsent(new Key(solid, face), k -> new Face(solid, face));
    }

    /** The block shows that face whole: sturdy, with nothing sturdy against it. */
    private static boolean carries(ClientLevel level, BlockPos solid, Direction face) {
        BlockPos open = solid.relative(face);
        return level.getBlockState(solid).isFaceSturdy(level, solid, face)
                && !level.getBlockState(open).isFaceSturdy(level, open, face.getOpposite());
    }

    static BlockPos cell(Direction.Axis normal, int n, Direction.Axis a, int ia, Direction.Axis b, int ib) {
        int[] xyz = new int[3];
        xyz[normal.ordinal()] = n;
        xyz[a.ordinal()] = ia;
        xyz[b.ordinal()] = ib;
        return new BlockPos(xyz[0], xyz[1], xyz[2]);
    }

    // ---------------------------------------------------------------------------------------------- per tick

    static void tick(ClientLevel level) {
        sameLevel(level);
        clock++;
        FACES.values().removeIf(f -> f.until <= clock);
    }

    private static void sameLevel(ClientLevel level) {
        if (level != lastLevel) {
            FACES.clear();
            lastLevel = level;
        }
    }

    static void clear() {
        FACES.clear();
        lastLevel = null;
    }

    /** For tests: the tick marks are reckoned in. */
    static int clock() {
        return clock;
    }

    // ---------------------------------------------------------------------------------------------- drawing

    static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES || FACES.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || level != lastLevel) return;
        Vec3 cam = event.getCamera().getPosition();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        RenderType type = RenderType.entityTranslucent(WHITE);
        VertexConsumer out = buffers.getBuffer(type);
        for (Face f : FACES.values()) {
            if (f.solid.distToCenterSqr(cam) > DRAW_DISTANCE * DRAW_DISTANCE) continue;
            if (!carries(level, f.solid, f.face)) continue; // the block went, or something now covers that face
            drawFace(out, cam, f, LevelRenderer.getLightColor(level, f.solid.relative(f.face)), level.getShade(f.face, true));
        }
        buffers.endBatch(type);
    }

    /** The light a texel is drawn in: the world's, or its glow if that's brighter (as block light; the sky's is kept). */
    static int lit(int light, int glow) {
        if (glow <= LightTexture.block(light)) return light;
        return LightTexture.pack(glow, LightTexture.sky(light));
    }

    /**
     * One face's texels. {@code shade} is how the world shades that face of a block (a wall darker than the top), so the frost on it
     * is shaded just as the block under it is; the normal given is straight up, for which entity lighting adds nothing more.
     */
    static void drawFace(VertexConsumer out, Vec3 cam, Face f, int light, float shade) {
        Direction face = f.face;
        Direction.Axis n = face.getAxis(), a = across(n), b = down(n);
        int step = face.getAxisDirection().getStep();
        double plane = f.solid.get(n) + (step > 0 ? 1 : 0) + step * LIFT;
        int ca = f.solid.get(a), cb = f.solid.get(b);
        for (int v = 0; v < TEXELS; v++) {
            int u = 0;
            while (u < TEXELS) {
                int c = f.shown(v * TEXELS + u, clock);
                if (c == 0) {
                    u++;
                    continue;
                }
                int g = f.glow[v * TEXELS + u];
                int end = u + 1;
                while (end < TEXELS && f.shown(v * TEXELS + end, clock) == c && f.glow[v * TEXELS + end] == g) end++;
                quad(out, cam, n, plane, a, ca + u / (double) TEXELS, ca + end / (double) TEXELS, b, cb + v / (double) TEXELS,
                        cb + (v + 1) / (double) TEXELS, c, lit(light, g), shade);
                u = end;
            }
        }
    }

    private static void quad(VertexConsumer out, Vec3 cam, Direction.Axis n, double plane, Direction.Axis a, double a0, double a1,
            Direction.Axis b, double b0, double b1, int argb, int light, float shade) {
        int alpha = argb >>> 24;
        int r = Math.round(((argb >> 16) & 255) * shade), g = Math.round(((argb >> 8) & 255) * shade), bl = Math.round((argb & 255) * shade);
        double[][] corners = {{a0, b0}, {a1, b0}, {a1, b1}, {a0, b1}};
        for (double[] c : corners) {
            double[] xyz = new double[3];
            xyz[n.ordinal()] = plane;
            xyz[a.ordinal()] = c[0];
            xyz[b.ordinal()] = c[1];
            out.addVertex((float) (xyz[0] - cam.x), (float) (xyz[1] - cam.y), (float) (xyz[2] - cam.z))
                    .setColor(r, g, bl, alpha).setUv(0.5f, 0.5f)
                    .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light)
                    .setNormal(0f, 1f, 0f);
        }
    }
}

package com.cnethuka.kegare.innate_technique.rime.client;

import com.cnethuka.kegare.Kegare;
import com.cnethuka.kegare.client.visual.ClientVisuals;
import com.cnethuka.kegare.innate_technique.rime.RimeConfig;
import com.cnethuka.kegare.innate_technique.rime.RimeIds;
import com.cnethuka.kegare.innatetechnique.visual.Visual;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

/**
 * Shatter Shell's burst, on every client, as the server's cast visual ({@code shatter_shell_burst}) arrives:
 * <ul>
 * <li>the shell's plates fly off the body out to the ring ({@link ShellClient#burst}; a whole set even with no shell up);</li>
 * <li>a smooth shock ring races out over the ground from the feet to exactly the knockback's reach (the server's
 * {@code shatter_shell.radius}, synced) and fades: a soft trail of frost behind a bright front, a low wall of light at the front;</li>
 * <li>and leaves the same ring in frost on the blocks (block mark {@code marks/shatter_ring}, pixel art, moved in or out to that
 * reach), which holds a moment and melts. No block changes.</li>
 * </ul>
 */
public final class ShatterClient {
    static final ResourceLocation RING_MARK = Kegare.id("shatter_ring");
    /** The mark's ring, in texels from its middle (the art is drawn for the default reach, 4.5 blocks). */
    static final int ART_RADIUS = 72;
    /** Near its ring, the mark is moved in or out whole for another reach, so the ring keeps its width; inside, it's stretched. */
    static final int KEEP = 12;
    static final ResourceLocation TEXTURE = Kegare.id("textures/fx/shatter_ring.png");
    /** Where the bright front is across the texture ({@code textures/fx/shatter_ring.png}, as u). */
    static final float FRONT = 0.82f;
    /** Ticks the shock takes to reach the edge; ticks it then takes to fade. */
    static final int EXPAND = 5, FADE = 10;
    /** The ring's width on the ground and its wall's height, in blocks. */
    static final float WIDTH = 1.1f, WALL = 0.55f;
    /** Ticks the frost left on the ground holds once the ring's out, then takes to melt from the edge in. */
    static final int HOLD = 30, MELT = 24;
    private static final int SEGMENTS = 96;

    private record Ring(Vec3 at, float radius, int born) {}

    private static final List<Ring> RINGS = new ArrayList<>();
    private static final RandomSource RANDOM = RandomSource.create();
    private static int clock;
    private static @Nullable BlockMarks.Pattern moved;
    private static float movedFor = Float.NaN;

    private ShatterClient() {}

    static void init() {
        ClientVisuals.listen(new ClientVisuals.Listener() {
            @Override
            public void playedOnce(Entity entity, Visual visual) {
                if (visual.effect().filter(RimeIds.FX_SHATTER_SHELL_BURST::equals).isPresent()) burst(entity);
            }
        });
    }

    /** The knockback's reach, in blocks. */
    static float radius() {
        try {
            return RimeConfig.SHATTER_RADIUS.get().floatValue();
        } catch (IllegalStateException notLoaded) {
            return 4.5f;
        }
    }

    static void burst(Entity caster) {
        float radius = radius();
        ShellClient.burst(caster, radius - 0.4f);
        Vec3 feet = caster.position();
        RINGS.add(new Ring(feet.add(0, 0.04, 0), radius, clock));
        if (caster.level() instanceof ClientLevel level) frost(level, caster, feet, radius);
    }

    /** The shock's front, {@code t} ticks after the burst: fast out of the feet, easing into the edge. */
    static float front(float radius, float t) {
        float f = Mth.clamp(t / EXPAND, 0f, 1f);
        float eased = 1f - (1f - f) * (1f - f) * (1f - f);
        return 0.5f + (radius - 0.5f) * eased;
    }

    // ---------------------------------------------------------------------------------------------- the frost on the ground

    private static void frost(ClientLevel level, Entity caster, Vec3 feet, float radius) {
        BlockMarks.Pattern art = BlockMarks.pattern(RING_MARK);
        if (art == null) return;
        if (movedFor != radius) {
            moved = ring(art, radius);
            movedFor = radius;
        }
        BlockHitResult hit = level.clip(new ClipContext(feet.add(0, 0.2, 0), feet.subtract(0, 3, 0), ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, caster));
        if (hit.getType() == HitResult.Type.MISS || hit.getDirection() != Direction.UP) return;
        Vec3 ground = new Vec3(feet.x, hit.getLocation().y, feet.z);
        BlockMarks.Timing timing = new BlockMarks.Timing(1f, 0, EXPAND, HOLD, MELT);
        int variant = RANDOM.nextInt(8);
        // A step up or down still gets its part of the ring: each level marks only the tops that are really there and open.
        for (int dy = -1; dy <= 1; dy++) BlockMarks.stamp(level, moved, ground.add(0, dy, 0), Direction.UP, variant, timing);
    }

    /**
     * The mark for a reach of {@code radius} blocks: the art's ring moved out (or in) to it, its width kept, and the streaks inside
     * stretched to meet it. The default reach is the art as drawn.
     */
    static BlockMarks.Pattern ring(BlockMarks.Pattern art, float radius) {
        float target = radius * BlockMarks.TEXELS;
        if (Math.abs(target - ART_RADIUS) < 0.5f) return art;
        float keep = Math.min(KEEP, target * 0.5f);
        float inner = target - keep, artInner = ART_RADIUS - keep;
        int margin = art.width() / 2 - ART_RADIUS;
        int size = 2 * (Mth.ceil(target) + margin);
        int[] argb = new int[size * size], order = new int[size * size];
        float c = size / 2f, ac = art.width() / 2f;
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                float dx = x + 0.5f - c, dy = y + 0.5f - c;
                float d = Mth.sqrt(dx * dx + dy * dy);
                if (d < 1e-3f) continue;
                float from = d >= inner ? d - (target - ART_RADIUS) : d * artInner / inner;
                int sx = Mth.floor(ac + dx / d * from), sy = Mth.floor(ac + dy / d * from);
                if (sx < 0 || sy < 0 || sx >= art.width() || sy >= art.height()) continue;
                argb[y * size + x] = art.argb()[sy * art.width() + sx];
                order[y * size + x] = art.order()[sy * art.width() + sx];
            }
        }
        return new BlockMarks.Pattern(size, size, argb, order);
    }

    // ---------------------------------------------------------------------------------------------- the shock ring

    static void tick() {
        clock++;
        RINGS.removeIf(r -> clock - r.born() > EXPAND + FADE);
    }

    static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES || RINGS.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        Vec3 cam = event.getCamera().getPosition();
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        RenderType type = RenderType.entityTranslucentEmissive(TEXTURE);
        VertexConsumer out = buffers.getBuffer(type);
        for (Ring ring : RINGS) {
            float t = clock - ring.born() + partial;
            float alpha = t <= EXPAND ? 1f : Math.max(0f, 1f - (t - EXPAND) / FADE);
            if (alpha <= 0f) continue;
            float r = front(ring.radius(), t);
            Matrix4f m = new Matrix4f().translation((float) (ring.at().x - cam.x), (float) (ring.at().y - cam.y), (float) (ring.at().z - cam.z));
            draw(out, m, r, alpha);
        }
        buffers.endBatch(type);
    }

    /** One ring, front at {@code r}: the band flat on the ground (its front at r, its trail inside) and the low wall at the front. */
    private static void draw(VertexConsumer out, Matrix4f m, float r, float alpha) {
        float in = r - WIDTH * FRONT, outer = r + WIDTH * (1f - FRONT);
        float u0 = 0f;
        if (in < 0f) {
            u0 = -in / WIDTH;
            in = 0f;
        }
        int a = Math.round(alpha * 255);
        float wall = WALL * (0.6f + 0.4f * alpha);
        for (int i = 0; i < SEGMENTS; i++) {
            float a0 = Mth.TWO_PI * i / SEGMENTS, a1 = Mth.TWO_PI * (i + 1) / SEGMENTS;
            float c0 = Mth.cos(a0), s0 = Mth.sin(a0), c1 = Mth.cos(a1), s1 = Mth.sin(a1);
            vertex(out, m, c0 * in, 0f, s0 * in, u0, 0.02f, a);
            vertex(out, m, c1 * in, 0f, s1 * in, u0, 0.02f, a);
            vertex(out, m, c1 * outer, 0f, s1 * outer, 1f, 0.02f, a);
            vertex(out, m, c0 * outer, 0f, s0 * outer, 1f, 0.02f, a);

            vertex(out, m, c0 * r, 0f, s0 * r, FRONT, 0.02f, a);
            vertex(out, m, c1 * r, 0f, s1 * r, FRONT, 0.02f, a);
            vertex(out, m, c1 * r, wall, s1 * r, FRONT, 0.98f, a);
            vertex(out, m, c0 * r, wall, s0 * r, FRONT, 0.98f, a);
        }
    }

    private static void vertex(VertexConsumer out, Matrix4f m, float x, float y, float z, float u, float v, int alpha) {
        out.addVertex(m, x, y, z)
                .setColor(255, 255, 255, alpha)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightTexture.FULL_BRIGHT)
                .setNormal(0f, 1f, 0f);
    }

    static void clear() {
        RINGS.clear();
    }
}

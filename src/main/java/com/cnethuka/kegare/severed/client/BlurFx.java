package com.cnethuka.kegare.severed.client;

import com.cnethuka.kegare.config.KegareClientConfig;
import com.cnethuka.kegare.config.KegareClientConfig.VfxQuality;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Blur as everyone else sees it (DESIGN §3.27): a smear of delayed afterimages trailing behind the one blurring, desaturated and
 * physical (lit by the world like the body, never glowing), with dust and grit kicked off the ground. Where they really are reads
 * clearly: the body itself is drawn as always, a sharp, solid silhouette at the front, and no afterimage is drawn within reach of it,
 * so a hit on what you see is a hit on their real box. Reduced draws fewer afterimages; Vanilla none, only the dust. No afterimage is
 * drawn round the camera looking at it (yours in first person, or anyone they blur past or through).
 */
final class BlurFx {
    static final ResourceLocation WHITE = ResourceLocation.withDefaultNamespace("textures/misc/white.png");
    /** Dust-grey: the afterimage's one colour. */
    private static final int GHOST_RGB = 0xA59E94;
    /** How many ticks back each afterimage is, at Full and at Reduced. */
    private static final int[] BACK_FULL = {2, 3, 5, 7, 9}, BACK_REDUCED = {3, 6, 9};
    /** No afterimage closer than this to the real body (the front stays clean), nor this close to another. */
    private static final double CLEAR_OF_BODY = 0.9, APART = 0.5;
    /** Nor this close to the camera looking at it: someone blurring past (or through) you never fills your view with grey. */
    private static final double CLEAR_OF_CAMERA = 1.6;
    private static final int KEPT = 12;
    /** Faster than a sprint (blocks a tick): dust flies. */
    private static final double DUSTY = 0.32;

    private static final Map<Integer, ArrayDeque<Vec3>> TRAILS = new HashMap<>();
    private static long clock;

    private BlurFx() {}

    static VfxQuality quality() {
        return KegareClientConfig.VFX_QUALITY.get();
    }

    static void started(Entity entity) {
        TRAILS.computeIfAbsent(entity.getId(), k -> new ArrayDeque<>());
    }

    static void stopped(Entity entity) {
        TRAILS.remove(entity.getId());
    }

    static void clear() {
        TRAILS.clear();
    }

    static void tick(ClientLevel level) {
        clock++;
        for (Iterator<Map.Entry<Integer, ArrayDeque<Vec3>>> it = TRAILS.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Integer, ArrayDeque<Vec3>> e = it.next();
            Entity entity = level.getEntity(e.getKey());
            if (entity == null || !entity.isAlive()) {
                it.remove();
                continue;
            }
            ArrayDeque<Vec3> trail = e.getValue();
            Vec3 at = entity.position();
            Vec3 was = trail.peekLast();
            if (was != null && was.distanceToSqr(at) > 64) trail.clear(); // a teleport isn't a smear
            trail.addLast(at);
            while (trail.size() > KEPT) trail.pollFirst();
            double speed = was == null ? 0 : Math.sqrt(Mth.square(at.x - was.x) + Mth.square(at.z - was.z));
            if (speed > DUSTY && entity.onGround()) dust(level, entity, at, speed);
        }
    }

    /** Dust and grit off the ground behind them: what they're running on. */
    private static void dust(ClientLevel level, Entity entity, Vec3 at, double speed) {
        int count = switch (quality()) {
            case FULL -> 2;
            case REDUCED -> 1;
            case VANILLA -> clock % 2 == 0 ? 1 : 0;
        };
        BlockState ground = level.getBlockState(BlockPos.containing(at.x, at.y - 0.2, at.z));
        if (ground.isAir() || count == 0) return;
        Vec3 motion = entity.getDeltaMovement();
        for (int i = 0; i < count; i++) {
            double x = at.x + (level.random.nextDouble() - 0.5) * 0.5, z = at.z + (level.random.nextDouble() - 0.5) * 0.5;
            level.addParticle(new BlockParticleOption(ParticleTypes.BLOCK, ground), x, at.y + 0.1, z, -motion.x * 0.4, 0.12 + speed * 0.1, -motion.z * 0.4);
        }
    }

    static void render(PoseStack pose, Camera camera, float partial) {
        if (TRAILS.isEmpty() || quality() == VfxQuality.VANILLA) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) return;
        boolean firstPerson = mc.options.getCameraType().isFirstPerson();
        Vec3 cam = camera.getPosition();
        int[] back = quality() == VfxQuality.FULL ? BACK_FULL : BACK_REDUCED;
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        for (Map.Entry<Integer, ArrayDeque<Vec3>> e : TRAILS.entrySet()) {
            Entity entity = level.getEntity(e.getKey());
            if (entity == null || (entity == mc.player && firstPerson)) continue;
            List<Vec3> trail = new ArrayList<>(e.getValue());
            Vec3 now = entity.getPosition(partial);
            Vec3 last = now;
            for (int k = 0; k < back.length; k++) {
                int index = trail.size() - 1 - back[k];
                if (index < 0) break;
                Vec3 at = trail.get(index);
                if (at.distanceTo(now) < CLEAR_OF_BODY || at.distanceTo(last) < APART) continue;
                if (at.add(0, entity.getBbHeight() / 2, 0).distanceTo(cam) < CLEAR_OF_CAMERA) continue;
                last = at;
                float alpha = 0.42f * (1f - k / (float) back.length);
                int light = LevelRenderer.getLightColor(level, BlockPos.containing(at.x, at.y + 1.0, at.z));
                ghost(pose, buffers, entity, at.subtract(cam), alpha, light, partial);
            }
        }
    }

    /** The body drawn again at {@code at} (from the camera), all of it in dust-grey, see-through, lit as the world is there. */
    private static void ghost(PoseStack pose, MultiBufferSource.BufferSource buffers, Entity e, Vec3 at, float alpha, int light, float partial) {
        EntityRenderDispatcher dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
        RenderType type = RenderType.itemEntityTranslucentCull(WHITE);
        int a = Math.round(Mth.clamp(alpha, 0f, 1f) * 255);
        MultiBufferSource tinted = t -> drawn(t) ? new Tint(buffers.getBuffer(type), a, light) : NOTHING;
        dispatcher.setRenderShadow(false);
        try {
            com.cnethuka.kegare.client.visual.BodyCopies.draw(() -> dispatcher.render(e, at.x, at.y, at.z, e.getYRot(), partial, pose, tinted, light));
        } catch (RuntimeException ignored) {
            // An odd renderer: no afterimage of it, rather than a crash.
        } finally {
            dispatcher.setRenderShadow(true);
        }
        buffers.endBatch(type);
    }

    private static boolean drawn(RenderType t) {
        if (t.mode() != VertexFormat.Mode.QUADS) return false;
        String s = t.toString();
        String name = s.startsWith("RenderType[") ? s.substring(11, Math.max(11, s.indexOf(':'))) : s;
        return !name.startsWith("text") && !name.contains("glint") && !name.contains("shadow") && !name.contains("outline");
    }

    private static final VertexConsumer NOTHING = new VertexConsumer() {
        @Override public VertexConsumer addVertex(float x, float y, float z) { return this; }
        @Override public VertexConsumer setColor(int r, int g, int b, int a) { return this; }
        @Override public VertexConsumer setUv(float u, float v) { return this; }
        @Override public VertexConsumer setUv1(int u, int v) { return this; }
        @Override public VertexConsumer setUv2(int u, int v) { return this; }
        @Override public VertexConsumer setNormal(float x, float y, float z) { return this; }
    };

    /** Each vertex written whole in the one colour, with the world's light; whatever the renderer sets after is ignored. */
    private record Tint(VertexConsumer out, int alpha, int light) implements VertexConsumer {
        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            out.addVertex(x, y, z).setColor(GHOST_RGB >> 16 & 0xFF, GHOST_RGB >> 8 & 0xFF, GHOST_RGB & 0xFF, alpha).setUv(0.5f, 0.5f)
                    .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(0f, 1f, 0f);
            return this;
        }

        @Override public VertexConsumer setColor(int r, int g, int b, int a) { return this; }
        @Override public VertexConsumer setUv(float u, float v) { return this; }
        @Override public VertexConsumer setUv1(int u, int v) { return this; }
        @Override public VertexConsumer setUv2(int u, int v) { return this; }
        @Override public VertexConsumer setNormal(float x, float y, float z) { return this; }
    }
}

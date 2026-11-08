package com.cnethuka.kegare.innate_technique.time_manipulation.client;

import com.cnethuka.kegare.config.KegareClientConfig;
import com.cnethuka.kegare.config.KegareClientConfig.VfxQuality;
import com.cnethuka.kegare.innate_technique.time_manipulation.TimeNet;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * What Time Manipulation draws itself (DESIGN §3.26): clean bronze and silver-white shapes with hard edges, quick and gone, no haze,
 * no particle soup, nothing that lingers past its moment.
 * <ul>
 *   <li><b>Afterimages</b>: the entity itself drawn again where it was, in one flat colour, see-through: a Point Maker return
 *   leaves them along the way it came back (newest first, fading as they go), a Rewind along the path it's yanked back down, and
 *   Accelerate a faint one every few ticks behind anyone running with it on. Never drawn round your own first-person camera.</li>
 *   <li><b>Dials</b>: clock faces drawn on someone. Rewind: one at the target, its hands swept round counter-clockwise. Fast Forward:
 *   one laid flat round the caster's waist, three hands whirling forward. A Decelerate hit: a small one flashing up over the target,
 *   its hands dragging round slowly for as long as it's slowed.</li>
 *   <li>The <b>Stasis Field</b>: a smooth glass dome of bronze, clear in the middle and denser to its edge, over a clock face laid
 *   on the ground that turns slowly; it snaps open and folds away.</li>
 *   <li>The <b>snap</b>: someone Rewound or returning jumps to where they were sent instead of sliding there, so it's instant for
 *   everyone watching.</li>
 * </ul>
 * Reduced draws fewer afterimages and a coarser dome; Vanilla draws none of it and puts small bronze and silver dust in its place.
 */
public final class TimeFx {
    static final ResourceLocation WHITE = ResourceLocation.withDefaultNamespace("textures/misc/white.png");
    static final int BRONZE = 0xB08D57, BRONZE_DARK = 0x7A6038, BRONZE_PALE = 0xD9BC8A, SILVER = 0xE9E6DF, BRIGHT = 0xFFFCF2;
    static final DustParticleOptions BRONZE_DUST = new DustParticleOptions(new Vector3f(0.69f, 0.55f, 0.34f), 0.6f);
    static final DustParticleOptions SILVER_DUST = new DustParticleOptions(new Vector3f(0.91f, 0.9f, 0.87f), 0.5f);
    /** An afterimage this close to your first-person camera isn't drawn (you're standing in it). */
    private static final double CLEAR_OF_CAMERA = 1.4;
    private static final int SEGMENTS = 48;

    /** An entity drawn again where it was: from {@code born} for {@code life} ticks, fading from {@code alpha}. */
    private record Ghost(int entityId, Vec3 feet, long born, int life, float alpha, int rgb) {}

    enum DialKind { REWIND, WHIRL, SLOWED }

    private record Dial(DialKind kind, int entityId, long born, int ticks) {}

    private static final class Field {
        final Vec3 centre;
        final float radius;
        final long born, until;
        long closing = -1;

        Field(Vec3 centre, float radius, long born, int ticks) {
            this.centre = centre;
            this.radius = radius;
            this.born = born;
            this.until = born + ticks;
        }
    }

    private static final List<Ghost> GHOSTS = new ArrayList<>();
    private static final List<Dial> DIALS = new ArrayList<>();
    private static final Map<Integer, Field> FIELDS = new HashMap<>();
    /** Who is running Accelerate (its loop), and where each was when it last left an afterimage. */
    private static final Map<Integer, Vec3> ACCELERATING = new HashMap<>();
    private static long clock;

    private TimeFx() {}

    static VfxQuality quality() {
        return KegareClientConfig.VFX_QUALITY.get();
    }

    static boolean drawn() {
        return quality() != VfxQuality.VANILLA;
    }

    // ---------------------------------------------------------------- what the server says happened

    static void onFx(TimeNet.Fx fx) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        switch (fx.kind()) {
            case RETURN -> trail(level, fx.entity(), fx.path(), 10, 5, 8, 0.5f, BRONZE);
            case REWIND -> {
                trail(level, fx.entity(), fx.path(), 8, 4, 6, 0.6f, BRONZE);
                DIALS.add(new Dial(DialKind.REWIND, fx.entity(), clock, 12));
                if (TimeStopClient.active() && level.getEntity(fx.entity()) instanceof Entity e && TimeStopClient.frozen(e) && !fx.path().isEmpty()) {
                    // Frozen in a Standstill, it never ticks to where it was sent: put it there.
                    Vec3 to = fx.path().get(0);
                    e.setPos(to);
                    e.setOldPosAndRot();
                }
            }
            case FAST_FORWARD -> DIALS.add(new Dial(DialKind.WHIRL, fx.entity(), clock, 14));
            case SLOWED -> DIALS.add(new Dial(DialKind.SLOWED, fx.entity(), clock, Math.max(10, fx.ticks())));
            case STASIS -> {
                if (!fx.path().isEmpty()) FIELDS.put(fx.entity(), new Field(fx.path().get(0), fx.value(), clock, fx.ticks()));
            }
            case STASIS_END -> {
                Field f = FIELDS.get(fx.entity());
                if (f != null && f.closing < 0) f.closing = clock;
            }
        }
    }

    /**
     * Afterimages along {@code path} (oldest first), drawn backward: the newest first, then on back to the oldest over
     * {@code spread} ticks. At most {@code count} (half at Reduced; dust at Vanilla). The mover is snapped to where it was sent.
     */
    private static void trail(ClientLevel level, int entityId, List<Vec3> path, int count, int spread, int life, float alpha, int rgb) {
        Entity entity = level.getEntity(entityId);
        if (entity != null) snap(entity);
        if (path.size() < 2) return;
        if (!drawn()) {
            for (int i = 0; i < path.size(); i += Math.max(1, path.size() / 6)) {
                Vec3 p = path.get(i);
                level.addParticle(BRONZE_DUST, p.x, p.y + 1.0, p.z, 0, 0, 0);
            }
            return;
        }
        if (quality() == VfxQuality.REDUCED) count = Math.max(2, count / 2);
        int n = Math.min(count, path.size());
        for (int k = 0; k < n; k++) {
            // k = 0 is the newest; the last is the oldest.
            int index = path.size() - 1 - Math.round(k * (path.size() - 1) / (float) Math.max(1, n - 1));
            int delay = Math.round(k * spread / (float) Math.max(1, n - 1));
            GHOSTS.add(new Ghost(entityId, path.get(index), clock + delay, life, alpha * (1f - 0.35f * k / Math.max(1, n - 1)), rgb));
        }
    }

    /** A moved entity drawn where it was sent at once, not sliding there over three ticks as vanilla draws a teleport. */
    private static void snap(Entity entity) {
        if (entity == Minecraft.getInstance().player || entity.isControlledByLocalInstance()) return;
        Vec3 to = new Vec3(entity.lerpTargetX(), entity.lerpTargetY(), entity.lerpTargetZ());
        if (to.distanceToSqr(entity.position()) < 0.25) return;
        float yaw = entity.lerpTargetYRot(), pitch = entity.lerpTargetXRot();
        entity.setPos(to);
        entity.lerpTo(to.x, to.y, to.z, yaw, pitch, 0);
        entity.setOldPosAndRot();
    }

    static void accelerateStarted(Entity entity) {
        ACCELERATING.put(entity.getId(), entity.position());
    }

    static void accelerateStopped(Entity entity) {
        ACCELERATING.remove(entity.getId());
    }

    // ---------------------------------------------------------------- ticking

    static void tick(ClientLevel level) {
        clock++;
        GHOSTS.removeIf(g -> clock - g.born() >= g.life());
        DIALS.removeIf(d -> clock - d.born() >= d.ticks() || level.getEntity(d.entityId()) == null);
        for (Field f : FIELDS.values()) if (f.closing < 0 && clock >= f.until + 5) f.closing = clock; // its end never came
        FIELDS.values().removeIf(f -> f.closing >= 0 && clock - f.closing > 3);
        int every = quality() == VfxQuality.FULL ? 2 : 4;
        for (Iterator<Map.Entry<Integer, Vec3>> it = ACCELERATING.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Integer, Vec3> e = it.next();
            Entity entity = level.getEntity(e.getKey());
            if (entity == null || !entity.isAlive()) {
                it.remove();
                continue;
            }
            if (clock % every != 0) continue;
            Vec3 was = e.getValue(), now = entity.position();
            if (now.distanceToSqr(was) >= 0.09 && !TimeStopClient.frozen(entity)) {
                if (drawn()) GHOSTS.add(new Ghost(entity.getId(), was, clock, 4, 0.2f, SILVER));
                else level.addParticle(SILVER_DUST, was.x, was.y + 0.2, was.z, 0, 0, 0);
            }
            e.setValue(now);
        }
        if (!drawn()) vanilla(level);
    }

    /** Vanilla's stand-ins for the dials and the field: a few specks, gone at once. */
    private static void vanilla(ClientLevel level) {
        for (Dial d : DIALS) {
            Entity e = level.getEntity(d.entityId());
            if (e == null) continue;
            long age = clock - d.born();
            Vec3 c = e.position().add(0, d.kind() == DialKind.SLOWED ? e.getBbHeight() + 0.3 : e.getBbHeight() * 0.55, 0);
            if (d.kind() == DialKind.SLOWED) {
                if (age % 10 == 0) level.addParticle(BRONZE_DUST, c.x, c.y, c.z, 0, 0, 0);
            } else if (age < 6) {
                double a = age * (d.kind() == DialKind.REWIND ? -1.1 : 1.1);
                level.addParticle(d.kind() == DialKind.REWIND ? BRONZE_DUST : SILVER_DUST, c.x + Math.sin(a) * 0.8, c.y, c.z + Math.cos(a) * 0.8, 0, 0, 0);
            }
        }
        for (Field f : FIELDS.values()) {
            long age = clock - f.born;
            if (age % 10 != 0 || f.closing >= 0) continue;
            for (int i = 0; i < 16; i++) {
                double a = Mth.TWO_PI * i / 16 + age * 0.02;
                level.addParticle(BRONZE_DUST, f.centre.x + Math.sin(a) * f.radius, f.centre.y + 0.1, f.centre.z + Math.cos(a) * f.radius, 0, 0, 0);
            }
        }
    }

    static void clear() {
        GHOSTS.clear();
        DIALS.clear();
        FIELDS.clear();
        ACCELERATING.clear();
    }

    // ---------------------------------------------------------------- drawing

    static void render(PoseStack pose, Camera camera, float partial) {
        if (!drawn() || (GHOSTS.isEmpty() && DIALS.isEmpty() && FIELDS.isEmpty())) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) return;
        Vec3 cam = camera.getPosition();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        boolean firstPerson = mc.options.getCameraType().isFirstPerson();
        for (Ghost g : GHOSTS) {
            long age = clock - g.born();
            if (age < 0) continue;
            Entity e = level.getEntity(g.entityId());
            if (e == null) continue;
            if (firstPerson && g.feet().add(0, 1.0, 0).distanceTo(cam) < CLEAR_OF_CAMERA) continue;
            float t = Mth.clamp((age + partial) / g.life(), 0f, 1f);
            ghost(pose, buffers, e, g.feet().subtract(cam), g.alpha() * (1f - t), g.rgb(), partial);
        }
        RenderType type = RenderType.entityTranslucentEmissive(WHITE);
        VertexConsumer out = buffers.getBuffer(type);
        for (Dial d : DIALS) {
            Entity e = level.getEntity(d.entityId());
            if (e == null) continue;
            if (firstPerson && e == mc.player && d.kind() != DialKind.WHIRL) continue;
            float age = clock - d.born() + partial;
            Vec3 at = e.getPosition(partial).subtract(cam);
            switch (d.kind()) {
                case REWIND -> rewindDial(out, pose, camera, at.add(0, e.getBbHeight() * 0.55, 0), age, d.ticks());
                case WHIRL -> whirl(out, pose, camera, at.add(0, e.getBbHeight() * 0.5, 0), Math.max(1.05f, e.getBbHeight() * 0.62f), age,
                        d.ticks(), firstPerson && e == mc.player);
                case SLOWED -> slowedDial(out, pose, camera, at.add(0, e.getBbHeight() + 0.95, 0), age, d.ticks());
            }
        }
        for (Field f : FIELDS.values()) field(out, pose, cam, f, partial);
        buffers.endBatch(type);
    }

    /** The entity drawn again at {@code at} (from the camera): every part of it in one colour, see-through, fully lit. */
    private static void ghost(PoseStack pose, MultiBufferSource.BufferSource buffers, Entity e, Vec3 at, float alpha, int rgb, float partial) {
        if (alpha <= 0.01f) return;
        EntityRenderDispatcher dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
        RenderType type = RenderType.itemEntityTranslucentCull(WHITE);
        int a = Math.round(Mth.clamp(alpha, 0f, 1f) * 255);
        MultiBufferSource tinted = t -> drawsGhost(t) ? new Tint(buffers.getBuffer(type), rgb, a) : NOTHING;
        dispatcher.setRenderShadow(false);
        try {
            com.cnethuka.kegare.client.visual.BodyCopies.draw(
                    () -> dispatcher.render(e, at.x, at.y, at.z, e.getYRot(), partial, pose, tinted, LightTexture.FULL_BRIGHT));
        } catch (RuntimeException ignored) {
            // An odd renderer: no afterimage of it, rather than a crash.
        } finally {
            dispatcher.setRenderShadow(true);
        }
        buffers.endBatch(type);
    }

    /** The body's own quads go into the afterimage; its name, glint, lines and strips don't. */
    private static boolean drawsGhost(RenderType t) {
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

    /** Writes each vertex whole, in one colour, the moment it's started; whatever the renderer sets after is ignored. */
    private record Tint(VertexConsumer out, int rgb, int alpha) implements VertexConsumer {
        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            out.addVertex(x, y, z).setColor(rgb >> 16 & 0xFF, rgb >> 8 & 0xFF, rgb & 0xFF, alpha).setUv(0.5f, 0.5f)
                    .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(0f, 1f, 0f);
            return this;
        }

        @Override public VertexConsumer setColor(int r, int g, int b, int a) { return this; }
        @Override public VertexConsumer setUv(float u, float v) { return this; }
        @Override public VertexConsumer setUv1(int u, int v) { return this; }
        @Override public VertexConsumer setUv2(int u, int v) { return this; }
        @Override public VertexConsumer setNormal(float x, float y, float z) { return this; }
    }

    // ---------------------------------------------------------------- dials

    /** A face toward the camera at {@code at}: its +Y is twelve, +X three. */
    private static Matrix4f facing(PoseStack pose, Camera camera, Vec3 at, float scale) {
        return pose.last().pose().translate((float) at.x, (float) at.y, (float) at.z, new Matrix4f()).rotate(camera.rotation()).scale(scale);
    }

    /** Rewind's dial: a face snapping up at the target, both hands swept round counter-clockwise, gone in {@code ticks}. */
    private static void rewindDial(VertexConsumer out, PoseStack pose, Camera camera, Vec3 at, float age, int ticks) {
        float t = age / ticks;
        if (t >= 1f) return;
        float alpha = t < 0.15f ? t / 0.15f : 1f - Math.max(0f, (t - 0.6f) / 0.4f);
        Matrix4f m = facing(pose, camera, at, 0.62f * (0.85f + 0.15f * Math.min(1f, age / 2f)));
        face(out, m, BRONZE, BRONZE_PALE, alpha, quality() == VfxQuality.FULL ? 12 : 4);
        float sweep = -Mth.TWO_PI * 1.5f * easeOut(t); // backward, a turn and a half
        hand(out, m, sweep, 0.82f, 0.07f, 0.16f, SILVER, alpha);
        hand(out, m, sweep * 0.5f + Mth.PI, 0.55f, 0.09f, 0.12f, SILVER, alpha);
        disc(out, m, 0.07f, BRONZE, alpha);
    }

    /**
     * Fast Forward: a clock face round the caster, the caster in the middle of it and three hands whirling forward round them, faster
     * and faster. Turned toward whoever watches (a face laid flat at the waist would be edge-on from eye height); in the caster's own
     * first person it's laid flat round their waist instead, where looking down shows it.
     */
    private static void whirl(VertexConsumer out, PoseStack pose, Camera camera, Vec3 at, float radius, float age, int ticks, boolean own) {
        float t = age / ticks;
        if (t >= 1f) return;
        float alpha = t < 0.1f ? t / 0.1f : 1f - Math.max(0f, (t - 0.7f) / 0.3f);
        Matrix4f m = own
                ? pose.last().pose().translate((float) at.x, (float) at.y - 0.15f, (float) at.z, new Matrix4f()).rotate(Axis.XP.rotation(-Mth.HALF_PI)).scale(radius)
                : facing(pose, camera, at, radius);
        face(out, m, BRONZE, BRONZE_PALE, alpha * 0.9f, 12);
        ring(out, m, 0.95f, 0.12f, BRONZE, alpha * 0.5f);
        float turn = Mth.TWO_PI * 3.2f * t * t; // forward, speeding up
        for (int i = 0; i < 3; i++) hand(out, m, turn * (1f + 0.25f * i) + i * Mth.TWO_PI / 3f, 0.92f - 0.12f * i, 0.05f, 0.1f, SILVER, alpha);
    }

    /** A Decelerate hit: a small face flashing up over the target, its hands dragging round slowly for as long as it lasts. */
    private static void slowedDial(VertexConsumer out, PoseStack pose, Camera camera, Vec3 at, float age, int ticks) {
        if (age >= ticks) return;
        float flash = Math.max(0f, 1f - age / 4f);
        float alpha = Math.min(1f, 0.55f + flash) * Math.min(1f, (ticks - age) / 8f);
        Matrix4f m = facing(pose, camera, at, 0.24f * (1f + 0.6f * flash));
        face(out, m, flash > 0.3f ? BRIGHT : BRONZE, BRONZE_PALE, alpha, 4);
        float drag = Mth.TWO_PI * age / ticks; // once round in all that time: barely moving
        hand(out, m, drag, 0.8f, 0.09f, 0.14f, SILVER, alpha);
        hand(out, m, drag / 12f + 2.1f, 0.5f, 0.11f, 0.1f, SILVER, alpha);
    }

    /** A clock face of radius 1 in the matrix's XY plane: its rim and {@code marks} hour marks (the quarters longer). */
    private static void face(VertexConsumer out, Matrix4f m, int rim, int marks, float alpha, int count) {
        ring(out, m, 0.95f, 0.07f, rim, alpha);
        for (int i = 0; i < count; i++) {
            float a = Mth.TWO_PI * i / count;
            boolean quarter = count == 4 || i % 3 == 0;
            float r0 = quarter ? 0.68f : 0.76f, w = quarter ? 0.06f : 0.04f;
            strip(out, m, Mth.sin(a) * r0, Mth.cos(a) * r0, Mth.sin(a) * 0.86f, Mth.cos(a) * 0.86f, w, marks, alpha);
        }
    }

    // ---------------------------------------------------------------- the field

    private static void field(VertexConsumer out, PoseStack pose, Vec3 cam, Field f, float partial) {
        float age = clock - f.born + partial;
        float open = easeOut(Math.min(1f, age / 4f));
        float close = f.closing < 0 ? 1f : 1f - Math.min(1f, (clock - f.closing + partial) / 3f);
        float r = f.radius * open * (0.6f + 0.4f * close);
        float alpha = close;
        if (r <= 0.05f || alpha <= 0.01f) return;
        Vec3 c = f.centre.subtract(cam);
        // The clock on the ground, turning slowly.
        Matrix4f ground = pose.last().pose().translate((float) c.x, (float) c.y + 0.02f, (float) c.z, new Matrix4f())
                .rotate(Axis.XP.rotation(-Mth.HALF_PI)).rotate(Axis.ZP.rotation(-age * 0.008f)).scale(r);
        face(out, ground, BRONZE, BRONZE_PALE, alpha * 0.85f, 12);
        hand(out, ground, age * 0.02f, 0.8f, 0.035f, 0.08f, SILVER, alpha * 0.9f);
        hand(out, ground, age * 0.02f / 12f + 1.3f, 0.52f, 0.045f, 0.06f, SILVER, alpha * 0.9f);
        disc(out, ground, 0.03f, BRONZE, alpha);
        // The dome: clear where you look straight through it, denser toward its edge, a bright rim where it meets the ground.
        int around = quality() == VfxQuality.FULL ? 40 : 20, up = quality() == VfxQuality.FULL ? 12 : 6;
        Matrix4f m = pose.last().pose();
        Vec3 toCam = cam.subtract(f.centre);
        for (int j = 0; j < up; j++) {
            float p0 = Mth.HALF_PI * j / up, p1 = Mth.HALF_PI * (j + 1) / up;
            for (int i = 0; i < around; i++) {
                float a0 = Mth.TWO_PI * i / around, a1 = Mth.TWO_PI * (i + 1) / around;
                domeVertex(out, m, c, r, a0, p0, toCam, alpha);
                domeVertex(out, m, c, r, a1, p0, toCam, alpha);
                domeVertex(out, m, c, r, a1, p1, toCam, alpha);
                domeVertex(out, m, c, r, a0, p1, toCam, alpha);
            }
        }
        Matrix4f rim = pose.last().pose().translate((float) c.x, (float) c.y + 0.03f, (float) c.z, new Matrix4f())
                .rotate(Axis.XP.rotation(-Mth.HALF_PI));
        ring(out, rim, r, 0.07f, BRONZE_PALE, alpha);
    }

    private static void domeVertex(VertexConsumer out, Matrix4f m, Vec3 c, float r, float around, float up, Vec3 toCam, float alpha) {
        float cu = Mth.cos(up);
        float nx = Mth.sin(around) * cu, ny = Mth.sin(up), nz = Mth.cos(around) * cu;
        Vec3 p = new Vec3(nx * r, ny * r, nz * r);
        Vec3 view = toCam.subtract(p).normalize();
        float facing = Math.abs((float) (nx * view.x + ny * view.y + nz * view.z));
        float edge = 1f - facing;
        int a = Math.round(Mth.clamp(alpha * (0.06f + 0.5f * edge * edge * edge), 0f, 1f) * 255);
        out.addVertex(m, (float) (c.x + p.x), (float) (c.y + p.y), (float) (c.z + p.z)).setColor(0xC9, 0xA6, 0x6B, a).setUv(0.5f, 0.5f)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(nx, ny, nz);
    }

    // ---------------------------------------------------------------- shapes (flat, in a matrix's XY plane)

    /** A hand from the pivot out at {@code angle} (from twelve, clockwise), {@code length} long: a slim blade with a short tail. */
    static void hand(VertexConsumer out, Matrix4f m, float angle, float length, float width, float tail, int rgb, float alpha) {
        float dx = Mth.sin(angle), dy = Mth.cos(angle), px = -dy, py = dx;
        int a = Math.round(Mth.clamp(alpha, 0f, 1f) * 255);
        float side = length * 0.18f;
        vertex(out, m, -dx * tail, -dy * tail, rgb, a);
        vertex(out, m, dx * side + px * width / 2, dy * side + py * width / 2, rgb, a);
        vertex(out, m, dx * length, dy * length, rgb, a);
        vertex(out, m, dx * side - px * width / 2, dy * side - py * width / 2, rgb, a);
    }

    static void ring(VertexConsumer out, Matrix4f m, float radius, float width, int rgb, float alpha) {
        int a = Math.round(Mth.clamp(alpha, 0f, 1f) * 255);
        float in = Math.max(0f, radius - width / 2), outer = radius + width / 2;
        for (int i = 0; i < SEGMENTS; i++) {
            float a0 = Mth.TWO_PI * i / SEGMENTS, a1 = Mth.TWO_PI * (i + 1) / SEGMENTS;
            float c0 = Mth.cos(a0), s0 = Mth.sin(a0), c1 = Mth.cos(a1), s1 = Mth.sin(a1);
            vertex(out, m, c0 * in, s0 * in, rgb, a);
            vertex(out, m, c1 * in, s1 * in, rgb, a);
            vertex(out, m, c1 * outer, s1 * outer, rgb, a);
            vertex(out, m, c0 * outer, s0 * outer, rgb, a);
        }
    }

    static void disc(VertexConsumer out, Matrix4f m, float radius, int rgb, float alpha) {
        ring(out, m, radius / 2, radius, rgb, alpha);
    }

    static void strip(VertexConsumer out, Matrix4f m, float x0, float y0, float x1, float y1, float width, int rgb, float alpha) {
        int a = Math.round(Mth.clamp(alpha, 0f, 1f) * 255);
        float dx = x1 - x0, dy = y1 - y0, len = Mth.sqrt(dx * dx + dy * dy);
        if (len < 1.0e-5f) return;
        float px = -dy / len * width / 2, py = dx / len * width / 2;
        vertex(out, m, x0 - px, y0 - py, rgb, a);
        vertex(out, m, x0 + px, y0 + py, rgb, a);
        vertex(out, m, x1 + px, y1 + py, rgb, a);
        vertex(out, m, x1 - px, y1 - py, rgb, a);
    }

    static void vertex(VertexConsumer out, Matrix4f m, float x, float y, int rgb, int alpha) {
        out.addVertex(m, x, y, 0f).setColor(rgb >> 16 & 0xFF, rgb >> 8 & 0xFF, rgb & 0xFF, alpha).setUv(0.5f, 0.5f)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(0f, 0f, 1f);
    }

    static float easeOut(float t) {
        float u = 1f - Mth.clamp(t, 0f, 1f);
        return 1f - u * u * u;
    }

    /** For the tests: the afterimages queued right now. */
    static int ghosts() {
        return GHOSTS.size();
    }
}

package com.cnethuka.kegare.innate_technique.rime.client;

import com.cnethuka.kegare.Kegare;
import com.cnethuka.kegare.client.visual.ClientVisuals;
import com.cnethuka.kegare.config.KegareClientConfig;
import com.cnethuka.kegare.config.KegareClientConfig.VfxQuality;
import com.cnethuka.kegare.innate_technique.rime.GlacialLanceEntity;
import com.cnethuka.kegare.innate_technique.rime.RimeIds;
import com.cnethuka.kegare.innatetechnique.visual.Visual;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * What's left when a Glacial Lance comes down, all on the client and none of it touching the world: a lance that hit a block stays
 * stuck in it, quivering, for about a second and then shatters; one that hit something bursts; one let go too soon crumbles out of
 * the hand. The shards are solid little pieces of the same ice, and frost marks spread over the ground and melt again a few seconds
 * later without a single block changing. A bigger charge makes all of it bigger.
 * <p>
 * The server says only where and which (stuck or burst) and names the lance; its charge and heading are read off the lance itself,
 * which is still there when the message arrives.
 */
public final class LanceAftermath {
    public static final ResourceLocation FROST_MARK = Kegare.id("textures/entity/glacial_lance_frost.png");
    /** How long a lance stays stuck before it shatters. */
    static final int STUCK_TICKS = 20;
    /** How deep a stuck lance's tip goes in. */
    private static final float BURIED = 0.32f;
    private static final int QUIVER_TICKS = 7;
    /** The frost-mark sheet: 4 x 2 frames, from whole (0) to nearly melted (7). */
    static final int MARK_FRAMES = 8;
    private static final int MARK_GROW_TICKS = 4;
    private static final int MARK_MELT_TICKS = 24;
    /** Effects closer than this to your own first-person camera are left out: they'd be in your face. */
    /** How far past the feet's hit the middle line is looked along for the block (a shallow throw into the ground meets it later). */
    private static final double MIDDLE_REACH = 2.0;
    private static final double NEAR_SELF = 2.0;

    private static final class Shard {
        Vec3 pos;
        Vec3 last;
        Vec3 vel;
        final Vector3f axis;
        float angle;
        float lastAngle;
        float spin;
        final float size;
        final int kind;
        final int life;
        int age;
        int bounces;

        Shard(Vec3 pos, Vec3 vel, Vector3f axis, float spin, float size, int kind, int life) {
            this.pos = this.last = pos;
            this.vel = vel;
            this.axis = axis;
            this.spin = spin;
            this.size = size;
            this.kind = kind;
            this.life = life;
        }
    }

    private static final class Stuck {
        final Vec3 tip;
        final Vector3f dir;
        final float[] scales;
        final float charge;
        final float roll;
        int age;

        Stuck(Vec3 tip, Vector3f dir, float[] scales, float charge, float roll) {
            this.tip = tip;
            this.dir = dir;
            this.scales = scales;
            this.charge = charge;
            this.roll = roll;
        }
    }

    /**
     * A frost mark: a square on one face of the blocks, drawn only over the cells where that face is really there. {@code variant}
     * turns and mirrors the sheet (eight ways) so two marks side by side don't match.
     */
    record Mark(Vec3 centre, Direction face, float radius, int variant, int life, int[] age) {}

    private static final List<Shard> SHARDS = new ArrayList<>();
    private static final List<Stuck> STUCK = new ArrayList<>();
    private static final List<Mark> MARKS = new ArrayList<>();
    private static final RandomSource RANDOM = RandomSource.create();

    private LanceAftermath() {}

    static void init() {
        ClientVisuals.onPlayAt(RimeIds.FX_GLACIAL_LANCE_STICK, LanceAftermath::onStick);
        ClientVisuals.onPlayAt(RimeIds.FX_GLACIAL_LANCE_IMPACT, LanceAftermath::onBurst);
    }

    /** How much bigger than a minimum charge everything is at this charge. */
    static float size(float charge) {
        return 0.75f + 0.5f * Mth.clamp(charge, 0f, 1f);
    }

    private static VfxQuality quality() {
        return KegareClientConfig.VFX_QUALITY.get();
    }

    // ---------------------------------------------------------------------------------------------- arriving

    private static float chargeOf(Entity e) {
        return e instanceof GlacialLanceEntity lance ? lance.charge() : 0.5f;
    }

    private static Vector3f headingOf(Entity e) {
        Vec3 v = e == null ? Vec3.ZERO : e.getDeltaMovement();
        if (v.lengthSqr() < 1.0e-6) return new Vector3f(0f, -1f, 0f);
        return new Vector3f((float) v.x, (float) v.y, (float) v.z).normalize();
    }

    /** It hit a block: it stays there, tip in, then shatters. */
    static boolean onStick(ClientLevel level, Vec3 pos, int entityId, Visual visual) {
        Entity e = entityId < 0 ? null : level.getEntity(entityId);
        float charge = chargeOf(e);
        Vector3f dir = headingOf(e);
        Vec3 d = new Vec3(dir.x, dir.y, dir.z);
        // The server finds the block along the line of the lance's feet; it's drawn, and trails, half its height above that. Where that
        // middle line meets the block is where it's seen to strike, unless it passes over (the top of a wall): then where the feet did.
        Vec3 middle = pos.add(0, e == null ? 0 : e.getBbHeight() * 0.5, 0);
        BlockHitResult hit = clip(level, middle.subtract(d.scale(0.6)), middle.add(d.scale(MIDDLE_REACH)));
        if (hit.getType() == HitResult.Type.MISS) hit = clip(level, pos.subtract(d.scale(0.6)), pos.add(d.scale(0.6)));
        Direction face = hit.getType() == HitResult.Type.MISS ? Direction.UP : hit.getDirection();
        Vec3 at = hit.getType() == HitResult.Type.MISS ? pos : hit.getLocation();
        float minimum = LanceShape.minimum();
        float[] scales = new float[LanceShape.PIECES.size()];
        for (int i = 0; i < scales.length; i++) scales[i] = LanceShape.shown(i, charge, minimum) ? 1f : 0f;
        float roll = e == null ? 0f : (e.tickCount * 11f) % 360f;
        STUCK.add(new Stuck(at, dir, scales, charge, roll));
        mark(level, at, face, charge, 1f);
        if (face.getAxis().isHorizontal()) groundMarkBelow(level, at, charge, 0.7f);
        ClientVisuals.playAt(level, at.add(face.getStepX() * 0.15, face.getStepY() * 0.15, face.getStepZ() * 0.15),
                RimeIds.FX_GLACIAL_LANCE_STICK, 0, size(charge));
        return true;
    }

    private static BlockHitResult clip(ClientLevel level, Vec3 from, Vec3 to) {
        return level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty()));
    }

    /** It hit something living, or ran out of range: it bursts where it is. */
    static boolean onBurst(ClientLevel level, Vec3 pos, int entityId, Visual visual) {
        Entity e = entityId < 0 ? null : level.getEntity(entityId);
        float charge = chargeOf(e);
        Vector3f dir = headingOf(e);
        groundMarkBelow(level, pos, charge, 0.85f);
        if (nearOwnCamera(pos)) return true; // it hit you: the hurt flash says so, and the burst would be in your eyes
        shards(level, pos, dir, Math.round(8 + 12 * charge), size(charge), 0.32f, 0.12f);
        ClientVisuals.playAt(level, pos, RimeIds.FX_GLACIAL_LANCE_IMPACT, 0, size(charge));
        return true;
    }

    /** What was in the hand when a charge stopped without a throw: it breaks up where it was and falls. */
    static void crumble(ClientLevel level, Matrix4f toWorld, float[] scales) {
        boolean self = false;
        for (int i = 0; i < scales.length; i++) {
            if (scales[i] <= 0f) continue;
            LanceMesh.Piece piece = LanceMesh.piece(LanceShape.PIECES.get(i));
            if (piece == null) continue;
            int n = i == 0 ? 3 : 2;
            for (int k = 0; k < n; k++) {
                float z = Mth.lerp((k + 0.5f) / n, piece.minZ(), piece.maxZ()) * scales[i];
                Vector3f p = toWorld.transformPosition(new Vector3f(0f, 0f, z));
                Vec3 at = new Vec3(p.x, p.y, p.z);
                self |= nearOwnCamera(at);
                Vec3 vel = new Vec3(RANDOM.nextGaussian() * 0.03, 0.02 + RANDOM.nextFloat() * 0.05, RANDOM.nextGaussian() * 0.03);
                spawnShard(level, at, vel, 0.55f + RANDOM.nextFloat() * 0.35f);
            }
        }
        Vector3f mid = toWorld.transformPosition(new Vector3f(0f, 0f, 0.1f));
        if (!self) ClientVisuals.playAt(level, new Vec3(mid.x, mid.y, mid.z), RimeIds.FX_GLACIAL_LANCE_SHATTER, 0, 0.3f);
    }

    private static void shatter(ClientLevel level, Stuck s) {
        // Shards from all along what was sticking out, flung off the surface it was in.
        Vector3f back = new Vector3f(s.dir).negate();
        float length = GlacialLanceRenderer.SCALE * 2.4f;
        int n = Math.round(6 + 10 * s.charge);
        for (int i = 0; i < n; i++) {
            float along = BURIED + RANDOM.nextFloat() * length * 0.8f;
            Vec3 at = s.tip.add(back.x * along, back.y * along, back.z * along);
            Vec3 vel = new Vec3(back.x * 0.12 + RANDOM.nextGaussian() * 0.09, 0.1 + RANDOM.nextFloat() * 0.14, back.z * 0.12 + RANDOM.nextGaussian() * 0.09);
            spawnShard(level, at, vel, size(s.charge) * (0.8f + RANDOM.nextFloat() * 0.5f));
        }
        float mid = BURIED + length * 0.35f;
        ClientVisuals.playAt(level, s.tip.add(back.x * mid, back.y * mid, back.z * mid), RimeIds.FX_GLACIAL_LANCE_SHATTER, 0, size(s.charge));
    }

    /** A spray of shards from a point, mostly onward along {@code dir}. */
    private static void shards(ClientLevel level, Vec3 at, Vector3f dir, int count, float size, float speed, float lift) {
        for (int i = 0; i < count; i++) {
            Vec3 vel = new Vec3(dir.x * speed * 0.5 + RANDOM.nextGaussian() * speed * 0.6,
                    lift + RANDOM.nextFloat() * speed * 0.6 + dir.y * speed * 0.3,
                    dir.z * speed * 0.5 + RANDOM.nextGaussian() * speed * 0.6);
            spawnShard(level, at, vel, size * (0.7f + RANDOM.nextFloat() * 0.7f));
        }
    }

    private static void spawnShard(ClientLevel level, Vec3 at, Vec3 vel, float size) {
        VfxQuality quality = quality();
        if (quality == VfxQuality.VANILLA) {
            // Vanilla particles only: chips of packed ice, as many as the shards would have been.
            level.addParticle(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.PACKED_ICE.defaultBlockState()), at.x, at.y, at.z, vel.x, vel.y, vel.z);
            return;
        }
        if (quality == VfxQuality.REDUCED && RANDOM.nextBoolean()) return;
        Vector3f axis = new Vector3f((float) RANDOM.nextGaussian(), (float) RANDOM.nextGaussian(), (float) RANDOM.nextGaussian());
        if (axis.lengthSquared() < 1.0e-4f) axis.set(0f, 1f, 0f);
        SHARDS.add(new Shard(at, vel, axis.normalize(), (RANDOM.nextFloat() - 0.5f) * 0.9f, size, RANDOM.nextInt(LanceShape.SHARDS),
                24 + RANDOM.nextInt(14)));
    }

    // ---------------------------------------------------------------------------------------------- frost marks

    static float markRadius(float charge) {
        return 0.55f + 0.95f * Mth.clamp(charge, 0f, 1f);
    }

    static int markLife(float charge) {
        return Math.round(60 + 40 * Mth.clamp(charge, 0f, 1f));
    }

    private static void mark(ClientLevel level, Vec3 at, Direction face, float charge, float size) {
        if (quality() == VfxQuality.VANILLA) return;
        MARKS.add(new Mark(at, face, markRadius(charge) * size, RANDOM.nextInt(8), markLife(charge), new int[1]));
    }

    private static void groundMarkBelow(ClientLevel level, Vec3 from, float charge, float size) {
        BlockHitResult down = level.clip(new ClipContext(from, from.add(0, -4, 0), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE,
                CollisionContext.empty()));
        if (down.getType() == HitResult.Type.BLOCK && down.getDirection() == Direction.UP) mark(level, down.getLocation(), Direction.UP, charge, size);
    }

    /** Which frame of the sheet a mark shows at this age: it spreads out in a few ticks, holds, then melts away from the edge in. */
    static int markFrame(int age, int life) {
        if (age < MARK_GROW_TICKS) return Math.max(0, (MARK_FRAMES - 2) - age * (MARK_FRAMES - 2) / MARK_GROW_TICKS);
        int left = life - age;
        if (left >= MARK_MELT_TICKS) return 0;
        return Mth.clamp(1 + (MARK_MELT_TICKS - left) * (MARK_FRAMES - 1) / MARK_MELT_TICKS, 1, MARK_FRAMES - 1);
    }

    // ---------------------------------------------------------------------------------------------- per tick

    static void tick(ClientLevel level) {
        for (Iterator<Shard> it = SHARDS.iterator(); it.hasNext(); ) {
            Shard s = it.next();
            s.last = s.pos;
            s.lastAngle = s.angle;
            if (++s.age >= s.life) {
                it.remove();
                continue;
            }
            s.vel = new Vec3(s.vel.x * 0.96, s.vel.y * 0.96 - 0.045, s.vel.z * 0.96);
            Vec3 next = s.pos.add(s.vel);
            BlockPos cell = BlockPos.containing(next);
            BlockState state = level.getBlockState(cell);
            if (!state.getCollisionShape(level, cell).isEmpty() && s.vel.y < 0) {
                double top = cell.getY() + state.getCollisionShape(level, cell).max(Direction.Axis.Y);
                next = new Vec3(next.x, top + 0.04, next.z);
                s.vel = s.bounces++ < 1 ? new Vec3(s.vel.x * 0.5, -s.vel.y * 0.28, s.vel.z * 0.5) : Vec3.ZERO;
                s.spin *= 0.4f;
            }
            s.pos = next;
            s.angle += s.spin;
        }
        for (Iterator<Stuck> it = STUCK.iterator(); it.hasNext(); ) {
            Stuck s = it.next();
            if (++s.age >= STUCK_TICKS) {
                it.remove();
                shatter(level, s);
            }
        }
        MARKS.removeIf(m -> ++m.age()[0] >= m.life());
    }

    static void clear() {
        SHARDS.clear();
        STUCK.clear();
        MARKS.clear();
    }

    private static boolean nearOwnCamera(Vec3 at) {
        Minecraft mc = Minecraft.getInstance();
        if (!mc.options.getCameraType().isFirstPerson()) return false;
        return mc.gameRenderer.getMainCamera().getPosition().distanceToSqr(at) < NEAR_SELF * NEAR_SELF;
    }

    // ---------------------------------------------------------------------------------------------- drawing

    static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return;
        if (SHARDS.isEmpty() && STUCK.isEmpty() && MARKS.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) return;
        Camera camera = event.getCamera();
        Vec3 cam = camera.getPosition();
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        PoseStack pose = new PoseStack();
        for (Stuck s : STUCK) drawStuck(pose, buffers, level, cam, s, partial);
        for (Shard s : SHARDS) drawShard(pose, buffers, level, cam, s, partial);
        if (!MARKS.isEmpty()) {
            VertexConsumer out = buffers.getBuffer(RenderType.entityCutoutNoCull(FROST_MARK));
            for (Mark m : MARKS) drawMark(out, level, cam, m);
        }
        buffers.endLastBatch();
    }

    private static void drawStuck(PoseStack pose, MultiBufferSource buffers, ClientLevel level, Vec3 cam, Stuck s, float partial) {
        pose.pushPose();
        pose.translate(s.tip.x - cam.x, s.tip.y - cam.y, s.tip.z - cam.z);
        pose.mulPose(new Quaternionf().rotationTo(new Vector3f(0f, 0f, 1f), s.dir));
        // A heavy thing that has just hit: a short shudder across its length that dies away.
        float t = s.age + partial;
        if (t < QUIVER_TICKS) {
            float wobble = 2.6f * (1f - t / QUIVER_TICKS) * Mth.sin(t * 2.4f);
            pose.mulPose(Axis.XP.rotationDegrees(wobble));
        }
        pose.mulPose(Axis.ZP.rotationDegrees(s.roll));
        float tip = 0f;
        for (int i = 0; i < s.scales.length; i++) {
            LanceMesh.Piece piece = s.scales[i] > 0f ? LanceMesh.piece(LanceShape.PIECES.get(i)) : null;
            if (piece != null) tip = Math.max(tip, piece.maxZ());
        }
        float scale = GlacialLanceRenderer.SCALE;
        pose.translate(0f, 0f, BURIED - tip * scale);
        pose.scale(scale, scale, scale);
        Vec3 out = s.tip.subtract(s.dir.x * 0.4, s.dir.y * 0.4, s.dir.z * 0.4);
        LanceMesh.draw(pose.last(), buffers, LanceShape.PIECES, s.scales, LevelRenderer.getLightColor(level, BlockPos.containing(out)));
        pose.popPose();
    }

    private static void drawShard(PoseStack pose, MultiBufferSource buffers, ClientLevel level, Vec3 cam, Shard s, float partial) {
        Vec3 at = s.last.lerp(s.pos, partial);
        float age = s.age + partial;
        float shrink = Mth.clamp((s.life - age) / 6f, 0f, 1f); // the last few ticks it shrinks away, edges still hard
        if (shrink <= 0f) return;
        pose.pushPose();
        pose.translate(at.x - cam.x, at.y - cam.y, at.z - cam.z);
        pose.mulPose(new Quaternionf().rotationAxis(Mth.lerp(partial, s.lastAngle, s.angle), s.axis));
        float size = s.size * shrink;
        pose.scale(size, size, size);
        String name = "shard_" + s.kind;
        LanceMesh.draw(pose.last(), buffers, List.of(name), new float[] {1f}, LevelRenderer.getLightColor(level, BlockPos.containing(at)));
        pose.popPose();
    }

    private static void drawMark(VertexConsumer out, ClientLevel level, Vec3 cam, Mark m) {
        int frame = markFrame(m.age()[0], m.life());
        float u0 = (frame % 4) / 4f, v0 = (frame / 4) / 2f;
        Direction face = m.face();
        // The mark's plane: two axes across the face, and the axis it faces along.
        Direction.Axis normal = face.getAxis();
        Direction.Axis a = normal == Direction.Axis.Y ? Direction.Axis.X : normal == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X;
        Direction.Axis b = normal == Direction.Axis.Y ? Direction.Axis.Z : Direction.Axis.Y;
        double plane = m.centre().get(normal) + face.getAxisDirection().getStep() * 0.012;
        double ca = m.centre().get(a), cb = m.centre().get(b);
        float r = m.radius();
        int minA = Mth.floor(ca - r), maxA = Mth.floor(ca + r), minB = Mth.floor(cb - r), maxB = Mth.floor(cb + r);
        // The block that carries the mark sits behind the face; the cell in front of it must be open.
        int behind = Mth.floor(m.centre().get(normal) - face.getAxisDirection().getStep() * 0.01);
        for (int ia = minA; ia <= maxA; ia++) {
            for (int ib = minB; ib <= maxB; ib++) {
                BlockPos solid = cell(normal, behind, a, ia, b, ib);
                BlockPos open = solid.relative(face);
                if (!level.getBlockState(solid).isFaceSturdy(level, solid, face)) continue;
                if (level.getBlockState(open).isFaceSturdy(level, open, face.getOpposite())) continue;
                double a0 = Math.max(ia, ca - r), a1 = Math.min(ia + 1, ca + r), b0 = Math.max(ib, cb - r), b1 = Math.min(ib + 1, cb + r);
                if (a1 <= a0 || b1 <= b0) continue;
                // The world's own light, no floor: a floor lifts block light, which is warm, and frost would go brown at night.
                int light = LevelRenderer.getLightColor(level, open);
                double[][] corners = {{a0, b0}, {a1, b0}, {a1, b1}, {a0, b1}};
                for (double[] c : corners) {
                    // Where this corner falls in the mark (0 to 1 across it), turned and mirrored its own way, in this frame of the sheet.
                    float x = (float) ((c[0] - ca) / r) * 0.5f + 0.5f, y = (float) ((c[1] - cb) / r) * 0.5f + 0.5f;
                    if ((m.variant() & 1) != 0) x = 1f - x;
                    if ((m.variant() & 2) != 0) y = 1f - y;
                    if ((m.variant() & 4) != 0) {
                        float swap = x;
                        x = y;
                        y = swap;
                    }
                    float u = u0 + x / 4f, v = v0 + y / 2f;
                    Vec3 p = point(normal, plane, a, c[0], b, c[1]);
                    out.addVertex((float) (p.x - cam.x), (float) (p.y - cam.y), (float) (p.z - cam.z))
                            .setColor(255, 255, 255, 255).setUv(u, v)
                            .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light)
                            .setNormal(face.getStepX(), face.getStepY(), face.getStepZ());
                }
            }
        }
    }

    private static BlockPos cell(Direction.Axis normal, int n, Direction.Axis a, int ia, Direction.Axis b, int ib) {
        int[] xyz = new int[3];
        xyz[normal.ordinal()] = n;
        xyz[a.ordinal()] = ia;
        xyz[b.ordinal()] = ib;
        return new BlockPos(xyz[0], xyz[1], xyz[2]);
    }

    private static Vec3 point(Direction.Axis normal, double n, Direction.Axis a, double va, Direction.Axis b, double vb) {
        double[] xyz = new double[3];
        xyz[normal.ordinal()] = n;
        xyz[a.ordinal()] = va;
        xyz[b.ordinal()] = vb;
        return new Vec3(xyz[0], xyz[1], xyz[2]);
    }
}

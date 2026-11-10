package com.cnethuka.kegare.innate_technique.rime.client;

import com.cnethuka.kegare.Kegare;
import com.cnethuka.kegare.client.visual.ClientVisuals;
import com.cnethuka.kegare.config.KegareClientConfig;
import com.cnethuka.kegare.config.KegareClientConfig.VfxQuality;
import com.cnethuka.kegare.innate_technique.rime.Rime;
import com.cnethuka.kegare.innate_technique.rime.RimeConfig;
import com.cnethuka.kegare.innate_technique.rime.RimeIds;
import com.cnethuka.kegare.innatetechnique.visual.VisualPhase;
import com.cnethuka.kegare.innatetechnique.visual.VisualService;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * Frost Field's look round its cone, all on the client and read off the server's own channel loop on the caster, so every client
 * shows it while it really runs and it stops when it does:
 * <ul>
 * <li>pixel frost racing out over the ground the cone really covers (in range, inside its half-angle, in sight of the eyes), and
 * on any wall it meets: block marks ({@code marks/field_frost}) on the blocks' own texels, kept up while it's held and melting
 * from the edge in once it's gone;</li>
 * <li>small solid crystals of the same ice jutting out of the frosted ground for a moment and sinking back
 * ({@code models/fx/frost_field.obj});</li>
 * <li>cold mist lying on the frost ({@code frost_field_mist}).</li>
 * </ul>
 * No block is touched. Nothing here comes within reach of your own first-person camera.
 */
public final class FieldClient {
    /** Two patches of the same frost, so the mat doesn't repeat. */
    static final ResourceLocation FROST = Kegare.id("field_frost"), FROST_B = Kegare.id("field_frost_b");
    static final String CHANNEL = VisualService.key(Rime.FROST_FIELD, VisualPhase.CHANNEL_LOOP);
    /** How fast the frost races out from the caster when the channel starts: ticks a block. */
    static final float RACE_TICKS_PER_BLOCK = 0.8f;
    /** How often the frost is laid again while it's held (overlapping texels just live longer). */
    static final int REFRESH_TICKS = 3;
    /** Ticks: a patch forms over SPREAD, holds HOLD past its last refresh, then melts from the edge in over MELT. */
    static final int SPREAD = 5, HOLD = 7, MELT = 30;
    /** How much of a patch shows: nearly all, short of its outermost specks, so the frost doesn't run far past the cone's edge. */
    static final float REACH = 0.85f;
    /** A crystal shoots up over this, holds a moment, then sinks back over this (ticks). */
    static final int RISE = 2, SINK = 4;
    /** No crystal within this many blocks of your own first-person camera. */
    static final double CLEAR_OF_OWN_EYES = 2.2;
    private static final RandomSource RANDOM = RandomSource.create();

    private static final class Crystal {
        final Vec3 pos;
        final String piece;
        final float yaw, lean, size;
        final int hold;
        int age;

        Crystal(Vec3 pos, String piece, float yaw, float lean, float size, int hold) {
            this.pos = pos;
            this.piece = piece;
            this.yaw = yaw;
            this.lean = lean;
            this.size = size;
            this.hold = hold;
        }

        int life() {
            return RISE + hold + SINK;
        }
    }

    private static final List<Crystal> CRYSTALS = new ArrayList<>();

    private FieldClient() {}

    // ---------------------------------------------------------------------------------------------- the cone

    /** Whether a point is in the cone: within {@code range} of the eyes and within the half-angle ({@code cos}) of the look. */
    static boolean inCone(Vec3 eye, Vec3 look, Vec3 at, double range, double cos) {
        Vec3 to = at.subtract(eye);
        double d = to.length();
        return d <= range && d > 1.0e-3 && to.scale(1.0 / d).dot(look) >= cos;
    }

    /** How many ticks after now a patch {@code distance} blocks from the caster forms, the channel having run {@code age} ticks. */
    static int raceDelay(double distance, int age) {
        return Math.max(0, Math.round((float) distance * RACE_TICKS_PER_BLOCK) - age);
    }

    /** A crystal's height out of the ground at {@code t} ticks: shoots up (a little past, and back), holds, sinks; 0 to about 1. */
    static float risen(float t, int hold) {
        if (t <= 0f) return 0f;
        if (t < RISE) {
            float f = t / RISE;
            return 1f - (1f - f) * (1f - f) + 0.08f * Mth.sin(f * Mth.PI);
        }
        if (t < RISE + hold) return 1f;
        float f = Mth.clamp((t - RISE - hold) / SINK, 0f, 1f);
        return 1f - f * f;
    }

    private static double range() {
        try {
            return RimeConfig.FIELD_RANGE.get();
        } catch (IllegalStateException notLoaded) {
            return 6.0;
        }
    }

    private static double halfAngle() {
        try {
            return RimeConfig.FIELD_HALF_ANGLE.get();
        } catch (IllegalStateException notLoaded) {
            return 35.0;
        }
    }

    /** The top of the ground in a column near {@code feet} height, or NaN: a block whose top is solid with room above it. */
    private static double groundTop(ClientLevel level, double x, double feet, double z) {
        int top = Mth.floor(feet + 1.0);
        for (int y = top; y >= top - 4; y--) {
            BlockPos pos = BlockPos.containing(x, y, z);
            BlockState state = level.getBlockState(pos);
            if (!state.isFaceSturdy(level, pos, Direction.UP)) continue;
            BlockPos above = pos.above();
            if (level.getBlockState(above).isFaceSturdy(level, above, Direction.DOWN)) continue;
            return y + 1.0;
        }
        return Double.NaN;
    }

    /** The ground the cone really covers: block-top points in it that the eyes can see. */
    static List<Vec3> footprint(ClientLevel level, Player caster) {
        double range = range(), cos = Math.cos(Math.toRadians(halfAngle()));
        Vec3 eye = caster.getEyePosition();
        Vec3 look = caster.getLookAngle();
        List<Vec3> out = new ArrayList<>();
        int r = Mth.ceil(range);
        int bx = caster.getBlockX(), bz = caster.getBlockZ();
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                double x = bx + dx + 0.5, z = bz + dz + 0.5;
                double top = groundTop(level, x, caster.getY(), z);
                if (Double.isNaN(top)) continue;
                Vec3 at = new Vec3(x, top, z);
                if (!inCone(eye, look, at, range, cos)) continue;
                BlockHitResult hit = level.clip(new ClipContext(eye, at.add(0, 0.1, 0), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE,
                        CollisionContext.empty()));
                if (hit.getType() != HitResult.Type.MISS) continue;
                out.add(at);
            }
        }
        return out;
    }

    /** Where rays through the cone meet a wall (not the ground: the footprint has that): the point and the face it hit. */
    private static List<BlockHitResult> walls(ClientLevel level, Player caster) {
        double range = range(), half = Math.toRadians(halfAngle());
        Vec3 eye = caster.getEyePosition();
        Vec3 look = caster.getLookAngle();
        Vec3 side = look.cross(new Vec3(0, 1, 0));
        side = side.lengthSqr() < 1.0e-6 ? new Vec3(1, 0, 0) : side.normalize();
        Vec3 up = side.cross(look).normalize();
        List<BlockHitResult> out = new ArrayList<>();
        for (int ring = 0; ring < 3; ring++) {
            double off = half * new double[] {0, 0.55, 0.95}[ring];
            int n = ring == 0 ? 1 : 8;
            for (int k = 0; k < n; k++) {
                double a = 2 * Math.PI * k / n + ring * 0.4;
                Vec3 dir = look.scale(Math.cos(off)).add(side.scale(Math.sin(off) * Math.cos(a))).add(up.scale(Math.sin(off) * Math.sin(a)));
                BlockHitResult hit = level.clip(new ClipContext(eye, eye.add(dir.scale(range)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE,
                        CollisionContext.empty()));
                if (hit.getType() == HitResult.Type.BLOCK && hit.getDirection().getAxis().isHorizontal()) out.add(hit);
            }
        }
        return out;
    }

    // ---------------------------------------------------------------------------------------------- per tick

    static void tick(ClientLevel level) {
        for (Iterator<Crystal> it = CRYSTALS.iterator(); it.hasNext(); ) {
            Crystal c = it.next();
            if (++c.age >= c.life()) it.remove();
        }
        VfxQuality quality = KegareClientConfig.VFX_QUALITY.get();
        for (Player caster : level.players()) {
            ClientVisuals.LoopTime time = ClientVisuals.loopTime(caster, CHANNEL, 0f);
            if (time == null) continue;
            int age = (int) time.ticks();
            boolean refresh = age % REFRESH_TICKS == 0;
            boolean crystal = RANDOM.nextFloat() < (quality == VfxQuality.REDUCED ? 0.3f : 0.6f);
            boolean mist = age % (quality == VfxQuality.REDUCED ? 8 : 4) == 0;
            if (!refresh && !crystal && !mist) continue;
            List<Vec3> ground = footprint(level, caster);
            if (refresh) frost(level, caster, ground, age);
            if (ground.isEmpty()) continue;
            if (crystal) crystal(caster, ground);
            if (mist) {
                Vec3 at = ground.get(RANDOM.nextInt(ground.size()));
                ClientVisuals.playAt(level, at.add(RANDOM.nextFloat() - 0.5, 0.15, RANDOM.nextFloat() - 0.5), RimeIds.FX_FROST_FIELD_MIST, 0, 1f);
            }
        }
    }

    /**
     * Whether the frost is laid from this block: every other one, like a chessboard's (about 1.4 blocks apart), so the patches overlap
     * into a mat with the ground still showing through, not a white sheet.
     */
    static boolean seeds(int x, int z) {
        return Math.floorMod(x + z, 2) == 0;
    }

    /** A block's own patch: where in it, and turned which way, always the same, so laying it again only keeps it alive. */
    static int cellHash(int x, int z) {
        int h = x * 73856093 ^ z * 19349663;
        return h ^ (h >>> 13);
    }

    private static void frost(ClientLevel level, Player caster, List<Vec3> ground, int age) {
        BlockMarks.Pattern p = BlockMarks.pattern(FROST), q = BlockMarks.pattern(FROST_B);
        if (p == null) return;
        Vec3 feet = caster.position();
        for (Vec3 at : ground) {
            int x = Mth.floor(at.x), z = Mth.floor(at.z);
            if (!seeds(x, z)) continue;
            int h = cellHash(x, z);
            BlockMarks.Pattern patch = q != null && ((h >> 20) & 1) == 1 ? q : p;
            Vec3 placed = at.add(((h & 255) / 255f - 0.5f) * 0.7, 0, (((h >> 8) & 255) / 255f - 0.5f) * 0.7);
            int delay = raceDelay(Math.hypot(at.x - feet.x, at.z - feet.z), age);
            BlockMarks.stamp(level, patch, placed, Direction.UP, (h >> 16) & 7, new BlockMarks.Timing(REACH, delay, SPREAD, HOLD, MELT));
        }
        // On a wall, as on the ground: one patch a block face at most, from every other one, each always the same.
        Set<BlockPos> laid = new HashSet<>();
        for (BlockHitResult wall : walls(level, caster)) {
            BlockPos block = wall.getBlockPos();
            Direction face = wall.getDirection();
            Direction.Axis across = face.getAxis() == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X;
            if (!seeds(block.get(across), block.getY()) || !laid.add(block)) continue;
            int h = cellHash(block.get(across) * 7 + block.get(face.getAxis()), block.getY());
            Vec3 centre = Vec3.atCenterOf(block).add(face.getStepX() * 0.5, face.getStepY() * 0.5, face.getStepZ() * 0.5);
            Vec3 placed = centre.add(across == Direction.Axis.X ? ((h & 255) / 255f - 0.5f) * 0.6 : 0, (((h >> 8) & 255) / 255f - 0.5f) * 0.6,
                    across == Direction.Axis.Z ? ((h & 255) / 255f - 0.5f) * 0.6 : 0);
            BlockMarks.Pattern patch = q != null && ((h >> 20) & 1) == 1 ? q : p;
            int delay = raceDelay(placed.distanceTo(feet), age);
            BlockMarks.stamp(level, patch, placed, face, (h >> 16) & 7, new BlockMarks.Timing(REACH * 0.8f, delay, SPREAD, HOLD, MELT));
        }
    }

    private static void crystal(Player caster, List<Vec3> ground) {
        Vec3 at = ground.get(RANDOM.nextInt(ground.size())).add((RANDOM.nextFloat() - 0.5) * 0.8, 0, (RANDOM.nextFloat() - 0.5) * 0.8);
        if (LanceAftermath.nearOwnCamera(at.add(0, 0.4, 0), CLEAR_OF_OWN_EYES)) return;
        // Leaning away from the caster, as if the frost threw them up as it ran past.
        float away = (float) Math.toDegrees(Math.atan2(at.z - caster.getZ(), at.x - caster.getX()));
        float size = 0.8f + RANDOM.nextFloat() * 0.45f;
        CRYSTALS.add(new Crystal(at, "cluster_" + RANDOM.nextInt(4), away, 8f + RANDOM.nextFloat() * 14f, size, 5 + RANDOM.nextInt(5)));
    }

    static void clear() {
        CRYSTALS.clear();
    }

    // ---------------------------------------------------------------------------------------------- drawing

    static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES || CRYSTALS.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) return;
        Vec3 cam = event.getCamera().getPosition();
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        PoseStack pose = new PoseStack();
        for (Crystal c : CRYSTALS) {
            float up = risen(c.age + partial, c.hold);
            if (up <= 0f) continue;
            pose.pushPose();
            pose.translate(c.pos.x - cam.x, c.pos.y - cam.y, c.pos.z - cam.z);
            // Its own up is +Y with the ground at 0: turned to lean away, then pushed down into the ground until it has risen.
            pose.mulPose(Axis.YP.rotationDegrees(-c.yaw));
            pose.mulPose(Axis.ZP.rotationDegrees(-c.lean));
            pose.translate(0f, -(1f - up) * 0.85f * c.size, 0f);
            pose.scale(c.size, c.size, c.size);
            LanceMesh.FIELD.draw(pose.last(), buffers, List.of(c.piece), new float[] {1f},
                    LevelRenderer.getLightColor(level, BlockPos.containing(c.pos.add(0, 0.3, 0))));
            pose.popPose();
        }
        buffers.endLastBatch();
    }

    /** For tests: the crystals now standing. */
    static int crystals() {
        return CRYSTALS.size();
    }
}

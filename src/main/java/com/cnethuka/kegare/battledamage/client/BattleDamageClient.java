package com.cnethuka.kegare.battledamage.client;

import com.cnethuka.kegare.Kegare;
import com.cnethuka.kegare.battledamage.BattleDamagePayload;
import com.cnethuka.kegare.config.KegareClientConfig;
import com.cnethuka.kegare.config.KegareClientConfig.VfxQuality;
import com.cnethuka.kegare.innate_technique.rime.client.BlockMarks;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Battle damage on this client (DESIGN §3.24). Each broken block (in COSMETIC mode, each that would have broken) throws
 * <b>debris</b>: small solid chunks cut from its own texture that tumble, bounce once and are gone in about a second. Once the world
 * has caught up, <b>cracks</b> mark what survived: the surfaces round the hit and the rim of the hole, as pixel art on the block grid
 * ({@code textures/marks/battle_crack} through {@link BlockMarks}, as Rime's frost), reaching further the harder the hit and fading
 * after half a minute. Reduced: fewer chunks and cracks. Vanilla: vanilla's block particles, no cracks.
 */
public final class BattleDamageClient {
    private static final ResourceLocation CRACK = Kegare.id("battle_crack");
    private static final RenderType TYPE = RenderType.entityCutoutNoCull(InventoryMenu.BLOCK_ATLAS);
    /** Ticks cracks wait, so the broken blocks are gone from this client first. */
    private static final int CRACK_DELAY = 3;
    private static final int MAX_CHUNKS = 400;
    private static final RandomSource RANDOM = RandomSource.create();

    private static final class Chunk {
        Vec3 pos, prev, vel;
        final float size;
        final Vector3f axis;
        float angle, prevAngle;
        final float spin;
        final float u0, v0, u1, v1;
        final int rgb;
        final int life;
        int age;
        boolean bounced;

        Chunk(Vec3 pos, Vec3 vel, float size, Vector3f axis, float spin, float u0, float v0, float u1, float v1, int rgb, int life) {
            this.pos = this.prev = pos;
            this.vel = vel;
            this.size = size;
            this.axis = axis;
            this.spin = spin;
            this.u0 = u0;
            this.v0 = v0;
            this.u1 = u1;
            this.v1 = v1;
            this.rgb = rgb;
            this.life = life;
        }
    }

    private record PendingCracks(BattleDamagePayload hit, long at) {}

    private static final List<Chunk> CHUNKS = new ArrayList<>();
    private static final List<PendingCracks> CRACKS = new ArrayList<>();

    private BattleDamageClient() {}

    public static void init() {
        BattleDamagePayload.setClientHandler(BattleDamageClient::onHit);
        NeoForge.EVENT_BUS.addListener(BattleDamageClient::render);
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post e) -> tick());
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut e) -> {
            CHUNKS.clear();
            CRACKS.clear();
        });
    }

    static void onHit(BattleDamagePayload hit) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) return;
        VfxQuality quality = KegareClientConfig.VFX_QUALITY.get();
        for (int i = 0; i < hit.blocks().size(); i++) {
            BlockState state = Block.stateById(hit.states()[i]);
            if (state.isAir()) continue;
            BlockPos pos = hit.blocks().get(i);
            if (quality == VfxQuality.VANILLA) vanilla(level, hit, pos, state);
            else debris(level, hit, pos, state, quality == VfxQuality.REDUCED ? 2 : 5);
        }
        while (CHUNKS.size() > MAX_CHUNKS) CHUNKS.remove(0);
        if (quality != VfxQuality.VANILLA) CRACKS.add(new PendingCracks(hit, level.getGameTime() + CRACK_DELAY));
    }

    // ---------------------------------------------------------------- debris

    private static void vanilla(ClientLevel level, BattleDamagePayload hit, BlockPos pos, BlockState state) {
        Vec3 c = Vec3.atCenterOf(pos);
        Vec3 out = c.subtract(hit.contact()).normalize();
        for (int k = 0; k < 4; k++) {
            level.addParticle(new BlockParticleOption(ParticleTypes.BLOCK, state), c.x + RANDOM.nextGaussian() * 0.3, c.y + RANDOM.nextGaussian() * 0.3,
                    c.z + RANDOM.nextGaussian() * 0.3, out.x * 0.3, 0.2 + out.y * 0.2, out.z * 0.3);
        }
    }

    private static void debris(ClientLevel level, BattleDamagePayload hit, BlockPos pos, BlockState state, int count) {
        Minecraft mc = Minecraft.getInstance();
        TextureAtlasSprite sprite = mc.getBlockRenderer().getBlockModelShaper().getParticleIcon(state);
        int rgb = 0xFFFFFF;
        if (!state.is(Blocks.GRASS_BLOCK)) { // as vanilla: the grass block's particle is plain dirt, so no tint
            int c = mc.getBlockColors().getColor(state, level, pos, 0);
            if (c != -1) rgb = c & 0xFFFFFF;
        }
        Vec3 centre = Vec3.atCenterOf(pos);
        Vec3 away = centre.subtract(hit.contact());
        away = away.lengthSqr() < 1.0e-4 ? new Vec3(0, 1, 0) : away.normalize();
        for (int k = 0; k < count; k++) {
            Vec3 at = centre.add((RANDOM.nextFloat() - 0.5) * 0.8, (RANDOM.nextFloat() - 0.5) * 0.8, (RANDOM.nextFloat() - 0.5) * 0.8);
            double speed = 0.12 + 0.25 * RANDOM.nextFloat() + 0.02 * Math.min(10, hit.power());
            Vec3 vel = away.scale(speed).add(RANDOM.nextGaussian() * 0.07, 0.12 + 0.18 * RANDOM.nextFloat(), RANDOM.nextGaussian() * 0.07);
            int texels = 2 + RANDOM.nextInt(3); // a 2 to 4 texel piece of the block's face: hard pixels
            float size = texels / 16f * (0.9f + 0.4f * RANDOM.nextFloat());
            int tu = RANDOM.nextInt(17 - texels), tv = RANDOM.nextInt(17 - texels);
            Vector3f axis = new Vector3f(RANDOM.nextFloat() - 0.5f, RANDOM.nextFloat() - 0.5f, RANDOM.nextFloat() - 0.5f).normalize();
            CHUNKS.add(new Chunk(at, vel, size, axis, (RANDOM.nextFloat() - 0.5f) * 0.9f, sprite.getU(tu / 16f), sprite.getV(tv / 16f),
                    sprite.getU((tu + texels) / 16f), sprite.getV((tv + texels) / 16f), rgb, 12 + RANDOM.nextInt(11)));
        }
    }

    private static void tick() {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        for (Iterator<Chunk> it = CHUNKS.iterator(); it.hasNext(); ) {
            Chunk c = it.next();
            if (++c.age > c.life) {
                it.remove();
                continue;
            }
            c.prev = c.pos;
            c.prevAngle = c.angle;
            c.vel = new Vec3(c.vel.x * 0.96, c.vel.y * 0.96 - 0.045, c.vel.z * 0.96);
            Vec3 next = c.pos.add(c.vel);
            if (solid(level, next)) {
                if (!solid(level, new Vec3(next.x, c.pos.y, next.z))) { // it hit the ground (or a ceiling)
                    c.vel = new Vec3(c.vel.x * 0.5, c.bounced ? 0 : -c.vel.y * 0.3, c.vel.z * 0.5);
                } else { // a wall
                    c.vel = new Vec3(-c.vel.x * 0.25, c.vel.y, -c.vel.z * 0.25);
                }
                c.bounced = true;
                next = c.pos.add(c.vel);
                if (solid(level, next)) next = c.pos;
            }
            c.pos = next;
            c.angle += c.spin * (c.bounced ? 0.4f : 1f);
        }
        long now = level.getGameTime();
        for (Iterator<PendingCracks> it = CRACKS.iterator(); it.hasNext(); ) {
            PendingCracks p = it.next();
            if (now < p.at) continue;
            it.remove();
            cracks(level, p.hit);
        }
    }

    private static boolean solid(ClientLevel level, Vec3 at) {
        BlockPos pos = BlockPos.containing(at);
        return !level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
    }

    private static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || CHUNKS.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) return;
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        Camera camera = event.getCamera();
        Vec3 cam = camera.getPosition();
        boolean firstPerson = !camera.isDetached();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer out = buffers.getBuffer(TYPE);
        PoseStack pose = event.getPoseStack();
        for (Chunk c : CHUNKS) {
            Vec3 at = c.prev.lerp(c.pos, partial);
            if (firstPerson && at.distanceToSqr(cam) < 0.6 * 0.6) continue; // never a chunk in your eyes
            float left = c.life - c.age - partial;
            float s = c.size * Mth.clamp(left / 5f, 0f, 1f);
            if (s <= 0.002f) continue;
            int light = LevelRenderer.getLightColor(level, BlockPos.containing(at));
            pose.pushPose();
            pose.translate(at.x - cam.x, at.y - cam.y, at.z - cam.z);
            pose.mulPose(new Quaternionf().rotationAxis(Mth.lerp(partial, c.prevAngle, c.angle) * 6f, c.axis));
            pose.scale(s, s, s);
            cube(out, pose.last(), c, light);
            pose.popPose();
        }
        buffers.endBatch(TYPE);
    }

    /** A unit cube centred on the origin, every face the same piece of texture. */
    private static void cube(VertexConsumer out, PoseStack.Pose pose, Chunk c, int light) {
        for (Direction d : Direction.values()) {
            Vector3f n = d.step();
            Vector3f a = new Vector3f(), b = new Vector3f();
            switch (d.getAxis()) {
                case X -> { a.set(0, 1, 0); b.set(0, 0, 1); }
                case Y -> { a.set(1, 0, 0); b.set(0, 0, 1); }
                default -> { a.set(1, 0, 0); b.set(0, 1, 0); }
            }
            if (d.getAxisDirection() == Direction.AxisDirection.NEGATIVE) b.negate(); // keep the winding facing out
            Vector3f centre = new Vector3f(n).mul(0.5f);
            vertex(out, pose, new Vector3f(centre).sub(new Vector3f(a).mul(0.5f)).sub(new Vector3f(b).mul(0.5f)), n, c.u0, c.v1, c.rgb, light);
            vertex(out, pose, new Vector3f(centre).add(new Vector3f(a).mul(0.5f)).sub(new Vector3f(b).mul(0.5f)), n, c.u1, c.v1, c.rgb, light);
            vertex(out, pose, new Vector3f(centre).add(new Vector3f(a).mul(0.5f)).add(new Vector3f(b).mul(0.5f)), n, c.u1, c.v0, c.rgb, light);
            vertex(out, pose, new Vector3f(centre).sub(new Vector3f(a).mul(0.5f)).add(new Vector3f(b).mul(0.5f)), n, c.u0, c.v0, c.rgb, light);
        }
    }

    private static void vertex(VertexConsumer out, PoseStack.Pose pose, Vector3f p, Vector3f n, float u, float v, int rgb, int light) {
        out.addVertex(pose, p.x, p.y, p.z).setColor(rgb >> 16 & 0xFF, rgb >> 8 & 0xFF, rgb & 0xFF, 255).setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, n.x, n.y, n.z);
    }

    // ---------------------------------------------------------------- cracks

    private record Face(Vec3 at, Direction face, double distance, float reach) {}

    /**
     * Cracks the surfaces round the hit (along each axis) and the rim of the hole, nearest first, each as far as the power left there
     * against the block's resistance.
     */
    static void cracks(ClientLevel level, BattleDamagePayload hit) {
        BlockMarks.Pattern pattern = BlockMarks.pattern(CRACK);
        if (pattern == null) return;
        boolean reduced = KegareClientConfig.VFX_QUALITY.get() == VfxQuality.REDUCED;
        double reachRadius = Math.max(hit.radius() * 1.5, hit.radius() + 1.0);
        List<Face> faces = new ArrayList<>();
        Vec3 c = hit.contact();
        for (Direction d : Direction.values()) {
            Vec3 to = c.add(Vec3.atLowerCornerOf(d.getNormal()).scale(reachRadius));
            BlockHitResult r = level.clip(new ClipContext(c, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty()));
            if (r.getType() != HitResult.Type.BLOCK) continue;
            Face f = face(level, hit, r.getBlockPos(), r.getLocation(), r.getDirection(), reachRadius);
            if (f != null) faces.add(f);
        }
        List<Face> rim = new ArrayList<>();
        if (!hit.cosmetic()) {
            Set<BlockPos> gone = new HashSet<>(hit.blocks());
            for (BlockPos pos : hit.blocks()) {
                for (Direction d : Direction.values()) {
                    BlockPos wall = pos.relative(d);
                    if (gone.contains(wall)) continue;
                    BlockState state = level.getBlockState(wall);
                    if (state.getCollisionShape(level, wall).isEmpty()) continue;
                    Direction facing = d.getOpposite();
                    Vec3 at = Vec3.atCenterOf(wall).add(Vec3.atLowerCornerOf(facing.getNormal()).scale(0.5));
                    Face f = face(level, hit, wall, at, facing, reachRadius);
                    if (f != null) rim.add(new Face(f.at, f.face, f.distance, Math.min(f.reach, 0.6f)));
                }
            }
            rim.sort(Comparator.comparingDouble(Face::distance));
        }
        faces.sort(Comparator.comparingDouble(Face::distance));
        int around = reduced ? 3 : 6, edges = reduced ? 2 : 5;
        for (int i = 0; i < Math.min(around, faces.size()); i++) stamp(level, pattern, faces.get(i));
        for (int i = 0; i < Math.min(edges, rim.size()); i++) stamp(level, pattern, rim.get(i));
    }

    private static Face face(ClientLevel level, BattleDamagePayload hit, BlockPos pos, Vec3 at, Direction face, double reachRadius) {
        double d = at.distanceTo(hit.contact());
        if (d >= reachRadius) return null;
        float resistance = Math.max(0.5f, level.getBlockState(pos).getBlock().getExplosionResistance());
        double ratio = hit.power() * (1 - d / reachRadius) / resistance;
        if (ratio < 0.25) return null; // too little left here to mark it
        return new Face(at, face, d, (float) Mth.clamp(0.25 + 0.75 * Math.min(1, ratio), 0.25, 1.0));
    }

    private static void stamp(ClientLevel level, BlockMarks.Pattern pattern, Face f) {
        BlockMarks.stamp(level, pattern, f.at, f.face, RANDOM.nextInt(8), new BlockMarks.Timing(f.reach, 0, 3, 600, 160));
    }
}

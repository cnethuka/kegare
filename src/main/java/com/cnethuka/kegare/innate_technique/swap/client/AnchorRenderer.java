package com.cnethuka.kegare.innate_technique.swap.client;

import com.cnethuka.kegare.Kegare;
import com.cnethuka.kegare.innate_technique.rime.client.LanceMesh;
import com.cnethuka.kegare.innate_technique.swap.AnchorEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

/**
 * Anchor's token (DESIGN §3.19): a small solid teal coin (tools/blockbench/build_swap_anchor.py), drawn the way Rime's ice is
 * (smooth mesh, filtered texture, a lit glow pass) but Swap's own. In flight it flips end over end with a thin crisp teal line
 * behind it; stuck, it stands on its edge just off the face it hit, turning slowly, and pings: a thin ring spreads from it in the
 * face's plane once every 1.5 s. Everything here is ordinary geometry, no Photon, so what's filmed is what players see.
 * <p>
 * Vanilla: the token itself (it's the thing, not an effect), and teal specks for the line and the ping ({@link #tick}).
 */
public class AnchorRenderer extends EntityRenderer<AnchorEntity> {
    public static final LanceMesh.Model TOKEN = new LanceMesh.Model(Kegare.id("models/fx/swap_anchor.obj"),
            Kegare.id("textures/entity/swap_anchor.png"), Kegare.id("textures/entity/swap_anchor_glow.png"));
    private static final List<String> PIECES = List.of("token");
    private static final float[] FULL = {1f};
    /** The coin's radius (build_swap_anchor.py's RADIUS). */
    static final float RADIUS = 0.13f;
    /** How far the server put it off the face it hit (AnchorEntity.onHit). */
    private static final float OFF_FACE = 0.08f;
    /** Degrees a tick: flipping in flight, turning on its edge while it waits. */
    private static final float FLIP = 42f, TURN = 5f;
    private static final int PING_EVERY = 30, PING_TICKS = 9;
    /** Your own isn't drawn this close to your first-person camera: it leaves your hand just below and beside it. */
    private static final double OWN_HIDDEN_WITHIN = 1.2;
    private static final float LINE_LENGTH = 1.4f, LINE_WIDTH = 0.035f;
    /** How far short of the thrower's eyes the line stops. */
    private static final float CLEAR_OF_THROWER = 0.6f;
    private static final Direction[] FACES = {Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST, Direction.DOWN};

    public AnchorRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0f;
    }

    @Override
    public boolean shouldRender(AnchorEntity anchor, Frustum frustum, double camX, double camY, double camZ) {
        return anchor.shouldRender(camX, camY, camZ) && frustum.isVisible(anchor.getBoundingBox().inflate(0.7));
    }

    @Override
    public void render(AnchorEntity anchor, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        float age = anchor.tickCount + partialTick;
        if (anchor.isStuck()) {
            Direction face = face(anchor.level(), anchor.position());
            Vec3 n = Vec3.atLowerCornerOf(face.getNormal());
            // On its edge, just off the face: its middle a radius and a little out from it.
            Vec3 middle = n.scale(RADIUS + 0.02f - OFF_FACE);
            pose.pushPose();
            pose.translate(middle.x, middle.y, middle.z);
            pose.mulPose(Axis.YP.rotationDegrees(age * TURN + anchor.getId() * 61f));
            TOKEN.draw(pose.last(), buffers, PIECES, FULL, light);
            pose.popPose();
            if (SwapFx.drawn()) ping(pose, buffers, n, age);
        } else if (!hiddenFromOwnCamera(anchor, partialTick)) {
            pose.pushPose();
            pose.translate(0f, anchor.getBbHeight() * 0.5f, 0f);
            pose.mulPose(Axis.YP.rotationDegrees(Mth.lerp(partialTick, anchor.yRotO, anchor.getYRot())));
            pose.mulPose(Axis.XP.rotationDegrees(age * FLIP)); // end over end, the way it's going
            TOKEN.draw(pose.last(), buffers, PIECES, FULL, light);
            pose.popPose();
            if (SwapFx.drawn()) line(anchor, partialTick, pose, buffers);
        }
        super.render(anchor, yaw, partialTick, pose, buffers, light);
    }

    /** The face it's stuck to, found from where it sits (the server doesn't send it): the first solid block it's just off. */
    static Direction face(Level level, Vec3 at) {
        for (Direction d : FACES) {
            Vec3 probe = at.subtract(Vec3.atLowerCornerOf(d.getNormal()).scale(OFF_FACE + 0.04));
            BlockPos pos = BlockPos.containing(probe);
            if (!level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()) return d;
        }
        return Direction.UP;
    }

    /** The waiting token's ping: a thin ring spreading from it in the plane of its face, just off it, fading as it goes. */
    private static void ping(PoseStack pose, MultiBufferSource buffers, Vec3 n, float age) {
        float t = (age % PING_EVERY) / PING_TICKS;
        if (t > 1f) return;
        pose.pushPose();
        Vec3 onFace = n.scale(0.02f - OFF_FACE);
        pose.translate(onFace.x, onFace.y, onFace.z);
        // The ring is drawn flat in XZ, facing up: turned so it faces out of the face.
        pose.mulPose(new Quaternionf().rotationTo(0f, 1f, 0f, (float) n.x, (float) n.y, (float) n.z));
        float ease = 1f - (1f - t) * (1f - t);
        VertexConsumer out = buffers.getBuffer(RenderType.entityTranslucentEmissive(SwapFx.WHITE));
        SwapFx.ring(out, pose.last().pose(), RADIUS + 0.06f + 0.36f * ease, 0.03f * (1f - 0.5f * t), SwapFx.GLOW, 0.95f * (1f - t));
        pose.popPose();
    }

    /** The thin line behind it in flight: never back past where it was thrown from, nor into the thrower's head or your camera. */
    private static void line(AnchorEntity anchor, float partialTick, PoseStack pose, MultiBufferSource buffers) {
        Vec3 v = anchor.getDeltaMovement();
        if (v.lengthSqr() < 1.0e-4) return;
        Vec3 dir = v.normalize();
        Vec3 at = anchor.getPosition(partialTick).add(0, anchor.getBbHeight() * 0.5, 0);
        float length = Math.min(LINE_LENGTH, (anchor.tickCount + partialTick) * (float) v.length() - 0.3f);
        Entity owner = anchor.getOwner();
        if (owner != null) length = Math.min(length, (float) at.distanceTo(owner.getEyePosition(partialTick)) - CLEAR_OF_THROWER);
        Minecraft mc = Minecraft.getInstance();
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        if (owner == mc.player && mc.options.getCameraType().isFirstPerson()) {
            length = Math.min(length, (float) (at.distanceTo(cam) - OWN_HIDDEN_WITHIN - 0.3));
        }
        if (length <= 0.05f) return;
        VertexConsumer out = buffers.getBuffer(RenderType.entityTranslucentEmissive(SwapFx.WHITE));
        Matrix4f m = pose.last().pose();
        Vec3 eye = cam.subtract(anchor.getPosition(partialTick)); // the camera in the entity's frame
        Vec3 h = new Vec3(0, anchor.getBbHeight() * 0.5, 0);
        // In the entity's frame (its feet at the origin): from just behind the coin back along the way it came, in four pieces that
        // fade toward the far end.
        for (int i = 0; i < 4; i++) {
            float t0 = i / 4f, t1 = (i + 1) / 4f;
            Vec3 a = h.subtract(dir.scale(RADIUS + t0 * length)), b = h.subtract(dir.scale(RADIUS + t1 * length));
            SwapFx.strip(out, m, a, b, eye, LINE_WIDTH * (1f - 0.6f * t0), SwapFx.GLOW, 0.9f * (1f - t0));
        }
    }

    private static boolean hiddenFromOwnCamera(AnchorEntity anchor, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || anchor.getOwner() != mc.player || !mc.options.getCameraType().isFirstPerson()) return false;
        Vec3 camera = mc.gameRenderer.getMainCamera().getPosition();
        return anchor.getPosition(partialTick).distanceToSqr(camera) < OWN_HIDDEN_WITHIN * OWN_HIDDEN_WITHIN;
    }

    /** Vanilla's stand-ins, a tick at a time: a teal speck behind each token in flight, and a ring of them as a waiting one pings. */
    static void tick(ClientLevel level) {
        if (SwapFx.drawn()) return;
        Minecraft mc = Minecraft.getInstance();
        for (Entity e : level.entitiesForRendering()) {
            if (!(e instanceof AnchorEntity anchor)) continue;
            if (!anchor.isStuck()) {
                if (anchor.getOwner() == mc.player && mc.options.getCameraType().isFirstPerson()
                        && anchor.position().distanceTo(mc.gameRenderer.getMainCamera().getPosition()) < OWN_HIDDEN_WITHIN + 0.5) continue;
                level.addParticle(SwapFx.SPECK, anchor.xo, anchor.yo + anchor.getBbHeight() * 0.5, anchor.zo, 0, 0, 0);
            } else if (anchor.tickCount % PING_EVERY == 0) {
                Vec3 n = Vec3.atLowerCornerOf(face(level, anchor.position()).getNormal());
                Vec3 c = anchor.position().add(n.scale(0.02f - OFF_FACE));
                Vec3 u = Math.abs(n.y) > 0.5 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
                Vec3 w = n.cross(u);
                for (int i = 0; i < 8; i++) {
                    double a = Mth.TWO_PI * i / 8.0;
                    Vec3 p = c.add(u.scale(Math.cos(a) * 0.3)).add(w.scale(Math.sin(a) * 0.3));
                    level.addParticle(SwapFx.SPECK, p.x, p.y, p.z, 0, 0, 0);
                }
            }
        }
    }

    @Override
    public ResourceLocation getTextureLocation(AnchorEntity anchor) {
        return Kegare.id("textures/entity/swap_anchor.png");
    }
}

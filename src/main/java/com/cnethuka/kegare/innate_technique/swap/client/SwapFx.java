package com.cnethuka.kegare.innate_technique.swap.client;

import com.cnethuka.kegare.client.visual.ClientVisuals;
import com.cnethuka.kegare.config.KegareClientConfig;
import com.cnethuka.kegare.innatetechnique.visual.Visual;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * What Swap draws itself, rather than with Photon (DESIGN §3.19): smooth, flat teal shapes with hard edges, quick and gone, so a
 * swap reads as a clean trick. With {@code vfxQuality = VANILLA} none of it is drawn and the vanilla stand-ins play instead.
 * <ul>
 *   <li>The <b>afterimage</b>: a teal figure of a player left where they stood, in the pose they were in (the clap): the body's
 *   boxes in flat teal, shaded by the way each face points, with the arms this client sees on them (a disguise's included) and
 *   nothing of the skin. It flickers and is gone in a third of a second. Never drawn round your own first-person camera.
 *   <li>The <b>flicker ring</b>: a thin teal ring snapping out round the feet at both ends of a swap.
 *   <li>The <b>snap</b>: a moved player or mob jumps to where it was sent, instead of sliding there over three ticks as vanilla
 *   draws a teleport, so the swap is instant for everyone watching.
 * </ul>
 */
public final class SwapFx {
    static final ResourceLocation WHITE = ResourceLocation.withDefaultNamespace("textures/misc/white.png");
    /** Swap's teal drawn as light: its hue at full brightness. */
    static final int GLOW = 0x2EF2DA;
    static final int PALE = 0xCCFBF1;
    /** The afterimage's colour. */
    private static final int GHOST_RGB = 0x3FF0DA;
    /** The afterimage's opacity tick by tick: on, nearly off, on, then fading. */
    private static final float[] GHOST_ALPHA = {0.85f, 0.18f, 0.75f, 0.5f, 0.3f, 0.14f, 0.05f};
    /** An afterimage this close to your first-person camera isn't drawn (you've just landed in it). */
    private static final double CLEAR_OF_CAMERA = 1.3;
    private static final int RING_TICKS = 5;
    private static final int RING_SEGMENTS = 40;
    /** Ticks a moved entity is watched for its teleport, to snap it there. */
    private static final int SNAP_WINDOW = 10;
    /** Keep a player's last drawn pose this long. */
    private static final int POSE_KEPT = 40;

    /** A player as last drawn: the model's parts, which are shown, the skin and the body's facing. */
    record Pose(PartPose[] parts, boolean[] shown, boolean slim, ResourceLocation skin, float bodyYaw, long tick) {}

    private record Afterimage(Pose pose, Vec3 feet, long born) {}

    private record Ring(Vec3 feet, float radius, long born) {}

    /** An entity about to be moved by a swap: from where, and until when to watch for the move. */
    private record Snap(Vec3 from, long until) {}

    /** Photon's part of an effect, played a tick late. */
    private record Later(Vec3 pos, Visual visual, long due) {}

    private static final Map<Integer, Pose> POSES = new HashMap<>();
    private static final List<Afterimage> AFTERIMAGES = new ArrayList<>();
    private static final List<Ring> RINGS = new ArrayList<>();
    private static final Map<Integer, Snap> SNAP = new HashMap<>();
    private static final List<Later> LATER = new ArrayList<>();
    private static long clock;
    @Nullable private static PlayerModel<AbstractClientPlayer> wide, slim;

    private SwapFx() {}

    static boolean drawn() {
        return KegareClientConfig.VFX_QUALITY.get() != KegareClientConfig.VfxQuality.VANILLA;
    }

    // ---------------------------------------------------------------- what the server says happened

    /** Someone left {@code pos} (their middle): their afterimage, a flicker ring, and snap them when the move arrives. */
    static void depart(ClientLevel level, Vec3 pos, int entityId) {
        Entity entity = entityId >= 0 ? level.getEntity(entityId) : null;
        if (entity instanceof LivingEntity) SNAP.put(entityId, new Snap(entity.position(), clock + SNAP_WINDOW));
        if (!drawn()) return;
        Vec3 feet = entity != null ? entity.position() : pos.subtract(0, 0.9, 0);
        if (entity instanceof AbstractClientPlayer player) {
            Pose pose = poseOf(player);
            if (pose != null) AFTERIMAGES.add(new Afterimage(pose, feet, clock));
        }
        RINGS.add(new Ring(feet, entity != null ? Math.max(0.5f, entity.getBbWidth()) : 0.6f, clock));
    }

    /** Someone arrived at {@code pos}: the flicker ring there. */
    static void arrive(ClientLevel level, Vec3 pos, int entityId) {
        if (!drawn()) return;
        Entity entity = entityId >= 0 ? level.getEntity(entityId) : null;
        float half = entity != null ? entity.getBbHeight() / 2f : 0.9f;
        RINGS.add(new Ring(pos.subtract(0, half, 0), entity != null ? Math.max(0.5f, entity.getBbWidth()) : 0.6f, clock + 1));
    }

    /**
     * Photon's glints (or the vanilla dust) at a swap's end, a tick late: by then the move has reached this client, so if you've
     * just landed in them they're skipped as anything on your own first-person camera is, instead of filling your view.
     */
    static void later(Vec3 pos, Visual visual) {
        // Not this tick (it may run in the same frame the move was read, before the camera has followed it) but the next.
        LATER.add(new Later(pos, visual, clock + 2));
    }

    // ---------------------------------------------------------------- poses

    /** Every player drawn: remember the pose their model was drawn in (an ability's animation included). */
    static void remember(AbstractClientPlayer player, PlayerModel<AbstractClientPlayer> model, float partialTick) {
        POSES.put(player.getId(), capture(model, player, Mth.rotLerp(partialTick, player.yBodyRotO, player.yBodyRot)));
    }

    /** The pose to leave behind: as last drawn if that was just now, else worked out now (your own body, in first person). */
    @Nullable
    private static Pose poseOf(AbstractClientPlayer player) {
        Pose seen = POSES.get(player.getId());
        if (seen != null && clock - seen.tick() <= 2) return seen;
        PlayerModel<AbstractClientPlayer> model = model(player.getSkin().model() == PlayerSkin.Model.SLIM);
        if (model == null) return null;
        float limb = player.walkAnimation.position(), speed = Math.min(1f, player.walkAnimation.speed());
        float bodyYaw = player.yBodyRot;
        model.attackTime = 0f;
        model.crouching = player.isCrouching();
        model.prepareMobModel(player, limb, speed, 1f);
        model.setupAnim(player, limb, speed, player.tickCount, player.getYHeadRot() - bodyYaw, player.getXRot());
        return capture(model, player, bodyYaw);
    }

    private static Pose capture(PlayerModel<AbstractClientPlayer> model, AbstractClientPlayer player, float bodyYaw) {
        ModelPart[] parts = parts(model);
        PartPose[] poses = new PartPose[parts.length];
        boolean[] shown = new boolean[parts.length];
        for (int i = 0; i < parts.length; i++) {
            poses[i] = parts[i].storePose();
            shown[i] = parts[i].visible;
        }
        PlayerSkin skin = player.getSkin(); // as this client sees them: a disguise's skin and arms
        return new Pose(poses, shown, skin.model() == PlayerSkin.Model.SLIM, skin.texture(), bodyYaw, clock);
    }

    private static ModelPart[] parts(PlayerModel<?> m) {
        return new ModelPart[] {m.head, m.hat, m.body, m.jacket, m.rightArm, m.rightSleeve, m.leftArm, m.leftSleeve, m.rightLeg,
                m.rightPants, m.leftLeg, m.leftPants};
    }

    @Nullable
    private static PlayerModel<AbstractClientPlayer> model(boolean slimArms) {
        Minecraft mc = Minecraft.getInstance();
        if (slimArms) {
            if (slim == null) slim = new PlayerModel<>(mc.getEntityModels().bakeLayer(ModelLayers.PLAYER_SLIM), true);
            return slim;
        }
        if (wide == null) wide = new PlayerModel<>(mc.getEntityModels().bakeLayer(ModelLayers.PLAYER), false);
        return wide;
    }

    // ---------------------------------------------------------------- ticking

    static void tick(ClientLevel level) {
        clock++;
        if (!LATER.isEmpty()) {
            List<Later> due = LATER.stream().filter(l -> l.due() <= clock).toList();
            LATER.removeAll(due);
            due.forEach(l -> ClientVisuals.playAt(level, l.pos(), l.visual()));
        }
        AFTERIMAGES.removeIf(a -> clock - a.born() >= GHOST_ALPHA.length);
        RINGS.removeIf(r -> clock - r.born() > RING_TICKS);
        POSES.values().removeIf(p -> clock - p.tick() > POSE_KEPT);
        SNAP.values().removeIf(s -> s.until() < clock);
    }

    /** Before the world ticks (the moves have just been read): put a moved entity where it was sent, not on its way there. */
    static void snap(ClientLevel level) {
        for (Iterator<Map.Entry<Integer, Snap>> it = SNAP.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Integer, Snap> e = it.next();
            if (!(level.getEntity(e.getKey()) instanceof LivingEntity entity)) continue;
            if (entity == Minecraft.getInstance().player) {
                // Yourself: vanilla moves you and your view at once but leaves your head and body turning round from the old
                // facing, so in third person you'd see your own face for a frame. They face the new way as soon as you've moved.
                if (entity.position().distanceToSqr(e.getValue().from()) < 1.0) continue;
                entity.yHeadRot = entity.yHeadRotO = entity.yBodyRot = entity.yBodyRotO = entity.getYRot();
                it.remove();
                continue;
            }
            if (entity.isControlledByLocalInstance()) continue; // moved here already, not by a packet
            Vec3 to = new Vec3(entity.lerpTargetX(), entity.lerpTargetY(), entity.lerpTargetZ());
            if (to.distanceToSqr(entity.position()) < 1.0) continue;
            float yaw = entity.lerpTargetYRot(), pitch = entity.lerpTargetXRot();
            entity.setPos(to);
            entity.setYRot(yaw);
            entity.setXRot(pitch);
            entity.yBodyRot = yaw;
            entity.lerpTo(to.x, to.y, to.z, yaw, pitch, 0);
            entity.setOldPosAndRot();
            entity.yBodyRotO = yaw;
            it.remove();
        }
    }

    static void clear() {
        POSES.clear();
        AFTERIMAGES.clear();
        RINGS.clear();
        SNAP.clear();
        LATER.clear();
    }

    // ---------------------------------------------------------------- drawing

    static void render(PoseStack pose, Vec3 cam, float partial) {
        if ((AFTERIMAGES.isEmpty() && RINGS.isEmpty()) || !drawn()) return;
        MultiBufferSource.BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        Minecraft mc = Minecraft.getInstance();
        boolean firstPerson = mc.options.getCameraType().isFirstPerson();
        for (Afterimage a : AFTERIMAGES) {
            int age = (int) (clock - a.born());
            if (age < 0 || age >= GHOST_ALPHA.length) continue;
            if (firstPerson && a.feet().add(0, 1.0, 0).distanceTo(cam) < CLEAR_OF_CAMERA) continue;
            ghost(pose, buffers, a.pose(), a.feet().subtract(cam), GHOST_ALPHA[age]);
        }
        if (!RINGS.isEmpty()) {
            RenderType type = RenderType.entityTranslucentEmissive(WHITE);
            VertexConsumer out = buffers.getBuffer(type);
            for (Ring r : RINGS) {
                float t = (clock - r.born() + partial) / RING_TICKS;
                if (t < 0f || t > 1f) continue;
                float ease = 1f - (1f - t) * (1f - t);
                float radius = r.radius() * (0.55f + 0.75f * ease);
                Matrix4f m = pose.last().pose().translate((float) (r.feet().x - cam.x), (float) (r.feet().y - cam.y) + 0.03f,
                        (float) (r.feet().z - cam.z), new Matrix4f());
                ring(out, m, radius, 0.07f * (1f - 0.5f * t), GLOW, 1f - t);
            }
            buffers.endBatch(type);
        }
    }

    /** One afterimage: the player model's body boxes in its pose (not the outer layers), flat teal, see-through, lit fully. */
    private static void ghost(PoseStack pose, MultiBufferSource.BufferSource buffers, Pose p, Vec3 at, float alpha) {
        PlayerModel<AbstractClientPlayer> model = model(p.slim());
        if (model == null) return;
        ModelPart[] parts = parts(model);
        for (int i = 0; i < parts.length; i++) {
            parts[i].loadPose(p.parts()[i]);
            parts[i].visible = i % 2 == 0; // the body's boxes; the hat, jacket, sleeves and trousers would only thicken it
        }
        pose.pushPose();
        pose.translate(at.x, at.y, at.z);
        pose.mulPose(Axis.YP.rotationDegrees(180f - p.bodyYaw()));
        pose.scale(-1f, -1f, 1f);
        pose.scale(0.9375f, 0.9375f, 0.9375f);
        pose.translate(0f, -1.501f, 0f);
        RenderType type = RenderType.itemEntityTranslucentCull(WHITE);
        int color = Math.round(alpha * 255) << 24 | GHOST_RGB;
        model.renderToBuffer(pose, buffers.getBuffer(type), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, color);
        buffers.endBatch(type);
        pose.popPose();
        for (ModelPart part : parts) {
            part.resetPose();
            part.visible = true;
        }
    }

    /** A flat ring on the ground: {@code radius} to its middle, {@code width} across, in {@code rgb}. */
    static void ring(VertexConsumer out, Matrix4f m, float radius, float width, int rgb, float alpha) {
        int a = Math.round(Mth.clamp(alpha, 0f, 1f) * 255);
        float in = Math.max(0f, radius - width / 2), outer = radius + width / 2;
        for (int i = 0; i < RING_SEGMENTS; i++) {
            float a0 = Mth.TWO_PI * i / RING_SEGMENTS, a1 = Mth.TWO_PI * (i + 1) / RING_SEGMENTS;
            float c0 = Mth.cos(a0), s0 = Mth.sin(a0), c1 = Mth.cos(a1), s1 = Mth.sin(a1);
            vertex(out, m, c0 * in, 0f, s0 * in, rgb, a);
            vertex(out, m, c1 * in, 0f, s1 * in, rgb, a);
            vertex(out, m, c1 * outer, 0f, s1 * outer, rgb, a);
            vertex(out, m, c0 * outer, 0f, s0 * outer, rgb, a);
        }
    }

    static void vertex(VertexConsumer out, Matrix4f m, float x, float y, float z, int rgb, int alpha) {
        out.addVertex(m, x, y, z).setColor(rgb >> 16 & 0xFF, rgb >> 8 & 0xFF, rgb & 0xFF, alpha).setUv(0.5f, 0.5f)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(0f, 1f, 0f);
    }
}

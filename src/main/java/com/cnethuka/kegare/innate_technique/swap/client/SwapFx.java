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
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

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

    /** Switchback's countdown on an entity: the loop's key (its time comes from {@link ClientVisuals#loopTime}). */
    private record Countdown(int entityId, String key) {}

    /** A disguise landing on an entity: the card-flip ripple down its body. */
    private record Flip(int entityId, long born) {}

    /** Photon's part of an effect, played a tick late. */
    private record Later(Vec3 pos, Visual visual, long due) {}

    private static final Map<Integer, Pose> POSES = new HashMap<>();
    private static final List<Afterimage> AFTERIMAGES = new ArrayList<>();
    private static final List<Ring> RINGS = new ArrayList<>();
    private static final Map<Integer, Snap> SNAP = new HashMap<>();
    private static final List<Later> LATER = new ArrayList<>();
    private static final List<Countdown> COUNTDOWNS = new ArrayList<>();
    private static final List<Flip> FLIPS = new ArrayList<>();
    /** The card-flip ripple: slats down the body, each this many ticks after the one above, each turning over in FLIP_TICKS. */
    private static final int FLIP_SLATS = 9;
    private static final float FLIP_STAGGER = 0.7f;
    private static final float FLIP_TICKS = 3f;
    /** Vanilla's stand-in for Swap's drawn effects: small teal dust, gone almost at once. */
    private static final DustParticleOptions SPECK = new DustParticleOptions(new Vector3f(0.18f, 0.9f, 0.82f), 0.5f);
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
        tickCountdowns(level);
        FLIPS.removeIf(f -> clock - f.born() > FLIP_SLATS * FLIP_STAGGER + FLIP_TICKS + 1);
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
        COUNTDOWNS.clear();
        FLIPS.clear();
    }

    // ---------------------------------------------------------------- Awareness's card flip

    /**
     * A disguise landed on someone (Awareness, Total Swap): a ripple of thin teal slats round the body, flipping over one after
     * another from the head down, each showing its pale back as it turns, like a card turned over; a third of a second, then
     * gone. Everyone sees it on both; never round your own first-person camera. Vanilla: a short run of teal dust down the body.
     */
    static void flip(ClientLevel level, Vec3 pos, int entityId) {
        Entity entity = entityId >= 0 ? level.getEntity(entityId) : null;
        if (entity == null) return;
        if (drawn()) {
            FLIPS.add(new Flip(entityId, clock));
            return;
        }
        if (entity == Minecraft.getInstance().player && Minecraft.getInstance().options.getCameraType().isFirstPerson()) return;
        // Either side of the body (in its middle they'd be hidden inside it), at the height of each slat.
        double out = entity.getBbWidth() / 2 + 0.12, yaw = Math.toRadians(entity.getYRot());
        double sideX = Math.cos(yaw) * out, sideZ = Math.sin(yaw) * out;
        for (int i = 0; i < FLIP_SLATS; i++) {
            double y = entity.getY() + entity.getBbHeight() * (1 - (i + 0.5) / FLIP_SLATS);
            level.addParticle(SPECK, entity.getX() + sideX, y, entity.getZ() + sideZ, 0, 0, 0);
            level.addParticle(SPECK, entity.getX() - sideX, y, entity.getZ() - sideZ, 0, 0, 0);
        }
    }

    private static void renderFlips(PoseStack pose, MultiBufferSource.BufferSource buffers, Vec3 cam, float partial) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        boolean firstPerson = mc.options.getCameraType().isFirstPerson();
        RenderType type = RenderType.entityTranslucentEmissive(WHITE);
        VertexConsumer out = buffers.getBuffer(type);
        for (Flip f : FLIPS) {
            Entity entity = mc.level.getEntity(f.entityId());
            if (entity == null || firstPerson && entity == mc.player) continue;
            float age = clock - f.born() + partial;
            float half = entity.getBbWidth() / 2f + 0.07f, height = entity.getBbHeight(), slat = height / FLIP_SLATS;
            Vec3 feet = entity.getPosition(partial);
            float yaw = entity instanceof LivingEntity living ? Mth.rotLerp(partial, living.yBodyRotO, living.yBodyRot) : entity.getYRot();
            Matrix4f m = pose.last().pose().translate((float) (feet.x - cam.x), (float) (feet.y - cam.y), (float) (feet.z - cam.z), new Matrix4f())
                    .rotateY(-yaw * Mth.DEG_TO_RAD);
            for (int i = 0; i < FLIP_SLATS; i++) {
                float p = (age - i * FLIP_STAGGER) / FLIP_TICKS;
                if (p < 0f || p > 1f) continue;
                float open = Math.abs(Mth.cos(p * Mth.PI));         // edge-on halfway through the turn
                float h = slat * 0.42f * Math.max(open, 0.12f);
                float y = height - (i + 0.5f) * slat;
                int rgb = p < 0.5f ? GLOW : PALE;                   // its face, then its back
                band(out, m, half, y - h, y + h, rgb, 0.9f);
            }
        }
        buffers.endBatch(type);
    }

    /** A band round a body-sized box, from {@code y0} to {@code y1}: four upright faces {@code half} out from the middle. */
    private static void band(VertexConsumer out, Matrix4f m, float half, float y0, float y1, int rgb, float alpha) {
        int a = Math.round(alpha * 255);
        float[][] corners = {{-half, -half}, {half, -half}, {half, half}, {-half, half}};
        for (int k = 0; k < 4; k++) {
            float[] c0 = corners[k], c1 = corners[(k + 1) % 4];
            vertex(out, m, c0[0], y0, c0[1], rgb, a);
            vertex(out, m, c1[0], y0, c1[1], rgb, a);
            vertex(out, m, c1[0], y1, c1[1], rgb, a);
            vertex(out, m, c0[0], y1, c0[1], rgb, a);
        }
    }

    // ---------------------------------------------------------------- Switchback's countdown

    /**
     * Switchback's countdown ring went up on an entity (both of them, as it's cast): a thin teal ring at the feet with a bright arc
     * that drains round it over the delay, a mark for each second still to go and a pulse as each second starts. It's gone the
     * moment the loop stops (the swap back, or it's called off).
     */
    static void countdownStarted(Entity entity, String key) {
        countdownStopped(entity, key);
        COUNTDOWNS.add(new Countdown(entity.getId(), key));
    }

    static void countdownStopped(Entity entity, String key) {
        COUNTDOWNS.removeIf(c -> c.entityId() == entity.getId() && c.key().equals(key));
    }

    /** Drops countdowns whose entity is gone; with Vanilla effects, a circle of dust specks as each second starts. */
    private static void tickCountdowns(ClientLevel level) {
        if (COUNTDOWNS.isEmpty()) return;
        boolean vanilla = KegareClientConfig.VFX_QUALITY.get() == KegareClientConfig.VfxQuality.VANILLA;
        for (Iterator<Countdown> it = COUNTDOWNS.iterator(); it.hasNext(); ) {
            Countdown c = it.next();
            Entity entity = level.getEntity(c.entityId());
            ClientVisuals.LoopTime time = entity == null ? null : ClientVisuals.loopTime(entity, c.key(), 0f);
            if (time == null) {
                it.remove();
                continue;
            }
            if (!vanilla || (int) time.ticks() % 20 != 0 || time.ticks() >= time.growTicks()) continue;
            float radius = countdownRadius(entity);
            for (int i = 0; i < 12; i++) {
                double a = Mth.TWO_PI * i / 12.0;
                level.addParticle(SPECK, entity.getX() + Math.cos(a) * radius, entity.getY() + 0.1, entity.getZ() + Math.sin(a) * radius,
                        0, 0, 0);
            }
        }
    }

    private static float countdownRadius(Entity entity) {
        return Math.max(0.55f, entity.getBbWidth() * 0.9f);
    }

    private static void renderCountdowns(PoseStack pose, MultiBufferSource.BufferSource buffers, Vec3 cam, float partial) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        RenderType type = RenderType.entityTranslucentEmissive(WHITE);
        VertexConsumer out = buffers.getBuffer(type);
        for (Countdown c : COUNTDOWNS) {
            Entity entity = level.getEntity(c.entityId());
            ClientVisuals.LoopTime time = entity == null ? null : ClientVisuals.loopTime(entity, c.key(), partial);
            if (time == null) continue;
            int total = Math.max(1, time.growTicks());
            float left = 1f - Mth.clamp(time.ticks() / total, 0f, 1f);
            float radius = countdownRadius(entity);
            Vec3 feet = entity.getPosition(partial);
            Matrix4f m = pose.last().pose().translate((float) (feet.x - cam.x), (float) (feet.y - cam.y) + 0.04f,
                    (float) (feet.z - cam.z), new Matrix4f());
            ring(out, m, radius, 0.03f, GLOW, 0.25f);                       // the track
            arc(out, m, radius, 0.065f, 0f, left, GLOW, 0.95f);             // what's left, draining round
            int seconds = Math.max(1, Math.round(total / 20f));
            for (int k = 0; k < seconds; k++) {                            // a mark per second still to go
                float at = (float) k / seconds;
                if (at < left) mark(out, m, radius, at, 0.16f, 0.035f, PALE, 1f);
            }
            float sinceSecond = time.ticks() % 20f;                         // a pulse as each second starts
            if (sinceSecond < 5f && time.ticks() < total) {
                float p = sinceSecond / 5f;
                ring(out, m, radius * (1f + 0.35f * p), 0.05f * (1f - p), PALE, 0.8f * (1f - p));
            }
        }
        buffers.endBatch(type);
    }

    // ---------------------------------------------------------------- drawing

    static void render(PoseStack pose, Vec3 cam, float partial) {
        if ((AFTERIMAGES.isEmpty() && RINGS.isEmpty() && COUNTDOWNS.isEmpty() && FLIPS.isEmpty()) || !drawn()) return;
        MultiBufferSource.BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        if (!COUNTDOWNS.isEmpty()) renderCountdowns(pose, buffers, cam, partial);
        if (!FLIPS.isEmpty()) renderFlips(pose, buffers, cam, partial);
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

    /** Part of a flat ring: from {@code from} to {@code to} of the way round (0 to 1), clockwise seen from above. */
    static void arc(VertexConsumer out, Matrix4f m, float radius, float width, float from, float to, int rgb, float alpha) {
        if (to <= from) return;
        int a = Math.round(Mth.clamp(alpha, 0f, 1f) * 255);
        float in = Math.max(0f, radius - width / 2), outer = radius + width / 2;
        int segments = Math.max(1, Math.round(RING_SEGMENTS * (to - from)));
        for (int i = 0; i < segments; i++) {
            float a0 = -Mth.HALF_PI + Mth.TWO_PI * (from + (to - from) * i / segments);
            float a1 = -Mth.HALF_PI + Mth.TWO_PI * (from + (to - from) * (i + 1) / segments);
            float c0 = Mth.cos(a0), s0 = Mth.sin(a0), c1 = Mth.cos(a1), s1 = Mth.sin(a1);
            vertex(out, m, c0 * in, 0f, s0 * in, rgb, a);
            vertex(out, m, c1 * in, 0f, s1 * in, rgb, a);
            vertex(out, m, c1 * outer, 0f, s1 * outer, rgb, a);
            vertex(out, m, c0 * outer, 0f, s0 * outer, rgb, a);
        }
    }

    /** A short mark across the ring at {@code at} of the way round, {@code length} across and {@code width} wide. */
    static void mark(VertexConsumer out, Matrix4f m, float radius, float at, float length, float width, int rgb, float alpha) {
        int a = Math.round(Mth.clamp(alpha, 0f, 1f) * 255);
        float ang = -Mth.HALF_PI + Mth.TWO_PI * at;
        float cx = Mth.cos(ang), cz = Mth.sin(ang), tx = -cz * width / 2, tz = cx * width / 2;
        float r0 = radius - length / 2, r1 = radius + length / 2;
        vertex(out, m, cx * r0 - tx, 0.002f, cz * r0 - tz, rgb, a);
        vertex(out, m, cx * r0 + tx, 0.002f, cz * r0 + tz, rgb, a);
        vertex(out, m, cx * r1 + tx, 0.002f, cz * r1 + tz, rgb, a);
        vertex(out, m, cx * r1 - tx, 0.002f, cz * r1 - tz, rgb, a);
    }

    static void vertex(VertexConsumer out, Matrix4f m, float x, float y, float z, int rgb, int alpha) {
        out.addVertex(m, x, y, z).setColor(rgb >> 16 & 0xFF, rgb >> 8 & 0xFF, rgb & 0xFF, alpha).setUv(0.5f, 0.5f)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(0f, 1f, 0f);
    }
}

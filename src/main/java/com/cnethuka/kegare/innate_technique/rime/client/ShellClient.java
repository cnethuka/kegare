package com.cnethuka.kegare.innate_technique.rime.client;

import com.cnethuka.kegare.client.visual.ClientVisuals;
import com.cnethuka.kegare.client.visual.PlayerAnimations;
import com.cnethuka.kegare.innate_technique.rime.Rime;
import com.cnethuka.kegare.innatetechnique.visual.VisualPhase;
import com.cnethuka.kegare.innatetechnique.visual.VisualService;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Rime Shell's plates: solid slabs of the lance's ice on the body ({@code models/fx/rime_shell.obj}, one object a plate, each in its
 * body part's space), drawn with the player model's own parts so they follow every pose, for everyone who can see the body: other
 * players, and you in third person. Your own first person shows none of it, even while PAL draws your arms there
 * ({@link PlayerAnimations#firstPersonPass}).
 * <ul>
 * <li>When the shell goes up (its toggle loop starts) the plates form one after another, chest first, each popping out a little
 * past its size and settling.</li>
 * <li>When it comes down they break off where they were drawn and fall away, tumbling and shrinking ({@link #burst} sends them
 * flying out to the ring instead: Shatter Shell, ShatterClient; with no shell up, a Shatter Shell bursts a whole set off the body
 * all the same).</li>
 * </ul>
 * Over armour the plates stand out on it, not sunk in it ({@link #overArmour}). Read off the server's own toggle loop on the player,
 * so it shows exactly while the shell is up.
 */
public final class ShellClient {
    static final String LOOP = VisualService.key(Rime.RIME_SHELL, VisualPhase.TOGGLE_LOOP);
    /** The parts the plates sit on, and how many each has (as in {@code models/fx/rime_shell.obj}). */
    static final String[] PARTS = {"torso", "right_arm", "left_arm", "right_leg", "left_leg"};
    static final int[] COUNTS = {9, 4, 4, 3, 3};
    /**
     * Each part's box about its pivot, in pixels, in the plates' space (Blockbench's axes: up +Y, the player's right +X, front -Z):
     * x, y, z, each from and to. Wide arms: a chestplate's are always wide.
     */
    static final float[][] BOXES = {{-4, 4, -12, 0, -2, 2}, {-1, 3, -10, 2, -2, 2}, {-3, 1, -10, 2, -2, 2}, {-2, 2, -12, 0, -2, 2},
            {-2, 2, -12, 0, -2, 2}};
    /** Which of ArmourReach's parts each of {@link #PARTS} is. */
    private static final int[] REACH = {ArmourReach.BODY, ArmourReach.RIGHT_ARM, ArmourReach.LEFT_ARM, ArmourReach.RIGHT_LEG,
            ArmourReach.LEFT_LEG};
    /** Over armour, plates stand this far (pixels) out past it. */
    static final float OVER_ARMOUR = 0.15f;
    /** Every plate, in the order they form. */
    static final List<String> PLATES = plates();
    /**
     * Ticks before the first plate forms (the arms crossing over the chest, rime_shell_toggle: the ice forms as they're flung out);
     * between one plate starting to form and the next; ticks each takes; how far past its size it pops.
     */
    static final float DELAY = 4f, STAGGER = 0.3f, FORM = 3f, OVERSHOOT = 0.12f;
    /** Broken off: how long a plate lasts, the last part of that shrinking away; how fast it leaves the body (blocks a tick). */
    static final int FALL_TICKS = 16, SHRINK_TICKS = 6;
    static final float FALL_SPEED = 0.06f, FALL_GRAVITY = 0.04f;

    private static final RandomSource RANDOM = RandomSource.create();

    /** Where each plate was last drawn on each player: its own space to the world's (and the turn of its normals). */
    private record Drawn(Matrix4f toWorld, Matrix3f normals, float scale) {}

    /** Where each player's plates were last drawn in the world, and on which game tick. */
    private record Body(long tick, Map<String, Drawn> plates) {}

    private static final Map<Integer, Body> DRAWN = new HashMap<>();
    /** Shatter Shells cast with no shell up, by player, and the tick: the next time the body is drawn, a whole set bursts off it. */
    private static final Map<Integer, Long> PENDING = new HashMap<>();
    /** How far a burst's plates fly (blocks from the body), set by ShatterClient from the knockback's reach. */
    private static float burstReach = 4.1f;

    /** A plate off the body: where it was, which way it's going, how it turns. */
    private static final class Debris {
        final String plate;
        final Matrix4f toWorld;
        final Matrix3f normals;
        final Vector3f centre;
        Vector3f velocity;
        final Vector3f spinAxis;
        float spin;
        final float scale;
        float gravity;
        boolean burst;
        int life;
        int age;

        Debris(String plate, Drawn drawn, Vector3f centre, Vector3f velocity, float spin, int life, float gravity) {
            this.plate = plate;
            this.toWorld = drawn.toWorld;
            this.normals = drawn.normals;
            this.centre = centre;
            this.velocity = velocity;
            this.spinAxis = new Vector3f(RANDOM.nextFloat() - 0.5f, RANDOM.nextFloat() - 0.5f, RANDOM.nextFloat() - 0.5f).normalize();
            this.spin = spin;
            this.scale = drawn.scale;
            this.life = life;
            this.gravity = gravity;
        }
    }

    private static final List<Debris> DEBRIS = new ArrayList<>();
    /** Plates that broke off this tick, by player: a Shatter Shell in the same tick sends them flying instead. */
    private static final Map<Integer, List<Debris>> JUST_OFF = new HashMap<>();

    private ShellClient() {}

    private static List<String> plates() {
        List<String> out = new ArrayList<>();
        for (int p = 0; p < PARTS.length; p++) {
            for (int i = 0; i < COUNTS[p]; i++) out.add(PARTS[p] + "_" + i);
        }
        return List.copyOf(out);
    }

    static void init() {
        ClientVisuals.listen(new ClientVisuals.Listener() {
            @Override
            public void loopStopped(Entity entity, String key, float ticks, int growTicks) {
                if (LOOP.equals(key)) breakOff(entity);
            }
        });
    }

    static void addLayers(EntityRenderersEvent.AddLayers event) {
        for (PlayerSkin.Model model : event.getSkins()) {
            if (event.getSkin(model) instanceof PlayerRenderer renderer) renderer.addLayer(new Layer(renderer));
        }
    }

    // ---------------------------------------------------------------------------------------------- forming

    /** A plate's size {@code ticks} after the shell went up: 0 until its turn, then out past full and back. */
    static float formed(int order, float ticks) {
        float t = (ticks - DELAY - order * STAGGER) / FORM;
        if (t <= 0f) return 0f;
        if (t >= 1f) return 1f;
        // Fast out to the overshoot by two thirds of the way, then settling back to its size.
        if (t < 2f / 3f) {
            float k = t * 1.5f;
            return (1f + OVERSHOOT) * (1f - (1f - k) * (1f - k));
        }
        float k = (t - 2f / 3f) * 3f;
        return 1f + OVERSHOOT * (1f - k * k * (3f - 2f * k));
    }

    /** Ticks for the whole shell to form. */
    static float formTicks() {
        return DELAY + (PLATES.size() - 1) * STAGGER + FORM;
    }

    /**
     * Where a plate goes over armour standing {@code out} pixels off its part ({@code corners} in blocks, in its own space): on each
     * axis it lies off a face of the part, it slides out that far; on each it spans, it stretches from the part's middle so its edges
     * reach the armour's. Its thickness is kept. As a translation (blocks) then a scale: x, y, z, then sx, sy, sz.
     */
    static float[] overArmour(float[] corners, float[] box, float out) {
        float[] lo = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE}, hi = {-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
        for (int i = 0; i < corners.length; i += 9) {
            for (int k = 0; k < 3; k++) {
                lo[k] = Math.min(lo[k], corners[i + k] * 16f);
                hi[k] = Math.max(hi[k], corners[i + k] * 16f);
            }
        }
        float[] move = new float[6];
        for (int k = 0; k < 3; k++) {
            float from = box[2 * k], to = box[2 * k + 1], mid = (from + to) / 2f, half = (to - from) / 2f, at = (lo[k] + hi[k]) / 2f;
            float scale = 1f, shift = 0f;
            if (at >= to - 0.5f) shift = out;
            else if (at <= from + 0.5f) shift = -out;
            else {
                scale = (half + out) / half;
                shift = mid - mid * scale;
            }
            move[k] = shift / 16f;
            move[k + 3] = scale;
        }
        return move;
    }

    // ---------------------------------------------------------------------------------------------- on a body

    static final class Layer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
        Layer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
            super(parent);
        }

        @Override
        public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player, float limbSwing,
                           float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
            if (PlayerAnimations.firstPersonPass()) return; // your own arms in first person: the plates would be round the camera
            ClientVisuals.LoopTime time = ClientVisuals.loopTime(player, LOOP, partialTick);
            long now = player.level().getGameTime();
            if (time == null) {
                DRAWN.remove(player.getId());
                // A Shatter Shell with no shell up: a whole set bursts off the body as it's drawn, nothing is left on it.
                if (inWorld && PENDING.remove(player.getId()) != null) {
                    Map<String, Drawn> whole = place(pose, player, -1f, new ArrayList<>(), new ArrayList<>());
                    fling(player, whole);
                }
                return;
            }
            List<PoseStack.Pose> poses = new ArrayList<>();
            List<String> names = new ArrayList<>();
            Map<String, Drawn> drawn = place(pose, player, time.ticks(), poses, names);
            LanceMesh.SHELL.draw(buffers, poses, names, light);
            // Only where it's drawn in the world: the inventory's little figure is drawn with the same layer, in the screen's space.
            if (inWorld) DRAWN.put(player.getId(), new Body(now, drawn));
        }

        /**
         * Every plate formed by {@code ticks} after the shell went up (all of them, full size, if negative), posed on this body:
         * their poses and names for drawing, and where each sits in the world.
         */
        private Map<String, Drawn> place(PoseStack pose, AbstractClientPlayer player, float ticks, List<PoseStack.Pose> poses,
                                         List<String> names) {
            PlayerModel<AbstractClientPlayer> model = getParentModel();
            ModelPart[] parts = {model.body, model.rightArm, model.leftArm, model.rightLeg, model.leftLeg};
            boolean slim = player.getSkin().model() == PlayerSkin.Model.SLIM;
            float[] armour = ArmourReach.of(player);
            Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
            Vec3 cam = camera.getPosition();
            Map<String, Drawn> drawn = new HashMap<>();
            int order = 0;
            for (int p = 0; p < PARTS.length; p++) {
                pose.pushPose();
                parts[p].translateAndRotate(pose);
                pose.mulPose(Axis.ZP.rotationDegrees(180f)); // into Blockbench's axes: up is up, the player's right +X
                float reach = armour[REACH[p]];
                // A slim arm is a pixel narrower on its outside; a chestplate's arms are wide ones whatever the skin.
                if (slim && reach == 0f && p == 1) pose.translate(-1f / 16f, 0f, 0f);
                if (slim && reach == 0f && p == 2) pose.translate(1f / 16f, 0f, 0f);
                for (int i = 0; i < COUNTS[p]; i++, order++) {
                    String name = PARTS[p] + "_" + i;
                    float s = ticks < 0f ? 1f : formed(order, ticks);
                    if (s <= 0f) continue;
                    Vector3f c = LanceMesh.SHELL.centre(name);
                    pose.pushPose();
                    LanceMesh.Piece piece = reach > 0f ? LanceMesh.SHELL.piece(name) : null;
                    if (piece != null) {
                        float[] m = overArmour(piece.corners(), BOXES[p], reach + OVER_ARMOUR);
                        pose.translate(m[0], m[1], m[2]);
                        pose.scale(m[3], m[4], m[5]);
                    }
                    pose.translate(c.x, c.y, c.z);
                    pose.scale(s, s, s);
                    pose.translate(-c.x, -c.y, -c.z);
                    poses.add(pose.last().copy());
                    names.add(name);
                    Matrix4f toWorld = new Matrix4f().translation((float) cam.x, (float) cam.y, (float) cam.z).mul(pose.last().pose());
                    drawn.put(name, new Drawn(toWorld, new Matrix3f(pose.last().normal()), s));
                    pose.popPose();
                }
                pose.popPose();
            }
            return drawn;
        }
    }

    // ---------------------------------------------------------------------------------------------- off the body

    /**
     * The shell came down: every plate breaks off where it was last drawn and falls away. Only if it was drawn just now: in your own
     * first person, or out of sight, nothing was, and plates where the body was long ago would be wrong.
     */
    static void breakOff(Entity player) {
        Body body = DRAWN.remove(player.getId());
        if (body == null || body.plates().isEmpty() || player.level().getGameTime() - body.tick() > 2) return;
        Map<String, Drawn> drawn = body.plates();
        Vec3 middle = player.position().add(0, player.getBbHeight() * 0.55, 0);
        List<Debris> off = new ArrayList<>();
        for (Map.Entry<String, Drawn> e : drawn.entrySet()) {
            Vector3f c = LanceMesh.SHELL.centre(e.getKey());
            Vector3f at = e.getValue().toWorld.transformPosition(new Vector3f(c));
            Vector3f out = new Vector3f((float) (at.x - middle.x), 0f, (float) (at.z - middle.z));
            if (out.lengthSquared() < 1e-6f) out.set(RANDOM.nextFloat() - 0.5f, 0f, RANDOM.nextFloat() - 0.5f);
            out.normalize().mul(FALL_SPEED * (0.6f + RANDOM.nextFloat() * 0.8f)).add(0f, 0.04f + RANDOM.nextFloat() * 0.04f, 0f);
            Debris d = new Debris(e.getKey(), e.getValue(), c, out, Mth.lerp(RANDOM.nextFloat(), 6f, 14f), FALL_TICKS - RANDOM.nextInt(4),
                    FALL_GRAVITY);
            off.add(d);
            DEBRIS.add(d);
        }
        JUST_OFF.put(player.getId(), off);
    }

    /**
     * Shatter Shell: the plates that just broke off (the shell is what bursts) fly outward fast and flat instead of falling, out to
     * about {@code reach} blocks from the body's middle.
     */
    static void burst(Entity player, float reach) {
        burstReach = reach;
        List<Debris> off = JUST_OFF.remove(player.getId());
        if (off == null) {
            PENDING.put(player.getId(), player.level().getGameTime());
            return;
        }
        for (Debris d : off) launch(d);
    }

    /** A whole set off a body with no shell on it (Shatter Shell with none up): broken off and flung at once. */
    private static void fling(Entity player, Map<String, Drawn> whole) {
        DRAWN.put(player.getId(), new Body(player.level().getGameTime(), whole));
        breakOff(player);
        List<Debris> off = JUST_OFF.remove(player.getId());
        if (off != null) off.forEach(ShellClient::launch);
    }

    /** Out flat and fast, slowing, arcing down: it comes to the ground at about the burst's reach as it shrinks away. */
    private static void launch(Debris d) {
        Vector3f flat = new Vector3f(d.velocity.x, 0f, d.velocity.z);
        if (flat.lengthSquared() < 1e-6f) flat.set(1f, 0f, 0f);
        float speed = burstSpeed(burstReach) * (0.88f + RANDOM.nextFloat() * 0.12f);
        d.velocity = flat.normalize().mul(speed).add(0f, 0.04f + RANDOM.nextFloat() * 0.06f, 0f);
        d.gravity = BURST_GRAVITY;
        d.spin *= 2.5f;
        d.burst = true;
        d.life = BURST_TICKS;
    }

    /** Shatter Shell's plates: how much speed each tick keeps (drag), and how hard they fall (blocks a tick, a tick). */
    static final float BURST_DRAG = 0.8f, BURST_GRAVITY = 0.009f;
    /** How long a burst plate lasts, the last {@link #SHRINK_TICKS} shrinking. */
    static final int BURST_TICKS = 16;

    /** The speed out that carries a plate, under the drag, to {@code reach} blocks: the drag's whole run is {@code v / (1 - drag)}. */
    static float burstSpeed(float reach) {
        return Math.max(0.1f, reach - 0.3f) * (1f - BURST_DRAG);
    }

    static void tick(ClientLevel level) {
        JUST_OFF.clear();
        PENDING.values().removeIf(tick -> level.getGameTime() - tick > 2);
        for (Iterator<Debris> it = DEBRIS.iterator(); it.hasNext(); ) {
            Debris d = it.next();
            if (++d.age >= d.life) it.remove();
        }
        DRAWN.keySet().removeIf(id -> level.getEntity(id) == null);
    }

    /** Where a piece of debris is {@code t} ticks after it broke off, from where it was: its flight under drag and gravity. */
    static Vector3f travelled(Vector3f velocity, float gravity, boolean burst, float t) {
        if (!burst) return new Vector3f(velocity.x * t, velocity.y * t - gravity * t * t * 0.5f, velocity.z * t);
        // v * (1 - drag^t) / (1 - drag): fast at first, slowing.
        float k = (float) ((1 - Math.pow(BURST_DRAG, t)) / (1 - BURST_DRAG));
        return new Vector3f(velocity.x * k, velocity.y * k - gravity * t * t * 0.5f, velocity.z * k);
    }

    /** Whether the level's entities are being drawn now (between the sky and the end of the entities), not a screen's figure. */
    private static boolean inWorld;

    static void render(RenderLevelStageEvent event) {
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_SKY) inWorld = true;
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return;
        inWorld = false;
        if (DEBRIS.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Vec3 cam = event.getCamera().getPosition();
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        for (Debris d : DEBRIS) {
            float t = d.age + partial;
            Vector3f moved = travelled(d.velocity, d.gravity, d.burst, t);
            Vector3f c = d.toWorld.transformPosition(new Vector3f(d.centre));
            float left = d.life - t;
            float size = left < SHRINK_TICKS ? Math.max(0f, left / SHRINK_TICKS) : 1f;
            Matrix4f m = new Matrix4f()
                    .translation((float) (c.x + moved.x - cam.x), (float) (c.y + moved.y - cam.y), (float) (c.z + moved.z - cam.z))
                    .rotate((float) Math.toRadians(d.spin * t), d.spinAxis)
                    .scale(size)
                    .translate(-c.x, -c.y, -c.z)
                    .mul(d.toWorld);
            PoseStack pose = new PoseStack();
            pose.last().pose().set(m);
            pose.last().normal().set(new Matrix3f().rotate((float) Math.toRadians(d.spin * t), d.spinAxis).mul(d.normals));
            int light = LevelRenderer.getLightColor(mc.level, BlockPos.containing(c.x + moved.x, c.y + moved.y, c.z + moved.z));
            LanceMesh.SHELL.draw(buffers, List.of(pose.last()), List.of(d.plate), light);
        }
        buffers.endBatch(LanceMesh.SHELL.body());
        buffers.endBatch(LanceMesh.SHELL.glow());
    }

    static void clear() {
        DRAWN.clear();
        PENDING.clear();
        DEBRIS.clear();
        JUST_OFF.clear();
    }
}

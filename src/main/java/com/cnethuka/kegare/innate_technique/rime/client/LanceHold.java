package com.cnethuka.kegare.innate_technique.rime.client;

import com.cnethuka.kegare.client.visual.ClientVisuals;
import com.cnethuka.kegare.config.KegareClientConfig;
import com.cnethuka.kegare.innate_technique.rime.Rime;
import com.cnethuka.kegare.innate_technique.rime.RimeIds;
import com.cnethuka.kegare.innatetechnique.visual.Visual;
import com.cnethuka.kegare.innatetechnique.visual.VisualPhase;
import com.cnethuka.kegare.innatetechnique.visual.VisualService;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Glacial Lance in the hand while it charges. It grows out of the fist in chunks that snap into place (LanceShape), so anyone
 * watching can tell a stub from a javelin from a full lance, and anyone tracking the caster sees it: it's drawn from the charge loop
 * the server already sends them. Let go too soon, or get stopped, and what was in the hand crumbles; a throw takes it with it.
 * <p>
 * On a body it's held the way the wind-up holds it ({@code tools/anims/glacial_lance.py}, LANCE_GRIP). In your own first person
 * there's no body: a small one stands off to the lower right instead, pointing away up the right side, clear of the crosshair.
 */
public final class LanceHold {
    static final String CHARGE = VisualService.key(Rime.GLACIAL_LANCE, VisualPhase.CHARGE_LOOP);
    /** The lance in the right hand: Blockbench pixels from the arm's pivot, degrees X then Y then Z (glacial_lance.py's LANCE_GRIP). */
    static final Vector3f GRIP_POSITION = new Vector3f(1.0f, -10.0f, 0.0f);
    static final Vector3f GRIP_ROTATION = new Vector3f(-64.3f, 16.3f, -88.2f);
    static final float GRIP_SCALE = 0.85f;
    /** Your own first person: where the fist would be in the hand pass (right, up, back from the eye), where it points, how big. */
    static final Vector3f SELF_GRIP = new Vector3f(0.78f, -0.58f, -0.95f);
    static final Vector3f SELF_POINTING = new Vector3f(0.42f, 0.5f, -0.76f);
    static final float SELF_SCALE = 0.36f;
    /** After a charge stops, how long to wait for the throw that says it wasn't a fizzle. Both are sent in the same server tick. */
    static final int THROW_GRACE = 1;
    /** Nothing shows in the hand until the arm has wound back this far (ticks): before that it would hang down the leg. */
    static final float WIND_UP_TICKS = 2f;
    /** Degrees a tick the body turns to face the aim while it charges. */
    static final float SQUARE_UP_SPEED = 20f;

    /** A charge that stopped and hasn't been thrown (yet): still drawn as it was, until it crumbles or is thrown. */
    private record Ended(long gameTime, float ticks, int growTicks) {}

    private static final Map<Integer, Ended> ENDED = new HashMap<>();
    /** Where each lance was last drawn: its own space to the world's, and the chunks it had. */
    private record Drawn(Matrix4f toWorld, float[] scales, long frame) {}

    private static final Map<Integer, Drawn> DRAWN = new HashMap<>();
    private static final Map<Integer, Integer> COUNT = new HashMap<>();
    private static final Map<Integer, float[]> SPAN = new HashMap<>();
    /** The whole lance's length (aft to head), for how big the frost round a part-grown one is. */
    private static final float FULL_LENGTH = 1.76f;
    /** Chunk snaps seen while drawing, played on the next tick (not in the middle of a frame). */
    private static final List<Vec3> SNAPS = new ArrayList<>();
    /** The frost and glow round each lance in a hand, moved along with it; {@code self} is the small first-person one. */
    private record Aura(ClientVisuals.PositionalEffect effect, boolean self) {}

    private static final Map<Integer, Aura> AURAS = new HashMap<>();
    private static final RandomSource RANDOM = RandomSource.create();

    private LanceHold() {}

    static void init() {
        ClientVisuals.listen(new ClientVisuals.Listener() {
            @Override
            public void loopStopped(Entity entity, String key, float ticks, int growTicks) {
                if (CHARGE.equals(key)) ENDED.put(entity.getId(), new Ended(entity.level().getGameTime(), ticks, growTicks));
            }

            @Override
            public void playedOnce(Entity entity, Visual visual) {
                if (visual.animation().filter(RimeIds.ANIM_GLACIAL_LANCE_RELEASE::equals).isPresent()) {
                    ENDED.remove(entity.getId()); // thrown: the lance in flight is the projectile now
                    DRAWN.remove(entity.getId());
                    COUNT.remove(entity.getId());
                }
            }
        });
    }

    static void addLayers(EntityRenderersEvent.AddLayers event) {
        for (PlayerSkin.Model model : event.getSkins()) {
            if (event.getSkin(model) instanceof PlayerRenderer renderer) renderer.addLayer(new Layer(renderer));
        }
    }

    /** Each chunk's size now, or null if there's no lance in this player's hand. */
    @Nullable
    static float[] scales(Entity player, float partialTick) {
        ClientVisuals.LoopTime time = ClientVisuals.loopTime(player, CHARGE, partialTick);
        float ticks;
        int grow;
        if (time != null) {
            ticks = time.ticks();
            grow = time.growTicks();
        } else {
            Ended ended = ENDED.get(player.getId());
            if (ended == null) return null;
            ticks = ended.ticks();
            grow = ended.growTicks();
        }
        float minimum = LanceShape.minimum();
        float[] scales = new float[LanceShape.PIECES.size()];
        if (ticks < WIND_UP_TICKS) return null;
        for (int i = 0; i < scales.length; i++) scales[i] = LanceShape.held(i, ticks - WIND_UP_TICKS, Math.max(1, grow), minimum);
        return scales;
    }

    /** Draws the lance in the pose's own space and remembers where that was. */
    private static void draw(Entity player, PoseStack pose, MultiBufferSource buffers, float[] scales, int light, boolean snaps) {
        LanceMesh.draw(pose.last(), buffers, LanceShape.PIECES, scales, light);
        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        Vec3 at = camera.getPosition();
        Matrix4f toWorld = new Matrix4f().translation((float) at.x, (float) at.y, (float) at.z).mul(pose.last().pose());
        DRAWN.put(player.getId(), new Drawn(toWorld, scales.clone(), player.level().getGameTime()));
        int count = 0;
        for (float s : scales) if (s > 0f) count++;
        Integer before = COUNT.put(player.getId(), count);
        // Remember where along it the chunks reach, for the frost round it.
        float near = 0f, far = 0f;
        for (int i = 0; i < scales.length; i++) {
            LanceMesh.Piece piece = scales[i] > 0f ? LanceMesh.piece(LanceShape.PIECES.get(i)) : null;
            if (piece == null) continue;
            near = Math.min(near, piece.minZ() * scales[i]);
            far = Math.max(far, piece.maxZ() * scales[i]);
        }
        SPAN.put(player.getId(), new float[] {near, far});
        if (snaps && before != null && count > before) {
            LanceMesh.Piece piece = LanceMesh.piece(LanceShape.PIECES.get(count - 1));
            float mid = piece == null ? 0f : (piece.minZ() + piece.maxZ()) * 0.5f;
            Vector3f point = toWorld.transformPosition(new Vector3f(0f, 0f, mid));
            SNAPS.add(new Vec3(point.x, point.y, point.z));
        }
    }

    // ---------------------------------------------------------------------------------------------- on a body

    /** The lance in the hand of the wind-up, for everyone who can see the body: other players, and you in third person. */
    static final class Layer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
        Layer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
            super(parent);
        }

        @Override
        public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player, float limbSwing, float limbSwingAmount,
                           float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
            float[] scales = scales(player, partialTick);
            if (scales == null) return;
            boolean left = player.getMainArm() == HumanoidArm.LEFT;
            pose.pushPose();
            (left ? getParentModel().leftArm : getParentModel().rightArm).translateAndRotate(pose);
            grip(pose, left);
            draw(player, pose, buffers, scales, light, true);
            pose.popPose();
        }
    }

    /**
     * From an arm's own space to the held lance's. The arm's space is turned half round Z into Blockbench's axes (up is up, out is the
     * arm's outside), where the grip is written; a left arm takes the grip mirrored.
     */
    static void grip(PoseStack pose, boolean left) {
        float side = left ? -1f : 1f;
        pose.mulPose(Axis.ZP.rotationDegrees(180f));
        pose.translate(side * GRIP_POSITION.x / 16f, GRIP_POSITION.y / 16f, GRIP_POSITION.z / 16f);
        pose.mulPose(Axis.XP.rotationDegrees(GRIP_ROTATION.x));
        pose.mulPose(Axis.YP.rotationDegrees(side * GRIP_ROTATION.y));
        pose.mulPose(Axis.ZP.rotationDegrees(side * GRIP_ROTATION.z));
        pose.scale(GRIP_SCALE, GRIP_SCALE, GRIP_SCALE);
    }

    // ---------------------------------------------------------------------------------------------- your own first person

    static void onRenderHand(RenderHandEvent event) {
        if (event.getHand() != InteractionHand.MAIN_HAND) return;
        Minecraft mc = Minecraft.getInstance();
        AbstractClientPlayer player = mc.player;
        if (player == null) return;
        float[] scales = scales(player, event.getPartialTick());
        if (scales == null) return;
        boolean left = player.getMainArm() == HumanoidArm.LEFT;
        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        selfPose(pose, left);
        draw(player, pose, event.getMultiBufferSource(), scales, event.getPackedLight(), false);
        pose.popPose();
    }

    /** From the hand pass's space (right, up, back from the eye) to the small lance's: off to the lower right, pointing away up that side. */
    static void selfPose(PoseStack pose, boolean left) {
        float side = left ? -1f : 1f;
        pose.translate(side * SELF_GRIP.x, SELF_GRIP.y, SELF_GRIP.z);
        Vector3f to = new Vector3f(side * SELF_POINTING.x, SELF_POINTING.y, SELF_POINTING.z).normalize();
        // Turn +Z onto that direction by the shortest way.
        pose.mulPose(new Quaternionf().rotationTo(new Vector3f(0f, 0f, 1f), to));
        pose.scale(SELF_SCALE, SELF_SCALE, SELF_SCALE);
    }

    // ---------------------------------------------------------------------------------------------- per tick

    static void tick(ClientLevel level) {
        for (AbstractClientPlayer player : level.players()) {
            if (ClientVisuals.loopTime(player, CHARGE, 0f) != null) squareUp(player);
        }
        for (Vec3 at : SNAPS) ClientVisuals.playAt(level, at, RimeIds.FX_GLACIAL_LANCE_SNAP, 0, 1f);
        SNAPS.clear();
        long now = level.getGameTime();
        for (Iterator<Map.Entry<Integer, Ended>> it = ENDED.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Integer, Ended> e = it.next();
            if (now - e.getValue().gameTime() < THROW_GRACE) continue;
            it.remove();
            Drawn drawn = DRAWN.remove(e.getKey());
            COUNT.remove(e.getKey());
            // Never thrown: whatever was in the hand breaks up and falls.
            if (drawn != null && now - drawn.frame() <= THROW_GRACE + 2) LanceAftermath.crumble(level, drawn.toWorld(), drawn.scales());
        }
        DRAWN.entrySet().removeIf(e -> now - e.getValue().frame() > 40 && !ENDED.containsKey(e.getKey()));
        COUNT.keySet().removeIf(id -> !DRAWN.containsKey(id));
        SPAN.keySet().removeIf(id -> !DRAWN.containsKey(id));
        auras(level, now);
    }

    /**
     * A body standing still lags its head by up to 50 degrees, and the lance it holds would point that far off the aim. While the
     * lance is drawn back, the body turns to face where it'll be thrown, quickly but not in a snap. Only how it's drawn here.
     */
    private static void squareUp(AbstractClientPlayer player) {
        float off = Mth.wrapDegrees(player.getYHeadRot() - player.yBodyRot);
        player.yBodyRot += Mth.clamp(off, -SQUARE_UP_SPEED, SQUARE_UP_SPEED);
    }

    /** Frost and a faint glow round every lance being held, following it; gone (left to fade) as soon as it's thrown or let go. */
    private static void auras(ClientLevel level, long now) {
        Minecraft mc = Minecraft.getInstance();
        boolean vanilla = KegareClientConfig.VFX_QUALITY.get() == KegareClientConfig.VfxQuality.VANILLA;
        for (Iterator<Map.Entry<Integer, Aura>> it = AURAS.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Integer, Aura> e = it.next();
            Drawn drawn = DRAWN.get(e.getKey());
            if (drawn == null || now - drawn.frame() > 1 || ENDED.containsKey(e.getKey())) {
                e.getValue().effect().stop(false);
                it.remove();
            }
        }
        for (Map.Entry<Integer, Drawn> e : DRAWN.entrySet()) {
            int id = e.getKey();
            Drawn drawn = e.getValue();
            float[] span = SPAN.get(id);
            if (span == null || now - drawn.frame() > 1 || ENDED.containsKey(id)) continue;
            Vector3f mid = drawn.toWorld().transformPosition(new Vector3f(0f, 0f, (span[0] + span[1]) * 0.5f));
            Vec3 at = new Vec3(mid.x, mid.y, mid.z);
            boolean self = mc.player != null && id == mc.player.getId() && mc.options.getCameraType().isFirstPerson();
            if (vanilla) {
                // Vanilla particles only: a snowflake off the lance now and then, and none in your own face.
                if (!self && RANDOM.nextInt(3) == 0) {
                    Vector3f p = drawn.toWorld().transformPosition(new Vector3f(0f, 0f, Mth.lerp(RANDOM.nextFloat(), span[0], span[1])));
                    level.addParticle(ParticleTypes.SNOWFLAKE, p.x, p.y, p.z, 0, -0.02, 0);
                }
                continue;
            }
            Aura aura = AURAS.get(id);
            if (aura != null && aura.self() != self) {
                aura.effect().stop(true);
                aura = null;
            }
            if (aura == null) {
                ClientVisuals.PositionalEffect effect = ClientVisuals.startAt(self ? RimeIds.FX_GLACIAL_LANCE_CHARGE_SELF : RimeIds.FX_GLACIAL_LANCE_CHARGE, at);
                if (effect == null) continue;
                aura = new Aura(effect, self);
                AURAS.put(id, aura);
            }
            aura.effect().moveTo(at);
            if (!self) aura.effect().scale(Mth.clamp((span[1] - span[0]) / FULL_LENGTH, 0.35f, 1f));
        }
    }

    static void clear() {
        AURAS.values().forEach(a -> a.effect().stop(true));
        AURAS.clear();
        SPAN.clear();
        ENDED.clear();
        DRAWN.clear();
        COUNT.clear();
        SNAPS.clear();
    }
}

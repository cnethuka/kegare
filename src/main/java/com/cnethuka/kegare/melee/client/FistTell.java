package com.cnethuka.kegare.melee.client;

import com.cnethuka.kegare.Kegare;
import com.cnethuka.kegare.client.visual.ClientVisuals;
import com.cnethuka.kegare.config.KegareClientConfig;
import com.cnethuka.kegare.config.KegareClientConfig.VfxQuality;
import com.cnethuka.kegare.melee.MeleeIds;
import com.cnethuka.kegare.melee.MeleeTell;
import com.cnethuka.kegare.melee.MeleeTellPayload;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.joml.Vector3f;

/**
 * A ready Reinforced Strike on a player's attacking hand, and Reinforced Fists (DESIGN §3.28), which is the same fire on both hands for as
 * long as it's on. The server says what each client should see ({@link MeleeTell}, {@link com.cnethuka.kegare.fists.FistsTell}), and this
 * draws it, in the arm's own space so it follows every animation: Malice burning round the hand and forearm ({@link FistFire}, and
 * {@link FistFlames} on your own hand). A Severed player never has one (DESIGN §3.27).
 * <p>
 * Whatever comes off the fist goes where the hand really is, reported each frame from where it was drawn. In first person there are two
 * ways that happens: while an animation shows your own body the layer draws as usual, and otherwise the hand pass does ({@link #onRenderHand}).
 */
public final class FistTell {
    /** No report of where the fist is for this long (off screen): go by the body, so nothing's left behind. */
    private static final int STALE_AFTER_TICKS = 3;
    /** How far off where the player is a fist can be and still be in the world. Further than that, this is a screen drawing them. */
    private static final double FARTHEST_FIST = 4.0;
    /** How often a curl comes off the fist: at full effects, at Reduced, and your own in first person. */
    private static final int WISP_EVERY_TICKS = 3;
    private static final int WISP_EVERY_TICKS_REDUCED = 6;
    private static final int WISP_EVERY_TICKS_SELF = 5;
    private static final float WISP_SIZE = 0.3f;
    /** Your own, right by the camera. */
    private static final float WISP_SIZE_SELF = 0.11f;
    private static final float WISP_SIZE_GRIP = 0.09f;
    private static final float WISP_SCALE_SELF = 0.42f;
    /** And in close: there's no arm in the way of them here, and they'd look like separate fires out to the side. */
    private static final float WISP_IN_SELF = 0.25f;
    /** In first person with something in hand: from where a bare fist would be to where vanilla puts the grip, in view space. */
    private static final float GRIP_ACROSS = 0.12f;
    private static final float GRIP_UP = 0.04f;
    /** The bottom middle of the fire's clump round the grip, from where vanilla's item transforms start. */
    private static final float CLUMP_ACROSS = -0.03f;
    private static final float CLUMP_UP = 0.083f;
    private static final float CLUMP_NEARER = 0.04f;
    /** The quads have to clear the corners of the arm's cube, wherever the camera is. */
    private static final float RADIUS = 3f / 16f;
    private static final float RADIUS_SLIM = 2.7f / 16f;

    private static final class Shown {
        MeleeTell.Look look;
        @Nullable Vec3 fist;
        int staleTicks;
        /** A ring of the curls that are off the fist: where each came off, and when. */
        final Vec3[] wispFrom = new Vec3[(FistFlames.WISP_LIFE_TICKS / WISP_EVERY_TICKS) + 1];
        final int[] wispBorn = new int[wispFrom.length];
        int nextWisp;

        Shown(MeleeTell.Look look) {
            this.look = look;
        }

        /** Nothing of it is left behind in the world (the curls are drawn from the ring, which goes with it). */
        void stopEffect() {
        }
    }

    /** A ready Strike's look on each player, as the server sent it (their attacking hand). */
    private static final Map<Integer, MeleeTell.Look> STRIKE = new HashMap<>();
    /** What's burning on each hand that has something on it, by {@link #key}: the look, where the fist is, its curls. */
    private static final Map<Long, Shown> SHOWN = new HashMap<>();
    private static float mainHandSwing;
    private static float mainHandEquip;

    private FistTell() {}

    public static void init(IEventBus modBus) {
        MeleeTellPayload.setClientHandler(FistTell::update);
        com.cnethuka.kegare.fists.FistsPayload.setClientHandler(FistBurn::update);
        modBus.addListener(FistTell::addLayers);
        NeoForge.EVENT_BUS.addListener(FistTell::onClientTick);
        NeoForge.EVENT_BUS.addListener(FistTell::onRenderHand);
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut e) -> {
            clear();
            FistBurn.clear();
        });
    }

    private static void addLayers(EntityRenderersEvent.AddLayers event) {
        for (PlayerSkin.Model model : event.getSkins()) {
            if (event.getSkin(model) instanceof PlayerRenderer renderer) {
                renderer.addLayer(new FistTellLayer(renderer, event.getContext().getItemInHandRenderer(), model == PlayerSkin.Model.SLIM));
            }
        }
    }

    private static void update(MeleeTellPayload payload) {
        if (payload.look().kind() == MeleeTell.Kind.NONE) STRIKE.remove(payload.entityId());
        else STRIKE.put(payload.entityId(), payload.look());
        Shown shown = SHOWN.get(key(payload.entityId(), false));
        if (shown != null) shown.stopEffect(); // a tell that outlives the Strike would lie; tick starts the right one, in the right color
    }

    private static void clear() {
        STRIKE.clear();
        SHOWN.values().forEach(Shown::stopEffect);
        SHOWN.clear();
    }

    /** A hand of a player: their attacking (main) hand, or the other. */
    private static long key(int entityId, boolean offHand) {
        return ((long) entityId << 1) | (offHand ? 1L : 0L);
    }

    /**
     * What to draw on this hand of this player, or null: a ready Strike on the attacking hand, Reinforced Fists' fire on either (the same
     * look exactly, in the same color, §3.28). Nothing on someone you can't see: a glow can't give away an invisible player.
     */
    @Nullable
    static MeleeTell.Look lookOn(Player player, boolean offHand) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || player.isInvisibleTo(mc.player)) return null;
        MeleeTell.Look strike = offHand ? null : STRIKE.get(player.getId());
        if (strike != null) return strike;
        Integer fists = FistBurn.colorOn(player);
        return fists == null ? null : MeleeTell.Look.glow(fists);
    }

    /**
     * The tell, with the pose already in the arm's space.
     *
     * @param toCamera toward the camera, in the axes the pose comes out in
     * @param up       which way is up, in those axes
     * @param ownHand  your own hand in first person, right by the camera: a layer round it would fill the corner of the screen
     */
    static void drawArm(PoseStack pose, MultiBufferSource buffers, MeleeTell.Look look, float time, boolean slim, boolean leftArm, int light,
                        Vector3f toCamera, Vector3f up, boolean ownHand) {
        if (look.kind() != MeleeTell.Kind.GLOW) return;
        float centre = FistShellMesh.armCentre(slim, leftArm);
        int rgb = MeleeTell.bright(look.color()) & 0xFFFFFF;
        if (!ownHand) {
            FistFire.drawArm(pose.last(), buffers.getBuffer(FistFire.renderType()), rgb, time, FistShellMesh.handEnd(), centre, slim, toCamera, up);
        } else {
            FistFlames.drawOwnHand(pose.last(), buffers.getBuffer(FistFlames.renderType()), rgb, time, FistShellMesh.handEnd(), centre,
                    slim ? RADIUS_SLIM : RADIUS, toCamera, up, leftArm);
        }
    }

    /** The curls off this player's fist, where they came off it, in the world as the camera sees it. */
    static void drawWisps(Player player, boolean offHand, MultiBufferSource buffers, float partialTick) {
        Shown shown = SHOWN.get(key(player.getId(), offHand));
        if (shown == null || shown.look.kind() != MeleeTell.Kind.GLOW || KegareClientConfig.VFX_QUALITY.get() == VfxQuality.VANILLA) return;
        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        Vec3 from = camera.getPosition();
        Vector3f right = new Vector3f(camera.getLeftVector()).negate();
        Vector3f up = new Vector3f(camera.getUpVector());
        Vector3f at = new Vector3f();
        int rgb = MeleeTell.bright(shown.look.color()) & 0xFFFFFF;
        VertexConsumer out = buffers.getBuffer(FistFlames.renderType());
        float time = player.tickCount + partialTick;
        for (int i = 0; i < shown.wispFrom.length; i++) {
            Vec3 start = shown.wispFrom[i];
            if (start == null) continue;
            float age = (time - shown.wispBorn[i]) / FistFlames.WISP_LIFE_TICKS;
            if (age < 0f || age >= 1f) continue;
            float seed = seed(shown.wispBorn[i]);
            FistFlames.wispPath(age, seed, at);
            FistFlames.drawWisp(out, (float) (start.x - from.x) + at.x, (float) (start.y - from.y) + at.y, (float) (start.z - from.z) + at.z,
                    right, up, WISP_SIZE, age, seed < 0.5f, rgb);
        }
    }

    /** Your own curls in first person: they rise off the hand on the screen and stay near it. */
    private static void drawWispsSelf(Shown shown, MultiBufferSource buffers, Vector3f fist, float time, float size) {
        if (KegareClientConfig.VFX_QUALITY.get() == VfxQuality.VANILLA) return;
        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        Vector3f right = new Vector3f(camera.getLeftVector()).negate();
        Vector3f up = new Vector3f(camera.getUpVector());
        Vector3f at = new Vector3f();
        int rgb = MeleeTell.bright(shown.look.color()) & 0xFFFFFF;
        VertexConsumer out = buffers.getBuffer(FistFlames.renderType());
        for (int i = 0; i < shown.wispFrom.length; i++) {
            if (shown.wispFrom[i] == null) continue;
            float age = (time - shown.wispBorn[i]) / FistFlames.WISP_LIFE_TICKS;
            if (age < 0f || age >= 1f) continue;
            float seed = seed(shown.wispBorn[i]);
            FistFlames.wispPath(age, seed, at).mul(WISP_SCALE_SELF);
            at.x *= WISP_IN_SELF;
            at.z *= WISP_IN_SELF;
            FistFlames.drawWisp(out, fist.x + at.x, fist.y + at.y, fist.z + at.z, right, up, size, age, seed < 0.5f, rgb);
        }
    }

    /** From when it came off, so it stays the same over its life and differs from the one before. */
    private static float seed(int born) {
        return Mth.frac(born * 0.6180339f);
    }

    /** The world position of the fist the pose is in the arm space of. {@code relative}: as the level draws it, relative to the camera. */
    static void reportFist(Player player, boolean offHand, Vector3f relative) {
        Shown shown = SHOWN.get(key(player.getId(), offHand));
        if (shown == null) return;
        shown.fist = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition().add(relative.x, relative.y, relative.z);
        shown.staleTicks = 0;
    }

    /**
     * Where a point in vanilla's first-person hand pass is, from the camera along the world's axes, which is what {@link #reportFist} wants.
     * The hand pass starts its pose with the camera's own rotation, so what comes out is already turned the world's way. Turning it again
     * put your own curls off to one side or behind you whenever you weren't facing north.
     */
    static Vector3f handPassPoint(PoseStack.Pose pose, float x, float y, float z) {
        return pose.pose().transformPosition(x, y, z, new Vector3f());
    }

    /** Is this fist being drawn in the world? A screen's preview of a player goes through the same layer, in its own space. */
    static boolean inWorld(Player player, Vector3f relative, float partialTick) {
        Vec3 at = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition().add(relative.x, relative.y, relative.z);
        return at.distanceToSqr(player.getPosition(partialTick)) < FARTHEST_FIST * FARTHEST_FIST;
    }

    /** Your own first person, where the fire has to stay small. */
    static boolean firstPersonSelf(Player player) {
        Minecraft mc = Minecraft.getInstance();
        return player == mc.player && mc.options.getCameraType().isFirstPerson();
    }

    /**
     * Vanilla first person: there's no body to draw on, and with something in hand no arm either, so the tell is drawn where vanilla
     * puts a bare arm (the same moves as {@code ItemInHandRenderer.renderPlayerArm}, swing and all), or the fire goes round the grip of
     * what's held. It waits for the off hand's turn, which comes after the main arm is down. Only the main hand's swing and equip numbers
     * are any use, so they're kept from its turn.
     */
    private static void onRenderHand(RenderHandEvent event) {
        Minecraft mc = Minecraft.getInstance();
        AbstractClientPlayer player = mc.player;
        if (player == null) return;
        if (event.getHand() == InteractionHand.MAIN_HAND) {
            mainHandSwing = event.getSwingProgress();
            mainHandEquip = event.getEquipProgress();
            return;
        }
        MeleeTell.Look look = lookOn(player, false);
        if (look == null) return;
        boolean left = player.getMainArm() == HumanoidArm.LEFT;
        float side = left ? -1f : 1f;
        float swing = mainHandSwing;
        float root = Mth.sqrt(swing);
        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        GuardHand.apply(pose, side, event.getPartialTick());
        if (!player.getMainHandItem().isEmpty()) { // no arm drawn: round what's held
            drawGripFire(pose, event, player, look, side, swing, root);
            pose.popPose();
            return;
        }
        pose.translate(side * (-0.3f * Mth.sin(root * Mth.PI) + 0.64000005f), 0.4f * Mth.sin(root * Mth.TWO_PI) - 0.6f - mainHandEquip * 0.6f,
                -0.4f * Mth.sin(swing * Mth.PI) - 0.71999997f);
        pose.mulPose(Axis.YP.rotationDegrees(side * 45f));
        pose.mulPose(Axis.YP.rotationDegrees(side * Mth.sin(root * Mth.PI) * 70f));
        pose.mulPose(Axis.ZP.rotationDegrees(side * Mth.sin(swing * swing * Mth.PI) * -20f));
        pose.translate(side * -1f, 3.6f, 3.5f);
        pose.mulPose(Axis.ZP.rotationDegrees(side * 120f));
        pose.mulPose(Axis.XP.rotationDegrees(200f));
        pose.mulPose(Axis.YP.rotationDegrees(side * -135f));
        pose.translate(side * 5.6f, 0f, 0f);
        if (mc.getEntityRenderDispatcher().getRenderer(player) instanceof PlayerRenderer renderer) {
            PlayerModel<AbstractClientPlayer> model = renderer.getModel();
            ModelPart arm = left ? model.leftArm : model.rightArm;
            model.attackTime = 0f;
            model.crouching = false;
            model.swimAmount = 0f;
            model.setupAnim(player, 0f, 0f, 0f, 0f, 0f);
            arm.xRot = 0f;
            arm.translateAndRotate(pose);
            boolean slim = player.getSkin().model() == PlayerSkin.Model.SLIM;
            float time = player.tickCount + event.getPartialTick();
            Vector3f fist = handPassPoint(pose.last(), FistShellMesh.armCentre(slim, left), FistShellMesh.fistY(), 0f);
            drawArm(pose, event.getMultiBufferSource(), look, time, slim, left, event.getPackedLight(), fist.negate(new Vector3f()).normalize(),
                    new Vector3f(0f, 1f, 0f), true);
            Shown shown = SHOWN.get(key(player.getId(), false));
            if (shown != null && look.kind() == MeleeTell.Kind.GLOW) drawWispsSelf(shown, event.getMultiBufferSource(), fist, time, WISP_SIZE_SELF);
            reportFist(player, false, fist);
        }
        pose.popPose();
    }

    /** With something in hand there's no arm to wrap: one clump round the grip, going wherever vanilla's item moves take it (renderArmWithItem's default case). */
    private static void drawGripFire(PoseStack pose, RenderHandEvent event, AbstractClientPlayer player, MeleeTell.Look look, float side, float swing, float root) {
        float lift = Mth.sin(root * Mth.PI);
        pose.translate(side * -0.4f * lift, 0.2f * Mth.sin(root * Mth.TWO_PI), -0.2f * Mth.sin(swing * Mth.PI));
        pose.translate(side * 0.56f, -0.52f - mainHandEquip * 0.6f, -0.72f);
        pose.mulPose(Axis.YP.rotationDegrees(side * (45f + Mth.sin(swing * swing * Mth.PI) * -20f)));
        pose.mulPose(Axis.ZP.rotationDegrees(side * lift * -20f));
        pose.mulPose(Axis.XP.rotationDegrees(lift * -80f));
        pose.mulPose(Axis.YP.rotationDegrees(side * -45f));
        // The hand pass's pose comes out relative to the camera along the world's axes, so the camera's own axes are what face it.
        Vector3f base = handPassPoint(pose.last(), side * CLUMP_ACROSS, CLUMP_UP, CLUMP_NEARER);
        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        Vector3f right = new Vector3f(camera.getLeftVector()).negate();
        Vector3f up = new Vector3f(camera.getUpVector());
        float time = player.tickCount + event.getPartialTick();
        FistFire.drawGrip(event.getMultiBufferSource().getBuffer(FistFire.renderType()), MeleeTell.bright(look.color()) & 0xFFFFFF, time, base, right, up);
        Vector3f middle = FistFire.gripMiddle(base, up);
        Shown shown = SHOWN.get(key(player.getId(), false));
        if (shown != null) drawWispsSelf(shown, event.getMultiBufferSource(), middle, time, WISP_SIZE_GRIP);
        reportFist(player, false, middle);
    }

    private static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) {
            clear();
            return;
        }
        // Players gone from view or dead: forget them (the server sends it again when they're back in range).
        java.util.Set<Integer> ids = new java.util.HashSet<>(STRIKE.keySet());
        ids.addAll(FistBurn.ids());
        for (int id : ids) {
            Entity entity = level.getEntity(id);
            if (!(entity instanceof Player player) || !player.isAlive()) {
                STRIKE.remove(id);
                FistBurn.forget(id);
            }
        }
        // Hands with nothing on them any more.
        for (Iterator<Map.Entry<Long, Shown>> entries = SHOWN.entrySet().iterator(); entries.hasNext(); ) {
            Map.Entry<Long, Shown> entry = entries.next();
            boolean offHand = (entry.getKey() & 1L) != 0;
            Entity entity = level.getEntity((int) (entry.getKey() >> 1));
            if (!(entity instanceof Player player) || !player.isAlive() || lookOn(player, offHand) == null) {
                entry.getValue().stopEffect();
                entries.remove();
            }
        }
        ids = new java.util.HashSet<>(STRIKE.keySet());
        ids.addAll(FistBurn.ids());
        for (int id : ids) {
            if (!(level.getEntity(id) instanceof Player player)) continue;
            for (boolean offHand : new boolean[] {false, true}) {
                MeleeTell.Look look = lookOn(player, offHand);
                if (look == null) continue;
                Shown shown = SHOWN.computeIfAbsent(key(id, offHand), k -> new Shown(look));
                if (!shown.look.equals(look)) {
                    shown.stopEffect(); // tick starts the right one, in the right color
                    shown.look = look;
                }
                tickHand(level, player, shown);
            }
        }
    }

    /** One hand's curls for this tick, from where its fist was last drawn. */
    private static void tickHand(ClientLevel level, Player player, Shown shown) {
        Vec3 fist = shown.fist != null && shown.staleTicks < STALE_AFTER_TICKS ? shown.fist : player.position().add(0, player.getBbHeight() * 0.45, 0);
        shown.staleTicks++;
        boolean self = firstPersonSelf(player);
        VfxQuality quality = KegareClientConfig.VFX_QUALITY.get();
        if (quality == VfxQuality.VANILLA) {
            if (!self) vanillaParticles(level, shown.look, fist);
            return;
        }
        int every = self ? WISP_EVERY_TICKS_SELF : quality == VfxQuality.REDUCED ? WISP_EVERY_TICKS_REDUCED : WISP_EVERY_TICKS;
        if (player.tickCount % every == 0) {
            shown.wispFrom[shown.nextWisp] = fist;
            shown.wispBorn[shown.nextWisp] = player.tickCount;
            shown.nextWisp = (shown.nextWisp + 1) % shown.wispFrom.length;
        }
    }

    private static void vanillaParticles(ClientLevel level, MeleeTell.Look look, Vec3 at) {
        int rgb = MeleeTell.bright(look.color());
        level.addParticle(new DustParticleOptions(new Vector3f((rgb >> 16 & 0xFF) / 255f, (rgb >> 8 & 0xFF) / 255f, (rgb & 0xFF) / 255f), 0.8f),
                at.x, at.y, at.z, 0, 0.02, 0);
    }
}

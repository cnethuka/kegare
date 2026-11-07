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
 * A ready Reinforced Strike on a player's attacking hand. The server says what each client should see ({@link MeleeTell}), and this
 * draws it: a shell over the hand and forearm, in the arm's own space so it follows every animation, and an effect flowing off the fist.
 * Glowing in the Malice color, or for the physical tell a strained forearm with steam coming off it.
 * <p>
 * The effect goes where the shell's hand really is, reported each frame from where it was drawn. In first person there are two ways
 * that happens: while an animation shows your own body the layer draws as usual, and otherwise the hand pass does ({@link #onRenderHand}).
 */
public final class FistTell {
    private static final ResourceLocation SHELL = Kegare.id("textures/entity/fist_shell.png");
    private static final ResourceLocation STRAIN = Kegare.id("textures/entity/fist_strain.png");
    /** No report of where the fist is for this long (off screen): go by the body, so nothing's left behind. */
    private static final int STALE_AFTER_TICKS = 3;

    private static final class Shown {
        MeleeTell.Look look;
        @Nullable ClientVisuals.PositionalEffect effect;
        @Nullable ResourceLocation effectId;
        @Nullable Vec3 fist;
        int staleTicks;

        Shown(MeleeTell.Look look) {
            this.look = look;
        }

        void stopEffect() {
            if (effect != null) effect.stop(true); // a tell that outlives the Strike would lie to the other player
            effect = null;
            effectId = null;
        }
    }

    private static final Map<Integer, Shown> SHOWN = new HashMap<>();
    private static float mainHandSwing;
    private static float mainHandEquip;

    private FistTell() {}

    public static void init(IEventBus modBus) {
        MeleeTellPayload.setClientHandler(FistTell::update);
        modBus.addListener(FistTell::addLayers);
        modBus.addListener((RegisterClientReloadListenersEvent e) -> e.registerReloadListener((ResourceManagerReloadListener) manager -> FistShellMesh.forget()));
        NeoForge.EVENT_BUS.addListener(FistTell::onClientTick);
        NeoForge.EVENT_BUS.addListener(FistTell::onRenderHand);
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut e) -> clear());
    }

    private static void addLayers(EntityRenderersEvent.AddLayers event) {
        for (PlayerSkin.Model model : event.getSkins()) {
            if (event.getSkin(model) instanceof PlayerRenderer renderer) {
                renderer.addLayer(new FistTellLayer(renderer, event.getContext().getItemInHandRenderer(), model == PlayerSkin.Model.SLIM));
            }
        }
    }

    private static void update(MeleeTellPayload payload) {
        Shown shown = SHOWN.get(payload.entityId());
        if (payload.look().kind() == MeleeTell.Kind.NONE) {
            if (shown != null) shown.stopEffect();
            SHOWN.remove(payload.entityId());
        } else if (shown == null) {
            SHOWN.put(payload.entityId(), new Shown(payload.look()));
        } else if (!shown.look.equals(payload.look())) {
            shown.stopEffect(); // tick starts the right one, in the right color
            shown.look = payload.look();
        }
    }

    private static void clear() {
        SHOWN.values().forEach(Shown::stopEffect);
        SHOWN.clear();
    }

    /** What to draw on this player, or null. Nothing on someone you can't see: a glow can't give away an invisible player. */
    @Nullable
    static MeleeTell.Look lookOn(Player player) {
        Shown shown = SHOWN.get(player.getId());
        Minecraft mc = Minecraft.getInstance();
        if (shown == null || mc.player == null || player.isInvisibleTo(mc.player)) return null;
        return shown.look;
    }

    /** The shell, with the pose already in the arm's space. */
    static void drawShell(PoseStack pose, MultiBufferSource buffers, MeleeTell.Look look, float time, boolean slim, boolean leftArm, int light) {
        FistShellMesh mesh = FistShellMesh.of(slim);
        float centre = FistShellMesh.armCentre(slim, leftArm);
        PoseStack.Pose last = pose.last();
        if (look.kind() == MeleeTell.Kind.GLOW) {
            int color = MeleeTell.bright(look.color()) & 0xFFFFFF;
            int pale = mix(color, 0xFFFFFF, 0.5f);
            float breathe = 0.86f + 0.14f * Mth.sin(time * 0.45f);
            VertexConsumer out = buffers.getBuffer(RenderType.entityTranslucentEmissive(SHELL));
            mesh.draw(last, out, leftArm, centre, 1f, time * -0.045f, color, 0.92f * breathe, light, false);
            mesh.draw(last, out, leftArm, centre, 1.1f, time * -0.075f + 0.37f, pale, 0.62f * breathe, light, false);
        } else {
            VertexConsumer out = buffers.getBuffer(RenderType.entityTranslucent(STRAIN));
            mesh.draw(last, out, leftArm, centre, 1.08f, 0f, 0xFFFFFF, 1f, light, true);
        }
    }

    private static int mix(int from, int to, float t) {
        int r = Math.round((from >> 16 & 0xFF) + ((to >> 16 & 0xFF) - (from >> 16 & 0xFF)) * t);
        int g = Math.round((from >> 8 & 0xFF) + ((to >> 8 & 0xFF) - (from >> 8 & 0xFF)) * t);
        int b = Math.round((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * t);
        return r << 16 | g << 8 | b;
    }

    /** The world position of the fist the pose is in the arm space of. {@code relative}: as the level draws it, relative to the camera. */
    static void reportFist(Player player, Vector3f relative) {
        Shown shown = SHOWN.get(player.getId());
        if (shown == null) return;
        shown.fist = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition().add(relative.x, relative.y, relative.z);
        shown.staleTicks = 0;
    }

    /**
     * Vanilla first person: there's no body to draw on, and with something in hand no arm either, so the shell is drawn where
     * vanilla puts a bare arm (the same moves as {@code ItemInHandRenderer.renderPlayerArm}, swing and all), over the empty hand or
     * behind the item. It waits for the off hand's turn, which comes after the main arm is down: the shell doesn't write depth, so an arm
     * drawn after it would paint straight over it. Only the main hand's swing and equip numbers are any use, so they're kept from its turn.
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
        MeleeTell.Look look = lookOn(player);
        if (look == null) return;
        boolean left = player.getMainArm() == HumanoidArm.LEFT;
        float side = left ? -1f : 1f;
        float swing = mainHandSwing;
        float root = Mth.sqrt(swing);
        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        GuardHand.apply(pose, side, event.getPartialTick());
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
            drawShell(pose, event.getMultiBufferSource(), look, time, slim, left, event.getPackedLight());
            Vector3f fist = pose.last().pose().transformPosition(FistShellMesh.armCentre(slim, left), FistShellMesh.fistY(), 0f, new Vector3f());
            Camera camera = mc.gameRenderer.getMainCamera();
            reportFist(player, fist.rotate(camera.rotation()));
        }
        pose.popPose();
    }

    private static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) {
            clear();
            return;
        }
        Iterator<Map.Entry<Integer, Shown>> entries = SHOWN.entrySet().iterator();
        while (entries.hasNext()) {
            Map.Entry<Integer, Shown> entry = entries.next();
            Shown shown = entry.getValue();
            Entity entity = level.getEntity(entry.getKey());
            if (!(entity instanceof Player player) || !player.isAlive()) {
                shown.stopEffect();
                entries.remove();
                continue;
            }
            if (lookOn(player) == null) {
                shown.stopEffect();
                continue;
            }
            Vec3 fist = shown.fist != null && shown.staleTicks < STALE_AFTER_TICKS ? shown.fist : player.position().add(0, player.getBbHeight() * 0.45, 0);
            shown.staleTicks++;
            boolean self = player == mc.player && mc.options.getCameraType().isFirstPerson();
            if (KegareClientConfig.VFX_QUALITY.get() == VfxQuality.VANILLA) {
                shown.stopEffect();
                if (!self) vanillaParticles(level, shown.look, fist);
                continue;
            }
            ResourceLocation wanted = shown.look.kind() == MeleeTell.Kind.GLOW
                    ? (self ? MeleeIds.FX_FIST_WISPS_SELF : MeleeIds.FX_FIST_WISPS)
                    : (self ? MeleeIds.FX_FIST_STEAM_SELF : MeleeIds.FX_FIST_STEAM);
            if (shown.effect == null || !wanted.equals(shown.effectId)) {
                shown.stopEffect();
                shown.effect = ClientVisuals.startAt(wanted, fist);
                shown.effectId = wanted;
                if (shown.effect != null && shown.look.kind() == MeleeTell.Kind.GLOW) shown.effect.tint(MeleeTell.bright(shown.look.color()));
            } else {
                shown.effect.moveTo(fist);
            }
        }
    }

    private static void vanillaParticles(ClientLevel level, MeleeTell.Look look, Vec3 at) {
        if (look.kind() == MeleeTell.Kind.GLOW) {
            int rgb = MeleeTell.bright(look.color());
            level.addParticle(new DustParticleOptions(new Vector3f((rgb >> 16 & 0xFF) / 255f, (rgb >> 8 & 0xFF) / 255f, (rgb & 0xFF) / 255f), 0.8f),
                    at.x, at.y, at.z, 0, 0.02, 0);
        } else if (level.getGameTime() % 2 == 0) {
            level.addParticle(ParticleTypes.CLOUD, at.x, at.y, at.z, 0, 0.03, 0);
        }
    }
}

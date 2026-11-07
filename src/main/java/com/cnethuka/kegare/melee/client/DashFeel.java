package com.cnethuka.kegare.melee.client;

import com.cnethuka.kegare.Kegare;
import com.cnethuka.kegare.config.KegareClientConfig;
import com.cnethuka.kegare.config.KegareClientConfig.VfxQuality;
import com.cnethuka.kegare.melee.MeleeDashPayload;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.common.NeoForge;
import org.joml.Matrix4f;

/**
 * A dash from inside it, where the trail everyone else sees can't be drawn: a brief kick of the field of view and streaks round the
 * screen's edge, flowing from where you're headed. Nothing crosses the middle of the screen. Half of it at Reduced, none of it at
 * Vanilla, and the kick answers to vanilla's own FOV Effects slider too. No screen shake: that's for the Impact System.
 */
public final class DashFeel {
    private static final ResourceLocation LAYER = Kegare.id("dash_streaks");
    private static final int STREAKS_FULL = 22;
    private static final int STREAKS_REDUCED = 10;
    private static final int COLOR = 0xFFFFFF;
    private static final float PEAK_ALPHA = 0.8f;

    private static long startTick = Long.MIN_VALUE;
    private static float directionX;
    private static float directionZ;
    private static long seed;

    private DashFeel() {}

    public static void init(IEventBus modBus) {
        modBus.addListener((RegisterGuiLayersEvent event) -> event.registerBelow(VanillaGuiLayers.CROSSHAIR, LAYER, DashFeel::render));
        NeoForge.EVENT_BUS.addListener(DashFeel::onFov);
    }

    static void start(MeleeDashPayload payload) {
        Minecraft mc = Minecraft.getInstance();
        if (payload.distance() <= 0 || mc.level == null) return;
        startTick = mc.level.getGameTime();
        directionX = payload.dirX();
        directionZ = payload.dirZ();
        seed = mc.level.getGameTime() * 31L;
    }

    static void reset() {
        startTick = Long.MIN_VALUE;
    }

    /** 1 at full, half at Reduced, 0 at Vanilla. */
    private static float qualityScale() {
        VfxQuality quality = KegareClientConfig.VFX_QUALITY.get();
        return quality == VfxQuality.FULL ? 1f : quality == VfxQuality.REDUCED ? 0.5f : 0f;
    }

    private static float age(Minecraft mc, float partialTick) {
        return startTick == Long.MIN_VALUE || mc.level == null ? -1f : mc.level.getGameTime() - startTick + partialTick;
    }

    private static boolean showing(Minecraft mc) {
        return mc.player != null && mc.options.getCameraType().isFirstPerson();
    }

    private static void onFov(ViewportEvent.ComputeFov event) {
        if (!event.usedConfiguredFov()) return; // the hand's own view stays put
        Minecraft mc = Minecraft.getInstance();
        if (!showing(mc)) return;
        float strength = qualityScale() * mc.options.fovEffectScale().get().floatValue();
        if (strength > 0f) event.setFOV(event.getFOV() * DashKick.fovMultiplier(age(mc, (float) event.getPartialTick()), strength));
    }

    private static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (!showing(mc) || mc.options.hideGui) return;
        float quality = qualityScale();
        if (quality <= 0f) return;
        float partial = delta.getGameTimeDeltaPartialTick(false);
        float age = age(mc, partial);
        float strength = DashKick.envelope(age);
        if (strength <= 0f) return;

        float width = g.guiWidth(), height = g.guiHeight();
        float cx = width / 2f, cy = height / 2f;
        // Where you're headed, on the screen: the middle for a forward dash, out at the edge for a sideways one. Everything flows away from it.
        float yaw = mc.player.getViewYRot(partial) * Mth.DEG_TO_RAD;
        float forward = -Mth.sin(yaw) * directionX + Mth.cos(yaw) * directionZ;
        float right = -Mth.cos(yaw) * directionX - Mth.sin(yaw) * directionZ;
        float fromX = cx + right * width * 0.5f;
        float flow = forward < -0.3f ? -1f : 1f; // backwards: it all rushes in towards the middle instead
        float travel = height * 0.16f * age / DashKick.DURATION;

        Matrix4f pose = g.pose().last().pose();
        VertexConsumer out = g.bufferSource().getBuffer(RenderType.gui());
        RandomSource random = RandomSource.create(seed);
        int streaks = quality >= 1f ? STREAKS_FULL : STREAKS_REDUCED;
        for (int i = 0; i < streaks; i++) {
            float angle = (i + random.nextFloat()) / streaks * Mth.TWO_PI;
            float reach = 0.8f + random.nextFloat() * 0.35f;
            float length = height * (0.14f + random.nextFloat() * 0.2f);
            float thick = (0.5f + random.nextFloat() * 0.7f) * Math.min(1f, g.guiHeight() / 240f);
            float x = cx + Mth.cos(angle) * cx * reach;
            float y = cy + Mth.sin(angle) * cy * reach;
            float dx = x - fromX, dy = y - cy;
            float norm = Mth.sqrt(dx * dx + dy * dy);
            if (norm < 1f) continue;
            dx /= norm;
            dy /= norm;
            float shift = flow > 0f ? travel : height * 0.16f - travel;
            float x0 = x + dx * shift, y0 = y + dy * shift;
            float x1 = x0 + dx * length, y1 = y0 + dy * length;
            float px = -dy * thick, py = dx * thick;
            int inner = alpha(strength * quality, x0, y0, cx, cy);
            int outer = alpha(strength * quality, x1, y1, cx, cy);
            if (inner == 0 && outer == 0) continue;
            // A needle: nothing at the inner tip, its full width at the outer end.
            vertex(out, pose, x0, y0, inner);
            vertex(out, pose, x1 + px, y1 + py, outer);
            vertex(out, pose, x1 - px, y1 - py, outer);
            vertex(out, pose, x0, y0, inner);
        }
        g.flush();
    }

    private static int alpha(float strength, float x, float y, float cx, float cy) {
        float ex = (x - cx) / cx, ey = (y - cy) / cy;
        return Mth.floor(255f * PEAK_ALPHA * strength * DashKick.edgeFade(Mth.sqrt(ex * ex + ey * ey)));
    }

    private static void vertex(VertexConsumer out, Matrix4f pose, float x, float y, int alpha) {
        out.addVertex(pose, x, y, 0f).setColor(COLOR >> 16 & 0xFF, COLOR >> 8 & 0xFF, COLOR & 0xFF, alpha);
    }
}

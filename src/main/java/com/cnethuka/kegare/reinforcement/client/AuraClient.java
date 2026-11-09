package com.cnethuka.kegare.reinforcement.client;

import com.cnethuka.kegare.Kegare;
import com.cnethuka.kegare.config.KegareClientConfig;
import com.cnethuka.kegare.config.KegareClientConfig.VfxQuality;
import com.cnethuka.kegare.melee.MeleeTell;
import com.cnethuka.kegare.reinforcement.AuraPayload;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.common.NeoForge;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Reinforcement's aura (DESIGN §3.16): Malice wrapped round the whole body, the way a sorcerer's energy is drawn flowing. Our own
 * art ({@code tools/gen_art.py}): a sheet of frames that hugs the player's silhouette a little way out and rises into flame-like
 * tongues over the head and shoulders, always flowing upward, with a hard, near-black ink outline (irregular, with flicks and small
 * breaks) and inside it a see-through glow in the Malice color, strongest at the outline and thin near the body.
 * <ul>
 * <li>Drawn as an upright card facing the camera, just behind the player, so the body stays in front of it and readable; small
 * wisps peel off the tongues' tips and rise away.</li>
 * <li>Fuller and faster in a fight ({@link AuraPayload#FIGHT}), calmer out of one. Background Reinforcement alone
 * ({@link AuraPayload#CALM}): the same aura much calmer and fainter, a thin flow with a light outline.</li>
 * <li>Your own first person: only a faint flow up the edges of the view, round your arms' corner, never in the middle 60%.</li>
 * <li>Reduced: a simpler outline and fewer wisps. Vanilla: dust in the Malice color rising round the body. An invisible player shows
 * nothing.</li>
 * </ul>
 * The color is what the server sends this viewer ({@code AuraTell}): under a disguise, the face's.
 */
public final class AuraClient {
    static final ResourceLocation SHEET = Kegare.id("textures/entity/aura.png");
    static final ResourceLocation SHEET_SIMPLE = Kegare.id("textures/entity/aura_simple.png");
    static final ResourceLocation WISP = Kegare.id("textures/entity/aura_wisp.png");
    static final ResourceLocation EDGE = Kegare.id("textures/entity/aura_edge.png");
    static final ResourceLocation SHIMMER = Kegare.id("textures/entity/aura_shimmer.png");
    /** Background RMT's shimmer, as a Six Eyes bearer sees it: RMT's green. */
    static final int SHIMMER_RGB = 0x4ED873;
    static final ResourceLocation LAYER = Kegare.id("aura_edges");
    /** gen_art.py's sheet: frames, size in blocks, and the feet's height above its bottom edge, for a body 1.8 tall. */
    static final int FRAMES = 16;
    static final float SHEET_W = 1.6f, SHEET_H = 3.2f, FEET = 0.30f, BODY = 1.8f;
    /** How far behind the player (from the camera) the card stands. */
    static final float BEHIND = 0.32f;
    /** Frames a tick: calm, out of a fight, in one. */
    static final float SPEED_CALM = 0.32f, SPEED = 0.55f, SPEED_FIGHT = 0.9f;
    /** In a fight the aura stands this much bigger (and the calm one this much smaller). */
    static final float FULLER = 0.1f, CALMER = -0.05f;
    /** How see-through the whole aura is: the full one, out of and in a fight, and the calm one. */
    static final float ALPHA = 0.85f, ALPHA_FIGHT = 1f, ALPHA_CALM = 0.42f;
    /** The tongues' tips (left to right, blocks from the middle and above the feet, for a body 1.8 tall), where wisps peel off. */
    static final float[][] TIPS = {{-0.46f, 2.05f}, {-0.24f, 2.3f}, {0f, 2.55f}, {0.22f, 2.32f}, {0.45f, 2.05f}};
    /** First person: the flow's width at each edge as a share of the screen (the middle 60% stays clear), and how strong it is. */
    static final float EDGE_SHARE = 0.12f, EDGE_ARM_SHARE = 0.18f, EDGE_ALPHA = 0.22f, EDGE_ALPHA_CALM = 0.11f;

    private static final RandomSource RANDOM = RandomSource.create();

    /** A player's aura as this client knows it. */
    private static final class Entry {
        AuraPayload payload;
        float phase = RANDOM.nextFloat() * FRAMES, prevPhase = phase;
        /** 0 calm, 1 in a fight, followed smoothly. */
        float fight, prevFight;
        int wispClock;

        Entry(AuraPayload payload) {
            this.payload = payload;
        }

        boolean aura() {
            return payload.has(AuraPayload.FULL) || payload.has(AuraPayload.CALM);
        }

        boolean calm() {
            return !payload.has(AuraPayload.FULL) && payload.has(AuraPayload.CALM);
        }

        boolean shimmer() {
            return payload.has(AuraPayload.SHIMMER);
        }

        int shimmerClock;
    }

    /** A wisp off a tongue (or a shimmer's glint): where it is in the world, how it drifts, its age. */
    private static final class Wisp {
        Vec3 at, prev;
        final Vec3 drift;
        final int rgb, life;
        final float size, alpha;
        boolean glint;
        int age;

        Wisp(Vec3 at, Vec3 drift, int rgb, int life, float size, float alpha) {
            this.at = at;
            this.prev = at;
            this.drift = drift;
            this.rgb = rgb;
            this.life = life;
            this.size = size;
            this.alpha = alpha;
        }
    }

    private static final Map<Integer, Entry> SHOWN = new HashMap<>();
    private static final List<Wisp> WISPS = new ArrayList<>();

    private AuraClient() {}

    public static void init(IEventBus modBus) {
        AuraPayload.setClientHandler(AuraClient::update);
        modBus.addListener((RegisterGuiLayersEvent e) -> e.registerBelow(VanillaGuiLayers.CROSSHAIR, LAYER, AuraClient::renderEdges));
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post e) -> tick());
        NeoForge.EVENT_BUS.addListener((RenderLevelStageEvent e) -> {
            // After clouds and weather: the aura writes no depth, so anything drawn after it would paint over it.
            if (e.getStage() == RenderLevelStageEvent.Stage.AFTER_WEATHER) {
                render(e.getCamera(), e.getPartialTick().getGameTimeDeltaPartialTick(false));
            }
        });
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut e) -> clear());
    }

    static void update(AuraPayload payload) {
        if (payload.flags() == 0) {
            SHOWN.remove(payload.entityId());
            return;
        }
        Entry entry = SHOWN.get(payload.entityId());
        if (entry == null) SHOWN.put(payload.entityId(), new Entry(payload));
        else entry.payload = payload;
    }

    static void clear() {
        SHOWN.clear();
        WISPS.clear();
    }

    /** What's shown on this player for you (null if nothing, or you can't see them). */
    @org.jetbrains.annotations.Nullable
    public static AuraPayload on(Player player) {
        Entry entry = SHOWN.get(player.getId());
        Minecraft mc = Minecraft.getInstance();
        if (entry == null || mc.player == null || player.isInvisibleTo(mc.player)) return null;
        return entry.payload;
    }

    static VfxQuality quality() {
        return KegareClientConfig.VFX_QUALITY.get();
    }

    private static boolean firstPerson(Minecraft mc, Player player) {
        return player == mc.getCameraEntity() && mc.options.getCameraType().isFirstPerson();
    }

    private static float scale(Player player) {
        return player.getBbHeight() / BODY;
    }

    // ---------------------------------------------------------------------------------------------- ticking

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.isPaused()) return;
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        for (Iterator<Map.Entry<Integer, Entry>> it = SHOWN.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Integer, Entry> e = it.next();
            Entry entry = e.getValue();
            entry.prevPhase = entry.phase;
            entry.prevFight = entry.fight;
            float target = entry.payload.has(AuraPayload.FIGHT) ? 1f : 0f;
            entry.fight += (target - entry.fight) * 0.15f;
            entry.phase += entry.calm() ? SPEED_CALM : Mth.lerp(entry.fight, SPEED, SPEED_FIGHT);
            if (entry.phase >= FRAMES * 64) {
                entry.phase -= FRAMES * 64;
                entry.prevPhase -= FRAMES * 64;
            }
            if (!(level.getEntity(e.getKey()) instanceof Player player) || !player.isAlive() || on(player) == null) continue;
            boolean mine = firstPerson(mc, player);
            if (entry.shimmer() && !mine) shimmer(level, player, entry);
            if (!entry.aura()) continue;
            int rgb = MeleeTell.bright(entry.payload.color()) & 0xFFFFFF;
            if (quality() == VfxQuality.VANILLA) {
                if (!mine) dust(level, player, entry, rgb);
                continue;
            }
            if (mine) continue;
            // Wisps: one every so often off a tongue's tip, more often in a fight, rarer at Reduced and on the calm aura.
            int every = entry.calm() ? 16 : entry.fight > 0.5f ? 3 : 5;
            if (quality() == VfxQuality.REDUCED) every *= 2;
            if (++entry.wispClock >= every) {
                entry.wispClock = 0;
                wisp(player, entry, rgb, cam);
            }
        }
        for (Iterator<Wisp> it = WISPS.iterator(); it.hasNext(); ) {
            Wisp w = it.next();
            w.prev = w.at;
            w.at = w.at.add(w.drift.scale(1.0 - 0.4 * w.age / (double) w.life));
            if (++w.age >= w.life) it.remove();
        }
    }

    /** A wisp at one of the tongues' tips, in the card's plane as the camera sees it now. */
    private static void wisp(Player player, Entry entry, int rgb, Vec3 cam) {
        float s = scale(player);
        float[] tip = TIPS[RANDOM.nextInt(TIPS.length)];
        Vec3 pos = player.position();
        Vec3 away = new Vec3(pos.x - cam.x, 0, pos.z - cam.z);
        Vec3 right = away.lengthSqr() < 1.0e-4 ? Vec3.directionFromRotation(0, player.getYRot() + 90) : new Vec3(-away.z, 0, away.x).normalize();
        double lateral = (tip[0] + (RANDOM.nextFloat() - 0.5f) * 0.12f) * s;
        Vec3 at = pos.add(right.scale(lateral)).add(0, (tip[1] - 0.15f) * s, 0);
        Vec3 drift = right.scale((RANDOM.nextFloat() - 0.5f) * 0.012 + lateral * 0.01).add(0, 0.035 + RANDOM.nextFloat() * 0.02, 0)
                .add(player.getDeltaMovement().multiply(0.5, 0, 0.5));
        float alpha = entry.calm() ? ALPHA_CALM : 0.9f;
        WISPS.add(new Wisp(at, drift, rgb, 12 + RANDOM.nextInt(8), (0.10f + RANDOM.nextFloat() * 0.07f) * s, alpha));
    }

    /**
     * Background RMT as a Six Eyes bearer sees it (DESIGN §3.31): a faint green shimmer, small soft glints coming and going over the body
     * and drifting up. Reduced: half as many. Vanilla: a few specks of green dust.
     */
    private static void shimmer(ClientLevel level, Player player, Entry entry) {
        int every = quality() == VfxQuality.FULL ? 3 : quality() == VfxQuality.REDUCED ? 6 : 5;
        if (++entry.shimmerClock < every) return;
        entry.shimmerClock = 0;
        double w = player.getBbWidth() * 0.6;
        double x = player.getX() + (RANDOM.nextDouble() - 0.5) * 2 * w, z = player.getZ() + (RANDOM.nextDouble() - 0.5) * 2 * w;
        double y = player.getY() + 0.1 + RANDOM.nextDouble() * player.getBbHeight();
        if (quality() == VfxQuality.VANILLA) {
            level.addParticle(new DustParticleOptions(new Vector3f(0.31f, 0.85f, 0.45f), 0.5f), x, y, z, 0, 0.2, 0);
            return;
        }
        Wisp glint = new Wisp(new Vec3(x, y, z), new Vec3(0, 0.01 + RANDOM.nextDouble() * 0.01, 0), SHIMMER_RGB, 10 + RANDOM.nextInt(8),
                0.06f + RANDOM.nextFloat() * 0.05f, 0.55f);
        glint.glint = true;
        WISPS.add(glint);
    }

    /** Vanilla: dust in the Malice color rising round the body. */
    private static void dust(ClientLevel level, Player player, Entry entry, int rgb) {
        int count = entry.calm() ? (RANDOM.nextInt(3) == 0 ? 1 : 0) : entry.fight > 0.5f ? 3 : 2;
        Vector3f color = new Vector3f((rgb >> 16 & 0xFF) / 255f, (rgb >> 8 & 0xFF) / 255f, (rgb & 0xFF) / 255f);
        DustParticleOptions dust = new DustParticleOptions(color, entry.calm() ? 0.7f : 1.0f);
        double w = player.getBbWidth() * 0.5 + 0.2;
        for (int i = 0; i < count; i++) {
            double a = RANDOM.nextDouble() * Math.PI * 2;
            level.addParticle(dust, player.getX() + Math.cos(a) * w, player.getY() + RANDOM.nextDouble() * player.getBbHeight() * 1.1,
                    player.getZ() + Math.sin(a) * w, 0, 0.6, 0);
        }
    }

    // ---------------------------------------------------------------------------------------------- the body

    private static void render(Camera camera, float partial) {
        if (SHOWN.isEmpty() && WISPS.isEmpty()) return;
        // (the shimmer's glints are wisps too; Vanilla draws neither, only dust)
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || quality() == VfxQuality.VANILLA) return;
        Vec3 cam = camera.getPosition();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        PoseStack pose = new PoseStack();
        boolean simple = quality() == VfxQuality.REDUCED;
        for (Map.Entry<Integer, Entry> e : SHOWN.entrySet()) {
            Entry entry = e.getValue();
            if (!entry.aura() || !(level.getEntity(e.getKey()) instanceof Player player) || !player.isAlive() || on(player) == null) continue;
            if (firstPerson(mc, player)) continue;
            body(pose, buffers, entry, player, cam, partial, simple);
        }
        if (!WISPS.isEmpty()) {
            Vector3f right = new Vector3f(camera.getLeftVector()).negate(), up = new Vector3f(camera.getUpVector());
            for (boolean glints : new boolean[] {false, true}) {
                VertexConsumer out = buffers.getBuffer(AuraRenderTypes.SMOOTH.apply(glints ? SHIMMER : WISP));
                for (Wisp w : WISPS) {
                    if (w.glint != glints) continue;
                    float t = (w.age + partial) / w.life;
                    float alpha = w.glint ? w.alpha * Mth.sin((float) Math.PI * t) // a glint comes and goes
                            : w.alpha * (t < 0.15f ? t / 0.15f : 1f - (t - 0.15f) / 0.85f);
                    float size = w.glint ? w.size : w.size * (1f - 0.45f * t);
                    Vec3 at = w.prev.lerp(w.at, partial).subtract(cam);
                    quad(out, pose.last().pose(), at, right, up, size, w.glint ? size : size * 2f, 0f, 1f, w.rgb, alpha);
                }
            }
        }
        buffers.endBatch(AuraRenderTypes.SMOOTH.apply(SHEET));
        buffers.endBatch(AuraRenderTypes.SMOOTH.apply(SHEET_SIMPLE));
        buffers.endBatch(AuraRenderTypes.SMOOTH.apply(WISP));
        buffers.endBatch(AuraRenderTypes.SMOOTH.apply(SHIMMER));
    }

    /** The card behind the body, facing the camera round the vertical, the feet at the player's feet. */
    private static void body(PoseStack pose, MultiBufferSource buffers, Entry entry, Player player, Vec3 cam, float partial, boolean simple) {
        float fight = Mth.lerp(partial, entry.prevFight, entry.fight);
        boolean calm = entry.calm();
        float s = scale(player) * (1f + (calm ? CALMER : FULLER * fight));
        float alpha = calm ? ALPHA_CALM : Mth.lerp(fight, ALPHA, ALPHA_FIGHT);
        Vec3 feet = player.getPosition(partial);
        Vec3 away = new Vec3(feet.x - cam.x, 0, feet.z - cam.z);
        Vec3 dir = away.lengthSqr() < 1.0e-4 ? Vec3.directionFromRotation(0, player.getViewYRot(partial)) : away.normalize();
        Vec3 centre = feet.add(dir.scale(BEHIND * scale(player))).subtract(cam);
        Vector3f right = new Vector3f((float) -dir.z, 0f, (float) dir.x), up = new Vector3f(0f, 1f, 0f);
        int frame = Math.floorMod((int) Mth.lerp(partial, entry.prevPhase, entry.phase), FRAMES);
        float u0 = frame / (float) FRAMES, u1 = (frame + 1) / (float) FRAMES;
        Vec3 mid = centre.add(0, (SHEET_H / 2f - FEET) * s, 0);
        int rgb = MeleeTell.bright(entry.payload.color()) & 0xFFFFFF;
        VertexConsumer out = buffers.getBuffer(AuraRenderTypes.SMOOTH.apply(simple || calm ? SHEET_SIMPLE : SHEET));
        quad(out, pose.last().pose(), mid, right, up, SHEET_W * s / 2f, SHEET_H * s / 2f, u0, u1, rgb, alpha);
    }

    /** A quad centred at {@code at}, {@code halfW} along {@code right} and {@code halfH} along {@code up}, the texture's u from u0 to u1. */
    private static void quad(VertexConsumer out, Matrix4f pose, Vec3 at, Vector3f right, Vector3f up, float halfW, float halfH, float u0, float u1,
                             int rgb, float alpha) {
        float x = (float) at.x, y = (float) at.y, z = (float) at.z;
        vertex(out, pose, x - right.x * halfW - up.x * halfH, y - right.y * halfW - up.y * halfH, z - right.z * halfW - up.z * halfH, u0, 1f, rgb, alpha);
        vertex(out, pose, x + right.x * halfW - up.x * halfH, y + right.y * halfW - up.y * halfH, z + right.z * halfW - up.z * halfH, u1, 1f, rgb, alpha);
        vertex(out, pose, x + right.x * halfW + up.x * halfH, y + right.y * halfW + up.y * halfH, z + right.z * halfW + up.z * halfH, u1, 0f, rgb, alpha);
        vertex(out, pose, x - right.x * halfW + up.x * halfH, y - right.y * halfW + up.y * halfH, z - right.z * halfW + up.z * halfH, u0, 0f, rgb, alpha);
    }

    private static void vertex(VertexConsumer out, Matrix4f pose, float x, float y, float z, float u, float v, int rgb, float alpha) {
        out.addVertex(pose, x, y, z).setColor(rgb >> 16 & 0xFF, rgb >> 8 & 0xFF, rgb & 0xFF, Mth.clamp((int) (alpha * 255), 0, 255)).setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(0f, 1f, 0f);
    }

    // ---------------------------------------------------------------------------------------------- your own first person

    /** A faint flow up both edges of the view, a little wider low on the right where your arm is; the middle 60% stays clear. */
    private static void renderEdges(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || !firstPerson(mc, mc.player) || quality() == VfxQuality.VANILLA) return;
        Entry entry = SHOWN.get(mc.player.getId());
        if (entry == null || !entry.aura() || on(mc.player) == null) return;
        float partial = delta.getGameTimeDeltaPartialTick(false);
        int frame = Math.floorMod((int) Mth.lerp(partial, entry.prevPhase, entry.phase), FRAMES);
        int w = g.guiWidth(), h = g.guiHeight();
        int rgb = MeleeTell.bright(entry.payload.color()) & 0xFFFFFF;
        float alpha = entry.calm() ? EDGE_ALPHA_CALM : EDGE_ALPHA * (1f + 0.3f * entry.fight);
        mc.getTextureManager().getTexture(EDGE).setFilter(true, false);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor((rgb >> 16 & 0xFF) / 255f, (rgb >> 8 & 0xFF) / 255f, (rgb & 0xFF) / 255f, alpha);
        int edge = Math.round(w * EDGE_SHARE), arm = Math.round(w * EDGE_ARM_SHARE);
        edgeStrip(g, frame, 0, 0, edge, h, false, 1f, 1f);
        edgeStrip(g, frame, w - edge, 0, edge, h, true, 1f, 1f);
        // round your arm: low on the right, fading in from nothing at its top
        edgeStrip(g, (frame + FRAMES / 2) % FRAMES, w - arm, h * 2 / 5, arm, h - h * 2 / 5, true, 0f, 0.7f);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        RenderSystem.disableBlend();
    }

    /** One strip of the edge flow: the texture's screen edge at the left, or mirrored to the right. */
    private static void edgeStrip(GuiGraphics g, int frame, int x, int y, int width, int height, boolean mirrored, float topAlpha, float bottomAlpha) {
        float u0 = frame / (float) FRAMES, u1 = (frame + 1) / (float) FRAMES;
        if (mirrored) {
            float t = u0;
            u0 = u1;
            u1 = t;
        }
        RenderSystem.setShader(net.minecraft.client.renderer.GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, EDGE);
        Matrix4f m = g.pose().last().pose();
        com.mojang.blaze3d.vertex.BufferBuilder b = com.mojang.blaze3d.vertex.Tesselator.getInstance().begin(
                com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS, com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_TEX_COLOR);
        b.addVertex(m, x, y, 0).setUv(u0, 0f).setColor(1f, 1f, 1f, topAlpha);
        b.addVertex(m, x, y + height, 0).setUv(u0, 1f).setColor(1f, 1f, 1f, bottomAlpha);
        b.addVertex(m, x + width, y + height, 0).setUv(u1, 1f).setColor(1f, 1f, 1f, bottomAlpha);
        b.addVertex(m, x + width, y, 0).setUv(u1, 0f).setColor(1f, 1f, 1f, topAlpha);
        com.mojang.blaze3d.vertex.BufferUploader.drawWithShader(b.buildOrThrow());
    }
}

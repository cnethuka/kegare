package com.cnethuka.kegare.severed.client;

import com.cnethuka.kegare.Kegare;
import com.cnethuka.kegare.severed.SensesPayload;
import com.cnethuka.kegare.severed.SeveredConfig;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.HashSet;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * What a Severed player senses, drawn (DESIGN §3.27): every body the server says they sense gets a faint, pale outline, through
 * walls, fading with distance. Each body's silhouette is drawn into a mask (its brightness how near it is), then
 * {@code shaders/post/senses.json} lays a two-pixel outline just outside each silhouette over the picture. Invisible bodies are
 * drawn into the mask as if they weren't. No names, no labels, no colors, no glow; at every effects setting, because it's what
 * you play by.
 */
public final class SensesClient {
    private static final ResourceLocation CHAIN = Kegare.id("shaders/post/senses.json");
    /** How bright the farthest outline is, against the nearest. */
    private static final float FAR = 0.3f;
    private static final Set<Integer> SENSED = new HashSet<>();
    /** Real positions of what's sensed but seen behind where it is (DESIGN §3.30): the last two the server sent, for smooth drawing. */
    private static final java.util.Map<Integer, Vec3[]> REAL = new java.util.HashMap<>();
    @Nullable private static PostChain chain;
    private static boolean broken;
    private static int width, height;

    private SensesClient() {}

    public static void init() {
        SensesPayload.setClientHandler(p -> {
            SENSED.clear();
            SENSED.addAll(p.ids());
        });
        com.cnethuka.kegare.severed.SensedPositionsPayload.setClientHandler(p -> {
            java.util.Map<Integer, Vec3[]> next = new java.util.HashMap<>();
            for (var e : p.entries()) {
                Vec3[] was = REAL.get(e.id());
                next.put(e.id(), new Vec3[] {was == null ? e.pos() : was[1], e.pos()});
            }
            REAL.clear();
            REAL.putAll(next);
        });
        NeoForge.EVENT_BUS.addListener(SensesClient::onRender);
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut e) -> {
            SENSED.clear();
            REAL.clear();
        });
    }

    /** Who's sensed right now (for the tests and the HUD). */
    public static Set<Integer> sensed() {
        return SENSED;
    }

    /** Resource packs changed: the chain loads again. */
    public static void reload() {
        if (chain != null) chain.close();
        chain = null;
        broken = false;
    }

    private static void onRender(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_WEATHER || SENSED.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.player == null) return;
        PostChain post = chain(mc);
        if (post == null) return;
        RenderTarget mask = post.getTempTarget("mask");
        if (mask == null) return;
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        Vec3 cam = event.getCamera().getPosition();
        double range = Math.max(1.0, SeveredConfig.SENSES_RANGE.get());

        BufferBuilder builder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        Silhouette silhouette = new Silhouette(builder);
        EntityRenderDispatcher dispatcher = mc.getEntityRenderDispatcher();
        dispatcher.setRenderShadow(false);
        try {
            for (int id : SENSED) {
                Entity e = level.getEntity(id);
                if (e == null || e == mc.player || !e.isAlive()) continue;
                double d = e.position().distanceTo(mc.player.position());
                silhouette.shade = Mth.clamp((float) (1.0 - (1.0 - FAR) * d / range), FAR, 1f);
                Vec3[] real = REAL.get(id); // sensing isn't seeing: where they really are, not where eyes see them
                Vec3 at = (real != null ? real[0].lerp(real[1], partial) : e.getPosition(partial)).subtract(cam);
                boolean invisible = e.isInvisible();
                if (invisible) e.setInvisible(false); // sensed, not seen: an invisible body is there all the same
                try {
                    com.cnethuka.kegare.client.visual.BodyCopies.draw(() -> dispatcher.render(e, at.x, at.y, at.z, e.getViewYRot(partial), partial,
                            new PoseStack(), silhouette, LightTexture.FULL_BRIGHT));
                } catch (RuntimeException ignored) {
                    // An odd renderer: no outline for it, rather than a crash.
                } finally {
                    if (invisible) e.setInvisible(true);
                }
            }
        } finally {
            dispatcher.setRenderShadow(true);
        }
        MeshData mesh = builder.build();
        mask.setClearColor(0f, 0f, 0f, 0f);
        mask.clear(Minecraft.ON_OSX);
        if (mesh == null) {
            mc.getMainRenderTarget().bindWrite(false);
            return;
        }
        mask.bindWrite(false);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.disableDepthTest();
        RenderSystem.disableCull();
        RenderSystem.disableBlend();
        BufferUploader.drawWithShader(mesh);
        RenderSystem.enableCull();
        RenderSystem.enableDepthTest();
        mc.getMainRenderTarget().bindWrite(false);
        post.process(partial);
        mc.getMainRenderTarget().bindWrite(false);
    }

    @Nullable
    private static PostChain chain(Minecraft mc) {
        if (broken) return null;
        RenderTarget main = mc.getMainRenderTarget();
        if (chain == null) {
            try {
                chain = new PostChain(mc.getTextureManager(), mc.getResourceManager(), main, CHAIN);
            } catch (Exception | LinkageError e) {
                broken = true;
                Kegare.LOGGER.warn("Senses outline can't load ({}); not drawn", e.toString());
                return null;
            }
            width = height = -1;
        }
        if (width != main.width || height != main.height) {
            width = main.width;
            height = main.height;
            chain.resize(width, height);
        }
        return chain;
    }

    /** Every quad a body's renderer draws, in one shade, into the mask; its name, shadow, glint and lines left out. */
    private static final class Silhouette implements MultiBufferSource {
        private final BufferBuilder out;
        float shade;
        private final VertexConsumer writer = new VertexConsumer() {
            @Override
            public VertexConsumer addVertex(float x, float y, float z) {
                out.addVertex(x, y, z).setColor(shade, shade, shade, 1f);
                return this;
            }

            @Override public VertexConsumer setColor(int r, int g, int b, int a) { return this; }
            @Override public VertexConsumer setUv(float u, float v) { return this; }
            @Override public VertexConsumer setUv1(int u, int v) { return this; }
            @Override public VertexConsumer setUv2(int u, int v) { return this; }
            @Override public VertexConsumer setNormal(float x, float y, float z) { return this; }
        };
        private static final VertexConsumer NOTHING = new VertexConsumer() {
            @Override public VertexConsumer addVertex(float x, float y, float z) { return this; }
            @Override public VertexConsumer setColor(int r, int g, int b, int a) { return this; }
            @Override public VertexConsumer setUv(float u, float v) { return this; }
            @Override public VertexConsumer setUv1(int u, int v) { return this; }
            @Override public VertexConsumer setUv2(int u, int v) { return this; }
            @Override public VertexConsumer setNormal(float x, float y, float z) { return this; }
        };

        Silhouette(BufferBuilder out) {
            this.out = out;
        }

        @Override
        public VertexConsumer getBuffer(RenderType type) {
            if (type.mode() != VertexFormat.Mode.QUADS) return NOTHING;
            String s = type.toString();
            String name = s.startsWith("RenderType[") ? s.substring(11, Math.max(11, s.indexOf(':'))) : s;
            if (name.startsWith("text") || name.contains("glint") || name.contains("shadow") || name.contains("outline")) return NOTHING;
            return writer;
        }
    }
}

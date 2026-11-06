package com.cnethuka.kegare.innate_technique.puppet_manipulation.client;

import com.cnethuka.kegare.Kegare;
import com.cnethuka.kegare.client.visual.ClientVisuals;
import com.cnethuka.kegare.innate_technique.puppet_manipulation.PuppetIds;
import com.cnethuka.kegare.innate_technique.puppet_manipulation.Strings;
import com.cnethuka.kegare.innate_technique.puppet_manipulation.StringsSync;
import com.cnethuka.kegare.innatetechnique.visual.Visual;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.common.NeoForge;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Draws every string in the dimension, for everyone: the string is the tell. The models are Blockbench's
 * ({@code assets/kegare/models/string/}; sources in {@code art/blockbench/}, see DESIGN §3.20). A string is the segment
 * laid end to end along a shallow curve, at its true length: a longer string has more segments, never stretched ones, so
 * the braid can't smear, and only the last, shortened under its knot, changes length. Knots sit where it grips.
 */
public final class StringsClient {
    private static final ModelResourceLocation SEGMENT = ModelResourceLocation.standalone(Kegare.id("string/segment"));
    private static final ModelResourceLocation KNOT = ModelResourceLocation.standalone(Kegare.id("string/knot"));
    /** The segment model's length (8 model units), in blocks. */
    private static final double SEGMENT_LENGTH = 0.5;
    /** Each segment reaches this much past the next one's start, so a bend never opens a gap between them. */
    private static final float OVERLAP = 1.03f;
    private static final int MAX_SEGMENTS = 400;
    private static final float KNOT_SCALE = 0.75f;
    /** A knot this close to the camera (blocks) isn't drawn: your own hand or chest, in first person. */
    private static final double KNOT_HIDDEN_WITHIN = 1.6;
    /** The cord is drawn thinner within this many blocks of the camera, down to this fraction of its size. */
    private static final double THIN_WITHIN = 5.0;
    private static final double THIN_MOST = 0.22;
    /** How long (ticks) a new string takes to shoot out to what it grips. */
    private static final float REVEAL_TICKS = 3f;
    /** A slack string's sag: this share of its length, up to the most it ever hangs. */
    private static final double SAG_SHARE = 0.07;
    private static final double SAG_MOST = 1.3;
    /** How fast (per second) a string goes from slack to taut and back. */
    private static final float SLACK_RATE = 14f;
    private static final int SHIMMER_EVERY_TICKS = 2;

    /** A string this client knows about. */
    private static final class Shown {
        StringsSync.Entry entry;
        /** When its newest part appeared, in game time: it shoots out from there. */
        long born;
        /** 1 slack, 0 taut, eased so a string snaps straight rather than popping. */
        float slack = 1f;
        long lastFrameNanos = System.nanoTime();

        Shown(StringsSync.Entry entry, long now) {
            this.entry = entry;
            this.born = now;
        }
    }

    private static final Map<Integer, Shown> SHOWN = new HashMap<>();
    @Nullable
    private static List<BakedQuad> segmentQuads;
    @Nullable
    private static List<BakedQuad> knotQuads;

    private StringsClient() {}

    static void init(IEventBus modBus) {
        modBus.addListener(StringsClient::registerModels);
        modBus.addListener((ModelEvent.BakingCompleted e) -> {
            segmentQuads = null;
            knotQuads = null;
        });
        StringsSync.Payload.setClientHandler(StringsClient::update);
        NeoForge.EVENT_BUS.addListener(StringsClient::onRender);
        NeoForge.EVENT_BUS.addListener(StringsClient::onClientTick);
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut e) -> SHOWN.clear());
    }

    private static void registerModels(ModelEvent.RegisterAdditional event) {
        event.register(SEGMENT);
        event.register(KNOT);
    }

    private static void update(StringsSync.Payload payload) {
        ClientLevel level = Minecraft.getInstance().level;
        long now = level == null ? 0 : level.getGameTime();
        Map<Integer, Shown> before = new HashMap<>(SHOWN);
        SHOWN.clear();
        for (StringsSync.Entry e : payload.entries()) {
            Shown shown = before.get(e.caster());
            if (shown == null) {
                shown = new Shown(e, now);
            } else {
                if (shown.entry.second() != e.second() || shown.entry.first() != e.first()) shown.born = now; // a Tie or Leash's new part
                shown.entry = e;
            }
            SHOWN.put(e.caster(), shown);
        }
    }

    // ---------------------------------------------------------------- what runs where

    /** One stretch of string, end to end. */
    private record Span(Vec3 from, Vec3 to, boolean grow) {}

    private static List<Span> spans(ClientLevel level, StringsSync.Entry e, float partial) {
        List<Span> out = new ArrayList<>(2);
        Entity caster = level.getEntity(e.caster());
        Entity first = level.getEntity(e.first());
        Entity second = e.second() < 0 ? null : level.getEntity(e.second());
        if (first == null) return out;
        if (e.phase() == Strings.Phase.TIE_PULL || e.phase() == Strings.Phase.TETHER) { // one string, between the two
            if (second != null) out.add(between(first, second, partial));
            return out;
        }
        if (!(caster instanceof Player player)) return out;
        out.add(fromHand(player, first, partial, e.phase() != Strings.Phase.LEASH));
        if (second != null) out.add(fromHand(player, second, partial, true));
        return out;
    }

    private static Span between(Entity a, Entity b, float partial) {
        Vec3 ca = chest(a, partial);
        Vec3 cb = chest(b, partial);
        return new Span(surface(a, ca, cb), surface(b, cb, ca), true);
    }

    private static Span fromHand(Player caster, Entity target, float partial, boolean grow) {
        Vec3 hand = hand(caster, partial);
        Vec3 c = chest(target, partial);
        return new Span(hand, surface(target, c, hand), grow);
    }

    private static Vec3 chest(Entity e, float partial) {
        return e.getPosition(partial).add(0, e.getBbHeight() * 0.62, 0);
    }

    /** On the outside of {@code e}, on the side facing {@code toward}. */
    private static Vec3 surface(Entity e, Vec3 chest, Vec3 toward) {
        double dx = toward.x - chest.x;
        double dz = toward.z - chest.z;
        double length = Math.sqrt(dx * dx + dz * dz);
        if (length < 1.0e-3) return chest;
        double r = e.getBbWidth() * 0.5 / length;
        return chest.add(dx * r, 0, dz * r);
    }

    /** The string hand: the right one. In your own first person it comes from the lower right of the view, like a fishing line. */
    private static Vec3 hand(Player player, float partial) {
        Minecraft mc = Minecraft.getInstance();
        Camera camera = mc.gameRenderer.getMainCamera();
        if (player == mc.player && !camera.isDetached()) {
            Vec3 look = new Vec3(camera.getLookVector());
            Vec3 up = new Vec3(camera.getUpVector());
            Vec3 left = new Vec3(camera.getLeftVector());
            return camera.getPosition().add(look.scale(0.9)).subtract(left.scale(0.7)).subtract(up.scale(0.42));
        }
        float yaw = Mth.rotLerp(partial, player.yBodyRotO, player.yBodyRot) * Mth.DEG_TO_RAD;
        Vec3 forward = new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));
        Vec3 right = new Vec3(-Mth.cos(yaw), 0, -Mth.sin(yaw));
        double scale = player.getScale();
        return player.getPosition(partial).add(0, 1.0 * scale, 0).add(right.scale(0.38 * scale)).add(forward.scale(0.28 * scale));
    }

    // ---------------------------------------------------------------- drawing

    private static void onRender(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES || SHOWN.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) return;
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        Vec3 camera = event.getCamera().getPosition();
        PoseStack pose = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        RenderType type = RenderType.entityTranslucentEmissive(InventoryMenu.BLOCK_ATLAS);
        VertexConsumer out = buffers.getBuffer(type);
        List<BakedQuad> segment = segment(mc);
        List<BakedQuad> knot = knot(mc);
        float age = level.getGameTime() + partial;
        for (Shown shown : SHOWN.values()) {
            float reveal = Mth.clamp((age - shown.born) / REVEAL_TICKS, 0f, 1f);
            boolean pulling = shown.entry.limit() <= 0f;
            long nanos = System.nanoTime();
            float seconds = Math.min(0.1f, (nanos - shown.lastFrameNanos) / 1.0e9f);
            shown.lastFrameNanos = nanos;
            shown.slack += ((pulling ? 0f : 1f) - shown.slack) * Math.min(1f, seconds * SLACK_RATE);
            for (Span span : spans(level, shown.entry, partial)) {
                draw(pose, out, segment, knot, span.from().subtract(camera), span.to().subtract(camera), sag(span, shown), span.grow() ? reveal : 1f);
            }
        }
        buffers.endBatch(type);
    }

    /** Slack hangs; the nearer its limit the less it hangs, and at the limit (or pulling) it runs straight. */
    private static double sag(Span span, Shown shown) {
        double length = span.from().distanceTo(span.to());
        float limit = shown.entry.limit();
        double taut = limit <= 0f ? 1.0 : Mth.clamp(length / limit, 0.0, 1.0);
        return shown.slack * (1.0 - taut * taut) * Math.min(SAG_MOST, length * SAG_SHARE);
    }

    private static Vec3 along(Vec3 from, Vec3 to, double sag, double t) {
        return from.add(to.subtract(from).scale(t)).add(0, -sag * 4.0 * t * (1.0 - t), 0);
    }

    private static void draw(PoseStack pose, VertexConsumer out, List<BakedQuad> segment, List<BakedQuad> knot, Vec3 from, Vec3 to, double sag, float reveal) {
        double chord = from.distanceTo(to);
        if (chord < 1.0e-3) return;
        // A shallow curve's length, closely enough: the segments are spaced by it, so they stay their true size.
        double ratio = sag / chord;
        double arc = chord * (1.0 + 8.0 / 3.0 * ratio * ratio);
        double step = SEGMENT_LENGTH / arc;
        int count = Math.min(MAX_SEGMENTS, (int) Math.ceil(reveal / step));
        Vec3 start = from;
        for (int i = 0; i < count; i++) {
            double t = Math.min(reveal, (i + 1) * step);
            Vec3 end = along(from, to, sag, t);
            Vec3 d = end.subtract(start);
            double length = d.length();
            if (length > 1.0e-4) {
                pose.pushPose();
                pose.translate(start.x, start.y, start.z);
                pose.mulPose(aim(d));
                float thin = thickness(start.add(end).scale(0.5).length());
                pose.scale(thin, thin, (float) (length / SEGMENT_LENGTH * OVERLAP));
                pose.translate(-0.5f, -0.5f, 0f);
                emit(pose, out, segment);
                pose.popPose();
            }
            start = end;
        }
        // Each knot's hoop points away from the string, into what it grips.
        knot(pose, out, knot, along(from, to, sag, reveal), along(from, to, sag, reveal).subtract(along(from, to, sag, Math.max(0.0, reveal - 0.02))));
        knot(pose, out, knot, from, from.subtract(along(from, to, sag, Math.min(1.0, 0.02))));
    }

    /**
     * How thick the cord is drawn at this distance from the camera: thinner up close, or the end at your own hand or chest (the
     * string starting right under your eyes) would fill the view. From a few blocks away it's its true size.
     */
    private static float thickness(double distance) {
        return (float) Mth.clamp(distance / THIN_WITHIN, THIN_MOST, 1.0);
    }

    /** {@code at} is relative to the camera. */
    private static void knot(PoseStack pose, VertexConsumer out, List<BakedQuad> quads, Vec3 at, Vec3 direction) {
        if (direction.lengthSqr() < 1.0e-8 || at.lengthSqr() < KNOT_HIDDEN_WITHIN * KNOT_HIDDEN_WITHIN) return;
        pose.pushPose();
        pose.translate(at.x, at.y, at.z);
        pose.mulPose(aim(direction));
        pose.scale(KNOT_SCALE, KNOT_SCALE, KNOT_SCALE);
        pose.translate(-0.5f, -0.5f, -0.5f);
        emit(pose, out, quads);
        pose.popPose();
    }

    /** Turns the models' +Z along {@code d}. */
    private static Quaternionf aim(Vec3 d) {
        Vector3f to = new Vector3f((float) d.x, (float) d.y, (float) d.z).normalize();
        return new Quaternionf().rotationTo(new Vector3f(0f, 0f, 1f), to);
    }

    private static void emit(PoseStack pose, VertexConsumer out, List<BakedQuad> quads) {
        for (BakedQuad quad : quads) {
            out.putBulkData(pose.last(), quad, 1f, 1f, 1f, 1f, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
        }
    }

    private static List<BakedQuad> segment(Minecraft mc) {
        if (segmentQuads == null) segmentQuads = quads(mc.getModelManager().getModel(SEGMENT));
        return segmentQuads;
    }

    private static List<BakedQuad> knot(Minecraft mc) {
        if (knotQuads == null) knotQuads = quads(mc.getModelManager().getModel(KNOT));
        return knotQuads;
    }

    private static List<BakedQuad> quads(BakedModel model) {
        RandomSource random = RandomSource.create(42L);
        List<BakedQuad> out = new ArrayList<>(model.getQuads(null, null, random, ModelData.EMPTY, null));
        for (Direction side : Direction.values()) out.addAll(model.getQuads(null, side, random, ModelData.EMPTY, null));
        return out;
    }

    // ---------------------------------------------------------------- the tension shimmer

    private static void onClientTick(ClientTickEvent.Post event) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || SHOWN.isEmpty() || level.getGameTime() % SHIMMER_EVERY_TICKS != 0) return;
        Visual shimmer = Visual.effect(PuppetIds.FX_STRINGS_TENSION, Visual.Anchor.BODY);
        for (Shown shown : SHOWN.values()) {
            float limit = shown.entry.limit();
            for (Span span : spans(level, shown.entry, 1f)) {
                double length = span.from().distanceTo(span.to());
                // While pulling, or dragging something back at the end of a tether or leash; a waiting string is at rest.
                boolean tense = limit <= 0f || shown.entry.phase() != Strings.Phase.ATTACHED && length >= limit * 0.98;
                if (!tense) continue;
                int beads = Math.max(1, Math.min(4, (int) (length / 4.0)));
                for (int i = 0; i < beads; i++) {
                    ClientVisuals.playAt(level, along(span.from(), span.to(), 0.0, level.random.nextDouble()), shimmer);
                }
            }
        }
    }
}

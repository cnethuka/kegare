package com.cnethuka.kegare.innate_technique.puppet_manipulation.client;

import com.cnethuka.kegare.config.KegareClientConfig;
import com.cnethuka.kegare.client.visual.ClientVisuals;
import com.cnethuka.kegare.innate_technique.puppet_manipulation.PossessionLinks;
import com.cnethuka.kegare.innate_technique.puppet_manipulation.PuppetConfig;
import com.cnethuka.kegare.innate_technique.puppet_manipulation.PuppetEntity;
import com.cnethuka.kegare.innate_technique.puppet_manipulation.PuppetIds;
import com.cnethuka.kegare.innate_technique.puppet_manipulation.PuppetTier;
import com.cnethuka.kegare.innatetechnique.visual.Visual;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Puppet Manipulation's effects (DESIGN §3.20), drawn here and not by Photon: everything is thread. Fine lavender threads with
 * white highlights stitch a puppet together, unravel it, tug it, mark a target, burst out of it, pull two places together and
 * rise round a Stand-In; the possession thread and Strings' fibers too. Every piece is a crisp strip, full-bright, turned to
 * face the camera (or a flat ring, a small diamond, a small square), and gone within about a second, except the possession
 * thread, which lasts as long as the possession.
 * <p>
 * Reduced: the same, with fewer threads. Vanilla: small lavender specks in the same places ({@link #SPECK}), never the
 * explosion's puffs.
 */
public final class PuppetFx {
    static final ResourceLocation WHITE = ResourceLocation.withDefaultNamespace("textures/misc/white.png");
    /** Puppet Manipulation's lavender (#C4B5FD), its white highlight, a lighter and a deeper step (the braid's ramp, Strings). */
    static final int LAVENDER = 0xC4B5FD, PALE = 0xF5F3FF, LIGHT = 0xDDD6FE, DEEP = 0xA78BFA;
    /** Vanilla's stand-in: a small lavender speck (small dust also dies fast: its life scales with its size). */
    static final DustParticleOptions SPECK = new DustParticleOptions(new Vector3f(0.77f, 0.71f, 0.99f), 0.5f);
    static final DustParticleOptions PALE_SPECK = new DustParticleOptions(new Vector3f(0.96f, 0.95f, 1f), 0.45f);

    private static final float THREAD = 0.026f, HAIR = 0.012f;
    /** Strings' frayed fibers: a quarter of the cord's thickness (the model's cord is 0.0875 across). */
    private static final float FIBER = 0.022f;
    /** Nothing of an effect is drawn this close to your own first-person camera (it would sit in front of your eyes). */
    private static final double CLEAR_OF_CAMERA = 1.4;

    // ---------------------------------------------------------------- what's running

    /** A new puppet being stitched, from the ground up: a few quick threads for a Tap, a dense weave for a Full. */
    private record Form(int entityId, long born, int tier, int seed) {}
    /** Threads stitched round a body with no puppet to read (Possess and the return: the body changing hands); half its height. */
    private record Stitch(Vec3 middle, long born, int seed, float half) {}
    /** A puppet unravelling: its threads come loose, drift apart and vanish. */
    private record Unravel(Vec3 middle, long born, float width, float height, int seed) {}
    /** Directive heard: the threads drop onto the puppet from a control bar above and tug it. */
    private record Tug(int entityId, long born) {}
    /** Focus: a ring tightening round the target and a bead over its head, 2 s. */
    private record Mark(int entityId, long born) {}
    /** Curio Burst: a flash, then threads and scraps flung out no further than the blast reaches, and a ring on the ground at it. */
    private record Burst(Vec3 centre, double ground, long born, int tier, float radius, int seed) {}
    /** Understudy, at one of the two places: threads round it yanked toward the other place, and the line between them. */
    private static final class Pull {
        final Vec3 at;
        final long born;
        final int seed;
        @Nullable Vec3 other;
        boolean drawsLine;

        Pull(Vec3 at, long born, int seed) {
            this.at = at;
            this.born = born;
            this.seed = seed;
        }
    }
    /** Stand-In growing (or shrinking back) inside a rising column of threads. */
    private record Column(Vec3 feet, long born, int growTicks, int seed) {}
    /** Strings: fibers where a knot grips (and a pinch of light), or frayed fibers flung where a string gives way. */
    private record Fibers(Vec3 at, long born, boolean snap, int seed) {}

    private static final List<Form> FORMS = new ArrayList<>();
    private static final List<Stitch> STITCHES = new ArrayList<>();
    private static final List<Unravel> UNRAVELS = new ArrayList<>();
    private static final Map<Integer, Tug> TUGS = new HashMap<>();
    private static final Map<Integer, Mark> MARKS = new HashMap<>();
    private static final List<Burst> BURSTS = new ArrayList<>();
    private static final List<Pull> PULLS = new ArrayList<>();
    private static final List<Column> COLUMNS = new ArrayList<>();
    private static final List<Fibers> FIBERS = new ArrayList<>();

    /** How long each runs, in ticks (Form by tier). */
    private static final int[] FORM_STITCH = {5, 8, 12};
    private static final int[] FORM_THREADS = {4, 8, 16}, FORM_THREADS_REDUCED = {3, 5, 9};
    private static final float FORM_DRAW = 2.5f, FORM_FADE = 3f;
    private static final int STITCH_TICKS = 8, UNRAVEL_TICKS = 14, TUG_TICKS = 9, MARK_TICKS = 40, BURST_TICKS = 12, PULL_TICKS = 9,
            COLUMN_FADE = 5, FIBER_TICKS = 6, SNAP_TICKS = 12;

    private PuppetFx() {}

    static void init(IEventBus modBus) {
        for (ResourceLocation effect : List.of(PuppetIds.FX_PUPPET_FORM, PuppetIds.FX_PUPPET_DISSOLVE, PuppetIds.FX_POSSESS_LINK,
                PuppetIds.FX_DIRECTIVE_PULSE, PuppetIds.FX_FOCUS_MARK, PuppetIds.FX_CURIO_BURST_EXPLOSION_TAP,
                PuppetIds.FX_CURIO_BURST_EXPLOSION_HALF, PuppetIds.FX_CURIO_BURST_EXPLOSION_FULL, PuppetIds.FX_STAND_IN_GROW,
                PuppetIds.FX_UNDERSTUDY_SWAP, PuppetIds.FX_STRINGS_ATTACH, PuppetIds.FX_STRINGS_SNAP, PuppetIds.FX_STRINGS_TENSION)) {
            ClientVisuals.drawnByMove(effect);
        }
        ClientVisuals.listen(new ClientVisuals.Listener() {
            @Override
            public void playedOnce(Entity entity, Visual visual) {
                visual.effect().ifPresent(effect -> onEntity(entity, effect));
            }
        });
        ClientVisuals.onPlayAt(PuppetIds.FX_PUPPET_FORM, (level, pos, id, v) -> stitch(level, pos));
        ClientVisuals.onPlayAt(PuppetIds.FX_PUPPET_DISSOLVE, PuppetFx::unravel);
        ClientVisuals.onPlayAt(PuppetIds.FX_CURIO_BURST_EXPLOSION_TAP, (level, pos, id, v) -> burst(level, pos, PuppetTier.TAP));
        ClientVisuals.onPlayAt(PuppetIds.FX_CURIO_BURST_EXPLOSION_HALF, (level, pos, id, v) -> burst(level, pos, PuppetTier.HALF));
        ClientVisuals.onPlayAt(PuppetIds.FX_CURIO_BURST_EXPLOSION_FULL, (level, pos, id, v) -> burst(level, pos, PuppetTier.FULL));
        ClientVisuals.onPlayAt(PuppetIds.FX_UNDERSTUDY_SWAP, (level, pos, id, v) -> pull(level, pos));
        ClientVisuals.onPlayAt(PuppetIds.FX_STAND_IN_GROW, (level, pos, id, v) -> column(level, pos));
        ClientVisuals.onPlayAt(PuppetIds.FX_STRINGS_ATTACH, (level, pos, id, v) -> fibers(level, pos, false));
        ClientVisuals.onPlayAt(PuppetIds.FX_STRINGS_SNAP, (level, pos, id, v) -> fibers(level, pos, true));
        NeoForge.EVENT_BUS.addListener(PuppetFx::onRender);
        NeoForge.EVENT_BUS.addListener(PuppetFx::onClientTick);
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut e) -> clear());
    }

    static boolean drawn() {
        return KegareClientConfig.VFX_QUALITY.get() != KegareClientConfig.VfxQuality.VANILLA;
    }

    private static boolean reduced() {
        return KegareClientConfig.VFX_QUALITY.get() == KegareClientConfig.VfxQuality.REDUCED;
    }

    private static void clear() {
        FORMS.clear();
        STITCHES.clear();
        UNRAVELS.clear();
        TUGS.clear();
        MARKS.clear();
        BURSTS.clear();
        PULLS.clear();
        COLUMNS.clear();
        FIBERS.clear();
    }

    // ---------------------------------------------------------------- what the server says happened

    private static void onEntity(Entity entity, ResourceLocation effect) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || entity.level() != level) return;
        long now = level.getGameTime();
        if (effect.equals(PuppetIds.FX_PUPPET_FORM) && entity instanceof PuppetEntity puppet) {
            int tier = puppet.tier().ordinal();
            if (drawn()) {
                FORMS.add(new Form(entity.getId(), now, tier, entity.getId() * 31 + (int) now));
            } else {
                specksRound(level, entity.position(), entity.getBbWidth() * 0.6 + 0.04, entity.getBbHeight(), FORM_THREADS[tier] * 2, now);
            }
        } else if (effect.equals(PuppetIds.FX_DIRECTIVE_PULSE) && entity instanceof PuppetEntity) {
            // Only the puppets that heard react; the caster's cue is their own animation.
            TUGS.put(entity.getId(), new Tug(entity.getId(), now));
            if (!drawn()) {
                for (Vec3[] thread : tugThreads(entity, 0f, 1f)) speckLine(level, thread[0], thread[1], 0.2);
            }
        } else if (effect.equals(PuppetIds.FX_FOCUS_MARK)) {
            if (drawn()) {
                MARKS.put(entity.getId(), new Mark(entity.getId(), now));
            } else {
                specksRound(level, entity.position().add(0, entity.getBbHeight() * 0.45, 0), Math.max(0.6, entity.getBbWidth() * 1.2), 0, 12, now);
                level.addParticle(PALE_SPECK, entity.getX(), entity.getY() + entity.getBbHeight() + 0.3, entity.getZ(), 0, 0, 0);
            }
        }
    }

    private static boolean stitch(ClientLevel level, Vec3 middle) {
        if (nearCamera(middle, CLEAR_OF_CAMERA)) return true;
        // How tall the body is (a puppet's or a player's): it's played at its middle, so twice the way down to the ground.
        HitResult hit = level.clip(new ClipContext(middle, middle.subtract(0, 1.2, 0), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE,
                CollisionContext.empty()));
        float half = hit.getType() == HitResult.Type.MISS ? 0.45f : (float) Mth.clamp(middle.y - hit.getLocation().y, 0.3, 1.0);
        long now = level.getGameTime();
        if (drawn()) {
            STITCHES.add(new Stitch(middle, now, (int) (middle.x * 13 + middle.z * 7 + now), half));
        } else {
            specksRound(level, middle.subtract(0, half, 0), 0.04 + 0.4 * half, 2 * half, 8, now);
        }
        return true;
    }

    private static boolean unravel(ClientLevel level, Vec3 middle, int entityId, Visual visual) {
        Entity entity = entityId >= 0 ? level.getEntity(entityId) : null;
        float width = entity == null ? 0.3f : entity.getBbWidth(), height = entity == null ? 0.9f : entity.getBbHeight();
        long now = level.getGameTime();
        if (drawn()) {
            UNRAVELS.add(new Unravel(middle, now, width, height, entityId * 17 + (int) now));
        } else {
            specksRound(level, middle.subtract(0, height / 2, 0), width * 0.6 + 0.1, height, 14, now);
        }
        return true;
    }

    private static boolean burst(ClientLevel level, Vec3 centre, PuppetTier tier) {
        float radius = PuppetConfig.CURIO_RADIUS[tier.ordinal()].get().floatValue();
        // The ground under it: what it stood on, if that's close, else where a puppet's feet would be.
        HitResult hit = level.clip(new ClipContext(centre, centre.subtract(0, 1.5, 0), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE,
                CollisionContext.empty()));
        double ground = hit.getType() == HitResult.Type.MISS ? centre.y - 0.45 : hit.getLocation().y;
        long now = level.getGameTime();
        if (drawn()) {
            BURSTS.add(new Burst(centre, ground, now, tier.ordinal(), radius, (int) (centre.x * 31 + centre.z * 17 + now)));
        } else {
            for (int i = 0; i < 3; i++) level.addParticle(PALE_SPECK, centre.x, centre.y, centre.z, 0, 0, 0);
            specksRound(level, new Vec3(centre.x, ground + 0.1, centre.z), radius - 0.1, 0, 20 + 6 * tier.ordinal(), now);
            RandomSource random = RandomSource.create((long) (centre.x * 31 + centre.z * 17 + now));
            for (int i = 0; i < 8 + 4 * tier.ordinal(); i++) {
                double a = random.nextDouble() * Mth.TWO_PI, d = radius * (0.2 + 0.7 * random.nextDouble());
                level.addParticle(SPECK, centre.x + Math.cos(a) * d, centre.y + random.nextDouble() * 0.5 - 0.2, centre.z + Math.sin(a) * d, 0, 0, 0);
            }
        }
        return true;
    }

    /** Understudy's two places arrive one after the other in the same tick: the second is paired with the first. */
    private static boolean pull(ClientLevel level, Vec3 at) {
        long now = level.getGameTime();
        Pull pull = new Pull(at, now, (int) (at.x * 19 + at.z * 23 + now));
        for (Pull p : PULLS) {
            if (p.born == now && p.other == null && p.at.distanceTo(at) < 48 && p.at.distanceTo(at) > 0.1) {
                p.other = at;
                pull.other = p.at;
                p.drawsLine = true;
                break;
            }
        }
        PULLS.add(pull); // at Vanilla it's kept a tick for the pairing, and its specks go out in the client tick
        return true;
    }

    private static boolean column(ClientLevel level, Vec3 middle) {
        Vec3 feet = middle.subtract(0, 0.9, 0); // played 0.9 above the puppet's feet
        long now = level.getGameTime();
        int grow = Math.max(1, Math.round(PuppetConfig.STAND_IN_GROW.get().floatValue() * 20f));
        if (drawn()) {
            COLUMNS.add(new Column(feet, now, grow, (int) (feet.x * 7 + feet.z * 11 + now)));
        } else {
            specksRound(level, feet, 0.48, 2.0, 16, now);
        }
        return true;
    }

    private static boolean fibers(ClientLevel level, Vec3 at, boolean snap) {
        if (nearCamera(at, 1.2)) return true;
        long now = level.getGameTime();
        if (drawn()) {
            FIBERS.add(new Fibers(at, now, snap, (int) (at.x * 29 + at.y * 13 + at.z * 7 + now)));
        } else {
            RandomSource random = RandomSource.create((long) (at.x * 29 + at.z * 7 + now));
            for (int i = 0; i < (snap ? 6 : 4); i++) {
                level.addParticle(SPECK, at.x + random.nextGaussian() * 0.12, at.y + random.nextGaussian() * 0.12, at.z + random.nextGaussian() * 0.12, 0, 0, 0);
            }
            if (!snap) level.addParticle(PALE_SPECK, at.x, at.y, at.z, 0, 0, 0);
        }
        return true;
    }

    // ---------------------------------------------------------------- ticking (Vanilla specks, and dropping what's done)

    private static void onClientTick(ClientTickEvent.Post event) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            clear();
            return;
        }
        long now = level.getGameTime();
        FORMS.removeIf(f -> now - f.born() > FORM_STITCH[2] + FORM_DRAW + FORM_FADE + 2 || level.getEntity(f.entityId()) == null);
        STITCHES.removeIf(s -> now - s.born() > STITCH_TICKS + 1);
        UNRAVELS.removeIf(u -> now - u.born() > UNRAVEL_TICKS + 1);
        TUGS.values().removeIf(t -> now - t.born() > TUG_TICKS + 1);
        MARKS.values().removeIf(m -> now - m.born() > MARK_TICKS + 1 || level.getEntity(m.entityId()) == null);
        BURSTS.removeIf(b -> now - b.born() > BURST_TICKS + 1);
        COLUMNS.removeIf(c -> now - c.born() > c.growTicks() + COLUMN_FADE + 1);
        FIBERS.removeIf(f -> now - f.born() > (f.snap() ? SNAP_TICKS : FIBER_TICKS) + 1);
        for (Iterator<Pull> it = PULLS.iterator(); it.hasNext(); ) {
            Pull p = it.next();
            if (!drawn() && now > p.born) { // Vanilla: specks along the line and round both places, once paired (or not)
                specksRound(level, p.at.subtract(0, 0.9, 0), 0.4, 1.8, 8, now);
                if (p.drawsLine && p.other != null) speckLine(level, p.at, p.other, 0.8);
                it.remove();
            } else if (now - p.born > PULL_TICKS + 1) {
                it.remove();
            }
        }
    }

    // ---------------------------------------------------------------- drawing

    private static void onRender(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES || !drawn()) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) return;
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        List<Vec3[]> tense = StringsClient.tense(level, partial);
        boolean any = !FORMS.isEmpty() || !STITCHES.isEmpty() || !UNRAVELS.isEmpty() || !TUGS.isEmpty() || !MARKS.isEmpty()
                || !BURSTS.isEmpty() || !PULLS.isEmpty() || !COLUMNS.isEmpty() || !FIBERS.isEmpty() || !PossessionClient.links().isEmpty()
                || !tense.isEmpty();
        if (!any) return;
        Camera camera = event.getCamera();
        Draw d = new Draw(mc.renderBuffers().bufferSource(), event.getPoseStack(), camera, level.getGameTime() + partial);
        for (Form f : FORMS) drawForm(d, level, f, partial);
        for (Stitch s : STITCHES) drawStitch(d, s);
        for (Unravel u : UNRAVELS) drawUnravel(d, u);
        for (Tug t : TUGS.values()) drawTug(d, level, t, partial);
        for (Mark m : MARKS.values()) drawMark(d, level, m, partial);
        for (Burst b : BURSTS) drawBurst(d, b);
        for (Pull p : PULLS) drawPull(d, p);
        for (Column c : COLUMNS) drawColumn(d, c);
        for (Fibers f : FIBERS) drawFibers(d, f);
        drawPossession(d, level, partial);
        drawTension(d, tense);
        d.end();
    }

    // -- Puppet Creation

    /** While a Full puppet is still being woven its outline waits: the weave ends in it. */
    static boolean forming(PuppetEntity puppet) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return false;
        for (Form f : FORMS) {
            if (f.entityId() == puppet.getId()) return level.getGameTime() - f.born() < FORM_STITCH[f.tier()] + FORM_DRAW;
        }
        return false;
    }

    private static void drawForm(Draw d, ClientLevel level, Form f, float partial) {
        Entity entity = level.getEntity(f.entityId());
        if (entity == null) return;
        float t = d.now - f.born();
        int stitch = FORM_STITCH[f.tier()];
        float end = stitch + FORM_DRAW, alpha = 1f - Mth.clamp((t - end - 1f) / FORM_FADE, 0f, 1f);
        if (alpha <= 0f) return;
        Vec3 feet = entity.getPosition(partial);
        if (nearCamera(feet.add(0, entity.getBbHeight() / 2, 0), CLEAR_OF_CAMERA)) return;
        int n = (reduced() ? FORM_THREADS_REDUCED : FORM_THREADS)[f.tier()];
        double h = entity.getBbHeight(), r0 = entity.getBbWidth() * 0.6 + 0.04;
        RandomSource random = RandomSource.create(f.seed());
        for (int k = 0; k < n; k++) {
            double share = k / (double) n;
            float drawn = Mth.clamp((t - (float) share * stitch) / FORM_DRAW, 0f, 1f);
            if (drawn <= 0f) continue;
            boolean back = f.tier() == 2 && k % 2 == 1; // a Full's weave crosses itself
            double y0 = h * (0.02 + 0.84 * share), y1 = y0 + h * (back ? -0.1 : 0.2);
            double a0 = random.nextDouble() * Mth.TWO_PI, sweep = (back ? -1 : 1) * (3.2 + random.nextDouble() * 0.8);
            double r = r0 * (1.14 - 0.14 * drawn); // cinched as it's pulled tight
            List<Vec3> pts = helix(feet, r, a0, sweep, Math.max(0.01, y0), Math.min(h, Math.max(0.01, y1)), drawn, 10);
            d.polyline(pts, THREAD, LAVENDER, alpha);
            if (drawn < 1f) d.polyline(pts.subList(Math.max(0, pts.size() - 3), pts.size()), THREAD * 1.15f, PALE, alpha); // the needle
        }
    }

    private static void drawStitch(Draw d, Stitch s) {
        float t = d.now - s.born();
        float alpha = 1f - Mth.clamp((t - 5f) / 3f, 0f, 1f);
        if (alpha <= 0f) return;
        float half = s.half();
        Vec3 feet = s.middle().subtract(0, half, 0);
        RandomSource random = RandomSource.create(s.seed());
        int n = reduced() ? 3 : 5;
        for (int k = 0; k < n; k++) {
            double share = k / (double) n;
            float drawn = Mth.clamp((t - (float) share * 4f) / FORM_DRAW, 0f, 1f);
            if (drawn <= 0f) continue;
            double y0 = 2 * half * (0.05 + 0.8 * share);
            List<Vec3> pts = helix(feet, (0.04 + 0.4 * half) * (1.14 - 0.14 * drawn), random.nextDouble() * Mth.TWO_PI, 3.4, y0,
                    y0 + 0.4 * half, drawn, 10);
            d.polyline(pts, THREAD, LAVENDER, alpha);
            if (drawn < 1f) d.polyline(pts.subList(Math.max(0, pts.size() - 3), pts.size()), THREAD * 1.15f, PALE, alpha);
        }
    }

    // -- Dissolving

    private static void drawUnravel(Draw d, Unravel u) {
        float t = (d.now - u.born()) / UNRAVEL_TICKS;
        if (t > 1f) return;
        float ease = 1f - (1f - t) * (1f - t);
        float alpha = 1f - smooth(0.3f, 1f, t);
        Vec3 feet = u.middle().subtract(0, u.height() / 2, 0);
        RandomSource random = RandomSource.create(u.seed());
        int n = reduced() ? 5 : 8;
        double r0 = u.width() * 0.6 + 0.04;
        for (int k = 0; k < n; k++) {
            double share = k / (double) n;
            double a0 = random.nextDouble() * Mth.TWO_PI, sweep = (k % 2 == 0 ? 1 : -1) * (3.0 + random.nextDouble());
            double drift = random.nextDouble() * Mth.TWO_PI;
            Vec3 away = new Vec3(Math.cos(drift), 0.5 + random.nextDouble() * 0.5, Math.sin(drift)).scale(0.45 * ease);
            double y0 = u.height() * (0.05 + 0.82 * share);
            // Coming loose: a little wider, unwinding nearly straight, drifting off on its own way.
            List<Vec3> pts = helix(feet.add(away), r0 + 0.22 * ease, a0, sweep * (1.0 - 0.85 * ease), y0, y0 + u.height() * 0.18, 1.0, 10);
            d.polyline(pts, THREAD * (1f - 0.5f * t), t < 0.12f ? PALE : LAVENDER, alpha);
        }
    }

    // -- Directive

    /** How high a puppet jumps on its strings as it hears an order (PuppetRenderer lifts it by this). */
    public static float hop(PuppetEntity puppet, float partial) {
        Tug tug = TUGS.get(puppet.getId());
        ClientLevel level = Minecraft.getInstance().level;
        if (tug == null || level == null) return 0f;
        float t = level.getGameTime() + partial - tug.born();
        if (t < 1.5f || t > 5.5f) return 0f;
        return 0.13f * puppet.getBbHeight() / 0.9f * Mth.sin(Mth.PI * (t - 1.5f) / 4f);
    }

    /** The three strings of a tug, from the control bar above to the head and both hands: {top, bottom} pairs; plus the bar. */
    private static List<Vec3[]> tugThreads(Entity e, float partial, float drop) {
        Vec3 feet = e.getPosition(partial);
        double h = e.getBbHeight(), w = e.getBbWidth();
        float yaw = (e instanceof LivingEntity living ? Mth.rotLerp(partial, living.yBodyRotO, living.yBodyRot) : e.getYRot()) * Mth.DEG_TO_RAD;
        Vec3 right = new Vec3(-Mth.cos(yaw), 0, -Mth.sin(yaw));
        Vec3 bar = feet.add(0, h + 0.75, 0);
        Vec3[][] ends = {
                {bar, feet.add(0, h, 0)},
                {bar.add(right.scale(0.3)), feet.add(0, h * 0.6, 0).add(right.scale(w * 1.1))},
                {bar.subtract(right.scale(0.3)), feet.add(0, h * 0.6, 0).subtract(right.scale(w * 1.1))}};
        List<Vec3[]> out = new ArrayList<>(3);
        for (Vec3[] end : ends) out.add(new Vec3[] {end[0], end[0].add(end[1].subtract(end[0]).scale(drop))});
        return out;
    }

    private static void drawTug(Draw d, ClientLevel level, Tug tug, float partial) {
        Entity e = level.getEntity(tug.entityId());
        if (!(e instanceof PuppetEntity puppet) || puppet.isPossessed()) return;
        float t = d.now - tug.born();
        if (t > TUG_TICKS) return;
        float drop = Mth.clamp(t / 1.5f, 0f, 1f);
        float alpha = 1f - Mth.clamp((t - 5f) / 4f, 0f, 1f);
        float lift = hop(puppet, partial);
        Vec3 up = new Vec3(0, lift, 0);
        Vec3 feet = e.getPosition(partial);
        if (nearCamera(feet.add(0, e.getBbHeight(), 0), CLEAR_OF_CAMERA)) return;
        List<Vec3[]> threads = tugThreads(e, partial, drop);
        for (Vec3[] th : threads) d.strip(th[0].add(up), th[1].add(up), HAIR * 1.6f, LAVENDER, alpha);
        // The control bar: a short cross of pale thread the strings hang from.
        Vec3 left = threads.get(2)[0].add(up), right = threads.get(1)[0].add(up), mid = threads.get(0)[0].add(up);
        Vec3 across = right.subtract(left).normalize().cross(new Vec3(0, 1, 0)).scale(0.14);
        d.strip(left, right, THREAD * 1.2f, PALE, alpha);
        d.strip(mid.add(across), mid.subtract(across), THREAD * 1.2f, PALE, alpha);
    }

    // -- Focus

    private static void drawMark(Draw d, ClientLevel level, Mark mark, float partial) {
        Entity e = level.getEntity(mark.entityId());
        if (e == null) return;
        float t = d.now - mark.born();
        if (t > MARK_TICKS) return;
        float alpha = 1f - Mth.clamp((t - (MARK_TICKS - 6)) / 6f, 0f, 1f);
        Vec3 feet = e.getPosition(partial);
        double w = e.getBbWidth(), h = e.getBbHeight();
        float tight = Mth.clamp(t / 8f, 0f, 1f);
        float ease = 1f - (1f - tight) * (1f - tight) * (1f - tight);
        float from = (float) Math.max(1.1, w * 2.4), to = (float) (w * 0.72 + 0.14);
        float radius = Mth.lerp(ease, from, to);
        Vec3 waist = feet.add(0, h * 0.45, 0);
        d.loop(waist, radius, 0.045f, LAVENDER, alpha); // a loop of thread facing you: whole even seen nearly edge on
        d.loop(waist, radius, 0.014f, PALE, alpha);
        float turn = t * 0.035f;
        for (int k = 0; k < 4; k++) { // four short ticks across it, like a sight closing in
            double a = turn + k * Mth.HALF_PI + Mth.PI / 4;
            Vec3 dir = new Vec3(Math.cos(a), 0, Math.sin(a));
            d.flat(waist.add(dir.scale(radius + 0.02)), waist.add(dir.scale(radius + 0.16 + 0.12 * (1f - ease))), 0.03f, PALE, alpha);
        }
        Minecraft mc = Minecraft.getInstance();
        if (e == mc.getCameraEntity() && mc.options.getCameraType().isFirstPerson()) return; // the bead over your own head
        Vec3 bead = feet.add(0, h + 0.22 + 0.03 * Mth.sin(t * 0.25f), 0); // under a name tag, not on it
        d.diamond(bead, 0.115f, 0.082f, LAVENDER, alpha);
        d.diamond(bead, 0.075f, 0.05f, PALE, alpha);
    }

    // -- Curio Burst

    private static final float[] FLASH = {0.22f, 0.3f, 0.4f};
    /** A burst's pieces aren't drawn this close to the camera: one flying past your eyes is a streak across the view. */
    private static final double BURST_CLEAR = 2.5;
    private static final int[] BURST_THREADS = {10, 14, 18}, BURST_SCRAPS = {6, 8, 12};

    private static void drawBurst(Draw d, Burst b) {
        float t = d.now - b.born();
        if (t > BURST_TICKS) return;
        Vec3 c = b.centre();
        float reach = b.radius();
        boolean close = nearCamera(c, 2.5);
        if (t < 3f && !close) { // the flash: a sharp pale disc with a lavender rim, gone in a few frames
            float a = 1f - t / 3f;
            d.disc(c, FLASH[b.tier()], LIGHT, a);
            d.disc(c, FLASH[b.tier()] * 0.55f, PALE, a);
            d.circle(c, FLASH[b.tier()] * 1.3f, 0.05f, LAVENDER, a);
        }
        // The ring on the ground, out to exactly the blast's reach (its outer edge never past it).
        float grow = 1f - (1f - Mth.clamp(t / 5f, 0f, 1f)) * (1f - Mth.clamp(t / 5f, 0f, 1f));
        float ringWidth = 0.07f * (1f - 0.4f * t / BURST_TICKS);
        float ringAlpha = 1f - Mth.clamp((t - 4f) / 5f, 0f, 1f);
        if (ringAlpha > 0f) d.ring(new Vec3(c.x, b.ground() + 0.03, c.z), (reach - ringWidth / 2) * (0.25f + 0.75f * grow), ringWidth, LAVENDER, ringAlpha);
        RandomSource random = RandomSource.create(b.seed());
        float share = reduced() ? 0.6f : 1f;
        int threads = Math.round(BURST_THREADS[b.tier()] * share), scraps = Math.round(BURST_SCRAPS[b.tier()] * share);
        float fly = 1f - (1f - Mth.clamp(t / 7f, 0f, 1f)) * (1f - Mth.clamp(t / 7f, 0f, 1f));
        float threadAlpha = 1f - Mth.clamp((t - 6f) / 6f, 0f, 1f);
        for (int i = 0; i < threads; i++) { // torn threads, flung out, curling as they go
            Vec3 dir = flung(random, -0.15, 0.7);
            double length = (0.22 + 0.18 * random.nextDouble()) * (1 + 0.15 * b.tier());
            double out = (reach * 0.95 - length) * fly;
            Vec3 tail = drop(c.add(dir.scale(out)), t, b.ground());
            Vec3 side = dir.cross(new Vec3(0, 1, 0)).normalize().scale(0.06 * fly * (random.nextBoolean() ? 1 : -1));
            Vec3 mid = drop(c.add(dir.scale(out + length * 0.5)), t, b.ground()).add(side.scale(0.5));
            Vec3 head = drop(c.add(dir.scale(out + length)), t, b.ground()).add(side);
            // A piece flying past your own camera would cross the whole view.
            if (tail.distanceTo(d.cam) < BURST_CLEAR || head.distanceTo(d.cam) < BURST_CLEAR) continue;
            d.strip(tail, mid, THREAD * 0.9f, i % 3 == 0 ? PALE : LAVENDER, threadAlpha);
            d.strip(mid, head, THREAD * 0.9f, i % 3 == 0 ? PALE : LAVENDER, threadAlpha);
        }
        float scrapAlpha = 1f - Mth.clamp((t - 8f) / 4f, 0f, 1f);
        int[] colours = {PALE, LAVENDER, DEEP, LIGHT};
        for (int i = 0; i < scraps; i++) { // small solid scraps of it, tumbling
            Vec3 dir = flung(random, 0.0, 1.0);
            float size = (0.06f + 0.035f * random.nextFloat()) * (1 + 0.12f * b.tier());
            Vec3 at = drop(c.add(dir.scale((reach * 0.9 - size) * fly)), t, b.ground());
            if (at.distanceTo(d.cam) < BURST_CLEAR) continue;
            d.square(at, size, random.nextFloat() * Mth.TWO_PI + t * (0.5f + random.nextFloat()), colours[i % colours.length], scrapAlpha);
        }
    }

    /** A random direction out from a burst, between {@code low} and {@code high} (the sine of how far up it goes). */
    private static Vec3 flung(RandomSource random, double low, double high) {
        double a = random.nextDouble() * Mth.TWO_PI;
        double s = low + (high - low) * random.nextDouble();
        double c = Math.sqrt(1 - s * s);
        return new Vec3(Math.cos(a) * c, s, Math.sin(a) * c);
    }

    /** Falling a little as it goes, never through the ground. */
    private static Vec3 drop(Vec3 p, float t, double ground) {
        return new Vec3(p.x, Math.max(ground + 0.02, p.y - 0.012 * t * t), p.z);
    }

    // -- Understudy

    private static void drawPull(Draw d, Pull p) {
        float t = d.now - p.born;
        if (t > PULL_TICKS) return;
        float alpha = 1f - Mth.clamp((t - 4f) / 4f, 0f, 1f);
        float yank = Mth.clamp(t / 6f, 0f, 1f);
        float ease = yank * yank;
        Vec3 toward = p.other == null ? Vec3.ZERO : p.other.subtract(p.at).normalize();
        Vec3 feet = p.at.subtract(0, 0.9, 0);
        if (!nearCamera(p.at, 1.6)) { // threads round the place, cinched and yanked toward the other
            RandomSource random = RandomSource.create(p.seed);
            int n = reduced() ? 3 : 5;
            for (int k = 0; k < n; k++) {
                double y0 = 1.8 * (0.05 + 0.8 * k / n);
                List<Vec3> pts = helix(feet.add(toward.scale(0.75 * ease)), 0.42 * (1 - 0.6 * ease), random.nextDouble() * Mth.TWO_PI,
                        3.4, y0, y0 + 0.3, 1.0, 10);
                d.polyline(pts, THREAD, k % 2 == 0 ? LAVENDER : LIGHT, alpha);
            }
        }
        if (p.drawsLine && p.other != null && t < 8f) { // the taut thread between the two places, twanging
            float lineAlpha = 1f - Mth.clamp((t - 3f) / 5f, 0f, 1f);
            float amp = 0.14f * (1f - Mth.clamp(t / 6f, 0f, 1f));
            List<Vec3> pts = new ArrayList<>(17);
            for (int i = 0; i <= 16; i++) {
                double s = i / 16.0;
                Vec3 at = p.at.add(p.other.subtract(p.at).scale(s)).add(0, amp * Math.sin(Math.PI * s) * Math.sin(t * 2.6), 0);
                if (at.distanceTo(d.cam) > 1.2) pts.add(at);
            }
            if (pts.size() > 1) {
                d.polyline(pts, THREAD * 1.2f, LAVENDER, lineAlpha);
                d.polyline(pts, HAIR, PALE, lineAlpha);
            }
        }
    }

    // -- Stand-In

    private static void drawColumn(Draw d, Column c) {
        float t = d.now - c.born();
        int grow = c.growTicks();
        if (t > grow + COLUMN_FADE) return;
        float head = 2.0f * (1f - (1f - Mth.clamp(t / grow, 0f, 1f)) * (1f - Mth.clamp(t / grow, 0f, 1f)));
        float tailT = Mth.clamp((t - grow) / COLUMN_FADE, 0f, 1f);
        float tail = 2.0f * tailT * tailT;
        if (head - tail < 0.02f) return;
        if (nearCamera(c.feet().add(0, 1.0, 0), CLEAR_OF_CAMERA)) return;
        int n = reduced() ? 6 : 10;
        double r = 0.48;
        for (int k = 0; k < n; k++) { // threads rising round it, each with a quarter turn of twist
            double a = c.seed() * 0.37 + k * Mth.TWO_PI / n;
            List<Vec3> pts = new ArrayList<>(9);
            for (int i = 0; i <= 8; i++) {
                double y = tail + (head - tail) * i / 8.0;
                double ang = a + 0.7 * y / 2.0;
                pts.add(c.feet().add(Math.cos(ang) * r, y, Math.sin(ang) * r));
            }
            d.polyline(pts, THREAD, LAVENDER, 1f);
            if (t < grow) d.polyline(pts.subList(pts.size() - 2, pts.size()), THREAD * 1.15f, PALE, 1f);
        }
        d.loop(c.feet().add(0, head, 0), (float) r, 0.022f, LIGHT, t < grow ? 0.8f : 0.8f * (1f - tailT)); // the rising edge
    }

    // -- Strings

    private static void drawFibers(Draw d, Fibers f) {
        float t = d.now - f.born();
        RandomSource random = RandomSource.create(f.seed());
        if (!f.snap()) {
            if (t > FIBER_TICKS) return;
            float alpha = 1f - Mth.clamp((t - 2f) / 4f, 0f, 1f);
            float out = 1f - (1f - Mth.clamp(t / 4f, 0f, 1f)) * (1f - Mth.clamp(t / 4f, 0f, 1f));
            for (int i = 0; i < (reduced() ? 5 : 8); i++) {
                Vec3 dir = flung(random, -0.9, 0.9);
                double length = 0.1 + 0.1 * random.nextDouble();
                Vec3 a = f.at().add(dir.scale(0.04 + 0.22 * out)), b = a.add(dir.scale(length));
                d.strip(a, b, FIBER, i % 2 == 0 ? PALE : LAVENDER, alpha);
            }
            if (t < 2.5f) d.diamond(f.at(), 0.07f, 0.05f, PALE, 1f - t / 2.5f); // the pinch of light
            return;
        }
        if (t > SNAP_TICKS) return;
        float alpha = 1f - Mth.clamp((t - 6f) / 6f, 0f, 1f);
        float out = 1f - (1f - Mth.clamp(t / 10f, 0f, 1f)) * (1f - Mth.clamp(t / 10f, 0f, 1f));
        for (int i = 0; i < (reduced() ? 4 : 7); i++) { // frayed ends flung out, curling down as they fall
            Vec3 dir = flung(random, -0.2, 0.8);
            double length = 0.18 + 0.1 * random.nextDouble();
            Vec3 a = f.at().add(dir.scale(0.05 + 0.55 * out)).subtract(0, 0.01 * t * t, 0);
            Vec3 bend = new Vec3(0, -1, 0).scale(0.6 * out).add(dir).normalize();
            Vec3 m = a.add(dir.scale(length * 0.5));
            Vec3 b = m.add(bend.scale(length * 0.5));
            d.strip(a, m, FIBER, i % 2 == 0 ? PALE : LAVENDER, alpha);
            d.strip(m, b, FIBER, i % 2 == 0 ? PALE : LAVENDER, alpha);
        }
    }

    /** Strings' tension: short glints of light running along each tight string toward the hand, a little wider than the cord. */
    private static void drawTension(Draw d, List<Vec3[]> spans) {
        for (Vec3[] span : spans) {
            Vec3 far = span[0], near = span[1];
            double length = far.distanceTo(near);
            if (length < 0.3) continue;
            Vec3 dir = near.subtract(far).scale(1 / length);
            double offset = (d.now * GLINT_SPEED) % GLINT_SPACING;
            for (double head = offset; head < length + GLINT_LENGTH; head += GLINT_SPACING) {
                double to = Math.min(length, head), from = Math.max(0, head - GLINT_LENGTH);
                if (to - from < 0.02) continue;
                Vec3 a = far.add(dir.scale(from)), b = far.add(dir.scale(to));
                if (b.distanceTo(d.cam) < GLINT_CLEAR) continue; // close by, a glint is a big white block on your hand's string
                d.strip(a, b, 0.1f, LIGHT, 0.6f);
                d.strip(far.add(dir.scale(Math.max(from, to - 0.16))), b, 0.112f, PALE, 1f);
            }
        }
    }

    private static final double GLINT_SPACING = 1.6, GLINT_SPEED = 0.55, GLINT_LENGTH = 0.5, GLINT_CLEAR = 3.0;

    // -- Possess

    /** How far short of your own eyes your own possession thread stops, in your first person. */
    private static final double OWN_THREAD_CLEAR = 3.0;
    /** How long the possession thread takes to shoot from the body to the puppet. */
    private static final float LINK_SHOOT_TICKS = 5f;

    /** The thread from each empty body to whoever is possessing its puppet, for as long as they do. */
    private static void drawPossession(Draw d, ClientLevel level, float partial) {
        for (Map.Entry<UUID, PossessionClient.Link> e : PossessionClient.links().entrySet()) {
            PossessionLinks.Entry link = e.getValue().entry();
            Player player = level.getPlayerByUUID(link.player());
            Entity shell = link.shellId() < 0 ? null : level.getEntity(link.shellId());
            if (player == null || shell == null) continue;
            Vec3 from = shell.getPosition(partial).add(0, shell.getBbHeight() * 0.6, 0);
            Vec3 to = player.getPosition(partial).add(0, player.getBbHeight() * 0.6, 0);
            float shoot = Mth.clamp((d.now - e.getValue().since()) / LINK_SHOOT_TICKS, 0f, 1f);
            List<Vec3> pts = possessionCurve(from, to, shoot);
            // Not into your own eyes: from inside the puppet, your own thread runs straight at you, so it stops well short.
            Minecraft mc = Minecraft.getInstance();
            double clear = player == mc.getCameraEntity() && mc.options.getCameraType().isFirstPerson() ? OWN_THREAD_CLEAR : 1.2;
            pts.removeIf(p -> p.distanceTo(d.cam) < clear);
            if (pts.size() < 2) continue;
            d.polyline(pts, 0.05f, LAVENDER, 0.95f);
            d.polyline(pts, 0.016f, PALE, 1f);
        }
    }

    /** The possession thread: a little sag in the middle, like a slack thread (the same as before), out to {@code upTo} of it. */
    static List<Vec3> possessionCurve(Vec3 from, Vec3 to, float upTo) {
        double sag = Math.min(1.2, from.distanceTo(to) * 0.04);
        int n = Math.max(2, Math.min(48, (int) Math.ceil(from.distanceTo(to) / 0.6)));
        List<Vec3> out = new ArrayList<>(n + 1);
        for (int i = 0; i <= n; i++) {
            double s = upTo * i / (double) n;
            out.add(from.add(to.subtract(from).scale(s)).add(0, -sag * 4 * s * (1 - s), 0));
        }
        return out;
    }

    // ---------------------------------------------------------------- shapes

    /**
     * A thread wound round a vertical axis at {@code base}: from angle {@code a0} at height {@code y0}, turning {@code sweep}
     * radians to height {@code y1}, at radius {@code r}; drawn to {@code upTo} of its length, in {@code segments} pieces.
     */
    static List<Vec3> helix(Vec3 base, double r, double a0, double sweep, double y0, double y1, double upTo, int segments) {
        int n = Math.max(1, (int) Math.ceil(segments * upTo));
        List<Vec3> out = new ArrayList<>(n + 1);
        for (int i = 0; i <= n; i++) {
            double s = Math.min(upTo, i / (double) segments);
            double a = a0 + sweep * s;
            out.add(base.add(Math.cos(a) * r, y0 + (y1 - y0) * s, Math.sin(a) * r));
        }
        return out;
    }

    private static float smooth(float a, float b, float x) {
        float t = Mth.clamp((x - a) / (b - a), 0f, 1f);
        return t * t * (3 - 2 * t);
    }

    private static boolean nearCamera(Vec3 at, double within) {
        Minecraft mc = Minecraft.getInstance();
        if (!mc.options.getCameraType().isFirstPerson()) return false;
        return at.distanceTo(mc.gameRenderer.getMainCamera().getPosition()) < within;
    }

    // ---------------------------------------------------------------- Vanilla specks

    private static void specksRound(ClientLevel level, Vec3 base, double r, double height, int count, long seed) {
        RandomSource random = RandomSource.create(seed + (long) (base.x * 7 + base.z * 13));
        for (int i = 0; i < count; i++) {
            double a = Mth.TWO_PI * i / count + random.nextDouble() * 0.3;
            level.addParticle(i % 4 == 0 ? PALE_SPECK : SPECK, base.x + Math.cos(a) * r, base.y + height * random.nextDouble(),
                    base.z + Math.sin(a) * r, 0, 0, 0);
        }
    }

    static void speckLine(ClientLevel level, Vec3 from, Vec3 to, double spacing) {
        int n = Math.max(1, Math.min(48, (int) Math.ceil(from.distanceTo(to) / spacing)));
        for (int i = 0; i <= n; i++) {
            Vec3 p = from.add(to.subtract(from).scale(i / (double) n));
            level.addParticle(SPECK, p.x, p.y, p.z, 0, 0, 0);
        }
    }

    // ---------------------------------------------------------------- the drawing kit

    /** One frame's drawing: everything relative to the camera, into one batch of full-bright, see-through quads. */
    static final class Draw {
        final Vec3 cam;
        final float now;
        private final MultiBufferSource.BufferSource buffers;
        private final RenderType type = RenderType.entityTranslucentEmissive(WHITE);
        private final VertexConsumer out;
        private final Matrix4f m;
        private final Vec3 left, up;

        Draw(MultiBufferSource.BufferSource buffers, PoseStack pose, Camera camera, float now) {
            this.buffers = buffers;
            this.cam = camera.getPosition();
            this.now = now;
            this.out = buffers.getBuffer(type);
            this.m = pose.last().pose();
            this.left = new Vec3(camera.getLeftVector());
            this.up = new Vec3(camera.getUpVector());
        }

        void end() {
            buffers.endBatch(type);
        }

        /** A straight thread from {@code a} to {@code b} (world), {@code width} across, turned to face the camera. */
        void strip(Vec3 a, Vec3 b, float width, int rgb, float alpha) {
            if (alpha <= 0.004f) return;
            Vec3 ra = a.subtract(cam), rb = b.subtract(cam);
            Vec3 across = rb.subtract(ra).cross(ra.add(rb).scale(0.5));
            if (across.lengthSqr() < 1.0e-10) return;
            across = across.normalize().scale(width / 2);
            int al = alpha(alpha);
            vertex(ra.add(across), rgb, al);
            vertex(ra.subtract(across), rgb, al);
            vertex(rb.subtract(across), rgb, al);
            vertex(rb.add(across), rgb, al);
        }

        void polyline(List<Vec3> pts, float width, int rgb, float alpha) {
            for (int i = 0; i + 1 < pts.size(); i++) strip(pts.get(i), pts.get(i + 1), width, rgb, alpha);
        }

        /** A strip lying flat (horizontal), from {@code a} to {@code b}. */
        void flat(Vec3 a, Vec3 b, float width, int rgb, float alpha) {
            Vec3 d = b.subtract(a);
            Vec3 across = new Vec3(-d.z, 0, d.x);
            if (across.lengthSqr() < 1.0e-10) return;
            across = across.normalize().scale(width / 2);
            int al = alpha(alpha);
            Vec3 ra = a.subtract(cam), rb = b.subtract(cam);
            vertex(ra.add(across), rgb, al);
            vertex(ra.subtract(across), rgb, al);
            vertex(rb.subtract(across), rgb, al);
            vertex(rb.add(across), rgb, al);
        }

        /** A flat ring, lying level round {@code centre}. */
        void ring(Vec3 centre, float radius, float width, int rgb, float alpha) {
            if (alpha <= 0.004f || radius <= 0f) return;
            int al = alpha(alpha);
            Vec3 c = centre.subtract(cam);
            float in = Math.max(0f, radius - width / 2), outer = radius + width / 2;
            int segments = Mth.clamp(Math.round(radius * 24), 24, 96);
            for (int i = 0; i < segments; i++) {
                double a0 = Mth.TWO_PI * i / segments, a1 = Mth.TWO_PI * (i + 1) / segments;
                vertex(c.add(Math.cos(a0) * in, 0, Math.sin(a0) * in), rgb, al);
                vertex(c.add(Math.cos(a1) * in, 0, Math.sin(a1) * in), rgb, al);
                vertex(c.add(Math.cos(a1) * outer, 0, Math.sin(a1) * outer), rgb, al);
                vertex(c.add(Math.cos(a0) * outer, 0, Math.sin(a0) * outer), rgb, al);
            }
        }

        /** A level circle round {@code centre} drawn as thread: strips facing the camera, so it reads at any angle. */
        void loop(Vec3 centre, float radius, float width, int rgb, float alpha) {
            if (alpha <= 0.004f || radius <= 0f) return;
            int segments = Mth.clamp(Math.round(radius * 32), 24, 96);
            Vec3 last = centre.add(radius, 0, 0);
            for (int i = 1; i <= segments; i++) {
                double a = Mth.TWO_PI * i / segments;
                Vec3 next = centre.add(Math.cos(a) * radius, 0, Math.sin(a) * radius);
                strip(last, next, width, rgb, alpha);
                last = next;
            }
        }

        /** A circle facing the camera (a ring round a flash). */
        void circle(Vec3 centre, float radius, float width, int rgb, float alpha) {
            if (alpha <= 0.004f) return;
            int al = alpha(alpha);
            Vec3 c = centre.subtract(cam);
            float in = Math.max(0f, radius - width / 2), outer = radius + width / 2;
            for (int i = 0; i < 24; i++) {
                double a0 = Mth.TWO_PI * i / 24, a1 = Mth.TWO_PI * (i + 1) / 24;
                vertex(c.add(onScreen(a0, in)), rgb, al);
                vertex(c.add(onScreen(a1, in)), rgb, al);
                vertex(c.add(onScreen(a1, outer)), rgb, al);
                vertex(c.add(onScreen(a0, outer)), rgb, al);
            }
        }

        /** A filled disc facing the camera. */
        void disc(Vec3 centre, float radius, int rgb, float alpha) {
            if (alpha <= 0.004f) return;
            int al = alpha(alpha);
            Vec3 c = centre.subtract(cam);
            for (int i = 0; i < 24; i += 2) {
                double a0 = Mth.TWO_PI * i / 24, a1 = Mth.TWO_PI * (i + 1) / 24, a2 = Mth.TWO_PI * (i + 2) / 24;
                vertex(c, rgb, al);
                vertex(c.add(onScreen(a0, radius)), rgb, al);
                vertex(c.add(onScreen(a1, radius)), rgb, al);
                vertex(c.add(onScreen(a2, radius)), rgb, al);
            }
        }

        /** A small upright diamond facing the camera: {@code half} tall from its middle, {@code halfWidth} across. */
        void diamond(Vec3 centre, float half, float halfWidth, int rgb, float alpha) {
            if (alpha <= 0.004f) return;
            int al = alpha(alpha);
            Vec3 c = centre.subtract(cam);
            Vec3 side = left.scale(halfWidth);
            vertex(c.add(0, half, 0), rgb, al);
            vertex(c.add(side), rgb, al);
            vertex(c.add(0, -half, 0), rgb, al);
            vertex(c.subtract(side), rgb, al);
        }

        /** A small solid square facing the camera, turned {@code angle}. */
        void square(Vec3 centre, float size, float angle, int rgb, float alpha) {
            if (alpha <= 0.004f) return;
            int al = alpha(alpha);
            Vec3 c = centre.subtract(cam);
            for (int k = 0; k < 4; k++) vertex(c.add(onScreen(angle + k * Mth.HALF_PI, size * 0.7071)), rgb, al);
        }

        private Vec3 onScreen(double angle, double r) {
            return left.scale(Math.cos(angle) * r).add(up.scale(Math.sin(angle) * r));
        }

        private static int alpha(float alpha) {
            return Math.round(Mth.clamp(alpha, 0f, 1f) * 255);
        }

        private void vertex(Vec3 p, int rgb, int alpha) {
            out.addVertex(m, (float) p.x, (float) p.y, (float) p.z).setColor(rgb >> 16 & 0xFF, rgb >> 8 & 0xFF, rgb & 0xFF, alpha)
                    .setUv(0.5f, 0.5f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(0f, 1f, 0f);
        }
    }
}

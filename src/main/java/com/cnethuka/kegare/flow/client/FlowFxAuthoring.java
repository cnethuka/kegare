package com.cnethuka.kegare.flow.client;

import static com.cnethuka.kegare.client.visual.FxKit.*;

import com.cnethuka.kegare.flow.FlowIds;
import com.lowdragmc.lowdraglib2.math.GradientColor;
import com.lowdragmc.lowdraglib2.math.HDRColor;
import com.lowdragmc.photon.client.fx.FX;
import com.lowdragmc.photon.client.gameobject.emitter.data.material.BlendMode;
import com.lowdragmc.photon.client.gameobject.emitter.data.material.kila.KilaDriver;
import com.lowdragmc.photon.client.gameobject.emitter.data.material.kila.KilaMaterial;
import com.lowdragmc.photon.client.gameobject.emitter.data.material.kila.KilaPresets;
import com.lowdragmc.photon.client.gameobject.emitter.data.number.NumberFunction;
import com.lowdragmc.photon.client.gameobject.emitter.data.number.NumberFunction3;
import com.lowdragmc.photon.client.gameobject.emitter.data.number.curve.Curve;
import com.lowdragmc.photon.client.gameobject.emitter.data.number.curve.ECBCurves;
import com.lowdragmc.photon.client.gameobject.emitter.data.shape.Sphere;
import com.lowdragmc.photon.client.gameobject.emitter.particle.ParticleConfig;
import com.lowdragmc.photon.client.gameobject.emitter.particle.ParticleEmitter;
import com.lowdragmc.photon.client.gameobject.emitter.particle.ParticleRendererSetting;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * DEV TOOL (exported with {@code /kegare debug fx export}): Flow's aura (DESIGN §3.16), built from Photon's own objects (see
 * {@link com.cnethuka.kegare.client.visual.FxKit}). One flame standing round the body and drawn up to a single point over the head:
 * <ul>
 *   <li>its body: soft flames (Photon's fire: a radial glow broken up by noise rising through it), alpha-blended so it holds on a bright
 *   sky, the faint outer edge cut away hard; born round the legs and chest, they rise and shrink, and the emitter sits at the tip, so
 *   its pull inward is a pull up too and every flame closes on the same point;</li>
 *   <li>its shadow: the same a little bigger and dark, behind it, so the shape has a dark edge against the sky (the body writes depth,
 *   so whatever's behind it stays hidden whichever order they're drawn in);</li>
 *   <li>a brighter heart added as light round the head and shoulders, tongues licking up past the tip, a soft glow behind it all for
 *   the night, and grey-ink wisps leaving the tip, left behind in the world as the player moves.</li>
 * </ul>
 * Everything that burns is drawn pushed back from the camera, so the flame stands behind the player and the body stays clear. Authored
 * white and tinted with the Malice color the viewer is shown (emitters named {@code tint...}); the wisps are never tinted. Anchored at
 * mid-body: the feet are local y -0.9, the top of the head +0.9. Fuller and faster in a fight, much calmer and fainter for Background
 * Flow, and for your own first person only a few flames licking up at your sides ({@link #self}).
 */
public final class FlowFxAuthoring {
    /** The grey ink of the wisps, from their edge to their middle. */
    private static final int[] WISP_RAMP = {0xFF0C0D12, 0xFF22262E, 0xFF3A404A, 0xFF4A515C};
    /** The flame's own ramp (tinted): a slightly darker edge to a full heart. */
    private static final int[] FLAME_RAMP = {0xFFA0A0A0, 0xFFC8C8C8, 0xFFFFFFFF, 0xFFFFFFFF};
    /** The tongues' ramp (tinted): an ink edge, so they hold on the sky where they leave the shape. */
    private static final int[] TONGUE_RAMP = {0xFF0C0910, 0xFFE0E0E0, 0xFFFFFFFF, 0xFFFFFFFF};
    /** Its shadow's ramp (tinted, so it's the Malice color nearly black). */
    private static final int[] SHADE_RAMP = {0xFF101010, 0xFF202020, 0xFF303030, 0xFF383838};
    /** The heart's ramp, added as light: nothing at its edge, full at its middle. */
    private static final int[] CORE_RAMP = {0xFF000000, 0xFF303030, 0xFF909090, 0xFFFFFFFF};

    private FlowFxAuthoring() {}

    public static Map<ResourceLocation, Supplier<FX>> effects() {
        Map<ResourceLocation, Supplier<FX>> out = new LinkedHashMap<>();
        out.put(FlowIds.FX_AURA, () -> aura(Mood.STEADY));
        out.put(FlowIds.FX_AURA_FIGHT, () -> aura(Mood.FIGHT));
        out.put(FlowIds.FX_AURA_CALM, () -> aura(Mood.CALM));
        out.put(FlowIds.FX_AURA_SELF, () -> self(false));
        out.put(FlowIds.FX_AURA_CALM_SELF, () -> self(true));
        return out;
    }

    /** Where your own first person's flames are, for the vanilla stand-in: at your side, from the body's middle (forward, up, right). */
    public static final Vec3 SELF_AT = new Vec3(0.35, -0.3, 0.45);

    /**
     * How the aura burns: {@code size} scales the whole shape, {@code pace} how fast it rises (lifetimes shorten with it, so the height
     * stays), {@code amount} how many flames, {@code alpha} how solid they are.
     */
    enum Mood {
        STEADY(1f, 1f, 1f, 1f, true),
        /** In a fight: about 10% bigger, faster, fuller. */
        FIGHT(1.1f, 1.3f, 1.35f, 1f, true),
        /** Background Flow: smaller, slower, few and faint, no heart and no wisps. */
        CALM(0.85f, 0.7f, 0.4f, 0.55f, false);

        final float size, pace, amount, alpha;
        final boolean full;

        Mood(float size, float pace, float amount, float alpha, boolean full) {
            this.size = size;
            this.pace = pace;
            this.amount = amount;
            this.alpha = alpha;
            this.full = full;
        }
    }

    private static FX aura(Mood m) {
        List<ParticleEmitter> parts = new ArrayList<>();
        parts.add(glow(m));
        if (m.full) {
            parts.add(new Flames("wisps").world().rate(1.8f).max(60).life(16, 26).width(0.22f, 0.36f).height(0.3f, 0.5f).spread(0.7f, 0.4f)
                    .born(1.8f).tip(1.8f).rise(1.2f).pull(0f).drift(0.3f).end(0.15f).alpha(170)
                    .look(new Look(WISP_RAMP).clip(0.25f).cameraOffset(0f)).build(m));
        }
        parts.add(new Flames("tint_shade").rate(6f).max(200).life(14, 22).width(0.98f, 1.3f).height(1.3f, 1.7f).spread(1f, 1.6f)
                .born(-0.35f).tip(2.12f).rise(0.9f).pull(-6f).end(0.14f).alpha(200)
                .look(new Look(SHADE_RAMP).clip(0.3f).cameraOffset(-0.9f)).build(m));
        parts.add(new Flames("tint_body").rate(6f).max(200).life(14, 22).width(0.9f, 1.2f).height(1.2f, 1.6f).spread(1f, 1.6f)
                .born(-0.35f).tip(2.1f).rise(0.9f).pull(-6f).end(0.12f).alpha(255)
                .look(new Look(FLAME_RAMP).clip(0.3f).intensity(1.1f).depthWrite()).build(m));
        if (m.full) {
            parts.add(new Flames("tint_core").rate(4f).max(60).life(14, 20).width(0.8f, 1.1f).height(1.1f, 1.5f).spread(0.8f, 1.5f)
                    .born(0.1f).tip(2f).rise(0.9f).pull(-5f).end(0.15f).alpha(255)
                    .look(new Look(CORE_RAMP).additive().clip(0.35f).cameraOffset(-0.65f)).build(m));
        }
        parts.add(new Flames("tint_tongues").shell().rate(2f).max(60).life(9, 14).width(0.3f, 0.45f).height(0.9f, 1.2f).spread(1.2f, 1.4f)
                .born(0.6f).tip(2.4f).rise(1.6f).pull(-6f).end(0.12f).alpha(255)
                .look(new Look(TONGUE_RAMP).clip(0.22f)).build(m));
        return fx(parts.toArray(ParticleEmitter[]::new));
    }

    /** A soft glow behind it all, mostly for the night. */
    private static ParticleEmitter glow(Mood m) {
        ParticleEmitter glow = emitter("tint_glow", true, 40);
        ParticleConfig w = glow.config;
        w.setSimulationSpace(ParticleConfig.Space.Local);
        w.setMaxParticles(2);
        w.setStartLifetime(NumberFunction.constant(40));
        w.setStartSpeed(NumberFunction.constant(0));
        w.setStartSize(new NumberFunction3(NumberFunction.constant(2.6f * m.size), NumberFunction.constant(4f * m.size), NumberFunction.constant(1)));
        w.shape.setScale(new NumberFunction3(0, 0, 0));
        w.emission.setEmissionRate(NumberFunction.constant(0));
        w.emission.getBursts().add(burst(0, 1));
        int a = Math.round(45 * m.alpha);
        w.colorOverLifetime.setEnable(true);
        w.colorOverLifetime.setColor(gradient(a << 24 | 0xFFFFFF, a << 24 | 0xFFFFFF, a << 24 | 0xFFFFFF));
        w.renderer.setRenderMode(ParticleRendererSetting.Mode.VerticalBillboard);
        KilaMaterial mat = KilaPresets.GLOW.create();
        mat.color = new HDRColor(1f, 1f, 1f, 1f, 1f);
        mat.worldLight = false;
        mat.cameraOffset = -1f;
        material(glow, mat);
        glow.transform().localPosition(new Vector3f(0, 0.7f * m.size, 0));
        return glow;
    }

    /**
     * Your own first person: never round your head, where the camera is. A few small flames licking up at your sides, a little ahead of
     * you, so they rise at the bottom corners of your view, and round you when you look down. Turns with the body ({@code AuraClient}),
     * authored facing +X with +Z to the right. {@code calm}: Background Flow's, fewer and fainter.
     */
    private static FX self(boolean calm) {
        float n = calm ? 0.4f : 1f;
        int alpha = Math.round((calm ? 0.55f : 1f) * 150);
        List<ParticleEmitter> parts = new ArrayList<>();
        for (int side : new int[] {-1, 1}) {
            ParticleEmitter e = new Flames(side < 0 ? "tint_left" : "tint_right").rate(1.6f * n).max(40).life(9, 14).width(0.18f, 0.28f)
                    .height(0.32f, 0.5f).spread(0.3f, 1.2f).born((float) SELF_AT.y).tip((float) SELF_AT.y).rise(1.05f).pull(0f).end(0.2f).alpha(alpha)
                    .look(new Look(FLAME_RAMP).clip(0.3f).cameraOffset(0f)).build(Mood.STEADY);
            e.transform().localPosition(new Vector3f((float) SELF_AT.x, (float) SELF_AT.y, side * (float) SELF_AT.z));
            parts.add(e);
        }
        return fx(parts.toArray(ParticleEmitter[]::new));
    }

    /** A material for flames: Photon's fire, its ramp, how hard its edge is cut, how it blends and where it's drawn. */
    private static final class Look {
        final int[] ramp;
        float clip = 0.3f, intensity = 1f, cameraOffset = -0.7f;
        boolean additive, depthWrite;

        Look(int[] ramp) {
            this.ramp = ramp;
        }

        Look clip(float v) { clip = v; return this; }
        Look intensity(float v) { intensity = v; return this; }
        Look cameraOffset(float v) { cameraOffset = v; return this; }
        Look additive() { additive = true; return this; }
        Look depthWrite() { depthWrite = true; return this; }

        /**
         * Photon's fire, alpha-blended (or added as light): the faint outer edge cut away at {@code clip}, what's left mapped through the
         * ramp from edge to heart, burning away from the edge as it fades. A camera offset below 0 draws it pushed back from the camera.
         */
        KilaMaterial material() {
            KilaMaterial m = KilaPresets.FIRE.create();
            m.blend = additive ? BlendMode.Preset.ADDITIVE : BlendMode.Preset.ALPHA;
            m.color = new HDRColor(1f, 1f, 1f, 1f, intensity);
            m.alphaClip = clip;
            m.alphaSharpen = 0.8f;
            m.cameraOffset = cameraOffset;
            m.ramp.gradient.getRgbP().clear();
            m.ramp.gradient.getRgbP().addAll(new GradientColor(ramp).getRgbP());
            m.distortion.texture.scroll(0f, 0.6f);
            m.dissolve.softness = 0.2f;
            m.dissolve.texture.scroll(0f, 0.4f);
            m.dissolve.progress.bind(KilaDriver.Source.VERTEX_A, -1f);
            m.depthWrite = depthWrite;
            return m;
        }
    }

    /**
     * One layer of flames: born in an upright ellipsoid ({@code spread} wide and tall, centred at {@code born}; on its surface for a
     * {@code shell}), rising at {@code rise}, drawn toward the {@code tip} at {@code pull} and shrinking to {@code end} of their size,
     * so together they close to a point. Each is upright and faces the camera.
     */
    private static final class Flames {
        final String name;
        boolean shell, world;
        int max = 100, alpha = 255;
        float rate, lifeMin = 14, lifeMax = 20, wMin = 1, wMax = 1.4f, hMin = 1.4f, hMax = 1.9f, sx = 1, sy = 1.6f, born, tip,
                rise = 1, pull, drift, end = 0.15f;
        Look look;

        Flames(String name) {
            this.name = name;
        }

        Flames shell() { shell = true; return this; }
        Flames world() { world = true; return this; }
        Flames rate(float v) { rate = v; return this; }
        Flames max(int v) { max = v; return this; }
        Flames life(float min, float max) { lifeMin = min; lifeMax = max; return this; }
        Flames width(float min, float max) { wMin = min; wMax = max; return this; }
        Flames height(float min, float max) { hMin = min; hMax = max; return this; }
        Flames spread(float x, float y) { sx = x; sy = y; return this; }
        Flames born(float v) { born = v; return this; }
        Flames tip(float v) { tip = v; return this; }
        Flames rise(float v) { rise = v; return this; }
        Flames pull(float v) { pull = v; return this; }
        Flames drift(float v) { drift = v; return this; }
        Flames end(float v) { end = v; return this; }
        Flames alpha(int v) { alpha = v; return this; }
        Flames look(Look v) { look = v; return this; }

        ParticleEmitter build(Mood m) {
            float k = m.size;
            ParticleEmitter e = emitter(name, true, 20);
            ParticleConfig c = e.config;
            c.setSimulationSpace(world ? ParticleConfig.Space.World : ParticleConfig.Space.Local);
            c.setMaxParticles(Math.round(max * m.amount));
            c.setStartLifetime(range(lifeMin / m.pace, lifeMax / m.pace));
            c.setStartSpeed(NumberFunction.constant(0));
            c.setStartSize(new NumberFunction3(range(wMin * k, wMax * k), range(hMin * k, hMax * k), NumberFunction.constant(1)));
            Sphere shape = sphere(0.5f);
            if (shell) shape.setRadiusThickness(0f);
            c.shape.setShape(shape);
            c.shape.setScale(new NumberFunction3(sx * k, sy * k, sx * k));
            c.emission.setEmissionRate(NumberFunction.constant(rate * m.amount));
            c.velocityOverLifetime.setEnable(true);
            c.velocityOverLifetime.setLinear(new NumberFunction3(drift * m.pace, rise * k * m.pace, 0));
            c.velocityOverLifetime.setRadial(NumberFunction.constant(pull * k * m.pace));
            c.sizeOverLifetime.setEnable(true);
            c.sizeOverLifetime.setSize(uniform(swellThenTaper(end)));
            int a = Math.round(alpha * m.alpha);
            c.colorOverLifetime.setEnable(true);
            c.colorOverLifetime.setColor(gradient(0x00FFFFFF, a << 24 | 0xFFFFFF, a << 24 | 0xFFFFFF, 0x00FFFFFF));
            c.renderer.setRenderMode(ParticleRendererSetting.Mode.VerticalBillboard);
            material(e, look.material());
            // The emitter sits at the point the flames are drawn in to, and they're born below it: the pull inward is also a pull up.
            e.transform().localPosition(new Vector3f(0, tip * k, 0));
            c.shape.setPosition(new NumberFunction3(0, (born - tip) * k, 0));
            return e;
        }
    }

    /** Swells to full size in the first fifth of its life, then tapers to {@code end} of it. */
    private static NumberFunction swellThenTaper(float end) {
        return new Curve(0, 1, 0, 1, "t", "v", new ECBCurves(
                0f, 0.55f, 0.07f, 0.85f, 0.14f, 1f, 0.2f, 1f,
                0.2f, 1f, 0.5f, 0.9f, 0.8f, (1f + end) / 2f, 1f, end));
    }
}

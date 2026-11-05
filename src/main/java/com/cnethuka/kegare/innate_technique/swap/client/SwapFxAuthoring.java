package com.cnethuka.kegare.innate_technique.swap.client;

import static com.cnethuka.kegare.client.visual.FxKit.*;

import com.cnethuka.kegare.client.visual.FxKit;
import com.cnethuka.kegare.innate_technique.swap.SwapIds;
import com.lowdragmc.photon.client.fx.FX;
import com.lowdragmc.photon.client.gameobject.emitter.data.material.kila.KilaPresets;
import com.lowdragmc.photon.client.gameobject.emitter.data.number.NumberFunction;
import com.lowdragmc.photon.client.gameobject.emitter.data.number.NumberFunction3;
import com.lowdragmc.photon.client.gameobject.emitter.data.shape.Sphere;
import com.lowdragmc.photon.client.gameobject.emitter.particle.ParticleConfig;
import com.lowdragmc.photon.client.gameobject.emitter.particle.ParticleEmitter;
import com.lowdragmc.photon.client.gameobject.emitter.particle.ParticleRendererSetting;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.resources.ResourceLocation;
import org.joml.Vector3f;

/**
 * DEV TOOL (exported with {@code /kegarefx export}): Swap's Photon effects, built from Photon's own objects (see
 * {@link FxKit}). Palette: teal with Malice violet, never Rime's ice blue. Body-anchored effects sit at mid-body, so
 * the feet are local y -0.9.
 */
public final class SwapFxAuthoring {
    private static final int PALE = 0xFFCCFBF1;
    private static final int LIGHT = 0xFF5EEAD4;
    private static final int TEAL = 0xFF14B8A6;
    private static final int DEEP = 0xFF0F766E;
    private static final int VIOLET_PALE = 0xFFE9D5FF;
    private static final int VIOLET = 0xFFA855F7;

    private SwapFxAuthoring() {}

    public static Map<ResourceLocation, Supplier<FX>> effects() {
        Map<ResourceLocation, Supplier<FX>> out = new LinkedHashMap<>();
        out.put(SwapIds.FX_SWAP_DEPART, SwapFxAuthoring::depart);
        out.put(SwapIds.FX_SWAP_ARRIVE, SwapFxAuthoring::arrive);
        out.put(SwapIds.FX_SWAP_LINK, SwapFxAuthoring::link);
        out.put(SwapIds.FX_SWITCHBACK_TIMER, SwapFxAuthoring::switchbackTimer);
        out.put(SwapIds.FX_AWARENESS_SHIMMER, SwapFxAuthoring::awarenessShimmer);
        out.put(SwapIds.FX_MIMIC_FLICKER, SwapFxAuthoring::mimicFlicker);
        out.put(SwapIds.FX_ANCHOR_MARKER, SwapFxAuthoring::anchorMarker);
        out.put(SwapIds.FX_ANCHOR_TRAIL, SwapFxAuthoring::anchorTrail);
        out.put(SwapIds.FX_EXCHANGE_TRANSFER, SwapFxAuthoring::exchangeTransfer);
        out.put(SwapIds.FX_MOMENTUM_BURST, SwapFxAuthoring::momentumBurst);
        out.put(SwapIds.FX_TOTAL_SWAP_BURST, SwapFxAuthoring::totalSwapBurst);
        return out;
    }

    /** Where someone left: motes pulled in from a shell around the body, and a flash that collapses to nothing. */
    private static FX depart() {
        ParticleEmitter pull = burstEmitter("pull", 26, 30);
        ParticleConfig c = pull.config;
        c.setStartLifetime(range(7, 10));
        c.setStartSpeed(range(-3.6f, -2.6f)); // inward
        c.setStartSize(uniform(range(0.05f, 0.1f)));
        Sphere shell = sphere(1.0f);
        shell.setRadiusThickness(0f);
        c.shape.setShape(shell);
        c.shape.setScale(new NumberFunction3(0.7f, 1.1f, 0.7f));
        fadeIn(c, VIOLET, TEAL);
        material(pull, glow(0.35f, 0.85f, 0.8f, 1.4f));

        ParticleEmitter flash = single("flash", 8, 1.3f);
        fadeOut(flash.config, PALE, TEAL);
        shrink(flash.config);
        material(flash, glow(0.3f, 0.9f, 0.82f, 1.3f));

        ParticleEmitter column = single("column", 9, 1.6f);
        column.config.renderer.setRenderMode(ParticleRendererSetting.Mode.Horizontal);
        shrink(column.config);
        fadeOut(column.config, LIGHT, DEEP);
        material(column, recolor(KilaPresets.SHOCKWAVE.create(), 0.3f, 0.85f, 0.78f, 1.2f));
        column.transform().localPosition(new Vector3f(0, -0.85f, 0));
        return fx(pull, flash, column);
    }

    /** Where someone arrived: a teal ring spreading on the ground, glints thrown out, a violet-edged flash. */
    private static FX arrive() {
        ParticleEmitter ring = single("ring", 12, 2.4f);
        ring.config.renderer.setRenderMode(ParticleRendererSetting.Mode.Horizontal);
        grow(ring.config, 0.2f);
        fadeOut(ring.config, LIGHT, DEEP);
        material(ring, recolor(KilaPresets.SHOCKWAVE.create(), 0.3f, 0.9f, 0.8f, 1.4f));
        ring.transform().localPosition(new Vector3f(0, -0.85f, 0));

        ParticleEmitter glints = burstEmitter("glints", 22, 26);
        ParticleConfig c = glints.config;
        c.setStartLifetime(range(8, 14));
        c.setStartSpeed(range(2.2f, 4.5f));
        c.setStartSize(uniform(range(0.04f, 0.09f)));
        c.shape.setShape(sphere(0.35f));
        gravity(c, 0.25f);
        fadeOut(c, PALE, TEAL);
        material(glints, recolor(KilaPresets.SPARKS.create(), 0.4f, 0.95f, 0.85f, 1.5f));

        ParticleEmitter flash = single("flash", 7, 1.1f);
        fadeOut(flash.config, VIOLET_PALE, VIOLET);
        shrink(flash.config);
        material(flash, glow(0.66f, 0.4f, 0.97f, 1.2f));
        return fx(ring, glints, flash);
    }

    /** One bead of the link drawn between two swapped players (the client lines beads up into a beam). */
    private static FX link() {
        ParticleEmitter bead = single("bead", 9, 0.34f);
        fadeOut(bead.config, PALE, VIOLET);
        shrink(bead.config);
        material(bead, glow(0.35f, 0.92f, 0.85f, 1.5f));

        ParticleEmitter sparks = burstEmitter("sparks", 3, 4);
        ParticleConfig c = sparks.config;
        c.setStartLifetime(range(6, 10));
        c.setStartSpeed(range(0.2f, 0.6f));
        c.setStartSize(uniform(range(0.03f, 0.05f)));
        c.shape.setShape(sphere(0.12f));
        fadeOut(c, LIGHT, VIOLET);
        material(sparks, glow(0.5f, 0.5f, 0.95f, 1.2f));
        return fx(bead, sparks);
    }

    /** Switchback's countdown: a faint ring at the feet closing in once a second, with two motes circling. */
    private static FX switchbackTimer() {
        ParticleEmitter ring = emitter("ring", true, 20);
        ParticleConfig c = ring.config;
        c.setMaxParticles(2);
        c.setStartLifetime(NumberFunction.constant(20));
        c.setStartSpeed(NumberFunction.constant(0));
        c.setStartSize(uniform(NumberFunction.constant(1.6f)));
        c.shape.setScale(new NumberFunction3(0, 0, 0));
        c.emission.setEmissionRate(NumberFunction.constant(0));
        c.emission.getBursts().add(burst(0, 1));
        c.renderer.setRenderMode(ParticleRendererSetting.Mode.Horizontal);
        shrink(c);
        c.colorOverLifetime.setEnable(true);
        c.colorOverLifetime.setColor(gradient(0x445EEAD4, 0x6614B8A6, 0x22A855F7));
        material(ring, recolor(KilaPresets.SHOCKWAVE.create(), 0.3f, 0.85f, 0.78f, 0.9f));
        ring.transform().localPosition(new Vector3f(0, 0.05f, 0));

        ParticleEmitter motes = emitter("motes", true, 20);
        ParticleConfig m = motes.config;
        m.setMaxParticles(12);
        m.setStartLifetime(range(14, 20));
        m.setStartSpeed(NumberFunction.constant(0));
        m.setStartSize(uniform(range(0.04f, 0.06f)));
        Sphere around = sphere(0.6f);
        around.setRadiusThickness(0f);
        m.shape.setShape(around);
        m.shape.setScale(new NumberFunction3(1f, 0f, 1f));
        m.emission.setEmissionRate(NumberFunction.constant(0.6f));
        m.velocityOverLifetime.setEnable(true);
        m.velocityOverLifetime.setOrbital(new NumberFunction3(0, 3f, 0));
        fadeInOut(m, LIGHT, DEEP);
        material(motes, glow(0.35f, 0.9f, 0.8f, 1f));
        motes.transform().localPosition(new Vector3f(0, 0.1f, 0));
        return fx(ring, motes);
    }

    /** Only the disguised player sees this: a few faint violet-teal motes drifting up close around them. */
    private static FX awarenessShimmer() {
        ParticleEmitter motes = emitter("motes", true, 20);
        ParticleConfig c = motes.config;
        c.setSimulationSpace(ParticleConfig.Space.World);
        c.setMaxParticles(40);
        c.setStartLifetime(range(18, 28));
        c.setStartSpeed(NumberFunction.constant(0));
        c.setStartSize(uniform(range(0.03f, 0.06f)));
        Sphere around = sphere(0.7f);
        around.setRadiusThickness(0f);
        c.shape.setShape(around);
        c.shape.setScale(new NumberFunction3(1f, 1.3f, 1f));
        c.emission.setEmissionRate(NumberFunction.constant(1.4f));
        c.velocityOverLifetime.setEnable(true);
        c.velocityOverLifetime.setLinear(new NumberFunction3(0, 0.5f, 0));
        c.colorOverLifetime.setEnable(true);
        c.colorOverLifetime.setColor(gradient(0x00E9D5FF, 0x88A855F7, 0x665EEAD4, 0x0014B8A6));
        material(motes, glow(0.55f, 0.55f, 0.95f, 0.9f));
        return fx(motes);
    }

    /**
     * Mimic's cast, seen by everyone: a teal glow that flickers on and off over the body (twice, then gone) and
     * glitch motes winking across it, as the face is redrawn. Half a second; the disguise afterwards is the trick.
     */
    private static FX mimicFlicker() {
        ParticleEmitter flicker = single("flicker", 11, 2.3f);
        flicker.config.colorOverLifetime.setEnable(true);
        flicker.config.colorOverLifetime.setColor(gradient(0x0014B8A6, 0xCC5EEAD4, 0x1114B8A6, 0xAA5EEAD4, 0x110F766E, 0x66CCFBF1, 0x0014B8A6));
        material(flicker, glow(0.3f, 0.9f, 0.82f, 1.3f));

        ParticleEmitter motes = burstEmitter("motes", 20, 24);
        ParticleConfig c = motes.config;
        c.setStartLifetime(range(4, 10));
        c.setStartSpeed(NumberFunction.constant(0));
        c.setStartSize(uniform(range(0.05f, 0.11f)));
        Sphere body = sphere(0.55f);
        body.setRadiusThickness(0f);
        c.shape.setShape(body);
        c.shape.setScale(new NumberFunction3(1f, 1.7f, 1f));
        fadeOut(c, PALE, TEAL);
        material(motes, glow(0.35f, 0.92f, 0.85f, 1.5f));

        ParticleEmitter ring = single("ring", 8, 1.5f);
        ring.config.renderer.setRenderMode(ParticleRendererSetting.Mode.Horizontal);
        grow(ring.config, 0.4f);
        fadeOut(ring.config, LIGHT, DEEP);
        material(ring, recolor(KilaPresets.SHOCKWAVE.create(), 0.3f, 0.9f, 0.8f, 1.2f));
        ring.transform().localPosition(new Vector3f(0, -0.85f, 0));
        return fx(flicker, motes, ring);
    }

    /** The anchor waiting: a breathing teal core, a violet halo, motes orbiting it. */
    private static FX anchorMarker() {
        ParticleEmitter core = emitter("core", true, 24);
        ParticleConfig c = core.config;
        c.setMaxParticles(2);
        c.setStartLifetime(NumberFunction.constant(24));
        c.setStartSpeed(NumberFunction.constant(0));
        c.setStartSize(uniform(NumberFunction.constant(0.45f)));
        c.shape.setScale(new NumberFunction3(0, 0, 0));
        c.emission.setEmissionRate(NumberFunction.constant(0));
        c.emission.getBursts().add(burst(0, 1));
        c.colorOverLifetime.setEnable(true);
        c.colorOverLifetime.setColor(gradient(0x995EEAD4, 0xFF14B8A6, 0x995EEAD4));
        material(core, glow(0.3f, 0.92f, 0.82f, 1.6f));

        ParticleEmitter halo = emitter("halo", true, 36);
        ParticleConfig h = halo.config;
        h.setMaxParticles(2);
        h.setStartLifetime(NumberFunction.constant(36));
        h.setStartSpeed(NumberFunction.constant(0));
        h.setStartSize(uniform(NumberFunction.constant(1.0f)));
        h.shape.setScale(new NumberFunction3(0, 0, 0));
        h.emission.setEmissionRate(NumberFunction.constant(0));
        h.emission.getBursts().add(burst(0, 1));
        h.colorOverLifetime.setEnable(true);
        h.colorOverLifetime.setColor(gradient(0x22A855F7, 0x55A855F7, 0x22A855F7));
        material(halo, glow(0.66f, 0.4f, 0.97f, 0.8f));

        ParticleEmitter motes = emitter("motes", true, 20);
        ParticleConfig m = motes.config;
        m.setMaxParticles(16);
        m.setStartLifetime(range(14, 22));
        m.setStartSpeed(NumberFunction.constant(0));
        m.setStartSize(uniform(range(0.03f, 0.05f)));
        Sphere shell = sphere(0.4f);
        shell.setRadiusThickness(0f);
        m.shape.setShape(shell);
        m.emission.setEmissionRate(NumberFunction.constant(0.8f));
        m.velocityOverLifetime.setEnable(true);
        m.velocityOverLifetime.setOrbital(new NumberFunction3(0.5f, 2.4f, 0));
        fadeInOut(m, PALE, TEAL);
        material(motes, glow(0.4f, 0.95f, 0.85f, 1.2f));
        return fx(core, halo, motes);
    }

    /** The anchor in flight: a teal ribbon fading to violet, and a few glints shed behind it. */
    private static FX anchorTrail() {
        ParticleEmitter ribbon = FxKit.ribbon("ribbon", 0.18f, 8, gradient(PALE, TEAL, 0x00A855F7),
                recolor(KilaPresets.ENERGY_TRAIL.create(), 0.3f, 0.9f, 0.8f, 1.5f));
        ParticleEmitter glints = emitter("glints", true, 20);
        ParticleConfig c = glints.config;
        c.setSimulationSpace(ParticleConfig.Space.World);
        c.setMaxParticles(40);
        c.setStartLifetime(range(8, 12));
        c.setStartSpeed(range(0.0f, 0.3f));
        c.setStartSize(uniform(range(0.04f, 0.08f)));
        c.emission.setEmissionRate(NumberFunction.constant(2.5f));
        c.shape.setShape(sphere(0.05f));
        fadeOut(c, LIGHT, VIOLET);
        shrink(c);
        material(glints, glow(0.4f, 0.9f, 0.85f, 1.3f));
        return fx(ribbon, glints);
    }

    /** One step of an exchanged effect crossing over: a violet-cored orb with a teal halo (beads line up into its path). */
    private static FX exchangeTransfer() {
        ParticleEmitter orb = single("orb", 7, 0.42f);
        fadeOut(orb.config, VIOLET_PALE, VIOLET);
        shrink(orb.config);
        material(orb, glow(0.66f, 0.42f, 0.97f, 1.7f));

        ParticleEmitter halo = single("halo", 6, 0.75f);
        fadeOut(halo.config, 0x885EEAD4, 0x0014B8A6);
        shrink(halo.config);
        material(halo, glow(0.3f, 0.9f, 0.8f, 0.9f));
        return fx(orb, halo);
    }

    /** Momentum traded: streaks thrown out sideways, a ring at the waist, a hard flash. */
    private static FX momentumBurst() {
        ParticleEmitter streaks = burstEmitter("streaks", 26, 30);
        ParticleConfig c = streaks.config;
        c.setStartLifetime(range(6, 11));
        c.setStartSpeed(range(4f, 7f));
        c.setStartSize(uniform(range(0.05f, 0.1f)));
        c.shape.setShape(sphere(0.3f));
        c.shape.setScale(new NumberFunction3(1f, 0.25f, 1f));
        fadeOut(c, PALE, VIOLET);
        material(streaks, recolor(KilaPresets.SPARKS.create(), 0.5f, 0.75f, 0.95f, 1.6f));

        ParticleEmitter ring = single("ring", 10, 2.0f);
        ring.config.renderer.setRenderMode(ParticleRendererSetting.Mode.Horizontal);
        grow(ring.config, 0.15f);
        fadeOut(ring.config, VIOLET_PALE, VIOLET);
        material(ring, recolor(KilaPresets.SHOCKWAVE.create(), 0.66f, 0.42f, 0.97f, 1.3f));

        ParticleEmitter flash = single("flash", 6, 1.0f);
        fadeOut(flash.config, PALE, TEAL);
        shrink(flash.config);
        material(flash, glow(0.35f, 0.92f, 0.85f, 1.4f));
        return fx(streaks, ring, flash);
    }

    /** Total Swap: rings on the ground and around the body, a burst of teal and violet sparks, motes rising after. */
    private static FX totalSwapBurst() {
        ParticleEmitter ground = single("ground", 16, 3.6f);
        ground.config.renderer.setRenderMode(ParticleRendererSetting.Mode.Horizontal);
        grow(ground.config, 0.1f);
        fadeOut(ground.config, LIGHT, DEEP);
        material(ground, recolor(KilaPresets.SHOCKWAVE.create(), 0.3f, 0.9f, 0.8f, 1.5f));
        ground.transform().localPosition(new Vector3f(0, -0.85f, 0));

        ParticleEmitter halo = single("halo", 12, 2.6f);
        grow(halo.config, 0.2f);
        fadeOut(halo.config, VIOLET_PALE, VIOLET);
        material(halo, recolor(KilaPresets.SHOCKWAVE.create(), 0.66f, 0.42f, 0.97f, 1.4f));

        ParticleEmitter sparks = burstEmitter("sparks", 44, 50);
        ParticleConfig c = sparks.config;
        c.setStartLifetime(range(10, 18));
        c.setStartSpeed(range(3.5f, 7f));
        c.setStartSize(uniform(range(0.05f, 0.11f)));
        c.shape.setShape(sphere(0.3f));
        gravity(c, 0.2f);
        fadeOut(c, PALE, VIOLET);
        material(sparks, recolor(KilaPresets.SPARKS.create(), 0.45f, 0.85f, 0.9f, 1.6f));

        ParticleEmitter flash = single("flash", 9, 1.8f);
        fadeOut(flash.config, PALE, TEAL);
        shrink(flash.config);
        material(flash, glow(0.35f, 0.92f, 0.85f, 1.6f));

        ParticleEmitter rise = burstEmitter("rise", 20, 24);
        ParticleConfig r = rise.config;
        r.setStartLifetime(range(18, 28));
        r.setStartSpeed(NumberFunction.constant(0));
        r.setStartSize(uniform(range(0.04f, 0.07f)));
        Sphere around = sphere(0.6f);
        around.setRadiusThickness(0f);
        r.shape.setShape(around);
        r.velocityOverLifetime.setEnable(true);
        r.velocityOverLifetime.setLinear(new NumberFunction3(0, 1.4f, 0));
        fadeInOut(r, LIGHT, VIOLET);
        material(rise, glow(0.45f, 0.8f, 0.92f, 1.2f));
        return fx(ground, halo, sparks, flash, rise);
    }
}

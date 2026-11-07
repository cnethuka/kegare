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
 * DEV TOOL (exported with {@code /kegare debug fx export}): Swap's Photon effects, built from Photon's own objects (see
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
        out.put(SwapIds.FX_MOMENTUM_BURST, SwapFxAuthoring::momentumBurst);
        out.put(SwapIds.FX_TOTAL_SWAP_BURST, SwapFxAuthoring::totalSwapBurst);
        return out;
    }

    /**
     * Where someone left: a few tiny teal-white glints popping out of the body and gone in a few ticks. The afterimage and the
     * flicker ring are drawn by SwapFx; this is only the sparkle on them.
     */
    private static FX depart() {
        return fx(glints("glints", 9, 0.5f, 2.6f));
    }

    /** Where someone arrived: the same glints, fewer. */
    private static FX arrive() {
        return fx(glints("glints", 6, 0.45f, 2.2f));
    }

    /** {@code count} pin-point glints from a body-sized shell, thrown out at up to {@code speed}, 3 to 6 ticks long. */
    private static ParticleEmitter glints(String name, int count, float radius, float speed) {
        ParticleEmitter glints = burstEmitter(name, count, count + 2);
        ParticleConfig c = glints.config;
        c.setStartLifetime(range(3, 6));
        c.setStartSpeed(range(speed * 0.5f, speed));
        c.setStartSize(uniform(range(0.035f, 0.06f)));
        Sphere shell = sphere(radius);
        shell.setRadiusThickness(0.2f);
        c.shape.setShape(shell);
        c.shape.setScale(new NumberFunction3(1f, 1.8f, 1f));
        fadeOut(c, PALE, TEAL);
        shrink(c);
        material(glints, glow(0.35f, 0.95f, 0.86f, 1.6f));
        return glints;
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

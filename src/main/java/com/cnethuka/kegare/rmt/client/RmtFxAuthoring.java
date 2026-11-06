package com.cnethuka.kegare.rmt.client;

import static com.cnethuka.kegare.client.visual.FxKit.*;

import com.cnethuka.kegare.rmt.RmtIds;
import com.lowdragmc.photon.client.fx.FX;
import com.lowdragmc.photon.client.gameobject.emitter.data.number.NumberFunction;
import com.lowdragmc.photon.client.gameobject.emitter.data.number.NumberFunction3;
import com.lowdragmc.photon.client.gameobject.emitter.data.shape.Sphere;
import com.lowdragmc.photon.client.gameobject.emitter.data.material.kila.KilaPresets;
import com.lowdragmc.photon.client.gameobject.emitter.particle.ParticleConfig;
import com.lowdragmc.photon.client.gameobject.emitter.particle.ParticleEmitter;
import com.lowdragmc.photon.client.gameobject.emitter.particle.ParticleRendererSetting;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.resources.ResourceLocation;
import org.joml.Vector3f;

/**
 * DEV TOOL (exported with {@code /kegare debug fx export}): Reverse Malice Technique's Photon effects, built from Photon's own
 * objects (see {@link com.cnethuka.kegare.client.visual.FxKit}). Malice is negative energy, and RMT multiplies it by itself:
 * negative times negative is positive energy. So its palette is white and soft green; never Malice violet. Effects anchored
 * at the body sit at mid-body, so the feet are local y -0.9.
 */
public final class RmtFxAuthoring {
    private static final int WHITE = 0xFFFFFFFF;
    private static final int PALE = 0xFFC4F8D0;
    private static final int GREEN = 0xFF4ED873;
    private static final int DEEP = 0xFF22A046;

    private RmtFxAuthoring() {}

    public static Map<ResourceLocation, Supplier<FX>> effects() {
        Map<ResourceLocation, Supplier<FX>> out = new LinkedHashMap<>();
        out.put(RmtIds.FX_CHANNEL_AURA, RmtFxAuthoring::channelAura);
        out.put(RmtIds.FX_HEAL_PULSE, RmtFxAuthoring::healPulse);
        out.put(RmtIds.FX_ORB, RmtFxAuthoring::orb);
        out.put(RmtIds.FX_TRAIL_DOT, RmtFxAuthoring::trailDot);
        out.put(RmtIds.FX_UNLOCK_BURST, RmtFxAuthoring::unlockBurst);
        return out;
    }

    /** While healing: green and white motes rising round the body, white sparkles circling it, a white-hearted glow at the chest. */
    private static FX channelAura() {
        ParticleEmitter motes = emitter("motes", true, 20);
        ParticleConfig c = motes.config;
        c.setSimulationSpace(ParticleConfig.Space.World);
        c.setMaxParticles(90);
        c.setStartLifetime(range(16, 24));
        c.setStartSpeed(NumberFunction.constant(0));
        c.setStartSize(uniform(range(0.04f, 0.08f)));
        Sphere around = sphere(0.65f);
        around.setRadiusThickness(0f);
        c.shape.setShape(around);
        c.shape.setScale(new NumberFunction3(1f, 0f, 1f));
        c.emission.setEmissionRate(NumberFunction.constant(2.5f));
        c.velocityOverLifetime.setEnable(true);
        c.velocityOverLifetime.setLinear(new NumberFunction3(0, 1.25f, 0));
        fadeInOut(c, PALE, GREEN);
        shrink(c); // gone by the time they reach eye level: close to a first-person camera they'd be huge
        material(motes, glow(0.45f, 1f, 0.6f, 1.2f));
        motes.transform().localPosition(new Vector3f(0, -0.85f, 0));

        ParticleEmitter whiteMotes = emitter("white_motes", true, 20);
        ParticleConfig w = whiteMotes.config;
        w.setSimulationSpace(ParticleConfig.Space.World);
        w.setMaxParticles(50);
        w.setStartLifetime(range(14, 24));
        w.setStartSpeed(NumberFunction.constant(0));
        w.setStartSize(uniform(range(0.035f, 0.07f)));
        Sphere aroundWhite = sphere(0.55f);
        aroundWhite.setRadiusThickness(0f);
        w.shape.setShape(aroundWhite);
        w.shape.setScale(new NumberFunction3(1f, 0f, 1f));
        w.emission.setEmissionRate(NumberFunction.constant(1.4f));
        w.velocityOverLifetime.setEnable(true);
        w.velocityOverLifetime.setLinear(new NumberFunction3(0, 1.25f, 0));
        fadeInOut(w, WHITE, PALE);
        shrink(w);
        material(whiteMotes, glow(1f, 1f, 1f, 1.4f));
        whiteMotes.transform().localPosition(new Vector3f(0, -0.85f, 0));

        ParticleEmitter sparkles = emitter("sparkles", true, 20);
        ParticleConfig k = sparkles.config;
        k.setMaxParticles(30);
        k.setStartLifetime(range(14, 22));
        k.setStartSpeed(NumberFunction.constant(0));
        k.setStartSize(uniform(range(0.025f, 0.05f)));
        Sphere shell = sphere(0.55f);
        shell.setRadiusThickness(0f);
        k.shape.setShape(shell);
        k.shape.setScale(new NumberFunction3(1f, 0.6f, 1f));
        k.emission.setEmissionRate(NumberFunction.constant(1.1f));
        k.velocityOverLifetime.setEnable(true);
        k.velocityOverLifetime.setOrbital(new NumberFunction3(0, 2.2f, 0));
        fadeInOut(k, WHITE, PALE);
        material(sparkles, glow(1f, 1f, 1f, 1.6f));

        ParticleEmitter heart = emitter("heart", true, 20);
        ParticleConfig h = heart.config;
        h.setMaxParticles(2);
        h.setStartLifetime(NumberFunction.constant(20));
        h.setStartSpeed(NumberFunction.constant(0));
        h.setStartSize(uniform(NumberFunction.constant(0.55f)));
        h.shape.setScale(new NumberFunction3(0, 0, 0));
        h.emission.setEmissionRate(NumberFunction.constant(0));
        h.emission.getBursts().add(burst(0, 1));
        h.colorOverLifetime.setEnable(true);
        h.colorOverLifetime.setColor(gradient(0x224ED873, 0x554ED873, 0x224ED873));
        material(heart, glow(0.4f, 1f, 0.55f, 0.9f));
        heart.transform().localPosition(new Vector3f(0, 0.15f, 0));

        ParticleEmitter heartCore = emitter("heart_core", true, 20);
        ParticleConfig hc = heartCore.config;
        hc.setMaxParticles(2);
        hc.setStartLifetime(NumberFunction.constant(20));
        hc.setStartSpeed(NumberFunction.constant(0));
        hc.setStartSize(uniform(NumberFunction.constant(0.2f)));
        hc.shape.setScale(new NumberFunction3(0, 0, 0));
        hc.emission.setEmissionRate(NumberFunction.constant(0));
        hc.emission.getBursts().add(burst(0, 1));
        hc.colorOverLifetime.setEnable(true);
        hc.colorOverLifetime.setColor(gradient(0x22FFFFFF, 0x55FFFFFF, 0x22FFFFFF));
        material(heartCore, glow(1f, 1f, 1f, 0.8f));
        heartCore.transform().localPosition(new Vector3f(0, 0.15f, 0));
        return fx(motes, whiteMotes, sparkles, heart, heartCore);
    }

    /** Each heal: a green ring spreading on the ground with a white one inside it, a burst of white glints rising, a soft green flash with a white heart. */
    private static FX healPulse() {
        ParticleEmitter ring = single("ring", 12, 2.2f);
        ring.config.renderer.setRenderMode(ParticleRendererSetting.Mode.Horizontal);
        grow(ring.config, 0.25f);
        fadeOut(ring.config, PALE, DEEP);
        material(ring, recolor(KilaPresets.SHOCKWAVE.create(), 0.4f, 1f, 0.55f, 1.3f));
        ring.transform().localPosition(new Vector3f(0, -0.85f, 0));

        ParticleEmitter whiteRing = single("white_ring", 10, 1.4f);
        whiteRing.config.renderer.setRenderMode(ParticleRendererSetting.Mode.Horizontal);
        grow(whiteRing.config, 0.25f);
        fadeOut(whiteRing.config, WHITE, PALE);
        material(whiteRing, recolor(KilaPresets.SHOCKWAVE.create(), 1f, 1f, 1f, 1.5f));
        whiteRing.transform().localPosition(new Vector3f(0, -0.82f, 0));

        ParticleEmitter glints = burstEmitter("glints", 14, 16);
        ParticleConfig c = glints.config;
        c.setStartLifetime(range(8, 12));
        c.setStartSpeed(range(0.2f, 0.5f));
        c.setStartSize(uniform(range(0.03f, 0.06f)));
        c.shape.setShape(sphere(0.5f));
        c.velocityOverLifetime.setEnable(true);
        c.velocityOverLifetime.setLinear(new NumberFunction3(0, 0.7f, 0));
        fadeOut(c, WHITE, PALE);
        material(glints, glow(0.92f, 1f, 0.94f, 1.5f));

        ParticleEmitter flash = single("flash", 7, 0.7f);
        fadeOut(flash.config, PALE, GREEN);
        shrink(flash.config);
        material(flash, glow(0.5f, 1f, 0.6f, 1.1f));
        flash.transform().localPosition(new Vector3f(0, 0.1f, 0));

        ParticleEmitter whiteFlash = single("white_flash", 5, 0.28f);
        fadeOut(whiteFlash.config, 0xCCFFFFFF, 0x66FFFFFF);
        shrink(whiteFlash.config);
        material(whiteFlash, glow(1f, 1f, 1f, 1.1f));
        whiteFlash.transform().localPosition(new Vector3f(0, 0.1f, 0));
        return fx(ring, whiteRing, glints, flash, whiteFlash);
    }

    /**
     * The green orb: green with a white heart, breathing and drifting a little, in a soft green halo, with motes circling it and
     * sparks left hanging behind it as it travels. Loops until stopped (it fades out then).
     */
    private static FX orb() {
        ParticleEmitter body = emitter("body", true, 30);
        ParticleConfig b = body.config;
        b.setMaxParticles(2);
        b.setStartLifetime(NumberFunction.constant(30));
        b.setStartSpeed(NumberFunction.constant(0));
        b.setStartSize(uniform(NumberFunction.constant(0.34f)));
        b.shape.setScale(new NumberFunction3(0, 0, 0));
        b.emission.setEmissionRate(NumberFunction.constant(0));
        b.emission.getBursts().add(burst(0, 1));
        b.colorOverLifetime.setEnable(true);
        b.colorOverLifetime.setColor(gradient(0xBB4ED873, 0xEE6FE892, 0xBB4ED873));
        b.noise.setEnable(true);
        b.noise.setFrequency(0.4f);
        b.noise.setPosition(uniform(NumberFunction.constant(0.05f)));
        material(body, glow(0.25f, 1f, 0.4f, 1.0f));

        ParticleEmitter core = emitter("core", true, 30);
        ParticleConfig c = core.config;
        c.setMaxParticles(2);
        c.setStartLifetime(NumberFunction.constant(30));
        c.setStartSpeed(NumberFunction.constant(0));
        c.setStartSize(uniform(NumberFunction.constant(0.11f)));
        c.shape.setScale(new NumberFunction3(0, 0, 0));
        c.emission.setEmissionRate(NumberFunction.constant(0));
        c.emission.getBursts().add(burst(0, 1));
        c.colorOverLifetime.setEnable(true);
        c.colorOverLifetime.setColor(gradient(0xDDFFFFFF, 0xFFFFFFFF, 0xDDFFFFFF));
        c.noise.setEnable(true);
        c.noise.setFrequency(0.4f);
        c.noise.setPosition(uniform(NumberFunction.constant(0.05f)));
        material(core, glow(1f, 1f, 1f, 1.2f));

        ParticleEmitter halo = emitter("halo", true, 40);
        ParticleConfig h = halo.config;
        h.setMaxParticles(2);
        h.setStartLifetime(NumberFunction.constant(40));
        h.setStartSpeed(NumberFunction.constant(0));
        h.setStartSize(uniform(NumberFunction.constant(0.85f)));
        h.shape.setScale(new NumberFunction3(0, 0, 0));
        h.emission.setEmissionRate(NumberFunction.constant(0));
        h.emission.getBursts().add(burst(0, 1));
        h.colorOverLifetime.setEnable(true);
        h.colorOverLifetime.setColor(gradient(0x334ED873, 0x664ED873, 0x334ED873));
        material(halo, glow(0.35f, 1f, 0.5f, 0.9f));

        ParticleEmitter motes = emitter("motes", true, 20);
        ParticleConfig m = motes.config;
        m.setMaxParticles(40);
        m.setStartLifetime(range(16, 24));
        m.setStartSpeed(NumberFunction.constant(0));
        m.setStartSize(uniform(range(0.022f, 0.045f)));
        Sphere shell = sphere(0.34f);
        shell.setRadiusThickness(0f);
        m.shape.setShape(shell);
        m.emission.setEmissionRate(NumberFunction.constant(1.2f));
        m.velocityOverLifetime.setEnable(true);
        m.velocityOverLifetime.setOrbital(new NumberFunction3(0.6f, 2.0f, 0));
        fadeInOut(m, WHITE, GREEN);
        material(motes, glow(0.7f, 1f, 0.76f, 1.3f));

        ParticleEmitter sparks = emitter("sparks", true, 20);
        ParticleConfig p = sparks.config;
        p.setSimulationSpace(ParticleConfig.Space.World);
        p.setMaxParticles(40);
        p.setStartLifetime(range(14, 24));
        p.setStartSpeed(NumberFunction.constant(0));
        p.setStartSize(uniform(range(0.016f, 0.034f)));
        p.shape.setShape(sphere(0.08f));
        p.emission.setEmissionRate(NumberFunction.constant(0.8f));
        gravity(p, 0.03f);
        fadeOut(p, WHITE, GREEN);
        material(sparks, glow(1f, 1f, 1f, 1.4f));
        return fx(body, core, halo, motes, sparks);
    }

    /** One dot of the way ahead of the orb: small and faint, so it reads as a trail and not as more orbs. */
    private static FX trailDot() {
        ParticleEmitter dot = emitter("dot", true, 20);
        ParticleConfig c = dot.config;
        c.setMaxParticles(2);
        c.setStartLifetime(NumberFunction.constant(20));
        c.setStartSpeed(NumberFunction.constant(0));
        c.setStartSize(uniform(NumberFunction.constant(0.045f)));
        c.shape.setScale(new NumberFunction3(0, 0, 0));
        c.emission.setEmissionRate(NumberFunction.constant(0));
        c.emission.getBursts().add(burst(0, 1));
        c.colorOverLifetime.setEnable(true);
        c.colorOverLifetime.setColor(gradient(0x88C4F8D0, 0xBBE4FFEA, 0x88C4F8D0));
        material(dot, glow(0.65f, 1f, 0.72f, 0.9f));
        return fx(dot);
    }

    /** The orb taken in: a green flash with a white heart, a white ring inside a green one, and white and green glints bursting outward. */
    private static FX unlockBurst() {
        ParticleEmitter flash = single("flash", 8, 1.4f);
        fadeOut(flash.config, PALE, GREEN);
        shrink(flash.config);
        material(flash, glow(0.55f, 1f, 0.65f, 1.3f));

        ParticleEmitter whiteFlash = single("white_flash", 6, 0.5f);
        fadeOut(whiteFlash.config, 0xDDFFFFFF, 0x66FFFFFF);
        shrink(whiteFlash.config);
        material(whiteFlash, glow(1f, 1f, 1f, 1.2f));

        ParticleEmitter ring = single("ring", 12, 2.6f);
        grow(ring.config, 0.15f);
        fadeOut(ring.config, PALE, DEEP);
        material(ring, recolor(KilaPresets.SHOCKWAVE.create(), 0.4f, 1f, 0.55f, 1.4f));

        ParticleEmitter whiteRing = single("white_ring", 10, 1.7f);
        grow(whiteRing.config, 0.15f);
        fadeOut(whiteRing.config, 0x99FFFFFF, 0x33C4F8D0);
        material(whiteRing, recolor(KilaPresets.SHOCKWAVE.create(), 1f, 1f, 1f, 1.1f));

        ParticleEmitter glints = burstEmitter("glints", 30, 34);
        ParticleConfig c = glints.config;
        c.setStartLifetime(range(10, 18));
        c.setStartSpeed(range(3f, 6f));
        c.setStartSize(uniform(range(0.04f, 0.09f)));
        c.shape.setShape(sphere(0.2f));
        gravity(c, -0.2f);
        fadeOut(c, WHITE, GREEN);
        material(glints, recolor(KilaPresets.SPARKS.create(), 0.55f, 1f, 0.65f, 1.5f));

        ParticleEmitter whiteGlints = burstEmitter("white_glints", 18, 22);
        ParticleConfig w = whiteGlints.config;
        w.setStartLifetime(range(10, 18));
        w.setStartSpeed(range(2.5f, 5f));
        w.setStartSize(uniform(range(0.04f, 0.08f)));
        w.shape.setShape(sphere(0.2f));
        gravity(w, -0.2f);
        fadeOut(w, WHITE, PALE);
        material(whiteGlints, recolor(KilaPresets.SPARKS.create(), 1f, 1f, 1f, 1.6f));
        return fx(flash, whiteFlash, ring, whiteRing, glints, whiteGlints);
    }
}

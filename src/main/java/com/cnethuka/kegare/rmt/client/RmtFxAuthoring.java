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
 * DEV TOOL (exported with {@code /kegarefx export}): Reverse Malice Technique's Photon effects, built from Photon's own
 * objects (see {@link com.cnethuka.kegare.client.visual.FxKit}). Palette: soft green and white, the colors of
 * healing; never Malice violet. Effects anchored at the body sit at mid-body, so the feet are local y -0.9.
 */
public final class RmtFxAuthoring {
    private static final int WHITE = 0xFFF2FFF4;
    private static final int PALE = 0xFFA6F5B8;
    private static final int GREEN = 0xFF4ED873;
    private static final int DEEP = 0xFF22A046;

    private RmtFxAuthoring() {}

    public static Map<ResourceLocation, Supplier<FX>> effects() {
        Map<ResourceLocation, Supplier<FX>> out = new LinkedHashMap<>();
        out.put(RmtIds.FX_CHANNEL_AURA, RmtFxAuthoring::channelAura);
        out.put(RmtIds.FX_HEAL_PULSE, RmtFxAuthoring::healPulse);
        out.put(RmtIds.FX_ORB, RmtFxAuthoring::orb);
        out.put(RmtIds.FX_UNLOCK_BURST, RmtFxAuthoring::unlockBurst);
        return out;
    }

    /** While healing: green motes rising round the body, a few white sparkles circling it, a faint glow at the chest. */
    private static FX channelAura() {
        ParticleEmitter motes = emitter("motes", true, 20);
        ParticleConfig c = motes.config;
        c.setSimulationSpace(ParticleConfig.Space.World);
        c.setMaxParticles(90);
        c.setStartLifetime(range(16, 26));
        c.setStartSpeed(NumberFunction.constant(0));
        c.setStartSize(uniform(range(0.04f, 0.08f)));
        Sphere around = sphere(0.65f);
        around.setRadiusThickness(0f);
        c.shape.setShape(around);
        c.shape.setScale(new NumberFunction3(1f, 0f, 1f));
        c.emission.setEmissionRate(NumberFunction.constant(2.5f));
        c.velocityOverLifetime.setEnable(true);
        c.velocityOverLifetime.setLinear(new NumberFunction3(0, 1.3f, 0));
        fadeInOut(c, PALE, GREEN);
        material(motes, glow(0.45f, 1f, 0.6f, 1.2f));
        motes.transform().localPosition(new Vector3f(0, -0.85f, 0));

        ParticleEmitter sparkles = emitter("sparkles", true, 20);
        ParticleConfig k = sparkles.config;
        k.setMaxParticles(30);
        k.setStartLifetime(range(14, 22));
        k.setStartSpeed(NumberFunction.constant(0));
        k.setStartSize(uniform(range(0.025f, 0.05f)));
        Sphere shell = sphere(0.55f);
        shell.setRadiusThickness(0f);
        k.shape.setShape(shell);
        k.emission.setEmissionRate(NumberFunction.constant(0.8f));
        k.velocityOverLifetime.setEnable(true);
        k.velocityOverLifetime.setOrbital(new NumberFunction3(0, 2.2f, 0));
        fadeInOut(k, WHITE, PALE);
        material(sparkles, glow(0.9f, 1f, 0.92f, 1.3f));

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
        return fx(motes, sparkles, heart);
    }

    /** Each heal: a green ring spreading on the ground, a burst of white-green glints rising, a soft flash. */
    private static FX healPulse() {
        ParticleEmitter ring = single("ring", 12, 2.2f);
        ring.config.renderer.setRenderMode(ParticleRendererSetting.Mode.Horizontal);
        grow(ring.config, 0.25f);
        fadeOut(ring.config, PALE, DEEP);
        material(ring, recolor(KilaPresets.SHOCKWAVE.create(), 0.4f, 1f, 0.55f, 1.3f));
        ring.transform().localPosition(new Vector3f(0, -0.85f, 0));

        ParticleEmitter glints = burstEmitter("glints", 14, 16);
        ParticleConfig c = glints.config;
        c.setStartLifetime(range(10, 16));
        c.setStartSpeed(range(0.6f, 1.4f));
        c.setStartSize(uniform(range(0.04f, 0.08f)));
        c.shape.setShape(sphere(0.5f));
        c.velocityOverLifetime.setEnable(true);
        c.velocityOverLifetime.setLinear(new NumberFunction3(0, 2.2f, 0));
        fadeOut(c, WHITE, GREEN);
        material(glints, glow(0.6f, 1f, 0.7f, 1.4f));

        ParticleEmitter flash = single("flash", 7, 0.7f);
        fadeOut(flash.config, PALE, GREEN);
        shrink(flash.config);
        material(flash, glow(0.5f, 1f, 0.6f, 1.1f));
        flash.transform().localPosition(new Vector3f(0, 0.1f, 0));
        return fx(ring, glints, flash);
    }

    /**
     * The green orb: a glowing core that breathes and drifts a little, a soft halo, and motes circling it. Loops until
     * stopped (it fades out then).
     */
    private static FX orb() {
        ParticleEmitter core = emitter("core", true, 30);
        ParticleConfig c = core.config;
        c.setMaxParticles(2);
        c.setStartLifetime(NumberFunction.constant(30));
        c.setStartSpeed(NumberFunction.constant(0));
        c.setStartSize(uniform(NumberFunction.constant(0.42f)));
        c.shape.setScale(new NumberFunction3(0, 0, 0));
        c.emission.setEmissionRate(NumberFunction.constant(0));
        c.emission.getBursts().add(burst(0, 1));
        c.colorOverLifetime.setEnable(true);
        c.colorOverLifetime.setColor(gradient(0xCCF2FFF4, 0xFFA6F5B8, 0xCCF2FFF4));
        c.noise.setEnable(true);
        c.noise.setFrequency(0.4f);
        c.noise.setPosition(uniform(NumberFunction.constant(0.12f)));
        material(core, glow(0.5f, 1f, 0.62f, 1.7f));

        ParticleEmitter halo = emitter("halo", true, 40);
        ParticleConfig h = halo.config;
        h.setMaxParticles(2);
        h.setStartLifetime(NumberFunction.constant(40));
        h.setStartSpeed(NumberFunction.constant(0));
        h.setStartSize(uniform(NumberFunction.constant(1.1f)));
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
        m.setStartSize(uniform(range(0.03f, 0.06f)));
        Sphere shell = sphere(0.45f);
        shell.setRadiusThickness(0f);
        m.shape.setShape(shell);
        m.emission.setEmissionRate(NumberFunction.constant(1.2f));
        m.velocityOverLifetime.setEnable(true);
        m.velocityOverLifetime.setOrbital(new NumberFunction3(0.6f, 2.0f, 0));
        fadeInOut(m, WHITE, GREEN);
        material(motes, glow(0.6f, 1f, 0.7f, 1.3f));
        return fx(core, halo, motes);
    }

    /** The orb taken in: a bright green flash, a ring, and glints bursting outward. */
    private static FX unlockBurst() {
        ParticleEmitter flash = single("flash", 8, 1.4f);
        fadeOut(flash.config, WHITE, GREEN);
        shrink(flash.config);
        material(flash, glow(0.55f, 1f, 0.65f, 1.6f));

        ParticleEmitter ring = single("ring", 12, 2.6f);
        grow(ring.config, 0.15f);
        fadeOut(ring.config, PALE, DEEP);
        material(ring, recolor(KilaPresets.SHOCKWAVE.create(), 0.4f, 1f, 0.55f, 1.4f));

        ParticleEmitter glints = burstEmitter("glints", 30, 34);
        ParticleConfig c = glints.config;
        c.setStartLifetime(range(10, 18));
        c.setStartSpeed(range(3f, 6f));
        c.setStartSize(uniform(range(0.04f, 0.09f)));
        c.shape.setShape(sphere(0.2f));
        gravity(c, -0.2f);
        fadeOut(c, WHITE, GREEN);
        material(glints, recolor(KilaPresets.SPARKS.create(), 0.55f, 1f, 0.65f, 1.5f));
        return fx(flash, ring, glints);
    }
}

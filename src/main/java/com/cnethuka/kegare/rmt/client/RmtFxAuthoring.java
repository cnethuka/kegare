package com.cnethuka.kegare.rmt.client;

import static com.cnethuka.kegare.client.visual.FxKit.*;

import com.cnethuka.kegare.Kegare;
import com.cnethuka.kegare.rmt.RmtIds;
import com.lowdragmc.photon.client.fx.FX;
import com.lowdragmc.photon.client.gameobject.emitter.data.material.BlendMode;
import com.lowdragmc.photon.client.gameobject.emitter.data.number.curve.Curve;
import com.lowdragmc.photon.client.gameobject.emitter.data.number.curve.ECBCurves;
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
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * DEV TOOL (exported with {@code /kegare debug fx export}): Reverse Malice Technique's Photon effects, built from Photon's own
 * objects (see {@link com.cnethuka.kegare.client.visual.FxKit}). Malice is negative energy, and RMT multiplies it by itself:
 * negative times negative is positive energy. So healing is white light (with near-black scraps so it holds on a bright sky); the green
 * orb that teaches it is green with a white heart; never Malice violet. Effects anchored at the body sit at mid-body, so the feet are
 * local y -0.9.
 */
public final class RmtFxAuthoring {
    private static final int WHITE = 0xFFFFFFFF;
    private static final int SILVER = 0xFFDCE3EC;
    private static final int PALE = 0xFFC4F8D0;
    private static final int GREEN = 0xFF4ED873;
    private static final int DEEP = 0xFF22A046;

    private RmtFxAuthoring() {}

    public static Map<ResourceLocation, Supplier<FX>> effects() {
        Map<ResourceLocation, Supplier<FX>> out = new LinkedHashMap<>();
        out.put(RmtIds.FX_CHANNEL_AURA, () -> handLight(FIST, false));
        out.put(RmtIds.FX_CHANNEL_AURA_SELF, () -> handLight(FIST, true));
        out.put(RmtIds.FX_ON_ANOTHER, () -> handLight(HAND, false));
        out.put(RmtIds.FX_ON_ANOTHER_SELF, () -> handLight(HAND, true));
        out.put(RmtIds.FX_HEAL_PULSE, () -> healPulse(false));
        out.put(RmtIds.FX_HEAL_PULSE_SELF, () -> healPulse(true));
        out.put(RmtIds.FX_BACKGROUND_SHIMMER, RmtFxAuthoring::backgroundShimmer);
        out.put(RmtIds.FX_ORB, RmtFxAuthoring::orb);
        out.put(RmtIds.FX_TRAIL_DOT, RmtFxAuthoring::trailDot);
        out.put(RmtIds.FX_UNLOCK_BURST, RmtFxAuthoring::unlockBurst);
        return out;
    }

    /** Where the fist held to the chest is (the channel's pose, {@code player_animations/rmt.json}), from the body's middle: forward, up, right. */
    public static final Vec3 FIST = new Vec3(0.30, 0.45, -0.10);
    /**
     * Where the hand reaching out onto another is (healing another's pose, {@code rmt_on_another}), the same way: the arm a little below
     * level and turned a little in, the light on the hand just short of its tip, where it touches them.
     */
    public static final Vec3 HAND = new Vec3(0.50, 0.34, 0.19);
    private static final ResourceLocation LICKS = Kegare.id("textures/fx/rmt_licks.png");
    /** Near-black: the scraps rising off the light, so its shape holds against a bright sky. */
    private static final float[] INK = {0.047f, 0.035f, 0.063f};

    /**
     * RMT's light on a hand at {@code at} (the fist held to the chest while it channels, the hand laid on another while it heals them): white flame licks (our pixel flames, {@code textures/fx/rmt_licks.png}, played as a
     * sprite sheet with hard edges) burning off it as added light, bright enough to bloom, over a soft glow; and near-black scraps of the same
     * flames rising off the edge and drifting away. The body hides what's behind it. Turns with the body ({@code RmtClient}).
     * {@code self}, for your own first person: a few small licks that don't bloom, so it never fills the view.
     */
    private static FX handLight(Vec3 at, boolean self) {
        Vector3f fist = new Vector3f((float) at.x, (float) at.y, (float) at.z);
        ParticleEmitter licks = emitter("licks", true, 20);
        ParticleConfig c = licks.config;
        c.setSimulationSpace(ParticleConfig.Space.Local);
        c.setMaxParticles(48);
        c.setStartLifetime(range(8, 12));
        c.setStartSpeed(range(0f, 0.3f));
        c.setStartSize(uniform(range(0.1f, 0.18f)));
        c.shape.setShape(sphere(0.1f));
        c.emission.setEmissionRate(NumberFunction.constant(2.8f));
        c.velocityOverLifetime.setEnable(true);
        c.velocityOverLifetime.setLinear(new NumberFunction3(0, 0.25f, 0));
        flare(c);
        fadeInOut(c, 0xD8FFFFFF, WHITE);
        spriteSheet(licks, LICKS, 8, 2, BlendMode.Preset.ADDITIVE, 1f, 1f, 1f, 1.05f);
        licks.transform().localPosition(fist);
        if (self) {
            c.setMaxParticles(8);
            c.setStartLifetime(range(6, 9));
            c.setStartSize(uniform(range(0.045f, 0.075f)));
            c.emission.setEmissionRate(NumberFunction.constant(0.8f));
            spriteSheet(licks, LICKS, 8, 2, BlendMode.Preset.ADDITIVE, 1f, 1f, 1f, 0.85f);
            return fx(licks);
        }

        ParticleEmitter glow = emitter("glow", true, 20);
        ParticleConfig g = glow.config;
        g.setMaxParticles(2);
        g.setStartLifetime(NumberFunction.constant(20));
        g.setStartSpeed(NumberFunction.constant(0));
        g.setStartSize(uniform(NumberFunction.constant(0.42f)));
        g.shape.setScale(new NumberFunction3(0, 0, 0));
        g.emission.setEmissionRate(NumberFunction.constant(0));
        g.emission.getBursts().add(burst(0, 1));
        g.colorOverLifetime.setEnable(true);
        g.colorOverLifetime.setColor(gradient(0x30FFFFFF, 0x50FFFFFF, 0x30FFFFFF));
        material(glow, glow(1f, 1f, 1f, 1.0f));
        glow.transform().localPosition(fist);

        ParticleEmitter scraps = emitter("scraps", true, 20);
        ParticleConfig k = scraps.config;
        k.setSimulationSpace(ParticleConfig.Space.World);
        k.setMaxParticles(36);
        k.setStartLifetime(range(10, 15));
        k.setStartSpeed(NumberFunction.constant(0));
        k.setStartSize(uniform(range(0.05f, 0.09f)));
        Sphere edge = sphere(0.16f);
        edge.setRadiusThickness(0f);
        k.shape.setShape(edge);
        k.emission.setEmissionRate(NumberFunction.constant(1.8f));
        k.velocityOverLifetime.setEnable(true);
        k.velocityOverLifetime.setLinear(new NumberFunction3(0, 0.45f, 0));
        shrink(k);
        k.colorOverLifetime.setEnable(true);
        k.colorOverLifetime.setColor(gradient(0xFF0C0910, 0xFF0C0910, 0x000C0910));
        spriteSheet(scraps, LICKS, 8, 2, BlendMode.Preset.ALPHA, INK[0], INK[1], INK[2], 1f);
        // Behind the fist, level with the face: the head hides the ones that rise over it, so they show round the silhouette.
        scraps.transform().localPosition(new Vector3f(fist).add(-0.14f, 0.04f, 0f));
        return fx(licks, glow, scraps);
    }

    /**
     * Background RMT, on anyone running it, for everyone to see: the channel's white licks, small, few and faint, coming and going on a shell just outside the body and
     * drifting up a little, with no glow behind them and no dark scraps. Quiet, since it's only ever a hint.
     */
    private static FX backgroundShimmer() {
        ParticleEmitter licks = emitter("licks", true, 20);
        ParticleConfig c = licks.config;
        c.setSimulationSpace(ParticleConfig.Space.World);
        c.setMaxParticles(20);
        c.setStartLifetime(range(12, 20));
        c.setStartSpeed(NumberFunction.constant(0));
        c.setStartSize(uniform(range(0.08f, 0.13f)));
        // On a shell just outside the body (the body hides what's inside it), from the feet to the top of the head.
        Sphere shell = sphere(0.5f);
        shell.setRadiusThickness(0f);
        c.shape.setShape(shell);
        c.shape.setScale(new NumberFunction3(1.1f, 1.85f, 1.1f));
        c.emission.setEmissionRate(NumberFunction.constant(0.7f));
        c.velocityOverLifetime.setEnable(true);
        c.velocityOverLifetime.setLinear(new NumberFunction3(0, 0.25f, 0));
        flare(c);
        fadeInOut(c, 0xC0FFFFFF, WHITE);
        spriteSheet(licks, LICKS, 8, 2, BlendMode.Preset.ADDITIVE, 1f, 1f, 1f, 0.95f);
        return fx(licks);
    }

    /** Swells from 70% to full in the first third of its life, then dies away to nothing. */
    private static void flare(ParticleConfig c) {
        c.sizeOverLifetime.setEnable(true);
        c.sizeOverLifetime.setSize(uniform(new Curve(0, 1, 0, 1, "t", "v", new ECBCurves(
                0f, 0.7f, 0.1f, 0.9f, 0.2f, 1f, 0.3f, 1f,
                0.3f, 1f, 0.6f, 0.95f, 0.85f, 0.4f, 1f, 0f))));
    }

    /**
     * Each heal: a white ring spreading on the ground with a brighter one inside it, a burst of white glints rising, a soft white flash.
     * {@code self}, for your own first person: only the two rings on the ground (the rest is at the chest).
     */
    private static FX healPulse(boolean self) {
        ParticleEmitter ring = single("ring", 12, 2.2f);
        ring.config.renderer.setRenderMode(ParticleRendererSetting.Mode.Horizontal);
        grow(ring.config, 0.25f);
        fadeOut(ring.config, SILVER, SILVER);
        material(ring, recolor(KilaPresets.SHOCKWAVE.create(), 0.9f, 0.93f, 0.97f, 1.3f));
        ring.transform().localPosition(new Vector3f(0, -0.85f, 0));

        ParticleEmitter whiteRing = single("white_ring", 10, 1.4f);
        whiteRing.config.renderer.setRenderMode(ParticleRendererSetting.Mode.Horizontal);
        grow(whiteRing.config, 0.25f);
        fadeOut(whiteRing.config, WHITE, SILVER);
        material(whiteRing, recolor(KilaPresets.SHOCKWAVE.create(), 1f, 1f, 1f, 1.5f));
        whiteRing.transform().localPosition(new Vector3f(0, -0.82f, 0));
        if (self) return fx(ring, whiteRing);

        ParticleEmitter glints = burstEmitter("glints", 14, 16);
        ParticleConfig c = glints.config;
        c.setStartLifetime(range(8, 12));
        c.setStartSpeed(range(0.2f, 0.5f));
        c.setStartSize(uniform(range(0.03f, 0.06f)));
        c.shape.setShape(sphere(0.5f));
        c.velocityOverLifetime.setEnable(true);
        c.velocityOverLifetime.setLinear(new NumberFunction3(0, 0.7f, 0));
        fadeOut(c, WHITE, SILVER);
        material(glints, glow(1f, 1f, 1f, 1.5f));

        ParticleEmitter flash = single("flash", 7, 0.7f);
        fadeOut(flash.config, 0xAAFFFFFF, SILVER);
        shrink(flash.config);
        material(flash, glow(0.92f, 0.95f, 1f, 1.1f));
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

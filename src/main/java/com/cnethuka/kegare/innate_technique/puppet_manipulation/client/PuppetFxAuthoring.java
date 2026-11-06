package com.cnethuka.kegare.innate_technique.puppet_manipulation.client;

import static com.cnethuka.kegare.client.visual.FxKit.*;

import com.cnethuka.kegare.client.visual.FxKit;
import com.cnethuka.kegare.innate_technique.puppet_manipulation.PuppetIds;
import com.lowdragmc.photon.client.fx.FX;
import com.lowdragmc.photon.client.gameobject.emitter.data.material.kila.KilaMaterial;
import com.lowdragmc.photon.client.gameobject.emitter.data.material.kila.KilaPresets;
import com.lowdragmc.photon.client.gameobject.emitter.data.number.NumberFunction;
import com.lowdragmc.photon.client.gameobject.emitter.data.number.NumberFunction3;
import com.lowdragmc.photon.client.gameobject.emitter.data.shape.Sphere;
import com.lowdragmc.photon.client.gameobject.emitter.particle.ParticleConfig;
import com.lowdragmc.photon.client.gameobject.emitter.particle.ParticleEmitter;
import com.lowdragmc.photon.client.gameobject.emitter.particle.ParticleRendererSetting;
import com.lowdragmc.lowdraglib2.math.HDRColor;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.resources.ResourceLocation;
import org.joml.Vector3f;

/**
 * DEV TOOL (exported with {@code /kegare debug fx export}): Puppet Manipulation's Photon effects, built from Photon's own
 * objects (see {@link FxKit}). Palette: lavender (the Malice color, #C4B5FD) from near-white to deep violet; threads
 * and seams are the motif. Body-anchored effects sit at mid-body: a half-size puppet's feet are local y -0.45, a
 * full-size body's -0.9.
 */
public final class PuppetFxAuthoring {
    private static final int PALE = 0xFFF5F3FF;
    private static final int LIGHT = 0xFFDDD6FE;
    private static final int LAV = 0xFFC4B5FD;
    private static final int MID = 0xFFA78BFA;
    private static final int DEEP = 0xFF7C3AED;

    private PuppetFxAuthoring() {}

    public static Map<ResourceLocation, Supplier<FX>> effects() {
        Map<ResourceLocation, Supplier<FX>> out = new LinkedHashMap<>();
        out.put(PuppetIds.FX_PUPPET_FORM, PuppetFxAuthoring::puppetForm);
        out.put(PuppetIds.FX_PUPPET_DISSOLVE, PuppetFxAuthoring::puppetDissolve);
        out.put(PuppetIds.FX_POSSESS_LINK, PuppetFxAuthoring::possessLink);
        out.put(PuppetIds.FX_DIRECTIVE_PULSE, PuppetFxAuthoring::directivePulse);
        out.put(PuppetIds.FX_FOCUS_MARK, PuppetFxAuthoring::focusMark);
        out.put(PuppetIds.FX_CURIO_BURST_EXPLOSION_TAP, () -> curioBurst(0.7f));
        out.put(PuppetIds.FX_CURIO_BURST_EXPLOSION_HALF, () -> curioBurst(1.0f));
        out.put(PuppetIds.FX_CURIO_BURST_EXPLOSION_FULL, () -> curioBurst(1.45f));
        out.put(PuppetIds.FX_STAND_IN_GROW, PuppetFxAuthoring::standInGrow);
        out.put(PuppetIds.FX_UNDERSTUDY_SWAP, PuppetFxAuthoring::understudySwap);
        out.put(PuppetIds.FX_STRINGS_ATTACH, PuppetFxAuthoring::stringsAttach);
        out.put(PuppetIds.FX_STRINGS_SNAP, PuppetFxAuthoring::stringsSnap);
        out.put(PuppetIds.FX_STRINGS_TENSION, PuppetFxAuthoring::stringsTension);
        return out;
    }

    private static KilaMaterial lavGlow(float intensity) {
        return glow(0.77f, 0.71f, 0.99f, intensity);
    }

    /** Threads weaving the puppet together: motes drawn in from a shell around it, a seam ring at its feet, a soft flash. */
    private static FX puppetForm() {
        ParticleEmitter threads = burstEmitter("threads", 30, 34);
        ParticleConfig c = threads.config;
        c.setStartLifetime(range(8, 12));
        c.setStartSpeed(range(-2.2f, -1.4f)); // inward
        c.setStartSize(uniform(range(0.03f, 0.06f)));
        Sphere shell = sphere(0.75f);
        shell.setRadiusThickness(0f);
        c.shape.setShape(shell);
        c.shape.setScale(new NumberFunction3(0.8f, 1.2f, 0.8f));
        c.velocityOverLifetime.setEnable(true);
        c.velocityOverLifetime.setOrbital(new NumberFunction3(0, 4f, 0)); // wound round, like thread on a spool
        fadeIn(c, MID, PALE);
        material(threads, lavGlow(1.4f));

        ParticleEmitter flash = single("flash", 7, 0.8f);
        flash.config.emission.getBursts().clear();
        flash.config.emission.getBursts().add(burst(7, 1)); // once the threads have closed in
        fadeOut(flash.config, LIGHT, LAV);
        shrink(flash.config);
        material(flash, lavGlow(1.0f));

        ParticleEmitter seam = single("seam", 12, 1.3f);
        seam.config.renderer.setRenderMode(ParticleRendererSetting.Mode.Horizontal);
        grow(seam.config, 0.2f);
        fadeOut(seam.config, LIGHT, DEEP);
        material(seam, recolor(KilaPresets.SHOCKWAVE.create(), 0.77f, 0.71f, 0.99f, 1.2f));
        seam.transform().localPosition(new Vector3f(0, -0.42f, 0));
        return fx(threads, flash, seam);
    }

    /** Coming apart at the seams: threads drift up and away, a few scraps fall, a quick flash. */
    private static FX puppetDissolve() {
        ParticleEmitter threads = burstEmitter("threads", 36, 40);
        ParticleConfig c = threads.config;
        c.setStartLifetime(range(14, 24));
        c.setStartSpeed(range(0.6f, 1.6f));
        c.setStartSize(uniform(range(0.04f, 0.09f)));
        c.shape.setShape(sphere(0.35f));
        c.shape.setScale(new NumberFunction3(1f, 1.6f, 1f));
        c.velocityOverLifetime.setEnable(true);
        c.velocityOverLifetime.setLinear(new NumberFunction3(0, 0.9f, 0));
        fadeOut(c, LAV, 0x007C3AED);
        material(threads, lavGlow(1.3f));

        ParticleEmitter scraps = burstEmitter("scraps", 10, 12);
        ParticleConfig s = scraps.config;
        s.setStartLifetime(range(16, 24));
        s.setStartSpeed(range(1.5f, 2.6f));
        s.setStartSize(uniform(range(0.08f, 0.14f)));
        s.shape.setShape(sphere(0.25f));
        gravity(s, 0.6f);
        fadeOut(s, LIGHT, MID);
        material(scraps, glow(0.65f, 0.55f, 0.98f, 0.9f));

        ParticleEmitter flash = single("flash", 5, 0.7f);
        fadeOut(flash.config, LAV, MID);
        shrink(flash.config);
        material(flash, lavGlow(0.9f));
        return fx(threads, scraps, flash);
    }

    /** One bead of the thread from a possessed body to its puppet (the client strings beads into a slack line). */
    private static FX possessLink() {
        ParticleEmitter bead = single("bead", 8, 0.14f);
        fadeOut(bead.config, PALE, LAV);
        shrink(bead.config);
        material(bead, lavGlow(1.6f));

        ParticleEmitter fuzz = burstEmitter("fuzz", 2, 3);
        ParticleConfig c = fuzz.config;
        c.setStartLifetime(range(5, 9));
        c.setStartSpeed(range(0.05f, 0.25f));
        c.setStartSize(uniform(range(0.02f, 0.035f)));
        c.shape.setShape(sphere(0.06f));
        fadeOut(c, LIGHT, 0x00A78BFA);
        material(fuzz, glow(0.65f, 0.55f, 0.98f, 1.1f));
        return fx(bead, fuzz);
    }

    /** An order given: a lavender ring spreads along the ground, an inner one after it, motes rising from its edge. */
    private static FX directivePulse() {
        ParticleEmitter outer = single("outer", 14, 3.2f);
        outer.config.renderer.setRenderMode(ParticleRendererSetting.Mode.Horizontal);
        grow(outer.config, 0.08f);
        fadeOut(outer.config, LIGHT, DEEP);
        material(outer, recolor(KilaPresets.SHOCKWAVE.create(), 0.77f, 0.71f, 0.99f, 1.3f));
        outer.transform().localPosition(new Vector3f(0, 0.05f, 0));

        ParticleEmitter inner = single("inner", 10, 1.6f);
        inner.config.emission.getBursts().clear();
        inner.config.emission.getBursts().add(burst(3, 1));
        inner.config.renderer.setRenderMode(ParticleRendererSetting.Mode.Horizontal);
        grow(inner.config, 0.1f);
        fadeOut(inner.config, PALE, MID);
        material(inner, recolor(KilaPresets.SHOCKWAVE.create(), 0.96f, 0.95f, 1f, 1.1f));
        inner.transform().localPosition(new Vector3f(0, 0.06f, 0));

        ParticleEmitter motes = burstEmitter("motes", 14, 16);
        ParticleConfig c = motes.config;
        c.setStartLifetime(range(10, 16));
        c.setStartSpeed(NumberFunction.constant(0));
        c.setStartSize(uniform(range(0.03f, 0.06f)));
        Sphere edge = sphere(0.9f);
        edge.setRadiusThickness(0f);
        c.shape.setShape(edge);
        c.shape.setScale(new NumberFunction3(1f, 0f, 1f));
        c.velocityOverLifetime.setEnable(true);
        c.velocityOverLifetime.setLinear(new NumberFunction3(0, 1.1f, 0));
        fadeOut(c, LAV, 0x00A78BFA);
        material(motes, lavGlow(1.2f));
        motes.transform().localPosition(new Vector3f(0, 0.1f, 0));
        return fx(outer, inner, motes);
    }

    /** Focus's mark on the target, two seconds: a ring tightening round the waist, a bead hanging over the head, sparks. */
    private static FX focusMark() {
        ParticleEmitter ring = single("ring", 40, 2.4f);
        ring.config.renderer.setRenderMode(ParticleRendererSetting.Mode.Horizontal);
        ring.config.sizeOverLifetime.setEnable(true);
        ring.config.sizeOverLifetime.setSize(uniform(curve(1f, 0.45f)));
        ring.config.colorOverLifetime.setEnable(true);
        ring.config.colorOverLifetime.setColor(gradient(0x00C4B5FD, 0xFFDDD6FE, 0xFFA78BFA, 0x00A78BFA));
        material(ring, recolor(KilaPresets.SHOCKWAVE.create(), 0.65f, 0.55f, 0.98f, 1.5f));

        ParticleEmitter pin = single("pin", 40, 0.32f);
        fadeInOut(pin.config, PALE, MID);
        material(pin, lavGlow(1.8f));
        pin.transform().localPosition(new Vector3f(0, 1.35f, 0));

        ParticleEmitter sparks = burstEmitter("sparks", 12, 14);
        ParticleConfig c = sparks.config;
        c.setStartLifetime(range(6, 10));
        c.setStartSpeed(range(1.5f, 3f));
        c.setStartSize(uniform(range(0.04f, 0.08f)));
        c.shape.setShape(sphere(0.3f));
        fadeOut(c, PALE, MID);
        material(sparks, recolor(KilaPresets.SPARKS.create(), 0.77f, 0.71f, 0.99f, 1.5f));
        return fx(ring, pin, sparks);
    }

    /** A curio popping: a hard flash, a ground ring, sparks, torn threads falling, a lavender puff. Scaled per tier. */
    private static FX curioBurst(float scale) {
        ParticleEmitter flash = single("flash", 6, 1.1f * scale);
        fadeOut(flash.config, LIGHT, MID);
        shrink(flash.config);
        material(flash, lavGlow(1.15f));

        ParticleEmitter ring = single("ring", 10, 3.0f * scale);
        ring.config.renderer.setRenderMode(ParticleRendererSetting.Mode.Horizontal);
        grow(ring.config, 0.1f);
        fadeOut(ring.config, LIGHT, DEEP);
        material(ring, recolor(KilaPresets.SHOCKWAVE.create(), 0.77f, 0.71f, 0.99f, 1.5f));
        ring.transform().localPosition(new Vector3f(0, -0.4f, 0));

        ParticleEmitter sparks = burstEmitter("sparks", Math.round(20 * scale), Math.round(24 * scale));
        ParticleConfig c = sparks.config;
        c.setStartLifetime(range(6, 12));
        c.setStartSpeed(range(3f * scale, 6f * scale));
        c.setStartSize(uniform(range(0.06f, 0.12f)));
        c.shape.setShape(sphere(0.3f));
        fadeOut(c, PALE, MID);
        material(sparks, recolor(KilaPresets.SPARKS.create(), 0.77f, 0.71f, 0.99f, 1.7f));

        ParticleEmitter threads = burstEmitter("threads", Math.round(10 * scale), Math.round(12 * scale));
        ParticleConfig t = threads.config;
        t.setStartLifetime(range(12, 20));
        t.setStartSpeed(range(1.5f * scale, 3f * scale));
        t.setStartSize(uniform(range(0.06f, 0.11f)));
        t.shape.setShape(sphere(0.2f));
        gravity(t, 0.5f);
        fadeOut(t, LAV, DEEP);
        material(threads, glow(0.65f, 0.55f, 0.98f, 1.0f));

        ParticleEmitter puff = burstEmitter("puff", Math.round(6 * scale), Math.round(8 * scale));
        ParticleConfig p = puff.config;
        p.setStartLifetime(range(16, 26));
        p.setStartSpeed(range(0.4f, 1.0f));
        p.setStartSize(uniform(range(0.5f * scale, 0.9f * scale)));
        p.shape.setShape(sphere(0.4f * scale));
        fadeOut(p, 0x99C4B5FD, 0x004C1D95);
        KilaMaterial smoke = KilaPresets.SMOKE.create();
        smoke.color = new HDRColor(0.72f, 0.66f, 0.9f, 0.6f, 1f);
        smoke.worldLight = false;
        material(puff, smoke);
        return fx(flash, ring, sparks, threads, puff);
    }

    /** A puppet growing into its owner's size: a column of lavender motes rising round it, a ring at its feet, a flash. */
    private static FX standInGrow() {
        ParticleEmitter column = burstEmitter("column", 44, 48);
        ParticleConfig c = column.config;
        c.setStartLifetime(range(10, 16));
        c.setStartSpeed(NumberFunction.constant(0));
        c.setStartSize(uniform(range(0.04f, 0.08f)));
        Sphere around = sphere(0.55f);
        around.setRadiusThickness(0f);
        c.shape.setShape(around);
        c.shape.setScale(new NumberFunction3(1f, 1.9f, 1f));
        c.velocityOverLifetime.setEnable(true);
        c.velocityOverLifetime.setLinear(new NumberFunction3(0, 1.3f, 0));
        c.velocityOverLifetime.setOrbital(new NumberFunction3(0, 2.5f, 0));
        fadeOut(c, PALE, 0x007C3AED);
        material(column, lavGlow(1.4f));

        ParticleEmitter ring = single("ring", 12, 2.0f);
        ring.config.renderer.setRenderMode(ParticleRendererSetting.Mode.Horizontal);
        grow(ring.config, 0.2f);
        fadeOut(ring.config, LIGHT, DEEP);
        material(ring, recolor(KilaPresets.SHOCKWAVE.create(), 0.77f, 0.71f, 0.99f, 1.3f));
        ring.transform().localPosition(new Vector3f(0, -0.88f, 0));

        ParticleEmitter flash = single("flash", 8, 2.2f);
        fadeOut(flash.config, 0x88F5F3FF, 0x00C4B5FD);
        shrink(flash.config);
        material(flash, lavGlow(1.0f));
        return fx(column, ring, flash);
    }

    /** A knot grips: fibers kicked out from the spot, and a quick pinch of light. */
    private static FX stringsAttach() {
        ParticleEmitter fibers = burstEmitter("fibers", 12, 14);
        ParticleConfig c = fibers.config;
        c.setStartLifetime(range(5, 9));
        c.setStartSpeed(range(1.2f, 2.6f));
        c.setStartSize(uniform(range(0.03f, 0.055f)));
        c.shape.setShape(sphere(0.08f));
        fadeOut(c, PALE, 0x00A78BFA);
        material(fibers, lavGlow(1.5f));

        ParticleEmitter pinch = single("pinch", 5, 0.55f);
        fadeOut(pinch.config, LIGHT, 0x00C4B5FD);
        shrink(pinch.config);
        material(pinch, lavGlow(1.1f));
        return fx(fibers, pinch);
    }

    /** A string gives way: frayed fibers flung out, curling down under their own weight. */
    private static FX stringsSnap() {
        ParticleEmitter fibers = burstEmitter("fibers", 22, 26);
        ParticleConfig c = fibers.config;
        c.setStartLifetime(range(10, 18));
        c.setStartSpeed(range(1.8f, 4.6f));
        c.setStartSize(uniform(range(0.025f, 0.05f)));
        c.shape.setShape(sphere(0.12f));
        fadeOut(c, LIGHT, 0x007C3AED);
        gravity(c, 0.6f);
        material(fibers, lavGlow(1.3f));

        ParticleEmitter flick = single("flick", 4, 0.4f);
        fadeOut(flick.config, PALE, 0x00C4B5FD);
        material(flick, lavGlow(1.2f));
        return fx(fibers, flick);
    }

    /** Under tension: a faint glint on the cord, fading almost at once. Spawned often and along the string, so keep it small. */
    private static FX stringsTension() {
        ParticleEmitter glint = burstEmitter("glint", 2, 3);
        ParticleConfig c = glint.config;
        c.setStartLifetime(range(3, 6));
        c.setStartSpeed(range(0.02f, 0.12f));
        c.setStartSize(uniform(range(0.03f, 0.05f)));
        c.shape.setShape(sphere(0.03f));
        fadeOut(c, PALE, 0x00A78BFA);
        material(glint, lavGlow(1.2f));
        return fx(glint);
    }

    /** Owner and puppet trade places: motes drawn in to each spot, a thin vertical seam, a flash. */
    private static FX understudySwap() {
        ParticleEmitter pull = burstEmitter("pull", 24, 28);
        ParticleConfig c = pull.config;
        c.setStartLifetime(range(6, 9));
        c.setStartSpeed(range(-3.2f, -2.2f));
        c.setStartSize(uniform(range(0.04f, 0.08f)));
        Sphere shell = sphere(0.9f);
        shell.setRadiusThickness(0f);
        c.shape.setShape(shell);
        c.shape.setScale(new NumberFunction3(0.8f, 1.2f, 0.8f));
        fadeIn(c, MID, PALE);
        material(pull, lavGlow(1.4f));

        ParticleEmitter seam = burstEmitter("seam", 14, 16);
        ParticleConfig s = seam.config;
        s.setStartLifetime(range(5, 8));
        s.setStartSpeed(NumberFunction.constant(0));
        s.setStartSize(uniform(range(0.05f, 0.09f)));
        s.shape.setShape(sphere(0.1f));
        s.shape.setScale(new NumberFunction3(0.3f, 9f, 0.3f));
        fadeOut(s, PALE, LAV);
        material(seam, lavGlow(1.6f));

        ParticleEmitter flash = single("flash", 5, 0.9f);
        fadeOut(flash.config, LIGHT, LAV);
        shrink(flash.config);
        material(flash, lavGlow(1.0f));
        return fx(pull, seam, flash);
    }
}

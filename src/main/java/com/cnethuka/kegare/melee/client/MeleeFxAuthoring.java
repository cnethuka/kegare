package com.cnethuka.kegare.melee.client;

import static com.cnethuka.kegare.client.visual.FxKit.*;

import com.cnethuka.kegare.Kegare;
import com.cnethuka.kegare.melee.MeleeIds;
import com.lowdragmc.lowdraglib2.math.HDRColor;
import com.lowdragmc.photon.client.fx.FX;
import com.lowdragmc.photon.client.gameobject.emitter.data.material.kila.KilaMaterial;
import com.lowdragmc.photon.client.gameobject.emitter.data.material.kila.KilaPresets;
import com.lowdragmc.photon.client.gameobject.emitter.data.model.ObjModelSource;
import com.lowdragmc.photon.client.gameobject.emitter.data.number.NumberFunction;
import com.lowdragmc.photon.client.gameobject.emitter.data.number.NumberFunction3;
import com.lowdragmc.photon.client.gameobject.emitter.data.shape.MeshData;
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
 * DEV TOOL ({@code /kegare debug fx export}). The shared melee layer's effects.
 * Dash is air and pale white (Kinetic Manipulation), Parry is white on gold (Reinforcement). A ready fist and its Strike are the
 * exception, authored white and tinted at runtime with the Malice color the server says to show. All short and small: they say what
 * happened, at a glance, and get out of the way.
 */
public final class MeleeFxAuthoring {
    private static final int WHITE = 0xFFFFFFFF;
    private static final int AIR = 0xFFD8EAF5;

    private MeleeFxAuthoring() {}

    public static Map<ResourceLocation, Supplier<FX>> effects() {
        Map<ResourceLocation, Supplier<FX>> out = new LinkedHashMap<>();
        out.put(MeleeIds.FX_DASH_BURST, MeleeFxAuthoring::dashBurst);
        out.put(MeleeIds.FX_DASH_TRAIL, MeleeFxAuthoring::dashTrail);
        out.put(MeleeIds.FX_DASH_TRAIL_SELF, MeleeFxAuthoring::dashTrailSelf);
        out.put(MeleeIds.FX_PARRY_SPARK, MeleeFxAuthoring::parrySpark);
        out.put(MeleeIds.FX_PARRY_SPARK_SELF, MeleeFxAuthoring::parrySparkSelf);
        out.put(MeleeIds.FX_GUARD_HIT, MeleeFxAuthoring::guardHit);
        out.put(MeleeIds.FX_GUARD_HIT_SELF, MeleeFxAuthoring::guardHitSelf);
        out.put(MeleeIds.FX_STRIKE_IMPACT, MeleeFxAuthoring::strikeImpact);
        out.put(MeleeIds.FX_STRIKE_BURST, MeleeFxAuthoring::strikeBurst);
        return out;
    }

    /** Sparks out from a point, in every direction. */
    private static ParticleEmitter sparks(String name, int count, float speedMin, float speedMax, int lifeMin, int lifeMax, float size, float stretch, int from, int to, float r, float g, float b, float glow) {
        ParticleEmitter e = burstEmitter(name, count, count);
        ParticleConfig c = e.config;
        c.setSimulationSpace(ParticleConfig.Space.World);
        c.setStartLifetime(range(lifeMin, lifeMax));
        c.setStartSpeed(range(speedMin, speedMax));
        c.setStartSize(uniform(range(size * 0.7f, size)));
        c.shape.setShape(sphere(0.08f));
        c.renderer.setRenderMode(ParticleRendererSetting.Mode.StretchedBillboard);
        c.renderer.setLengthScale(stretch);
        fadeOut(c, from, to);
        material(e, glow(r, g, b, glow));
        return e;
    }

    /** One soft flash at a point, gone in a few ticks. */
    private static ParticleEmitter flash(String name, int life, float size, int from, int to, float r, float g, float b, float glow) {
        ParticleEmitter e = single(name, life, size);
        fadeOut(e.config, from, to);
        grow(e.config, 0.5f);
        material(e, glow(r, g, b, glow));
        return e;
    }

    /** A flat ring opening along the ground. */
    private static ParticleEmitter groundRing(String name, int life, float size, int from, int to, float r, float g, float b, float glow) {
        ParticleEmitter e = single(name, life, size);
        ParticleConfig c = e.config;
        c.renderer.setRenderMode(ParticleRendererSetting.Mode.Horizontal);
        grow(c, 0.2f);
        fadeOut(c, from, to);
        material(e, recolor(KilaPresets.SHOCKWAVE.create(), r, g, b, glow));
        e.transform().localPosition(new Vector3f(0, 0.08f, 0));
        return e;
    }

    /** The push-off: air slapped out from under the feet, left behind where they were. */
    private static FX dashBurst() {
        ParticleEmitter puffs = burstEmitter("puffs", 22, 22);
        ParticleConfig c = puffs.config;
        c.setSimulationSpace(ParticleConfig.Space.World);
        c.setStartLifetime(range(12, 20));
        c.setStartSpeed(range(1.6f, 3.4f));
        c.setStartSize(uniform(range(0.5f, 0.9f)));
        Sphere low = sphere(0.35f);
        low.setRadiusThickness(0f);
        c.shape.setShape(low);
        c.shape.setScale(new NumberFunction3(1f, 0.1f, 1f));
        c.colorOverLifetime.setEnable(true);
        c.colorOverLifetime.setColor(gradient(0xE0FFFFFF, 0xA0D8EAF5, 0x00D8EAF5));
        grow(c, 0.4f);
        material(puffs, mist());
        puffs.transform().localPosition(new Vector3f(0, 0.15f, 0));
        ParticleEmitter lines = sparks("lines", 16, 5f, 10f, 5, 9, 0.05f, 4f, WHITE, 0x00D8EAF5, 0.9f, 0.96f, 1f, 1.8f);
        lines.config.shape.setScale(new NumberFunction3(1f, 0.1f, 1f));
        lines.transform().localPosition(new Vector3f(0, 0.25f, 0));
        return fx(groundRing("ring", 10, 3.6f, 0xF0FFFFFF, 0x00D8EAF5, 0.9f, 0.96f, 1f, 1.6f), puffs, lines);
    }

    /** A low wake of kicked-up air, along the ground. */
    private static ParticleEmitter wake() {
        ParticleEmitter wake = emitter("wake", false, 6);
        ParticleConfig w = wake.config;
        w.setSimulationSpace(ParticleConfig.Space.World);
        w.setMaxParticles(24);
        w.setStartLifetime(range(10, 16));
        w.setStartSpeed(NumberFunction.constant(0.1f));
        w.setStartSize(uniform(range(0.55f, 0.85f)));
        w.emission.setEmissionRate(NumberFunction.constant(3f));
        w.shape.setShape(sphere(0.2f));
        w.colorOverLifetime.setEnable(true);
        w.colorOverLifetime.setColor(gradient(0xC0FFFFFF, 0x00D8EAF5));
        grow(w, 0.5f);
        material(wake, mist());
        wake.transform().localPosition(new Vector3f(0, 0.2f, 0));
        return wake;
    }

    /** While the dash lasts: tall pale afterimages left along the way, so anyone watching can see where it went, and the wake. */
    private static FX dashTrail() {
        ParticleEmitter after = emitter("afterimages", false, 6);
        ParticleConfig c = after.config;
        c.setSimulationSpace(ParticleConfig.Space.World);
        c.setMaxParticles(16);
        c.setStartLifetime(range(8, 11));
        c.setStartSpeed(NumberFunction.constant(0f));
        c.setStartSize(new NumberFunction3(NumberFunction.constant(0.5f), NumberFunction.constant(1.6f), NumberFunction.constant(1f)));
        c.emission.setEmissionRate(NumberFunction.constant(1f));
        c.shape.setScale(new NumberFunction3(0, 0, 0));
        c.renderer.setRenderMode(ParticleRendererSetting.Mode.VerticalBillboard);
        fadeOut(c, 0x90FFFFFF, 0x00D8EAF5);
        material(after, glow(0.85f, 0.94f, 1f, 0.95f));
        after.transform().localPosition(new Vector3f(0, 0.95f, 0));
        return fx(after, wake());
    }

    /** The dash from inside it, where an afterimage would be drawn over the camera. */
    private static FX dashTrailSelf() {
        return fx(wake());
    }

    /** Where a parry's effects sit, relative to the eyes: out over the raised guard, under the crosshair. */
    private static final Vector3f GUARD_POINT = new Vector3f(0.8f, -0.32f, 0f);
    private static final Vector3f GUARD_POINT_SELF = new Vector3f(2.2f, -0.75f, 0f);
    private static final ResourceLocation ARC_MODEL = Kegare.id("models/fx/parry_arc.obj");

    private static ParticleEmitter at(ParticleEmitter e) {
        e.transform().localPosition(new Vector3f(GUARD_POINT));
        return e;
    }

    /**
     * A perfect parry, riding the defender's look: a crescent thrown off the guard, with a crack of sparks. The crescent is a
     * Blockbench model ({@code art/blockbench/parry_arc.bbmodel}). Tried against Photon's stock SLASH sprite and it looked better: it
     * holds its shape in the world (a crescent from the front, a thin curve from the side), and takes the gold cleanly. The sprite
     * turned to face every camera, kept its own blue edge, and whited out the defender's view.
     */
    private static FX parrySpark() {
        ParticleEmitter arc = single("arc", 5, 0.95f);
        ParticleConfig c = arc.config;
        c.setStartRotation(new NumberFunction3(NumberFunction.constant(-28f), NumberFunction.constant(0f), NumberFunction.constant(0f)));
        c.renderer.setRenderMode(ParticleRendererSetting.Mode.Model);
        c.renderer.setModel(new MeshData(new ObjModelSource(ARC_MODEL)));
        grow(c, 0.7f);
        fadeOut(c, 0xF0FFF6D8, 0x00FFB347);
        KilaMaterial edge = glow(1f, 0.88f, 0.55f, 1.9f);
        edge.doubleSided = true;
        material(arc, edge);
        return fx(at(arc),
                at(flash("flash", 3, 0.28f, 0xB0FFFFFF, 0x00FFD24A, 1f, 0.9f, 0.55f, 1.1f)),
                at(sparks("sparks", 22, 4f, 9f, 4, 9, 0.04f, 4.5f, WHITE, 0x00FF9A2E, 1f, 0.82f, 0.35f, 2.2f)));
    }

    /**
     * The same parry as the defender sees it in first person: farther out and under the crosshair, so a pale thin curve and a few
     * tiny sparks. They need to know it worked and they need to see what they just parried.
     */
    private static FX parrySparkSelf() {
        ParticleEmitter arc = single("arc", 5, 1.5f);
        ParticleConfig c = arc.config;
        c.setStartRotation(new NumberFunction3(NumberFunction.constant(-28f), NumberFunction.constant(0f), NumberFunction.constant(0f)));
        c.renderer.setRenderMode(ParticleRendererSetting.Mode.Model);
        c.renderer.setModel(new MeshData(new ObjModelSource(ARC_MODEL)));
        grow(c, 0.8f);
        fadeOut(c, 0x90FFF6D8, 0x00FFB347);
        KilaMaterial edge = glow(1f, 0.86f, 0.5f, 0.9f);
        edge.doubleSided = true;
        material(arc, edge);
        return fx(self(arc), self(sparks("sparks", 12, 3f, 6f, 4, 8, 0.012f, 3f, WHITE, 0x00FF9A2E, 1f, 0.82f, 0.35f, 1.3f)));
    }

    private static ParticleEmitter self(ParticleEmitter e) {
        e.transform().localPosition(new Vector3f(GUARD_POINT_SELF));
        return e;
    }

    /** A guard took some of a hit: a dull, small version of the parry, so the two can't be confused. */
    private static FX guardHit() {
        return fx(at(flash("flash", 3, 0.4f, 0x90FFFFFF, 0x00D8D8D8, 0.95f, 0.92f, 0.85f, 0.9f)),
                at(sparks("sparks", 8, 2.5f, 5f, 4, 7, 0.03f, 2.5f, 0xFFE8E8E8, 0x00B0B0B0, 0.95f, 0.9f, 0.8f, 1.1f)));
    }

    private static FX guardHitSelf() {
        return fx(self(sparks("sparks", 6, 2f, 4f, 3, 6, 0.012f, 2.5f, 0xFFE8E8E8, 0x00B0B0B0, 0.95f, 0.9f, 0.8f, 1f)));
    }

    /**
     * A Reinforced Strike lands: heavier and slower than a parry, with a burst of force outward. Authored white with a white core: the
     * {@code tint} emitters are given the color of the glow that was on the fist.
     */
    private static FX strikeImpact() {
        ParticleEmitter shock = single("tint_shock", 7, 2.3f);
        ParticleConfig s = shock.config;
        s.renderer.setRenderMode(ParticleRendererSetting.Mode.Billboard);
        grow(s, 0.25f);
        fadeOut(s, 0xC0FFFFFF, 0x00C0C0C0);
        material(shock, recolor(KilaPresets.SHOCKWAVE.create(), 1f, 1f, 1f, 1.6f));
        return fx(flash("tint_flash", 5, 1.9f, 0xFFFFFFFF, 0x00C8C8C8, 1f, 1f, 1f, 2.0f), flash("core", 3, 0.7f, 0xF0FFFFFF, 0x00FFFFFF, 1f, 1f, 1f, 1.6f), shock,
                sparks("tint_sparks", 34, 4f, 9.5f, 8, 15, 0.065f, 3.5f, WHITE, 0x00C0C0C0, 1f, 1f, 1f, 2.2f));
    }

    /** Malice let go on contact, in the color of the glow that was on the fist: heavy streaks thrown out past the impact's own sparks. */
    private static FX strikeBurst() {
        ParticleEmitter body = burstEmitter("tint_body", 8, 8);
        ParticleConfig b = body.config;
        b.setSimulationSpace(ParticleConfig.Space.World);
        b.setStartLifetime(range(6, 10));
        b.setStartSpeed(range(1.2f, 2.8f));
        b.setStartSize(uniform(range(0.3f, 0.55f)));
        b.shape.setShape(sphere(0.15f));
        fadeOut(b, 0xB0FFFFFF, 0x00FFFFFF);
        KilaMaterial soft = KilaPresets.SMOKE.create();
        soft.color = new HDRColor(1f, 1f, 1f, 0.9f, 1.2f);
        soft.worldLight = false;
        material(body, soft);
        return fx(body, sparks("tint_streaks", 16, 2.5f, 6f, 6, 11, 0.08f, 5f, WHITE, 0x00D0D0D0, 1f, 1f, 1f, 2.4f));
    }

    /** A soft, alpha-blended material that takes the world's light: smoke, dust and steam. It doesn't glow. */
    private static KilaMaterial physical(float r, float g, float b, float a) {
        KilaMaterial m = KilaPresets.SMOKE.create();
        m.color = new HDRColor(r, g, b, a, 1f);
        m.worldLight = true;
        return m;
    }
}

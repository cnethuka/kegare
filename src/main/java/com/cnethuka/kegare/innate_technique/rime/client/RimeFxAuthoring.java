package com.cnethuka.kegare.innate_technique.rime.client;

import static com.cnethuka.kegare.client.visual.FxKit.*;

import com.cnethuka.kegare.client.visual.FxKit;

import com.cnethuka.kegare.innate_technique.rime.RimeIds;
import com.lowdragmc.lowdraglib2.Platform;
import com.lowdragmc.lowdraglib2.math.GradientColor;
import com.lowdragmc.lowdraglib2.math.HDRColor;
import com.lowdragmc.photon.Photon;
import com.lowdragmc.photon.client.fx.FX;
import com.lowdragmc.photon.client.gameobject.IFXObject;
import com.lowdragmc.photon.client.gameobject.emitter.data.EmissionSetting;
import com.lowdragmc.photon.client.gameobject.emitter.data.MaterialRequirements;
import com.lowdragmc.photon.client.gameobject.emitter.data.MaterialSetting;
import com.lowdragmc.photon.client.gameobject.emitter.data.material.BlendMode;
import com.lowdragmc.photon.client.gameobject.emitter.data.material.IMaterial;
import com.lowdragmc.photon.client.gameobject.emitter.data.material.kila.KilaMaterial;
import com.lowdragmc.photon.client.gameobject.emitter.data.material.kila.KilaPresets;
import com.lowdragmc.photon.client.gameobject.emitter.data.model.ObjModelSource;
import com.lowdragmc.photon.client.gameobject.emitter.data.number.NumberFunction;
import com.lowdragmc.photon.client.gameobject.emitter.data.number.NumberFunction3;
import com.lowdragmc.photon.client.gameobject.emitter.data.number.RandomConstant;
import com.lowdragmc.photon.client.gameobject.emitter.data.number.color.Gradient;
import com.lowdragmc.photon.client.gameobject.emitter.data.number.curve.Curve;
import com.lowdragmc.photon.client.gameobject.emitter.data.number.curve.ECBCurves;
import com.lowdragmc.photon.client.gameobject.emitter.data.shape.Cone;
import com.lowdragmc.photon.client.gameobject.emitter.data.shape.MeshData;
import com.lowdragmc.photon.client.gameobject.emitter.data.shape.Sphere;
import com.lowdragmc.photon.client.gameobject.emitter.particle.ParticleConfig;
import com.lowdragmc.photon.client.gameobject.emitter.particle.ParticleEmitter;
import com.lowdragmc.photon.client.gameobject.emitter.particle.ParticleRendererSetting;
import com.lowdragmc.photon.gui.editor.FXProject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.ResourceLocation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * DEV TOOL (client, dev environment only; see {@code /kegare debug fx export}). Builds Rime's Photon effects out of Photon's
 * own objects and writes them with Photon's own serializer, exactly as the editor exports a {@code .fx}: the FX's NBT,
 * plus {@code version = FXProject.VERSION}, gzip-compressed. Re-run after changing anything here.
 * <p>
 * Palette: pale cyan, white and deep blue, additive glow. Readable in PvP, never screen-filling: small particles, short
 * lifetimes, caps on counts. Effects that point somewhere face local +X (Photon turns +X to the look/motion direction).
 * Units (from Photon's source): lifetime in ticks, emission per tick, speed about blocks per second, size in blocks.
 * Emitters named {@code persist...} leave particles in the world whose lifetime Kegare sets from the server config at
 * play time (Ice Path's trail); give them a {@code maxParticles} with room for the longest configured time.
 */
public final class RimeFxAuthoring {
    // Palette (ARGB)
    private static final int WHITE = 0xFFF2FDFF;
    private static final int PALE = 0xFFC2EEFA;
    private static final int CYAN = 0xFF79C8EC;
    private static final int BLUE = 0xFF3A8FCC;
    private static final int DEEP = 0xFF1A5590;

    private RimeFxAuthoring() {}

    /** Every Rime effect, by ID. */
    public static Map<ResourceLocation, Supplier<FX>> effects() {
        Map<ResourceLocation, Supplier<FX>> out = new LinkedHashMap<>();
        out.put(RimeIds.FX_FROST_SHARD_TRAIL, RimeFxAuthoring::frostShardTrail);
        out.put(RimeIds.FX_FROST_SHARD_IMPACT, RimeFxAuthoring::frostShardImpact);
        out.put(RimeIds.FX_GLACIAL_LANCE_CHARGE, RimeFxAuthoring::glacialLanceCharge);
        out.put(RimeIds.FX_GLACIAL_LANCE_TRAIL, RimeFxAuthoring::glacialLanceTrail);
        out.put(RimeIds.FX_GLACIAL_LANCE_IMPACT, RimeFxAuthoring::glacialLanceImpact);
        out.put(RimeIds.FX_FROST_FIELD_CONE, RimeFxAuthoring::frostFieldCone);
        out.put(RimeIds.FX_RIME_SHELL_AURA, RimeFxAuthoring::rimeShellAura);
        out.put(RimeIds.FX_FREEZE_SHATTER, RimeFxAuthoring::freezeShatter);
        // Specials
        out.put(RimeIds.FX_SPLINTER_CAST, RimeFxAuthoring::splinterCast);
        out.put(RimeIds.FX_SPLINTER_TRAIL, RimeFxAuthoring::splinterTrail);
        out.put(RimeIds.FX_GLACIAL_SPIRE_CHARGE, RimeFxAuthoring::glacialSpireCharge);
        out.put(RimeIds.FX_GLACIAL_SPIRE_WARNING, RimeFxAuthoring::glacialSpireWarning);
        out.put(RimeIds.FX_GLACIAL_SPIRE_SPIKE, RimeFxAuthoring::glacialSpireSpike);
        out.put(RimeIds.FX_GLACIAL_SPIRE_ERUPT, RimeFxAuthoring::glacialSpireErupt);
        out.put(RimeIds.FX_ICE_PATH_TRAIL, RimeFxAuthoring::icePathTrail);
        out.put(RimeIds.FX_SHATTER_SHELL_BURST, RimeFxAuthoring::shatterShellBurst);
        return out;
    }

    // ================================================================ effects

    /** Frost Shard in flight: a thin icy ribbon plus a few glinting flakes left behind. */
    private static FX frostShardTrail() {
        ParticleEmitter ribbon = ribbon("ribbon", 0.22f, 8, PALE);
        ParticleEmitter glints = emitter("glints", true, 20);
        ParticleConfig c = glints.config;
        c.setSimulationSpace(ParticleConfig.Space.World);
        c.setMaxParticles(60);
        c.setStartLifetime(range(8, 14));
        c.setStartSpeed(range(0.0f, 0.4f));
        c.setStartSize(uniform(range(0.05f, 0.11f)));
        c.emission.setEmissionRate(NumberFunction.constant(4f));
        c.shape.setShape(sphere(0.06f));
        fadeOut(c, WHITE, CYAN);
        shrink(c);
        material(glints, glow(0.5f, 0.85f, 1f, 1.4f));
        return fx(ribbon, glints);
    }

    /** Frost Shard hits: a small flash, a ring and a spray of ice glints. */
    private static FX frostShardImpact() {
        ParticleEmitter flash = single("flash", 5, 0.5f);
        fadeOut(flash.config, PALE, CYAN);
        shrink(flash.config);
        material(flash, glow(0.5f, 0.85f, 1f, 1.4f));

        ParticleEmitter ring = single("ring", 8, 0.9f);
        grow(ring.config, 0.2f);
        fadeOut(ring.config, PALE, BLUE);
        material(ring, recolor(KilaPresets.SHOCKWAVE.create(), 0.45f, 0.82f, 1f, 1.2f));

        ParticleEmitter spray = burstEmitter("spray", 18, 24);
        ParticleConfig c = spray.config;
        c.setStartLifetime(range(8, 15));
        c.setStartSpeed(range(2.5f, 5.5f));
        c.setStartSize(uniform(range(0.05f, 0.12f)));
        c.shape.setShape(sphere(0.1f));
        gravity(c, 0.6f);
        fadeOut(c, WHITE, CYAN);
        shrink(c);
        material(spray, recolor(KilaPresets.SPARKS.create(), 0.55f, 0.88f, 1f, 1.5f));
        return fx(flash, ring, spray);
    }

    /**
     * Glacial Lance charging: a glowing core by the raised hand with motes drawn into it. Kegare scales the whole
     * effect from 30% to 100% over the charge, so it grows with the charge.
     */
    private static FX glacialLanceCharge() {
        // Ahead, up and to the right of the eyes (+X forward, +Z right): far enough from the camera that in first
        // person it is a small orb in the upper right, not a screen-filling glow.
        Vector3f hand = new Vector3f(1.0f, 0.25f, 0.4f);

        ParticleEmitter core = emitter("core", true, 20);
        ParticleConfig c = core.config;
        c.setMaxParticles(2);
        c.setStartLifetime(NumberFunction.constant(20));
        c.setStartSpeed(NumberFunction.constant(0));
        c.setStartSize(uniform(NumberFunction.constant(0.24f)));
        c.shape.setScale(new NumberFunction3(0, 0, 0));
        c.emission.setEmissionRate(NumberFunction.constant(0));
        c.emission.getBursts().add(burst(0, 1));
        c.colorOverLifetime.setEnable(true);
        c.colorOverLifetime.setColor(gradient(0x00FFFFFF, WHITE, PALE, 0x00FFFFFF));
        material(core, glow(0.5f, 0.85f, 1f, 1.6f));
        core.transform().localPosition(new Vector3f(hand));

        ParticleEmitter motes = emitter("motes", true, 20);
        ParticleConfig m = motes.config;
        m.setMaxParticles(40);
        m.setStartLifetime(NumberFunction.constant(10));
        m.setStartSpeed(NumberFunction.constant(0));
        m.setStartSize(uniform(range(0.025f, 0.05f)));
        Sphere shell = sphere(0.38f);
        shell.setRadiusThickness(0f);
        m.shape.setShape(shell);
        m.emission.setEmissionRate(NumberFunction.constant(1.5f));
        m.velocityOverLifetime.setEnable(true);
        m.velocityOverLifetime.setRadial(NumberFunction.constant(-0.8f)); // drawn inward
        fadeIn(m, PALE, WHITE);
        material(motes, glow(0.55f, 0.88f, 1f, 1.4f));
        motes.transform().localPosition(new Vector3f(hand));

        // A spinning ice crystal in the glow: a glow alone washes out against a bright sky, so opponents could miss it.
        ParticleEmitter crystal = emitter("crystal", true, 40);
        ParticleConfig k = crystal.config;
        k.setMaxParticles(2);
        k.setStartLifetime(NumberFunction.constant(40));
        k.setStartSpeed(NumberFunction.constant(0));
        k.setStartSize(new NumberFunction3(0.16f, 0.34f, 0.16f));
        k.shape.setScale(new NumberFunction3(0, 0, 0));
        k.emission.setEmissionRate(NumberFunction.constant(0));
        k.emission.getBursts().add(burst(0, 1));
        k.rotationOverLifetime.setEnable(true);
        k.rotationOverLifetime.setYaw(NumberFunction.constant(270));
        k.renderer.setRenderMode(ParticleRendererSetting.Mode.Model);
        k.renderer.setModel(new MeshData(new ObjModelSource(Photon.id("models/cube.obj"))));
        material(crystal, KilaPresets.CRYSTAL.create());
        crystal.transform().localPosition(new Vector3f(hand));
        return fx(core, motes, crystal);
    }

    /** Glacial Lance in flight: a wide ribbon, glints and a little frost mist. */
    private static FX glacialLanceTrail() {
        ParticleEmitter ribbon = ribbon("ribbon", 0.36f, 11, PALE);

        ParticleEmitter glints = emitter("glints", true, 20);
        ParticleConfig c = glints.config;
        c.setSimulationSpace(ParticleConfig.Space.World);
        c.setMaxParticles(120);
        c.setStartLifetime(range(10, 18));
        c.setStartSpeed(range(0.1f, 0.6f));
        c.setStartSize(uniform(range(0.07f, 0.17f)));
        c.emission.setEmissionRate(NumberFunction.constant(4f));
        c.shape.setShape(sphere(0.18f));
        fadeOut(c, WHITE, CYAN);
        shrink(c);
        material(glints, glow(0.5f, 0.85f, 1f, 1.5f));

        ParticleEmitter mist = emitter("mist", true, 20);
        ParticleConfig mc = mist.config;
        mc.setSimulationSpace(ParticleConfig.Space.World);
        mc.setMaxParticles(40);
        mc.setStartLifetime(range(14, 22));
        mc.setStartSpeed(range(0.0f, 0.2f));
        mc.setStartSize(uniform(range(0.18f, 0.3f)));
        mc.emission.setEmissionRate(NumberFunction.constant(0.6f));
        mc.shape.setShape(sphere(0.1f));
        mc.colorOverLifetime.setEnable(true);
        mc.colorOverLifetime.setColor(gradient(0x00C2EEFA, 0x55C2EEFA, 0x0079C8EC));
        grow(mc, 0.6f);
        material(mist, mist());
        return fx(ribbon, glints, mist);
    }

    /** Glacial Lance hits: a bigger burst. Flash, double ring, a spray of shards and frost mist. */
    private static FX glacialLanceImpact() {
        ParticleEmitter flash = single("flash", 7, 1.1f);
        fadeOut(flash.config, PALE, CYAN);
        shrink(flash.config);
        material(flash, glow(0.5f, 0.85f, 1f, 1.5f));

        ParticleEmitter ring = single("ring", 12, 2.4f);
        grow(ring.config, 0.15f);
        fadeOut(ring.config, PALE, BLUE);
        material(ring, recolor(KilaPresets.SHOCKWAVE.create(), 0.4f, 0.78f, 1f, 1.3f));

        ParticleEmitter innerRing = single("inner_ring", 9, 1.2f);
        grow(innerRing.config, 0.1f);
        fadeOut(innerRing.config, PALE, CYAN);
        material(innerRing, recolor(KilaPresets.SHOCKWAVE.create(), 0.55f, 0.88f, 1f, 1.4f));

        ParticleEmitter shards = burstEmitter("shards", 36, 40);
        ParticleConfig c = shards.config;
        c.setStartLifetime(range(10, 20));
        c.setStartSpeed(range(3.5f, 8f));
        c.setStartSize(uniform(range(0.07f, 0.18f)));
        c.shape.setShape(sphere(0.2f));
        gravity(c, 0.9f);
        fadeOut(c, WHITE, CYAN);
        shrink(c);
        material(shards, recolor(KilaPresets.SPARKS.create(), 0.55f, 0.88f, 1f, 1.6f));

        ParticleEmitter mist = burstEmitter("mist", 5, 6);
        ParticleConfig mc = mist.config;
        mc.setStartLifetime(range(12, 20));
        mc.setStartSpeed(range(0.4f, 1.0f));
        mc.setStartSize(uniform(range(0.35f, 0.6f)));
        mc.shape.setShape(sphere(0.25f));
        mc.colorOverLifetime.setEnable(true);
        mc.colorOverLifetime.setColor(gradient(0x00C2EEFA, 0x55C2EEFA, 0x0079C8EC));
        grow(mc, 0.5f);
        material(mist, mist());
        return fx(flash, ring, innerRing, shards, mist);
    }

    /** Frost Field: a cone of frost gusting forward from in front of the caster (+X), with fluttering flakes. */
    private static FX frostFieldCone() {
        Vector3f origin = new Vector3f(0.55f, -0.25f, 0f);
        // Photon's cone emits along local +Y; turn it to +X (forward).
        Quaternionf forward = new Quaternionf().rotateZ((float) (-Math.PI / 2));

        ParticleEmitter gust = emitter("gust", true, 20);
        ParticleConfig c = gust.config;
        c.setSimulationSpace(ParticleConfig.Space.World);
        c.setMaxParticles(140);
        c.setStartLifetime(range(14, 20));
        c.setStartSpeed(range(5.5f, 7.5f));
        c.setStartSize(uniform(range(0.12f, 0.2f)));
        c.emission.setEmissionRate(NumberFunction.constant(5f));
        c.shape.setShape(cone(28f, 0.12f));
        c.colorOverLifetime.setEnable(true);
        c.colorOverLifetime.setColor(gradient(0x00F2FDFF, 0xAAF2FDFF, 0x66C2EEFA, 0x0079C8EC));
        grow(c, 0.4f);
        material(gust, mist());
        gust.transform().localPosition(new Vector3f(origin));
        gust.transform().localRotation(new Quaternionf(forward));

        ParticleEmitter flakes = emitter("flakes", true, 20);
        ParticleConfig f = flakes.config;
        f.setSimulationSpace(ParticleConfig.Space.World);
        f.setMaxParticles(120);
        f.setStartLifetime(range(10, 16));
        f.setStartSpeed(range(6f, 9f));
        f.setStartSize(uniform(range(0.04f, 0.09f)));
        f.emission.setEmissionRate(NumberFunction.constant(4f));
        f.shape.setShape(cone(32f, 0.1f));
        f.noise.setEnable(true);
        f.noise.setFrequency(1.5f);
        f.noise.setPosition(uniform(NumberFunction.constant(0.08f)));
        fadeOut(f, WHITE, CYAN);
        material(flakes, glow(0.5f, 0.85f, 1f, 1.3f));
        flakes.transform().localPosition(new Vector3f(origin));
        flakes.transform().localRotation(new Quaternionf(forward));
        return fx(gust, flakes);
    }

    /** Rime Shell: a faint, glassy ice bubble (invisible from the inside) with flakes circling it. */
    private static FX rimeShellAura() {
        ParticleEmitter shell = emitter("shell", true, 40);
        ParticleConfig c = shell.config;
        c.setMaxParticles(2);
        c.setStartLifetime(NumberFunction.constant(40));
        c.setStartSpeed(NumberFunction.constant(0));
        c.setStartSize(uniform(NumberFunction.constant(2.3f)));
        c.shape.setScale(new NumberFunction3(0, 0, 0));
        c.emission.setEmissionRate(NumberFunction.constant(0));
        c.emission.getBursts().add(burst(0, 1));
        c.renderer.setRenderMode(ParticleRendererSetting.Mode.Model);
        c.renderer.setModel(new MeshData(new ObjModelSource(Photon.id("models/sphere.obj"))));
        KilaMaterial shield = KilaPresets.ENERGY_SHIELD.create();
        shield.color = new HDRColor(0.45f, 0.82f, 1f, 0.18f, 1f);
        shield.fresnel.color = new HDRColor(0.5f, 0.88f, 1f, 1f, 1.6f);
        shield.doubleSided = false; // seen from inside (first person): nothing in the way
        material(shell, shield);

        ParticleEmitter flakes = emitter("flakes", true, 20);
        ParticleConfig f = flakes.config;
        f.setMaxParticles(50);
        f.setStartLifetime(range(24, 34));
        f.setStartSpeed(NumberFunction.constant(0));
        f.setStartSize(uniform(range(0.03f, 0.06f)));
        Sphere around = sphere(1.2f);
        around.setRadiusThickness(0f);
        f.shape.setShape(around);
        f.emission.setEmissionRate(NumberFunction.constant(1.2f));
        f.velocityOverLifetime.setEnable(true);
        f.velocityOverLifetime.setOrbital(new NumberFunction3(0, 1.6f, 0)); // circle the body
        fadeInOut(f, WHITE, CYAN);
        material(flakes, glow(0.5f, 0.85f, 1f, 1.3f));
        return fx(shell, flakes);
    }

    /** Breaking free of a freeze: ice chunks burst outward and fall, with a flash and a ring. */
    private static FX freezeShatter() {
        ParticleEmitter flash = single("flash", 6, 0.9f);
        fadeOut(flash.config, PALE, CYAN);
        shrink(flash.config);
        material(flash, glow(0.5f, 0.85f, 1f, 1.4f));

        ParticleEmitter ring = single("ring", 10, 1.8f);
        grow(ring.config, 0.2f);
        fadeOut(ring.config, PALE, BLUE);
        material(ring, recolor(KilaPresets.SHOCKWAVE.create(), 0.45f, 0.82f, 1f, 1.2f));

        ParticleEmitter chunks = burstEmitter("chunks", 14, 16);
        ParticleConfig c = chunks.config;
        c.setStartLifetime(range(16, 26));
        c.setStartSpeed(range(2.5f, 5f));
        c.setStartSize(uniform(range(0.12f, 0.24f)));
        c.setStartRotation(new NumberFunction3(range(0, 360), range(0, 360), range(0, 360)));
        c.shape.setShape(sphere(0.45f));
        gravity(c, 1.6f);
        c.rotationOverLifetime.setEnable(true);
        c.rotationOverLifetime.setRoll(range(-360, 360));
        c.colorOverLifetime.setEnable(true);
        c.colorOverLifetime.setColor(gradient(WHITE, PALE, 0x0079C8EC));
        c.renderer.setRenderMode(ParticleRendererSetting.Mode.Model);
        c.renderer.setModel(new MeshData(new ObjModelSource(Photon.id("models/cube.obj"))));
        material(chunks, KilaPresets.CRYSTAL.create());

        ParticleEmitter glints = burstEmitter("glints", 20, 24);
        ParticleConfig g = glints.config;
        g.setStartLifetime(range(8, 14));
        g.setStartSpeed(range(3f, 6f));
        g.setStartSize(uniform(range(0.05f, 0.1f)));
        g.shape.setShape(sphere(0.3f));
        gravity(g, 0.5f);
        fadeOut(g, WHITE, CYAN);
        material(glints, recolor(KilaPresets.SPARKS.create(), 0.55f, 0.88f, 1f, 1.5f));
        return fx(flash, ring, chunks, glints);
    }

    // ================================================================ specials

    /** Splinter thrown: a small flash at the hand and a flat fan of glints flying forward. */
    private static FX splinterCast() {
        Vector3f hand = new Vector3f(1.0f, -0.3f, 0.35f);
        ParticleEmitter flash = single("flash", 4, 0.3f);
        fadeOut(flash.config, WHITE, CYAN);
        shrink(flash.config);
        material(flash, glow(0.5f, 0.85f, 1f, 1.3f));
        flash.transform().localPosition(new Vector3f(hand));

        ParticleEmitter fan = burstEmitter("fan", 16, 20);
        ParticleConfig c = fan.config;
        c.setStartLifetime(range(4, 8));
        c.setStartSpeed(range(5f, 9f));
        c.setStartSize(uniform(range(0.03f, 0.07f)));
        c.shape.setShape(cone(14f, 0.05f));
        c.shape.setScale(new NumberFunction3(1f, 1f, 0.25f)); // flattened: a fan, not a cone
        fadeOut(c, WHITE, CYAN);
        shrink(c);
        material(fan, recolor(KilaPresets.SPARKS.create(), 0.55f, 0.88f, 1f, 1.4f));
        fan.transform().localPosition(new Vector3f(hand));
        fan.transform().localRotation(new Quaternionf().rotateZ((float) (-Math.PI / 2)));
        return fx(flash, fan);
    }

    /** A Splinter shard in flight: a thinner, shorter ribbon than Frost Shard's, and a few glints. */
    private static FX splinterTrail() {
        ParticleEmitter ribbon = ribbon("ribbon", 0.14f, 6, PALE);
        ParticleEmitter glints = emitter("glints", true, 20);
        ParticleConfig c = glints.config;
        c.setSimulationSpace(ParticleConfig.Space.World);
        c.setMaxParticles(30);
        c.setStartLifetime(range(6, 10));
        c.setStartSpeed(range(0.0f, 0.3f));
        c.setStartSize(uniform(range(0.03f, 0.07f)));
        c.emission.setEmissionRate(NumberFunction.constant(2f));
        c.shape.setShape(sphere(0.04f));
        fadeOut(c, WHITE, CYAN);
        shrink(c);
        material(glints, glow(0.5f, 0.85f, 1f, 1.3f));
        return fx(ribbon, glints);
    }

    /** Glacial Spire charging: frost swirling round the lowered hand (below the view in first person). Grows with charge. */
    private static FX glacialSpireCharge() {
        Vector3f hand = new Vector3f(0.7f, -0.95f, 0.35f);
        ParticleEmitter core = emitter("core", true, 20);
        ParticleConfig c = core.config;
        c.setMaxParticles(2);
        c.setStartLifetime(NumberFunction.constant(20));
        c.setStartSpeed(NumberFunction.constant(0));
        c.setStartSize(uniform(NumberFunction.constant(0.22f)));
        c.shape.setScale(new NumberFunction3(0, 0, 0));
        c.emission.setEmissionRate(NumberFunction.constant(0));
        c.emission.getBursts().add(burst(0, 1));
        c.colorOverLifetime.setEnable(true);
        c.colorOverLifetime.setColor(gradient(0x00FFFFFF, PALE, CYAN, 0x00FFFFFF));
        material(core, glow(0.45f, 0.82f, 1f, 1.4f));
        core.transform().localPosition(new Vector3f(hand));

        ParticleEmitter swirl = emitter("swirl", true, 20);
        ParticleConfig m = swirl.config;
        m.setMaxParticles(50);
        m.setStartLifetime(range(10, 16));
        m.setStartSpeed(NumberFunction.constant(0));
        m.setStartSize(uniform(range(0.025f, 0.05f)));
        Sphere ring = sphere(0.45f);
        ring.setRadiusThickness(0f);
        m.shape.setShape(ring);
        m.shape.setScale(new NumberFunction3(1f, 0.25f, 1f));
        m.emission.setEmissionRate(NumberFunction.constant(2f));
        m.velocityOverLifetime.setEnable(true);
        m.velocityOverLifetime.setOrbital(new NumberFunction3(0, 4f, 0));
        m.velocityOverLifetime.setRadial(NumberFunction.constant(-0.3f));
        fadeInOut(m, WHITE, CYAN);
        material(swirl, glow(0.5f, 0.85f, 1f, 1.3f));
        swirl.transform().localPosition(new Vector3f(hand));
        return fx(core, swirl);
    }

    /**
     * Glacial Spire's warning: a pulsing frost circle flat on the ground (authored for the default 1.5 radius), frost
     * rising inside it, and a curtain of glints rising from its edge.
     */
    private static FX glacialSpireWarning() {
        ParticleEmitter ring = emitter("ring", true, 8);
        ParticleConfig c = ring.config;
        c.setMaxParticles(3);
        c.setStartLifetime(NumberFunction.constant(12));
        c.setStartSpeed(NumberFunction.constant(0));
        c.setStartSize(uniform(NumberFunction.constant(3.1f)));
        c.shape.setScale(new NumberFunction3(0, 0, 0));
        c.emission.setEmissionRate(NumberFunction.constant(0));
        c.emission.getBursts().add(burst(0, 1));
        c.renderer.setRenderMode(ParticleRendererSetting.Mode.Horizontal);
        grow(c, 0.85f);
        c.colorOverLifetime.setEnable(true);
        c.colorOverLifetime.setColor(gradient(0x00C2EEFA, 0xEEF2FDFF, 0xAA79C8EC, 0x003A8FCC));
        material(ring, recolor(KilaPresets.SHOCKWAVE.create(), 0.5f, 0.86f, 1f, 1.5f));
        ring.transform().localPosition(new Vector3f(0, 0.06f, 0));

        ParticleEmitter disc = emitter("disc", true, 20);
        ParticleConfig d = disc.config;
        d.setMaxParticles(2);
        d.setStartLifetime(NumberFunction.constant(20));
        d.setStartSpeed(NumberFunction.constant(0));
        d.setStartSize(uniform(NumberFunction.constant(2.8f)));
        d.shape.setScale(new NumberFunction3(0, 0, 0));
        d.emission.setEmissionRate(NumberFunction.constant(0));
        d.emission.getBursts().add(burst(0, 1));
        d.renderer.setRenderMode(ParticleRendererSetting.Mode.Horizontal);
        d.colorOverLifetime.setEnable(true);
        d.colorOverLifetime.setColor(gradient(0x4479C8EC, 0x6679C8EC, 0x4479C8EC));
        material(disc, glow(0.35f, 0.7f, 1f, 0.9f));
        disc.transform().localPosition(new Vector3f(0, 0.04f, 0));

        ParticleEmitter frost = emitter("frost", true, 20);
        ParticleConfig f = frost.config;
        f.setMaxParticles(40);
        f.setStartLifetime(range(10, 18));
        f.setStartSpeed(range(0.2f, 0.6f));
        f.setStartSize(uniform(range(0.04f, 0.08f)));
        f.shape.setShape(sphere(1.4f));
        f.shape.setScale(new NumberFunction3(1f, 0.05f, 1f));
        f.emission.setEmissionRate(NumberFunction.constant(2f));
        f.velocityOverLifetime.setEnable(true);
        f.velocityOverLifetime.setLinear(new NumberFunction3(0, 0.8f, 0));
        fadeInOut(f, WHITE, CYAN);
        material(frost, glow(0.5f, 0.85f, 1f, 1.2f));

        // A curtain of glints rising from the circle's edge past head height: someone standing in it sees the warning
        // at the edge of their view even while looking straight ahead (the circle itself is below it).
        ParticleEmitter curtain = emitter("curtain", true, 20);
        ParticleConfig k = curtain.config;
        k.setMaxParticles(200);
        k.setStartLifetime(range(14, 20));
        k.setStartSpeed(NumberFunction.constant(0));
        k.setStartSize(uniform(range(0.03f, 0.055f)));
        Sphere edge = sphere(1.5f);
        edge.setRadiusThickness(0f);
        k.shape.setShape(edge);
        k.shape.setScale(new NumberFunction3(1f, 0f, 1f));
        k.emission.setEmissionRate(NumberFunction.constant(6f));
        k.velocityOverLifetime.setEnable(true);
        k.velocityOverLifetime.setLinear(new NumberFunction3(0, 3.4f, 0));
        fadeInOut(k, WHITE, CYAN);
        material(curtain, glow(0.5f, 0.86f, 1f, 1.1f));
        return fx(ring, disc, frost, curtain);
    }

    /**
     * Glacial Spire standing: a tapered ice spike (a stack of crystal cubes, wide at the base and narrowing to a tip:
     * Photon's model particles scale uniformly, so one stretched cube can't make a spike) with a crown of small
     * crystals round its foot. Everything shoots up in an instant, then holds until the spike shatters.
     */
    private static FX glacialSpireSpike() {
        float[][] stack = { // {centre height, size}
                {0.35f, 0.72f}, {0.95f, 0.6f}, {1.45f, 0.5f}, {1.86f, 0.4f}, {2.18f, 0.3f}, {2.42f, 0.2f}, {2.6f, 0.12f}};
        List<IFXObject> parts = new java.util.ArrayList<>();
        for (int i = 0; i < stack.length; i++) {
            ParticleEmitter block = emitter("spike_" + i, true, 200);
            ParticleConfig c = block.config;
            c.setMaxParticles(1);
            c.setStartLifetime(NumberFunction.constant(200));
            c.setStartSpeed(NumberFunction.constant(0));
            c.setStartSize(uniform(NumberFunction.constant(stack[i][1])));
            c.setStartRotation(new NumberFunction3(NumberFunction.constant(0), NumberFunction.constant(45 + i * 9), NumberFunction.constant(0)));
            c.shape.setScale(new NumberFunction3(0, 0, 0));
            c.emission.setEmissionRate(NumberFunction.constant(0));
            c.emission.getBursts().add(burst(0, 1));
            c.renderer.setRenderMode(ParticleRendererSetting.Mode.Model);
            c.renderer.setModel(new MeshData(new ObjModelSource(Photon.id("models/cube.obj"))));
            c.sizeOverLifetime.setEnable(true);
            c.sizeOverLifetime.setSize(uniform(shootUp()));
            material(block, KilaPresets.CRYSTAL.create());
            block.transform().localPosition(new Vector3f(0, stack[i][0], 0));
            parts.add(block);
        }

        ParticleEmitter crown = emitter("crown", true, 200);
        ParticleConfig k = crown.config;
        k.setMaxParticles(7);
        k.setStartLifetime(NumberFunction.constant(200));
        k.setStartSpeed(NumberFunction.constant(0));
        k.setStartSize(uniform(range(0.25f, 0.4f)));
        k.setStartRotation(new NumberFunction3(range(-30, 30), range(0, 360), range(-30, 30)));
        Sphere around = sphere(0.75f);
        around.setRadiusThickness(0f);
        k.shape.setShape(around);
        k.shape.setScale(new NumberFunction3(1f, 0f, 1f));
        k.emission.setEmissionRate(NumberFunction.constant(0));
        k.emission.getBursts().add(burst(0, 7));
        k.renderer.setRenderMode(ParticleRendererSetting.Mode.Model);
        k.renderer.setModel(new MeshData(new ObjModelSource(Photon.id("models/cube.obj"))));
        k.sizeOverLifetime.setEnable(true);
        k.sizeOverLifetime.setSize(uniform(shootUp()));
        material(crown, KilaPresets.CRYSTAL.create());
        crown.transform().localPosition(new Vector3f(0, 0.15f, 0));
        parts.add(crown);

        ParticleEmitter mist = emitter("mist", true, 20);
        ParticleConfig mc = mist.config;
        mc.setMaxParticles(20);
        mc.setStartLifetime(range(14, 22));
        mc.setStartSpeed(range(0.1f, 0.3f));
        mc.setStartSize(uniform(range(0.3f, 0.5f)));
        mc.shape.setShape(sphere(0.9f));
        mc.shape.setScale(new NumberFunction3(1f, 0.1f, 1f));
        mc.emission.setEmissionRate(NumberFunction.constant(0.6f));
        mc.colorOverLifetime.setEnable(true);
        mc.colorOverLifetime.setColor(gradient(0x00C2EEFA, 0x55C2EEFA, 0x0079C8EC));
        grow(mc, 0.5f);
        material(mist, mist());
        parts.add(mist);
        return fx(parts.toArray(IFXObject[]::new));
    }

    /** Glacial Spire erupts: shards thrown up out of the ground, a flat shockwave and a puff of frost. */
    private static FX glacialSpireErupt() {
        ParticleEmitter ring = single("ring", 10, 4.0f);
        ring.config.renderer.setRenderMode(ParticleRendererSetting.Mode.Horizontal);
        grow(ring.config, 0.25f);
        fadeOut(ring.config, PALE, BLUE);
        material(ring, recolor(KilaPresets.SHOCKWAVE.create(), 0.45f, 0.82f, 1f, 1.4f));
        ring.transform().localPosition(new Vector3f(0, 0.1f, 0));

        ParticleEmitter shards = burstEmitter("shards", 28, 32);
        ParticleConfig c = shards.config;
        c.setStartLifetime(range(12, 22));
        c.setStartSpeed(range(5f, 10f));
        c.setStartSize(uniform(range(0.08f, 0.18f)));
        c.setStartRotation(new NumberFunction3(range(0, 360), range(0, 360), range(0, 360)));
        c.shape.setShape(cone(35f, 0.6f)); // a cone emits up (+Y)
        gravity(c, 1.4f);
        c.colorOverLifetime.setEnable(true);
        c.colorOverLifetime.setColor(gradient(WHITE, PALE, 0x0079C8EC));
        c.renderer.setRenderMode(ParticleRendererSetting.Mode.Model);
        c.renderer.setModel(new MeshData(new ObjModelSource(Photon.id("models/cube.obj"))));
        material(shards, KilaPresets.CRYSTAL.create());

        ParticleEmitter glints = burstEmitter("glints", 24, 28);
        ParticleConfig g = glints.config;
        g.setStartLifetime(range(8, 14));
        g.setStartSpeed(range(4f, 8f));
        g.setStartSize(uniform(range(0.05f, 0.1f)));
        g.shape.setShape(cone(45f, 0.8f));
        gravity(g, 0.6f);
        fadeOut(g, WHITE, CYAN);
        material(glints, recolor(KilaPresets.SPARKS.create(), 0.55f, 0.88f, 1f, 1.5f));

        ParticleEmitter mist = burstEmitter("mist", 6, 6);
        ParticleConfig mc = mist.config;
        mc.setStartLifetime(range(14, 22));
        mc.setStartSpeed(range(0.6f, 1.4f));
        mc.setStartSize(uniform(range(0.4f, 0.7f)));
        mc.shape.setShape(sphere(0.8f));
        mc.shape.setScale(new NumberFunction3(1f, 0.2f, 1f));
        mc.colorOverLifetime.setEnable(true);
        mc.colorOverLifetime.setColor(gradient(0x00C2EEFA, 0x66C2EEFA, 0x0079C8EC));
        grow(mc, 0.5f);
        material(mist, mist());
        return fx(ring, shards, glints, mist);
    }

    /**
     * Ice Path: flat ice left on the ground behind the slider (world space, so it stays where it was laid) that fades
     * out over the configured trail time, with a little frost spray at the feet. The two {@code persist} emitters'
     * lifetimes come from the config at play time.
     */
    private static FX icePathTrail() {
        ParticleEmitter ice = emitter("persist_ice", true, 20);
        ParticleConfig c = ice.config;
        c.setSimulationSpace(ParticleConfig.Space.World);
        c.setMaxParticles(1200);
        c.setStartLifetime(NumberFunction.constant(80));
        c.setStartSpeed(NumberFunction.constant(0));
        c.setStartSize(uniform(range(0.9f, 1.3f)));
        c.setStartRotation(new NumberFunction3(NumberFunction.constant(0), NumberFunction.constant(0), range(0, 360)));
        c.shape.setShape(sphere(0.2f));
        c.shape.setScale(new NumberFunction3(1f, 0f, 1f));
        c.emission.setEmissionRate(NumberFunction.constant(1.5f));
        c.renderer.setRenderMode(ParticleRendererSetting.Mode.Horizontal);
        c.colorOverLifetime.setEnable(true);
        c.colorOverLifetime.setColor(gradient(0x88C2EEFA, 0x7079C8EC, 0x6079C8EC, 0x003A8FCC));
        material(ice, glow(0.4f, 0.78f, 1f, 0.9f));
        ice.transform().localPosition(new Vector3f(0, 0.04f, 0));

        ParticleEmitter sparkle = emitter("persist_sparkle", true, 20);
        ParticleConfig k = sparkle.config;
        k.setSimulationSpace(ParticleConfig.Space.World);
        k.setMaxParticles(600);
        k.setStartLifetime(NumberFunction.constant(80));
        k.setStartSpeed(NumberFunction.constant(0));
        k.setStartSize(uniform(range(0.04f, 0.08f)));
        k.shape.setShape(sphere(0.55f));
        k.shape.setScale(new NumberFunction3(1f, 0f, 1f));
        k.emission.setEmissionRate(NumberFunction.constant(0.8f));
        fadeInOut(k, WHITE, CYAN);
        material(sparkle, glow(0.55f, 0.88f, 1f, 1.3f));
        sparkle.transform().localPosition(new Vector3f(0, 0.12f, 0));

        ParticleEmitter spray = emitter("spray", true, 20);
        ParticleConfig f = spray.config;
        f.setSimulationSpace(ParticleConfig.Space.World);
        f.setMaxParticles(60);
        f.setStartLifetime(range(6, 12));
        f.setStartSpeed(range(0.6f, 1.6f));
        f.setStartSize(uniform(range(0.04f, 0.08f)));
        f.shape.setShape(sphere(0.3f));
        f.shape.setScale(new NumberFunction3(1f, 0.2f, 1f));
        f.emission.setEmissionRate(NumberFunction.constant(2f));
        gravity(f, 0.4f);
        fadeOut(f, WHITE, CYAN);
        material(spray, glow(0.5f, 0.85f, 1f, 1.2f));
        spray.transform().localPosition(new Vector3f(0, 0.1f, 0));
        return fx(ice, sparkle, spray);
    }

    /** Shatter Shell: the shell bursts into fragments that fly outward, with a flat shockwave the size of its reach. */
    private static FX shatterShellBurst() {
        ParticleEmitter ring = single("ring", 9, 9.0f);
        ring.config.renderer.setRenderMode(ParticleRendererSetting.Mode.Horizontal);
        grow(ring.config, 0.15f);
        fadeOut(ring.config, PALE, BLUE);
        material(ring, recolor(KilaPresets.SHOCKWAVE.create(), 0.45f, 0.82f, 1f, 1.4f));
        ring.transform().localPosition(new Vector3f(0, -0.8f, 0)); // at the feet (the effect sits at mid-body)

        ParticleEmitter fragments = burstEmitter("fragments", 30, 34);
        ParticleConfig c = fragments.config;
        c.setStartLifetime(range(10, 18));
        c.setStartSpeed(range(7f, 12f));
        c.setStartSize(uniform(range(0.08f, 0.16f)));
        c.setStartRotation(new NumberFunction3(range(0, 360), range(0, 360), range(0, 360)));
        Sphere shell = sphere(1.1f);
        shell.setRadiusThickness(0f);
        c.shape.setShape(shell);
        gravity(c, 1.2f);
        c.rotationOverLifetime.setEnable(true);
        c.rotationOverLifetime.setRoll(range(-540, 540));
        c.colorOverLifetime.setEnable(true);
        c.colorOverLifetime.setColor(gradient(WHITE, PALE, 0x0079C8EC));
        c.renderer.setRenderMode(ParticleRendererSetting.Mode.Model);
        c.renderer.setModel(new MeshData(new ObjModelSource(Photon.id("models/cube.obj"))));
        material(fragments, KilaPresets.CRYSTAL.create());

        ParticleEmitter glints = burstEmitter("glints", 30, 34);
        ParticleConfig g = glints.config;
        g.setStartLifetime(range(8, 14));
        g.setStartSpeed(range(6f, 11f));
        g.setStartSize(uniform(range(0.05f, 0.1f)));
        Sphere around = sphere(1.0f);
        around.setRadiusThickness(0f);
        g.shape.setShape(around);
        gravity(g, 0.4f);
        fadeOut(g, WHITE, CYAN);
        material(glints, recolor(KilaPresets.SPARKS.create(), 0.55f, 0.88f, 1f, 1.5f));

        ParticleEmitter mist = burstEmitter("mist", 8, 8);
        ParticleConfig mc = mist.config;
        mc.setStartLifetime(range(12, 20));
        mc.setStartSpeed(range(2f, 3.5f));
        mc.setStartSize(uniform(range(0.4f, 0.7f)));
        Sphere puff = sphere(1.0f);
        puff.setRadiusThickness(0f);
        mc.shape.setShape(puff);
        mc.shape.setScale(new NumberFunction3(1f, 0.3f, 1f));
        mc.colorOverLifetime.setEnable(true);
        mc.colorOverLifetime.setColor(gradient(0x00C2EEFA, 0x55C2EEFA, 0x0079C8EC));
        grow(mc, 0.5f);
        material(mist, mist());
        mist.transform().localPosition(new Vector3f(0, -0.6f, 0));
        return fx(ring, fragments, glints, mist);
    }


    // ================================================================ Rime-only building blocks

    /** An ice ribbon (pale to cyan to clear blue) with Rime's energy-trail material. */
    private static ParticleEmitter ribbon(String name, float width, int lengthTicks, int color) {
        return FxKit.ribbon(name, width, lengthTicks, gradient(color, CYAN, 0x003A8FCC),
                recolor(KilaPresets.ENERGY_TRAIL.create(), 0.45f, 0.82f, 1f, 1.5f));
    }
}

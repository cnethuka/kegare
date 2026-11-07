package com.cnethuka.kegare.innate_technique.rime.client;

import static com.cnethuka.kegare.client.visual.FxKit.*;

import com.cnethuka.kegare.Kegare;
import com.cnethuka.kegare.client.visual.FxKit;

import com.cnethuka.kegare.innate_technique.rime.RimeIds;
import com.lowdragmc.lowdraglib2.Platform;
import com.lowdragmc.lowdraglib2.math.GradientColor;
import com.lowdragmc.lowdraglib2.math.HDRColor;
import com.lowdragmc.photon.Photon;
import com.lowdragmc.photon.client.fx.FX;
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
import com.lowdragmc.photon.client.gameobject.emitter.trail.TrailConfig;
import com.lowdragmc.photon.client.gameobject.emitter.trail.TrailEmitter;
import com.lowdragmc.photon.gui.editor.FXProject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
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
        out.put(RimeIds.FX_GLACIAL_LANCE_CHARGE_SELF, RimeFxAuthoring::glacialLanceChargeSelf);
        out.put(RimeIds.FX_GLACIAL_LANCE_SNAP, RimeFxAuthoring::glacialLanceSnap);
        out.put(RimeIds.FX_GLACIAL_LANCE_STICK, RimeFxAuthoring::glacialLanceStick);
        out.put(RimeIds.FX_GLACIAL_LANCE_SHATTER, RimeFxAuthoring::glacialLanceShatter);
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
        out.put(RimeIds.FX_GLACIAL_SPIRE_SHATTER, RimeFxAuthoring::glacialSpireShatter);
        out.put(RimeIds.FX_ICE_PATH_TRAIL, RimeFxAuthoring::icePathTrail);
        out.put(RimeIds.FX_SHATTER_SHELL_BURST, RimeFxAuthoring::shatterShellBurst);
        return out;
    }

    // ================================================================ effects

    // Frost Shard's crystal is a solid mesh (ShardRenderer) and its chips are solid (ShardImpact); these are only what's soft round them.

    /**
     * Frost Shard in flight: a few specks of frost shed as it goes. Its short line is drawn with the shard (ShardRenderer): a Photon
     * trail seen straight down its length turned into a sheet across the view.
     */
    private static FX frostShardTrail() {
        ParticleEmitter specks = emitter("specks", true, 20);
        ParticleConfig c = specks.config;
        c.setSimulationSpace(ParticleConfig.Space.World);
        c.setMaxParticles(30);
        c.setStartLifetime(range(6, 10));
        c.setStartSpeed(range(0.0f, 0.08f));
        c.setStartSize(uniform(range(0.02f, 0.04f)));
        c.emission.setEmissionRate(NumberFunction.constant(2f));
        c.shape.setShape(sphere(0.05f));
        gravity(c, 0.25f);
        fadeOut(c, WHITE, CYAN);
        shrink(c);
        material(specks, glow(0.62f, 0.88f, 1f, 1.4f));
        return fx(specks);
    }

    /** Frost Shard breaks (its chips are solid, ShardImpact): a small flash, hard glints flung out and a breath of mist. */
    private static FX frostShardImpact() {
        ParticleEmitter flash = single("flash", 3, 0.42f);
        fadeOut(flash.config, WHITE, CYAN);
        shrink(flash.config);
        material(flash, glow(0.6f, 0.86f, 1f, 1.4f));

        ParticleEmitter glints = glints("glints", 12, 2f, 4.5f, 4, 8, 0.04f, 2.2f);
        gravity(glints.config, 0.6f);

        ParticleEmitter mist = burstEmitter("mist", 3, 3);
        ParticleConfig mc = mist.config;
        mc.setStartLifetime(range(10, 16));
        mc.setStartSpeed(range(0.2f, 0.5f));
        mc.setStartSize(uniform(range(0.22f, 0.36f)));
        mc.shape.setShape(sphere(0.15f));
        mc.colorOverLifetime.setEnable(true);
        mc.colorOverLifetime.setColor(gradient(0x50E6F4FF, 0x28C2EEFA, 0x0079C8EC));
        grow(mc, 0.5f);
        material(mist, mist());
        return fx(flash, glints, mist);
    }

    // ---------------------------------------------------------------- Glacial Lance
    // The lance itself is a solid mesh (LanceMesh); these are only what's soft round it: glow, mist, frost, glints. Each is short,
    // and none of them is drawn as the lance's shape. Played where the client puts them (LanceHold, LanceAftermath), scaled by charge.

    /** Round the lance in the hand while it charges (moved along with it): frost falling off it, cold vapour sinking, a faint glow. */
    private static FX glacialLanceCharge() {
        ParticleEmitter frost = emitter("frost", true, 20);
        ParticleConfig c = frost.config;
        c.setSimulationSpace(ParticleConfig.Space.World);
        c.setMaxParticles(60);
        c.setStartLifetime(range(14, 24));
        c.setStartSpeed(range(0.02f, 0.15f));
        c.setStartSize(uniform(range(0.025f, 0.05f)));
        c.emission.setEmissionRate(NumberFunction.constant(1.6f));
        c.shape.setShape(sphere(0.45f));
        gravity(c, 0.22f);
        fadeOut(c, WHITE, CYAN);
        shrink(c);
        material(frost, glow(0.62f, 0.86f, 1f, 1.4f));

        ParticleEmitter vapour = emitter("vapour", true, 20);
        ParticleConfig v = vapour.config;
        v.setSimulationSpace(ParticleConfig.Space.World);
        v.setMaxParticles(14);
        v.setStartLifetime(range(18, 28));
        v.setStartSpeed(range(0f, 0.08f));
        v.setStartSize(uniform(range(0.22f, 0.4f)));
        v.emission.setEmissionRate(NumberFunction.constant(0.4f));
        v.shape.setShape(sphere(0.4f));
        gravity(v, 0.04f);
        v.colorOverLifetime.setEnable(true);
        v.colorOverLifetime.setColor(gradient(0x00C2EEFA, 0x38C2EEFA, 0x0079C8EC));
        grow(v, 0.5f);
        material(vapour, mist());

        ParticleEmitter aura = emitter("aura", true, 20);
        ParticleConfig a = aura.config;
        a.setMaxParticles(4);
        a.setStartLifetime(range(6, 8)); // short, so the glow goes with the lance when it's thrown
        a.setStartSpeed(NumberFunction.constant(0f));
        a.setStartSize(uniform(range(0.55f, 0.8f)));
        a.emission.setEmissionRate(NumberFunction.constant(0.45f));
        a.shape.setShape(sphere(0.3f));
        a.colorOverLifetime.setEnable(true);
        a.colorOverLifetime.setColor(gradient(0x003A8FCC, 0x403A8FCC, 0x003A8FCC));
        material(aura, glow(0.35f, 0.62f, 1f, 1.1f));
        return fx(frost, vapour, aura);
    }

    /** The same in your own first person, round the small lance at the bottom right: a few specks of frost and nothing that clouds the view. */
    private static FX glacialLanceChargeSelf() {
        ParticleEmitter frost = emitter("frost", true, 20);
        ParticleConfig c = frost.config;
        c.setSimulationSpace(ParticleConfig.Space.World);
        c.setMaxParticles(16);
        c.setStartLifetime(range(10, 16));
        c.setStartSpeed(range(0.01f, 0.05f));
        c.setStartSize(uniform(range(0.008f, 0.014f)));
        c.emission.setEmissionRate(NumberFunction.constant(0.7f));
        c.shape.setShape(sphere(0.14f));
        gravity(c, 0.1f);
        fadeOut(c, WHITE, CYAN);
        shrink(c);
        material(frost, glow(0.62f, 0.86f, 1f, 1.3f));
        return fx(frost);
    }

    /** A chunk snapping onto the lance: a crack of light and a puff of ice dust where it lands. */
    private static FX glacialLanceSnap() {
        ParticleEmitter flash = single("flash", 3, 0.42f);
        fadeOut(flash.config, WHITE, CYAN);
        material(flash, glow(0.6f, 0.86f, 1f, 1.6f));
        ParticleEmitter chips = glints("chips", 7, 1.2f, 2.6f, 4, 7, 0.035f, 2.5f);
        ParticleEmitter dust = burstEmitter("dust", 2, 2);
        ParticleConfig d = dust.config;
        d.setStartLifetime(range(8, 12));
        d.setStartSpeed(range(0.1f, 0.3f));
        d.setStartSize(uniform(range(0.16f, 0.24f)));
        d.shape.setShape(sphere(0.08f));
        d.colorOverLifetime.setEnable(true);
        d.colorOverLifetime.setColor(gradient(0x60E6F4FF, 0x00C2EEFA));
        grow(d, 0.6f);
        material(dust, mist());
        return fx(flash, chips, dust);
    }

    /** Glacial Lance in flight: a thin pale line behind it and frost falling off it as it goes. */
    private static FX glacialLanceTrail() {
        TrailEmitter ribbon = thinTrail("ribbon", 0.06f, 8, 0.98f);

        ParticleEmitter frost = emitter("frost", true, 20);
        ParticleConfig c = frost.config;
        c.setSimulationSpace(ParticleConfig.Space.World);
        c.setMaxParticles(120);
        c.setStartLifetime(range(16, 26));
        c.setStartSpeed(range(0f, 0.12f));
        c.setStartSize(uniform(range(0.03f, 0.065f)));
        c.emission.setEmissionRate(NumberFunction.constant(3f));
        c.shape.setShape(sphere(0.16f));
        gravity(c, 0.35f);
        fadeOut(c, WHITE, CYAN);
        shrink(c);
        material(frost, glow(0.62f, 0.86f, 1f, 1.4f));

        ParticleEmitter mist = emitter("mist", true, 20);
        ParticleConfig mc = mist.config;
        mc.setSimulationSpace(ParticleConfig.Space.World);
        mc.setMaxParticles(24);
        mc.setStartLifetime(range(12, 20));
        mc.setStartSpeed(range(0.0f, 0.1f));
        mc.setStartSize(uniform(range(0.14f, 0.24f)));
        mc.emission.setEmissionRate(NumberFunction.constant(0.4f));
        mc.shape.setShape(sphere(0.08f));
        mc.colorOverLifetime.setEnable(true);
        mc.colorOverLifetime.setColor(gradient(0x00C2EEFA, 0x3AC2EEFA, 0x0079C8EC));
        grow(mc, 0.6f);
        material(mist, mist());
        return fx(ribbon, frost, mist);
    }

    /** Glacial Lance bursts on whatever it hit (the solid shards are LanceAftermath's): a hard white flash, glints and a cloud of frost. */
    private static FX glacialLanceImpact() {
        ParticleEmitter flash = single("flash", 5, 1.15f);
        fadeOut(flash.config, WHITE, CYAN);
        shrink(flash.config);
        material(flash, glow(0.6f, 0.86f, 1f, 1.7f));

        ParticleEmitter ring = single("ring", 9, 2.2f);
        grow(ring.config, 0.2f);
        fadeOut(ring.config, 0xB0C2EEFA, 0x003A8FCC);
        material(ring, recolor(KilaPresets.SHOCKWAVE.create(), 0.5f, 0.8f, 1f, 1.2f));

        ParticleEmitter glints = glints("glints", 26, 4f, 9f, 5, 10, 0.055f, 3f);
        gravity(glints.config, 0.6f);

        ParticleEmitter mist = burstEmitter("mist", 7, 7);
        ParticleConfig mc = mist.config;
        mc.setStartLifetime(range(16, 26));
        mc.setStartSpeed(range(0.5f, 1.2f));
        mc.setStartSize(uniform(range(0.45f, 0.8f)));
        mc.shape.setShape(sphere(0.3f));
        mc.colorOverLifetime.setEnable(true);
        mc.colorOverLifetime.setColor(gradient(0x70E6F4FF, 0x40C2EEFA, 0x0079C8EC));
        grow(mc, 0.5f);
        gravity(mc, 0.03f);
        material(mist, mist());
        return fx(flash, ring, glints, mist);
    }

    /** It drove into a block: a flash at the point, ice dust kicked back off the surface. */
    private static FX glacialLanceStick() {
        ParticleEmitter flash = single("flash", 4, 0.9f);
        fadeOut(flash.config, WHITE, CYAN);
        material(flash, glow(0.6f, 0.86f, 1f, 1.6f));
        ParticleEmitter glints = glints("glints", 12, 2f, 5f, 4, 8, 0.045f, 2.5f);
        gravity(glints.config, 0.6f);
        ParticleEmitter dust = burstEmitter("dust", 5, 5);
        ParticleConfig d = dust.config;
        d.setStartLifetime(range(14, 22));
        d.setStartSpeed(range(0.3f, 0.8f));
        d.setStartSize(uniform(range(0.3f, 0.5f)));
        d.shape.setShape(sphere(0.15f));
        d.colorOverLifetime.setEnable(true);
        d.colorOverLifetime.setColor(gradient(0x70E6F4FF, 0x30C2EEFA, 0x0079C8EC));
        grow(d, 0.5f);
        gravity(d, 0.04f);
        material(dust, mist());
        return fx(flash, glints, dust);
    }

    /** The stuck lance gives way: a flash down the middle of its length, glints and frost. */
    private static FX glacialLanceShatter() {
        ParticleEmitter flash = single("flash", 4, 1.1f);
        fadeOut(flash.config, WHITE, CYAN);
        shrink(flash.config);
        material(flash, glow(0.6f, 0.86f, 1f, 1.6f));
        ParticleEmitter glints = glints("glints", 18, 2.5f, 6f, 5, 9, 0.05f, 2.5f);
        gravity(glints.config, 0.6f);
        ParticleEmitter mist = burstEmitter("mist", 5, 5);
        ParticleConfig mc = mist.config;
        mc.setStartLifetime(range(14, 22));
        mc.setStartSpeed(range(0.3f, 0.9f));
        mc.setStartSize(uniform(range(0.35f, 0.6f)));
        mc.shape.setShape(sphere(0.4f));
        mc.colorOverLifetime.setEnable(true);
        mc.colorOverLifetime.setColor(gradient(0x60E6F4FF, 0x30C2EEFA, 0x0079C8EC));
        grow(mc, 0.5f);
        gravity(mc, 0.03f);
        material(mist, mist());
        return fx(flash, glints, mist);
    }

    /**
     * Frost Field: a cone of frost gusting forward from in front of the caster (+X), as wide as the area it really covers.
     * Three layers so it reads at any distance: bright fog gives the cone its shape from far off (and doesn't turn grey
     * against the sky), fast streaks give it its direction, and flakes give it a texture up close. Each starts small and
     * clear at the caster, so from first person it opens out without hiding what you're aiming at.
     */
    private static FX frostFieldCone() {
        Vector3f origin = new Vector3f(0.55f, -0.25f, 0f);
        // Photon's cone emits along local +Y; turn it to +X (forward).
        Quaternionf forward = new Quaternionf().rotateZ((float) (-Math.PI / 2));
        float halfAngle = 35f; // RimeConfig.FIELD_HALF_ANGLE's default

        ParticleEmitter gust = emitter("gust", true, 20);
        ParticleConfig c = gust.config;
        c.setSimulationSpace(ParticleConfig.Space.World);
        c.setMaxParticles(110);
        c.setStartLifetime(range(14, 20));
        c.setStartSpeed(range(5f, 7f));
        c.setStartSize(uniform(range(0.55f, 0.9f)));
        c.emission.setEmissionRate(NumberFunction.constant(5f));
        c.shape.setShape(cone(halfAngle, 0.15f));
        c.colorOverLifetime.setEnable(true);
        c.colorOverLifetime.setColor(gradient(0x00FFFFFF, 0x14F2FDFF, 0x60D8F4FF, 0x0079C8EC));
        grow(c, 0.25f);
        KilaMaterial fog = mist();
        fog.color = new HDRColor(0.9f, 0.97f, 1f, 0.85f, 1.4f);
        material(gust, fog);
        gust.transform().localPosition(new Vector3f(origin));
        gust.transform().localRotation(new Quaternionf(forward));

        ParticleEmitter streaks = emitter("streaks", true, 20);
        ParticleConfig t = streaks.config;
        t.setSimulationSpace(ParticleConfig.Space.World);
        t.setMaxParticles(70);
        t.setStartLifetime(range(8, 13));
        t.setStartSpeed(range(8f, 11f));
        t.setStartSize(uniform(range(0.05f, 0.08f)));
        t.emission.setEmissionRate(NumberFunction.constant(5f));
        t.shape.setShape(cone(halfAngle, 0.12f));
        t.renderer.setRenderMode(ParticleRendererSetting.Mode.StretchedBillboard);
        t.renderer.setLengthScale(5f);
        fadeOut(t, WHITE, CYAN);
        material(streaks, glow(0.6f, 0.9f, 1f, 1.6f));
        streaks.transform().localPosition(new Vector3f(origin));
        streaks.transform().localRotation(new Quaternionf(forward));

        ParticleEmitter flakes = emitter("flakes", true, 20);
        ParticleConfig f = flakes.config;
        f.setSimulationSpace(ParticleConfig.Space.World);
        f.setMaxParticles(120);
        f.setStartLifetime(range(10, 16));
        f.setStartSpeed(range(5f, 8f));
        f.setStartSize(uniform(range(0.05f, 0.1f)));
        f.emission.setEmissionRate(NumberFunction.constant(5f));
        f.shape.setShape(cone(halfAngle, 0.1f));
        f.noise.setEnable(true);
        f.noise.setFrequency(1.5f);
        f.noise.setPosition(uniform(NumberFunction.constant(0.08f)));
        fadeOut(f, WHITE, CYAN);
        material(flakes, glow(0.5f, 0.85f, 1f, 1.1f));
        flakes.transform().localPosition(new Vector3f(origin));
        flakes.transform().localRotation(new Quaternionf(forward));
        return fx(gust, streaks, flakes);
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

    // Glacial Spire's spike is a solid mesh (GlacialSpireRenderer) and what it does to the ground is pixel art on the blocks (SpireClient,
    // BlockMarks); these are only what's soft round them: glow, mist, glints. No flat shapes on the ground, no cubes.

    /** Glacial Spire charging: cold gathering in the raised fist (moved along with it by SpireClient; never in your own first person). */
    private static FX glacialSpireCharge() {
        ParticleEmitter core = emitter("core", true, 20);
        ParticleConfig c = core.config;
        c.setMaxParticles(4);
        c.setStartLifetime(range(6, 8));
        c.setStartSpeed(NumberFunction.constant(0));
        c.setStartSize(uniform(range(0.3f, 0.42f)));
        c.shape.setShape(sphere(0.05f));
        c.emission.setEmissionRate(NumberFunction.constant(0.45f));
        c.colorOverLifetime.setEnable(true);
        c.colorOverLifetime.setColor(gradient(0x003A8FCC, 0x553A8FCC, 0x003A8FCC));
        material(core, glow(0.35f, 0.62f, 1f, 1.2f));

        // Specks drawn in to the fist from round it, on a slow spiral.
        ParticleEmitter swirl = emitter("swirl", true, 20);
        ParticleConfig m = swirl.config;
        m.setMaxParticles(40);
        m.setStartLifetime(range(8, 12));
        m.setStartSpeed(NumberFunction.constant(0));
        m.setStartSize(uniform(range(0.025f, 0.045f)));
        Sphere ring = sphere(0.42f);
        ring.setRadiusThickness(0f);
        m.shape.setShape(ring);
        m.emission.setEmissionRate(NumberFunction.constant(2.2f));
        m.velocityOverLifetime.setEnable(true);
        m.velocityOverLifetime.setOrbital(new NumberFunction3(0, 3f, 0));
        m.velocityOverLifetime.setRadial(NumberFunction.constant(-0.9f));
        fadeInOut(m, WHITE, CYAN);
        material(swirl, glow(0.62f, 0.88f, 1f, 1.5f));

        // Frost falling off it, and cold vapour sinking.
        ParticleEmitter frost = emitter("frost", true, 20);
        ParticleConfig f = frost.config;
        f.setSimulationSpace(ParticleConfig.Space.World);
        f.setMaxParticles(30);
        f.setStartLifetime(range(14, 22));
        f.setStartSpeed(range(0.02f, 0.1f));
        f.setStartSize(uniform(range(0.02f, 0.04f)));
        f.shape.setShape(sphere(0.18f));
        f.emission.setEmissionRate(NumberFunction.constant(1f));
        gravity(f, 0.22f);
        fadeOut(f, WHITE, CYAN);
        shrink(f);
        material(frost, glow(0.62f, 0.86f, 1f, 1.4f));

        ParticleEmitter vapour = emitter("vapour", true, 20);
        ParticleConfig v = vapour.config;
        v.setSimulationSpace(ParticleConfig.Space.World);
        v.setMaxParticles(10);
        v.setStartLifetime(range(18, 26));
        v.setStartSpeed(range(0f, 0.06f));
        v.setStartSize(uniform(range(0.18f, 0.32f)));
        v.emission.setEmissionRate(NumberFunction.constant(0.35f));
        v.shape.setShape(sphere(0.2f));
        gravity(v, 0.04f);
        v.colorOverLifetime.setEnable(true);
        v.colorOverLifetime.setColor(gradient(0x00C2EEFA, 0x34C2EEFA, 0x0079C8EC));
        grow(v, 0.5f);
        material(vapour, mist());
        return fx(core, swirl, frost, vapour);
    }

    /**
     * Glacial Spire's warning (the crack on the ground is pixel art, SpireClient): cold mist creeping low over the circle (authored
     * for the default 1.5 radius), frost specks lifting out of it, and a curtain of glints rising from its edge past head height, so
     * someone standing in it sees the warning at the edge of their view even while looking straight ahead.
     */
    private static FX glacialSpireWarning() {
        ParticleEmitter mist = emitter("mist", true, 20);
        ParticleConfig mc = mist.config;
        mc.setMaxParticles(16);
        mc.setStartLifetime(range(14, 20));
        mc.setStartSpeed(range(0.05f, 0.2f));
        mc.setStartSize(uniform(range(0.45f, 0.7f)));
        mc.shape.setShape(sphere(1.3f));
        mc.shape.setScale(new NumberFunction3(1f, 0.04f, 1f));
        mc.emission.setEmissionRate(NumberFunction.constant(1.2f));
        mc.colorOverLifetime.setEnable(true);
        mc.colorOverLifetime.setColor(gradient(0x00C2EEFA, 0x3AC2EEFA, 0x0079C8EC));
        grow(mc, 0.4f);
        material(mist, mist());
        mist.transform().localPosition(new Vector3f(0, 0.2f, 0));

        ParticleEmitter frost = emitter("frost", true, 20);
        ParticleConfig f = frost.config;
        f.setMaxParticles(40);
        f.setStartLifetime(range(10, 18));
        f.setStartSpeed(range(0.2f, 0.6f));
        f.setStartSize(uniform(range(0.03f, 0.06f)));
        f.shape.setShape(sphere(1.4f));
        f.shape.setScale(new NumberFunction3(1f, 0.05f, 1f));
        f.emission.setEmissionRate(NumberFunction.constant(2f));
        f.velocityOverLifetime.setEnable(true);
        f.velocityOverLifetime.setLinear(new NumberFunction3(0, 0.8f, 0));
        fadeInOut(f, WHITE, CYAN);
        material(frost, glow(0.5f, 0.85f, 1f, 1.2f));

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
        return fx(mist, frost, curtain);
    }

    /**
     * Glacial Spire standing (the spike itself is the mesh): a faint cold glow round it, frost glinting off its faces and drifting
     * down, and mist pooling at its foot.
     */
    private static FX glacialSpireSpike() {
        ParticleEmitter aura = emitter("aura", true, 20);
        ParticleConfig a = aura.config;
        a.setMaxParticles(4);
        a.setStartLifetime(range(10, 14));
        a.setStartSpeed(NumberFunction.constant(0f));
        a.setStartSize(uniform(range(1.6f, 2.1f)));
        a.emission.setEmissionRate(NumberFunction.constant(0.3f));
        a.shape.setShape(sphere(0.2f));
        a.colorOverLifetime.setEnable(true);
        a.colorOverLifetime.setColor(gradient(0x003A8FCC, 0x2C3A8FCC, 0x003A8FCC));
        material(aura, glow(0.35f, 0.62f, 1f, 1f));
        aura.transform().localPosition(new Vector3f(0, 1.4f, 0));

        ParticleEmitter glints = emitter("glints", true, 20);
        ParticleConfig g = glints.config;
        g.setSimulationSpace(ParticleConfig.Space.World);
        g.setMaxParticles(40);
        g.setStartLifetime(range(10, 18));
        g.setStartSpeed(range(0.02f, 0.1f));
        g.setStartSize(uniform(range(0.025f, 0.05f)));
        g.shape.setShape(sphere(0.55f));
        g.shape.setScale(new NumberFunction3(1f, 2.4f, 1f));
        g.emission.setEmissionRate(NumberFunction.constant(1.6f));
        gravity(g, 0.18f);
        fadeInOut(g, WHITE, CYAN);
        material(glints, glow(0.62f, 0.88f, 1f, 1.5f));
        glints.transform().localPosition(new Vector3f(0, 1.4f, 0));

        ParticleEmitter mist = emitter("mist", true, 20);
        ParticleConfig mc = mist.config;
        mc.setMaxParticles(20);
        mc.setStartLifetime(range(16, 24));
        mc.setStartSpeed(range(0.08f, 0.25f));
        mc.setStartSize(uniform(range(0.4f, 0.65f)));
        mc.shape.setShape(sphere(1.0f));
        mc.shape.setScale(new NumberFunction3(1f, 0.08f, 1f));
        mc.emission.setEmissionRate(NumberFunction.constant(0.7f));
        mc.colorOverLifetime.setEnable(true);
        mc.colorOverLifetime.setColor(gradient(0x00C2EEFA, 0x48C2EEFA, 0x0079C8EC));
        grow(mc, 0.5f);
        material(mist, mist());
        mist.transform().localPosition(new Vector3f(0, 0.2f, 0));
        return fx(aura, glints, mist);
    }

    /**
     * Glacial Spire erupts (the mesh shoots up; the ground's debris is the ground's own block particles): a flash at the foot, hard
     * glints thrown up the length of the spike, and a ring of mist punched out flat along the ground.
     */
    private static FX glacialSpireErupt() {
        ParticleEmitter flash = single("flash", 5, 2.4f);
        fadeOut(flash.config, WHITE, CYAN);
        shrink(flash.config);
        material(flash, glow(0.6f, 0.86f, 1f, 1.5f));
        flash.transform().localPosition(new Vector3f(0, 0.5f, 0));

        ParticleEmitter glints = glints("glints", 30, 4f, 9f, 7, 13, 0.06f, 3f);
        glints.config.shape.setShape(cone(28f, 0.6f)); // a cone emits up (+Y)
        gravity(glints.config, 0.9f);

        ParticleEmitter ring = burstEmitter("ring", 14, 14);
        ParticleConfig r = ring.config;
        r.setStartLifetime(range(16, 24));
        r.setStartSpeed(range(1.6f, 2.6f));
        r.setStartSize(uniform(range(0.45f, 0.75f)));
        Sphere out = sphere(0.6f);
        out.setRadiusThickness(0f);
        r.shape.setShape(out);
        r.shape.setScale(new NumberFunction3(1f, 0f, 1f));
        r.colorOverLifetime.setEnable(true);
        r.colorOverLifetime.setColor(gradient(0x70E6F4FF, 0x40C2EEFA, 0x0079C8EC));
        grow(r, 0.5f);
        material(ring, mist());
        ring.transform().localPosition(new Vector3f(0, 0.25f, 0));

        ParticleEmitter column = burstEmitter("column", 6, 6);
        ParticleConfig mc = column.config;
        mc.setStartLifetime(range(14, 22));
        mc.setStartSpeed(range(0.8f, 1.8f));
        mc.setStartSize(uniform(range(0.4f, 0.65f)));
        mc.shape.setShape(cone(12f, 0.5f));
        mc.colorOverLifetime.setEnable(true);
        mc.colorOverLifetime.setColor(gradient(0x60E6F4FF, 0x30C2EEFA, 0x0079C8EC));
        grow(mc, 0.5f);
        material(column, mist());
        return fx(flash, glints, ring, column);
    }

    /**
     * Glacial Spire breaking up (the chunks are solid, SpireClient): hard glints thrown off it up its whole height and a puff of cold
     * mist. Only a faint glow at its heart, so the chunks stay crisp.
     */
    private static FX glacialSpireShatter() {
        ParticleEmitter flash = single("flash", 3, 0.7f);
        flash.config.colorOverLifetime.setEnable(true);
        flash.config.colorOverLifetime.setColor(gradient(0x80E6F4FF, 0x003A8FCC));
        material(flash, glow(0.5f, 0.8f, 1f, 1f));

        ParticleEmitter glints = glints("glints", 26, 2f, 5f, 6, 11, 0.05f, 2.5f);
        Sphere along = sphere(0.4f);
        glints.config.shape.setShape(along);
        glints.config.shape.setScale(new NumberFunction3(1f, 3.2f, 1f));
        gravity(glints.config, 0.7f);

        ParticleEmitter mist = burstEmitter("mist", 8, 8);
        ParticleConfig mc = mist.config;
        mc.setStartLifetime(range(16, 24));
        mc.setStartSpeed(range(0.3f, 0.9f));
        mc.setStartSize(uniform(range(0.4f, 0.7f)));
        mc.shape.setShape(sphere(0.5f));
        mc.shape.setScale(new NumberFunction3(1f, 2.5f, 1f));
        mc.colorOverLifetime.setEnable(true);
        mc.colorOverLifetime.setColor(gradient(0x50E6F4FF, 0x28C2EEFA, 0x0079C8EC));
        grow(mc, 0.5f);
        gravity(mc, 0.03f);
        material(mist, mist());
        return fx(flash, glints, mist);
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

    /**
     * A thin pale line drawn along the path its emitter has taken over the last {@code lengthTicks} (Photon's own trail emitter, which
     * follows where it's carried in the world), tapering to nothing at its far end and hidden from {@code hiddenFrom} (0 to 1 along it)
     * to where it's being drawn from. Drawn with the glow material: Kila's energy-trail material, which the older ribbons use, drew
     * nothing at all on the test machine.
     */
    private static TrailEmitter thinTrail(String name, float width, int lengthTicks, float hiddenFrom) {
        TrailEmitter e = new TrailEmitter();
        e.setName(name);
        TrailConfig t = e.config;
        t.setLooping(true);
        t.setDuration(40);
        t.setTime(lengthTicks);
        t.setMinVertexDistance(0.08f);
        // Along the trail from its far, oldest end (0) to where the emitter is now (1). It fades in from the far end, and is gone again
        // from hiddenFrom on: that last stretch is still inside the lance it comes from.
        t.setWidthOverTrail(curve(0f, width));
        int[] stops = new int[21];
        for (int i = 0; i < stops.length; i++) {
            float at = i / 20f;
            float alpha = at >= hiddenFrom + 0.04f ? 0f : at >= hiddenFrom ? 0.5f : Math.min(1f, at / 0.5f);
            stops[i] = (Math.round(alpha * 0xC8) << 24) | (PALE & 0xFFFFFF);
        }
        t.setColorOverTrail(gradient(stops));
        t.renderer.getMaterials().clear();
        // The glow material with a line in place of its round spot: the same all along, so only the curves above shape it.
        KilaMaterial line = glow(0.78f, 0.93f, 1f, 1.3f);
        line.main.texture.texture = Kegare.id("textures/fx/glacial_lance_trail.png");
        t.renderer.getMaterials().add(new MaterialSetting(line));
        MaterialRequirements.fix(t.renderer.getMaterials(), t.additionalGPUDataSetting, false, t.renderer::isUseGPUInstance, t.renderer::setUseGPUInstance);
        return e;
    }

    /** Hard little glints of ice thrown out from a point in every direction, stretched along their flight. */
    private static ParticleEmitter glints(String name, int count, float speedMin, float speedMax, int lifeMin, int lifeMax, float size, float stretch) {
        ParticleEmitter e = burstEmitter(name, count, count);
        ParticleConfig c = e.config;
        c.setSimulationSpace(ParticleConfig.Space.World);
        c.setStartLifetime(range(lifeMin, lifeMax));
        c.setStartSpeed(range(speedMin, speedMax));
        c.setStartSize(uniform(range(size * 0.7f, size)));
        c.shape.setShape(sphere(0.08f));
        c.renderer.setRenderMode(ParticleRendererSetting.Mode.StretchedBillboard);
        c.renderer.setLengthScale(stretch);
        fadeOut(c, WHITE, CYAN);
        material(e, glow(0.62f, 0.88f, 1f, 1.6f));
        return e;
    }

    /** An ice ribbon (pale to cyan to clear blue) with Rime's energy-trail material. */
    private static ParticleEmitter ribbon(String name, float width, int lengthTicks, int color) {
        return FxKit.ribbon(name, width, lengthTicks, gradient(color, CYAN, 0x003A8FCC),
                recolor(KilaPresets.ENERGY_TRAIL.create(), 0.45f, 0.82f, 1f, 1.5f));
    }
}

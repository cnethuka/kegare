package com.cnethuka.kegare.innate_technique.rime.client;

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
 * DEV TOOL (client, dev environment only; see {@code /kegarefx export}). Builds Rime's Photon effects out of Photon's
 * own objects and writes them with Photon's own serializer, exactly as the editor exports a {@code .fx}: the FX's NBT,
 * plus {@code version = FXProject.VERSION}, gzip-compressed. Re-run after changing anything here.
 * <p>
 * Palette: pale cyan, white and deep blue, additive glow. Readable in PvP, never screen-filling: small particles, short
 * lifetimes, caps on counts. Effects that point somewhere face local +X (Photon turns +X to the look/motion direction).
 * Units (from Photon's source): lifetime in ticks, emission per tick, speed about blocks per second, size in blocks.
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
        return out;
    }

    /** Writes every effect under {@code <assetsRoot>/<namespace>/fx/<path>.fx}. Returns the files written. */
    public static List<Path> export(Path assetsRoot) throws IOException {
        List<Path> written = new java.util.ArrayList<>();
        for (Map.Entry<ResourceLocation, Supplier<FX>> e : effects().entrySet()) {
            ResourceLocation id = e.getKey();
            Path file = assetsRoot.resolve(id.getNamespace()).resolve("fx").resolve(id.getPath() + FX.SUFFIX);
            CompoundTag tag = e.getValue().get().serializeNBT(Platform.getFrozenRegistry());
            tag.putInt("version", FXProject.VERSION);
            Files.createDirectories(file.getParent());
            NbtIo.writeCompressed(tag, file);
            written.add(file);
        }
        return written;
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
        return fx(core, motes);
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
        mc.setStartSize(uniform(range(0.25f, 0.4f)));
        mc.emission.setEmissionRate(NumberFunction.constant(1f));
        mc.shape.setShape(sphere(0.1f));
        mc.colorOverLifetime.setEnable(true);
        mc.colorOverLifetime.setColor(gradient(0x00C2EEFA, 0x88C2EEFA, 0x0079C8EC));
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

        ParticleEmitter mist = burstEmitter("mist", 8, 10);
        ParticleConfig mc = mist.config;
        mc.setStartLifetime(range(18, 28));
        mc.setStartSpeed(range(0.5f, 1.5f));
        mc.setStartSize(uniform(range(0.6f, 1.1f)));
        mc.shape.setShape(sphere(0.3f));
        mc.colorOverLifetime.setEnable(true);
        mc.colorOverLifetime.setColor(gradient(0x00C2EEFA, 0x99C2EEFA, 0x0079C8EC));
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
        f.setStartSize(uniform(range(0.04f, 0.08f)));
        Sphere around = sphere(1.0f);
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

    // ================================================================ building blocks

    private static FX fx(IFXObject... objects) {
        FX fx = new FX();
        fx.getFxData().objects().addAll(List.of(objects));
        return fx;
    }

    private static ParticleEmitter emitter(String name, boolean looping, int duration) {
        ParticleEmitter e = new ParticleEmitter();
        e.setName(name);
        e.config.setLooping(looping);
        e.config.setDuration(duration);
        e.config.renderer.setUseGPUInstance(false);
        return e;
    }

    /** A one-shot burst of {@code count} particles (the emitter plays once). */
    private static ParticleEmitter burstEmitter(String name, int count, int maxParticles) {
        ParticleEmitter e = emitter(name, false, 40);
        e.config.setMaxParticles(maxParticles);
        e.config.emission.setEmissionRate(NumberFunction.constant(0));
        e.config.emission.getBursts().add(burst(0, count));
        return e;
    }

    /** One particle, once, that lives {@code lifetime} ticks at {@code size} (flashes, rings). */
    private static ParticleEmitter single(String name, int lifetime, float size) {
        ParticleEmitter e = burstEmitter(name, 1, 1);
        e.config.setStartLifetime(NumberFunction.constant(lifetime));
        e.config.setStartSpeed(NumberFunction.constant(0));
        e.config.setStartSize(uniform(NumberFunction.constant(size)));
        e.config.shape.setScale(new NumberFunction3(0, 0, 0));
        return e;
    }

    /**
     * A ribbon behind a moving anchor: one particle riding the anchor (local space) whose trail records its path.
     * The particle itself is not drawn. Built the way Photon's gallery builds its energy-trail showcase.
     */
    private static ParticleEmitter ribbon(String name, float width, int lengthTicks, int color) {
        ParticleEmitter e = emitter(name, true, 40);
        ParticleConfig c = e.config;
        c.setMaxParticles(1);
        c.setStartLifetime(NumberFunction.constant(40));
        c.setStartSpeed(NumberFunction.constant(0));
        c.setStartSize(uniform(NumberFunction.constant(width)));
        c.shape.setScale(new NumberFunction3(0, 0, 0));
        c.emission.setEmissionRate(NumberFunction.constant(0));
        c.emission.getBursts().add(burst(0, 1));
        c.renderer.setRenderMode(ParticleRendererSetting.Mode.None);
        c.renderer.getMaterials().clear();
        var trails = c.trails;
        trails.setEnable(true);
        trails.setRatio(1f);
        trails.setLifetime(NumberFunction.constant(lengthTicks));
        trails.setSizeAffectsWidth(true);
        trails.setColorOverLifetime(gradient(color, CYAN, 0x003A8FCC));
        trails.config.setWidthOverTrail(curve(1f, 0f));
        trails.config.renderer.getMaterials().clear();
        trails.config.renderer.getMaterials().add(new MaterialSetting(recolor(KilaPresets.ENERGY_TRAIL.create(), 0.45f, 0.82f, 1f, 1.5f)));
        MaterialRequirements.fix(trails.config.renderer.getMaterials(), trails.config.additionalGPUDataSetting, false,
                trails.config.renderer::isUseGPUInstance, trails.config.renderer::setUseGPUInstance);
        return e;
    }

    private static EmissionSetting.Burst burst(int time, int count) {
        EmissionSetting.Burst b = new EmissionSetting.Burst();
        b.time = time;
        b.cycles = 1;
        b.setCount(NumberFunction.constant(count));
        return b;
    }

    private static Sphere sphere(float radius) {
        Sphere s = new Sphere();
        s.setRadius(radius);
        return s;
    }

    private static Cone cone(float angle, float radius) {
        Cone c = new Cone();
        c.setAngle(angle);
        c.setRadius(radius);
        return c;
    }

    private static void material(ParticleEmitter emitter, IMaterial material) {
        emitter.config.renderer.getMaterials().clear();
        emitter.config.renderer.getMaterials().add(new MaterialSetting(material));
    }

    /** Additive glow sprite (Kila GLOW preset), recoloured. */
    private static KilaMaterial glow(float r, float g, float b, float intensity) {
        return recolor(KilaPresets.GLOW.create(), r, g, b, intensity);
    }

    /** Soft frosty mist (Kila SMOKE preset), pale blue, alpha-blended. */
    private static KilaMaterial mist() {
        KilaMaterial m = KilaPresets.SMOKE.create();
        m.color = new HDRColor(0.78f, 0.92f, 1f, 0.75f, 1f);
        m.worldLight = false;
        return m;
    }

    private static KilaMaterial recolor(KilaMaterial m, float r, float g, float b, float intensity) {
        m.color = new HDRColor(r, g, b, 1f, intensity);
        m.worldLight = false;
        if (m.blend == BlendMode.Preset.ALPHA) m.blend = BlendMode.Preset.ADDITIVE;
        return m;
    }

    private static NumberFunction range(float min, float max) {
        return new RandomConstant(min, max);
    }

    private static NumberFunction3 uniform(NumberFunction f) {
        return new NumberFunction3(f, f, f);
    }

    /** A 0..1 curve from {@code start} to {@code end} over the particle's life (smooth). */
    private static NumberFunction curve(float start, float end) {
        return new Curve(0, 1, 0, 1, "t", "v", new ECBCurves(0, start, 0.3f, start, 0.7f, end, 1, end));
    }

    private static Gradient gradient(int... argb) {
        return new Gradient(new GradientColor(argb));
    }

    private static void fadeOut(ParticleConfig c, int from, int to) {
        c.colorOverLifetime.setEnable(true);
        c.colorOverLifetime.setColor(gradient(from, to, to & 0x00FFFFFF));
    }

    private static void fadeIn(ParticleConfig c, int from, int to) {
        c.colorOverLifetime.setEnable(true);
        c.colorOverLifetime.setColor(gradient(from & 0x00FFFFFF, from, to));
    }

    private static void fadeInOut(ParticleConfig c, int peak, int edge) {
        c.colorOverLifetime.setEnable(true);
        c.colorOverLifetime.setColor(gradient(edge & 0x00FFFFFF, peak, edge & 0x00FFFFFF));
    }

    private static void shrink(ParticleConfig c) {
        c.sizeOverLifetime.setEnable(true);
        c.sizeOverLifetime.setSize(uniform(curve(1f, 0f)));
    }

    /** Grows from {@code from} (fraction) to full size over its life. */
    private static void grow(ParticleConfig c, float from) {
        c.sizeOverLifetime.setEnable(true);
        c.sizeOverLifetime.setSize(uniform(curve(from, 1f)));
    }

    private static void gravity(ParticleConfig c, float g) {
        c.physics.setEnable(true);
        c.physics.setHasCollision(false);
        c.physics.setGravity(NumberFunction.constant(g));
    }
}

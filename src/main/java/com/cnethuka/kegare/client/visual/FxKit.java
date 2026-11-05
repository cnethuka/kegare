package com.cnethuka.kegare.client.visual;

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
import com.lowdragmc.photon.client.gameobject.emitter.data.number.NumberFunction3;
import com.lowdragmc.photon.client.gameobject.emitter.data.number.NumberFunction;
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
 * DEV TOOL building blocks for authoring Photon effects in code (Rime's, Reverse Malice Technique's...): Photon's own
 * objects, written with Photon's own serializer exactly as the editor exports a {@code .fx}. See the authoring classes
 * for the conventions (local +X faces forward; lifetime in ticks, emission per tick, speed about blocks per second).
 */
public final class FxKit {
    private FxKit() {}

    /** Writes every effect under {@code <assetsRoot>/<namespace>/fx/<path>.fx}. Returns the files written. */
    public static List<Path> export(Map<ResourceLocation, Supplier<FX>> effects, Path assetsRoot) throws IOException {
        List<Path> written = new java.util.ArrayList<>();
        for (Map.Entry<ResourceLocation, Supplier<FX>> e : effects.entrySet()) {
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

    /**
     * A size curve that shoots from a stub to full size in the first 2.5% of the life (5 ticks of a 200-tick life),
     * then holds. ECBCurves takes cubic Bezier segments of 8 floats (start, two controls, end), each spanning its own
     * stretch of the life.
     */
    public static NumberFunction shootUp() {
        return new Curve(0, 1, 0, 1, "t", "v", new ECBCurves(
                0f, 0.05f, 0.004f, 1f, 0.012f, 1f, 0.025f, 1f,
                0.025f, 1f, 0.35f, 1f, 0.7f, 1f, 1f, 1f));
    }

    // ================================================================ building blocks

    public static FX fx(IFXObject... objects) {
        FX fx = new FX();
        fx.getFxData().objects().addAll(List.of(objects));
        return fx;
    }

    public static ParticleEmitter emitter(String name, boolean looping, int duration) {
        ParticleEmitter e = new ParticleEmitter();
        e.setName(name);
        e.config.setLooping(looping);
        e.config.setDuration(duration);
        e.config.renderer.setUseGPUInstance(false);
        return e;
    }

    /** A one-shot burst of {@code count} particles (the emitter plays once). */
    public static ParticleEmitter burstEmitter(String name, int count, int maxParticles) {
        ParticleEmitter e = emitter(name, false, 40);
        e.config.setMaxParticles(maxParticles);
        e.config.emission.setEmissionRate(NumberFunction.constant(0));
        e.config.emission.getBursts().add(burst(0, count));
        return e;
    }

    /** One particle, once, that lives {@code lifetime} ticks at {@code size} (flashes, rings). */
    public static ParticleEmitter single(String name, int lifetime, float size) {
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
     *
     * @param colors   the trail's color over its length (head to tail)
     * @param material the trail's material (e.g. a recolored {@code KilaPresets.ENERGY_TRAIL})
     */
    public static ParticleEmitter ribbon(String name, float width, int lengthTicks, Gradient colors, IMaterial material) {
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
        trails.setColorOverLifetime(colors);
        trails.config.setWidthOverTrail(curve(1f, 0f));
        trails.config.renderer.getMaterials().clear();
        trails.config.renderer.getMaterials().add(new MaterialSetting(material));
        MaterialRequirements.fix(trails.config.renderer.getMaterials(), trails.config.additionalGPUDataSetting, false,
                trails.config.renderer::isUseGPUInstance, trails.config.renderer::setUseGPUInstance);
        return e;
    }

    public static EmissionSetting.Burst burst(int time, int count) {
        EmissionSetting.Burst b = new EmissionSetting.Burst();
        b.time = time;
        b.cycles = 1;
        b.setCount(NumberFunction.constant(count));
        return b;
    }

    public static Sphere sphere(float radius) {
        Sphere s = new Sphere();
        s.setRadius(radius);
        return s;
    }

    public static Cone cone(float angle, float radius) {
        Cone c = new Cone();
        c.setAngle(angle);
        c.setRadius(radius);
        return c;
    }

    public static void material(ParticleEmitter emitter, IMaterial material) {
        emitter.config.renderer.getMaterials().clear();
        emitter.config.renderer.getMaterials().add(new MaterialSetting(material));
    }

    /** Additive glow sprite (Kila GLOW preset), recoloured. */
    public static KilaMaterial glow(float r, float g, float b, float intensity) {
        return recolor(KilaPresets.GLOW.create(), r, g, b, intensity);
    }

    /** Soft frosty mist (Kila SMOKE preset), pale blue, alpha-blended. */
    public static KilaMaterial mist() {
        KilaMaterial m = KilaPresets.SMOKE.create();
        m.color = new HDRColor(0.78f, 0.92f, 1f, 0.75f, 1f);
        m.worldLight = false;
        return m;
    }

    public static KilaMaterial recolor(KilaMaterial m, float r, float g, float b, float intensity) {
        m.color = new HDRColor(r, g, b, 1f, intensity);
        m.worldLight = false;
        if (m.blend == BlendMode.Preset.ALPHA) m.blend = BlendMode.Preset.ADDITIVE;
        return m;
    }

    public static NumberFunction range(float min, float max) {
        return new RandomConstant(min, max);
    }

    public static NumberFunction3 uniform(NumberFunction f) {
        return new NumberFunction3(f, f, f);
    }

    /** A 0..1 curve from {@code start} to {@code end} over the particle's life (smooth). */
    public static NumberFunction curve(float start, float end) {
        return new Curve(0, 1, 0, 1, "t", "v", new ECBCurves(0, start, 0.3f, start, 0.7f, end, 1, end));
    }

    public static Gradient gradient(int... argb) {
        return new Gradient(new GradientColor(argb));
    }

    public static void fadeOut(ParticleConfig c, int from, int to) {
        c.colorOverLifetime.setEnable(true);
        c.colorOverLifetime.setColor(gradient(from, to, to & 0x00FFFFFF));
    }

    public static void fadeIn(ParticleConfig c, int from, int to) {
        c.colorOverLifetime.setEnable(true);
        c.colorOverLifetime.setColor(gradient(from & 0x00FFFFFF, from, to));
    }

    public static void fadeInOut(ParticleConfig c, int peak, int edge) {
        c.colorOverLifetime.setEnable(true);
        c.colorOverLifetime.setColor(gradient(edge & 0x00FFFFFF, peak, edge & 0x00FFFFFF));
    }

    public static void shrink(ParticleConfig c) {
        c.sizeOverLifetime.setEnable(true);
        c.sizeOverLifetime.setSize(uniform(curve(1f, 0f)));
    }

    /** Grows from {@code from} (fraction) to full size over its life. */
    public static void grow(ParticleConfig c, float from) {
        c.sizeOverLifetime.setEnable(true);
        c.sizeOverLifetime.setSize(uniform(curve(from, 1f)));
    }

    public static void gravity(ParticleConfig c, float g) {
        c.physics.setEnable(true);
        c.physics.setHasCollision(false);
        c.physics.setGravity(NumberFunction.constant(g));
    }
}

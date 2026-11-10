package com.cnethuka.kegare.innate_technique.swap.client;

import static com.cnethuka.kegare.client.visual.FxKit.*;

import com.cnethuka.kegare.client.visual.FxKit;
import com.cnethuka.kegare.innate_technique.swap.SwapIds;
import com.lowdragmc.photon.client.fx.FX;
import com.lowdragmc.photon.client.gameobject.emitter.data.number.NumberFunction3;
import com.lowdragmc.photon.client.gameobject.emitter.data.shape.Sphere;
import com.lowdragmc.photon.client.gameobject.emitter.particle.ParticleConfig;
import com.lowdragmc.photon.client.gameobject.emitter.particle.ParticleEmitter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.resources.ResourceLocation;

/**
 * DEV TOOL (exported with {@code /kegare debug fx export}): Swap's Photon effects, built from Photon's own objects (see
 * {@link FxKit}). Photon only draws the glints on a swap's two ends; everything else Swap shows is drawn by {@link SwapFx}
 * (and Anchor's token by {@link AnchorRenderer}). Teal, never Rime's ice blue.
 */
public final class SwapFxAuthoring {
    private static final int PALE = 0xFFCCFBF1;
    private static final int TEAL = 0xFF14B8A6;

    private SwapFxAuthoring() {}

    public static Map<ResourceLocation, Supplier<FX>> effects() {
        Map<ResourceLocation, Supplier<FX>> out = new LinkedHashMap<>();
        out.put(SwapIds.FX_SWAP_DEPART, SwapFxAuthoring::depart);
        out.put(SwapIds.FX_SWAP_ARRIVE, SwapFxAuthoring::arrive);
        return out;
    }

    /**
     * Where someone left: a few tiny teal-white glints popping out of the body, gone in a few ticks. SwapFx draws the
     * afterimage and flicker ring; this is only the sparkle on top.
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
}

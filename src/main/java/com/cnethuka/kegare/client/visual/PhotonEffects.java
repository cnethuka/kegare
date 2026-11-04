package com.cnethuka.kegare.client.visual;

import com.cnethuka.kegare.Kegare;
import com.cnethuka.kegare.innatetechnique.visual.Visual;
import com.lowdragmc.photon.client.fx.BlockEffectExecutor;
import com.lowdragmc.photon.client.fx.EntityEffectExecutor;
import com.lowdragmc.photon.client.fx.FX;
import com.lowdragmc.photon.client.fx.FXEffectExecutor;
import com.lowdragmc.photon.client.fx.FXHelper;
import com.lowdragmc.photon.client.fx.FXRuntime;
import com.lowdragmc.photon.client.gameobject.IFXObject;
import com.lowdragmc.photon.client.gameobject.emitter.data.number.Constant;
import com.lowdragmc.photon.client.gameobject.emitter.particle.ParticleEmitter;
import java.util.Collection;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * Photon playback (client only). Effects live in {@code assets/<namespace>/fx/<path>.fx}. Anything that goes wrong
 * (missing or broken file, a Photon error) returns null so the caller falls back to vanilla particles; each failing
 * effect is logged once. Never throws.
 * <p>
 * Orientation: with a look/motion anchor Photon turns the effect so its local +X axis points forward, so effects are
 * authored facing +X.
 */
final class PhotonEffects {
    private static final Set<ResourceLocation> WARNED = ConcurrentHashMap.newKeySet();

    private PhotonEffects() {}

    @Nullable
    static FX load(ResourceLocation id) {
        try {
            FX fx = FXHelper.getFX(id);
            if (fx == null) warnOnce(id, "missing or failed to load");
            return fx;
        } catch (Throwable t) {
            warnOnce(id, t.toString());
            return null;
        }
    }

    /**
     * An effect attached to an entity, or null (then use the vanilla fallback).
     *
     * @param vanishWithEntity remove everything at once if the entity goes away (state loops); otherwise what is
     *                         already in the air fades out (trails, one-shots)
     */
    @Nullable
    static EntityEffectExecutor onEntity(ResourceLocation id, Entity entity, Visual.Anchor anchor, boolean reduced, boolean vanishWithEntity) {
        FX fx = load(id);
        if (fx == null) return null;
        try {
            EntityEffectExecutor.AutoRotate rotate = switch (anchor) {
                case EYES -> EntityEffectExecutor.AutoRotate.LOOK;
                case MOTION -> EntityEffectExecutor.AutoRotate.FORWARD;
                case BODY, FEET -> EntityEffectExecutor.AutoRotate.NONE;
            };
            EntityEffectExecutor executor = new EntityEffectExecutor(fx, entity.level(), entity, rotate);
            executor.setOffset(offset(entity, anchor));
            executor.setAllowMulti(true);
            executor.setForcedDeath(vanishWithEntity);
            executor.start();
            if (executor.getRuntime() == null) return null;
            if (reduced) reduce(executor.getRuntime());
            return executor;
        } catch (Throwable t) {
            warnOnce(id, t.toString());
            return null;
        }
    }

    /** A one-shot effect at a position, or null. */
    @Nullable
    static BlockEffectExecutor at(ResourceLocation id, Level level, Vec3 pos, boolean reduced) {
        FX fx = load(id);
        if (fx == null) return null;
        try {
            BlockPos block = BlockPos.containing(pos);
            BlockEffectExecutor executor = new BlockEffectExecutor(fx, level, block);
            // The root sits at the block's centre plus the offset.
            executor.setOffset(new Vector3f((float) (pos.x - block.getX() - 0.5), (float) (pos.y - block.getY() - 0.5),
                    (float) (pos.z - block.getZ() - 0.5)));
            executor.setAllowMulti(true);
            executor.setCheckState(false);
            executor.setForcedDeath(false);
            executor.start();
            if (executor.getRuntime() == null) return null;
            if (reduced) reduce(executor.getRuntime());
            return executor;
        } catch (Throwable t) {
            warnOnce(id, t.toString());
            return null;
        }
    }

    /** Stops future emission; particles already in the air fade out naturally. */
    static void stop(@Nullable FXEffectExecutor executor) {
        stop(executor, false);
    }

    /** @param immediately also remove what is already visible (a charge that just became a projectile) */
    static void stop(@Nullable FXEffectExecutor executor, boolean immediately) {
        if (executor == null) return;
        try {
            FXRuntime runtime = executor.getRuntime();
            if (runtime != null) runtime.destroy(immediately);
        } catch (Throwable t) {
            Kegare.LOGGER.debug("Couldn't stop a Photon effect", t);
        }
    }

    static boolean isPlaying(@Nullable FXEffectExecutor executor) {
        try {
            return executor != null && executor.getRuntime() != null && executor.getRuntime().isValid();
        } catch (Throwable t) {
            return false;
        }
    }

    /** Uniform scale of the whole effect (a growing charge). Photon applies the executor's scale only at start. */
    static void scale(@Nullable FXEffectExecutor executor, float scale) {
        try {
            if (executor != null && executor.getRuntime() != null) executor.getRuntime().getRoot().updateScale(new Vector3f(scale));
        } catch (Throwable t) {
            Kegare.LOGGER.debug("Couldn't scale a Photon effect", t);
        }
    }

    /** Warms the given effects up off the hot path (reads files, compiles shaders), and again after resource reloads. */
    static void preload(Collection<ResourceLocation> ids) {
        if (ids.isEmpty()) return;
        try {
            FXHelper.keepPreloaded(ids);
        } catch (Throwable t) {
            Kegare.LOGGER.warn("Couldn't preload Photon effects", t);
        }
    }

    /** REDUCED quality: about half the particles from every particle emitter (rate and cap). */
    private static void reduce(FXRuntime runtime) {
        for (IFXObject object : runtime.getObjects().values()) {
            if (!(object instanceof ParticleEmitter emitter)) continue;
            var values = emitter.runtime();
            if (values.emission.emissionRate.authored() instanceof Constant rate) {
                values.emission.emissionRate.set(new Constant(rate.getNumber().floatValue() * 0.5f));
            }
            Integer max = values.maxParticles.authored();
            if (max != null && max > 1) values.maxParticles.set(Math.max(1, max / 2));
        }
    }

    private static Vector3f offset(Entity entity, Visual.Anchor anchor) {
        float eye = entity.getEyeHeight();
        float height = entity.getBbHeight();
        return switch (anchor) {
            case EYES -> new Vector3f();
            case BODY, MOTION -> new Vector3f(0, -(eye - height / 2f), 0);
            case FEET -> new Vector3f(0, -eye + 0.05f, 0);
        };
    }

    private static void warnOnce(ResourceLocation id, String why) {
        if (WARNED.add(id)) Kegare.LOGGER.warn("Photon effect {} can't play ({}); using vanilla particles instead", id, why);
    }
}

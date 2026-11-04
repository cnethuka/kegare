package com.cnethuka.kegare.client.visual;

import com.cnethuka.kegare.config.KegareClientConfig;
import com.cnethuka.kegare.config.KegareClientConfig.VfxQuality;
import com.cnethuka.kegare.innatetechnique.visual.Visual;
import com.cnethuka.kegare.network.VisualPayload;
import com.lowdragmc.photon.client.fx.FXEffectExecutor;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * Client side of cosmetics: plays {@link VisualPayload}s on the right entity or position. Purely visual; nothing is
 * ever sent back. Loops are keyed per entity so a stop only ends its own loop. Photon effects that can't play, and
 * everything when {@code vfxQuality = VANILLA}, fall back to vanilla particles.
 */
public final class ClientVisuals {
    /** A charge effect starts at this fraction of its size and grows to full over the charge time. */
    private static final float GROW_FROM = 0.3f;

    private static final class Loop {
        final Visual visual;
        @Nullable final FXEffectExecutor executor;
        final int growTicks;
        final long startTick;
        final boolean animation;

        Loop(Visual visual, @Nullable FXEffectExecutor executor, int growTicks, long startTick, boolean animation) {
            this.visual = visual;
            this.executor = executor;
            this.growTicks = growTicks;
            this.startTick = startTick;
            this.animation = animation;
        }

        /** Vanilla particles stand in while there is an effect but no Photon runtime. */
        boolean usesFallback() {
            return visual.effect().isPresent() && executor == null;
        }
    }

    private static final Map<Integer, Map<String, Loop>> LOOPS = new HashMap<>();

    private ClientVisuals() {}

    public static void handle(VisualPayload payload) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        switch (payload.action()) {
            case PLAY_AT -> playAt(level, payload.pos(), payload.visual());
            case PLAY_ONCE -> {
                Entity entity = level.getEntity(payload.entityId());
                if (entity != null) playOnce(entity, payload.visual());
            }
            case START_LOOP -> {
                Entity entity = level.getEntity(payload.entityId());
                if (entity != null) startLoop(entity, payload.key(), payload.visual(), payload.growTicks());
            }
            case STOP -> stopLoop(level, payload.entityId(), payload.key());
            case STOP_ALL -> stopAll(level, payload.entityId());
        }
    }

    // ---------------------------------------------------------------- playback

    public static void playOnce(Entity entity, Visual visual) {
        visual.animation().ifPresent(animation -> {
            if (entity instanceof AbstractClientPlayer player) PlayerAnimations.play(player, animation, false);
        });
        visual.effect().ifPresent(effect -> {
            VfxQuality quality = KegareClientConfig.VFX_QUALITY.get();
            FXEffectExecutor executor = quality == VfxQuality.VANILLA ? null
                    : PhotonEffects.onEntity(effect, entity, visual.anchor(), quality == VfxQuality.REDUCED);
            if (executor == null) {
                VanillaFallback.Spec spec = VanillaFallback.spec(effect);
                VanillaFallback.spawn(entity.level(), anchorPos(entity, visual.anchor()), entity.getLookAngle(), spec, spec.burst());
            }
        });
    }

    public static void startLoop(Entity entity, String key, Visual visual, int growTicks) {
        stopLoop(entity.level(), entity.getId(), key);
        boolean animation = false;
        if (visual.animation().isPresent() && entity instanceof AbstractClientPlayer player) {
            PlayerAnimations.play(player, visual.animation().get(), true);
            animation = true;
        }
        FXEffectExecutor executor = null;
        if (visual.effect().isPresent()) {
            VfxQuality quality = KegareClientConfig.VFX_QUALITY.get();
            if (quality != VfxQuality.VANILLA) {
                executor = PhotonEffects.onEntity(visual.effect().get(), entity, visual.anchor(), quality == VfxQuality.REDUCED);
                if (growTicks > 0) PhotonEffects.scale(executor, GROW_FROM);
            }
        }
        LOOPS.computeIfAbsent(entity.getId(), id -> new HashMap<>())
                .put(key, new Loop(visual, executor, growTicks, entity.level().getGameTime(), animation));
    }

    public static void stopLoop(net.minecraft.world.level.Level level, int entityId, String key) {
        Map<String, Loop> loops = LOOPS.get(entityId);
        if (loops == null) return;
        Loop loop = loops.remove(key);
        if (loops.isEmpty()) LOOPS.remove(entityId);
        if (loop != null) end(level.getEntity(entityId), loop);
    }

    public static void stopAll(net.minecraft.world.level.Level level, int entityId) {
        Map<String, Loop> loops = LOOPS.remove(entityId);
        Entity entity = level.getEntity(entityId);
        if (loops != null) loops.values().forEach(loop -> end(entity, loop));
        if (entity instanceof AbstractClientPlayer player) PlayerAnimations.stopAll(player);
    }

    public static void playAt(ClientLevel level, Vec3 pos, Visual visual) {
        visual.effect().ifPresent(effect -> {
            VfxQuality quality = KegareClientConfig.VFX_QUALITY.get();
            FXEffectExecutor executor = quality == VfxQuality.VANILLA ? null
                    : PhotonEffects.at(effect, level, pos, quality == VfxQuality.REDUCED);
            if (executor == null) {
                VanillaFallback.Spec spec = VanillaFallback.spec(effect);
                VanillaFallback.spawn(level, pos, new Vec3(0, 1, 0), spec, spec.burst());
            }
        });
    }

    /** A projectile's trail, started by the projectile itself on the client; ends when the projectile is gone. */
    public static void startTrail(Entity projectile, ResourceLocation effect) {
        startLoop(projectile, "trail", Visual.effect(effect, Visual.Anchor.MOTION), 0);
    }

    private static void end(@Nullable Entity entity, Loop loop) {
        PhotonEffects.stop(loop.executor);
        if (loop.animation && entity instanceof AbstractClientPlayer player) {
            loop.visual.animation().ifPresent(animation -> PlayerAnimations.stop(player, animation));
        }
    }

    // ---------------------------------------------------------------- per tick

    /** Client tick: grow charge effects, drive vanilla fallbacks, and drop loops whose entity is gone. */
    public static void tick() {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            clear();
            return;
        }
        long now = level.getGameTime();
        Iterator<Map.Entry<Integer, Map<String, Loop>>> entities = LOOPS.entrySet().iterator();
        while (entities.hasNext()) {
            Map.Entry<Integer, Map<String, Loop>> entry = entities.next();
            Entity entity = level.getEntity(entry.getKey());
            if (entity == null || entity.isRemoved()) {
                entry.getValue().values().forEach(loop -> PhotonEffects.stop(loop.executor));
                entities.remove();
                continue;
            }
            for (Loop loop : entry.getValue().values()) {
                if (loop.growTicks > 0 && loop.executor != null) {
                    float t = Math.min(1f, (now - loop.startTick) / (float) loop.growTicks);
                    PhotonEffects.scale(loop.executor, GROW_FROM + (1f - GROW_FROM) * t);
                }
                if (loop.usesFallback()) {
                    VanillaFallback.Spec spec = VanillaFallback.spec(loop.visual.effect().get());
                    VanillaFallback.spawn(level, anchorPos(entity, loop.visual.anchor()), entity.getLookAngle(), spec, spec.perTick());
                }
            }
        }
    }

    /** Leaving a world: forget everything (Photon drops its own particles with the level). */
    public static void clear() {
        LOOPS.values().forEach(loops -> loops.values().forEach(loop -> PhotonEffects.stop(loop.executor)));
        LOOPS.clear();
        PlayerAnimations.clear();
    }

    private static Vec3 anchorPos(Entity entity, Visual.Anchor anchor) {
        return switch (anchor) {
            case EYES -> entity.getEyePosition().add(entity.getLookAngle().scale(0.6));
            case BODY, MOTION -> entity.position().add(0, entity.getBbHeight() / 2.0, 0);
            case FEET -> entity.position().add(0, 0.1, 0);
        };
    }
}

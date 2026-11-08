package com.cnethuka.kegare.client.visual;

import com.cnethuka.kegare.config.KegareClientConfig;
import com.cnethuka.kegare.config.KegareClientConfig.VfxQuality;
import com.cnethuka.kegare.innatetechnique.visual.Visual;
import com.cnethuka.kegare.network.VisualPayload;
import com.lowdragmc.photon.client.fx.FXEffectExecutor;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
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
        /** Projectile trails fade out; every other loop shows a state and must vanish the moment it ends. */
        final boolean trail;

        Loop(Visual visual, @Nullable FXEffectExecutor executor, int growTicks, long startTick, boolean animation, boolean trail) {
            this.visual = visual;
            this.executor = executor;
            this.growTicks = growTicks;
            this.startTick = startTick;
            this.animation = animation;
            this.trail = trail;
        }

        /** Vanilla particles stand in while there is an effect but no Photon runtime (not for one its move draws itself). */
        boolean usesFallback() {
            return visual.effect().isPresent() && executor == null && !DRAWN_BY_MOVE.contains(visual.effect().get());
        }
    }

    private static final Map<Integer, Map<String, Loop>> LOOPS = new HashMap<>();
    /** Effects their move draws itself: neither Photon nor vanilla particles, see {@link #drawnByMove}. */
    private static final Set<ResourceLocation> DRAWN_BY_MOVE = new HashSet<>();
    private static final String TRAIL_KEY = "trail";

    private ClientVisuals() {}

    public static void handle(VisualPayload payload) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        switch (payload.action()) {
            case PLAY_AT -> {
                PlayAtHandler handler = payload.visual().effect().map(PLAY_AT_HANDLERS::get).orElse(null);
                if (handler == null || !handler.handle(level, payload.pos(), payload.entityId(), payload.visual())) {
                    playAt(level, payload.pos(), payload.visual());
                }
            }
            case PLAY_ONCE -> {
                Entity entity = level.getEntity(payload.entityId());
                if (entity != null) playOnce(entity, payload.visual());
            }
            case START_LOOP -> {
                Entity entity = level.getEntity(payload.entityId());
                if (entity != null) startLoop(entity, payload.key(), payload.visual(), payload.growTicks(), payload.lifetimeTicks(),
                        payload.elapsedTicks());
            }
            case STOP -> stopLoop(level, payload.entityId(), payload.key());
            case STOP_ALL -> stopAll(level, payload.entityId());
        }
    }

    // ---------------------------------------------------------------- playback

    public static void playOnce(Entity entity, Visual visual) {
        LISTENERS.forEach(l -> l.playedOnce(entity, visual));
        visual.animation().ifPresent(animation -> {
            if (entity instanceof AbstractClientPlayer player) PlayerAnimations.play(player, animation, false);
        });
        visual.effect().filter(effect -> !DRAWN_BY_MOVE.contains(effect)).map(effect -> firstPersonVariant(entity, effect)).ifPresent(effect -> {
            VfxQuality quality = KegareClientConfig.VFX_QUALITY.get();
            FXEffectExecutor executor = quality == VfxQuality.VANILLA ? null
                    : PhotonEffects.onEntity(effect, entity, visual.anchor(), quality == VfxQuality.REDUCED, false);
            PhotonEffects.tint(executor, visual.tint());
            if (executor == null) {
                VanillaFallback.Spec spec = VanillaFallback.spec(effect).tinted(visual.tint());
                VanillaFallback.spawn(entity.level(), anchorPos(entity, visual.anchor()), entity.getLookAngle(), spec, spec.burst());
            }
        });
    }

    public static void startLoop(Entity entity, String key, Visual visual, int growTicks) {
        startLoop(entity, key, visual, growTicks, 0);
    }

    public static void startLoop(Entity entity, String key, Visual visual, int growTicks, int lifetimeTicks) {
        startLoop(entity, key, visual, growTicks, lifetimeTicks, 0);
    }

    /** @param elapsedTicks how long it has already been running (a loop sent to a viewer who missed its start) */
    public static void startLoop(Entity entity, String key, Visual visual, int growTicks, int lifetimeTicks, int elapsedTicks) {
        stopLoop(entity.level(), entity.getId(), key);
        boolean animation = false;
        if (visual.animation().isPresent() && entity instanceof AbstractClientPlayer player) {
            PlayerAnimations.play(player, visual.animation().get(), true);
            animation = true;
        }
        boolean trail = TRAIL_KEY.equals(key) || visual.linger();
        FXEffectExecutor executor = null;
        if (visual.effect().isPresent() && !DRAWN_BY_MOVE.contains(visual.effect().get())) {
            VfxQuality quality = KegareClientConfig.VFX_QUALITY.get();
            if (quality != VfxQuality.VANILLA) {
                executor = PhotonEffects.onEntity(firstPersonVariant(entity, visual.effect().get()), entity, visual.anchor(),
                        quality == VfxQuality.REDUCED, !trail);
                PhotonEffects.tint(executor, visual.tint());
                if (growTicks > 0) PhotonEffects.scale(executor, GROW_FROM + (1f - GROW_FROM) * Math.min(1f, elapsedTicks / (float) growTicks));
                if (lifetimeTicks > 0) PhotonEffects.persistLifetime(executor, lifetimeTicks);
            }
        }
        LOOPS.computeIfAbsent(entity.getId(), id -> new HashMap<>())
                .put(key, new Loop(visual, executor, growTicks, entity.level().getGameTime() - Math.max(0, elapsedTicks), animation, trail));
        LISTENERS.forEach(l -> l.loopStarted(entity, key, visual));
    }

    public static void stopLoop(net.minecraft.world.level.Level level, int entityId, String key) {
        Map<String, Loop> loops = LOOPS.get(entityId);
        if (loops == null) return;
        Loop loop = loops.remove(key);
        if (loops.isEmpty()) LOOPS.remove(entityId);
        if (loop == null) return;
        Entity entity = level.getEntity(entityId);
        end(entity, loop);
        if (entity != null) stopped(entity, key, loop);
    }

    private static void stopped(Entity entity, String key, Loop loop) {
        float ticks = entity.level().getGameTime() - loop.startTick;
        LISTENERS.forEach(l -> l.loopStopped(entity, key, ticks, loop.growTicks));
    }

    public static void stopAll(net.minecraft.world.level.Level level, int entityId) {
        Map<String, Loop> loops = LOOPS.remove(entityId);
        Entity entity = level.getEntity(entityId);
        if (loops != null) {
            loops.values().forEach(loop -> end(entity, loop));
            if (entity != null) loops.forEach((key, loop) -> stopped(entity, key, loop));
        }
        if (entity instanceof AbstractClientPlayer player) PlayerAnimations.stopAll(player);
    }

    /** Positional effects closer than this to a first-person camera are skipped (they would block the view). */
    private static final double NEAR_CAMERA = 2.0;
    private static final Map<ResourceLocation, Double> NEAR_CAMERA_OVERRIDES = new HashMap<>();

    private static final Map<ResourceLocation, ResourceLocation> FIRST_PERSON_VARIANTS = new HashMap<>();

    /** An effect riding a player that would fill their own screen: in first person, they see {@code variant} instead. Everyone else sees {@code effect}. */
    public static void setFirstPersonVariant(ResourceLocation effect, ResourceLocation variant) {
        FIRST_PERSON_VARIANTS.put(effect, variant);
    }

    private static ResourceLocation firstPersonVariant(Entity entity, ResourceLocation effect) {
        Minecraft mc = Minecraft.getInstance();
        if (entity != mc.player || !mc.options.getCameraType().isFirstPerson()) return effect;
        return FIRST_PERSON_VARIANTS.getOrDefault(effect, effect);
    }

    /** An effect that has to be seen from closer than usual (a hit landing on someone at arm's length). */
    public static void setNearCameraDistance(ResourceLocation effect, double distance) {
        NEAR_CAMERA_OVERRIDES.put(effect, distance);
    }

    public static void playAt(ClientLevel level, Vec3 pos, Visual visual) {
        visual.effect().ifPresent(effect -> {
            VfxQuality quality = KegareClientConfig.VFX_QUALITY.get();
            // On your own body in first person (you got hit, you broke free): anything there, even small particles,
            // would sit right in front of your eyes. Skip it; you still get the hurt flash, overlays, HUD and sound.
            if (nearFirstPersonCamera(pos, NEAR_CAMERA_OVERRIDES.getOrDefault(effect, NEAR_CAMERA))) return;
            FXEffectExecutor executor = quality == VfxQuality.VANILLA ? null
                    : PhotonEffects.at(effect, level, pos, quality == VfxQuality.REDUCED);
            PhotonEffects.tint(executor, visual.tint());
            if (executor == null) {
                VanillaFallback.Spec spec = VanillaFallback.spec(effect).tinted(visual.tint());
                VanillaFallback.spawn(level, pos, new Vec3(0, 1, 0), spec, spec.burst());
            }
        });
    }

    /**
     * A client-only effect at a position that keeps playing until stopped (things only this client should see, such
     * as the green orb). Returns null if Photon can't play it or {@code vfxQuality = VANILLA}: then the caller draws
     * its own vanilla stand-in.
     */
    @Nullable
    public static PositionalEffect startAt(ResourceLocation effect, Vec3 pos) {
        ClientLevel level = Minecraft.getInstance().level;
        VfxQuality quality = KegareClientConfig.VFX_QUALITY.get();
        if (level == null || quality == VfxQuality.VANILLA) return null;
        PhotonEffects.MovableEffect executor = PhotonEffects.movableAt(effect, level, pos, quality == VfxQuality.REDUCED);
        return executor == null ? null : new PositionalEffect(executor);
    }

    /** Handle to a {@link #startAt} effect. */
    public static final class PositionalEffect {
        private final PhotonEffects.MovableEffect executor;

        private PositionalEffect(PhotonEffects.MovableEffect executor) {
            this.executor = executor;
        }

        /** Call once a tick; it glides there between ticks. */
        public void moveTo(Vec3 pos) {
            executor.moveTo(pos);
        }

        public void scale(float scale) {
            PhotonEffects.scale(executor, scale);
        }

        /** Its {@code tint...} emitters in this color, for what they emit from now on. */
        public void tint(int argb) {
            PhotonEffects.tint(executor, argb);
        }

        /** @param immediately remove what is visible now; otherwise it fades out over its particles' lifetimes */
        public void stop(boolean immediately) {
            PhotonEffects.stop(executor, immediately);
        }
    }

    /** A projectile's trail, started by the projectile itself on the client; ends when the projectile is gone. */
    public static void startTrail(Entity projectile, ResourceLocation effect) {
        startLoop(projectile, TRAIL_KEY, Visual.effect(effect, Visual.Anchor.MOTION), 0);
    }

    /** Projectiles older than this start their trail wherever they are. */
    private static final int TRAIL_WAIT_TICKS = 10;

    /**
     * A trail drawn at a size (a projectile that comes in sizes). With {@code clearOfOwnEyes} above 0, your own projectile in first
     * person doesn't start it until it's that far from your camera: it's thrown from between your eyes.
     *
     * @return false if it's waiting for that
     */
    public static boolean startTrail(Entity projectile, ResourceLocation effect, float scale, double clearOfOwnEyes) {
        if (clearOfOwnEyes > 0 && projectile.tickCount < TRAIL_WAIT_TICKS && projectile instanceof Projectile p) {
            Minecraft mc = Minecraft.getInstance();
            if (p.getOwner() == mc.player && mc.options.getCameraType().isFirstPerson()
                    && mc.gameRenderer.getMainCamera().getPosition().distanceToSqr(p.position()) < clearOfOwnEyes * clearOfOwnEyes) {
                return false;
            }
        }
        startTrail(projectile, effect);
        Map<String, Loop> loops = LOOPS.get(projectile.getId());
        Loop loop = loops == null ? null : loops.get(TRAIL_KEY);
        if (loop != null && loop.executor != null && scale != 1f) PhotonEffects.scale(loop.executor, scale);
        return true;
    }

    private static void end(@Nullable Entity entity, Loop loop) {
        // A state loop (shell, cone, charge, frozen) vanishes at once: a lingering shell would lie to opponents.
        // Only trails (behind a projectile, or a lingering ground trail) fade out.
        PhotonEffects.stop(loop.executor, !loop.trail);
        if (loop.animation && entity instanceof AbstractClientPlayer player) {
            loop.visual.animation().ifPresent(animation -> PlayerAnimations.stop(player, animation));
        }
    }

    // ---------------------------------------------------------------- for moves that draw their own

    /** Told when loops stop and one-shots play, for a move that draws part of itself (Glacial Lance's lance in the hand). */
    public interface Listener {
        /** Loop {@code key} started on {@code entity} ({@link #loopTime} says for how long and over how many ticks it grows). */
        default void loopStarted(Entity entity, String key, Visual visual) {}

        /** Loop {@code key} on {@code entity} stopped after {@code ticks}; it was growing over {@code growTicks}. */
        default void loopStopped(Entity entity, String key, float ticks, int growTicks) {}

        default void playedOnce(Entity entity, Visual visual) {}
    }

    private static final List<Listener> LISTENERS = new CopyOnWriteArrayList<>();

    public static void listen(Listener listener) {
        LISTENERS.add(listener);
    }

    /**
     * {@code effect} is drawn by its move (found through a {@link Listener}): a loop of it is kept and timed as usual, and a
     * one-shot still reaches the listeners, but neither plays a Photon effect or vanilla particles.
     */
    public static void drawnByMove(ResourceLocation effect) {
        DRAWN_BY_MOVE.add(effect);
    }

    public static boolean isDrawnByMove(ResourceLocation effect) {
        return DRAWN_BY_MOVE.contains(effect);
    }

    /** How long a loop has run, in ticks (partial tick included), and how long it grows for. */
    public record LoopTime(float ticks, int growTicks) {}

    /** How long loop {@code key} has run on {@code entity}; null if it isn't running there. */
    @Nullable
    public static LoopTime loopTime(Entity entity, String key, float partialTick) {
        Map<String, Loop> loops = LOOPS.get(entity.getId());
        Loop loop = loops == null ? null : loops.get(key);
        if (loop == null) return null;
        return new LoopTime(Math.max(0f, entity.level().getGameTime() - loop.startTick + partialTick), loop.growTicks);
    }

    /** Plays a positional effect itself: return false to let it play as usual. */
    @FunctionalInterface
    public interface PlayAtHandler {
        /** @param entityId the entity the server named with it, or -1 */
        boolean handle(ClientLevel level, Vec3 pos, int entityId, Visual visual);
    }

    private static final Map<ResourceLocation, PlayAtHandler> PLAY_AT_HANDLERS = new HashMap<>();

    public static void onPlayAt(ResourceLocation effect, PlayAtHandler handler) {
        PLAY_AT_HANDLERS.put(effect, handler);
    }

    /**
     * An effect at a position at a size: Photon, or vanilla particles for {@code vfxQuality = VANILLA}, and nothing at all if it's
     * right by your own first-person camera.
     */
    public static void playAt(ClientLevel level, Vec3 pos, ResourceLocation effect, int tint, float scale) {
        if (nearFirstPersonCamera(pos, NEAR_CAMERA_OVERRIDES.getOrDefault(effect, NEAR_CAMERA))) return;
        VfxQuality quality = KegareClientConfig.VFX_QUALITY.get();
        FXEffectExecutor executor = quality == VfxQuality.VANILLA ? null : PhotonEffects.at(effect, level, pos, quality == VfxQuality.REDUCED);
        PhotonEffects.tint(executor, tint);
        if (executor != null && scale != 1f) PhotonEffects.scale(executor, scale);
        if (executor == null) {
            VanillaFallback.Spec spec = VanillaFallback.spec(effect).tinted(tint);
            VanillaFallback.spawn(level, pos, new Vec3(0, 1, 0), spec, Math.max(1, Math.round(spec.burst() * scale)));
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
                entry.getValue().values().forEach(loop -> PhotonEffects.stop(loop.executor, !loop.trail));
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

    private static boolean nearFirstPersonCamera(Vec3 pos, double distance) {
        Minecraft mc = Minecraft.getInstance();
        if (!mc.options.getCameraType().isFirstPerson()) return false;
        return mc.gameRenderer.getMainCamera().getPosition().distanceToSqr(pos) < distance * distance;
    }

    private static Vec3 anchorPos(Entity entity, Visual.Anchor anchor) {
        return switch (anchor) {
            case EYES -> entity.getEyePosition().add(entity.getLookAngle().scale(0.6));
            case BODY, MOTION -> entity.position().add(0, entity.getBbHeight() / 2.0, 0);
            case FEET -> entity.position().add(0, 0.1, 0);
        };
    }
}

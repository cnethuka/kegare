package com.cnethuka.kegare.innate_technique.time_manipulation.client;

import com.cnethuka.kegare.Kegare;
import com.cnethuka.kegare.impact.client.ImpactClient;
import com.cnethuka.kegare.impact.client.ScreenFx;
import com.cnethuka.kegare.innate_technique.time_manipulation.TimeNet;
import com.cnethuka.kegare.sound.KegareSounds;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.Input;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.client.event.sound.PlaySoundEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/**
 * Standstill on the client (DESIGN §3.26), mirroring the server's {@code TimeStops}: everything frozen there is frozen here too, its
 * tick skipped (its old position is its position, so it doesn't drift between frames, and {@code LevelRendererMixin} draws it at a
 * fixed partial tick so nothing shivers). Frozen yourself: no movement, attack or use keys, and silence but for Standstill's own three
 * sounds (the stop, its slow tick, the resume). The gray comes from the server's screen effect; the inverted flash it starts on is drawn
 * here, at Full only (Reduced and Vanilla never flash the whole screen). Everyone inside hears one slow tick each second it stands.
 */
public final class TimeStopClient {
    /** Standstill's inverted frame. */
    public static final ResourceLocation INVERT = Kegare.id("invert");
    private static TimeNet.Stop stop = TimeNet.Stop.none();
    private static final Set<Integer> FREE = new HashSet<>();
    /** Projectiles the caster or someone immune threw into it, and the ticks they still fly. */
    private static final Map<Integer, Integer> FLIGHT = new HashMap<>();
    private static long startedAt = -1;

    private TimeStopClient() {}

    public static void init() {
        TimeNet.Stop.setClientHandler(TimeStopClient::onStop);
        NeoForge.EVENT_BUS.addListener(TimeStopClient::onEntityTick);
        NeoForge.EVENT_BUS.addListener(TimeStopClient::onJoin);
        NeoForge.EVENT_BUS.addListener(TimeStopClient::onInput);
        NeoForge.EVENT_BUS.addListener(TimeStopClient::onKey);
        NeoForge.EVENT_BUS.addListener(TimeStopClient::onSound);
        NeoForge.EVENT_BUS.addListener(TimeStopClient::onClientTick);
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut e) -> clear());
    }

    private static void onStop(TimeNet.Stop s) {
        Minecraft mc = Minecraft.getInstance();
        boolean was = stop.active();
        stop = s;
        FREE.clear();
        FREE.addAll(s.free());
        if (!s.active()) {
            FLIGHT.clear();
            startedAt = -1;
            return;
        }
        if (!was && mc.level != null) {
            startedAt = mc.level.getGameTime();
            if (mc.player != null && inside(mc.player)) {
                mc.getSoundManager().stop(); // the hush; the click itself comes right after
                if (ImpactClient.impactFrames()) ScreenFx.play(INVERT, 50L, 0L);
            }
        }
    }

    public static boolean active() {
        return stop.active();
    }

    private static boolean inside(Entity e) {
        double r = stop.radius();
        return e.position().distanceToSqr(stop.centre()) <= r * r;
    }

    /** Whether time is stopped for this entity on this client right now. */
    public static boolean frozen(Entity e) {
        if (!stop.active()) return false;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || e.level() != mc.level) return false;
        int id = e.getId();
        if (id == stop.caster() || FREE.contains(id)) return false;
        if (e instanceof Player p && p.isSpectator()) return false;
        if (e.getType().is(Tags.EntityTypes.BOSSES)) return false; // never stopped (DESIGN §4)
        for (Entity passenger : e.getPassengers()) if (passenger instanceof Player && !frozen(passenger)) return false;
        Integer flying = FLIGHT.get(id);
        if (flying != null && flying > 0) return false;
        return inside(e);
    }

    /** What a frozen entity is drawn at: the end of its last tick, every frame, so it holds dead still. */
    public static float partialTick(Entity e, float partialTick) {
        return stop.active() && frozen(e) ? 1f : partialTick;
    }

    private static void onEntityTick(EntityTickEvent.Pre event) {
        Entity e = event.getEntity();
        if (!e.level().isClientSide || !stop.active()) return;
        Integer flying = FLIGHT.get(e.getId());
        if (flying != null && flying > 0) {
            FLIGHT.put(e.getId(), flying - 1);
            return;
        }
        if (!frozen(e)) return;
        event.setCanceled(true);
        e.tickCount--;
    }

    private static void onJoin(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide || !stop.active() || !(event.getEntity() instanceof Projectile p)) return;
        Entity owner = p.getOwner();
        if (owner != null && (owner.getId() == stop.caster() || FREE.contains(owner.getId()))) FLIGHT.put(p.getId(), stop.flightTicks());
    }

    private static boolean selfFrozen() {
        Minecraft mc = Minecraft.getInstance();
        return stop.active() && mc.player != null && frozen(mc.player);
    }

    private static void onInput(MovementInputUpdateEvent event) {
        if (!selfFrozen()) return;
        Input in = event.getInput();
        in.forwardImpulse = 0f;
        in.leftImpulse = 0f;
        in.up = in.down = in.left = in.right = false;
        in.jumping = false;
        in.shiftKeyDown = false;
    }

    private static void onKey(InputEvent.InteractionKeyMappingTriggered event) {
        if (!selfFrozen()) return;
        event.setCanceled(true);
        event.setSwingHand(false);
    }

    /** Frozen, you hear nothing but Standstill itself. */
    private static void onSound(PlaySoundEvent event) {
        if (event.getSound() == null || !selfFrozen()) return;
        ResourceLocation id = event.getSound().getLocation();
        if (id.equals(KegareSounds.TIME_STOP.getId()) || id.equals(KegareSounds.TIME_STOP_TICK.getId()) || id.equals(KegareSounds.TIME_RESUME.getId())) {
            return;
        }
        event.setSound(null);
    }

    private static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (!stop.active() || mc.level == null || mc.player == null || startedAt < 0) return;
        long t = mc.level.getGameTime() - startedAt;
        if (t > 0 && t % 20 == 0 && mc.level.getGameTime() < stop.until() && inside(mc.player)) {
            mc.getSoundManager().play(SimpleSoundInstance.forUI(KegareSounds.TIME_STOP_TICK.value(), 1f, 0.7f));
        }
    }

    public static void clear() {
        stop = TimeNet.Stop.none();
        FREE.clear();
        FLIGHT.clear();
        startedAt = -1;
    }
}

package com.cnethuka.kegare.sixeyes;

import com.cnethuka.kegare.innatetechnique.CastManager;
import com.cnethuka.kegare.innatetechnique.CastState;
import com.cnethuka.kegare.innatetechnique.CastType;
import com.cnethuka.kegare.innatetechnique.InnateTechnique;
import com.cnethuka.kegare.innatetechnique.InnateTechniqueData;
import com.cnethuka.kegare.innatetechnique.InnateTechniqueService;
import com.cnethuka.kegare.malice.MaliceData;
import com.cnethuka.kegare.malice.MaliceService;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Six Eyes' Reads (DESIGN §3.32), at a glance: every player near enough to be in view gets a compact Read automatically
 * (their innate technique and current Malice; a void says only "Void"), and the one nearest the bearer's crosshair gets full detail: what
 * they're charging or channelling and how far, their cooldowns and their active toggles. The server decides the focus from where the bearer
 * looks. Never Grade (private, §3.21), never traits, nothing §1 keeps secret. Mobs' Reads (their kind and flow) come from what the eyes
 * already see ({@link Sight}).
 */
public final class Reads {
    /** Players this close are read (render distance's reach, round). */
    public static final double RANGE = 96.0;
    /** The focus: within this many degrees of the crosshair, nearest to it. */
    public static final double FOCUS_DEGREES = 20.0;
    public static final int EVERY = 4;
    /** Who each bearer focused last, to tell listeners of a new one. */
    private static final Map<UUID, Integer> FOCUSED = new HashMap<>();
    /** Told when a bearer's focus moves to someone new (Attunement; "Something's Missing"). */
    private static final List<BiConsumer<ServerPlayer, ServerPlayer>> FOCUS_LISTENERS = new CopyOnWriteArrayList<>();

    private Reads() {}

    static void init() {
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post e) -> {
            if (e.getServer().getTickCount() % EVERY == 0) tick(e.getServer());
        });
    }

    public static void addFocusListener(BiConsumer<ServerPlayer, ServerPlayer> listener) {
        FOCUS_LISTENERS.add(listener);
    }

    static void tick(MinecraftServer server) {
        for (ServerPlayer bearer : SixEyes.bearers(server)) PacketDistributor.sendToPlayer(bearer, build(bearer));
    }

    /** What this bearer reads now. */
    public static ReadsPayload build(ServerPlayer bearer) {
        List<ReadsPayload.Glance> glances = new ArrayList<>();
        for (ServerPlayer other : bearer.serverLevel().players()) {
            if (other == bearer || other.isSpectator() || !other.isAlive() || other.distanceTo(bearer) > RANGE) continue;
            glances.add(glance(other));
        }
        ServerPlayer focus = focus(bearer);
        Integer before = FOCUSED.put(bearer.getUUID(), focus == null ? null : focus.getId());
        if (focus != null && (before == null || before != focus.getId())) for (var l : FOCUS_LISTENERS) l.accept(bearer, focus);
        return new ReadsPayload(glances, Optional.ofNullable(focus).map(Reads::detail));
    }

    public static ReadsPayload.Glance glance(ServerPlayer other) {
        MaliceData malice = MaliceService.get(other);
        if (malice.isSevered()) return new ReadsPayload.Glance(other.getId(), Optional.empty(), 0, true);
        return new ReadsPayload.Glance(other.getId(), InnateTechniqueService.current(other).map(InnateTechnique::id), (int) Math.floor(malice.current()), false);
    }

    /** The player nearest the bearer's crosshair, within {@link #FOCUS_DEGREES} and {@link #RANGE}; walls don't matter to the eyes. */
    @Nullable
    public static ServerPlayer focus(ServerPlayer bearer) {
        Vec3 eye = bearer.getEyePosition(), look = bearer.getLookAngle();
        ServerPlayer best = null;
        double bestAngle = FOCUS_DEGREES;
        for (ServerPlayer other : bearer.serverLevel().players()) {
            if (other == bearer || other.isSpectator() || !other.isAlive() || other.distanceTo(bearer) > RANGE) continue;
            Vec3 to = other.position().add(0, other.getBbHeight() * 0.6, 0).subtract(eye);
            double angle = Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, to.normalize().dot(look)))));
            if (angle < bestAngle) {
                bestAngle = angle;
                best = other;
            }
        }
        return best;
    }

    /** Full detail on one player: a void's is empty. */
    public static ReadsPayload.Focus detail(ServerPlayer other) {
        if (MaliceService.isSevered(other)) return new ReadsPayload.Focus(other.getId(), true, Optional.empty(), List.of(), List.of());
        Optional<ReadsPayload.Cast> cast = CastManager.currentCast(other).map(active -> cast(other, active));
        List<ReadsPayload.Cooldown> cooldowns = new ArrayList<>();
        long now = other.level().getGameTime();
        for (Map.Entry<ResourceLocation, InnateTechniqueData.Cooldown> e : InnateTechniqueService.get(other).cooldowns().entrySet()) {
            long left = e.getValue().endTick() - now;
            if (left > 0) cooldowns.add(new ReadsPayload.Cooldown(e.getKey(), left / 20f));
        }
        List<ResourceLocation> toggles = new ArrayList<>();
        for (CastState.Active t : CastManager.activeToggles(other)) toggles.add(t.ability().id());
        return new ReadsPayload.Focus(other.getId(), false, cast, cooldowns, toggles);
    }

    private static ReadsPayload.Cast cast(ServerPlayer other, CastState.Active active) {
        var ability = active.ability();
        float progress;
        if (ability.castType() == CastType.CHARGED) {
            int max = ability.maxChargeTicks(other);
            progress = max <= 0 ? 1f : Math.min(1f, active.activeTicks() / (float) max);
        } else {
            int max = ability.maxChannelTicks(other);
            progress = max <= 0 ? -active.activeTicks() / 20f : Math.min(1f, active.activeTicks() / (float) max);
        }
        return new ReadsPayload.Cast(ability.id(), ability.castType().ordinal(), progress);
    }

    static void forget(UUID bearer) {
        FOCUSED.remove(bearer);
    }
}

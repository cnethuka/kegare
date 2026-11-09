package com.cnethuka.kegare.sixeyes;

import com.cnethuka.kegare.innatetechnique.CastManager;
import com.cnethuka.kegare.innatetechnique.InnateTechnique;
import com.cnethuka.kegare.innatetechnique.InnateTechniqueService;
import com.cnethuka.kegare.malice.MaliceData;
import com.cnethuka.kegare.malice.MaliceService;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Six Eyes' perception, on the server (DESIGN §3.32). The eyes see the whole of their dimension, with no number, no growth and no cap:
 * every player in it (always), and every living mob loaded in it (mobs only exist where an area is loaded). Other dimensions are never
 * seen. The bearer is sent what the eyes see, in their own payloads, and nobody else is sent any of it, so a modified client without the
 * eyes has nothing to show. Players every {@link #PLAYERS_EVERY} ticks, mobs every {@link #MOBS_EVERY}; a pulse the moment a player
 * starts a cast (charge, channel, cast or toggle: before its tell plays), and when a hostile mob is about to act ({@link MobIntent}).
 */
public final class Sight {
    public static final int PLAYERS_EVERY = 2, MOBS_EVERY = 10;
    /** Who was sent sight last tick: losing the eyes clears what they saw. */
    private static final Set<UUID> SEEING = new HashSet<>();
    /** Told about what's sent to whom (tests). */
    static BiConsumer<ServerPlayer, Object> sentHook;

    private Sight() {}

    static void init() {
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post e) -> tick(e.getServer()));
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent e) -> SEEING.clear());
        CastManager.addStartListener(Sight::castStarted);
    }

    static void tick(MinecraftServer server) {
        long tick = server.getTickCount();
        Set<UUID> now = new HashSet<>();
        for (ServerPlayer player : SixEyes.everyone(server)) {
            if (!SixEyes.isBearer(player)) {
                if (SEEING.remove(player.getUUID())) send(player, new SightPayloads.Players(false, List.of()));
                continue;
            }
            now.add(player.getUUID());
            if (tick % PLAYERS_EVERY == 0 || !SEEING.contains(player.getUUID())) send(player, players(player));
            if (tick % MOBS_EVERY == 0 || !SEEING.contains(player.getUUID())) send(player, mobs(player));
        }
        SEEING.retainAll(now);
        SEEING.addAll(now);
    }

    private static void send(ServerPlayer to, net.minecraft.network.protocol.common.custom.CustomPacketPayload payload) {
        if (sentHook != null) sentHook.accept(to, payload);
        PacketDistributor.sendToPlayer(to, payload);
    }

    /** Every other player in the bearer's dimension. */
    public static SightPayloads.Players players(ServerPlayer bearer) {
        List<SightPayloads.Players.Dot> dots = new ArrayList<>();
        for (ServerPlayer other : bearer.serverLevel().players()) {
            if (other == bearer || other.isSpectator() || !other.isAlive()) continue;
            MaliceData malice = MaliceService.get(other);
            int color = InnateTechniqueService.current(other).map(InnateTechnique::maliceColor).orElse(InnateTechnique.DEFAULT_MALICE_COLOR);
            dots.add(new SightPayloads.Players.Dot(other.getUUID(), other.getId(), other.getGameProfile().getName(), other.getX(), other.getY(), other.getZ(),
                    color, malice.capacity(), malice.isSevered()));
        }
        return new SightPayloads.Players(true, dots);
    }

    /** Every living mob loaded in the bearer's dimension. */
    public static SightPayloads.Mobs mobs(ServerPlayer bearer) {
        List<SightPayloads.Mobs.Dot> dots = new ArrayList<>();
        for (Entity e : bearer.serverLevel().getAllEntities()) {
            if (!(e instanceof LivingEntity living) || e instanceof Player || e instanceof ArmorStand || !living.isAlive()) continue;
            dots.add(new SightPayloads.Mobs.Dot(e.getId(), BuiltInRegistries.ENTITY_TYPE.getId(e.getType()), (float) e.getX(), (float) e.getY(),
                    (float) e.getZ(), hostile(living)));
        }
        return new SightPayloads.Mobs(dots);
    }

    /** Everything living gives off Malice; hostility is negative emotion. */
    public static boolean hostile(LivingEntity living) {
        return living instanceof Enemy;
    }

    /** A player started a cast: every bearer in that dimension sees their outline pulse, before the cast's own tell. */
    private static void castStarted(ServerPlayer caster) {
        if (MaliceService.isSevered(caster)) return; // a void has nothing to pulse with
        for (ServerPlayer bearer : caster.serverLevel().players()) {
            if (bearer != caster && SixEyes.isBearer(bearer)) send(bearer, new SightPayloads.Pulse(caster.getId(), SightPayloads.Pulse.CAST));
        }
    }

    /** A hostile mob about to act: bearers within {@code range} of it see it pulse. */
    static void mobPulse(ServerLevel level, Entity mob, int kind, double range) {
        for (ServerPlayer bearer : level.players()) {
            if (SixEyes.isBearer(bearer) && bearer.distanceToSqr(mob) <= range * range) send(bearer, new SightPayloads.Pulse(mob.getId(), kind));
        }
    }
}

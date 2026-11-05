package com.cnethuka.kegare.innate_technique.swap;

import com.cnethuka.kegare.Kegare;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Swap's server hooks: Switchback's timer, disguise expiry and sync, calling things off on death, logout and dimension
 * change, and the damage-based advancements.
 */
@EventBusSubscriber(modid = Kegare.MODID)
public final class SwapEvents {
    private SwapEvents() {}

    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        Switchback.tick(event.getServer());
        Disguises.tick(event.getServer());
    }

    /** A joining player gets the current disguises. */
    @SubscribeEvent
    static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) Disguises.sendTo(player);
    }

    /** Damage that lands on a disguised pair's other half, or on a Mimic's real target (player attackers only). */
    @SubscribeEvent
    static void onDamage(LivingDamageEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer victim) || event.getNewDamage() <= 0) return;
        if (!(event.getSource().getEntity() instanceof ServerPlayer attacker) || attacker == victim) return;
        // Wrong Target: the victim wears the caster's face (Awareness or Total Swap), and a third player hit them.
        Disguises.get(victim).ifPresent(d -> {
            if (d.kind() == Disguises.Kind.MIMIC || !d.as().equals(d.caster())) return;
            ServerPlayer caster = Switchback.find(victim.server, d.caster());
            if (caster != null && attacker != caster && Disguises.get(caster).isPresent()) {
                SwapAdvancements.award(caster, SwapAdvancements.WRONG_TARGET);
            }
        });
        // There Are Two of You: someone Mimicking the victim, and another player hit the real one.
        for (ServerPlayer mimic : victim.server.getPlayerList().getPlayers()) {
            if (mimic == attacker || mimic == victim) continue;
            Disguises.get(mimic).ifPresent(d -> {
                if (d.kind() == Disguises.Kind.MIMIC && d.as().equals(victim.getUUID())) SwapAdvancements.award(mimic, SwapAdvancements.TWO_OF_YOU);
            });
        }
    }

    @SubscribeEvent
    static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) endAll(player);
    }

    @SubscribeEvent
    static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) endAll(player);
    }

    @SubscribeEvent
    static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) endAll(player);
    }

    @SubscribeEvent
    static void onServerStopped(ServerStoppedEvent event) {
        Switchback.clear();
        Disguises.clear();
        SwapDebug.clear();
    }

    /** Everything Swap has running that involves this player ends. */
    private static void endAll(ServerPlayer player) {
        Switchback.cancelInvolving(player);
        Disguises.endInvolving(player);
    }
}

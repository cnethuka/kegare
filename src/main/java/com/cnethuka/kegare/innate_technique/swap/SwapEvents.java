package com.cnethuka.kegare.innate_technique.swap;

import com.cnethuka.kegare.Kegare;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.tags.DamageTypeTags;
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
        SwapTracker.tick(event.getServer());
    }

    /** A joining player gets the current disguises. */
    @SubscribeEvent
    static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) Disguises.sendTo(player);
    }

    /**
     * Damage-based advancements. All three are mastery ones, so only players count (DESIGN §4): Round Trip, the caster
     * hits a player while a Switchback swap back is pending; Wrong Target, a third player hits whoever wears the
     * caster's face; There Are Two of You, a player other than the mimic hits the player being Mimicked.
     */
    @SubscribeEvent
    static void onDamage(LivingDamageEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer victim) || event.getNewDamage() <= 0) return;
        if (!(event.getSource().getEntity() instanceof ServerPlayer attacker) || attacker == victim) return;
        if (Switchback.isPending(attacker)) SwapAdvancements.award(attacker, SwapAdvancements.ROUND_TRIP);
        Disguises.get(victim).ifPresent(d -> {
            if (d.kind() == Disguises.Kind.MIMIC || !d.as().equals(d.caster())) return;
            ServerPlayer caster = Switchback.find(victim.server, d.caster());
            if (caster != null && attacker != caster && Disguises.get(caster).isPresent()) {
                SwapAdvancements.award(caster, SwapAdvancements.WRONG_TARGET);
            }
        });
        for (Disguises.Disguise d : Disguises.wearing(victim.getUUID())) {
            if (d.kind() != Disguises.Kind.MIMIC) continue;
            ServerPlayer mimic = Switchback.find(victim.server, d.wearer());
            if (mimic != null && mimic != attacker) SwapAdvancements.award(mimic, SwapAdvancements.TWO_OF_YOU);
        }
    }

    /** Any player or mob: fall-death advancements, then everything Swap has running with it ends. */
    @SubscribeEvent
    static void onDeath(LivingDeathEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide) return;
        if (event.getSource().is(DamageTypeTags.IS_FALL)) SwapTracker.fellToDeath(entity);
        SwapTracker.forget(entity);
        if (entity instanceof ServerPlayer player) {
            endAll(player);
        } else {
            Switchback.cancelInvolving(entity);
        }
    }

    @SubscribeEvent
    static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            SwapTracker.forget(player);
            endAll(player);
        }
    }

    @SubscribeEvent
    static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) endAll(player);
    }

    @SubscribeEvent
    static void onServerStopped(ServerStoppedEvent event) {
        Switchback.clear();
        Disguises.clear();
        Anchors.clear();
        SwapTracker.clear();
        SwapDebug.clear();
    }

    /** Everything Swap has running that involves this player ends. */
    private static void endAll(ServerPlayer player) {
        Switchback.cancelInvolving(player);
        Disguises.endInvolving(player);
        Anchors.drop(player);
    }
}

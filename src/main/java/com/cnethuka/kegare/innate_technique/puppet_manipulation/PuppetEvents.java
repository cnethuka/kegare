package com.cnethuka.kegare.innate_technique.puppet_manipulation;

import com.cnethuka.kegare.Kegare;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageTypes;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingHealEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/** Puppet Manipulation's server hooks: possession's ways back and limits, and the owner's HUD. */
@EventBusSubscriber(modid = Kegare.MODID)
public final class PuppetEvents {
    private PuppetEvents() {}

    @SubscribeEvent
    static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        Possession.tick(player);
        if (player.tickCount % 10 == 0) PuppetHud.check(player);
    }

    @SubscribeEvent
    static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        Possession.restoreOnLogin(player); // the server stopped mid-possession: everything comes back
        PossessionLinks.sendTo(player);
        PuppetHud.markDirty(player);
    }

    /** Logging out: back in the body first (before the player is saved). */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) Possession.release(player, Possession.Return.LOGOUT);
    }

    /**
     * Lethal damage while possessing kills the PUPPET, not the player: backlash instead of death. A real death
     * (/kill) returns them to their body first, then goes ahead there.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !Possession.isPossessing(player)) return;
        if (event.getSource().is(DamageTypes.GENERIC_KILL)) {
            Possession.release(player, Possession.Return.DEATH);
            return;
        }
        event.setCanceled(true);
        Possession.release(player, Possession.Return.PUPPET_DIED);
    }

    /** A dimension change: back in the body first. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    static void onTravel(EntityTravelToDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && Possession.isPossessing(player)) {
            Possession.release(player, Possession.Return.DIMENSION_CHANGE);
        }
    }

    /** A puppet doesn't heal (natural regeneration would mend it for free). */
    @SubscribeEvent
    static void onHeal(LivingHealEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && Possession.isPossessing(player)) event.setCanceled(true);
    }

    /** No items while possessing. */
    @SubscribeEvent
    static void onPickup(ItemEntityPickupEvent.Pre event) {
        if (event.getPlayer() instanceof ServerPlayer player && Possession.isPossessing(player)) event.setCanPickup(TriState.FALSE);
    }

    @SubscribeEvent
    static void onOpenContainer(PlayerContainerEvent.Open event) {
        if (event.getEntity() instanceof ServerPlayer player && Possession.isPossessing(player)
                && event.getContainer() != player.inventoryMenu) {
            player.closeContainer();
        }
    }

    @SubscribeEvent
    static void onServerStopped(ServerStoppedEvent event) {
        Possession.clear();
    }
}

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
        StringsSync.sendTo(player);
        PuppetHud.markDirty(player);
    }

    /** Logging out: back in the body first (before the player is saved). */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        Strings.cut(player);
        Possession.release(player, Possession.Return.LOGOUT);
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

    /** Arriving in a dimension: the strings there (the entity IDs of the last one's mean nothing here). */
    @SubscribeEvent
    static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) StringsSync.sendTo(player);
    }

    /** Guard: whoever damages a Puppet Manipulation player has their guarding puppets to deal with. */
    @SubscribeEvent
    static void onOwnerDamaged(net.neoforged.neoforge.event.entity.living.LivingDamageEvent.Post event) {
        if (event.getNewDamage() <= 0 || !(event.getEntity() instanceof ServerPlayer owner)) return;
        if (event.getSource().getEntity() instanceof net.minecraft.world.entity.LivingEntity attacker && attacker != owner
                && !(attacker instanceof PuppetEntity puppet && puppet.isOwnedBy(owner))) {
            Puppets.ownerAttacked(owner, attacker);
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
    static void onServerTick(net.neoforged.neoforge.event.tick.ServerTickEvent.Post event) {
        CurioBursts.tick(event.getServer());
        Strings.tick(event.getServer());
        if (event.getServer().getTickCount() % 20 == 0 && event.getServer().overworld() != null) {
            PuppetAdvancements.checkSpinning(event.getServer());
        }
    }

    /**
     * Kill-based advancements (mastery: a PLAYER on the other side). Decoy: a player kills your puppet while you're far
     * away. Curiosity Killed: your Curio Burst kills a player. Little Me: you land the killing blow on a player while
     * possessing a puppet. Runs before the possession death handling (a possessing VICTIM is handled there).
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    static void onKill(LivingDeathEvent event) {
        if (event.getEntity().level().isClientSide) return;
        if (event.getEntity() instanceof PuppetEntity puppet && event.getSource().getEntity() instanceof ServerPlayer killer) {
            PuppetAdvancements.puppetKilled(puppet, killer);
        }
        if (!(event.getEntity() instanceof ServerPlayer victim) || Possession.isPossessing(victim)) return;
        if (!(event.getSource().getEntity() instanceof ServerPlayer killer) || killer == victim) return;
        if (event.getSource().is(CurioBursts.CURIO_BURST)) PuppetAdvancements.award(killer, PuppetAdvancements.CURIOSITY_KILLED);
        if (Possession.isPossessing(killer) && event.getSource().getDirectEntity() == killer) {
            PuppetAdvancements.award(killer, PuppetAdvancements.LITTLE_ME);
        }
    }

    @SubscribeEvent
    static void onServerStopped(ServerStoppedEvent event) {
        Possession.clear();
        CurioBursts.clear();
        StandInAbility.clear();
        Strings.clear();
    }
}

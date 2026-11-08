package com.cnethuka.kegare.player;

import com.cnethuka.kegare.Kegare;
import com.cnethuka.kegare.malice.MaliceService;
import com.cnethuka.kegare.combat.CombatHelper;
import com.cnethuka.kegare.network.PlayerSync;
import com.cnethuka.kegare.network.RevealPayload;
import com.cnethuka.kegare.roll.RollReason;
import com.cnethuka.kegare.roll.RollService;
import com.cnethuka.kegare.innatetechnique.CastManager;
import com.cnethuka.kegare.innatetechnique.EndReason;
import com.cnethuka.kegare.innatetechnique.InnateTechniqueService;
import com.cnethuka.kegare.innatetechnique.visual.VisualService;
import com.cnethuka.kegare.trait.TraitService;
import com.cnethuka.kegare.trait.TraitSuccession;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Player and server lifecycle wiring (game bus, server side). */
@EventBusSubscriber(modid = Kegare.MODID)
public final class PlayerEvents {
    private PlayerEvents() {}

    @SubscribeEvent
    static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player instanceof FakePlayer) return;
        handleLogin(player);
    }

    /** Login handling, callable directly by in-game tests (which use fake players the event skips). */
    static void handleLogin(ServerPlayer player) {
        TraitSuccession.markSeen(player);
        // Reconcile first: applies offline revocations and releases stale unique claims before any re-roll.
        TraitService.reconcile(player);
        // Safety net: a Severed player can never keep a Malice-based trait (e.g. data from before this rule).
        if (MaliceService.get(player).isBorn() && MaliceService.isSevered(player)) TraitSuccession.stripMaliceTraits(player);
        if (!MaliceService.get(player).isBorn()) {
            BirthService.birth(player);
        } else if (InnateTechniqueService.validateOnLogin(player) && !MaliceService.isSevered(player)) {
            // Their innate technique was removed by an update: free replacement (no pity change, no Reawakening).
            RollService.rollInnateTechnique(player, RollReason.REPLACEMENT).ifPresent(o -> {
                RollService.announce(player, o);
                RollService.awardOutcomeAdvancements(player, o);
                RevealPayload.sendRoll(player, o);
            });
        }
        TraitSuccession.onLogin(player);
        PlayerSync.syncNow(player);
    }

    @SubscribeEvent
    static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player instanceof FakePlayer) return;
        CastManager.stopAll(player, EndReason.LOGOUT);
        VisualService.stopAll(player);
        TraitSuccession.markSeen(player);
    }

    /** A charge, channel or toggle never carries through a portal, and neither do its visuals. */
    @SubscribeEvent
    static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player instanceof FakePlayer) return;
        CastManager.stopAll(player, EndReason.DIMENSION_CHANGE);
        VisualService.stopAll(player);
    }

    /** Someone came into view of an entity: show them the loops already playing on it. */
    @SubscribeEvent
    static void onStartTracking(PlayerEvent.StartTracking event) {
        if (event.getEntity() instanceof ServerPlayer tracker) VisualService.onStartTracking(tracker, event.getTarget());
    }

    /** Any living entity's visuals stop when it dies (status loops on mobs, too). */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void onAnyDeath(LivingDeathEvent event) {
        if (event.isCanceled() || event.getEntity().level().isClientSide) return;
        if (!(event.getEntity() instanceof ServerPlayer)) VisualService.stopAll(event.getEntity());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer victim) || victim instanceof FakePlayer) return;
        CastManager.stopAll(victim, EndReason.DEATH);
        VisualService.stopAll(victim);
        // Runs on the dying entity, before its data is copied to the respawned player.
        ServerPlayer killer = playerKiller(victim, event.getSource());
        if (killer != null) TraitSuccession.onPlayerKilled(victim, killer);
    }

    /**
     * The player responsible for a death: the direct attacker/shooter, or whoever last hurt the victim within
     * vanilla's kill-credit window (e.g. knocked into the void). Null for non-PvP deaths.
     */
    @Nullable
    static ServerPlayer playerKiller(ServerPlayer victim, DamageSource source) {
        if (source.getEntity() instanceof ServerPlayer direct && direct != victim) return direct;
        LivingEntity credit = victim.getKillCredit();
        if (credit instanceof ServerPlayer player && player != victim) return player;
        return null;
    }

    @SubscribeEvent
    static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        // Capacity is copied unchanged (copyOnDeath); only the reserve refills.
        if (!event.isEndConquered()) MaliceService.refill(player);
        PlayerSync.syncNow(player);
    }

    @SubscribeEvent
    static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player instanceof FakePlayer) return;
        if (player.isAlive()) {
            com.cnethuka.kegare.innatetechnique.Burnout.tick(player);
            CastManager.tick(player);
            MaliceService.tickRegen(player);
        }
        PlayerSync.watchEnvironment(player);
        PlayerSync.flush(player);
    }

    /** Dealing or taking real damage tags combat (pauses regen, feeds isInCombat). */
    @SubscribeEvent
    static void onDamage(LivingDamageEvent.Post event) {
        if (event.getNewDamage() <= 0) return;
        if (event.getEntity() instanceof ServerPlayer victim) CombatHelper.markCombat(victim);
        if (event.getSource().getEntity() instanceof ServerPlayer attacker && attacker != event.getEntity()) {
            CombatHelper.markCombat(attacker);
        }
    }

    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        if (event.getServer().overworld() == null) return; // no worlds loaded (e.g. the in-game JUnit test server)
        int tick = event.getServer().getTickCount();
        if (tick % 20 == 0) TraitSuccession.tick(event.getServer());
        if (tick % 1200 == 0) TraitSuccession.checkInactive(event.getServer());
    }

    @SubscribeEvent
    static void onServerStarted(ServerStartedEvent event) {
        if (event.getServer().overworld() == null) return;
        TraitSuccession.checkInactive(event.getServer());
    }
}

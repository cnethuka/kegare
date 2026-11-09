package com.cnethuka.kegare.rmt;

import com.cnethuka.kegare.Kegare;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/** Server hooks for the green orb and the RMT advancements. */
@EventBusSubscriber(modid = Kegare.MODID)
public final class RmtEvents {
    private RmtEvents() {}

    /** A real hit from another player: remember it, and maybe roll for the orb. Any hit at all: injury fatigue. */
    @SubscribeEvent
    static void onDamage(LivingDamageEvent.Post event) {
        if (event.getNewDamage() <= 0 || !(event.getEntity() instanceof ServerPlayer victim)) return;
        if (!victim.isFakePlayer()) RmtFatigue.onDamageTaken(victim, event.getNewDamage());
        double low = RmtConfig.MOMENT_HEALTH.get();
        if (victim.isAlive() && victim.getHealth() <= low && victim.getHealth() + event.getNewDamage() > low) RmtOnAnother.onDroppedLow(victim);
        if (victim instanceof FakePlayer) return;
        if (event.getSource().getEntity() instanceof Player attacker && attacker != victim) GreenOrbService.onHitByPlayer(victim);
    }

    @SubscribeEvent
    static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player && !(player instanceof FakePlayer)) {
            GreenOrbService.tick(player);
            RmtFatigue.tick(player);
        }
    }

    @SubscribeEvent
    static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && !event.isCanceled()) {
            GreenOrbService.clear(player);
            RmtFatigue.reset(player);
            GreenOrbService.state(player).survivedLowAt = Long.MAX_VALUE; // dead before the 10 s: rmt_survived_low doesn't count
        }
    }

    /**
     * Unlocked before unlocking it paid Output Insight (DESIGN §3.21): paid now, once. {@code unlocked_rmt} pays only once ever, so
     * this does nothing for anyone already paid.
     */
    @SubscribeEvent
    static void onLogin(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) payUnlockedRmt(player);
    }

    static void payUnlockedRmt(ServerPlayer player) {
        if (com.cnethuka.kegare.loadout.SharedAbilityService.isUnlocked(player, RmtIds.ABILITY)) {
            com.cnethuka.kegare.progression.InsightHooks.unlockedRmt(player);
        }
    }

    @SubscribeEvent
    static void onLogout(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            RmtFatigue.reset(player);
            GreenOrbService.state(player).survivedLowAt = Long.MAX_VALUE;
        }
    }
}

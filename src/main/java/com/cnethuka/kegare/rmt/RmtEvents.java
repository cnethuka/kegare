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

    /** A real hit from another player: remember it, and maybe roll for the orb. */
    @SubscribeEvent
    static void onDamage(LivingDamageEvent.Post event) {
        if (event.getNewDamage() <= 0 || !(event.getEntity() instanceof ServerPlayer victim) || victim instanceof FakePlayer) return;
        if (event.getSource().getEntity() instanceof Player attacker && attacker != victim) GreenOrbService.onHitByPlayer(victim);
    }

    @SubscribeEvent
    static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player && !(player instanceof FakePlayer)) GreenOrbService.tick(player);
    }

    @SubscribeEvent
    static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && !event.isCanceled()) GreenOrbService.clear(player);
    }
}

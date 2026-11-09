package com.cnethuka.kegare.timing;

import com.cnethuka.kegare.Kegare;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/** Where sub-tick timing meets the game (DESIGN §3.29): the clock pings, and the melee attack's time. */
@EventBusSubscriber(modid = Kegare.MODID)
public final class TimingEvents {
    private TimingEvents() {}

    @SubscribeEvent
    static void onTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player && !(player instanceof FakePlayer)) ClockSync.tick(player);
    }

    /** First, so everything after it can ask when the swing was. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    static void onAttack(AttackEntityEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) SubTick.mark(player, TimedInput.ATTACK);
    }

    @SubscribeEvent
    static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        SubTick.forget(event.getEntity().getUUID());
    }

    @SubscribeEvent
    static void onStopped(ServerStoppedEvent event) {
        SubTick.clear();
    }
}

package com.cnethuka.kegare.severed;

import com.cnethuka.kegare.malice.MaliceService;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.util.FakePlayer;

/**
 * Severed players' physical identity (DESIGN §3.27): they trade all their Malice for a superhuman body. No Malice, no glow, no Innate
 * color: everything here comes from the body.
 */
public final class Severed {
    private Severed() {}

    public static void init() {
        NeoForge.EVENT_BUS.addListener(SeveredBody::onIncoming);
        NeoForge.EVENT_BUS.addListener(SeveredBody::onDamage);
        NeoForge.EVENT_BUS.addListener((PlayerTickEvent.Post e) -> {
            if (e.getEntity() instanceof ServerPlayer p && !(p instanceof FakePlayer)) Senses.tick(p);
        });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> Senses.forget(e.getEntity().getUUID()));
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent e) -> Senses.clear());
    }

    /** Born with no Malice. */
    public static boolean is(Player player) {
        return MaliceService.isSevered(player);
    }
}

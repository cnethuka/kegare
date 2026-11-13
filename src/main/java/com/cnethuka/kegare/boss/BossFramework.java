package com.cnethuka.kegare.boss;

import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Game-bus wiring for boss encounters. No registration, payloads or persistence. */
public final class BossFramework {
    private static boolean initialized;

    private BossFramework() {}

    public static synchronized void init() {
        if (initialized) return;
        initialized = true;
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, (LivingDamageEvent.Pre event) -> BossDamageTracker.pre(event));
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, (LivingDamageEvent.Post event) -> BossDamageTracker.post(event));
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, (LivingDeathEvent event) -> {
            if (event.isCanceled() || event.getEntity().level().isClientSide) return;
            BossRuntime.find(event.getEntity().getUUID())
                    .filter(fight -> fight.boss() == event.getEntity())
                    .ifPresent(fight -> BossRuntime.defeated(fight, event.getSource()));
        });
        NeoForge.EVENT_BUS.addListener((EntityLeaveLevelEvent event) -> {
            if (event.getEntity() instanceof net.minecraft.world.entity.LivingEntity living && !event.getLevel().isClientSide) {
                BossRuntime.removed(living);
            }
        });
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> BossRuntime.tick(event.getServer()));
        NeoForge.EVENT_BUS.addListener((ServerStoppingEvent event) -> BossRuntime.stop(event.getServer()));
    }
}

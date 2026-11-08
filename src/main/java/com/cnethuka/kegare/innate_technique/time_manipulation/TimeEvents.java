package com.cnethuka.kegare.innate_technique.time_manipulation;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.ICancellableEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Time Manipulation's server hooks: Point Maker's marks, the position history Rewind reads, Stasis Fields, Standstill's stops and the
 * advancements' timers; and everything they need cleared on death, logout, a change of dimension and the server stopping.
 */
final class TimeEvents {
    private TimeEvents() {}

    static void init() {
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post e) -> PositionHistory.tick(e.getServer()));
        NeoForge.EVENT_BUS.addListener((LevelTickEvent.Post e) -> {
            if (e.getLevel() instanceof ServerLevel level) {
                StasisFields.tick(level);
                TimeStops.tick(level);
            }
        });
        NeoForge.EVENT_BUS.addListener((PlayerTickEvent.Post e) -> {
            if (e.getEntity() instanceof ServerPlayer p) {
                PointMarks.tick(p);
                TimeAdvancements.tick(p);
            }
        });
        NeoForge.EVENT_BUS.addListener(TimeStops::onEntityTick);
        NeoForge.EVENT_BUS.addListener(TimeStops::onJoin);
        NeoForge.EVENT_BUS.addListener(TimeStops::onImpact);
        // First: what reaches the frozen is held at its full amount, and nothing else sees it until it lands.
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, TimeStops::onIncomingDamage);

        // A frozen player does nothing.
        NeoForge.EVENT_BUS.addListener((AttackEntityEvent e) -> refuse(e.getEntity(), e));
        NeoForge.EVENT_BUS.addListener((PlayerInteractEvent.RightClickBlock e) -> refuse(e.getEntity(), e));
        NeoForge.EVENT_BUS.addListener((PlayerInteractEvent.RightClickItem e) -> refuse(e.getEntity(), e));
        NeoForge.EVENT_BUS.addListener((PlayerInteractEvent.LeftClickBlock e) -> refuse(e.getEntity(), e));
        NeoForge.EVENT_BUS.addListener((PlayerInteractEvent.EntityInteract e) -> refuse(e.getEntity(), e));
        NeoForge.EVENT_BUS.addListener((PlayerInteractEvent.EntityInteractSpecific e) -> refuse(e.getEntity(), e));
        NeoForge.EVENT_BUS.addListener((LivingEntityUseItemEvent.Start e) -> refuse(e.getEntity(), e));
        NeoForge.EVENT_BUS.addListener((BlockEvent.BreakEvent e) -> refuse(e.getPlayer(), e));

        NeoForge.EVENT_BUS.addListener((LivingDeathEvent e) -> {
            LivingEntity entity = e.getEntity();
            if (entity.level().isClientSide) return;
            PositionHistory.forget(entity);
            if (entity instanceof ServerPlayer p) {
                PointMarks.clear(p);
                TimeAdvancements.forget(p.getUUID());
                TimeStops.onGone(p);
            }
        });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> {
            if (!(e.getEntity() instanceof ServerPlayer p)) return;
            PointMarks.forget(p.getUUID());
            PositionHistory.forget(p);
            TimeAdvancements.forget(p.getUUID());
            TimeStops.onGone(p);
            AccelerateAbility.forget(p.getUUID());
        });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer p) TimeStops.sendTo(p);
        });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerChangedDimensionEvent e) -> {
            if (!(e.getEntity() instanceof ServerPlayer p)) return;
            TimeStops.onGone(p); // a caster who leaves ends it; the held are let go where they are
            TimeStops.sendTo(p);
        });
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent e) -> {
            PointMarks.clearAll();
            PositionHistory.clear();
            StasisFields.clear();
            TimeStops.clear();
            TimeAdvancements.clear();
        });
    }

    private static void refuse(Entity who, ICancellableEvent event) {
        if (who instanceof ServerPlayer p && TimeStops.frozen(p)) event.setCanceled(true);
    }
}

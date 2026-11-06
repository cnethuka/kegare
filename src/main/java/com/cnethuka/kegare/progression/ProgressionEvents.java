package com.cnethuka.kegare.progression;

import com.cnethuka.kegare.Kegare;
import com.cnethuka.kegare.registry.KegareAttachments;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Where the game feeds progression. Fake players count, so the tests run the real path. */
@EventBusSubscriber(modid = Kegare.MODID)
public final class ProgressionEvents {
    private ProgressionEvents() {}

    /** Anything that joins the world during a cast belongs to that cast. */
    @SubscribeEvent
    static void onEntityJoin(EntityJoinLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel) || event.getEntity() instanceof Player) return;
        ServerPlayer caster = CastScope.player();
        if (caster == null || event.getEntity().hasData(KegareAttachments.CAST_ORIGIN)) return;
        event.getEntity().setData(KegareAttachments.CAST_ORIGIN, new CastOrigin(caster.getUUID(), CastScope.ability().id()));
    }

    @SubscribeEvent
    static void onDamage(LivingDamageEvent.Post event) {
        float amount = event.getNewDamage();
        LivingEntity victim = event.getEntity();
        if (amount <= 0 || !(victim.level() instanceof ServerLevel level)) return;
        Attribution.Hit hit = Attribution.of(event.getSource(), level.getServer());
        if (hit == null || !Attribution.isRealTarget(hit.attacker(), victim)) return;
        double counted = Math.min(amount, victim.getMaxHealth()); // what a single absurd hit is worth
        if (victim instanceof ServerPlayer target) FightTracker.damage(hit.attacker(), target, counted);
        else GradeService.onMobDamage(hit.attacker(), counted);
    }

    /** Runs after everything else has had its say: a cancelled death is no death. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void onDeath(LivingDeathEvent event) {
        if (!event.isCanceled() && event.getEntity() instanceof ServerPlayer dead && !dead.level().isClientSide) FightTracker.onDeath(dead);
    }

    @SubscribeEvent
    static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) FightTracker.onLogout(player);
    }

    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        if (event.getServer().overworld() != null) FightTracker.tick(event.getServer());
    }

    @SubscribeEvent
    static void onServerStopped(ServerStoppedEvent event) {
        FightTracker.clear();
    }
}

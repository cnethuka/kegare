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
import net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.entity.player.CriticalHitEvent;
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
        // Being hit by something real is being in a fight too, for abilities with no target of their own.
        if (victim instanceof ServerPlayer player && event.getSource().getEntity() instanceof LivingEntity source
                && Attribution.isRealTarget(player, source)) {
            Landings.engaged(player, source);
        }
        Attribution.Hit hit = Attribution.of(event.getSource(), level.getServer());
        // Every hit a player takes, by whom (Bush Camper reads the last stealWindow of it): a hit from themself counts as from nobody.
        if (victim instanceof ServerPlayer target) {
            BushCamper.took(target, hit == null || hit.attacker() == target ? null : hit.attacker(), Math.min(amount, victim.getMaxHealth()), level.getGameTime());
        }
        if (hit == null || !Attribution.isRealTarget(hit.attacker(), victim)) return;
        ServerPlayer attacker = hit.attacker();
        boolean onItsOwn = event.getSource().getEntity() instanceof OwnedEntity owned && owned.actsOnItsOwn();
        if (!onItsOwn) Landings.landedOwnHit(attacker);
        Landings.engaged(attacker, victim);
        double counted = Math.min(amount, victim.getMaxHealth()); // what a single absurd hit is worth
        if (victim instanceof ServerPlayer target) FightTracker.damage(attacker, target, counted);
        else if (!onItsOwn || Landings.isActive(attacker)) GradeService.onMobDamage(attacker, counted);
        if (hit.ability() != null) {
            if (onItsOwn) Landings.landOnItsOwn(attacker, victim, hit.ability());
            else Landings.land(attacker, victim, hit.ability());
        } else if (victim instanceof ServerPlayer target) {
            FightTracker.melee(attacker, target, Landings.wasStrike(attacker, target) ? MeleeMoves.REINFORCED_STRIKE
                    : MeleeMoves.of(attacker, event.getSource(), Landings.wasCritical(attacker)));
        }
    }

    /** An effect put on someone else during a cast lands. */
    @SubscribeEvent
    static void onEffectAdded(MobEffectEvent.Added event) {
        LivingEntity target = event.getEntity();
        if (target.level().isClientSide) return;
        ServerPlayer caster = CastScope.player();
        if (caster != null && event.getEffectSource() == caster) Landings.land(caster, target, CastScope.ability());
    }

    /** So does knocking someone back during a cast. */
    @SubscribeEvent
    static void onKnockback(LivingKnockBackEvent event) {
        LivingEntity target = event.getEntity();
        if (target.level().isClientSide) return;
        ServerPlayer caster = CastScope.player();
        if (caster != null) Landings.land(caster, target, CastScope.ability());
    }

    /** The damage event can't tell a critical from any other hit. */
    @SubscribeEvent
    static void onCritical(CriticalHitEvent event) {
        if (event.isCriticalHit() && event.getEntity() instanceof ServerPlayer player) Landings.markCritical(player);
    }

    /** Runs after everything else has had its say: a cancelled death is no death. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void onDeath(LivingDeathEvent event) {
        if (event.isCanceled() || !(event.getEntity() instanceof ServerPlayer dead) || dead.level().isClientSide) return;
        Attribution.Hit blow = Attribution.of(event.getSource(), dead.server);
        BushCamper.died(dead, blow == null ? null : blow.attacker(), dead.level().getGameTime());
        FightTracker.onDeath(dead);
    }

    @SubscribeEvent
    static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        GradeService.tryPromote(player); // the config may have changed while they were away
        Unlocks.evaluate(player);
    }

    @SubscribeEvent
    static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            FightTracker.onLogout(player);
            BushCamper.forget(player.getUUID());
        }
    }

    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        if (event.getServer().overworld() == null) return; // no worlds loaded (e.g. the in-game JUnit test server)
        FightTracker.tick(event.getServer());
        TechniqueUnlocks.watchConfig(event.getServer());
    }

    @SubscribeEvent
    static void onServerStopped(ServerStoppedEvent event) {
        FightTracker.clear();
        BushCamper.clear();
    }
}

package com.cnethuka.kegare.innate_technique.rime;

import com.cnethuka.kegare.Kegare;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.minecraft.server.level.ServerLevel;

/**
 * Rime's server-side hooks: the shell's damage reduction and melee chill, freeze ticking, Cold Blooded, Ice Path
 * trails melting and slowing, and forgetting Glacial Spire launches on landing.
 */
@EventBusSubscriber(modid = Kegare.MODID)
public final class RimeEvents {
    private RimeEvents() {}

    /** Rime Shell: less damage taken, melee attackers slowed, blocked damage counted toward Thin Ice. */
    @SubscribeEvent(priority = EventPriority.LOW)
    static void onDamage(LivingDamageEvent.Pre event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !Rime.RIME_SHELL.isActive(player)) return;
        float before = event.getNewDamage();
        if (before <= 0) return;
        float after = before * (float) (1.0 - RimeConfig.SHELL_DAMAGE_REDUCTION.get());
        event.setNewDamage(after);

        RimeStats stats = player.getData(RimeRegistry.STATS);
        stats.shellBlocked += before - after;
        if (stats.shellBlocked >= RimeAdvancements.THIN_ICE_DAMAGE) RimeAdvancements.award(player, RimeAdvancements.THIN_ICE);

        DamageSource source = event.getSource();
        boolean melee = source.getEntity() instanceof LivingEntity attacker && source.getDirectEntity() == attacker
                && !source.is(DamageTypeTags.IS_PROJECTILE) && attacker != player;
        if (melee) {
            int ticks = RimeConfig.ticks(RimeConfig.SHELL_ATTACKER_SLOWNESS_SECONDS);
            if (ticks > 0) ((LivingEntity) source.getEntity()).addEffect(
                    new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ticks, RimeConfig.SHELL_ATTACKER_SLOWNESS_AMPLIFIER.get()), player);
        }
    }

    /** Freeze stacks melt and frozen targets break free (only entities that ever had freeze data). */
    @SubscribeEvent
    static void onEntityTick(EntityTickEvent.Post event) {
        if (event.getEntity() instanceof LivingEntity living && !living.level().isClientSide && living.hasData(RimeRegistry.FREEZE)) {
            FreezeService.tick(living);
        }
    }

    @SubscribeEvent
    static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player) RimeLaunch.tick(player);
    }

    @SubscribeEvent
    static void onLevelTick(LevelTickEvent.Post event) {
        if (event.getLevel() instanceof ServerLevel level) IcePaths.tick(level);
    }

    /** Cold Blooded: kill a player who is frozen solid. Then the freeze ends without a shatter. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void onDeath(LivingDeathEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide || event.isCanceled()) return;
        if (victim instanceof ServerPlayer && FreezeService.isFrozen(victim)) {
            ServerPlayer killer = event.getSource().getEntity() instanceof ServerPlayer p ? p
                    : victim.getKillCredit() instanceof ServerPlayer p ? p : null;
            if (killer != null && killer != victim) RimeAdvancements.award(killer, RimeAdvancements.COLD_BLOODED);
        }
        FreezeService.clear(victim);
    }
}

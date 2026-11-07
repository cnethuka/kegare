package com.cnethuka.kegare.melee;

import com.cnethuka.kegare.Kegare;
import com.cnethuka.kegare.combat.CombatHelper;
import com.cnethuka.kegare.progression.Attribution;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Where the layer meets the game's damage. Cancelling happens first, so a parried hit never reaches a Strike or a guard. */
@EventBusSubscriber(modid = Kegare.MODID)
public final class MeleeEvents {
    private MeleeEvents() {}

    @SubscribeEvent
    static void onTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player && !(player instanceof FakePlayer) && player.isAlive()) MeleeLayer.tick(player);
    }

    /** A perfect parry cancels the hit outright. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    static void onIncoming(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !player.hasData(MeleeLayer.ATTACHMENT)) return;
        MeleeState state = MeleeLayer.state(player);
        DamageSource source = event.getSource();
        if (!Parry.inPerfectWindow(player, state) || !(source.getEntity() instanceof LivingEntity attacker) || attacker == player) return;
        if (MeleeHits.of(source, (ServerLevel) player.level()) != MeleeHits.Kind.MELEE) return;
        if (CombatHelper.isBoss(attacker)) return; // a boss's hit is only reduced, in onDamage
        event.setCanceled(true);
        Parry.parried(player, state, attacker);
    }

    /** What a guard takes off, and what a Strike adds. */
    @SubscribeEvent
    static void onDamage(LivingDamageEvent.Pre event) {
        LivingEntity victim = event.getEntity();
        if (!(victim.level() instanceof ServerLevel level) || event.getNewDamage() <= 0) return;
        DamageSource source = event.getSource();
        MeleeHits.Kind kind = MeleeHits.of(source, level);
        if (victim instanceof ServerPlayer player && player.hasData(MeleeLayer.ATTACHMENT) && Parry.holding(player, MeleeLayer.state(player))) {
            MeleeNumbers numbers = MeleeNumbers.of(player);
            if (kind == MeleeHits.Kind.MELEE && source.getEntity() != player) {
                event.setNewDamage(event.getNewDamage() * (float) (1.0 - numbers.guardMeleeReduction()));
                MeleeImpact.GUARD_HIT.fire(level, source.getEntity() instanceof LivingEntity attacker ? Parry.contact(player, attacker) : player.getEyePosition(), player);
            } else if (kind == MeleeHits.Kind.TECHNIQUE) {
                event.setNewDamage(event.getNewDamage() * (float) (1.0 - numbers.guardTechniqueReduction()));
            }
        }
        if (kind == MeleeHits.Kind.MELEE && source.getEntity() instanceof ServerPlayer attacker && attacker.hasData(MeleeLayer.ATTACHMENT)
                && Attribution.isRealTarget(attacker, victim)) {
            event.setNewDamage(ReinforcedStrike.onHit(attacker, victim, event.getNewDamage()));
        }
    }

    /** A hit that really landed means the dash didn't dodge. */
    @SubscribeEvent
    static void onHurt(LivingDamageEvent.Post event) {
        if (event.getNewDamage() > 0 && event.getEntity() instanceof ServerPlayer player && player.hasData(MeleeLayer.ATTACHMENT)
                && event.getSource().getEntity() != null) {
            CleanDodge.wasHit(MeleeLayer.state(player));
        }
    }

    /** Attacking drops the guard. The hit still goes through: that's how a parry turns into a Strike. */
    @SubscribeEvent
    static void onAttack(AttackEntityEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        Parry.noteSwing(player);
        Parry.release(player);
    }

    /** The body that died took its timers with it: nothing on the new one's HUD, and no dash still driving. */
    @SubscribeEvent
    static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PacketDistributor.sendToPlayer(player, MeleeHudPayload.NONE);
            PacketDistributor.sendToPlayer(player, MeleeDashPayload.STOP);
            PacketDistributor.sendToPlayer(player, new MeleeTellPayload(player.getId(), MeleeTell.Look.NONE));
        }
    }

    @SubscribeEvent
    static void onStartTracking(PlayerEvent.StartTracking event) {
        if (event.getEntity() instanceof ServerPlayer tracker) MeleeTell.onStartTracking(tracker, event.getTarget());
    }

    @SubscribeEvent
    static void onDimensionChange(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) MeleeLayer.reset(player);
    }
}

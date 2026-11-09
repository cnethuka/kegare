package com.cnethuka.kegare.fists;

import com.cnethuka.kegare.Kegare;
import com.cnethuka.kegare.melee.MeleeHits;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Where Reinforced Fists meet the game (DESIGN §3.28). */
@EventBusSubscriber(modid = Kegare.MODID)
public final class FistsEvents {
    private FistsEvents() {}

    /**
     * Melee hits with Fists on deal more: {@code fistBonus} with an empty hand, {@code weaponBonus} with anything held. Added to the hit
     * itself, before the target's defences (a perfect parry, at a higher priority, has already cancelled it outright).
     */
    @SubscribeEvent
    static void onIncoming(LivingIncomingDamageEvent event) {
        DamageSource source = event.getSource();
        if (!(event.getEntity().level() instanceof ServerLevel level)) return;
        if (SecondImpact.isSecondImpact(source)) { // it doesn't add to the hurt cooldown the Strike started
            event.setInvulnerabilityTicks(event.getEntity().invulnerableTime);
            return;
        }
        if (!(source.getEntity() instanceof ServerPlayer attacker) || !ReinforcedFists.isOn(attacker)) return;
        if (MeleeHits.of(source, level) != MeleeHits.Kind.MELEE) return;
        event.setAmount(event.getAmount() * (float) (1.0 + bonus(attacker)));
    }

    /** What Fists add to this player's melee hits right now, as a share: bigger with an empty hand. */
    public static double bonus(ServerPlayer attacker) {
        return attacker.getMainHandItem().isEmpty() ? FistsConfig.FIST_BONUS.get() : FistsConfig.WEAPON_BONUS.get();
    }

    @SubscribeEvent
    static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player && (FistsTell.showing(player) || ReinforcedFists.isOn(player))) FistsTell.sync(player);
    }

    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        SecondImpact.tick(event.getServer().overworld());
    }

    @SubscribeEvent
    static void onStartTracking(PlayerEvent.StartTracking event) {
        if (event.getEntity() instanceof ServerPlayer tracker) FistsTell.onStartTracking(tracker, event.getTarget());
    }

    @SubscribeEvent
    static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) FistsTell.forget(player);
    }

    @SubscribeEvent
    static void onDimensionChange(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) FistsTell.forget(player);
    }

    @SubscribeEvent
    static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        FistsTell.forget(event.getEntity().getUUID());
    }

    @SubscribeEvent
    static void onStopped(ServerStoppedEvent event) {
        FistsTell.clear();
        SecondImpact.clear();
    }
}

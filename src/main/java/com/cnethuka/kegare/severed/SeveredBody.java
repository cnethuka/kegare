package com.cnethuka.kegare.severed;

import com.cnethuka.kegare.malice.MaliceService;
import com.cnethuka.kegare.melee.MeleeHits;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/**
 * A Severed player's body, always on (DESIGN §3.27). Strength: their melee hits ({@link MeleeHits}: fists and weapons) deal
 * {@code meleeDamage} times as much, before armour (as a heavier blow would) and before a Reinforced Strike's own bonus. Toughness:
 * after armour, what reaches them is cut to {@code takenFall} for falls, {@code takenTechnique} for techniques and {@code takenPhysical}
 * for everything else; the void, /kill and starving are never touched. Stamina: normal hunger drain is cut to {@code exhaustion}
 * (through {@code PlayerExhaustionMixin} and {@code FoodDataMixin}); Blur adds its own drain straight to the food bar, outside it.
 * Healing stays vanilla.
 */
public final class SeveredBody {
    private SeveredBody() {}

    /** Strength, on the incoming amount. */
    static void onIncoming(LivingIncomingDamageEvent event) {
        if (!(event.getEntity().level() instanceof ServerLevel level)) return;
        DamageSource source = event.getSource();
        if (!(source.getEntity() instanceof ServerPlayer attacker) || !Severed.is(attacker)) return;
        if (MeleeHits.of(source, level) != MeleeHits.Kind.MELEE) return;
        event.setAmount(event.getAmount() * strength());
    }

    /** Toughness, on what's left after armour. */
    static void onDamage(LivingDamageEvent.Pre event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !Severed.is(player) || event.getNewDamage() <= 0) return;
        event.setNewDamage(event.getNewDamage() * taken(event.getSource(), player.serverLevel()));
    }

    static float strength() {
        return SeveredConfig.MELEE_DAMAGE.get().floatValue();
    }

    /** The share of a hit of this kind a Severed body takes. Package-visible for the tests. */
    static float taken(DamageSource source, ServerLevel level) {
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) || source.is(DamageTypes.STARVE)) return 1f; // the void, /kill, starving
        if (source.is(DamageTypeTags.IS_FALL)) return SeveredConfig.TAKEN_FALL.get().floatValue();
        if (MeleeHits.of(source, level) == MeleeHits.Kind.TECHNIQUE) return SeveredConfig.TAKEN_TECHNIQUE.get().floatValue();
        return SeveredConfig.TAKEN_PHYSICAL.get().floatValue();
    }

    /** Stamina: the hunger a Severed player feels from normal activity. Called from the mixins, on both sides. */
    public static float exhaustion(Player player, float amount) {
        if (amount <= 0f || player.level().isClientSide || !MaliceService.isSevered(player)) return amount;
        return amount * SeveredConfig.EXHAUSTION.get().floatValue();
    }
}

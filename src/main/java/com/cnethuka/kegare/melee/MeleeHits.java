package com.cnethuka.kegare.melee;

import com.cnethuka.kegare.progression.Attribution;
import com.cnethuka.kegare.progression.CastScope;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;

/** What kind of hit a damage source is, as far as the layer cares. */
public final class MeleeHits {
    public enum Kind {
        /** Someone's own hand or weapon. */
        MELEE,
        /** Damage from an innate technique, projectiles included. */
        TECHNIQUE,
        /** Everything else: arrows, lava, falling. */
        OTHER
    }

    private MeleeHits() {}

    public static Kind of(DamageSource source, ServerLevel level) {
        if (isMeleeType(source) && source.getEntity() instanceof LivingEntity attacker && source.getDirectEntity() == attacker) {
            // The same hit type inside a running cast is that cast's doing.
            return attacker instanceof ServerPlayer player && CastScope.current(player) != null ? Kind.TECHNIQUE : Kind.MELEE;
        }
        // Every technique's damage is its own Kegare damage type; the attribution catches anything that isn't.
        if (source.typeHolder().unwrapKey().map(key -> key.location().getNamespace().equals("kegare")).orElse(false)) return Kind.TECHNIQUE;
        Attribution.Hit hit = Attribution.of(source, level.getServer());
        return hit != null && hit.ability() != null ? Kind.TECHNIQUE : Kind.OTHER;
    }

    private static boolean isMeleeType(DamageSource source) {
        return !source.is(DamageTypeTags.IS_PROJECTILE)
                && (source.is(DamageTypes.PLAYER_ATTACK) || source.is(DamageTypes.MOB_ATTACK) || source.is(DamageTypes.MOB_ATTACK_NO_AGGRO));
    }
}

package com.cnethuka.kegare.impact;

import javax.annotation.Nullable;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * A hit, resolved at the end of the server tick it landed in (everything it did in that tick added up): what the Impact System's
 * listeners get (the clients' feel, the hit sounds, battle damage).
 *
 * @param attacker     who's credited: a player for their puppets and techniques; null if nobody
 * @param contact      where it met the victim's body
 * @param direction    which way it was going (unit), out of the attacker into the victim
 * @param damage       health it took (a Total Swap: health moved), in points
 * @param knockback    knockback it landed with, stacked as vanilla stacks it
 * @param launch       the upward speed it threw them at (blocks/tick)
 * @param malice       the base Malice behind it: its cast's base cost, plus what a puppet that went off carried
 * @param breaksBlocks false for hits that never break the world (Swap: a clean magic trick)
 * @param weapon       a melee hit's weapon (empty: a fist), else empty
 * @param source       the damage that landed, if any did
 */
public record Impact(ServerLevel level, ImpactTier tier, double strength, ImpactKind kind, @Nullable Entity attacker, LivingEntity victim,
                     Vec3 contact, Vec3 direction, double damage, double knockback, double launch, double charge, double malice, boolean breaksBlocks,
                     ItemStack weapon, @Nullable DamageSource source) {
    public boolean melee() {
        return kind == ImpactKind.MELEE && attacker != null && source != null && source.getDirectEntity() == attacker;
    }
}

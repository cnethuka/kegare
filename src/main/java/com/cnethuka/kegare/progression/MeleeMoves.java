package com.cnethuka.kegare.progression;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MaceItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TridentItem;

/**
 * The moves in melee variety: what kind of hit a plain hit was (its weapon, and how it was thrown), and the melee layer's own three
 * moves, each its own move for variety and repeats alike. Only a Severed player's Assessment reads these.
 */
public final class MeleeMoves {
    public static final String PERFECT_PARRY = "perfect_parry";
    public static final String CLEAN_DODGE = "clean_dodge";
    public static final String REINFORCED_STRIKE = "reinforced_strike";

    private MeleeMoves() {}

    static String of(ServerPlayer attacker, DamageSource source, boolean critical) {
        if (source.is(DamageTypeTags.IS_PROJECTILE)) return "ranged";
        String weapon = weapon(attacker.getMainHandItem());
        if (critical) return weapon + "_critical";
        return attacker.isSprinting() ? weapon + "_sprint" : weapon;
    }

    private static String weapon(ItemStack held) {
        if (held.isEmpty()) return "fist";
        if (held.getItem() instanceof SwordItem) return "sword";
        if (held.getItem() instanceof AxeItem) return "axe";
        if (held.getItem() instanceof MaceItem) return "mace";
        if (held.getItem() instanceof TridentItem) return "trident";
        return "other";
    }
}

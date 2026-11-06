package com.cnethuka.kegare.progression;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MaceItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TridentItem;

/** What kind of hit a plain hit was: its weapon, and how it was thrown. Only a Severed player's Assessment reads this. */
final class MeleeMoves {
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

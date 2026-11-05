package com.cnethuka.kegare.innate_technique.rime;

import com.cnethuka.kegare.Kegare;
import com.cnethuka.kegare.innatetechnique.Ability;
import com.cnethuka.kegare.innatetechnique.CastType;
import com.cnethuka.kegare.innatetechnique.SpecialAbility;
import java.util.function.Supplier;
import net.minecraft.server.level.ServerPlayer;

/** Shared bits of Rime's specials (R): the same environment multiplier and Home Field check as its abilities. */
abstract class RimeSpecial extends SpecialAbility {
    RimeSpecial(String name, CastType castType, Supplier<? extends Ability> base) {
        super(Kegare.id(name), castType, base);
    }

    static double environment(ServerPlayer player) {
        return RimeAbility.environment(player);
    }

    static void onCast(ServerPlayer player) {
        RimeAbility.onCast(player);
    }
}

package com.cnethuka.kegare.innate_technique.rime;

import com.cnethuka.kegare.Kegare;
import com.cnethuka.kegare.innatetechnique.BaseAbility;
import com.cnethuka.kegare.innatetechnique.CastType;
import net.minecraft.server.level.ServerPlayer;

/** Shared bits of Rime's abilities: the environment cost multiplier and the Home Field check. */
abstract class RimeAbility extends BaseAbility {
    RimeAbility(String name, CastType castType) {
        super(Kegare.id(name), castType);
    }

    /** Multiplies every Rime cost and upkeep (cheaper in the cold, dearer in the heat). */
    static double environment(ServerPlayer player) {
        return RimeEnvironment.evaluate(player).multiplier();
    }

    /** Call when a Rime ability actually goes off. */
    static void onCast(ServerPlayer player) {
        if (RimeEnvironment.snowfallInColdBiome(player)) RimeAdvancements.award(player, RimeAdvancements.HOME_FIELD);
    }
}

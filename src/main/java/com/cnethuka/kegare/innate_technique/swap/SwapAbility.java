package com.cnethuka.kegare.innate_technique.swap;

import com.cnethuka.kegare.Kegare;
import com.cnethuka.kegare.innatetechnique.Ability;
import com.cnethuka.kegare.innatetechnique.AbilityContext;
import com.cnethuka.kegare.innatetechnique.BaseAbility;
import com.cnethuka.kegare.innatetechnique.CastType;
import com.cnethuka.kegare.innatetechnique.SpecialAbility;
import java.util.function.Supplier;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** Shared bits of Swap's abilities. Abilities that need a target fizzle for free when nobody is in sight. */
abstract class SwapAbility extends BaseAbility {
    SwapAbility(String name, CastType castType) {
        super(Kegare.id(name), castType);
    }

    @Override
    public Component blockedMessage(ServerPlayer player) {
        return Component.translatable("message.kegare.swap.no_target");
    }

    /** For abilities that act on the player under the crosshair. */
    static boolean hasTarget(AbilityContext ctx) {
        return SwapTargeting.lookedAt(ctx.player()).isPresent();
    }

    /** A Swap special (R): shares its base ability's cooldown. */
    abstract static class Special extends SpecialAbility {
        Special(String name, CastType castType, Supplier<? extends Ability> base) {
            super(Kegare.id(name), castType, base);
        }

        @Override
        public Component blockedMessage(ServerPlayer player) {
            return Component.translatable("message.kegare.swap.no_target");
        }
    }
}

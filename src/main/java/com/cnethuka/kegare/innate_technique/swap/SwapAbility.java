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

/**
 * Shared bits of Swap's abilities. Abilities that need a target fizzle for free when nobody is in sight. Some take
 * players only (the disguises, Total Swap), the rest players or mobs ({@link SwapTargeting#lookedAtLiving}).
 */
abstract class SwapAbility extends BaseAbility {
    SwapAbility(String name, CastType castType) {
        super(Kegare.id(name), castType);
    }

    /** Whether mobs are valid targets too (else players only). */
    boolean takesMobs() {
        return false;
    }

    @Override
    public Component blockedMessage(ServerPlayer player) {
        return noTarget(takesMobs());
    }

    static Component noTarget(boolean mobs) {
        return Component.translatable(mobs ? "message.kegare.swap.no_target_any" : "message.kegare.swap.no_target");
    }

    /** A player under the crosshair. */
    static boolean hasTarget(AbilityContext ctx) {
        return SwapTargeting.lookedAt(ctx.player()).isPresent();
    }

    /** A player or a swappable mob under the crosshair. */
    static boolean hasLivingTarget(AbilityContext ctx) {
        return SwapTargeting.lookedAtLiving(ctx.player()).isPresent();
    }

    /** A Swap special (R): shares its base ability's cooldown. */
    abstract static class Special extends SpecialAbility {
        Special(String name, CastType castType, Supplier<? extends Ability> base) {
            super(Kegare.id(name), castType, base);
        }

        boolean takesMobs() {
            return false;
        }

        @Override
        public Component blockedMessage(ServerPlayer player) {
            return noTarget(takesMobs());
        }
    }
}

package com.cnethuka.kegare.innate_technique.puppet_manipulation;

import com.cnethuka.kegare.innatetechnique.AbilityContext;
import com.cnethuka.kegare.innatetechnique.CastType;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Special of Strings (R, INSTANT): the second stage's other half. Zip pulls you to the attached target; Leash, if you're
 * now looking at something else, leashes both to you. With no string attached it fizzles at no cost.
 */
public final class ZipLeashAbility extends PuppetAbility.Special {
    ZipLeashAbility() {
        super("zip_leash", CastType.INSTANT, () -> PuppetManipulation.STRINGS);
    }

    /** Nothing with no string: R then fizzles for free even with no Malice at all. */
    @Override
    public double cost(ServerPlayer p) {
        if (!Strings.isAttached(p)) return 0.0;
        return Strings.variation(p, true) == StringsVariation.LEASH ? PuppetConfig.STRINGS_LEASH_COST.get() : PuppetConfig.STRINGS_ZIP_COST.get();
    }

    @Override
    public boolean canCast(AbilityContext ctx) {
        ServerPlayer p = ctx.player();
        return Strings.isAttached(p) && Strings.canMoveAnything(p, Strings.variation(p, true));
    }

    @Override
    public Component blockedMessage(ServerPlayer player) {
        return Component.translatable(Strings.isAttached(player) ? "message.kegare.strings.immovable" : "message.kegare.strings.no_string");
    }

    @Override
    public void execute(AbilityContext ctx) {
        Strings.stageTwo(ctx.player(), Strings.variation(ctx.player(), true));
    }
}

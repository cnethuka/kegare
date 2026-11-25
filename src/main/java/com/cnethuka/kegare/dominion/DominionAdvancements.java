package com.cnethuka.kegare.dominion;

import com.cnethuka.kegare.Kegare;
import com.cnethuka.kegare.advancement.KegareAdvancements;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * Dominion clashes' advancements (Base tab, DESIGN §3.11 and §3.35): hidden and silent. A clash is seen by whoever is near, but an
 * announcement would tell the whole server that a player can open a Dominion, and so a floor under their private Grade (§3.21).
 */
public final class DominionAdvancements {
    /** Won a clash before it came to a fight (goal). */
    public static final ResourceLocation PUSHBACK = Kegare.id("pushback");
    /** Won a clash in the fight (challenge). */
    public static final ResourceLocation FIGHT_ME = Kegare.id("fight_me");
    /** A clash that broke both (task). */
    public static final ResourceLocation MUTUAL_DESTRUCTION = Kegare.id("mutual_destruction");

    private DominionAdvancements() {}

    static void decided(Clash clash, @Nullable Clash.Side winner, Clash.Result result) {
        switch (result) {
            case MAINTAIN -> award(winner, PUSHBACK);
            case FIGHT -> award(winner, FIGHT_ME);
            case TIE -> {
                for (Clash.Side s : clash.sides) award(s, MUTUAL_DESTRUCTION);
            }
            case FORFEIT -> {} // nothing was won
        }
    }

    private static void award(@Nullable Clash.Side side, ResourceLocation id) {
        if (side == null) return;
        ServerPlayer caster = Dominions.caster(side.dominion);
        if (caster != null) KegareAdvancements.award(caster, id);
    }
}

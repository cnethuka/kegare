package com.cnethuka.kegare.progression;

import com.cnethuka.kegare.api.technique.InnateTechnique;
import com.cnethuka.kegare.api.technique.InnateTechniqueRarity;
import com.cnethuka.kegare.innatetechnique.InnateTechniqueService;
import net.minecraft.server.level.ServerPlayer;

/** Where Insight comes from. */
public final class InsightHooks {
    private InsightHooks() {}

    private static boolean pay(ServerPlayer player, InsightSource source, String qualifier) {
        String key = qualifier.isEmpty() ? source.id() : source.id() + "/" + qualifier;
        return Insight.recordFirst(player, key, ProgressionConfig.insightCategory(source), ProgressionConfig.insightAmount(source));
    }

    /** The first time a technique lands on this player, once per technique. */
    static void hitByTechnique(ServerPlayer player, InnateTechnique technique) {
        pay(player, InsightSource.HIT_BY_TECHNIQUE, technique.id().toString());
    }

    /** The first fight against a holder of a Legendary technique, once ever. */
    static void onFightStarted(ServerPlayer player, ServerPlayer opponent) {
        InnateTechnique technique = InnateTechniqueService.current(opponent).orElse(null);
        if (technique != null && technique.rarity() == InnateTechniqueRarity.LEGENDARY) pay(player, InsightSource.FIRST_LEGENDARY_FIGHT, "");
    }

    /** Reverse Malice Technique unlocked, by any way (DESIGN §3.17). Once ever. */
    public static boolean unlockedRmt(ServerPlayer player) {
        return pay(player, InsightSource.UNLOCKED_RMT, "");
    }

    /** At 3 hearts or less during their own RMT, healed above it, and alive 10 s later. Once ever. */
    public static boolean rmtSurvivedLow(ServerPlayer player) {
        return pay(player, InsightSource.RMT_SURVIVED_LOW, "");
    }

    /** Healed by another player's RMT (its special). Once ever. */
    public static boolean healedByOther(ServerPlayer player) {
        return pay(player, InsightSource.HEALED_BY_OTHER, "");
    }

    /** Their first Black Spark ever, on anything living (DESIGN §3.28). Once ever. */
    public static boolean firstBlackSpark(ServerPlayer player) {
        return pay(player, InsightSource.FIRST_BLACK_SPARK, "");
    }

    /** Held in a Standstill and still alive when it ends. Once ever. */
    public static boolean survivedStandstill(ServerPlayer player) {
        return pay(player, InsightSource.SURVIVED_STANDSTILL, "");
    }

    /**
     * Trapped inside someone else's Domain as it closed (DESIGN §3.35). Once per kind of Domain: {@code domain} is the ability that
     * opened it (a technique's ultimate, so once per technique, or the Incomplete Domain).
     */
    public static boolean caughtInDomain(ServerPlayer player, net.minecraft.resources.ResourceLocation domain) {
        return pay(player, InsightSource.CAUGHT_IN_DOMAIN, domain.toString());
    }

    /** Took part in a Domain clash (DESIGN §3.35), against a Domain opened by {@code domain}. Once per kind, as above. */
    public static boolean clashedWithDomain(ServerPlayer player, net.minecraft.resources.ResourceLocation domain) {
        return pay(player, InsightSource.CLASHED_WITH_DOMAIN, domain.toString());
    }
}

package com.cnethuka.kegare.progression;

import com.cnethuka.kegare.innatetechnique.InnateTechnique;
import com.cnethuka.kegare.innatetechnique.InnateTechniqueRarity;
import com.cnethuka.kegare.innatetechnique.InnateTechniqueService;
import net.minecraft.server.level.ServerPlayer;

/**
 * Where Insight comes from. The first two are wired up. The rest are hooks with nothing calling them yet: Standstill,
 * Maximum Output, Dominions and the Book of Knowledge call theirs when they're built.
 */
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

    /** For the future Standstill. */
    public static boolean survivedStandstill(ServerPlayer player) {
        return pay(player, InsightSource.SURVIVED_STANDSTILL, "");
    }

    /** For the future Maximum Output. Once per technique, like any other hit. */
    public static boolean hitByMaximumOutput(ServerPlayer player, InnateTechnique technique) {
        return pay(player, InsightSource.HIT_BY_MAXIMUM_OUTPUT, technique.id().toString());
    }

    /** For the future Dominions. Once per technique. */
    public static boolean caughtInDominion(ServerPlayer player, InnateTechnique technique) {
        return pay(player, InsightSource.CAUGHT_IN_DOMINION, technique.id().toString());
    }

    /** For the future Dominions. Once per technique. */
    public static boolean clashedWithDominion(ServerPlayer player, InnateTechnique technique) {
        return pay(player, InsightSource.CLASHED_WITH_DOMINION, technique.id().toString());
    }

    /** For the future Book of Knowledge. Once per chapter. */
    public static boolean bookChapter(ServerPlayer player, String chapter) {
        return pay(player, InsightSource.BOOK_CHAPTER, chapter);
    }
}

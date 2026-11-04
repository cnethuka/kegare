package com.cnethuka.kegare.roll;

import com.cnethuka.kegare.Kegare;
import com.cnethuka.kegare.advancement.KegareAdvancements;
import com.cnethuka.kegare.ce.CeService;
import com.cnethuka.kegare.combat.CombatHelper;
import com.cnethuka.kegare.config.KegareServerConfig;
import com.cnethuka.kegare.network.PlayerSync;
import com.cnethuka.kegare.registry.KegareAttachments;
import com.cnethuka.kegare.registry.KegareRegistries;
import com.cnethuka.kegare.technique.Technique;
import com.cnethuka.kegare.technique.TechniqueRarity;
import com.cnethuka.kegare.technique.TechniqueService;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;

/** Technique rolls with pity. Never touches ce or traits. */
public final class RollService {
    /** @param rollNumber roll number the roll was evaluated as (1-based, since the last LEGENDARY) */
    public record Outcome(Technique technique, int rollNumber, boolean byHardPity, RollReason reason) {
        public TechniqueRarity rarity() {
            return technique.rarity();
        }
    }

    public enum RerollResult { SUCCESS, SEVERED, IN_COMBAT, NO_CANDIDATES }

    private RollService() {}

    public static RollData get(Player player) {
        return player.getData(KegareAttachments.ROLL);
    }

    public static PityRoller.Settings settings() {
        return new PityRoller.Settings(
                KegareServerConfig.rarityWeights(),
                KegareServerConfig.SOFT_PITY_START.get(),
                KegareServerConfig.SOFT_PITY_INCREMENT.get(),
                KegareServerConfig.HARD_PITY.get());
    }

    /**
     * Rolls and assigns a new technique. Excludes the current technique (never "reroll into yourself")
     * and test techniques when disabled in config. Pity is read and updated according to {@code reason}
     * (see {@link RollReason}, {@link PityState}). Empty if nothing is eligible (nothing changes then).
     */
    public static Optional<Outcome> rollTechnique(ServerPlayer player, RollReason reason) {
        Technique current = TechniqueService.current(player).orElse(null);
        boolean includeTest = KegareServerConfig.INCLUDE_TEST_TECHNIQUES.get();
        Map<TechniqueRarity, List<Technique>> pool = new EnumMap<>(TechniqueRarity.class);
        for (Technique technique : KegareRegistries.TECHNIQUE) {
            if (technique == current) continue;
            if (technique.isTestContent() && !includeTest) continue;
            pool.computeIfAbsent(technique.rarity(), r -> new ArrayList<>()).add(technique);
        }
        boolean[] available = new boolean[TechniqueRarity.values().length];
        for (TechniqueRarity rarity : pool.keySet()) available[rarity.ordinal()] = true;

        RollData roll = get(player);
        int counter = roll.state().effectiveCounter(reason);
        Optional<PityRoller.Result> result = PityRoller.pick(settings(), counter, available, player.getRandom()::nextInt);
        if (result.isEmpty()) {
            Kegare.LOGGER.warn("No eligible technique to roll for {}", player.getGameProfile().getName());
            return Optional.empty();
        }
        List<Technique> candidates = pool.get(result.get().rarity());
        Technique chosen = candidates.get(player.getRandom().nextInt(candidates.size()));

        roll.set(roll.state().after(chosen.rarity(), reason));
        TechniqueService.setTechnique(player, chosen);
        return Optional.of(new Outcome(chosen, counter + 1, result.get().byHardPity(), reason));
    }

    /**
     * A reroll (talisman or admin). Rerolls the technique ONLY. Blocked for Severed players, and the talisman
     * is also blocked in combat. Awards Reawakening (talisman only) before the outcome advancements.
     */
    public static RerollResult reroll(ServerPlayer player, RollReason reason) {
        if (CeService.isSevered(player)) {
            player.sendSystemMessage(Component.translatable("message.kegare.reroll.severed"));
            return RerollResult.SEVERED;
        }
        if (reason == RollReason.TALISMAN && CombatHelper.isInCombat(player)) {
            player.sendSystemMessage(Component.translatable("message.kegare.reroll.in_combat"));
            return RerollResult.IN_COMBAT;
        }
        Optional<Outcome> outcome = rollTechnique(player, reason);
        if (outcome.isEmpty()) {
            player.sendSystemMessage(Component.translatable("message.kegare.reroll.no_candidates"));
            return RerollResult.NO_CANDIDATES;
        }
        announce(player, outcome.get());
        if (reason.grantsReawakening()) KegareAdvancements.award(player, KegareAdvancements.REAWAKENING); // parent first
        awardOutcomeAdvancements(player, outcome.get());
        PlayerSync.markDirty(player);
        return RerollResult.SUCCESS;
    }

    public static void awardOutcomeAdvancements(ServerPlayer player, Outcome outcome) {
        if (outcome.rarity() == TechniqueRarity.LEGENDARY) KegareAdvancements.award(player, KegareAdvancements.CHOSEN_BY_FATE);
        if (outcome.byHardPity()) KegareAdvancements.award(player, KegareAdvancements.BITTER_LUCK);
    }

    /** Chat message for the new technique; LEGENDARY gets a fanfare. */
    public static void announce(ServerPlayer player, Outcome outcome) {
        Technique technique = outcome.technique();
        Component rarity = Component.translatable(technique.rarity().translationKey()).withColor(technique.rarity().color());
        switch (outcome.reason()) {
            case BIRTH -> player.sendSystemMessage(Component.translatable("message.kegare.birth.technique", technique.displayName(), rarity));
            case REPLACEMENT -> {
                player.sendSystemMessage(Component.translatable("message.kegare.roll.replaced"));
                player.sendSystemMessage(Component.translatable("message.kegare.birth.technique", technique.displayName(), rarity));
            }
            case TALISMAN, ADMIN -> player.sendSystemMessage(Component.translatable("message.kegare.reroll.success", technique.displayName(), rarity));
        }
        if (outcome.byHardPity()) {
            player.sendSystemMessage(Component.translatable("message.kegare.roll.hard_pity", outcome.rollNumber()));
        }
        if (technique.rarity() == TechniqueRarity.LEGENDARY) {
            player.playNotifySound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1.0f, 1.0f);
        }
    }

    /** Admin/debug: set the pity counter. */
    public static void setPity(ServerPlayer player, int rollsSinceLegendary) {
        get(player).setRollsSinceLegendary(rollsSinceLegendary);
    }
}

package com.cnethuka.kegare.roll;

import com.cnethuka.kegare.Kegare;
import com.cnethuka.kegare.advancement.KegareAdvancements;
import com.cnethuka.kegare.malice.MaliceService;
import com.cnethuka.kegare.combat.CombatHelper;
import com.cnethuka.kegare.config.KegareServerConfig;
import com.cnethuka.kegare.network.PlayerSync;
import com.cnethuka.kegare.registry.KegareAttachments;
import com.cnethuka.kegare.registry.KegareRegistries;
import com.cnethuka.kegare.innatetechnique.InnateTechnique;
import com.cnethuka.kegare.innatetechnique.InnateTechniqueRarity;
import com.cnethuka.kegare.innatetechnique.InnateTechniqueService;
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

/** Innate technique rolls with pity. Never touches Malice or traits. */
public final class RollService {
    /** @param rollNumber roll number the roll was evaluated as (1-based, since the last LEGENDARY) */
    public record Outcome(InnateTechnique innateTechnique, int rollNumber, boolean byHardPity, RollReason reason) {
        public InnateTechniqueRarity rarity() {
            return innateTechnique.rarity();
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
     * Rolls and assigns a new innate technique. Excludes the current innate technique (never "reroll into yourself")
     * and test innate techniques when disabled in config. Pity is read and updated according to {@code reason}
     * (see {@link RollReason}, {@link PityState}). Empty if nothing is eligible (nothing changes then).
     */
    public static Optional<Outcome> rollInnateTechnique(ServerPlayer player, RollReason reason) {
        InnateTechnique current = InnateTechniqueService.current(player).orElse(null);
        boolean includeTest = KegareServerConfig.INCLUDE_TEST_INNATE_TECHNIQUES.get();
        Map<InnateTechniqueRarity, List<InnateTechnique>> pool = new EnumMap<>(InnateTechniqueRarity.class);
        for (InnateTechnique innateTechnique : KegareRegistries.INNATE_TECHNIQUE) {
            if (innateTechnique == current) continue;
            if (innateTechnique.isTestContent() && !includeTest) continue;
            pool.computeIfAbsent(innateTechnique.rarity(), r -> new ArrayList<>()).add(innateTechnique);
        }
        boolean[] available = new boolean[InnateTechniqueRarity.values().length];
        for (InnateTechniqueRarity rarity : pool.keySet()) available[rarity.ordinal()] = true;

        RollData roll = get(player);
        int counter = roll.state().effectiveCounter(reason);
        Optional<PityRoller.Result> result = PityRoller.pick(settings(), counter, available, player.getRandom()::nextInt);
        if (result.isEmpty()) {
            Kegare.LOGGER.warn("No eligible innate technique to roll for {}", player.getGameProfile().getName());
            return Optional.empty();
        }
        List<InnateTechnique> candidates = pool.get(result.get().rarity());
        InnateTechnique chosen = candidates.get(player.getRandom().nextInt(candidates.size()));

        roll.set(roll.state().after(chosen.rarity(), reason));
        InnateTechniqueService.setInnateTechnique(player, chosen);
        // Its own advancement tab unlocks on the first roll (test innate techniques have no tab).
        KegareAdvancements.awardIfPresent(player, KegareAdvancements.innateTechniqueRoot(chosen.id()));
        return Optional.of(new Outcome(chosen, counter + 1, result.get().byHardPity(), reason));
    }

    /**
     * A reroll (talisman or admin). Rerolls the innate technique ONLY. Blocked for Severed players, and the talisman
     * is also blocked in combat. Awards Reawakening (talisman only) before the outcome advancements.
     */
    public static RerollResult reroll(ServerPlayer player, RollReason reason) {
        if (MaliceService.isSevered(player)) {
            player.sendSystemMessage(Component.translatable("message.kegare.reroll.severed"));
            return RerollResult.SEVERED;
        }
        if (reason == RollReason.TALISMAN && CombatHelper.isInCombat(player)) {
            player.sendSystemMessage(Component.translatable("message.kegare.reroll.in_combat"));
            return RerollResult.IN_COMBAT;
        }
        Optional<Outcome> outcome = rollInnateTechnique(player, reason);
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
        if (outcome.rarity() == InnateTechniqueRarity.LEGENDARY) KegareAdvancements.award(player, KegareAdvancements.CHOSEN_BY_FATE);
        if (outcome.byHardPity()) KegareAdvancements.award(player, KegareAdvancements.BITTER_LUCK);
    }

    /** Chat message for the new innate technique; LEGENDARY gets a fanfare. */
    public static void announce(ServerPlayer player, Outcome outcome) {
        InnateTechnique innateTechnique = outcome.innateTechnique();
        Component rarity = Component.translatable(innateTechnique.rarity().translationKey()).withColor(innateTechnique.rarity().color());
        switch (outcome.reason()) {
            case BIRTH -> player.sendSystemMessage(Component.translatable("message.kegare.birth.innate_technique", innateTechnique.displayName(), rarity));
            case REPLACEMENT -> {
                player.sendSystemMessage(Component.translatable("message.kegare.roll.replaced"));
                player.sendSystemMessage(Component.translatable("message.kegare.birth.innate_technique", innateTechnique.displayName(), rarity));
            }
            case TALISMAN, ADMIN -> player.sendSystemMessage(Component.translatable("message.kegare.reroll.success", innateTechnique.displayName(), rarity));
        }
        if (outcome.byHardPity()) {
            player.sendSystemMessage(Component.translatable("message.kegare.roll.hard_pity", outcome.rollNumber()));
        }
        if (innateTechnique.rarity() == InnateTechniqueRarity.LEGENDARY) {
            player.playNotifySound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1.0f, 1.0f);
        }
    }

    /** Admin/debug: set the pity counter. */
    public static void setPity(ServerPlayer player, int rollsSinceLegendary) {
        get(player).setRollsSinceLegendary(rollsSinceLegendary);
    }
}

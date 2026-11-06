package com.cnethuka.kegare.player;

import com.cnethuka.kegare.advancement.KegareAdvancements;
import com.cnethuka.kegare.malice.MaliceRoller;
import com.cnethuka.kegare.malice.MaliceRules;
import com.cnethuka.kegare.malice.MaliceService;
import com.cnethuka.kegare.combat.CombatData;
import com.cnethuka.kegare.network.PlayerSync;
import com.cnethuka.kegare.network.RevealPayload;
import com.cnethuka.kegare.registry.KegareAttachments;
import com.cnethuka.kegare.roll.RollData;
import com.cnethuka.kegare.roll.RollReason;
import com.cnethuka.kegare.roll.RollService;
import com.cnethuka.kegare.innatetechnique.EndReason;
import com.cnethuka.kegare.innatetechnique.CastManager;
import com.cnethuka.kegare.innatetechnique.InnateTechniqueData;
import com.cnethuka.kegare.innatetechnique.InnateTechniqueService;
import com.cnethuka.kegare.trait.Trait;
import com.cnethuka.kegare.trait.TraitService;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** First-join birth (Malice, traits, innate technique) and admin rebirth. */
public final class BirthService {
    private BirthService() {}

    /**
     * Rolls Malice once, then traits (Severed players skip Malice-based ones), then an innate technique (unless Severed).
     * Grants Awakening first so trait/outcome advancements land under it.
     */
    public static void birth(ServerPlayer player) {
        completeBirth(player, MaliceService.rollBirth(player));
    }

    /**
     * Admin debug: re-roll Malice FIRST, while the player still holds their traits. If that makes them Severed,
     * their Malice-based traits are stripped and unique ones pass on through the normal transfer. Then wipe the
     * remaining traits (unique ones released), innate technique, pity and combat state, and finish a fresh birth.
     */
    public static void rebirth(ServerPlayer player) {
        rebirth(player, OptionalInt.empty());
    }

    /** Admin debug: as {@link #rebirth(ServerPlayer)}, optionally forcing the Malice capacity (0 = Severed). */
    public static void rebirth(ServerPlayer player, OptionalInt forcedCapacity) {
        CastManager.stopAll(player, EndReason.INNATE_TECHNIQUE_CHANGED);
        InnateTechniqueService.setInnateTechnique(player, null);
        MaliceRoller.Result malice = forcedCapacity.isPresent()
                ? MaliceService.adminBirth(player, forcedCapacity.getAsInt())
                : MaliceService.rollBirth(player);
        TraitService.removeAll(player, Trait.RemoveReason.REBIRTH);
        player.setData(KegareAttachments.INNATE_TECHNIQUE, new InnateTechniqueData());
        player.setData(KegareAttachments.ROLL, new RollData());
        player.setData(KegareAttachments.COMBAT, new CombatData());
        completeBirth(player, malice);
    }

    private static void completeBirth(ServerPlayer player, MaliceRoller.Result malice) {
        KegareAdvancements.award(player, KegareAdvancements.AWAKENING);
        if (malice.capacity() <= 0) KegareAdvancements.award(player, KegareAdvancements.SEVERED);
        if (MaliceRules.isOverflowing(malice.tier())) KegareAdvancements.award(player, KegareAdvancements.OVERFLOWING);
        List<Trait> traits = TraitService.rollBirthTraits(player);
        Optional<RollService.Outcome> outcome = malice.capacity() > 0
                ? RollService.rollInnateTechnique(player, RollReason.BIRTH)
                : Optional.empty();

        Component tier = Component.translatable(malice.tier().translationKey());
        if (malice.capacity() <= 0) {
            player.sendSystemMessage(Component.translatable("message.kegare.birth.severed"));
        } else {
            player.sendSystemMessage(Component.translatable("message.kegare.birth.malice", malice.capacity(), tier));
        }
        for (Trait trait : traits) {
            if (trait.isUnique()) continue; // chat never names one (DESIGN §1): the private roll reveal tells them
            player.sendSystemMessage(Component.translatable("message.kegare.birth.trait", trait.displayName()));
        }
        if (malice.capacity() > 0 && outcome.isEmpty()) {
            player.sendSystemMessage(Component.translatable("message.kegare.reroll.no_candidates"));
        }
        outcome.ifPresent(o -> RollService.announce(player, o));
        outcome.ifPresent(o -> RollService.awardOutcomeAdvancements(player, o));
        PlayerSync.syncNow(player);
        // Everything above is decided and saved; the reveal only shows it (birth traits go to this player only).
        RevealPayload.sendBirth(player, malice, outcome, traits);
    }
}

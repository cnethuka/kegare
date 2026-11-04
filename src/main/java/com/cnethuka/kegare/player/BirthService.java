package com.cnethuka.kegare.player;

import com.cnethuka.kegare.advancement.KegareAdvancements;
import com.cnethuka.kegare.ce.CeData;
import com.cnethuka.kegare.ce.CeRoller;
import com.cnethuka.kegare.ce.CeService;
import com.cnethuka.kegare.combat.CombatData;
import com.cnethuka.kegare.network.PlayerSync;
import com.cnethuka.kegare.registry.KegareAttachments;
import com.cnethuka.kegare.roll.RollData;
import com.cnethuka.kegare.roll.RollReason;
import com.cnethuka.kegare.roll.RollService;
import com.cnethuka.kegare.technique.EndReason;
import com.cnethuka.kegare.technique.CastManager;
import com.cnethuka.kegare.technique.TechniqueData;
import com.cnethuka.kegare.technique.TechniqueService;
import com.cnethuka.kegare.trait.Trait;
import com.cnethuka.kegare.trait.TraitService;
import java.util.List;
import java.util.Optional;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** First-join birth (ce, traits, technique) and admin rebirth. */
public final class BirthService {
    private BirthService() {}

    /**
     * Rolls ce once, then traits (Severed players skip ce-based ones), then a technique (unless Severed).
     * Grants Awakening first so trait/outcome advancements land under it.
     */
    public static void birth(ServerPlayer player) {
        CeRoller.Result ce = CeService.rollBirth(player);
        KegareAdvancements.award(player, KegareAdvancements.AWAKENING);
        if (ce.capacity() <= 0) KegareAdvancements.award(player, KegareAdvancements.SEVERED);
        List<Trait> traits = TraitService.rollBirthTraits(player);
        Optional<RollService.Outcome> outcome = ce.capacity() > 0
                ? RollService.rollTechnique(player, RollReason.BIRTH)
                : Optional.empty();

        Component tier = Component.translatable(ce.tier().translationKey());
        if (ce.capacity() <= 0) {
            player.sendSystemMessage(Component.translatable("message.kegare.birth.severed"));
        } else {
            player.sendSystemMessage(Component.translatable("message.kegare.birth.ce", ce.capacity(), tier));
        }
        for (Trait trait : traits) {
            player.sendSystemMessage(Component.translatable("message.kegare.birth.trait", trait.displayName()));
        }
        if (ce.capacity() > 0 && outcome.isEmpty()) {
            player.sendSystemMessage(Component.translatable("message.kegare.reroll.no_candidates"));
        }
        outcome.ifPresent(o -> RollService.announce(player, o));
        outcome.ifPresent(o -> RollService.awardOutcomeAdvancements(player, o));
        PlayerSync.syncNow(player);
    }

    /** Admin debug: wipe ce, traits (releasing unique ones), technique, pity and combat state, then birth again. */
    public static void rebirth(ServerPlayer player) {
        CastManager.stopAll(player, EndReason.TECHNIQUE_CHANGED);
        TechniqueService.setTechnique(player, null);
        TraitService.removeAll(player, Trait.RemoveReason.REBIRTH);
        player.setData(KegareAttachments.CE, new CeData());
        player.setData(KegareAttachments.TECHNIQUE, new TechniqueData());
        player.setData(KegareAttachments.ROLL, new RollData());
        player.setData(KegareAttachments.COMBAT, new CombatData());
        birth(player);
    }
}

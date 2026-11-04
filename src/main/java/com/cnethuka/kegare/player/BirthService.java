package com.cnethuka.kegare.player;

import com.cnethuka.kegare.advancement.KegareAdvancements;
import com.cnethuka.kegare.malice.MaliceData;
import com.cnethuka.kegare.malice.MaliceRoller;
import com.cnethuka.kegare.malice.MaliceService;
import com.cnethuka.kegare.combat.CombatData;
import com.cnethuka.kegare.network.PlayerSync;
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
        MaliceRoller.Result malice = MaliceService.rollBirth(player);
        KegareAdvancements.award(player, KegareAdvancements.AWAKENING);
        if (malice.capacity() <= 0) KegareAdvancements.award(player, KegareAdvancements.SEVERED);
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
            player.sendSystemMessage(Component.translatable("message.kegare.birth.trait", trait.displayName()));
        }
        if (malice.capacity() > 0 && outcome.isEmpty()) {
            player.sendSystemMessage(Component.translatable("message.kegare.reroll.no_candidates"));
        }
        outcome.ifPresent(o -> RollService.announce(player, o));
        outcome.ifPresent(o -> RollService.awardOutcomeAdvancements(player, o));
        PlayerSync.syncNow(player);
    }

    /** Admin debug: wipe Malice, traits (releasing unique ones), innate technique, pity and combat state, then birth again. */
    public static void rebirth(ServerPlayer player) {
        CastManager.stopAll(player, EndReason.INNATE_TECHNIQUE_CHANGED);
        InnateTechniqueService.setInnateTechnique(player, null);
        TraitService.removeAll(player, Trait.RemoveReason.REBIRTH);
        player.setData(KegareAttachments.MALICE, new MaliceData());
        player.setData(KegareAttachments.INNATE_TECHNIQUE, new InnateTechniqueData());
        player.setData(KegareAttachments.ROLL, new RollData());
        player.setData(KegareAttachments.COMBAT, new CombatData());
        birth(player);
    }
}

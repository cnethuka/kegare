package com.cnethuka.kegare.progression;

import com.cnethuka.kegare.innatetechnique.Ability;
import com.cnethuka.kegare.innatetechnique.InnateTechniqueService;
import com.cnethuka.kegare.network.ProgressionToastPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

/** Small private toasts. Only their owner ever gets one: Grade and unlocks are nobody else's business. */
public final class ProgressionToasts {
    private ProgressionToasts() {}

    private static void send(ServerPlayer player, ProgressionToastPayload.Kind kind, String id) {
        // A FakePlayer in a test or from another mod has nothing to send it to.
        if (player.connection != null) PacketDistributor.sendToPlayer(player, new ProgressionToastPayload(kind, id));
    }

    static void promoted(ServerPlayer player, Grade grade) {
        send(player, ProgressionToastPayload.Kind.PROMOTION, grade.id());
    }

    static void abilityUnlocked(ServerPlayer player, Ability ability) {
        boolean ultimate = InnateTechniqueService.current(player).flatMap(t -> t.ultimate()).filter(u -> u == ability).isPresent();
        ProgressionToastPayload.Kind kind = ultimate ? ProgressionToastPayload.Kind.ULTIMATE
                : ability.isSpecial() ? ProgressionToastPayload.Kind.SPECIAL : ProgressionToastPayload.Kind.ABILITY;
        send(player, kind, ability.id().toString());
    }

    static void unlocked(ServerPlayer player, ResourceLocation id) {
        send(player, ProgressionToastPayload.Kind.UNLOCK, id.toString());
    }
}

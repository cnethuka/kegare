package com.cnethuka.kegare.trait;

import com.cnethuka.kegare.advancement.KegareAdvancements;
import com.cnethuka.kegare.config.KegareServerConfig;
import net.minecraft.server.level.ServerPlayer;

/**
 * Lensing Eyes: unique, needs Malice, inheritable, lost when the bearer is killed by another player.
 * No gameplay effects yet. This class only wires succession settings and its advancements.
 */
public final class LensingEyesTrait extends Trait {
    public LensingEyesTrait() {
        super(new Properties()
                .unique()
                .requiresMalice()
                .inheritable()
                .lostOnPvpDeath()
                .birthChance(KegareServerConfig.LENSING_EYES_BIRTH_CHANCE::get));
    }

    @Override
    public int successionDelayTicks() {
        return KegareServerConfig.ticks(KegareServerConfig.LENSING_EYES_TRANSFER_DELAY);
    }

    @Override
    public TransferMode successionMode() {
        return KegareServerConfig.LENSING_EYES_TRANSFER_MODE.get();
    }

    @Override
    public String successionBroadcastKey() {
        return "message.kegare.trait.lensing_eyes.succession";
    }

    @Override
    public void onAdded(ServerPlayer player, AddReason reason) {
        switch (reason) {
            case BIRTH -> KegareAdvancements.award(player, KegareAdvancements.CLEAR_SIGHT);
            case INHERITED -> KegareAdvancements.award(player, KegareAdvancements.THE_CYCLE_CONTINUES);
            case ADMIN -> { }
        }
    }

    @Override
    public void onRemoved(ServerPlayer player, RemoveReason reason) {
        switch (reason) {
            case LOST_IN_PVP, RELEASED, REVOKED -> KegareAdvancements.award(player, KegareAdvancements.EYES_CLOSED);
            default -> { }
        }
    }

    @Override
    public void onBearerKilledByPlayer(ServerPlayer victim, ServerPlayer killer) {
        KegareAdvancements.award(killer, KegareAdvancements.BLINDED);
    }
}

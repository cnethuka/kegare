package com.cnethuka.kegare.trait;

import com.cnethuka.kegare.advancement.KegareAdvancements;
import com.cnethuka.kegare.config.KegareServerConfig;
import net.minecraft.server.level.ServerPlayer;

/**
 * Six Eyes: unique, needs Malice, inheritable, lost when the bearer is killed by another player.
 * Its gameplay is the {@code sixeyes} package (DESIGN §3.32); this class wires succession settings, its advancements and what passes on.
 */
public final class SixEyesTrait extends Trait {
    public SixEyesTrait() {
        super(new Properties()
                .unique()
                .requiresMalice()
                .inheritable()
                .lostOnPvpDeath()
                .birthChance(KegareServerConfig.SIX_EYES_BIRTH_CHANCE::get));
    }

    @Override
    public int successionDelayTicks() {
        return KegareServerConfig.ticks(KegareServerConfig.SIX_EYES_TRANSFER_DELAY);
    }

    @Override
    public TransferMode successionMode() {
        return KegareServerConfig.SIX_EYES_TRANSFER_MODE.get();
    }

    @Override
    public String successionBroadcastKey() {
        return "message.kegare.trait.six_eyes.succession";
    }

    @Override
    public void onAdded(ServerPlayer player, AddReason reason) {
        com.cnethuka.kegare.sixeyes.SixEyes.gained(player);
        switch (reason) {
            case BIRTH -> KegareAdvancements.award(player, KegareAdvancements.CLEAR_SIGHT);
            case INHERITED -> KegareAdvancements.award(player, KegareAdvancements.THE_CYCLE_CONTINUES);
            case ADMIN -> { }
        }
    }

    @Override
    public void onRemoved(ServerPlayer player, RemoveReason reason) {
        com.cnethuka.kegare.sixeyes.SixEyes.lost(player);
        switch (reason) {
            case LOST_IN_PVP, RELEASED, REVOKED, SEVERED -> KegareAdvancements.award(player, KegareAdvancements.EYES_CLOSED);
            default -> { }
        }
    }

    @Override
    public void onBearerKilledByPlayer(ServerPlayer victim, ServerPlayer killer) {
        KegareAdvancements.award(killer, KegareAdvancements.BLINDED);
    }
}

package com.cnethuka.kegare.network;

import com.cnethuka.kegare.registry.KegareAttachments;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Server-to-owner sync. Anything that changes client-visible state calls {@link #markDirty};
 * the player tick sends at most one {@link SyncPayload} per tick via {@link #flush}.
 */
public final class PlayerSync {
    private PlayerSync() {}

    public static void markDirty(ServerPlayer player) {
        player.getData(KegareAttachments.SYNC_STATE).dirty = true;
    }

    public static void flush(ServerPlayer player) {
        if (player.getData(KegareAttachments.SYNC_STATE).dirty) syncNow(player);
    }

    public static void syncNow(ServerPlayer player) {
        player.getData(KegareAttachments.SYNC_STATE).dirty = false;
        if (player instanceof FakePlayer || player.connection == null) return;
        PacketDistributor.sendToPlayer(player, SyncPayload.of(player));
    }
}

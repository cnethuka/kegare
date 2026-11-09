package com.cnethuka.kegare.mixin;

import com.cnethuka.kegare.database.KegareDatabase;
import java.util.Optional;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Right after a player's world file is read (or found not to be there), before anything else in the game can read their data: with Postgres
 * storage on, what's in the database goes on them here, so nothing rolls for them first (DESIGN §3.33).
 */
@Mixin(PlayerList.class)
abstract class PlayerListLoadMixin {
    @Inject(method = "load", at = @At("RETURN"))
    private void kegare$applyStoredData(ServerPlayer player, CallbackInfoReturnable<Optional<CompoundTag>> callback) {
        KegareDatabase database = KegareDatabase.current();
        if (database != null) database.applyOnJoin(player);
    }
}

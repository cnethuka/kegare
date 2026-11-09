package com.cnethuka.kegare.mixin;

import com.cnethuka.kegare.database.KegareDatabase;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.storage.PlayerDataStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** With Postgres storage on, Kegare's keys aren't written to the world's player files (DESIGN §3.33). */
@Mixin(PlayerDataStorage.class)
abstract class PlayerDataStorageMixin {
    @ModifyArg(method = "save", at = @At(value = "INVOKE", target = "Lnet/minecraft/nbt/NbtIo;writeCompressed(Lnet/minecraft/nbt/CompoundTag;Ljava/nio/file/Path;)V"), index = 0)
    private CompoundTag kegare$withoutStoredData(CompoundTag file) {
        KegareDatabase.beforePlayerFileWritten(file);
        return file;
    }
}

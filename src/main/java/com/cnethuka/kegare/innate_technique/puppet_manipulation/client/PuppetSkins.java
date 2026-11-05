package com.cnethuka.kegare.innate_technique.puppet_manipulation.client;

import com.cnethuka.kegare.innate_technique.puppet_manipulation.PuppetEntity;
import com.cnethuka.kegare.innate_technique.puppet_manipulation.PuppetShellEntity;
import com.cnethuka.kegare.innate_technique.swap.client.DisguiseClient;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.PlayerSkin;

/**
 * A puppet's look: its owner's real skin (never a Swap disguise they happen to wear). From the tab list while the owner
 * is online; otherwise from the skin property the puppet carries (loaded once by the skin manager, which caches it),
 * falling back to the owner's default skin.
 */
public final class PuppetSkins {
    private PuppetSkins() {}

    public static PlayerSkin skin(PuppetEntity puppet) {
        return skin(puppet.ownerId(), puppet::ownerProfile);
    }

    /** A possessing player's body: their real skin too. */
    public static PlayerSkin skin(PuppetShellEntity shell) {
        return skin(shell.ownerId(), shell::ownerProfile);
    }

    private static PlayerSkin skin(UUID owner, java.util.function.Supplier<com.mojang.authlib.GameProfile> profile) {
        Minecraft mc = Minecraft.getInstance();
        ClientPacketListener connection = mc.getConnection();
        if (owner != null && connection != null) {
            PlayerInfo info = connection.getPlayerInfo(owner);
            if (info != null) return DisguiseClient.resolving(info::getSkin);
        }
        return mc.getSkinManager().getInsecureSkin(profile.get());
    }
}

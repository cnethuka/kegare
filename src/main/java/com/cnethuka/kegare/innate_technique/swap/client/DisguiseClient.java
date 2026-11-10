package com.cnethuka.kegare.innate_technique.swap.client;

import com.cnethuka.kegare.innate_technique.swap.Disguises;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import com.cnethuka.kegare.sixeyes.client.SightClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderNameTagEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Draws Swap's disguises ({@link Disguises}, synced). A disguised player is drawn as who they look like: skin, arm
 * model and cape ({@code PlayerInfoMixin}), tab-list name ({@code PlayerTabOverlayMixin}), name tag, visible armor and
 * held items (render events below). Never for the local player: everyone sees their own body, HUD and inventory
 * normally. Nothing real moves: equipment is only borrowed for the length of one render.
 */
public final class DisguiseClient {
    /** Set while resolving the face behind a disguise, so a two-way swap doesn't chase itself. Client thread only. */
    private static boolean resolving;
    /** Equipment lent for one render: entity ID -> its own armor (4), main hand, off hand. */
    private static final Map<Integer, ItemStack[]> LENT = new HashMap<>();

    private DisguiseClient() {}

    static void init() {
        NeoForge.EVENT_BUS.addListener(DisguiseClient::onNameTag);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, DisguiseClient::onRenderPre);
        NeoForge.EVENT_BUS.addListener(DisguiseClient::onRenderPost);
        NeoForge.EVENT_BUS.addListener(DisguiseClient::onLogout);
    }

    /** Who this player should be drawn as on this client, or null (not disguised, expired, or it's us). */
    @Nullable
    public static UUID shownAs(UUID player) {
        if (resolving) return null;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || player.equals(mc.player.getUUID())) return null;
        Disguises.Entry entry = Disguises.clientEntry(player);
        if (entry == null || entry.endTick() <= mc.level.getGameTime()) return null;
        return entry.as();
    }

    /** The tab-list entry of who this player looks like, or null. A Six Eyes bearer's list keeps the real name: they know people by their energy. */
    @Nullable
    public static PlayerInfo shownInfo(UUID player) {
        UUID as = SightClient.isBearer() ? null : shownAs(player);
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        return as == null || connection == null ? null : connection.getPlayerInfo(as);
    }

    /** The skin to draw for this player, or null for their own. */
    @Nullable
    public static PlayerSkin skinFor(UUID player) {
        UUID as = shownAs(player);
        if (as == null) return null;
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        PlayerInfo info = connection == null ? null : connection.getPlayerInfo(as);
        if (info == null) return DefaultPlayerSkin.get(as);
        return resolving(info::getSkin);
    }

    /** Runs {@code task} with disguises switched off (to read the real look of the face being borrowed). */
    public static <T> T resolving(Supplier<T> task) {
        boolean was = resolving;
        resolving = true;
        try {
            return task.get();
        } finally {
            resolving = was;
        }
    }

    // ---------------------------------------------------------------- name tag

    private static void onNameTag(RenderNameTagEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        // The face stays, but a Six Eyes bearer knows people by their energy and reads the real name over it (DESIGN §3.32).
        UUID as = SightClient.isBearer() ? null : shownAs(player.getUUID());
        if (as != null) event.setContent(nameOf(as));
    }

    private static Component nameOf(UUID as) {
        Minecraft mc = Minecraft.getInstance();
        Player face = mc.level == null ? null : mc.level.getPlayerByUUID(as);
        if (face != null) return face.getDisplayName();
        PlayerInfo info = mc.getConnection() == null ? null : mc.getConnection().getPlayerInfo(as);
        return Component.literal(info != null ? info.getProfile().getName() : "?");
    }

    // ---------------------------------------------------------------- armor and held items

    private static void onRenderPre(RenderPlayerEvent.Pre event) {
        Player player = event.getEntity();
        UUID as = shownAs(player.getUUID());
        if (as == null) return;
        Minecraft mc = Minecraft.getInstance();
        Player face = mc.level == null ? null : mc.level.getPlayerByUUID(as);
        Inventory inv = player.getInventory();
        int hand = Inventory.isHotbarSlot(inv.selected) ? inv.selected : 0;
        LENT.put(player.getId(), new ItemStack[] {inv.armor.get(0), inv.armor.get(1), inv.armor.get(2), inv.armor.get(3),
                inv.items.get(hand), inv.offhand.get(0)});
        // The face's visible gear, or nothing if this client can't see the face (better bare than the wearer's own).
        for (int i = 0; i < 4; i++) inv.armor.set(i, face != null ? face.getInventory().armor.get(i) : ItemStack.EMPTY);
        inv.items.set(hand, face != null ? face.getMainHandItem() : ItemStack.EMPTY);
        inv.offhand.set(0, face != null ? face.getOffhandItem() : ItemStack.EMPTY);
    }

    private static void onRenderPost(RenderPlayerEvent.Post event) {
        ItemStack[] own = LENT.remove(event.getEntity().getId());
        if (own == null) return;
        Inventory inv = event.getEntity().getInventory();
        int hand = Inventory.isHotbarSlot(inv.selected) ? inv.selected : 0;
        for (int i = 0; i < 4; i++) inv.armor.set(i, own[i]);
        inv.items.set(hand, own[4]);
        inv.offhand.set(0, own[5]);
    }

    private static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        Disguises.clearClient();
        LENT.clear();
    }
}

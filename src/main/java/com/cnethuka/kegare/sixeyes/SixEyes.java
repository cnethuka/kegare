package com.cnethuka.kegare.sixeyes;

import com.cnethuka.kegare.trait.KegareTraits;
import com.cnethuka.kegare.trait.TraitService;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * Six Eyes (DESIGN §3.32): passive and always on, at full from the moment someone has it. It sees Malice everywhere in its dimension in
 * detail ({@link Sight}), identifies people by it, sees what has none as a void, and lets its bearer use energy with near-zero waste.
 * Uncovered too long, the brain tires. It never gives a faster body or faster cooldowns. Everything it sees is sent to the bearer alone.
 */
public final class SixEyes {
    private SixEyes() {}

    public static boolean isBearer(Player player) {
        return TraitService.has(player, KegareTraits.SIX_EYES.get());
    }

    /** Every online bearer (at most one: the trait is unique). */
    public static List<ServerPlayer> bearers(MinecraftServer server) {
        List<ServerPlayer> out = new ArrayList<>();
        for (ServerPlayer p : everyone(server)) if (isBearer(p) && p.isAlive()) out.add(p);
        return out;
    }

    /** Every player in every dimension (as each level holds them). */
    public static List<ServerPlayer> everyone(MinecraftServer server) {
        List<ServerPlayer> out = new ArrayList<>();
        for (net.minecraft.server.level.ServerLevel level : server.getAllLevels()) out.addAll(level.players());
        return out;
    }

    /** Someone gains the eyes: a new bearer starts out clumsy, with no Strain. */
    public static void gained(ServerPlayer player) {
        Strain.data(player).wipe();
    }

    /** Someone loses them: everything goes with them. */
    public static void lost(ServerPlayer player) {
        Strain.data(player).wipe();
        Strain.forget(player);
    }

    public static void init() {
        Sight.init();
        MobIntent.init();
        Reads.init();
        Strain.init();
        Edge.init();
        Efficiency.init();
        Attunement.init();
        // Something's Missing: a full Read that finds nothing there. (The server decides the focus, so this is the Read it can vouch for.)
        Reads.addFocusListener((bearer, target) -> {
            if (com.cnethuka.kegare.malice.MaliceService.isSevered(target)) {
                com.cnethuka.kegare.advancement.KegareAdvancements.award(bearer, com.cnethuka.kegare.advancement.KegareAdvancements.SOMETHINGS_MISSING);
            }
        });
    }
}

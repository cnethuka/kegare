package com.cnethuka.kegare.innate_technique.puppet_manipulation;

import com.cnethuka.kegare.Kegare;
import com.cnethuka.kegare.advancement.KegareAdvancements;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/**
 * Puppet Manipulation's advancement tab (root {@code kegare:innate_technique/puppet_manipulation}, unlocked on first
 * rolling it). Every one is hidden and silent in chat (they would reveal who holds it), uses minecraft:impossible and is
 * awarded from code, to holders only. Learning ones (task) may count anything; mastery ones (goal, challenge) need a
 * PLAYER on the other side (DESIGN §4).
 */
public final class PuppetAdvancements {
    public static final ResourceLocation ROOT = KegareAdvancements.innateTechniqueRoot(Kegare.id("puppet_manipulation"));
    public static final ResourceLocation PULLING_STRINGS = id("pulling_strings");
    public static final ResourceLocation OUT_OF_BODY = id("out_of_body");
    public static final ResourceLocation KURU_KURU = id("kuru_kuru");
    public static final ResourceLocation NOBODY_HOME = id("nobody_home");
    public static final ResourceLocation DECOY = id("decoy");
    public static final ResourceLocation WHOS_REAL = id("whos_real");
    public static final ResourceLocation CURIOSITY_KILLED = id("curiosity_killed");
    public static final ResourceLocation LITTLE_ME = id("little_me");
    public static final ResourceLocation GET_OVER_HERE = id("get_over_here");
    public static final ResourceLocation TIED_UP = id("tied_up");
    public static final ResourceLocation WALKIES = id("walkies");
    public static final ResourceLocation STRINGS_ATTACHED = id("strings_attached");

    /** Kuru Kuru: this many of your puppets spinning at once. */
    public static final int KURU_KURU_PUPPETS = 10;
    /** Decoy: you must be further than this (blocks) from the puppet a player kills (or in another dimension). */
    public static final double DECOY_DISTANCE = 16.0;

    private PuppetAdvancements() {}

    private static ResourceLocation id(String name) {
        return Kegare.id("innate_technique/puppet_manipulation/" + name);
    }

    /** Only a player who currently holds Puppet Manipulation earns these (so the tab never opens for anyone else). */
    public static void award(ServerPlayer player, ResourceLocation id) {
        if (PuppetManipulation.holds(player)) KegareAdvancements.award(player, id);
    }

    /** Kuru Kuru, checked once a second: whose puppets are spinning, and how many. */
    static void checkSpinning(MinecraftServer server) {
        Map<UUID, Integer> spinning = new HashMap<>();
        for (ServerLevel level : server.getAllLevels()) {
            for (Entity e : level.getEntities(PuppetRegistry.PUPPET.get(), p -> p.isSpinning() && p.ownerId() != null)) {
                spinning.merge(((PuppetEntity) e).ownerId(), 1, Integer::sum);
            }
        }
        spinning.forEach((owner, count) -> {
            if (count < KURU_KURU_PUPPETS) return;
            ServerPlayer player = online(server, owner);
            if (player != null) award(player, KURU_KURU);
        });
    }

    /** An online player by UUID (players in a level but not the player list, like test fake players, count too). */
    private static ServerPlayer online(MinecraftServer server, UUID id) {
        ServerPlayer player = server.getPlayerList().getPlayer(id);
        if (player != null) return player;
        for (ServerLevel level : server.getAllLevels()) {
            if (level.getPlayerByUUID(id) instanceof ServerPlayer found) return found;
        }
        return null;
    }

    /** Decoy: a player killed one of {@code owner}'s puppets while the owner was far away. */
    static void puppetKilled(PuppetEntity puppet, ServerPlayer killer) {
        UUID ownerId = puppet.ownerId();
        if (ownerId == null || killer.getUUID().equals(ownerId)) return;
        ServerPlayer owner = online(killer.server, ownerId);
        if (owner == null) return;
        boolean far = owner.level() != puppet.level() || owner.distanceToSqr(puppet) > DECOY_DISTANCE * DECOY_DISTANCE;
        if (far) award(owner, DECOY);
    }
}

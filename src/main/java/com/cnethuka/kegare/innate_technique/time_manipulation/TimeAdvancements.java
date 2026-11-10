package com.cnethuka.kegare.innate_technique.time_manipulation;

import com.cnethuka.kegare.Kegare;
import com.cnethuka.kegare.advancement.KegareAdvancements;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * Time Manipulation's advancement tab (root {@code kegare:innate_technique/time_manipulation}, unlocked on first rolling it). Every one is
 * hidden and silent in chat (they would reveal who has it), uses minecraft:impossible and is awarded from code. The learning ones
 * (task: The World Stood Still, Overclock) need no target; the mastery ones (goal and challenge) count players only (DESIGN §4).
 */
public final class TimeAdvancements {
    public static final ResourceLocation ROOT = KegareAdvancements.innateTechniqueRoot(Kegare.id("time_manipulation"));
    public static final ResourceLocation THE_WORLD_STOOD_STILL = id("the_world_stood_still");
    public static final ResourceLocation OVERCLOCK = id("overclock");
    public static final ResourceLocation SECOND_CHANCE = id("second_chance");
    public static final ResourceLocation SLOW_MOTION = id("slow_motion");
    public static final ResourceLocation NOT_SO_FAST = id("not_so_fast");
    public static final ResourceLocation FROZEN_IN_FLIGHT = id("frozen_in_flight");
    public static final ResourceLocation SAME_CLOCK = id("same_clock");
    public static final ResourceLocation PRE_PLANNED = id("pre_planned");

    /** Overclock: Accelerate on for 30 seconds straight. */
    public static final long OVERCLOCK_TICKS = 30 * 20;
    /** Second Chance: the return made under 3 hearts... */
    public static final float SECOND_CHANCE_HEALTH = 6f;
    /** ...and still alive 10 seconds later. */
    public static final long SECOND_CHANCE_SURVIVE_TICKS = 10 * 20;
    /** Not So Fast: the Rewound player had got this much further from you in those seconds. */
    public static final double NOT_SO_FAST_BLOCKS = 8.0;
    /** Frozen in Flight: this many players' projectiles caught in one Stasis Field. */
    public static final int FROZEN_IN_FLIGHT_COUNT = 5;
    /** Pre-Planned: this many of your projectiles that hung in your Standstill hitting players... */
    public static final int PRE_PLANNED_HITS = 5;
    /** ...within this long of time resuming (5 s). */
    public static final long PRE_PLANNED_WINDOW_TICKS = 5 * 20;

    /** Second Chance in progress: when each player made the return. */
    private static final Map<UUID, Long> SECOND_CHANCE_SINCE = new HashMap<>();

    private TimeAdvancements() {}

    private static ResourceLocation id(String name) {
        return Kegare.id("innate_technique/time_manipulation/" + name);
    }

    /** Only a player who currently holds Time Manipulation earns these (so the tab never opens for anyone else). */
    public static void award(ServerPlayer player, ResourceLocation id) {
        if (TimeManipulation.holds(player)) KegareAdvancements.award(player, id);
    }

    /** A Point Maker return made under {@link #SECOND_CHANCE_HEALTH}: Second Chance if they live {@link #SECOND_CHANCE_SURVIVE_TICKS}. */
    static void secondChanceStarted(ServerPlayer player) {
        SECOND_CHANCE_SINCE.put(player.getUUID(), player.level().getGameTime());
    }

    static void tick(ServerPlayer player) {
        Long since = SECOND_CHANCE_SINCE.get(player.getUUID());
        if (since == null || !player.isAlive()) return;
        if (player.level().getGameTime() - since >= SECOND_CHANCE_SURVIVE_TICKS) {
            SECOND_CHANCE_SINCE.remove(player.getUUID());
            award(player, SECOND_CHANCE);
        }
    }

    /** Death or logout cancels a Second Chance in progress. */
    static void forget(UUID player) {
        SECOND_CHANCE_SINCE.remove(player);
    }

    static void clear() {
        SECOND_CHANCE_SINCE.clear();
    }
}

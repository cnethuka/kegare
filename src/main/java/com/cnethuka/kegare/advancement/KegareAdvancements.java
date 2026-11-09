package com.cnethuka.kegare.advancement;

import com.cnethuka.kegare.Kegare;
import java.util.Set;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * Advancement IDs. All use {@code minecraft:impossible} triggers (see {@code KegareAdvancementGenerator})
 * and are awarded only from code via {@link #award}.
 */
public final class KegareAdvancements {
    // Base tab (root: awakening)
    public static final ResourceLocation AWAKENING = Kegare.id("awakening");
    public static final ResourceLocation REAWAKENING = Kegare.id("reawakening");
    public static final ResourceLocation CHOSEN_BY_FATE = Kegare.id("chosen_by_fate");
    public static final ResourceLocation BITTER_LUCK = Kegare.id("bitter_luck");
    public static final ResourceLocation SEVERED = Kegare.id("severed");
    public static final ResourceLocation OVERFLOWING = Kegare.id("overflowing");
    public static final ResourceLocation RUNNING_ON_EMPTY = Kegare.id("running_on_empty");
    // Reverse Malice Technique (Base tab, hidden and silent: they reveal that a player can heal)
    public static final ResourceLocation A_GREEN_LIGHT = Kegare.id("a_green_light");
    public static final ResourceLocation ITS_BEEN_A_WHILE = Kegare.id("its_been_a_while");
    /** Stealing another person's kill (progression.BushCamper): hidden, announced. */
    public static final ResourceLocation BUSH_CAMPER = Kegare.id("bush_camper");
    /** Reading a void for the first time (sixeyes.SixEyes, Six Eyes tab): hidden and silent like the rest of it. */
    public static final ResourceLocation SOMETHINGS_MISSING = Kegare.id("somethings_missing");
    /** Explaining RMT to the one you're fighting (rmt.AreTheyHigh): hidden, announced. */
    public static final ResourceLocation ARE_THEY_HIGH = Kegare.id("are_they_high");
    /** Background RMT unlocked (DESIGN §3.31). */
    public static final ResourceLocation ALWAYS_ON = Kegare.id("always_on");
    // Progression (Base tab, hidden and silent: Grade is private)
    public static final ResourceLocation MOVING_UP = Kegare.id("moving_up");
    public static final ResourceLocation PROVEN = Kegare.id("proven");
    public static final ResourceLocation RECOGNIZED = Kegare.id("recognized");
    public static final ResourceLocation OUTSIDE_THE_LADDER = Kegare.id("outside_the_ladder");
    public static final ResourceLocation FULL_KIT = Kegare.id("full_kit");
    // Six Eyes tab (root: clear_sight)
    public static final ResourceLocation CLEAR_SIGHT = Kegare.id("clear_sight");
    public static final ResourceLocation THE_CYCLE_CONTINUES = Kegare.id("the_cycle_continues");
    public static final ResourceLocation EYES_CLOSED = Kegare.id("eyes_closed");
    public static final ResourceLocation BLINDED = Kegare.id("blinded");
    // Shrine (FUTURE, reserved: not generated or awarded yet)
    public static final ResourceLocation VESSEL_OF_THE_KING = Kegare.id("vessel_of_the_king");
    public static final ResourceLocation UNWORTHY_HEIR = Kegare.id("unworthy_heir");
    public static final ResourceLocation UNSEALED = Kegare.id("unsealed");

    /**
     * Design rule: an advancement that would reveal a secret identity (who holds a unique trait or the Shrine)
     * is hidden and never announced to chat; only its earner sees the toast. The generator applies this to every
     * ID here automatically, and datagen fails if one ever isn't hidden and silent. Add future secret IDs here.
     */
    public static final Set<ResourceLocation> SECRET_IDENTITY = Set.of(
            CLEAR_SIGHT, THE_CYCLE_CONTINUES, EYES_CLOSED, BLINDED, SOMETHINGS_MISSING,
            VESSEL_OF_THE_KING, UNWORTHY_HEIR, UNSEALED);

    /**
     * Documented exception to the secret-identity rule: these secret TAB ROOTS may be visible (still silent).
     * A tab only appears to players who earned something in it, so a visible root reveals nothing to anyone else,
     * and a hidden root would render as a dangling connector line for everyone who hasn't earned it.
     */
    public static final Set<ResourceLocation> SECRET_VISIBLE_ROOTS = Set.of(CLEAR_SIGHT);

    /**
     * Not secret, but never announced to chat: they reveal PvP information. overflowing reveals Malice capacity;
     * chosen_by_fate reveals a Legendary innate technique; a_green_light and its_been_a_while reveal that a player
     * can heal (Reverse Malice Technique), and always_on that it can keep healing in the background; the progression ones reveal a player's Grade, or how far along their technique is.
     */
    public static final Set<ResourceLocation> SILENT = Set.of(OVERFLOWING, CHOSEN_BY_FATE, A_GREEN_LIGHT, ITS_BEEN_A_WHILE, ALWAYS_ON,
            MOVING_UP, PROVEN, RECOGNIZED, OUTSIDE_THE_LADDER, FULL_KIT);

    /** Silent AND hidden until earned (datagen enforces both): nobody should even learn they exist from the tab. */
    public static final Set<ResourceLocation> SILENT_HIDDEN = Set.of(A_GREEN_LIGHT, ITS_BEEN_A_WHILE, ALWAYS_ON,
            MOVING_UP, PROVEN, RECOGNIZED, OUTSIDE_THE_LADDER, FULL_KIT);

    private KegareAdvancements() {}

    /**
     * Root of an innate technique's own advancement tab: {@code <namespace>:innate_technique/<path>}.
     * Generated for every non-test innate technique and awarded the first time the player rolls it.
     */
    public static ResourceLocation innateTechniqueRoot(ResourceLocation innateTechniqueId) {
        return ResourceLocation.fromNamespaceAndPath(innateTechniqueId.getNamespace(), "innate_technique/" + innateTechniqueId.getPath());
    }

    public static void award(ServerPlayer player, ResourceLocation id) {
        awardInternal(player, id, true);
    }

    /** Awards if the advancement exists; silently does nothing otherwise (e.g. test innate techniques have no tab). */
    public static void awardIfPresent(ServerPlayer player, ResourceLocation id) {
        awardInternal(player, id, false);
    }

    private static void awardInternal(ServerPlayer player, ResourceLocation id, boolean warnIfMissing) {
        AdvancementHolder holder = player.server.getAdvancements().get(id);
        if (holder == null) {
            if (warnIfMissing) Kegare.LOGGER.warn("Missing advancement {}", id);
            return;
        }
        AdvancementProgress progress = player.getAdvancements().getOrStartProgress(holder);
        if (progress.isDone()) return;
        for (String criterion : progress.getRemainingCriteria()) {
            player.getAdvancements().award(holder, criterion);
        }
    }
}

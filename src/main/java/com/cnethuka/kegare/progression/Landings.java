package com.cnethuka.kegare.progression;

import com.cnethuka.kegare.innatetechnique.Ability;
import com.cnethuka.kegare.innatetechnique.InnateTechnique;
import com.cnethuka.kegare.innatetechnique.InnateTechniqueService;
import com.cnethuka.kegare.loadout.SharedAbilities;
import com.cnethuka.kegare.registry.KegareAttachments;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/**
 * The shared hit path: "this ability landed on a real target". Every technique reaches it without Refinement or
 * Assessment code of its own.
 * <ul>
 *   <li><b>Damage is automatic:</b> any hit traced to a cast (running in a {@link CastScope}, or by an entity one
 *       spawned, {@link CastOrigin}) lands.</li>
 *   <li><b>So are effects and knockback</b> applied to someone else during a cast ({@link ProgressionEvents}).</li>
 *   <li><b>Anything else calls {@link #land}</b>: an ability that lands without hurting (a swap, a string), or
 *       {@link #during} for a delayed effect.</li>
 *   <li><b>An ability with no target</b> overrides {@link Ability#hasNoTarget()}: it counts only used in a real fight
 *       (see {@link #usedWithoutTarget}), so casting it into the air earns nothing.</li>
 * </ul>
 * Only the caster's CURRENT technique counts, so shared abilities like RMT and a projectile from before a reroll don't.
 * What acts on its own ({@link OwnedEntity#actsOnItsOwn}) only earns while its owner is fighting too ({@link #isActive}).
 */
public final class Landings {
    /** Per player, runtime only. */
    public static final class State {
        private final Map<String, Long> lastLanding = new HashMap<>();
        @Nullable
        private LivingEntity engaged;
        private long engagedTick;
        private long criticalTick = -1;
        private long ownLandingTick = -1;
    }

    private Landings() {}

    private static State state(ServerPlayer player) {
        return player.getData(KegareAttachments.LANDING_STATE);
    }

    private static long now(ServerPlayer player) {
        return player.server.overworld().getGameTime();
    }

    /** {@code ability} of theirs landed on {@code target}. Ignored unless it's a real target and an ability of their current technique. */
    public static void land(ServerPlayer caster, LivingEntity target, Ability ability) {
        if (!counts(caster, target, ability)) return;
        landedOwnHit(caster);
        record(caster, target, ability, true);
    }

    /**
     * Something of theirs landed this on its own (a puppet's punch). It's on the fight's record like any landing, but it only
     * earns Refinement while they're fighting too. An idle owner earns nothing from it.
     */
    public static void landOnItsOwn(ServerPlayer owner, LivingEntity target, Ability ability) {
        if (counts(owner, target, ability)) record(owner, target, ability, isActive(owner));
    }

    /** They landed a hit or an ability of their own on something real. */
    static void landedOwnHit(ServerPlayer player) {
        state(player).ownLandingTick = now(player);
    }

    /** They've landed a hit or an ability of their own on something real within {@code ownerActiveWindow}. */
    public static boolean isActive(ServerPlayer player) {
        long last = state(player).ownLandingTick;
        return last >= 0 && now(player) - last <= ProgressionConfig.ticks(ProgressionConfig.OWNER_ACTIVE_WINDOW);
    }

    private static boolean counts(ServerPlayer caster, LivingEntity target, Ability ability) {
        return Attribution.isRealTarget(caster, target) && isTechniqueAbility(caster, ability);
    }

    private static void record(ServerPlayer caster, LivingEntity target, Ability ability, boolean earns) {
        State state = state(caster);
        long now = now(caster);
        String key = ability.id() + "/" + target.getUUID();
        Long last = state.lastLanding.get(key);
        // One channel tick or one volley on one target isn't twenty landings.
        if (last != null && now - last < ProgressionConfig.ticks(ProgressionConfig.LANDING_REPEAT)) return;
        if (state.lastLanding.size() > 256) state.lastLanding.clear();
        state.lastLanding.put(key, now);
        engaged(caster, target);
        if (target instanceof ServerPlayer player) {
            FightTracker.landed(caster, player, ability.id().toString(), ability.isSpecial());
            InnateTechniqueService.current(caster).ifPresent(technique -> InsightHooks.hitByTechnique(player, technique));
        }
        if (earns) RefinementService.onLanded(caster, target, ability);
    }

    /** Runs a delayed effect as part of the cast it came from, so what it hits lands for that ability. */
    public static void during(ServerPlayer caster, Ability ability, Runnable effect) {
        CastScope.run(caster, ability, effect);
    }

    /**
     * An ability with no target went off. In a real fight it counts as landing on whoever that fight is with; with
     * nobody around it earns nothing. It wasn't landed on anyone, so it doesn't make them {@linkplain #isActive active}.
     */
    public static void usedWithoutTarget(ServerPlayer caster, Ability ability) {
        State state = state(caster);
        if (state.engaged == null || now(caster) - state.engagedTick > ProgressionConfig.ticks(ProgressionConfig.NO_TARGET_WINDOW)) return;
        if (counts(caster, state.engaged, ability)) record(caster, state.engaged, ability, true);
    }

    /** {@code player} and {@code other} just traded a hit. */
    public static void engaged(ServerPlayer player, LivingEntity other) {
        State state = state(player);
        state.engaged = other;
        state.engagedTick = now(player);
    }

    /** Malice paid for a cast. Counts toward Efficiency at its base cost: what the surroundings changed about it is undone. */
    public static void spent(ServerPlayer player, Ability ability, double paid) {
        if (paid <= 0 || !isTechniqueAbility(player, ability)) return;
        InnateTechnique technique = InnateTechniqueService.current(player).orElse(null);
        if (technique == null) return;
        FightTracker.spend(player, baseCost(paid, technique.environment(player).multiplier()));
    }

    /** What a cast costs with no surroundings or discounts: what Efficiency rates. Any future discount has to be divided out here too. */
    public static double baseCost(double paid, double environmentMultiplier) {
        return environmentMultiplier > 0 ? paid / environmentMultiplier : paid;
    }

    private static boolean isTechniqueAbility(ServerPlayer player, Ability ability) {
        ResourceLocation id = ability.id();
        if (SharedAbilities.find(id).isPresent()) return false;
        Optional<InnateTechnique> technique = InnateTechniqueService.current(player);
        return technique.isPresent() && technique.get().findAbility(id).isPresent();
    }

    static void markCritical(ServerPlayer player) {
        state(player).criticalTick = now(player);
    }

    static boolean wasCritical(ServerPlayer player) {
        return state(player).criticalTick == now(player);
    }
}

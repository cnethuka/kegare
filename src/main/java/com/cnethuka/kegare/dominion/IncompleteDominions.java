package com.cnethuka.kegare.dominion;

import com.cnethuka.kegare.api.ability.Ability;
import com.cnethuka.kegare.api.ability.AbilityCategory;
import com.cnethuka.kegare.api.dominion.DominionContext;
import com.cnethuka.kegare.api.dominion.DominionLook;
import com.cnethuka.kegare.fists.SecondImpact;
import com.cnethuka.kegare.innatetechnique.CastManager;
import com.cnethuka.kegare.innatetechnique.CooldownRates;
import com.cnethuka.kegare.innatetechnique.InnateTechniqueService;
import com.cnethuka.kegare.loadout.LoadoutService;
import com.cnethuka.kegare.loadout.SharedAbilities;
import com.cnethuka.kegare.loadout.SharedAbilityService;
import com.cnethuka.kegare.malice.MaliceService;
import com.cnethuka.kegare.melee.MeleeHits;
import com.cnethuka.kegare.progression.Attribution;
import com.cnethuka.kegare.progression.GradeService;
import com.cnethuka.kegare.progression.InsightCategory;
import com.cnethuka.kegare.progression.RefinementService;
import com.cnethuka.kegare.progression.UnlockRequirements;
import com.cnethuka.kegare.progression.Unlocks;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * The Incomplete Dominion's rules (DESIGN §3.35): its spec, the one wasteland every Incomplete Dominion looks like, how it's learned (the
 * unlock framework: a Grade and a Peak Refinement, the Refinement lowered by Dominion Insight, kept once learned and through rerolls), and
 * what it does for its caster while it stands: their innate technique cheaper, its cooldowns faster and its hits harder. Nobody else
 * inside gets anything.
 */
public final class IncompleteDominions {
    public static final IncompleteDominion ABILITY = SharedAbilities.register(new IncompleteDominion());
    /**
     * The inner space with nothing of a technique in it: grey cracked ground, an ash sky, ruins and drifting dust, and a dark cracked
     * shell from outside. No Malice color anywhere, so every Incomplete Dominion looks the same whoever opens it.
     */
    public static final DominionLook WASTELAND = new DominionLook(0x8E8A85, 0x837E79, 30f, 0x3C3936, Optional.of(DominionIds.WASTELAND_GROUND),
            true, 0xA7A29B, true);
    /** Its cooldown rate's key ({@link CooldownRates}). */
    private static final String RATE = "incomplete_dominion";

    private static final DominionSpec.Hooks HOOKS = new DominionSpec.Hooks() {
        @Override
        public void onOpen(DominionContext ctx) {
            ServerPlayer caster = ctx.caster();
            if (caster != null) {
                CooldownRates.set(caster, new CooldownRates.Rate(RATE, DominionConfig.INCOMPLETE_COOLDOWN_RATE.get(),
                        key -> boosted(caster) && techniques(caster, key), Long.MAX_VALUE));
            }
        }

        @Override
        public void onClose(DominionContext ctx, boolean broken) {
            ServerPlayer caster = ctx.caster();
            if (caster != null) CooldownRates.remove(caster, RATE);
        }
    };

    private IncompleteDominions() {}

    public static void init() {
        Unlocks.register(DominionIds.INCOMPLETE, UnlockRequirements.builder().condition(IncompleteDominions::meetsUnlock).build());
        Unlocks.addListener((player, id) -> {
            if (DominionIds.INCOMPLETE.equals(id)) learn(player);
        });
        // Earned but somehow not had (an admin locked the ability alone): theirs again on login.
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer p && Unlocks.has(p, DominionIds.INCOMPLETE)) learn(p);
        });
        DominionMeter.addShownWhen(p -> SharedAbilityService.isUnlocked(p, DominionIds.INCOMPLETE));
        CastManager.addAbilityCostModifier((player, ability, amount) -> ability.category() == AbilityCategory.INNATE && boosted(player)
                ? amount * (1.0 - DominionConfig.INCOMPLETE_TECHNIQUE_COST.get()) : amount);
        NeoForge.EVENT_BUS.addListener(IncompleteDominions::onIncoming);
    }

    public static DominionSpec spec(ServerPlayer caster) {
        return new DominionSpec(DominionIds.INCOMPLETE, false, DominionConfig.INCOMPLETE_RADIUS.get(), DominionConfig.ticks(DominionConfig.INCOMPLETE_DURATION),
                DominionConfig.INCOMPLETE_BARRIER.get(), DominionConfig.ticks(DominionConfig.INCOMPLETE_BURNOUT), DominionConfig.INCOMPLETE_METER_SHARE.get(),
                WASTELAND, false, HOOKS);
    }

    // ---------------------------------------------------------------------------------------------- learning it

    /** Whether they meet what it takes now: Malice, the Grade, and the Peak Refinement as their Dominion Insight lowers it. */
    static boolean meetsUnlock(Player player) {
        if (MaliceService.isSevered(player) || !MaliceService.get(player).isBorn()) return false;
        return GradeService.grade(player).atLeast(DominionConfig.INCOMPLETE_UNLOCK_GRADE.get()) && RefinementService.peak(player) >= refinementNeeded(player);
    }

    /** The Peak Refinement they need now, with what their Dominion Insight takes off. */
    public static int refinementNeeded(Player player) {
        return Unlocks.refinementNeeded(player, DominionConfig.INCOMPLETE_UNLOCK_REFINEMENT.get(), InsightCategory.DOMINION);
    }

    /** It's theirs: the shared ability, and a place on the bar if there's an empty one. Returns whether it's new. */
    static boolean learn(ServerPlayer player) {
        if (!SharedAbilityService.unlock(player, ABILITY)) return false;
        LoadoutService.offer(player, ABILITY);
        return true;
    }

    /** Admin: gives it to them whether or not they meet it (never to a Severed player). Returns whether it's new. */
    public static boolean teach(ServerPlayer player) {
        if (MaliceService.isSevered(player)) return false;
        boolean entry = Unlocks.grant(player, DominionIds.INCOMPLETE); // its listener teaches it
        return learn(player) || entry;
    }

    /** Admin: takes it away (off the bar, ended if it's open). They learn it again when they next meet it. Returns whether they had it. */
    public static boolean forget(ServerPlayer player) {
        boolean entry = Unlocks.revoke(player, DominionIds.INCOMPLETE);
        Dominions.ownedBy(player).filter(d -> !d.complete()).ifPresent(d -> Dominions.end(d, Dominions.EndReason.COMMAND));
        return SharedAbilityService.lock(player, ABILITY) || entry;
    }

    // ---------------------------------------------------------------------------------------------- the boosts

    /** Their own Incomplete Dominion stands round them now (closed; not rising, not contested). */
    public static boolean boosted(ServerPlayer player) {
        return Dominions.ownedBy(player).filter(d -> !d.complete() && d.isClosed()).isPresent();
    }

    private static boolean techniques(ServerPlayer player, ResourceLocation cooldownKey) {
        return InnateTechniqueService.current(player).map(t -> t.findAbility(cooldownKey).isPresent()).orElse(false);
    }

    /** Their innate technique's hits do more, before the target's defences. */
    private static void onIncoming(LivingIncomingDamageEvent event) {
        if (!(event.getEntity().level() instanceof ServerLevel level)) return;
        Attribution.Hit hit = Attribution.of(event.getSource(), level.getServer());
        if (hit == null || hit.attacker() == event.getEntity() || !boosted(hit.attacker())) return;
        if (MeleeHits.of(event.getSource(), level) != MeleeHits.Kind.TECHNIQUE || SecondImpact.isSecondImpact(event.getSource())) return;
        Ability ability = hit.ability(); // known for most hits; a technique's own damage type with no cast behind it counts too
        if (ability != null && ability.category() != AbilityCategory.INNATE) return;
        event.setAmount(event.getAmount() * (float) (1.0 + DominionConfig.INCOMPLETE_OUTPUT.get()));
    }
}

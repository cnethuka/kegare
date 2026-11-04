package com.cnethuka.kegare.data;

import com.electronwill.nightconfig.core.UnmodifiableConfig;
import com.cnethuka.kegare.Kegare;
import com.cnethuka.kegare.malice.MaliceTier;
import com.cnethuka.kegare.config.KegareClientConfig;
import com.cnethuka.kegare.config.KegareServerConfig;
import com.cnethuka.kegare.registry.KegareItems;
import com.cnethuka.kegare.registry.KegareRegistries;
import com.cnethuka.kegare.innatetechnique.Ability;
import com.cnethuka.kegare.innatetechnique.CastType;
import com.cnethuka.kegare.innatetechnique.InnateTechnique;
import com.cnethuka.kegare.innatetechnique.InnateTechniqueRarity;
import com.cnethuka.kegare.trait.Trait;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.minecraft.data.PackOutput;
import net.neoforged.neoforge.common.data.LanguageProvider;

/**
 * Generates {@code assets/kegare/lang/en_us.json}. This is the ONLY place player-facing names live.
 * Rename a display term by changing the constant below and rerunning {@code ./gradlew runData}.
 * Fails datagen if a registered innate technique, ability or trait has no name or one-line description ({@code .desc}).
 */
public final class KegareLanguageProvider extends LanguageProvider {
    // ---- Display terms. Code IDs match them: malice / innate_technique / dominion / severed. ----
    // "Kegare" is the mod name and is reserved for the future hostile spirit mobs; never use it for the energy.
    private static final String MALICE = "Malice";
    private static final String INNATE_TECHNIQUE = "Innate Technique";
    private static final String DOMINION = "Dominion";
    private static final String SEVERED = "Severed";
    private static final String REROLL_ITEM = "Purification Talisman";
    private static final String LENSING_EYES = "Lensing Eyes";

    private final Set<String> written = new HashSet<>();

    public KegareLanguageProvider(PackOutput output) {
        super(output, Kegare.MODID, "en_us");
    }

    @Override
    public void add(String key, String value) {
        written.add(key);
        super.add(key, value);
    }

    @Override
    protected void addTranslations() {
        // Terms, for UI that wants the bare word.
        add("kegare.term.malice", MALICE);
        add("kegare.term.innate_technique", INNATE_TECHNIQUE);
        add("kegare.term.dominion", DOMINION);
        add("kegare.term.severed", SEVERED);

        // Item
        add(KegareItems.PURIFICATION_TALISMAN.get(), REROLL_ITEM);
        add("item.kegare.purification_talisman.desc", "Rerolls your " + INNATE_TECHNIQUE + ". " + MALICE + " and traits are untouched.");

        // Keybinds
        add("key.categories.kegare", "Kegare");
        add("key.kegare.cast", "Cast Ability");
        add("key.kegare.next_ability", "Next Ability");
        add("key.kegare.previous_ability", "Previous Ability");
        add("key.kegare.ultimate", "Ultimate");
        add("key.kegare.sorcerer", "Sorcerer Screen");

        // Malice tiers
        add(MaliceTier.ZERO.translationKey(), "Zero");
        add(MaliceTier.LOW.translationKey(), "Low");
        add(MaliceTier.AVERAGE.translationKey(), "Average");
        add(MaliceTier.HIGH.translationKey(), "High");
        add(MaliceTier.VAST.translationKey(), "Vast");

        // Rarities and cast types
        add(InnateTechniqueRarity.COMMON.translationKey(), "Common");
        add(InnateTechniqueRarity.RARE.translationKey(), "Rare");
        add(InnateTechniqueRarity.EPIC.translationKey(), "Epic");
        add(InnateTechniqueRarity.LEGENDARY.translationKey(), "Legendary");
        add(CastType.INSTANT.translationKey(), "Instant");
        add(CastType.CHARGED.translationKey(), "Charged");
        add(CastType.CHANNELED.translationKey(), "Channeled");
        add(CastType.TOGGLE.translationKey(), "Toggle");

        // Rime
        add("innate_technique.kegare.rime", "Rime");
        add("innate_technique.kegare.rime.desc", MALICE + " made into ice: shards, a lance, a freezing gale and a shell of frost. "
                + "Five Freeze stacks freeze a target solid. Cheaper in the cold, dearer in the heat.");
        add("ability.kegare.frost_shard", "Frost Shard");
        add("ability.kegare.frost_shard.desc", "A fast ice shard: light damage, a short Slowness and 1 Freeze stack.");
        add("ability.kegare.glacial_lance", "Glacial Lance");
        add("ability.kegare.glacial_lance.desc", "Hold to charge (up to 2s), release to hurl a heavy lance. More charge, more damage; "
                + "2 Freeze stacks. A quick tap fizzles.");
        add("ability.kegare.frost_field", "Frost Field");
        add("ability.kegare.frost_field.desc", "While held, a freezing cone in front of you slows and adds 1 Freeze stack per second.");
        add("ability.kegare.rime_shell", "Rime Shell");
        add("ability.kegare.rime_shell.desc", "Toggle a shell of ice: take less damage, and anyone who hits you up close is slowed.");
        add("entity.kegare.frost_shard", "Frost Shard");
        add("entity.kegare.glacial_lance", "Glacial Lance");
        add("item.kegare.frost_shard", "Frost Shard");
        add("item.kegare.glacial_lance", "Glacial Lance");
        add("death.attack.kegare.rime", "%1$s was frozen solid by %2$s");
        add("death.attack.kegare.rime.item", "%1$s was frozen solid by %2$s using %3$s");
        add("death.attack.kegare.rime.player", "%1$s froze while fighting %2$s");
        add("environment.kegare.rime.precipitation", "Rain or snow");
        add("environment.kegare.rime.water", "Near water");
        add("environment.kegare.rime.cold_biome", "Cold biome");
        add("environment.kegare.rime.hot_biome", "Hot biome");
        add("environment.kegare.rime.nether", "The Nether");

        // TEST CONTENT names
        add("innate_technique.kegare.test_kinetic", "[Test] Kinetic");
        add("innate_technique.kegare.test_flux", "[Test] Flux");
        add("innate_technique.kegare.test_apex", "[Test] Apex");
        add("innate_technique.kegare.test_prism", "[Test] Prism");
        add("innate_technique.kegare.test_kinetic.desc", "Placeholder: raw motion. A dash and a charged knockback burst.");
        add("innate_technique.kegare.test_flux.desc", "Placeholder: lift and flow. Hover in place or run with a speed aura.");
        add("innate_technique.kegare.test_prism.desc", "Placeholder: an Epic test card. Burst, hover and a shockwave ultimate.");
        add("innate_technique.kegare.test_apex.desc", "Placeholder: every cast type in one, plus a shockwave ultimate.");
        add("ability.kegare.test_dash", "[Test] Dash");
        add("ability.kegare.test_burst", "[Test] Burst");
        add("ability.kegare.test_hover", "[Test] Hover");
        add("ability.kegare.test_aura", "[Test] Aura");
        add("ability.kegare.test_shockwave", "[Test] Shockwave");
        add("ability.kegare.test_dash.desc", "Launch forward in the direction you face.");
        add("ability.kegare.test_burst.desc", "Hold to charge, release to knock nearby creatures away.");
        add("ability.kegare.test_hover.desc", "Hold to levitate. Drains " + MALICE + " while held.");
        add("ability.kegare.test_aura.desc", "Toggle a speed aura that drains " + MALICE + " every second.");
        add("ability.kegare.test_shockwave.desc", "A huge knockback wave around you.");

        // Traits
        add("trait.kegare.lensing_eyes", LENSING_EYES);
        add("trait.kegare.lensing_eyes.desc", "Eyes that see through everything. Only one bearer at a time. (No effects yet.)");
        add("message.kegare.trait.lost", "You have lost %s.");
        add("message.kegare.trait.inherited", "You have inherited %s.");
        add("message.kegare.trait.lensing_eyes.succession", "The eyes have closed... and opened elsewhere.");

        // HUD
        add("hud.kegare.no_innate_technique", "No " + INNATE_TECHNIQUE);
        add("hud.kegare.malice", MALICE + " %s/%s");
        add("hud.kegare.selected", "» %s (%s)");
        add("hud.kegare.cooldown", "%ss");
        add("hud.kegare.cost_multiplier", "×%s");
        add("hud.kegare.freeze", "Freeze");
        add("hud.kegare.frozen", "Frozen!");
        add("gui.kegare.cost.current", "Cost here: %s " + MALICE);
        add("gui.kegare.cost.upkeep", "Upkeep here: %s " + MALICE + "/s");
        add("gui.kegare.cost.no_modifier", "No surroundings modifier");
        add("gui.kegare.cost.factor", "×%s %s");
        add("gui.kegare.cost.total", "Total ×%s");
        add("gui.kegare.cost.short", "Cost %s");
        add("gui.kegare.cost.short_upkeep", "Cost %s + %s/s");
        add("gui.kegare.sorcerer.ability.ultimate_locked", "Ultimate: Locked");
        add("gui.kegare.sorcerer.ability.ultimate_locked.desc", "Its " + DOMINION + " comes later.");

        // Cast feedback (action bar)
        add("message.kegare.cast.severed", "You are " + SEVERED + ". No " + MALICE + " flows through you.");
        add("message.kegare.cast.no_innate_technique", "You have no " + INNATE_TECHNIQUE + ".");
        add("message.kegare.cast.no_ultimate", "Your " + INNATE_TECHNIQUE + " has no ultimate.");
        add("message.kegare.cast.ultimate_locked", "Locked. Your " + DOMINION + " comes later.");
        add("message.kegare.cast.busy", "Already casting.");
        add("message.kegare.cast.stunned", "Stunned! (%ss)");
        add("message.kegare.cast.on_cooldown", "%s is on cooldown (%ss)");
        add("message.kegare.cast.not_enough_malice", "Not enough " + MALICE + " (need %s, have %s)");
        add("message.kegare.cast.blocked", "%s can't be cast right now.");
        add("message.kegare.cast.charge_too_short", "%s fizzled. Hold longer to charge.");
        add("message.kegare.cast.interrupted", "%s was interrupted!");
        add("message.kegare.cast.out_of_malice", "%s faded: out of " + MALICE + ".");
        add("message.kegare.toggle.on", "%s: on");
        add("message.kegare.toggle.off", "%s: off");

        // Birth and rolls (chat)
        add("message.kegare.birth.malice", "You awaken with %s " + MALICE + " (%s).");
        add("message.kegare.birth.severed", "You awaken " + SEVERED + ". Not a drop of " + MALICE + " flows through you.");
        add("message.kegare.birth.trait", "You were born with a rare trait: %s");
        add("message.kegare.birth.innate_technique", "Your " + INNATE_TECHNIQUE + ": %s (%s)");
        add("message.kegare.reroll.success", "Purified. Your new " + INNATE_TECHNIQUE + ": %s (%s)");
        add("message.kegare.reroll.severed", "There is nothing to purify. A " + SEVERED + " body holds no " + INNATE_TECHNIQUE + ".");
        add("message.kegare.reroll.no_candidates", "No other " + INNATE_TECHNIQUE + " is available.");
        add("message.kegare.reroll.in_combat", "You can't purify mid-fight.");
        add("message.kegare.reroll.no_talisman", "The talisman is no longer in your hand.");
        add("message.kegare.roll.hard_pity", "Fate relented on roll %s.");
        add("message.kegare.roll.replaced", "Your " + INNATE_TECHNIQUE + " was removed by an update and has been re-rolled.");

        // Advancements
        advancement("awakening", "Awakening", "Be born into a world of " + MALICE + ".");
        advancement("reawakening", "Reawakening", "Use a " + REROLL_ITEM + " to reroll your " + INNATE_TECHNIQUE + ".");
        advancement("chosen_by_fate", "Chosen by Fate", "Roll a Legendary " + INNATE_TECHNIQUE + ".");
        advancement("bitter_luck", "Bitter Luck", "Hit hard pity. That Legendary was guaranteed, not lucky.");
        advancement("severed", SEVERED, "Be born with zero " + MALICE + ".");
        advancement("overflowing", "Overflowing", "Be born in the Vast " + MALICE + " tier.");
        advancement("running_on_empty", "Running on Empty", "Run out of " + MALICE + " in the middle of a fight.");
        add("advancements.kegare.innate_technique_root.description", "Roll this " + INNATE_TECHNIQUE + " for the first time.");
        advancement("clear_sight", "Clear Sight", "Be born with " + LENSING_EYES + ".");
        // Rime tab (kegare:innate_technique/rime/...)
        advancement("rime.shatter", "Shatter", "Freeze a player solid");
        advancement("rime.cold_open", "Cold Open", "Hit a player with Frost Shard");
        advancement("rime.brain_freeze", "Brain Freeze", "Freeze a player while they're charging or channeling");
        advancement("rime.full_charge", "Full Charge", "Hit a player with a fully charged Glacial Lance");
        advancement("rime.thin_ice", "Thin Ice", "Block 100 total damage with Rime Shell");
        advancement("rime.cold_blooded", "Cold Blooded", "Kill a player who is frozen solid");
        advancement("rime.deep_freeze", "Deep Freeze", "Freeze the same player 3 times within 60 seconds");
        advancement("rime.long_way_from_home", "Long Way From Home", "Freeze a player in the Nether");
        advancement("rime.home_field", "Home Field", "Cast a Rime ability during snowfall in a cold biome");
        advancement("the_cycle_continues", "The Cycle Continues", "Inherit " + LENSING_EYES + " from a fallen bearer.");
        advancement("eyes_closed", "Eyes Closed", "Lose your " + LENSING_EYES + ".");
        advancement("blinded", "Blinded", "Kill the bearer of " + LENSING_EYES + ".");

        // ---- GUI: roll reveal ----
        add("gui.kegare.reveal.title.birth", "Awakening");
        add("gui.kegare.reveal.title.talisman", "Reawakening");
        add("gui.kegare.reveal.title.admin", "Reroll");
        add("gui.kegare.reveal.title.replacement", "Re-rolled");
        add("gui.kegare.reveal.subtitle.talisman", "The talisman burns away. A new " + INNATE_TECHNIQUE + " answers.");
        add("gui.kegare.reveal.subtitle.admin", "An operator rerolled your " + INNATE_TECHNIQUE + ".");
        add("gui.kegare.reveal.subtitle.replacement", "Your " + INNATE_TECHNIQUE + " was removed by an update.");
        add("gui.kegare.reveal.malice", "%s " + MALICE + " · %s");
        add("gui.kegare.reveal.hard_pity", "%s · Fate relented on roll %s");
        add("gui.kegare.reveal.trait", "Rare trait: %s");
        add("gui.kegare.reveal.trait_private", "Only you know this.");
        add("gui.kegare.reveal.no_innate_technique", "No " + INNATE_TECHNIQUE + " answered your call.");
        add("gui.kegare.reveal.severed.title", "Born without " + MALICE);
        add("gui.kegare.reveal.severed.subtitle", SEVERED);
        add("gui.kegare.reveal.severed.desc", "Not a drop of " + MALICE + " flows through you. No " + INNATE_TECHNIQUE + " will ever answer.");
        add("gui.kegare.reveal.skip", "Skip");
        add("gui.kegare.reveal.continue", "Continue");

        // ---- GUI: Sorcerer screen ----
        add("gui.kegare.sorcerer.title", "Sorcerer");
        add("gui.kegare.sorcerer.tab.malice", MALICE);
        add("gui.kegare.sorcerer.tab.innate_technique", INNATE_TECHNIQUE);
        add("gui.kegare.sorcerer.tab.severed", SEVERED);
        add("gui.kegare.sorcerer.tab.traits", "Traits");
        add("gui.kegare.sorcerer.tab.pity", "Pity");
        add("gui.kegare.sorcerer.malice.heading", MALICE);
        add("gui.kegare.sorcerer.malice.capacity", "Capacity: %s (%s)");
        add("gui.kegare.sorcerer.malice.reserve", "Reserve: %s / %s");
        add("gui.kegare.sorcerer.malice.regen", "Regeneration: %s per second (%s%% of capacity)");
        add("gui.kegare.sorcerer.malice.regen_paused", "Regeneration paused: in combat");
        add("gui.kegare.sorcerer.malice.severed", SEVERED + ": you have no " + MALICE + ".");
        add("gui.kegare.sorcerer.innate_technique.none", "No " + INNATE_TECHNIQUE + ".");
        add("gui.kegare.sorcerer.abilities", "Abilities");
        add("gui.kegare.sorcerer.ability.slot", "%s. %s (%s)");
        add("gui.kegare.sorcerer.ability.ultimate", "Ultimate: %s (%s)");
        add("gui.kegare.sorcerer.ability.stats", "Cost: %s " + MALICE + " · Cooldown: %ss");
        add("gui.kegare.sorcerer.ability.stats_upkeep", "Cost: %s " + MALICE + " · Upkeep: %s/s · Cooldown: %ss");
        add("gui.kegare.sorcerer.severed.heading", SEVERED);
        add("gui.kegare.sorcerer.severed.desc", "Born without " + MALICE + ". You hold no " + INNATE_TECHNIQUE + ", cannot cast and cannot reroll.");
        add("gui.kegare.sorcerer.traits.heading", "Traits");
        add("gui.kegare.sorcerer.traits.none", "No traits.");
        add("gui.kegare.sorcerer.traits.unique", "%s (unique)");
        add("gui.kegare.sorcerer.pity.heading", "Pity");
        add("gui.kegare.sorcerer.pity.count", "Rolls since Legendary: %s");
        add("gui.kegare.sorcerer.pity.soft_in", "Soft pity starts in %s rolls");
        add("gui.kegare.sorcerer.pity.soft_active", "Soft pity active: Legendary weight %s (base %s)");
        add("gui.kegare.sorcerer.pity.hard_in", "Legendary guaranteed in %s rolls");
        add("gui.kegare.sorcerer.pity.hard_next", "Legendary guaranteed on your next roll");
        add("gui.kegare.sorcerer.pity.hard_disabled", "Hard pity is disabled");
        add("gui.kegare.sorcerer.pity.total", "Total rolls: %s");

        // ---- GUI: Purification Talisman confirmation ----
        add("gui.kegare.talisman.title", REROLL_ITEM);
        add("gui.kegare.talisman.question", "Reroll your " + INNATE_TECHNIQUE + "?");
        add("gui.kegare.talisman.current", "Current: %s");
        add("gui.kegare.talisman.none", "none");
        add("gui.kegare.talisman.pity", "Pity: %s/%s");
        add("gui.kegare.talisman.note", "Consumes one talisman. " + MALICE + " and traits are untouched.");
        add("gui.kegare.talisman.confirm", "Reroll");
        add("gui.kegare.talisman.cancel", "Cancel");

        // ---- GUI: radial ability menu ----
        add("gui.kegare.radial.title", "Select Ability");
        add("gui.kegare.radial.hint", "Release over an ability to select it");

        // Commands
        add("commands.kegare.unknown_innate_technique", "Unknown " + INNATE_TECHNIQUE + ": %s");
        add("commands.kegare.unknown_trait", "Unknown trait: %s");
        add("commands.kegare.severed", "%s is " + SEVERED + " and can't hold an " + INNATE_TECHNIQUE + ".");
        add("commands.kegare.info.header", "--- %s ---");
        add("commands.kegare.info.malice", MALICE + ": %s / %s (%s)");
        add("commands.kegare.info.severed", SEVERED + " (no " + INNATE_TECHNIQUE + ", can't reroll)");
        add("commands.kegare.info.innate_technique", INNATE_TECHNIQUE + ": %s (%s), slot %s/%s: %s");
        add("commands.kegare.info.no_innate_technique", INNATE_TECHNIQUE + ": none");
        add("commands.kegare.info.cooldowns", "Cooldowns: %s");
        add("commands.kegare.info.cooldown_entry", "%s %ss");
        add("commands.kegare.info.active", "Active: %s");
        add("commands.kegare.info.traits", "Traits: %s");
        add("commands.kegare.info.pity", "Pity: %s rolls since Legendary (soft pity after %s, hard pity on roll %s). Total rolls: %s");
        add("commands.kegare.info.combat", "In combat: %s | Stunned: %s");
        add("commands.kegare.info.seconds", "%ss");
        add("commands.kegare.info.no", "no");
        add("commands.kegare.info.none", "none");
        add("commands.kegare.setmalice.success", "Set %s's " + MALICE + " to %s / %s");
        add("commands.kegare.setcapacity.success", "Set %s's " + MALICE + " capacity to %s (%s)");
        add("commands.kegare.setcapacity.hint_reroll", "They have no " + INNATE_TECHNIQUE + ". Use /kegare reroll or /kegare setinnatetechnique.");
        add("commands.kegare.setinnatetechnique.success", "Set %s's " + INNATE_TECHNIQUE + " to %s");
        add("commands.kegare.reroll.success", "Rerolled %s into %s (pity counter: %s)");
        add("commands.kegare.setpity.success", "Set %s's pity counter to %s");
        add("commands.kegare.trait.added", "Gave %s to %s");
        add("commands.kegare.trait.already_has", "%s already has %s");
        add("commands.kegare.trait.unique_taken", "%s is unique and already held by %s");
        add("commands.kegare.trait.not_present", "%s doesn't have %s");
        add("commands.kegare.trait.removed", "Removed %s from %s");
        add("commands.kegare.trait.requires_malice", "%s needs " + MALICE + ", and %s is " + SEVERED);
        add("commands.kegare.trait.not_unique", "%s isn't a unique trait");
        add("commands.kegare.trait.unclaimed", "Nobody holds %s");
        add("commands.kegare.trait.released", "Released %s from %s");
        add("commands.kegare.trait.released_offline", "Released %s from %s (offline: it is removed on their next login)");
        add("commands.kegare.trait.passing_on", "%s passes to an eligible player in %ss (or the next eligible login)");
        add("commands.kegare.trait.status.held", "%s is held by %s");
        add("commands.kegare.trait.status.passing", "%s is passing on (opens in %ss)");
        add("commands.kegare.trait.status.waiting", "%s is waiting for an eligible player to log in");
        add("commands.kegare.trait.status.unclaimed", "%s is unclaimed");
        add("commands.kegare.rebirth.success", "%s was reborn");
        add("commands.kegare.stun.success", "Stunned %s for %ss");

        // Config screen labels, generated from the config specs.
        add("kegare.configuration.title", "Kegare Configuration");
        add("kegare.configuration.malice", MALICE);
        add("kegare.configuration.hud", "HUD");
        add("kegare.configuration.test_abilities", "Test Abilities (test content)");
        add("kegare.configuration.vfxQuality", "VFX Quality");
        configLabels(KegareServerConfig.SPEC.getValues());
        configLabels(KegareClientConfig.SPEC.getValues());

        validateRegistries();
    }

    private void advancement(String id, String title, String description) {
        add("advancements.kegare." + id + ".title", title);
        add("advancements.kegare." + id + ".description", description);
    }

    /** NeoForge's config screen looks up {@code kegare.configuration.<leafName>} for values and sections. */
    private void configLabels(UnmodifiableConfig config) {
        for (UnmodifiableConfig.Entry entry : config.entrySet()) {
            String key = "kegare.configuration." + entry.getKey();
            if (!written.contains(key)) add(key, humanize(entry.getKey()));
            if (entry.getRawValue() instanceof UnmodifiableConfig section) configLabels(section);
        }
    }

    private static String humanize(String key) {
        String spaced = key.replace('_', ' ').replaceAll("([a-z0-9])([A-Z])", "$1 $2");
        StringBuilder out = new StringBuilder();
        for (String word : spaced.split(" ")) {
            if (word.isEmpty()) continue;
            if (!out.isEmpty()) out.append(' ');
            out.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1).toLowerCase(Locale.ROOT));
        }
        return out.toString();
    }

    /** Every registered innate technique, ability and trait must have a display name. */
    private void validateRegistries() {
        List<String> missing = new ArrayList<>();
        for (InnateTechnique innateTechnique : KegareRegistries.INNATE_TECHNIQUE) {
            if (!written.contains(innateTechnique.descriptionId())) missing.add(innateTechnique.descriptionId());
            if (!written.contains(innateTechnique.descriptionId() + ".desc")) missing.add(innateTechnique.descriptionId() + ".desc");
            List<Ability> abilities = new ArrayList<>(innateTechnique.abilities());
            innateTechnique.ultimate().ifPresent(abilities::add);
            for (Ability ability : abilities) {
                if (!written.contains(ability.descriptionId())) missing.add(ability.descriptionId());
                if (!written.contains(ability.descriptionId() + ".desc")) missing.add(ability.descriptionId() + ".desc");
            }
        }
        for (Trait trait : KegareRegistries.TRAIT) {
            if (!written.contains(trait.descriptionId())) missing.add(trait.descriptionId());
            if (!written.contains(trait.descriptionId() + ".desc")) missing.add(trait.descriptionId() + ".desc");
        }
        if (!missing.isEmpty()) throw new IllegalStateException("Missing lang entries: " + missing.stream().distinct().toList());
    }
}

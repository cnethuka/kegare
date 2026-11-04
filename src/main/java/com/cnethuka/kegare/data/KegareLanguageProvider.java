package com.cnethuka.kegare.data;

import com.electronwill.nightconfig.core.UnmodifiableConfig;
import com.cnethuka.kegare.Kegare;
import com.cnethuka.kegare.ce.CeTier;
import com.cnethuka.kegare.config.KegareClientConfig;
import com.cnethuka.kegare.config.KegareServerConfig;
import com.cnethuka.kegare.registry.KegareItems;
import com.cnethuka.kegare.registry.KegareRegistries;
import com.cnethuka.kegare.technique.Ability;
import com.cnethuka.kegare.technique.CastType;
import com.cnethuka.kegare.technique.Technique;
import com.cnethuka.kegare.technique.TechniqueRarity;
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
 * Fails datagen if a registered technique, ability or trait has no name.
 */
public final class KegareLanguageProvider extends LanguageProvider {
    // ---- Display terms (code IDs stay neutral: ce / technique / domain / trait) ----
    private static final String CE = "Kegare";
    private static final String TECHNIQUE = "Imprint";
    private static final String DOMAIN = "Dominion";
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
        add("kegare.term.ce", CE);
        add("kegare.term.technique", TECHNIQUE);
        add("kegare.term.domain", DOMAIN);
        add("kegare.term.severed", SEVERED);

        // Item
        add(KegareItems.PURIFICATION_TALISMAN.get(), REROLL_ITEM);
        add("item.kegare.purification_talisman.desc", "Rerolls your " + TECHNIQUE + ". " + CE + " and traits are untouched.");

        // Keybinds
        add("key.categories.kegare", "Kegare");
        add("key.kegare.cast", "Cast Ability");
        add("key.kegare.next_ability", "Next Ability");
        add("key.kegare.previous_ability", "Previous Ability");
        add("key.kegare.ultimate", "Ultimate");

        // ce tiers
        add(CeTier.ZERO.translationKey(), "Zero");
        add(CeTier.LOW.translationKey(), "Low");
        add(CeTier.AVERAGE.translationKey(), "Average");
        add(CeTier.HIGH.translationKey(), "High");
        add(CeTier.VAST.translationKey(), "Vast");

        // Rarities and cast types
        add(TechniqueRarity.COMMON.translationKey(), "Common");
        add(TechniqueRarity.RARE.translationKey(), "Rare");
        add(TechniqueRarity.EPIC.translationKey(), "Epic");
        add(TechniqueRarity.LEGENDARY.translationKey(), "Legendary");
        add(CastType.INSTANT.translationKey(), "Instant");
        add(CastType.CHARGED.translationKey(), "Charged");
        add(CastType.CHANNELED.translationKey(), "Channeled");
        add(CastType.TOGGLE.translationKey(), "Toggle");

        // TEST CONTENT names
        add("technique.kegare.test_kinetic", "[Test] Kinetic");
        add("technique.kegare.test_flux", "[Test] Flux");
        add("technique.kegare.test_apex", "[Test] Apex");
        add("ability.kegare.test_dash", "[Test] Dash");
        add("ability.kegare.test_burst", "[Test] Burst");
        add("ability.kegare.test_hover", "[Test] Hover");
        add("ability.kegare.test_aura", "[Test] Aura");
        add("ability.kegare.test_shockwave", "[Test] Shockwave");

        // Traits
        add("trait.kegare.lensing_eyes", LENSING_EYES);
        add("message.kegare.trait.lost", "You have lost %s.");
        add("message.kegare.trait.inherited", "You have inherited %s.");
        add("message.kegare.trait.lensing_eyes.succession", "The eyes have closed... and opened elsewhere.");

        // HUD
        add("hud.kegare.no_technique", "No " + TECHNIQUE);
        add("hud.kegare.ce", CE + " %s/%s");
        add("hud.kegare.selected", "» %s (%s)");
        add("hud.kegare.cooldown", "%ss");

        // Cast feedback (action bar)
        add("message.kegare.cast.severed", "You are " + SEVERED + ". No " + CE + " flows through you.");
        add("message.kegare.cast.no_technique", "You have no " + TECHNIQUE + ".");
        add("message.kegare.cast.no_ultimate", "Your " + TECHNIQUE + " has no ultimate.");
        add("message.kegare.cast.busy", "Already casting.");
        add("message.kegare.cast.stunned", "Stunned! (%ss)");
        add("message.kegare.cast.on_cooldown", "%s is on cooldown (%ss)");
        add("message.kegare.cast.not_enough_ce", "Not enough " + CE + " (need %s, have %s)");
        add("message.kegare.cast.blocked", "%s can't be cast right now.");
        add("message.kegare.cast.charge_too_short", "%s fizzled. Hold longer to charge.");
        add("message.kegare.cast.interrupted", "%s was interrupted!");
        add("message.kegare.cast.out_of_ce", "%s faded: out of " + CE + ".");
        add("message.kegare.toggle.on", "%s: on");
        add("message.kegare.toggle.off", "%s: off");

        // Birth and rolls (chat)
        add("message.kegare.birth.ce", "You awaken with %s " + CE + " (%s).");
        add("message.kegare.birth.severed", "You awaken " + SEVERED + ". Not a drop of " + CE + " flows through you.");
        add("message.kegare.birth.trait", "You were born with a rare trait: %s");
        add("message.kegare.birth.technique", "Your " + TECHNIQUE + ": %s (%s)");
        add("message.kegare.reroll.success", "Purified. Your new " + TECHNIQUE + ": %s (%s)");
        add("message.kegare.reroll.severed", "There is nothing to purify. A " + SEVERED + " body holds no " + TECHNIQUE + ".");
        add("message.kegare.reroll.no_candidates", "No other " + TECHNIQUE + " is available.");
        add("message.kegare.reroll.in_combat", "You can't purify mid-fight.");
        add("message.kegare.roll.hard_pity", "Fate relented on roll %s.");
        add("message.kegare.roll.replaced", "Your " + TECHNIQUE + " was removed by an update and has been re-rolled.");

        // Advancements
        advancement("awakening", "Awakening", "Be born into a world of " + CE + ".");
        advancement("reawakening", "Reawakening", "Use a " + REROLL_ITEM + " to reroll your " + TECHNIQUE + ".");
        advancement("chosen_by_fate", "Chosen by Fate", "Roll a Legendary " + TECHNIQUE + ".");
        advancement("bitter_luck", "Bitter Luck", "Hit hard pity. That Legendary was guaranteed, not lucky.");
        advancement("severed", SEVERED, "Be born with zero " + CE + ".");
        advancement("clear_sight", "Clear Sight", "Be born with " + LENSING_EYES + ".");
        advancement("the_cycle_continues", "The Cycle Continues", "Inherit " + LENSING_EYES + " from a fallen bearer.");
        advancement("eyes_closed", "Eyes Closed", "Lose your " + LENSING_EYES + ".");
        advancement("blinded", "Blinded", "Kill the bearer of " + LENSING_EYES + ".");

        // Commands
        add("commands.kegare.unknown_technique", "Unknown " + TECHNIQUE + ": %s");
        add("commands.kegare.unknown_trait", "Unknown trait: %s");
        add("commands.kegare.severed", "%s is " + SEVERED + " and can't hold an " + TECHNIQUE + ".");
        add("commands.kegare.info.header", "--- %s ---");
        add("commands.kegare.info.ce", CE + ": %s / %s (%s)");
        add("commands.kegare.info.severed", SEVERED + " (no " + TECHNIQUE + ", can't reroll)");
        add("commands.kegare.info.technique", TECHNIQUE + ": %s (%s), slot %s/%s: %s");
        add("commands.kegare.info.no_technique", TECHNIQUE + ": none");
        add("commands.kegare.info.cooldowns", "Cooldowns: %s");
        add("commands.kegare.info.cooldown_entry", "%s %ss");
        add("commands.kegare.info.active", "Active: %s");
        add("commands.kegare.info.traits", "Traits: %s");
        add("commands.kegare.info.pity", "Pity: %s rolls since Legendary (soft pity after %s, hard pity on roll %s). Total rolls: %s");
        add("commands.kegare.info.combat", "In combat: %s | Stunned: %s");
        add("commands.kegare.info.seconds", "%ss");
        add("commands.kegare.info.no", "no");
        add("commands.kegare.info.none", "none");
        add("commands.kegare.setce.success", "Set %s's " + CE + " to %s / %s");
        add("commands.kegare.setcapacity.success", "Set %s's " + CE + " capacity to %s (%s)");
        add("commands.kegare.setcapacity.hint_reroll", "They have no " + TECHNIQUE + ". Use /kegare reroll or /kegare settechnique.");
        add("commands.kegare.settechnique.success", "Set %s's " + TECHNIQUE + " to %s");
        add("commands.kegare.reroll.success", "Rerolled %s into %s (pity counter: %s)");
        add("commands.kegare.setpity.success", "Set %s's pity counter to %s");
        add("commands.kegare.trait.added", "Gave %s to %s");
        add("commands.kegare.trait.already_has", "%s already has %s");
        add("commands.kegare.trait.unique_taken", "%s is unique and already held by %s");
        add("commands.kegare.trait.not_present", "%s doesn't have %s");
        add("commands.kegare.trait.removed", "Removed %s from %s");
        add("commands.kegare.trait.requires_ce", "%s needs " + CE + ", and %s is " + SEVERED);
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
        add("kegare.configuration.ce", CE);
        add("kegare.configuration.hud", "HUD");
        add("kegare.configuration.test_abilities", "Test Abilities (test content)");
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

    /** Every registered technique, ability and trait must have a display name. */
    private void validateRegistries() {
        List<String> missing = new ArrayList<>();
        for (Technique technique : KegareRegistries.TECHNIQUE) {
            if (!written.contains(technique.descriptionId())) missing.add(technique.descriptionId());
            List<Ability> abilities = new ArrayList<>(technique.abilities());
            technique.ultimate().ifPresent(abilities::add);
            for (Ability ability : abilities) {
                if (!written.contains(ability.descriptionId())) missing.add(ability.descriptionId());
            }
        }
        for (Trait trait : KegareRegistries.TRAIT) {
            if (!written.contains(trait.descriptionId())) missing.add(trait.descriptionId());
        }
        if (!missing.isEmpty()) throw new IllegalStateException("Missing lang entries: " + missing.stream().distinct().toList());
    }
}

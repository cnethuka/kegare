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
 * Fails datagen if a registered innate technique, ability or trait has no name or one-line description ({@code .desc}), or a
 * non-test innate technique has no epithet ({@code .epithet}).
 */
public final class KegareLanguageProvider extends LanguageProvider {
    // ---- Display terms. Code IDs match them: malice / innate_technique / dominion / severed. ----
    // "Kegare" is the mod name and is reserved for the future hostile spirit mobs; never use it for the energy.
    private static final String MALICE = "Malice";
    private static final String INNATE_TECHNIQUE = "Innate Technique";
    private static final String DOMINION = "Dominion";
    private static final String SEVERED = "Severed";
    private static final String REROLL_ITEM = "Purification Talisman";
    private static final String SIX_EYES = "Six Eyes";

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
        add("key.kegare.special", "Special (with a number key)");
        add("key.kegare.technique_mode", "Technique Mode / Blur");
        add("key.kegare.parry", "Parry");
        add("key.kegare.six_eyes_map", "Six Eyes: map");
        add("key.kegare.dash", "Dash");
        add("message.kegare.melee.no_malice", "Not enough Malice to %s.");
        add("hud.kegare.melee.strike", "Reinforced");
        add("hud.kegare.melee.dash", "Dash");
        add("hud.kegare.melee.recovery", "Recovering");
        add("hud.kegare.zone", "120%% · %ss");
        add("gui.kegare.settings.dash_double_tap", "Double-tap sprint to Dash");
        add("gui.kegare.settings.dash_double_tap.200", "Fast");
        add("gui.kegare.settings.dash_double_tap.300", "Normal");
        add("gui.kegare.settings.dash_double_tap.400", "Slow");
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
        add("innate_technique.kegare.rime.epithet", "Breath Turned to Glass");
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
        add("ability.kegare.splinter", "Splinter");
        add("ability.kegare.splinter.desc", "Three smaller shards in a fan. Each deals less damage and adds 1 Freeze stack; all three can hit one target.");
        add("ability.kegare.glacial_spire", "Glacial Spire");
        add("ability.kegare.glacial_spire.desc", "Hold to charge (up to 2s) while aiming at the ground in range. A glowing crack rings the spot, "
                + "then an ice spike erupts: damage, 2 Freeze stacks, and anyone on it is launched (higher with more charge).");
        add("ability.kegare.ice_path", "Ice Path");
        add("ability.kegare.ice_path.desc", "While held, slide fast the way you look over a trail of ice. Anyone else on the trail is "
                + "slowed until it melts. Drains " + MALICE + " while held.");
        add("ability.kegare.shatter_shell", "Shatter Shell");
        add("ability.kegare.shatter_shell.desc", "Burst outward: everyone nearby is knocked back and gets 2 Freeze stacks. "
                + "Uses up an active Rime Shell; costs more without one.");

        // Swap
        add("innate_technique.kegare.swap", "Swap");
        add("innate_technique.kegare.swap.epithet", "Nothing Stays Where It Was");
        add("innate_technique.kegare.swap.desc", "Trade with whoever you're looking at: places, faces, fortunes and falls. "
                + "Players always count; most of it works on mobs too.");
        add("ability.kegare.positions", "Positions");
        add("ability.kegare.positions.desc", "Swap places and facing with the player or mob you're looking at (up to 24 blocks, in line of sight).");
        add("ability.kegare.switchback", "Switchback");
        add("ability.kegare.switchback.desc", "Swap places like Positions (player or mob), then swap back on your own after 3 seconds. "
                + "Called off if either of you dies, logs out or leaves the dimension.");
        add("ability.kegare.awareness", "Awareness");
        add("ability.kegare.awareness.desc", "Swap looks with the player you're looking at for 5 seconds: everyone else sees you as them "
                + "and them as you (name, skin, armor, held items, tab list). Only looks change hands.");
        add("ability.kegare.mimic", "Mimic");
        add("ability.kegare.mimic.desc", "Look like the player you're looking at for 8 seconds. They stay as themselves.");
        add("ability.kegare.anchor", "Anchor");
        add("ability.kegare.anchor.desc", "First cast: throw an anchor that sticks where it lands (it lasts 20 seconds). "
                + "Second cast: swap yourself with it.");
        add("ability.kegare.send_off", "Send Off");
        add("ability.kegare.send_off.desc", "Needs your anchor out: swap the player or mob you're looking at with the anchor instead of yourself. "
                + "Uses the anchor up.");
        add("ability.kegare.exchange", "Exchange");
        add("ability.kegare.exchange.desc", "Give the player or mob you're looking at your worst negative effect and take their strongest "
                + "positive one, duration and level included. Effects that last forever never move.");
        add("ability.kegare.momentum_exchange", "Momentum Exchange");
        add("ability.kegare.momentum_exchange.desc", "Swap velocity and fall distance with the player or mob you're looking at. "
                + "Nobody moves; their momentum does.");
        add("message.kegare.swap.nothing_to_exchange", "Nothing to exchange.");
        add("ability.kegare.total_swap", "Total Swap");
        add("ability.kegare.total_swap.desc", "Everything at once with the player you're looking at (4+ blocks away): swap places and facing, "
                + "look like each other for 5 seconds, and trade health as a share of max health.");
        add("message.kegare.swap.too_close", "%s needs the target at least %s blocks away.");
        add("entity.kegare.swap_anchor", "Swap Anchor");
        add("item.kegare.swap_anchor", "Swap Anchor");
        add("message.kegare.swap.no_anchor", "You have no anchor out.");
        add("message.kegare.swap.no_target", "No player in sight.");
        add("message.kegare.swap.no_target_any", "Nothing in sight to swap with.");
        add("message.kegare.swap.refused", "Something stops the swap.");
        add("subtitles.kegare.swap.clap", "Hands clap");
        add("subtitles.kegare.swap.double_clap", "Hands clap twice");
        add("subtitles.kegare.swap.snap", "Fingers snap");
        add("subtitles.kegare.swap.cupped_clap", "Cupped hands clap");
        add("subtitles.kegare.swap.big_clap", "Thunderous clap");
        add("subtitles.kegare.swap.flip", "Card flips");
        add("subtitles.kegare.swap.flick", "Token flicked");
        add("subtitles.kegare.swap.token_land", "Token lands");
        add("subtitles.kegare.swap.shove", "Hands shove");
        add("subtitles.kegare.swap.trade", "Hands clap, chimes cross");
        add("subtitles.kegare.puppet_manipulation.curio_burst", "Puppet bursts apart");
        add("subtitles.kegare.puppet_manipulation.curio_thump", "Heavy thump");
        add("subtitles.kegare.impact.boom", "Deep boom");
        add("subtitles.kegare.black_spark.crack", "Black spark cracks");
        add("message.kegare.swap.switchback_refused", "Something stops the swap back.");
        // Puppet Manipulation
        add("innate_technique.kegare.puppet_manipulation", "Puppet Manipulation");
        add("innate_technique.kegare.puppet_manipulation.epithet", "Strings Without a Stage");
        add("innate_technique.kegare.puppet_manipulation.desc", "Charge Malice into little copies of yourself. "
                + "Command them, ride inside them, set them off, or let one stand in for you.");
        add("ability.kegare.puppet_creation", "Puppet Creation");
        add("ability.kegare.puppet_creation.desc", "Hold to charge, release to make a half-size puppet of yourself beside you. "
                + "A tap (40 Malice) makes a weak one: 2 health, a faint glow. Half a charge (100) a steady one: 4 health. A full "
                + "charge (180) a strong one: 8 health, a bright glow and an outline. Puppets last until killed; you can hold 15 at once.");
        add("ability.kegare.directive", "Directive");
        add("ability.kegare.directive.desc", "Give every puppet within 32 blocks the next order: Follow, Stay, Guard (attack whoever "
                + "damages you and whatever you damage, player or mob) or Go (walk to the block you're looking at, then stay). New puppets take the current order.");
        add("ability.kegare.focus", "Focus");
        add("ability.kegare.focus.desc", "Every puppet in range swarms the player or mob you're looking at, until it dies or gets away; "
                + "then they go back to their order.");
        add("message.kegare.directive.order", "Puppets: %s (%s heard you)");
        add("message.kegare.focus.no_target", "Nothing in sight to focus on.");
        add("message.kegare.focus.no_puppets", "No puppets of yours in range.");
        add("message.kegare.focus.swarm", "%s puppets swarm %s.");
        add("ability.kegare.curio_burst", "Curio Burst");
        add("ability.kegare.curio_burst.desc", "Detonate the puppet you're looking at. The more Malice it was made with, the bigger the "
                + "blast: 4, 7 or 12 damage at the centre (half at the edge) and a push away. Never hurts you or your puppets.");
        add("ability.kegare.chain_burst", "Chain Burst");
        add("ability.kegare.chain_burst.desc", "Every puppet of yours within 8 blocks of what you're looking at detonates, "
                + "nearest first, one after another.");
        add("message.kegare.curio_burst.no_puppet", "No puppet of yours in sight.");
        add("message.kegare.chain_burst.no_target", "Nothing in sight.");
        add("message.kegare.chain_burst.no_puppets", "None of your puppets is within %s blocks of it.");
        add("death.attack.kegare.curio_burst", "%1$s was blown apart by %2$s's puppet");
        add("death.attack.kegare.curio_burst.item", "%1$s was blown apart by %2$s's puppet");
        add("death.attack.kegare.curio_burst.player", "%1$s was blown apart by a puppet while fighting %2$s");
        add("ability.kegare.understudy", "Understudy");
        add("ability.kegare.understudy.desc", "Swap places with the puppet you're looking at (up to 32 blocks). Anything that blocks "
                + "swaps blocks this too.");
        add("ability.kegare.stand_in", "Stand-In");
        add("ability.kegare.stand_in.desc", "For 8 seconds one puppet (the one you look at, else your nearest) grows to your size and "
                + "copies you: your moves, your held items and armor, your name. It looks exactly like you. Then it shrinks back.");
        add("message.kegare.understudy.refused", "Something stops the swap.");
        add("ability.kegare.strings", "Strings");
        add("ability.kegare.strings.desc", "Fire a string at the player, mob or puppet you're looking at (up to 24 blocks). It holds for 8 seconds, "
                + "or until stretched beyond 32 blocks. Press again to Reel In: pull it to you. Look at someone else and press to Tie them together: "
                + "they're pulled into each other, collide, and stay within 3 blocks of each other for 4 seconds.");
        add("ability.kegare.zip_leash", "Zip / Leash");
        add("ability.kegare.zip_leash.desc", "With a string attached: Zip pulls you to its target. Look at someone else to Leash: both stay within "
                + "6 blocks of you for 5 seconds, and you're slowed while they do. Leashed targets can still attack and cast.");
        add("message.kegare.strings.no_target", "Nothing to attach a string to.");
        add("message.kegare.strings.no_string", "No string attached.");
        add("message.kegare.strings.immovable", "It won't budge.");
        add("message.kegare.strings.refused", "The string won't pull.");
        add("message.kegare.strings.snapped", "The string snapped.");
        add("message.kegare.strings.slack", "The string went slack.");
        add("death.attack.kegare.strings_tie", "%1$s was tangled up in %2$s's strings");
        add("death.attack.kegare.strings_tie.item", "%1$s was tangled up in %2$s's strings");
        add("death.attack.kegare.strings_tie.player", "%1$s was tangled up in strings while fighting %2$s");
        add("entity.kegare.puppet", "Puppet");
        add("ability.kegare.possess", "Possess");
        add("ability.kegare.possess.desc", "Move into the puppet you're looking at (or your nearest one) and become it: its size, "
                + "its health, no items, no casting. Your body stays behind, defenceless. R again returns. You snap back if your body "
                + "is hit, if you go more than 64 blocks from it, or if the puppet dies (with backlash: nausea, a stun and lost Malice).");
        add("entity.kegare.puppet_shell", "Empty Body");
        add("message.kegare.possess.no_puppet", "No puppet of yours within %s blocks.");
        add("message.kegare.possess.no_casting", "You can't cast while possessing. Press R to return.");
        add("message.kegare.possess.snapped_back.shell_hit", "Your body was hit! You snap back.");
        add("message.kegare.possess.snapped_back.puppet_died", "Your puppet was destroyed! The backlash hits you.");
        add("message.kegare.possess.snapped_back.too_far", "Too far from your body. You snap back.");
        add("message.kegare.possess.snapped_back.death", "You return to your body.");
        add("message.kegare.possess.snapped_back.dimension_change", "You return to your body.");
        add("hud.kegare.puppets", "Puppets: %s/%s");
        add("hud.kegare.strings.fire", "Fire: %s");
        add("hud.kegare.strings.attached", "[%s] %s %s  ·  [%s] %s %s  ·  %ss");
        add("hud.kegare.strings.reel_in", "Reel In");
        add("hud.kegare.strings.tie", "Tie");
        add("hud.kegare.strings.zip", "Zip");
        add("hud.kegare.strings.leash", "Leash");
        add("gui.kegare.strings.stage.fire", "Now: fire a string (%s Malice)");
        add("gui.kegare.strings.stage.attached", "Now: a string is attached");
        add("gui.kegare.strings.stage.running", "Now: the string is at work");
        add("gui.kegare.strings.then", "Then: Reel In %s or Tie %s");
        add("gui.kegare.strings.then_special", "Then, with its special: Zip %s or Leash %s");
        add("hud.kegare.key_combo", "%s+%s");
        add("hud.kegare.possessing", "Possessing");
        add("hud.kegare.shell_health", "Body %s");
        add("message.kegare.puppet.cap", "You can't hold more than %s puppets.");
        add(com.cnethuka.kegare.innate_technique.puppet_manipulation.PuppetTier.TAP.translationKey(), "Tap");
        add(com.cnethuka.kegare.innate_technique.puppet_manipulation.PuppetTier.HALF.translationKey(), "Half");
        add(com.cnethuka.kegare.innate_technique.puppet_manipulation.PuppetTier.FULL.translationKey(), "Full");
        add(com.cnethuka.kegare.innate_technique.puppet_manipulation.PuppetOrder.FOLLOW.translationKey(), "Follow");
        add(com.cnethuka.kegare.innate_technique.puppet_manipulation.PuppetOrder.STAY.translationKey(), "Stay");
        add(com.cnethuka.kegare.innate_technique.puppet_manipulation.PuppetOrder.GUARD.translationKey(), "Guard");
        add(com.cnethuka.kegare.innate_technique.puppet_manipulation.PuppetOrder.GO.translationKey(), "Go");
        add("message.kegare.glacial_spire.no_ground", "%s needs ground within %s blocks.");
        add("entity.kegare.frost_shard", "Frost Shard");
        add("entity.kegare.glacial_lance", "Glacial Lance");
        add("entity.kegare.splinter_shard", "Splinter");
        add("entity.kegare.glacial_spire", "Glacial Spire");
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
        add("innate_technique.kegare.test_kinetic.epithet", "[Test]");
        add("innate_technique.kegare.test_flux.epithet", "[Test]");
        add("innate_technique.kegare.test_prism.epithet", "[Test]");
        add("innate_technique.kegare.test_apex.epithet", "[Test]");
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
        add("ability.kegare.test_blink", "[Test] Blink");
        add("ability.kegare.test_blink.desc", "Special of Dash: teleport a short way forward. Needs Aura switched on.");
        add("ability.kegare.test_updraft", "[Test] Updraft");
        add("ability.kegare.test_updraft.desc", "Special of Hover: hold to charge, release to shoot straight up. Needs a rare trait.");

        // Traits
        add("trait.kegare.six_eyes", SIX_EYES);
        add("trait.kegare.six_eyes.desc", "Eyes that see through everything: " + MALICE + " everywhere in your dimension on a map, outlines through "
                + "walls, a glance's Read of anyone, and those with none as voids. Your energy goes almost entirely unwasted, more so as you grow into "
                + "them. Left uncovered in a fight, they strain the brain. Only one bearer at a time.");
        add("message.kegare.trait.lost", "The eyes have closed.");
        add("message.kegare.trait.inherited", "The eyes have opened.");
        add("message.kegare.trait.six_eyes.succession", "The eyes have closed... and opened elsewhere.");

        // HUD
        add("hud.kegare.empty_slot", "Empty slot");
        add("hud.kegare.frozen", "Frozen!");
        add("gui.kegare.cost.factor", "×%s %s");
        add("gui.kegare.sorcerer.special.line", "Special: %s (%s) · %s " + MALICE);
        add("gui.kegare.sorcerer.special.line_upkeep", "Special: %s (%s) · %s + %s/s " + MALICE);
        add("gui.kegare.sorcerer.special.none", "No special");
        add("gui.kegare.special.requires", "Requires %s");
        add("gui.kegare.sorcerer.ability.ultimate_locked", "Ultimate: Locked");
        add("gui.kegare.sorcerer.ability.ultimate_locked.desc", "Its " + DOMINION + " comes later.");

        // Cast feedback (action bar)
        add("message.kegare.cast.severed", "You are " + SEVERED + ". No " + MALICE + " flows through you.");
        add("message.kegare.cast.no_innate_technique", "You have no " + INNATE_TECHNIQUE + ".");
        add("message.kegare.cast.no_ultimate", "Your " + INNATE_TECHNIQUE + " has no ultimate.");
        add("message.kegare.cast.empty_slot", "That slot is empty. Fill it from the Sorcerer screen.");

        // Reverse Malice Technique (shared ability)
        add("ability.kegare.reverse_malice_technique", "Reverse Malice Technique");
        add("ability.kegare.reverse_malice_technique.desc", "Turn " + MALICE + " back on your own body. Hold to heal, a little every tick, "
                + "so damage heals as it lands; it keeps running at full health. How fast depends on your output, and injuries piling up in a fight "
                + "drag it down. It drains " + MALICE + " fast. Interrupted by stuns and freezing like any channel.");
        add("ability.kegare.reverse_malice_technique_on_another", "Reverse Malice Technique, on another");
        add("ability.kegare.reverse_malice_technique_on_another.desc", "Heal the player you're looking at, within %1$s blocks, at %2$s of your "
                + "own output, for %3$s times the " + MALICE + ". Few can: it takes enough Output Insight, and then the right moment, when "
                + "someone near you is badly hurt.");
        add("message.kegare.rmt.unlocked", "Your body remembers how to heal.");
        add("message.kegare.rmt.brain_full", "Your brain can't take another repair.");
        add("death.attack.kegare.brain_repair", "%1$s's brain gave out");
        add("death.attack.kegare.brain_repair.player", "%1$s's brain gave out while fighting %2$s");
        add("gui.kegare.sorcerer.brain", "Brain: %s worn by repairs");
        add("gui.kegare.sorcerer.brain.desc", "Repairing a burnt-out technique with Reverse Malice Technique wears the brain. It heals slowly out of a "
                + "fight, and a night's sleep clears it. Full, no more repairs.");
        add("ability.kegare.reverse_malice_technique.unlock", "Take in the green orb.");

        // Eyewear (DESIGN §3.32): anyone's.
        add("item.kegare.blindfold", "Blindfold");
        add("item.kegare.tinted_glasses", "Tinted Glasses");
        add("curios.identifier.eyes", "Eyes");
        add("death.attack.kegare.eyes_collapse", "%1$s's mind gave out");
        add("death.attack.kegare.eyes_collapse.player", "%1$s's mind gave out while fighting %2$s");

        // Six Eyes (DESIGN §3.32): only its bearer ever sees these.
        add("hud.kegare.six_eyes.strain_top", "Your head is splitting");
        add("gui.kegare.sorcerer.eyes", "The eyes: Attunement %s");
        add("gui.kegare.sorcerer.eyes.values", MALICE + " costs at %s, Reverse Malice Technique's output x%s.");
        add("gui.kegare.six_eyes.map", "What the eyes see");
        add("gui.kegare.six_eyes.map.hint", "Scroll to zoom, drag to look round, right-click to come back. Click a dot to pin it.");
        add("gui.kegare.six_eyes.void", "Void");
        add("gui.kegare.six_eyes.pin", "Click to pin");
        add("gui.kegare.six_eyes.unpin", "Click to unpin");
        add("gui.kegare.six_eyes.pin_label", "%s %s");
        add("gui.kegare.settings.six_eyes_minimap", "Minimap of what the eyes see");
        add("gui.kegare.settings.six_eyes_read_tags", "Reads shown at once: %s");
        add("gui.kegare.six_eyes.read.player", "%s · %s " + MALICE);
        add("gui.kegare.six_eyes.read.no_technique", "No technique");
        add("gui.kegare.six_eyes.read.mob_hostile", "%s · hostile flow");
        add("gui.kegare.six_eyes.read.mob_passive", "%s · faint flow");
        add("gui.kegare.six_eyes.read.charging", "Charging %s %s");
        add("gui.kegare.six_eyes.read.channelling", "Channelling %s %s");
        add("gui.kegare.six_eyes.read.channelling_open", "Channelling %s %ss");
        add("gui.kegare.six_eyes.read.toggles", "On: %s");
        add("gui.kegare.six_eyes.read.cooldowns", "Cooling: %s");
        add("gui.kegare.six_eyes.read.cooldown", "%s %ss");
        add("gui.kegare.six_eyes.read.idle", "Idle");

        // Background Tasks (DESIGN §3.31)
        add("gui.kegare.sorcerer.view.background", "Background");
        add("gui.kegare.background.intro", "Things you keep running without thinking. Weaker than the ability on your bar, but they take no slot "
                + "and leave your hands free.");
        add("gui.kegare.background.slots", "Slots: %s");
        add("gui.kegare.background.empty", "Empty");
        add("gui.kegare.background.running", "Running");
        add("gui.kegare.background.paused", "Paused");
        add("gui.kegare.background.off", "Off");
        add("gui.kegare.background.drain", "Drain: %s " + MALICE + "/s");
        add("gui.kegare.background.none", "No Background Tasks exist yet.");
        add("gui.kegare.background.hint", "Click a task, then a slot. Right-click a slot to empty it.");
        add("gui.kegare.background.hint.picked", "Now click a slot to put it there.");
        add("gui.kegare.background.in_combat", "Mid-fight: switch tasks on or off, but slots stay as they are.");
        add("message.kegare.background_task.severed", "You have no " + MALICE + " to keep anything running.");
        add("message.kegare.background_task.in_combat", "You can't change your Background Tasks mid-fight.");
        add("message.kegare.background_task.bad_slot", "You don't have that slot.");
        add("message.kegare.background_task.empty_slot", "There's nothing in that slot.");
        add("message.kegare.background_task.locked", "You haven't unlocked that yet.");
        add("message.kegare.background_task.unknown", "There's no such Background Task.");

        // Background RMT (DESIGN §3.31)
        add("background_task.kegare.background_rmt", "Background RMT");
        add("background_task.kegare.background_rmt.desc", "Reverse Malice Technique left running: it heals you slowly and steadily at %1$s of "
                + "your channelled rate, even at full health, so damage is healed as it lands. It drains " + MALICE + " and slows your natural "
                + MALICE + " regeneration while it runs, and shortens any Burnout. It pauses below %2$s " + MALICE + " and resumes above %3$s.");
        add("background_task.kegare.background_rmt.unlock", "Heal 500 health in total with your own Reverse Malice Technique.");
        add("unlock.kegare.background_rmt", "Background RMT");
        add("background_task.kegare.background_reinforcement", "Background Reinforcement");
        add("background_task.kegare.background_reinforcement.desc", "Reinforcement kept up without thinking: %1$s of the ability's damage "
                + "reductions and knockback resistance, for less " + MALICE + ". With Reinforcement itself on, the stronger applies. A calm, "
                + "faint aura shows it to everyone.");
        add("background_task.kegare.background_reinforcement.unlock", "Reach Grade 3.");
        add("unlock.kegare.background_reinforcement", "Background Reinforcement");
        add("background_task.kegare.sustained_flow", "Sustained Flow");
        add("background_task.kegare.sustained_flow.desc", "A steady flow of " + MALICE + " that makes your active toggles cheaper to keep up: "
                + "their upkeep drains %1$s less (Reinforced Fists, Reinforcement, Accelerate, Rime Shell and the rest). Never a start cost, and "
                + "not other Background Tasks. Nothing shows.");
        add("background_task.kegare.sustained_flow.unlock", "Reach Refinement 5.");
        add("unlock.kegare.sustained_flow", "Sustained Flow");

        // Reinforcement (Malice, every Malice user's)
        add("ability.kegare.reinforcement", "Reinforcement");
        add("ability.kegare.reinforcement.desc", "Toggle: route " + MALICE + " through your whole body. You take %1$s less physical damage "
                + "and %2$s less from techniques, and you're %3$s harder to knock back. It adds no damage. Turning it on costs " + MALICE
                + ", it drains more while it runs, and it keeps running outside Technique Mode. Everyone can see it.");

        // Reinforced Fists (shared ability, every Malice user's)
        add("ability.kegare.reinforced_fists", "Reinforced Fists");
        add("ability.kegare.reinforced_fists.desc", "Toggle: wrap your fists in " + MALICE + ". Your melee hits deal %1$s more with an empty hand "
                + "and %2$s more with anything in it, and a Reinforced Strike landed with it on hits again a moment later, for %3$s of its "
                + "damage. Lighting it costs " + MALICE + ", it drains more while it burns, and it stays lit outside Technique Mode.");
        add("gui.kegare.rmt.output", "Output %sx");
        add("gui.kegare.rmt.knows_others", "You can heal others (R).");
        add("gui.kegare.rmt.not_others", "You don't know how to heal others yet.");
        add("message.kegare.rmt.dont_know", "You don't know how to do that yet.");
        add("message.kegare.rmt.no_one_to_heal", "No one to heal within %s blocks.");
        add("message.kegare.rmt.learned_others", "You know how to heal others now.");
        add("unlock.kegare.rmt_heal_others", "Healing others");
        add("gui.kegare.rmt.output_injured", "Output %sx, injuries %s");

        // Loadout
        add("ability_category.kegare.innate", "Innate");
        add("ability_category.kegare.innate.desc", "Abilities of your " + INNATE_TECHNIQUE + ". A reroll replaces them.");
        add("ability_category.kegare.malice", "Malice");
        add("ability_category.kegare.malice.desc", "Energy techniques any " + MALICE + " user can learn. Yours for good, through every reroll.");
        add("ability_category.kegare.melee", "Melee");
        add("ability_category.kegare.melee.desc", "Close-combat abilities. Yours for good, through every reroll.");
        add("gui.kegare.loadout.category_empty", "Nothing here yet.");
        add("gui.kegare.loadout.kind", "%s · %s");
        add("gui.kegare.loadout.reset.desc", "Put your " + INNATE_TECHNIQUE + "'s abilities back in their usual order.");
        add("gui.kegare.loadout.in_slot", "On your bar: slot %s");
        add("gui.kegare.loadout.not_slotted", "Not on your bar");
        add("gui.kegare.loadout.slot_hint", "Drag to move · right-click to empty");
        add("gui.kegare.loadout.empty_slot_hint", "Drag an ability here");
        add("gui.kegare.loadout.locked.desc", "Locked. Some abilities can't be learned, only found.");
        add("gui.kegare.loadout.ultimate_fixed", "Always your " + INNATE_TECHNIQUE + "'s ultimate. This slot can't be changed.");
        add("message.kegare.loadout.in_combat", "You can't change your loadout mid-fight.");
        add("message.kegare.loadout.casting", "Finish casting first.");
        add("message.kegare.loadout.severed", "You are " + SEVERED + ": there's no loadout to change.");
        add("message.kegare.loadout.no_innate_technique", "You have no " + INNATE_TECHNIQUE + ".");
        add("message.kegare.loadout.ultimate_fixed", "The ultimate slot can't be changed.");
        add("message.kegare.loadout.bad_slot", "That slot doesn't exist.");
        add("message.kegare.loadout.locked", "You haven't unlocked that ability.");
        add("message.kegare.loadout.unavailable", "That ability isn't available to you."); 
        add("message.kegare.cast.ultimate_locked", "Locked. Your " + DOMINION + " comes later.");
        add("message.kegare.cast.no_special", "%s has no special.");
        add("message.kegare.cast.hold_special", "Hold %s and %s to use %s.");
        add("message.kegare.cast.requirement", "%s requires %s.");
        add("message.kegare.cast.refinement_locked", "Requires Refinement %s.");
        add("requirement.kegare.trait", "a rare trait");
        add("requirement.kegare.toggle_active", "%s to be active");
        add("message.kegare.cast.busy", "Already casting.");
        add("message.kegare.cast.stunned", "Stunned! (%ss)");
        add("message.kegare.cast.burnt_out", "Burnt out (%ss).");
        add("hud.kegare.burnt_out", "Burnt out %ss");
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

        // Severed (DESIGN §3.27)
        add("hud.kegare.severed.blur", "Blur");
        add("hud.kegare.severed.winded", "Winded");
        add("hud.kegare.severed.closing_line", "Closing Line");
        add("message.kegare.severed.winded", "Too winded to Blur.");
        add("message.kegare.severed.no_target", "No one in sight.");
        add("message.kegare.severed.in_reach", "Already within reach.");
        add("message.kegare.severed.closing_line_cooldown", "Closing Line isn't ready (%ss).");
        add("subtitles.kegare.severed.blur_on", "Sharp breath out");
        add("subtitles.kegare.severed.blur_wind", "Air rushes past");
        add("subtitles.kegare.severed.closing_line_launch", "Ground cracks under a launch");
        add("subtitles.kegare.severed.closing_line_arrive", "Feet skid");
        add("subtitles.kegare.severed.wall_crash", "Body slams into a wall");
        add("subtitles.kegare.severed.winded", "Ragged breathing");
        // Time Manipulation
        add("innate_technique.kegare.time_manipulation", "Time Manipulation");
        add("innate_technique.kegare.time_manipulation.epithet", "The Clock Forgets to Tick");
        add("innate_technique.kegare.time_manipulation.desc", "Mark a moment to come back to, run your own clock fast and everyone "
                + "else's slow, and at the last, stop time itself.");
        add("ability.kegare.point_maker", "Point Maker");
        add("ability.kegare.point_maker.desc", "First cast: mark the top of the block you're looking at (up to %1$s blocks away; only you "
                + "see it, and it lasts %2$s seconds). Second cast: return to it, facing as you did, shedding every effect you've gained since, "
                + "healed by %3$s of your max health but never past the health you had when you marked it.");
        add("ability.kegare.rewind", "Rewind");
        add("ability.kegare.rewind.desc", "Snap the player or mob you're looking at (up to %1$s blocks) back to where it was %2$s seconds ago. "
                + "Only its place goes back: health, effects and facing stay as they are.");
        add("ability.kegare.accelerate", "Accelerate");
        add("ability.kegare.accelerate.desc", "Toggle: your own clock runs %s times as fast, and everything you do with it: walking, sprinting "
                + "and swimming, attacking, mining, eating, drinking, drawing a bow, loading a crossbow, raising a shield and your technique's "
                + "other cooldowns. You keep full control; jumping and falling don't change. Costs Malice every second it's on.");
        add("ability.kegare.fast_forward", "Fast Forward");
        add("ability.kegare.fast_forward.desc", "Take %s seconds off every other cooldown of your technique at once, never your ultimate's. "
                + "Switches Accelerate off and starts their shared cooldown.");
        add("ability.kegare.decelerate", "Decelerate");
        add("ability.kegare.decelerate.desc", "Throw a spinning clock hand. A hit deals %1$s damage and, for %2$s seconds, slows the target, "
                + "slows their mining and runs their ability cooldowns at %3$s of their speed.");
        add("ability.kegare.stasis_field", "Stasis Field");
        add("ability.kegare.stasis_field.desc", "A field %1$s blocks round the block you're looking at, for %2$s seconds: everything inside is "
                + "heavily slowed, and projectiles crawl through it at %3$s of their speed (yours too) until they're out.");
        add("ability.kegare.standstill", "Standstill");
        add("ability.kegare.standstill.desc", "Stop time for %1$s seconds within %2$s blocks of you. Everything else freezes: anything you "
                + "throw hangs in the air, and every hit on the frozen lands at once when time resumes, as one blow. Anyone else with "
                + "Time Manipulation moves too. Afterwards you're burnt out for %3$s seconds.");
        add("message.kegare.time.mark_elsewhere", "Your mark is in another dimension.");
        add("message.kegare.time.no_block", "No block in sight within %s blocks.");
        add("message.kegare.time.no_block_field", "No block in sight within %s blocks.");
        add("message.kegare.time.no_target", "No one in sight.");
        add("message.kegare.time.immune", "Time won't move %s.");
        add("message.kegare.time.no_history", "%s has no past here to go back to.");
        add("message.kegare.time.already_there", "%s was already there.");
        add("message.kegare.time.refused", "Something holds it where it is.");
        add("message.kegare.time.already_stopped", "Time already stands still here.");
        add("hud.kegare.point_maker.return", "Point Maker: return %ss");
        add("entity.kegare.decelerate", "Clock Hand");
        add("death.attack.kegare.time_manipulation", "%1$s was cut down by %2$s's clock hand");
        add("death.attack.kegare.second_impact", "%1$s was hit twice by %2$s's fist");
        add("death.attack.kegare.second_impact.item", "%1$s was hit twice by %2$s's fist");
        add("death.attack.kegare.second_impact.player", "%1$s was hit twice while fighting %2$s");
        add("death.attack.kegare.time_manipulation.item", "%1$s was cut down by %2$s's clock hand");
        add("death.attack.kegare.time_manipulation.player", "%1$s was cut down by a clock hand while fighting %2$s");
        add("subtitles.kegare.time_manipulation.click", "Watch clicks");
        add("subtitles.kegare.time_manipulation.tick", "Clock ticks");
        add("subtitles.kegare.time_manipulation.wind", "Watch winds");
        add("subtitles.kegare.time_manipulation.reverse_tick", "Clock ticks backward");
        add("subtitles.kegare.time_manipulation.whirl", "Clock hands whirl");
        add("subtitles.kegare.time_manipulation.throw", "Clock hand thrown");
        add("subtitles.kegare.time_manipulation.stasis", "Time slows");
        add("subtitles.kegare.time_manipulation.stop", "Time stops");
        add("subtitles.kegare.time_manipulation.stop_tick", "Slow tick");
        add("subtitles.kegare.time_manipulation.resume", "Time resumes");
        // Advancements
        advancement("awakening", "Awakening", "Be born into a world of " + MALICE + ".");
        advancement("reawakening", "Reawakening", "Use a " + REROLL_ITEM + " to reroll your " + INNATE_TECHNIQUE + ".");
        advancement("chosen_by_fate", "Chosen by Fate", "Roll a Legendary " + INNATE_TECHNIQUE + ".");
        advancement("bitter_luck", "Bitter Luck", "Hit hard pity. That Legendary was guaranteed, not lucky.");
        advancement("severed", SEVERED, "Be born with zero " + MALICE + ".");
        advancement("locked_in", "Locked in", "Land 3 Reinforced Strikes in a row on players, with no plain hit on a player between them, in one fight");
        advancement("120_percent", "120%", "Land a Black Spark on a player while in the zone of the one before");
        advancement("nothing_to_celebrate", "Nothing to Celebrate", "Chain 4 Black Sparks on players without leaving the zone");
        advancement("blessed_by_the_sparks", "Blessed by the Sparks", "Chain 7 Black Sparks on players without leaving the zone");
        advancement("lights_out", "Lights Out", "Send a player into a wall with a Black Spark, hard enough to leave a crater");
        advancement("how_cruel", "How cruel, don't you have a human heart?", "As " + SEVERED + ", kill 3 different " + MALICE + " users within 60 seconds");
        advancement("just_getting_started", "Our fight is just getting started!", "As " + SEVERED + ", drop to 3 hearts or less in a fight with a "
                + MALICE + " user, then kill that same player before the fight ends");
        advancement("overflowing", "Overflowing", "Be born in the Vast " + MALICE + " tier.");
        advancement("running_on_empty", "Running on Empty", "Run out of " + MALICE + " in the middle of a fight.");
        add("advancements.kegare.innate_technique_root.description", "Roll this " + INNATE_TECHNIQUE + " for the first time.");
        advancement("moving_up", "Moving Up", "Reach Grade 3");
        advancement("proven", "Proven", "Reach Grade 2");
        advancement("recognized", "Recognized", "Reach Grade 1");
        advancement("outside_the_ladder", "Outside the Ladder", "Reach Special Grade");
        advancement("full_kit", "Full Kit", "Unlock every ability and special of a technique");
        advancement("clear_sight", "Clear Sight", "Be born with " + SIX_EYES + ".");
        // Rime tab (kegare:innate_technique/rime/...)
        advancement("rime.shatter", "Shatter", "Freeze a player solid");
        advancement("rime.cold_open", "Cold Open", "Hit a player with Frost Shard");
        advancement("swap.switcheroo", "Switcheroo", "Swap positions with a player or a mob");
        advancement("swap.wrong_target", "Wrong Target", "While disguised with Awareness, have a third player damage the person wearing your disguise");
        advancement("swap.fall_guy", "Fall Guy", "A player you swapped dies from falling within 5 seconds");
        advancement("swap.identity_crisis", "Identity Crisis", "Use Total Swap below 30% health and survive for 30 seconds");
        advancement("swap.not_allowed", "Not Allowed", "Have a swap fail because the target can't be swapped");
        advancement("swap.round_trip", "Round Trip", "Damage a player during Switchback before you're swapped back");
        advancement("swap.two_of_you", "There Are Two of You", "While using Mimic, have a player damage the real target");
        advancement("swap.special_delivery_swap", "Return to Sender", "Send Off a player or mob onto your anchor");
        advancement("puppet_manipulation.pulling_strings", "Pulling Strings", "Create your first puppet");
        advancement("puppet_manipulation.out_of_body", "Out of Body", "Possess a puppet for the first time");
        advancement("puppet_manipulation.kuru_kuru", "Kuru Kuru", "Have 10 puppets spinning at once");
        advancement("puppet_manipulation.nobody_home", "Nobody Home", "Have your empty body hit by a player while you're possessing");
        advancement("puppet_manipulation.decoy", "Decoy", "Have a player kill your puppet while you're more than 16 blocks away");
        advancement("puppet_manipulation.whos_real", "Who's Real?", "Have a player hit your Stand-In");
        advancement("puppet_manipulation.curiosity_killed", "Curiosity Killed", "Kill a player with Curio Burst");
        advancement("puppet_manipulation.little_me", "Little Me", "Land the final hit on a player while possessing a puppet");
        advancement("puppet_manipulation.get_over_here", "Get Over Here", "Reel in a player or mob");
        advancement("puppet_manipulation.tied_up", "Tied Up", "Tie two players together");
        advancement("puppet_manipulation.walkies", "Walkies", "Leash two players at once");
        advancement("puppet_manipulation.strings_attached", "Strings Attached", "Tie a player to your puppet and detonate it with Curio Burst");
        advancement("time_manipulation.the_world_stood_still", "The World Stood Still", "Cast Standstill");
        advancement("time_manipulation.overclock", "Overclock", "Keep Accelerate on for 30 seconds straight");
        advancement("time_manipulation.second_chance", "Second Chance", "Return with Point Maker while under 3 hearts and survive the next 10 seconds");
        advancement("time_manipulation.slow_motion", "Slow Motion", "Hit a player with Decelerate while their ability is on cooldown");
        advancement("time_manipulation.not_so_fast", "Not So Fast", "Rewind a player who was running from you");
        advancement("time_manipulation.frozen_in_flight", "Frozen in Flight", "Catch 5 player projectiles in one Stasis Field");
        advancement("time_manipulation.same_clock", "Same Clock", "Be inside another player's Standstill as an immune Time user");
        advancement("time_manipulation.pre_planned", "Pre-Planned", "Have 5 or more of your frozen projectiles hit players when time resumes");
        advancement("swap.newtons_third", "Newton's Third", "A player dies from fall damage after you used Momentum Exchange on them");
        advancement("rime.brain_freeze", "Brain Freeze", "Freeze a player while they're charging or channeling");
        advancement("rime.full_charge", "Full Charge", "Hit a player with a fully charged Glacial Lance");
        advancement("rime.thin_ice", "Thin Ice", "Block 100 total damage from players with Rime Shell");
        advancement("rime.cold_blooded", "Cold Blooded", "Kill a player who is frozen solid");
        advancement("rime.deep_freeze", "Deep Freeze", "Freeze the same player 3 times within 60 seconds");
        advancement("rime.long_way_from_home", "Long Way From Home", "Freeze a player in the Nether");
        advancement("rime.home_field", "Home Field", "Cast a Rime ability during snowfall in a cold biome");
        advancement("a_green_light", "A Green Light", "Unlock Reverse Malice Technique");
        advancement("its_been_a_while", "It's been a while, huh?", "Use Reverse Malice Technique on the brink of death");
        advancement("are_they_high", "Are They High?", "Explain Reverse Malice Technique to the person you're fighting");
        advancement("always_on", "Always On", "Heal enough with Reverse Malice Technique that it keeps going on its own");
        advancement("rime.splinter", "Splinter", "Hit one player with all 3 Splinter shards");
        advancement("rime.sky_high", "Sky High", "Launch a player with Glacial Spire and hit them with Frost Shard before they land");
        advancement("rime.slip_n_slide", "Slip 'n Slide", "Travel 100 blocks with Ice Path");
        advancement("rime.shattered_pressure", "Shattered Pressure", "Hit 3 players with one Shatter Shell");
        advancement("the_cycle_continues", "The Cycle Continues", "Inherit " + SIX_EYES + " from a fallen bearer.");
        advancement("eyes_closed", "Eyes Closed", "Lose your " + SIX_EYES + ".");
        advancement("blinded", "Blinded", "Kill the bearer of " + SIX_EYES + ".");

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
        add("gui.kegare.reveal.six_eyes", "Nothing Is Hidden From You");
        add("gui.kegare.reveal.no_innate_technique", "No " + INNATE_TECHNIQUE + " answered your call.");
        add("gui.kegare.reveal.severed.heaven", "Heaven Gave You Nothing");
        add("gui.kegare.reveal.severed.title", "Born without " + MALICE);
        add("gui.kegare.reveal.severed.subtitle", SEVERED);
        add("gui.kegare.reveal.severed.desc", "Not a drop of " + MALICE + " flows through you. No " + INNATE_TECHNIQUE + " will ever answer.");
        add("gui.kegare.reveal.skip", "Click to skip");
        add("gui.kegare.reveal.continue", "Click to continue");

        // ---- GUI: Sorcerer screen ----
        add("gui.kegare.sorcerer.title", "Sorcerer");
        add("gui.kegare.sorcerer.view.technique", INNATE_TECHNIQUE);
        add("gui.kegare.sorcerer.view.settings", "Settings");
        add("gui.kegare.sorcerer.view.progress", "Progress");
        add("gui.kegare.sorcerer.progress.top", "The top of the ladder.");
        add("gui.kegare.sorcerer.progress.xp", "%s / %s XP to %s");
        add("gui.kegare.sorcerer.progress.assessment_only", "%s comes by Assessment alone.");
        add("gui.kegare.sorcerer.progress.assessment", "Assessment");
        add("gui.kegare.sorcerer.progress.assessment.average", "Average %s / %s");
        add("gui.kegare.sorcerer.progress.assessment.technique", "Technique %s / %s");
        add("gui.kegare.sorcerer.progress.assessment.melee", "Melee %s / %s");
        add("gui.kegare.sorcerer.progress.assessment.fights", "Fights %s / %s");
        add("gui.kegare.sorcerer.progress.assessment.opponents", "Opponents %s / %s");
        add("gui.kegare.sorcerer.progress.assessment.peak", "Peak %s / %s");
        add("gui.kegare.sorcerer.progress.refinement", "Refinement");
        add("gui.kegare.sorcerer.progress.refinement.level", "Level %s / %s: %s / %s XP");
        add("gui.kegare.sorcerer.progress.refinement.top", "Level %s: the top.");
        add("gui.kegare.sorcerer.progress.refinement.severed", "The Severed have none.");
        add("gui.kegare.sorcerer.progress.refinement.none", "No " + INNATE_TECHNIQUE + ".");
        add("gui.kegare.sorcerer.progress.peak", "Peak Refinement: %s");
        add("gui.kegare.sorcerer.progress.insight", "Insight");
        add("gui.kegare.sorcerer.progress.insight.values", "%s %s  %s %s  %s %s");
        for (com.cnethuka.kegare.progression.Grade grade : com.cnethuka.kegare.progression.Grade.values()) {
            add(grade.translationKey(), switch (grade) {
                case GRADE_4 -> "Grade 4";
                case GRADE_3 -> "Grade 3";
                case SEMI_GRADE_2 -> "Semi-Grade 2";
                case GRADE_2 -> "Grade 2";
                case SEMI_GRADE_1 -> "Semi-Grade 1";
                case GRADE_1 -> "Grade 1";
                case SPECIAL_GRADE -> "Special Grade";
            });
        }
        add("insight.kegare.dominion", DOMINION);
        add("insight.kegare.ward", "Ward");
        add("insight.kegare.output", "Output");
        add("toast.kegare.promotion", "Promoted");
        add("toast.kegare.unlocked", "Unlocked");
        add("toast.kegare.unlocked.special", "Special unlocked");
        add("toast.kegare.unlocked.ultimate", "Ultimate unlocked");
        add("gui.kegare.settings.vfx", "Ability effects");
        add("gui.kegare.settings.vfx.full", "Full");
        add("gui.kegare.settings.vfx.reduced", "Reduced");
        add("gui.kegare.settings.vfx.vanilla", "Vanilla");
        add("gui.kegare.settings.first_person", "Your own animations in first person");
        add("gui.kegare.settings.animation_fades", "Blend animations in and out");
        add("gui.kegare.settings.shake", "Screen shake when hits land: %s");
        add("gui.kegare.settings.impact_frames", "Impact frames (black-and-white flash on the biggest hits, Full only)");
        add("gui.kegare.settings.reveal", "Roll reveal animation");
        add("gui.kegare.settings.reveal_always_full", "Always play a technique's full reveal");
        add("gui.kegare.settings.reveal_reduced_motion", "Reveal: fewer flashes, no shake");
        add("gui.kegare.settings.on", "On");
        add("gui.kegare.settings.off", "Off");
        add("gui.kegare.sorcerer.header.category", "%s · %s");
        add("gui.kegare.sorcerer.overview_hint", "Hover an ability to read about it; click one to keep it here. Drag abilities onto your bar, drag slots to swap them, right-click a slot to empty it.");
        add("gui.kegare.sorcerer.hint", "Drag abilities onto your bar");
        add("gui.kegare.sorcerer.hint.in_hand", "Now click a slot on your bar");
        add("gui.kegare.sorcerer.side.malice", MALICE);
        add("gui.kegare.sorcerer.side.amount", "%s / %s");
        add("gui.kegare.sorcerer.side.amount_stacked", "%s /");
        add("gui.kegare.sorcerer.side.regen", "+%s/s");
        add("gui.kegare.sorcerer.side.regen_paused", "Regen paused");
        add("gui.kegare.sorcerer.malice.severed_short", "No " + MALICE + ".");
        add("gui.kegare.sorcerer.severed.short", "No " + INNATE_TECHNIQUE + " will ever answer.");
        add("gui.kegare.loadout.reset_short", "Reset");
        add("gui.kegare.sorcerer.tab.traits", "Traits");
        add("gui.kegare.sorcerer.tab.pity", "Pity");
        add("gui.kegare.sorcerer.innate_technique.none", "No " + INNATE_TECHNIQUE + ".");
        add("gui.kegare.sorcerer.ability.stats", "Cost: %s " + MALICE + " · Cooldown: %ss");
        add("gui.kegare.sorcerer.ability.stats_upkeep", "Cost: %s " + MALICE + " · Upkeep: %s/s · Cooldown: %ss");
        add("gui.kegare.sorcerer.severed.heading", SEVERED);
        add("gui.kegare.sorcerer.severed.desc", "Born without " + MALICE + ". You hold no " + INNATE_TECHNIQUE + ", cannot cast and cannot reroll.");
        add("gui.kegare.sorcerer.traits.none", "No traits.");
        add("gui.kegare.sorcerer.traits.unique", "%s (unique)");
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


        // Commands: what they all share
        add("commands.kegare.entry", "%s: %s");
        add("commands.kegare.rated", "%s (%s)");
        add("commands.kegare.no_player", "No online player matches %s. Use /list to see who's online.");
        add("commands.kegare.severed", "%s is " + SEVERED + " and can't hold an " + INNATE_TECHNIQUE + ".");

        // /kegare help
        add("commands.kegare.help.header", "Kegare commands. Use /kegare help <system> to see one in full, or click it.");
        add("commands.kegare.help.header.system", "/kegare %s commands. Click one to type it:");
        add("commands.kegare.help.unknown", "Unknown system: %s. Use /kegare help to see them all.");
        add("commands.kegare.help.help", "Show the commands you can run, or one system in full.");
        add("commands.kegare.help.info", "Show a player's " + MALICE + ", " + INNATE_TECHNIQUE + ", traits, pity and combat state.");
        add("commands.kegare.help.innate.list", "List every " + INNATE_TECHNIQUE + ".");
        add("commands.kegare.help.innate.get", "Show a player's " + INNATE_TECHNIQUE + ".");
        add("commands.kegare.help.innate.set", "Give a player an " + INNATE_TECHNIQUE + " without rolling.");
        add("commands.kegare.help.innate.reroll", "Roll a player a new " + INNATE_TECHNIQUE + ".");
        add("commands.kegare.help.trait.list", "List every trait.");
        add("commands.kegare.help.trait.get", "Show a player's traits.");
        add("commands.kegare.help.trait.give", "Give a player a trait.");
        add("commands.kegare.help.trait.remove", "Take a trait from a player.");
        add("commands.kegare.help.trait.release", "Free a unique trait from whoever holds it, online or not.");
        add("commands.kegare.help.trait.status", "Show who holds a unique trait, or whether it's passing on.");
        add("commands.kegare.help.malice.get", "Show a player's " + MALICE + " and capacity.");
        add("commands.kegare.help.malice.set", "Set a player's current " + MALICE + ".");
        add("commands.kegare.help.malice.fill", "Fill a player's " + MALICE + ".");
        add("commands.kegare.help.malice.capacity", "Set a player's " + MALICE + " capacity. 0 makes them " + SEVERED + ".");
        add("commands.kegare.help.rebirth", "Wipe a player and roll everything again. A capacity forces their " + MALICE + "; 0 makes them " + SEVERED + ".");
        add("commands.kegare.help.loadout.reset", "Put a player's loadout back to its default layout.");
        add("commands.kegare.help.cooldown.reset", "Make every cooldown ready (abilities, specials, the ultimate, shared ones, Dash, Closing Line) and end Burnout.");
        add("commands.kegare.help.cooldown.reset_one", "Make one ability's cooldown ready (dash and closing_line work too).");
        add("commands.kegare.help.grade.list", "List the Grades, lowest first.");
        add("commands.kegare.help.grade.get", "Show a player's Grade and XP.");
        add("commands.kegare.help.grade.set", "Set a player's Grade. An admin can lower it; nothing else can.");
        add("commands.kegare.help.grade.add", "Give a player Grade XP, which promotes them if the step needs only XP.");
        add("commands.kegare.help.assessment.get", "Show a player's recent fights and where they stand.");
        add("commands.kegare.help.assessment.add", "Add a scored fight. Technique use matches the score if left out.");
        add("commands.kegare.help.refinement.get", "Show a player's Refinement and Peak.");
        add("commands.kegare.help.refinement.set", "Set a player's Refinement.");
        add("commands.kegare.help.insight.get", "Show a player's three Insight values.");
        add("commands.kegare.help.insight.add", "Add to one of a player's Insight values.");
        add("commands.kegare.help.progression.reset", "Wipe a player's Grade, Assessment, Refinement, Insight and unlocks.");
        add("commands.kegare.help.debug.stun", "Stun a player. 0 clears it.");
        add("commands.kegare.help.debug.pity", "Set a player's pity counter.");
        add("commands.kegare.help.debug.reroll", "Roll a player into this " + INNATE_TECHNIQUE + ", reveal and all.");
        add("commands.kegare.help.debug.shared.unlock", "Unlock a shared ability for a player.");
        add("commands.kegare.help.debug.shared.lock", "Lock a shared ability again for a player.");
        add("commands.kegare.help.debug.task.unlock", "Unlock a Background Task for a player, whatever earns it.");
        add("commands.kegare.help.debug.task.lock", "Lock a Background Task again for a player.");
        add("commands.kegare.help.debug.orb.spawn", "Bring a player a green orb now.");
        add("commands.kegare.help.debug.orb.status", "Show a player's next orb roll and any orb under way.");
        add("commands.kegare.help.debug.swap", "Stop swaps from reaching a player, or let them again.");
        add("commands.kegare.help.debug.rmt.fatigue", "Set how much a player's injuries take off their Reverse Malice Technique (0 to 1).");
        add("commands.kegare.help.debug.rmt.healed", "Set how much a player has healed with their own Reverse Malice Technique (Background RMT unlocks at 500).");
        add("commands.kegare.help.debug.rmt.others.unlock", "Let a player heal others with Reverse Malice Technique, without its moment.");
        add("commands.kegare.help.debug.rmt.others.lock", "Take healing others away from a player.");
        add("commands.kegare.help.debug.spark.log", "Show you every Black Spark try (its gap, the rolled offset and the window) and Parry's two timings.");
        add("commands.kegare.help.debug.spark.log.off", "Stop showing you Black Spark's tries and Parry's timings.");
        add("commands.kegare.help.debug.spark.force", "Make the player's next melee hit a Black Spark, for filming.");
        add("commands.kegare.help.debug.rmt.moment", "Make the target glow for the player now, as their chance moment to learn healing others.");
        add("commands.kegare.debug.spark.log.on", "Every Black Spark try (its gap, the rolled offset and the window) and Parry's two timings will be shown to you.");
        add("commands.kegare.debug.spark.log.off", "The Black Spark log is off.");
        add("commands.kegare.debug.spark.force", "%s's next melee hit is a Black Spark.");
        add("commands.kegare.debug.spark.zone", "%s is in the zone for %s seconds.");
        add("commands.kegare.debug.spark.zone.off", "%s is out of the zone.");
        add("commands.kegare.help.debug.spark.zone", "Put the player in the zone for that many seconds (0 ends it). It starts no chain.");
        add("commands.kegare.debug.rmt.fatigue", "%s's injuries take %s off their Reverse Malice Technique: output %sx.");
        add("commands.kegare.debug.rmt.healed", "%s has healed %s with their own Reverse Malice Technique (%s unlocks Background RMT).");
        add("commands.kegare.debug.rmt.others.unlocked", "%s can heal others with Reverse Malice Technique now.");
        add("commands.kegare.debug.rmt.others.locked", "%s can no longer heal others.");
        add("commands.kegare.debug.rmt.others.already", "%s already knows how to heal others.");
        add("commands.kegare.debug.rmt.others.not_known", "%s doesn't know how to heal others.");
        add("commands.kegare.debug.rmt.moment", "%s glows for %s now, for %s seconds.");
        add("commands.kegare.debug.rmt.moment.not_eligible", "%s glows for %s now, for %s seconds, but they aren't eligible, so they can't learn from it.");
        add("commands.kegare.debug.rmt.moment.self", "%s can't glow for themself.");
        add("commands.kegare.help.debug.strings", "Stop strings from moving a player, or let them again.");
        add("commands.kegare.help.debug.impact", "Play an impact tier (light, medium, heavy, impact_frame, top) on a player's own screen as the attacker, victim or witness of a melee or technique hit, to tune how it feels. Nothing lands.");
        add("commands.kegare.help.debug.battle.restore", "Start rebuilding every block battle damage broke (restoring mode) now.");
        add("commands.kegare.help.debug.impact.log", "Show every hit's tier and strength to its attacker and victim, and every area blast's to its caster, for tuning.");
        add("commands.kegare.help.debug.screen", "Play a whole-screen effect (grayscale) on a player's screen for a time. 0 stops it.");
        add("commands.kegare.help.debug.fx.export", "Dev client only: rewrite every Photon effect from its authoring class.");
        add("commands.kegare.help.debug.fx.play", "Dev client only: preview an effect, in front, on yourself or looping.");

        // /kegare info
        add("commands.kegare.info.header", "--- %s ---");
        add("commands.kegare.info.malice", MALICE + ": %s / %s (%s)");
        add("commands.kegare.info.severed", SEVERED + " (no " + INNATE_TECHNIQUE + ", can't reroll)");
        add("commands.kegare.info.innate_technique", INNATE_TECHNIQUE + ": %s (%s), slot %s/%s: %s");
        add("commands.kegare.info.no_innate_technique", INNATE_TECHNIQUE + ": none");
        add("commands.kegare.info.loadout", "Loadout: %s");
        add("commands.kegare.info.loadout_entry", "%s %s");
        add("commands.kegare.info.loadout_empty_rest", "%s-%s empty");
        add("commands.kegare.info.shared", "Shared unlocked: %s");
        add("commands.kegare.info.rmt", "%s: output %sx, injuries %s");
        add("commands.kegare.info.brain", "Brain: %s worn by repairs");
        add("commands.kegare.info.six_eyes", "Six Eyes: Strain %s, Attunement %s, costs at %s, RMT output x%s");
        add("commands.kegare.info.cooldowns", "Cooldowns: %s");
        add("commands.kegare.info.cooldown_entry", "%s %ss");
        add("commands.kegare.info.active", "Active: %s");
        add("commands.kegare.info.traits", "Traits: %s");
        add("commands.kegare.info.pity", "Pity: %s rolls since Legendary (soft pity after %s, hard pity on roll %s). Total rolls: %s");
        add("commands.kegare.info.combat", "In combat: %s | Stunned: %s");
        add("commands.kegare.info.seconds", "%ss");
        add("commands.kegare.info.no", "no");
        add("commands.kegare.info.none", "none");

        // /kegare innate
        add("commands.kegare.innate.list", INNATE_TECHNIQUE + "s, lowest rarity first:");
        add("commands.kegare.innate.unknown", "Unknown " + INNATE_TECHNIQUE + ": %s. Use /kegare innate list to see them all.");
        add("commands.kegare.innate.get", "%s has %s.");
        add("commands.kegare.innate.get.none", "%s has no " + INNATE_TECHNIQUE + ".");
        add("commands.kegare.innate.set", "Set %s's " + INNATE_TECHNIQUE + " to %s.");
        add("commands.kegare.innate.reroll", "Rerolled %s into %s. Pity counter: %s.");

        // /kegare trait
        add("commands.kegare.trait.list", "Traits:");
        add("commands.kegare.trait.unknown", "Unknown trait: %s. Use /kegare trait list to see them all.");
        add("commands.kegare.trait.kind.unique", "Unique");
        add("commands.kegare.trait.holder.unnamed", "a player this server has no name for");
        add("commands.kegare.trait.get", "%s's traits: %s.");
        add("commands.kegare.trait.get.none", "%s has no traits.");
        add("commands.kegare.trait.give", "Gave %s to %s.");
        add("commands.kegare.trait.give.already", "%s already has %s.");
        add("commands.kegare.trait.give.unique_taken", "%s is unique and already held by %s.");
        add("commands.kegare.trait.give.requires_malice", "%s needs " + MALICE + ", and %s is " + SEVERED + ".");
        add("commands.kegare.trait.remove", "Removed %s from %s.");
        add("commands.kegare.trait.remove.not_present", "%s doesn't have %s.");
        add("commands.kegare.trait.release", "Released %s from %s.");
        add("commands.kegare.trait.release.offline", "Released %s from %s, who is offline: it's removed on their next login.");
        add("commands.kegare.trait.release.passing_on", "%s passes to an eligible player in %ss, or at the next eligible login.");
        add("commands.kegare.trait.release.not_unique", "%s isn't a unique trait.");
        add("commands.kegare.trait.release.unclaimed", "Nobody holds %s.");
        add("commands.kegare.trait.status.held", "%s is held by %s.");
        add("commands.kegare.trait.status.passing", "%s is passing on. It opens in %ss.");
        add("commands.kegare.trait.status.waiting", "%s is waiting for an eligible player to log in.");
        add("commands.kegare.trait.status.unclaimed", "%s is unclaimed.");

        // /kegare malice
        add("commands.kegare.malice.get", "%s has %s / %s " + MALICE + " (%s).");
        add("commands.kegare.malice.get.severed", "%s is " + SEVERED + " and has no " + MALICE + ".");
        add("commands.kegare.malice.set", "Set %s's " + MALICE + " to %s / %s.");
        add("commands.kegare.malice.fill", "Filled %s's " + MALICE + " to %s / %s.");
        add("commands.kegare.malice.capacity", "Set %s's " + MALICE + " capacity to %s (%s).");
        add("commands.kegare.malice.capacity.hint", "They have no " + INNATE_TECHNIQUE + ". Use /kegare innate reroll or /kegare innate set.");

        // /kegare rebirth and loadout
        add("commands.kegare.rebirth", "%s was reborn.");
        add("commands.kegare.loadout.reset", "Reset %s's loadout.");
        add("gamerule.kegareBattleDamage", "Kegare: hits break blocks");
        add("gamerule.kegareBattleDamage.description", "Big hits break blocks, leave craters and spill containers. Off: hits break nothing, and still feel the same.");
        add("commands.kegare.cooldown.reset", "Every cooldown of %s's is ready, and any Burnout is over.");
        add("commands.kegare.cooldown.reset_one", "%s is ready for %s.");
        add("commands.kegare.cooldown.unknown", "Nothing with a cooldown is called %s. Press Tab for the names.");

        // /kegare grade, assessment, refinement, insight, progression
        add("commands.kegare.grade.list", "Grades, lowest first:");
        add("commands.kegare.grade.unknown", "Unknown Grade: %s. Use /kegare grade list to see them all.");
        add("commands.kegare.grade.get", "%s is %s: %s / %s XP toward %s.");
        add("commands.kegare.grade.get.top", "%s is %s, the top of the ladder, with %s XP.");
        add("commands.kegare.grade.set", "%s is now %s.");
        add("commands.kegare.grade.add", "Gave %s XP to %s, who is %s with %s XP.");
        add("commands.kegare.assessment.recent", "%s's recent fights, oldest first: %s.");
        add("commands.kegare.assessment.recent_technique", "Technique use in those fights: %s.");
        add("commands.kegare.assessment.none", "Their next step takes no Assessment.");
        add("commands.kegare.assessment.status", "Toward %s: average %s / %s, Technique use %s / %s, fights %s / %s, opponents %s / %s, Peak Refinement %s / %s: %s");
        add("commands.kegare.assessment.met", "met.");
        add("commands.kegare.assessment.unmet", "not met.");
        add("commands.kegare.assessment.add", "Added a %s (Technique use %s) against %s to %s, who is %s.");
        add("commands.kegare.refinement.get", "%s: Refinement %s / %s, %s / %s XP to the next level, Peak Refinement %s.");
        add("commands.kegare.refinement.set", "%s is now at Refinement %s.");
        add("commands.kegare.insight.unknown", "Unknown Insight category: %s. Choose from %s.");
        add("commands.kegare.insight.get", "%s: " + DOMINION + " %s, Ward %s, Output %s.");
        add("commands.kegare.insight.add", "Added %s %s Insight to %s, who now has %s.");
        add("commands.kegare.progression.reset", "%s's Grade, Assessment, Refinement, Insight and unlocks are all reset.");

        // /kegare debug
        add("commands.kegare.debug.stun", "Stunned %s for %ss.");
        add("commands.kegare.debug.stun.cleared", "Cleared %s's stun.");
        add("commands.kegare.debug.pity", "Set %s's pity counter to %s.");
        add("commands.kegare.debug.shared.unknown", "Unknown shared ability: %s. Choose from %s.");
        add("commands.kegare.debug.shared.unlocked", "Unlocked %s for %s.");
        add("commands.kegare.debug.shared.locked", "Locked %s again for %s.");
        add("commands.kegare.debug.shared.already", "%s already has %s.");
        add("commands.kegare.debug.shared.not_unlocked", "%s hasn't unlocked %s.");
        add("commands.kegare.debug.shared.severed", "%s is " + SEVERED + " and can't unlock %s.");
        add("commands.kegare.debug.task.unknown", "Unknown Background Task: %s. Choose from %s.");
        add("commands.kegare.debug.brain", "%s's brain meter is now %s.");
        add("commands.kegare.debug.sixeyes.strain", "%s's Strain is now %s.");
        add("commands.kegare.debug.sixeyes.attunement", "%s's Attunement is now %s.");
        add("commands.kegare.help.debug.sixeyes.attunement", "Set a player's Attunement.");
        add("commands.kegare.help.debug.sixeyes.strain", "Set a player's Strain (0 to 100).");
        add("commands.kegare.help.debug.brain", "Set a player's brain meter (0 to 100): what repairing burnt-out techniques has cost.");
        add("commands.kegare.debug.task.unlocked", "Unlocked %s for %s.");
        add("commands.kegare.debug.task.locked", "Locked %s again for %s.");
        add("commands.kegare.debug.task.already", "%s already has %s.");
        add("commands.kegare.debug.task.not_unlocked", "%s hasn't unlocked %s.");
        add("commands.kegare.debug.task.severed", "%s is " + SEVERED + " and can't run %s.");
        add("commands.kegare.debug.orb.spawned", "A green orb appeared for %s. Only they can see it.");
        add("commands.kegare.debug.orb.already_unlocked", "%s has already unlocked Reverse Malice Technique, so the orb never comes again.");
        add("commands.kegare.debug.orb.severed", "%s is " + SEVERED + ", so the orb never comes to them.");
        add("commands.kegare.debug.orb.not_born", "%s hasn't been born yet.");
        add("commands.kegare.debug.orb.status", "Green orb for %s: next chance roll in %ss; orb waiting: %s; Reverse Malice Technique %s; eligible: %s.");
        add("commands.kegare.debug.orb.status.following", "yes (%s/%s ticks along, at %s %s %s)");
        add("commands.kegare.debug.orb.status.yes", "yes");
        add("commands.kegare.debug.orb.status.no", "no");
        add("commands.kegare.debug.orb.status.unlocked", "unlocked");
        add("commands.kegare.debug.orb.status.locked", "locked");
        add("commands.kegare.debug.impact", "Played a %s impact on %s's screen, as the %s. Nothing landed.");
        add("commands.kegare.debug.screen", "Playing %s on %s's screen for %s s.");
        add("commands.kegare.debug.impact.unknown", "No such choice: %s. Choose from %s.");
        add("commands.kegare.debug.impact.log.on", "Every hit's tier and strength now goes to its attacker and victim.");
        add("commands.kegare.debug.impact.log.off", "Hits aren't shown any more.");
        add("commands.kegare.debug.impact.blast", "Blast: %s, strength %s at its centre (Malice %s)");
        add("commands.kegare.debug.impact.line", "Hit: %s, strength %s (damage %s of %s, knockback %s, launch %s, charge %s, Malice %s)");
        add("impact.kegare.tier.light", "Light");
        add("impact.kegare.tier.medium", "Medium");
        add("impact.kegare.tier.heavy", "Heavy");
        add("impact.kegare.tier.impact_frame", "Impact frame");
        add("impact.kegare.tier.top", "Top");
        add("commands.kegare.debug.screen.stopped", "Stopped %s on %s's screen.");
        add("commands.kegare.debug.battle.restore", "%s broken blocks start coming back now.");
        add("commands.kegare.debug.swap.on", "%s can't be swapped now. This cancels SwapTargetEvent.");
        add("commands.kegare.debug.swap.off", "%s can be swapped again.");
        add("commands.kegare.debug.strings.on", "%s can't be moved by strings now. This cancels StringsTargetEvent.");
        add("commands.kegare.debug.strings.off", "%s can be moved by strings again.");

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
            if (!innateTechnique.isTestContent() && !written.contains(innateTechnique.epithetId())) missing.add(innateTechnique.epithetId());
            List<Ability> abilities = new ArrayList<>(innateTechnique.abilities());
            innateTechnique.ultimate().ifPresent(abilities::add);
            for (Ability ability : abilities) {
                if (!written.contains(ability.descriptionId())) missing.add(ability.descriptionId());
                if (!written.contains(ability.descriptionId() + ".desc")) missing.add(ability.descriptionId() + ".desc");
            }
        }
        for (Ability ability : com.cnethuka.kegare.loadout.SharedAbilities.all()) {
            if (!written.contains(ability.descriptionId())) missing.add(ability.descriptionId());
            if (!written.contains(ability.descriptionId() + ".desc")) missing.add(ability.descriptionId() + ".desc");
        }
        for (Trait trait : KegareRegistries.TRAIT) {
            if (!written.contains(trait.descriptionId())) missing.add(trait.descriptionId());
            if (!written.contains(trait.descriptionId() + ".desc")) missing.add(trait.descriptionId() + ".desc");
        }
        if (!missing.isEmpty()) throw new IllegalStateException("Missing lang entries: " + missing.stream().distinct().toList());
        for (InnateTechnique innateTechnique : KegareRegistries.INNATE_TECHNIQUE) {
            if (!innateTechnique.hasUnlockLevels()) {
                throw new IllegalStateException(innateTechnique.id() + " doesn't set its Refinement unlock levels: call defaultUnlockLevels() or unlockLevel(...) in its definition");
            }
        }
    }
}

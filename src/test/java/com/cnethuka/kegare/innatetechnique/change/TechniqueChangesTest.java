package com.cnethuka.kegare.innatetechnique.change;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cnethuka.kegare.TestServer;
import com.cnethuka.kegare.advancement.AdvancementAbilities;
import com.cnethuka.kegare.advancement.KegareAdvancements;
import com.cnethuka.kegare.api.KegareApi;
import com.cnethuka.kegare.api.ability.Ability;
import com.cnethuka.kegare.api.ability.AbilityContext;
import com.cnethuka.kegare.api.ability.BaseAbility;
import com.cnethuka.kegare.api.ability.CastType;
import com.cnethuka.kegare.api.ability.EndReason;
import com.cnethuka.kegare.api.ability.SpecialAbility;
import com.cnethuka.kegare.api.change.AbilityTweak;
import com.cnethuka.kegare.api.change.TechniqueChanges;
import com.cnethuka.kegare.api.example.ExampleAddon;
import com.cnethuka.kegare.api.technique.InnateTechnique;
import com.cnethuka.kegare.api.technique.InnateTechniqueRarity;
import com.cnethuka.kegare.innate_technique.rime.Rime;
import com.cnethuka.kegare.innate_technique.rime.RimeAdvancements;
import com.cnethuka.kegare.innatetechnique.CastIntent;
import com.cnethuka.kegare.innatetechnique.CastManager;
import com.cnethuka.kegare.innatetechnique.InnateTechniqueService;
import com.cnethuka.kegare.malice.MaliceService;
import com.cnethuka.kegare.player.BirthService;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Changing techniques that already exist (DESIGN §3.34): additions, replacements and tweaks, checked and applied (on a change set of
 * the test's own, so the game's stays as it is), the tests' example addon's real changes through the cast pipeline, the advancements a
 * change turns off, and the join check's description and message.
 */
@ExtendWith(EphemeralTestServerProvider.class)
class TechniqueChangesTest {
    private static final double EPS = 1.0e-6;
    private static final double Y = 120;
    private static final ResourceLocation RIME = ResourceLocation.fromNamespaceAndPath("kegare", "rime");

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("change_test", path);
    }

    /** An instant ability with fixed numbers, recording when its parts run. */
    private static BaseAbility plain(String path, double cost, List<String> log) {
        return new BaseAbility(id(path), CastType.INSTANT) {
            @Override public double cost(ServerPlayer player) { return cost; }
            @Override public int cooldownTicks(ServerPlayer player) { return 40; }
            @Override public void execute(AbilityContext ctx) { log.add(path); }
            @Override public void tick(AbilityContext ctx) { log.add(path + " tick"); }
            @Override public void onEnd(AbilityContext ctx, EndReason reason) { log.add(path + " end"); }
        };
    }

    private static List<ResourceLocation> ids(List<Ability> abilities) {
        return abilities.stream().map(Ability::id).toList();
    }

    /** Rime's unlock levels as its definition gives them, by ID. */
    private static int rimeLevel(ResourceLocation ability) {
        InnateTechnique rime = Rime.RIME.get();
        return rime.findAbility(ability).map(rime::unlockLevel).orElse(0);
    }

    @Test
    void additionsGoWhereTheyreToldAndEveryoneElseKeepsTheirLevel(MinecraftServer server) {
        ChangeSet changes = new ChangeSet();
        List<String> log = new ArrayList<>();
        BaseAbility first = plain("first", 10, log);
        BaseAbility last = plain("last", 10, log);
        BaseAbility withSpecial = new BaseAbility(id("with_special"), CastType.INSTANT) {
            @Override public double cost(ServerPlayer player) { return 10; }
            @Override public int cooldownTicks(ServerPlayer player) { return 10; }
            @Override public void execute(AbilityContext ctx) {}
            @Override public Optional<Ability> special() { return Optional.of(WITH_SPECIAL_SPECIAL); }
        };
        withSpecialBase = withSpecial;
        changes.add(RIME, Rime.GLACIAL_LANCE.id(), first, 3, null, "a");
        changes.add(RIME, Rime.GLACIAL_LANCE.id(), withSpecial, 4, 6, "b");
        changes.add(RIME, null, last, 9, null, "c");

        TechniqueViews.View view = changes.view(Rime.RIME.get(), RIME);
        assertEquals(List.of(Rime.FROST_SHARD.id(), Rime.GLACIAL_LANCE.id(), first.id(), withSpecial.id(), Rime.FROST_FIELD.id(), Rime.RIME_SHELL.id(),
                last.id()), ids(view.abilities()), "after Glacial Lance in the order registered; the last at the end");
        for (Ability rime : Rime.RIME.get().definedAbilities()) {
            assertEquals(rimeLevel(rime.id()), view.unlockLevel(rime.id(), TechniqueChangesTest::rimeLevel), rime.id() + " keeps its level");
            ResourceLocation special = rime.special().orElseThrow().id();
            assertEquals(rimeLevel(special), view.unlockLevel(special, TechniqueChangesTest::rimeLevel), special + " keeps its level");
        }
        assertEquals(3, view.unlockLevel(first.id(), TechniqueChangesTest::rimeLevel));
        assertEquals(4, view.unlockLevel(withSpecial.id(), TechniqueChangesTest::rimeLevel));
        assertEquals(6, view.unlockLevel(WITH_SPECIAL_SPECIAL.id(), TechniqueChangesTest::rimeLevel), "its special where it said");
        assertEquals(9, view.unlockLevel(last.id(), TechniqueChangesTest::rimeLevel));
        assertSame(Rime.FROST_SHARD, view.abilities().get(0), "what nobody changed is the same object");
        assertTrue(view.every().contains(WITH_SPECIAL_SPECIAL), "its special is castable");
    }

    private static BaseAbility withSpecialBase;
    private static final SpecialAbility WITH_SPECIAL_SPECIAL = new SpecialAbility(id("with_special_r"), CastType.INSTANT, () -> withSpecialBase) {
        @Override public double cost(ServerPlayer player) { return 10; }
        @Override public void execute(AbilityContext ctx) {}
    };

    @Test
    void aReplacementTakesTheSlotAndLevelsOfWhatItReplaces(MinecraftServer server) {
        ChangeSet changes = new ChangeSet();
        SpecialAbility newPath = new SpecialAbility(id("new_path"), CastType.INSTANT, () -> Rime.FROST_FIELD) {
            @Override public double cost(ServerPlayer player) { return 5; }
            @Override public void execute(AbilityContext ctx) {}
        };
        BaseAbility field = new BaseAbility(Rime.FROST_FIELD.id(), CastType.INSTANT) {
            @Override public double cost(ServerPlayer player) { return 77; }
            @Override public int cooldownTicks(ServerPlayer player) { return 10; }
            @Override public void execute(AbilityContext ctx) {}
            @Override public Optional<Ability> special() { return Optional.of(newPath); }
        };
        SpecialAbility splinter = new SpecialAbility(Rime.SPLINTER.id(), CastType.INSTANT, () -> Rime.FROST_SHARD) {
            @Override public double cost(ServerPlayer player) { return 1; }
            @Override public void execute(AbilityContext ctx) {}
        };
        changes.replace(field, "a");
        changes.replace(splinter, "b");
        TechniqueViews.View view = changes.view(Rime.RIME.get(), RIME);
        assertSame(field, view.abilities().get(2), "in Frost Field's slot");
        assertEquals(rimeLevel(Rime.FROST_FIELD.id()), view.unlockLevel(field.id(), TechniqueChangesTest::rimeLevel), "at its level");
        assertEquals(rimeLevel(Rime.ICE_PATH.id()), view.unlockLevel(newPath.id(), TechniqueChangesTest::rimeLevel),
                "its new special unlocks where Ice Path did");
        Ability shard = view.abilities().get(0);
        assertNotSame(Rime.FROST_SHARD, shard, "Frost Shard's special changed, so players get it through a wrapper");
        assertEquals(Rime.FROST_SHARD.id(), shard.id());
        assertSame(splinter, shard.special().orElseThrow(), "with the replacement as its special");
        assertTrue(view.every().contains(splinter) && !view.every().contains(Rime.SPLINTER));
        assertEquals(Set.of(Rime.FROST_FIELD.id(), Rime.SPLINTER.id()), changes.changed());
    }

    @Test
    void aReplacementHasToFitItsSlot(MinecraftServer server) {
        ChangeSet notSpecial = new ChangeSet();
        notSpecial.replace(plainWithId(Rime.SPLINTER.id()), "a");
        assertThrows(IllegalStateException.class, () -> notSpecial.view(Rime.RIME.get(), RIME), "a special replaced by an ordinary ability");
        ChangeSet special = new ChangeSet();
        special.replace(new SpecialAbility(Rime.GLACIAL_LANCE.id(), CastType.INSTANT, () -> Rime.FROST_SHARD) {
            @Override public double cost(ServerPlayer player) { return 1; }
            @Override public void execute(AbilityContext ctx) {}
        }, "a");
        assertThrows(IllegalStateException.class, () -> special.view(Rime.RIME.get(), RIME), "an ordinary ability replaced by a special");
        ChangeSet otherBase = new ChangeSet();
        otherBase.replace(new SpecialAbility(Rime.SPLINTER.id(), CastType.INSTANT, () -> Rime.GLACIAL_LANCE) {
            @Override public double cost(ServerPlayer player) { return 1; }
            @Override public void execute(AbilityContext ctx) {}
        }, "a");
        assertThrows(IllegalStateException.class, () -> otherBase.view(Rime.RIME.get(), RIME), "a special of another base");
    }

    private static BaseAbility plainWithId(ResourceLocation id) {
        return new BaseAbility(id, CastType.INSTANT) {
            @Override public double cost(ServerPlayer player) { return 1; }
            @Override public int cooldownTicks(ServerPlayer player) { return 1; }
            @Override public void execute(AbilityContext ctx) {}
        };
    }

    @Test
    void clashesAndMistakesStopTheGameWithTheirNames(MinecraftServer server) {
        ChangeSet changes = new ChangeSet();
        changes.replace(plainWithId(Rime.GLACIAL_LANCE.id()), "first_addon");
        IllegalStateException clash = assertThrows(IllegalStateException.class, () -> changes.replace(plainWithId(Rime.GLACIAL_LANCE.id()), "second_addon"));
        assertTrue(clash.getMessage().contains("first_addon") && clash.getMessage().contains("second_addon"), clash.getMessage());

        List<String> log = new ArrayList<>();
        assertThrows(IllegalArgumentException.class, () -> changes.add(RIME, null, Rime.SPLINTER, 1, null, "a"), "a special on its own");
        assertThrows(IllegalArgumentException.class, () -> changes.add(RIME, null, plain("negative", 1, log), -1, null, "a"));
        assertThrows(IllegalArgumentException.class, () -> changes.add(RIME, null, withSpecialBase(), 1, null, "a"), "its special needs a level");
        assertThrows(IllegalArgumentException.class, () -> changes.add(RIME, null, plain("no_special", 1, log), 1, 2, "a"), "no special, no special level");
        changes.add(RIME, null, plain("twice", 1, log), 1, null, "a");
        assertThrows(IllegalStateException.class, () -> changes.add(RIME, null, plain("twice", 1, log), 1, null, "b"), "one ID, added twice");

        ChangeSet anchor = new ChangeSet();
        anchor.add(RIME, id("nowhere"), plain("anchored", 1, log), 1, null, "a");
        assertThrows(IllegalStateException.class, () -> anchor.view(Rime.RIME.get(), RIME), "after an ability that isn't there");
        ChangeSet sameId = new ChangeSet();
        sameId.add(RIME, null, plainWithId(Rime.FROST_SHARD.id()), 1, null, "a");
        assertThrows(IllegalStateException.class, () -> sameId.view(Rime.RIME.get(), RIME), "an ID the technique already has");

        ChangeSet unknown = new ChangeSet();
        unknown.tweak(id("nothing"), new AbilityTweak() {}, "a");
        assertThrows(IllegalStateException.class, () -> unknown.checkAllFound(List.of(RIME), Set.of(Rime.FROST_SHARD.id())));
        ChangeSet unknownTechnique = new ChangeSet();
        unknownTechnique.add(id("no_technique"), null, plain("lost", 1, log), 1, null, "a");
        assertThrows(IllegalStateException.class, () -> unknownTechnique.checkAllFound(List.of(RIME), Set.of()));
        ChangeSet shared = new ChangeSet();
        shared.tweak(com.cnethuka.kegare.flow.Flow.FLOW.id(), new AbilityTweak() {}, "a");
        Set<ResourceLocation> every = Rime.RIME.get().everyAbility().stream().map(Ability::id).collect(Collectors.toSet());
        IllegalStateException sharedOne = assertThrows(IllegalStateException.class, () -> shared.checkAllFound(List.of(RIME), every));
        assertTrue(sharedOne.getMessage().contains("shared abilities"), sharedOne.getMessage());

        ChangeSet sealed = new ChangeSet();
        sealed.seal();
        IllegalStateException late = assertThrows(IllegalStateException.class, () -> sealed.tweak(Rime.FROST_SHARD.id(), new AbilityTweak() {}, "a"));
        assertTrue(late.getMessage().contains("constructor"), late.getMessage());
    }

    private static BaseAbility withSpecialBase() {
        return new BaseAbility(id("with_special_" + System.nanoTime()), CastType.INSTANT) {
            @Override public double cost(ServerPlayer player) { return 1; }
            @Override public int cooldownTicks(ServerPlayer player) { return 1; }
            @Override public void execute(AbilityContext ctx) {}
            @Override public Optional<Ability> special() { return Optional.of(Rime.SPLINTER); }
        };
    }

    @Test
    void tweaksStackInOrderAndRunAroundTheAbility(MinecraftServer server) throws Exception {
        List<String> log = new ArrayList<>();
        BaseAbility tweaked = plain("tweaked", 10, log);
        InnateTechnique technique = InnateTechnique.builder(InnateTechniqueRarity.COMMON).ability(tweaked).defaultUnlockLevels().build();
        ChangeSet changes = new ChangeSet();
        changes.tweak(tweaked.id(), new AbilityTweak() {
            @Override public double cost(ServerPlayer player, double cost) { return cost * 2; }
            @Override public int cooldownTicks(ServerPlayer player, int ticks) { return ticks + 5; }
            @Override public void beforeExecute(AbilityContext ctx) { log.add("first before"); }
            @Override public void afterExecute(AbilityContext ctx) { log.add("first after"); }
            @Override public void tick(AbilityContext ctx) { log.add("first tick"); }
            @Override public void onEnd(AbilityContext ctx, EndReason reason) { log.add("first end"); }
        }, "a");
        changes.tweak(tweaked.id(), new AbilityTweak() {
            @Override public double cost(ServerPlayer player, double cost) { return cost + 3; }
            @Override public boolean canCast(AbilityContext ctx) { return log.isEmpty(); }
            @Override public void afterExecute(AbilityContext ctx) { log.add("second after"); }
        }, "b");
        Ability ability = changes.view(technique, id("technique")).abilities().get(0);
        assertEquals(10 * 2 + 3, ability.cost(null), EPS, "in the order registered");
        assertEquals(10 * 2 + 3, ability.shownCost(null), EPS, "and what the screens show");
        assertEquals(45, ability.cooldownTicks(null));
        assertEquals(tweaked.id(), ability.id());
        assertEquals(tweaked.cooldownKey(), ability.cooldownKey(), "the same cooldown");
        assertTrue(ability.canCast(null), "a tweak may refuse it");
        ability.execute(null);
        ability.tick(null);
        ability.onEnd(null, EndReason.RELEASED);
        assertEquals(List.of("first before", "tweaked", "first after", "second after", "tweaked tick", "first tick", "tweaked end", "first end"), log);
        assertFalse(ability.canCast(null), "now the second tweak refuses it");
    }

    // ------------------------------------------------------------------ the game's changes: the tests' example addon

    @Test
    void theExampleAddonsChangesAreAppliedEverywhere(MinecraftServer server) {
        InnateTechnique technique = ExampleAddon.TECHNIQUE.get();
        assertEquals(List.of(ExampleAddon.JOLT.id(), ExampleAddon.SPARK.id(), ExampleAddon.HUM.id()), ids(technique.abilities()), "Spark added after Jolt");
        assertEquals(ExampleAddon.SPARK_LEVEL, technique.unlockLevel(ExampleAddon.SPARK));
        assertEquals(3, technique.unlockLevel(ExampleAddon.HUM_BURST_REPLACEMENT), "the replacement keeps Hum Burst's level");
        assertSame(ExampleAddon.HUM_BURST_REPLACEMENT, technique.abilities().get(2).special().orElseThrow(), "Hum's special is the replacement");
        assertEquals(Set.of(ExampleAddon.JOLT.id(), ExampleAddon.HUM_BURST.id(), Rime.FROST_FIELD.id()), TechniqueChanges.changedAbilities(),
                "replaced and tweaked, not added");
        assertFalse(TechniqueChanges.isChanged(ExampleAddon.SPARK.id()));
        assertEquals(Rime.FROST_FIELD.id(), Rime.RIME.get().ability(2).id(), "Kegare's Frost Field, through a wrapper");
        assertNotSame(Rime.FROST_FIELD, Rime.RIME.get().ability(2));
    }

    @Test
    void aTweakedAbilityCastsThroughThePipeline(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            TestServer.ensureOverworld(server);
            var level = server.overworld();
            Pig pig = EntityType.PIG.create(level);
            try {
                ServerPlayer p = TestServer.damageable(server, "change_jolt");
                level.addNewPlayer(p);
                BirthService.rebirth(p, OptionalInt.of(2000));
                InnateTechniqueService.setInnateTechnique(p, ExampleAddon.TECHNIQUE.get());
                MaliceService.refill(p);
                p.moveTo(0.5, Y, 0.5, 0f, 0f);
                pig.moveTo(2.5, Y, 0.5, 0f, 0f);
                pig.setNoGravity(true);
                pig.setNoAi(true);
                level.addFreshEntity(pig);
                InnateTechniqueService.select(p, 0);

                // Sneaking: the tweak refuses it before anything is paid.
                p.setShiftKeyDown(true);
                double before = MaliceService.get(p).current();
                CastManager.handleIntent(p, CastIntent.CAST_PRESS);
                assertEquals(before, MaliceService.get(p).current(), EPS, "refused for free");
                assertEquals(0, InnateTechniqueService.cooldownRemaining(p, ExampleAddon.JOLT), "no cooldown");

                p.setShiftKeyDown(false);
                int jolts = ExampleAddon.JOLTS.get();
                float health = pig.getHealth();
                CastManager.handleIntent(p, CastIntent.CAST_PRESS);
                assertEquals(before - ExampleAddon.JOLT_COST * 2, MaliceService.get(p).current(), EPS, "twice the cost");
                assertEquals(ExampleAddon.JOLT_COOLDOWN + ExampleAddon.JOLT_EXTRA_COOLDOWN, InnateTechniqueService.cooldownRemaining(p, ExampleAddon.JOLT));
                assertEquals(health - ExampleAddon.JOLT_DAMAGE * 2, pig.getHealth(), EPS, "twice the damage");
                assertEquals(jolts + 1, ExampleAddon.JOLTS.get(), "its hook ran");

                // The added ability is in the loadout and casts.
                InnateTechniqueService.select(p, 1);
                before = MaliceService.get(p).current();
                CastManager.handleIntent(p, CastIntent.CAST_PRESS);
                assertEquals(before - ExampleAddon.SPARK_COST, MaliceService.get(p).current(), EPS, "Spark, the added ability");
            } finally {
                pig.discard();
                TestServer.removePlayers(server);
            }
        });
    }

    @Test
    void kegaresOwnTweakedAbilityStillWorks(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            TestServer.ensureOverworld(server);
            try {
                ServerPlayer p = TestServer.player(server, "change_field");
                BirthService.rebirth(p, OptionalInt.of(2000));
                InnateTechniqueService.setInnateTechnique(p, Rime.RIME.get());
                MaliceService.refill(p);
                InnateTechniqueService.select(p, 2);
                int fields = ExampleAddon.FROST_FIELDS.get();
                CastManager.handleIntent(p, CastIntent.CAST_PRESS);
                assertEquals(Rime.FROST_FIELD.id(), CastManager.state(p).casting().ability().id(), "Frost Field channels as ever");
                assertEquals(fields + 1, ExampleAddon.FROST_FIELDS.get(), "and the addon's hook ran after it started");
                CastManager.handleIntent(p, CastIntent.CAST_RELEASE);
            } finally {
                TestServer.removePlayers(server);
            }
        });
    }

    // ------------------------------------------------------------------ advancements

    @Test
    void aChangedAbilityTurnsOffTheAdvancementsThatNeedIt(MinecraftServer server) {
        assertTrue(AdvancementAbilities.isTurnedOff(RimeAdvancements.FULL_CHARGE, Set.of(Rime.GLACIAL_LANCE.id())));
        assertTrue(AdvancementAbilities.isTurnedOff(RimeAdvancements.SKY_HIGH, Set.of(Rime.FROST_SHARD.id())), "either of two it needs");
        assertFalse(AdvancementAbilities.isTurnedOff(RimeAdvancements.COLD_OPEN, Set.of(Rime.GLACIAL_LANCE.id())));
        Set<ResourceLocation> allOfRime = Rime.RIME.get().everyAbility().stream().map(Ability::id).collect(Collectors.toSet());
        assertFalse(AdvancementAbilities.isTurnedOff(RimeAdvancements.SHATTER, allOfRime), "one about the technique as a whole stays on");
        assertFalse(AdvancementAbilities.isTurnedOff(KegareAdvancements.AWAKENING, allOfRime), "one outside the technique tabs");
        assertEquals(List.of(), AdvancementAbilities.turnedOff(TechniqueChanges.changedAbilities()),
                "the example addon's changes (Frost Field's hook) turn none of Kegare's off");
    }

    @Test
    void everyAdvancementInKegaresTechniqueTabsIsListed(MinecraftServer server) {
        Map<ResourceLocation, List<ResourceLocation>> listed = AdvancementAbilities.all();
        int seen = 0;
        for (AdvancementHolder holder : server.getAdvancements().getAllAdvancements()) {
            Optional<ResourceLocation> parent = holder.value().parent();
            if (parent.isEmpty() || !parent.get().getNamespace().equals(KegareApi.MOD_ID) || !parent.get().getPath().startsWith("innate_technique/")) continue;
            seen++;
            assertTrue(listed.containsKey(holder.id()), holder.id() + " says which abilities it depends on (AdvancementAbilities)");
            ResourceLocation techniqueId = ResourceLocation.fromNamespaceAndPath(KegareApi.MOD_ID, parent.get().getPath().substring("innate_technique/".length()));
            InnateTechnique technique = KegareApi.innateTechniques().get(techniqueId);
            for (ResourceLocation ability : listed.get(holder.id())) {
                assertTrue(technique.findAbility(ability).isPresent(), holder.id() + " lists " + ability + ", one of " + techniqueId + "'s");
            }
        }
        assertEquals(listed.size(), seen, "and nothing else is listed");
    }

    // ------------------------------------------------------------------ the join check

    @Test
    void theJoinCheckDescribesTechniquesAndChangesAndNamesWhatDiffers(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            List<String> lines = TechniqueCheck.describe();
            assertTrue(lines.contains("technique kegare_example:example: kegare_example:jolt kegare_example:spark kegare_example:hum/kegare_example:hum_burst"),
                    () -> "got " + lines);
            assertTrue(lines.stream().anyMatch(l -> l.startsWith("change kegare:frost_field: tweaked by ")), () -> "got " + lines);
            assertTrue(lines.stream().anyMatch(l -> l.startsWith("technique kegare:rime: kegare:frost_shard/kegare:splinter ")), () -> "got " + lines);
            assertEquals(Optional.empty(), TechniqueCheck.difference(lines, List.copyOf(lines)), "the same: let in");

            List<String> noAddonChanges = lines.stream().filter(l -> !l.startsWith("change kegare:frost_field")).toList();
            Component missing = TechniqueCheck.difference(lines, noAddonChanges).orElseThrow();
            assertEquals("disconnect.kegare.techniques_differ", ((TranslatableContents) missing.getContents()).getKey());
            assertEquals("Frost Field", ((Component) ((TranslatableContents) missing.getContents()).getArgs()[0]).getString(), "names what differs");

            List<String> otherRime = lines.stream().map(l -> l.startsWith("technique kegare:rime:") ? l + " extra:thing" : l).toList();
            Component rime = TechniqueCheck.difference(lines, otherRime).orElseThrow();
            assertEquals("Rime", ((Component) ((TranslatableContents) rime.getContents()).getArgs()[0]).getString());
        });
    }
}

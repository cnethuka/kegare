package com.cnethuka.kegare.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cnethuka.kegare.TestServer;
import com.cnethuka.kegare.client.gui.GuiDraw;
import com.cnethuka.kegare.combat.CombatHelper;
import com.cnethuka.kegare.config.KegareServerConfig;
import com.cnethuka.kegare.innate_technique.puppet_manipulation.PuppetManipulation;
import com.cnethuka.kegare.innate_technique.rime.Rime;
import com.cnethuka.kegare.innate_technique.swap.Swap;
import com.cnethuka.kegare.innate_technique.swap.SwapDebug;
import com.cnethuka.kegare.innatetechnique.InnateTechnique;
import com.cnethuka.kegare.innatetechnique.InnateTechniqueRarity;
import com.cnethuka.kegare.innatetechnique.InnateTechniqueService;
import com.cnethuka.kegare.innatetechnique.test.TestInnateTechniques;
import com.cnethuka.kegare.loadout.SharedAbilityService;
import com.cnethuka.kegare.malice.MaliceService;
import com.cnethuka.kegare.player.BirthService;
import com.cnethuka.kegare.progression.AssessmentService;
import com.cnethuka.kegare.progression.Grade;
import com.cnethuka.kegare.progression.GradeService;
import com.cnethuka.kegare.progression.Insight;
import com.cnethuka.kegare.progression.InsightCategory;
import com.cnethuka.kegare.progression.RefinementService;
import com.cnethuka.kegare.rmt.GreenOrbService;
import com.cnethuka.kegare.rmt.RmtIds;
import com.cnethuka.kegare.roll.RollService;
import com.cnethuka.kegare.trait.KegareTraits;
import com.cnethuka.kegare.trait.Trait;
import com.cnethuka.kegare.trait.TraitService;
import com.cnethuka.kegare.trait.TraitSuccession;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestion;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/** {@code /kegare}, through the real dispatcher: what it accepts, who may use it, who it defaults to and what it says. */
@ExtendWith(EphemeralTestServerProvider.class)
class KegareCommandTest {
    private static final int OP = 2;
    private static final Path SOURCES = Path.of(System.getProperty("kegare.projectDir", ".")).resolve("src/main/java");

    /** Whoever ran the command: everything it's told lands here. */
    private static final class Sender implements CommandSource {
        final List<Component> said = new ArrayList<>();

        @Override
        public void sendSystemMessage(Component message) {
            said.add(message);
        }

        @Override
        public boolean acceptsSuccess() {
            return true;
        }

        @Override
        public boolean acceptsFailure() {
            return true;
        }

        @Override
        public boolean shouldInformAdmins() {
            return false;
        }
    }

    private record Ran(int result, List<Component> said, @Nullable CommandSyntaxException thrown) {
        String text() {
            return said.stream().map(Component::getString).collect(Collectors.joining("\n"));
        }

        String error() {
            assertNotNull(thrown, "expected an error, but it said: " + text());
            return ComponentUtils.fromMessage(thrown.getRawMessage()).getString();
        }

        Ran ok() {
            if (thrown != null) throw new AssertionError("it threw: " + error());
            return this;
        }
    }

    private static CommandDispatcher<CommandSourceStack> dispatcher(MinecraftServer server) {
        return server.getCommands().getDispatcher();
    }

    private static CommandSourceStack source(MinecraftServer server, Sender out, @Nullable ServerPlayer sender, int level) {
        return new CommandSourceStack(out, Vec3.ZERO, Vec2.ZERO, server.overworld(), level, "tester", Component.literal("tester"), server, sender);
    }

    private static Ran run(MinecraftServer server, @Nullable ServerPlayer sender, int level, String command) {
        Sender out = new Sender();
        try {
            return new Ran(dispatcher(server).execute(command, source(server, out, sender, level)), out.said, null);
        } catch (CommandSyntaxException e) {
            return new Ran(0, out.said, e);
        }
    }

    /** As an operator with no player behind it, the way the console runs things. */
    private static Ran op(MinecraftServer server, String command) {
        return run(server, null, OP, command);
    }

    private static List<Suggestion> suggestions(MinecraftServer server, int level, String typed) throws Exception {
        CommandSourceStack source = source(server, new Sender(), null, level);
        return dispatcher(server).getCompletionSuggestions(dispatcher(server).parse(typed, source)).get().getList();
    }

    private static Set<String> usages(MinecraftServer server, int level) {
        var kegare = dispatcher(server).getRoot().getChild("kegare");
        return new TreeSet<>(List.of(dispatcher(server).getAllUsage(kegare, source(server, new Sender(), null, level), true)));
    }

    /** A real name the selectors can find, with no trait a birth might have rolled. */
    private static ServerPlayer online(MinecraftServer server, String name) {
        ServerPlayer player = TestServer.damageable(server, name);
        server.overworld().addNewPlayer(player);
        onlinePlayers(server).add(player);
        playersByUuid(server).put(player.getUUID(), player);
        BirthService.rebirth(player, OptionalInt.of(500));
        TraitService.removeAll(player, Trait.RemoveReason.ADMIN);
        return player;
    }

    /** The real list, since selectors read it and {@code getPlayers()} only hands out a read-only view. */
    @SuppressWarnings("unchecked")
    private static List<ServerPlayer> onlinePlayers(MinecraftServer server) {
        try {
            java.lang.reflect.Field players = net.minecraft.server.players.PlayerList.class.getDeclaredField("players");
            players.setAccessible(true);
            return (List<ServerPlayer>) players.get(server.getPlayerList());
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<java.util.UUID, ServerPlayer> playersByUuid(MinecraftServer server) {
        try {
            java.lang.reflect.Field players = net.minecraft.server.players.PlayerList.class.getDeclaredField("playersByUUID");
            players.setAccessible(true);
            return (Map<java.util.UUID, ServerPlayer>) players.get(server.getPlayerList());
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    @FunctionalInterface
    private interface Body {
        void run() throws Exception;
    }

    private static void inGame(MinecraftServer server, Body body) throws Exception {
        TestServer.onServerThread(server, () -> {
            TestServer.ensureOverworld(server);
            try {
                body.run();
            } catch (RuntimeException | Error e) {
                throw e;
            } catch (Exception e) {
                throw new IllegalStateException(e);
            } finally {
                onlinePlayers(server).clear();
                playersByUuid(server).clear();
                TestServer.removePlayers(server);
            }
        });
    }

    // ------------------------------------------------------------------ short names

    @Test
    void shortNamesResolve(MinecraftServer server) throws Exception {
        inGame(server, () -> {
            ServerPlayer p = online(server, "shorty");
            assertEquals("Set shorty's Innate Technique to Rime (" + rarity(Rime.RIME.get()) + ").", op(server, "kegare innate set shorty rime").ok().text());
            assertEquals(Rime.RIME.get(), InnateTechniqueService.current(p).orElseThrow());
            op(server, "kegare innate set shorty SWAP").ok();
            assertEquals(Swap.SWAP.get(), InnateTechniqueService.current(p).orElseThrow(), "case doesn't matter");
            op(server, "kegare innate set shorty puppet_manipulation").ok();
            assertEquals(PuppetManipulation.PUPPET_MANIPULATION.get(), InnateTechniqueService.current(p).orElseThrow());

            TraitSuccession.release(server, KegareTraits.SIX_EYES.get());
            op(server, "kegare trait give shorty six_eyes").ok();
            assertTrue(TraitService.has(p, KegareTraits.SIX_EYES.get()));
            op(server, "kegare trait remove shorty six_eyes").ok();
            assertFalse(TraitService.has(p, KegareTraits.SIX_EYES.get()));

            op(server, "kegare grade set shorty special_grade").ok();
            assertEquals(Grade.SPECIAL_GRADE, GradeService.grade(p));
            op(server, "kegare insight add shorty WARD 2").ok();
            assertEquals(2, Insight.get(p, InsightCategory.WARD));
            op(server, "kegare debug shared unlock shorty reverse_malice_technique").ok();
            assertTrue(SharedAbilityService.isUnlocked(p, RmtIds.ABILITY));
        });
    }

    @Test
    void testTechniquesFollowTheConfig(MinecraftServer server) throws Exception {
        inGame(server, () -> {
            ServerPlayer p = online(server, "tester_one");
            InnateTechnique kinetic = TestInnateTechniques.KINETIC.get();
            boolean before = KegareServerConfig.INCLUDE_TEST_INNATE_TECHNIQUES.get();
            try {
                KegareServerConfig.INCLUDE_TEST_INNATE_TECHNIQUES.set(true);
                op(server, "kegare innate set tester_one test_kinetic").ok();
                assertEquals(kinetic, InnateTechniqueService.current(p).orElseThrow());
                assertTrue(names(suggestions(server, OP, "kegare innate set tester_one ")).contains("test_kinetic"));
                assertTrue(op(server, "kegare innate list").ok().text().contains("test_kinetic"));

                KegareServerConfig.INCLUDE_TEST_INNATE_TECHNIQUES.set(false);
                assertTrue(op(server, "kegare innate set tester_one test_kinetic").error().startsWith("Unknown Innate Technique: test_kinetic."));
                assertTrue(op(server, "kegare debug reroll tester_one test_kinetic").error().startsWith("Unknown Innate Technique: test_kinetic."));
                assertFalse(names(suggestions(server, OP, "kegare innate set tester_one ")).contains("test_kinetic"));
                assertFalse(op(server, "kegare innate list").ok().text().contains("test_kinetic"));
                assertTrue(op(server, "kegare innate get tester_one").ok().text().contains(kinetic.displayName().getString()), "someone already holding one still shows it");
            } finally {
                KegareServerConfig.INCLUDE_TEST_INNATE_TECHNIQUES.set(before);
            }
        });
    }

    @Test
    void suggestionsAreShortNamesWithWhatTheyAreOnHover(MinecraftServer server) throws Exception {
        inGame(server, () -> {
            List<Suggestion> innates = suggestions(server, OP, "kegare innate set anyone ");
            assertTrue(names(innates).containsAll(List.of("rime", "swap", "puppet_manipulation")), () -> "got " + names(innates));
            assertTrue(names(innates).stream().noneMatch(name -> name.contains(":")), () -> "got " + names(innates));
            Component hover = (Component) named(innates, "rime").getTooltip();
            assertEquals("Rime (" + rarity(Rime.RIME.get()) + ")", hover.getString());
            assertEquals(Optional.of(Rime.RIME.get().rarity().color()), colorOf(hover, "Rime"), "in the rarity's color");

            assertEquals(List.of("rime"), names(suggestions(server, OP, "kegare innate set anyone ri")));
            assertEquals(List.of("puppet_manipulation", "time_manipulation"), names(suggestions(server, OP, "kegare debug reroll anyone manip")),
                    "it matches after an underscore too");

            List<Suggestion> traits = suggestions(server, OP, "kegare trait give anyone ");
            assertEquals(List.of("six_eyes"), names(traits));
            assertEquals("Six Eyes (Unique)", ((Component) named(traits, "six_eyes").getTooltip()).getString());
            assertEquals(List.of("six_eyes"), names(suggestions(server, OP, "kegare trait remove anyone ")));
            assertEquals(List.of("six_eyes"), names(suggestions(server, OP, "kegare trait release ")));
            assertEquals(List.of("six_eyes"), names(suggestions(server, OP, "kegare trait status ")));

            assertEquals("Special Grade", ((Component) named(suggestions(server, OP, "kegare grade set anyone "), "special_grade").getTooltip()).getString());
            assertEquals("Ward", ((Component) named(suggestions(server, OP, "kegare insight add anyone "), "ward").getTooltip()).getString());
            assertEquals("Reverse Malice Technique", ((Component) named(suggestions(server, OP, "kegare debug shared lock anyone "), "reverse_malice_technique").getTooltip()).getString());
        });
    }

    // ------------------------------------------------------------------ errors

    @Test
    void unknownNamesPointAtTheListCommand(MinecraftServer server) throws Exception {
        inGame(server, () -> {
            online(server, "typo");
            String innate = "Unknown Innate Technique: nope. Use /kegare innate list to see them all.";
            assertEquals(innate, op(server, "kegare innate set typo nope").error());
            assertEquals(innate, op(server, "kegare debug reroll typo nope").error());
            String trait = "Unknown trait: nope. Use /kegare trait list to see them all.";
            assertEquals(trait, op(server, "kegare trait give typo nope").error());
            assertEquals(trait, op(server, "kegare trait remove typo nope").error());
            assertEquals(trait, op(server, "kegare trait release nope").error());
            assertEquals(trait, op(server, "kegare trait status nope").error());
            assertEquals("Unknown Grade: nope. Use /kegare grade list to see them all.", op(server, "kegare grade set typo nope").error());
            assertEquals("Unknown Insight category: nope. Choose from dominion, ward, output.", op(server, "kegare insight add typo nope 1").error());
            assertEquals("Unknown shared ability: nope. Choose from reverse_malice_technique.", op(server, "kegare debug shared unlock typo nope").error());
            assertEquals("Unknown system: nope. Use /kegare help to see them all.", op(server, "kegare help nope").error());

            assertTrue(op(server, "kegare innate list").ok().text().contains("rime"), "and the list it points at has what to type");
            assertTrue(op(server, "kegare trait list").ok().text().contains("six_eyes"));
            assertTrue(op(server, "kegare grade list").ok().text().contains("semi_grade_2"));
        });
    }

    @Test
    void fullIdsAreRefusedAsUnknown(MinecraftServer server) throws Exception {
        inGame(server, () -> {
            ServerPlayer p = online(server, "full_id");
            InnateTechniqueService.setInnateTechnique(p, Swap.SWAP.get());
            assertEquals("Unknown Innate Technique: kegare:rime. Use /kegare innate list to see them all.", op(server, "kegare innate set full_id kegare:rime").error());
            assertEquals(Swap.SWAP.get(), InnateTechniqueService.current(p).orElseThrow());
            assertEquals("Unknown trait: kegare:six_eyes. Use /kegare trait list to see them all.", op(server, "kegare trait give full_id kegare:six_eyes").error());
            assertFalse(TraitService.has(p, KegareTraits.SIX_EYES.get()));
        });
    }

    @Test
    void aBadPlayerPointsAtList(MinecraftServer server) throws Exception {
        inGame(server, () -> {
            String expected = "No online player matches nobody. Use /list to see who's online.";
            assertEquals(expected, op(server, "kegare malice get nobody").error());
            assertEquals(expected, op(server, "kegare innate set nobody rime").error());
            assertEquals(expected, op(server, "kegare rebirth nobody").error());
            assertEquals("No online player matches @a. Use /list to see who's online.", op(server, "kegare malice get @a").error(), "a selector that finds nobody");
        });
    }

    // ------------------------------------------------------------------ who it's about

    @Test
    void thePlayerDefaultsToTheSender(MinecraftServer server) throws Exception {
        inGame(server, () -> {
            ServerPlayer sender = online(server, "the_sender");
            ServerPlayer other = online(server, "the_other");
            for (String command : List.of("info", "innate get", "innate reroll", "trait get", "malice get", "malice fill", "grade get", "assessment get",
                    "refinement get", "insight get", "loadout reset", "debug orb spawn", "debug orb status")) {
                Ran ran = run(server, sender, OP, "kegare " + command);
                assertTrue(ran.ok().text().contains("the_sender"), () -> command + " said: " + ran.text());
                assertFalse(ran.text().contains("the_other"), command);
            }
            MaliceService.setCurrent(sender, 0);
            MaliceService.setCurrent(other, 0);
            run(server, sender, OP, "kegare malice fill").ok();
            assertEquals(500.0, MaliceService.get(sender).current());
            assertEquals(0.0, MaliceService.get(other).current());
            assertTrue(run(server, sender, OP, "kegare malice get the_other").ok().text().startsWith("the_other has 0 / 500"), "naming someone else still works");
        });
    }

    @Test
    void theConsoleHasToNameAPlayer(MinecraftServer server) throws Exception {
        inGame(server, () -> {
            online(server, "named");
            assertEquals("A player is required to run this command here", op(server, "kegare malice get").error());
            assertEquals("A player is required to run this command here", op(server, "kegare info").error());
            assertTrue(op(server, "kegare malice get named").ok().text().startsWith("named has"));
        });
    }

    @Test
    void selectorsReachEveryone(MinecraftServer server) throws Exception {
        inGame(server, () -> {
            ServerPlayer a = online(server, "everyone_a");
            ServerPlayer b = online(server, "everyone_b");
            Ran ran = op(server, "kegare grade set @a grade_1").ok();
            assertEquals(2, ran.result());
            assertEquals(2, ran.said().size(), "a line for each");
            assertEquals(Grade.GRADE_1, GradeService.grade(a));
            assertEquals(Grade.GRADE_1, GradeService.grade(b));
            assertEquals(2, op(server, "kegare malice set @a 7").ok().result());
            assertEquals(7.0, MaliceService.get(b).current());
            assertEquals(2, op(server, "kegare innate set @a swap").ok().result());
            assertEquals(Swap.SWAP.get(), InnateTechniqueService.current(a).orElseThrow());
            assertEquals(Swap.SWAP.get(), InnateTechniqueService.current(b).orElseThrow());
            assertTrue(run(server, a, OP, "kegare grade get @s").ok().text().startsWith("everyone_a is"));
        });
    }

    // ------------------------------------------------------------------ who may use it

    @Test
    void onlyHelpAndTopAreOpenToEveryone(MinecraftServer server) throws Exception {
        inGame(server, () -> {
            Set<String> open = new TreeSet<>(List.of("help", "help <system>", "top <stat>"));
            assertEquals(open, usages(server, 0));
            assertEquals(open, usages(server, 1));
            assertTrue(usages(server, OP).size() > 40, "an operator gets the rest");

            ServerPlayer p = online(server, "plain_player");
            int capacity = MaliceService.get(p).capacity();
            for (String command : List.of("info", "innate list", "innate set plain_player rime", "trait get", "malice fill", "malice capacity plain_player 1000",
                    "rebirth plain_player", "loadout reset", "grade get", "assessment get", "refinement get", "insight get", "progression reset plain_player",
                    "debug stun plain_player 5", "debug orb spawn")) {
                Ran ran = run(server, p, 0, "kegare " + command);
                assertNotNull(ran.thrown(), command);
                assertEquals(CommandSyntaxException.BUILT_IN_EXCEPTIONS.dispatcherUnknownArgument(), ran.thrown().getType(), () -> command + " gave " + ran.error());
            }
            assertEquals(capacity, MaliceService.get(p).capacity());
            assertFalse(CombatHelper.isStunned(p));
            assertNull(run(server, p, 0, "kegare help").thrown());
        });
    }

    @Test
    void dbSaysSoWhenStorageIsOffAndWorksFromTheConsole(MinecraftServer server) throws Exception {
        inGame(server, () -> {
            assertEquals("Storage: off. Kegare's data is in the world's files.", run(server, null, OP, "kegare db status").ok().text());
            String off = "Kegare's storage is off on this server, so there's no database to do that with.";
            for (String command : List.of("kegare db migrate", "kegare db migrate dry", "kegare db export")) {
                Ran ran = run(server, null, OP, command).ok();
                assertEquals(off, ran.text(), command);
                assertEquals(0, ran.result(), command);
            }
            assertNotNull(run(server, null, 0, "kegare db status").thrown(), "ops only");
        });
    }

    @Test
    void topSaysSoWhenStorageIsOff(MinecraftServer server) throws Exception {
        inGame(server, () -> {
            assertEquals("Leaderboards need Kegare's database, and this server doesn't have it switched on.", run(server, null, 0, "kegare top pvp_kills").ok().text());
            assertEquals("There's no public stat called grade. There's pvp_kills, deaths, fights, damage_dealt, black_sparks, longest_chain.", run(server, null, 0, "kegare top grade").ok().text());
            assertEquals(List.of("black_sparks", "damage_dealt", "deaths", "fights", "longest_chain", "pvp_kills"), names(suggestions(server, 0, "kegare top ")));
        });
    }

    @Test
    void helpListsOnlyWhatTheSenderCanRun(MinecraftServer server) throws Exception {
        inGame(server, () -> {
            String plain = run(server, null, 0, "kegare help").ok().text();
            assertTrue(plain.contains("/kegare help"), plain);
            for (String system : List.of("info", "innate", "trait", "malice", "rebirth", "loadout", "grade", "assessment", "refinement", "insight", "progression", "debug")) {
                assertFalse(plain.contains("/kegare " + system), () -> plain);
                assertTrue(op(server, "kegare help").ok().text().contains("/kegare " + system), system);
                assertEquals("Unknown system: " + system + ". Use /kegare help to see them all.", run(server, null, 0, "kegare help " + system).error());
            }
            assertEquals(List.of("help", "top"), names(suggestions(server, 0, "kegare help ")));
            assertTrue(names(suggestions(server, OP, "kegare help ")).containsAll(List.of("innate", "debug", "progression")));

            String innate = op(server, "kegare help innate").ok().text();
            assertTrue(innate.contains("/kegare innate set <player> <innate>: Give a player an Innate Technique without rolling."), innate);
            assertFalse(innate.contains("/kegare trait"), innate);
            assertTrue(op(server, "kegare help").ok().text().contains("/kegare innate: list, get, set, reroll"));
            assertTrue(op(server, "kegare help").ok().text().contains("/kegare rebirth: <player> [capacity]"), "a system of one shows its arguments");
            assertTrue(op(server, "kegare help debug").ok().text().contains("/kegare debug fx export"), "the Fx tool, in a dev environment like this one");
        });
    }

    /** Help is written by hand, so this is what keeps it true: every command is in it, and everything in it is a command. */
    @Test
    void helpMatchesTheCommandTree(MinecraftServer server) throws Exception {
        inGame(server, () -> {
            Set<String> written = new TreeSet<>();
            for (HelpCommand.Entry entry : HelpCommand.ENTRIES) {
                if (entry.devClient()) continue; // in the dev client's own tree
                String required = entry.usage().replaceAll(" \\[[^]]*]", "");
                written.add(required);
                if (!required.equals(entry.usage())) written.add(entry.usage().replace('[', '<').replace(']', '>'));
            }
            // "innate reroll [player]" is both "innate reroll" and "innate reroll <player>".
            assertEquals(written, usages(server, OP));
        });
    }

    @Test
    void cooldownResetClearsEverythingOrOneAndTakesSelectors(MinecraftServer server) throws Exception {
        inGame(server, () -> {
            ServerPlayer a = online(server, "cd_a"), b = online(server, "cd_b");
            com.cnethuka.kegare.innatetechnique.Ability rmt = com.cnethuka.kegare.loadout.SharedAbilities.find(com.cnethuka.kegare.rmt.RmtIds.ABILITY).orElseThrow();
            List<com.cnethuka.kegare.innatetechnique.Ability> abilities = List.of(
                    com.cnethuka.kegare.innate_technique.time_manipulation.TimeManipulation.POINT_MAKER,
                    com.cnethuka.kegare.innate_technique.time_manipulation.TimeManipulation.ACCELERATE,
                    com.cnethuka.kegare.innate_technique.time_manipulation.TimeManipulation.STANDSTILL, rmt);
            for (ServerPlayer p : List.of(a, b)) {
                com.cnethuka.kegare.innatetechnique.InnateTechniqueService.setInnateTechnique(p,
                        com.cnethuka.kegare.innate_technique.time_manipulation.TimeManipulation.TIME_MANIPULATION.get());
                for (var ability : abilities) com.cnethuka.kegare.innatetechnique.InnateTechniqueService.startCooldown(p, ability);
                com.cnethuka.kegare.innatetechnique.Burnout.apply(p, 400);
                com.cnethuka.kegare.melee.MeleeIntentPayload.apply(p, new com.cnethuka.kegare.melee.MeleeIntentPayload(
                        com.cnethuka.kegare.melee.MeleeIntentPayload.Action.DASH, (byte) 1, (byte) 0));
                assertTrue(com.cnethuka.kegare.melee.MeleeLayer.dashCooldownLeft(p) > 0, "setup: Dash cooling down");
            }

            // One ability, named: only that one. Its special's name clears the cooldown they share.
            op(server, "kegare cooldown reset cd_a rewind").ok();
            assertEquals(0, com.cnethuka.kegare.innatetechnique.InnateTechniqueService.cooldownRemaining(a, abilities.get(0)), "Point Maker, through Rewind");
            assertTrue(com.cnethuka.kegare.innatetechnique.InnateTechniqueService.cooldownRemaining(a, abilities.get(1)) > 0, "the rest still running");
            assertTrue(com.cnethuka.kegare.innatetechnique.Burnout.isBurntOut(a), "and Burnout with them");
            op(server, "kegare cooldown reset cd_a dash").ok();
            assertEquals(0, com.cnethuka.kegare.melee.MeleeLayer.dashCooldownLeft(a), "Dash by name");
            assertTrue(op(server, "kegare cooldown reset cd_a nothing_like_it").error().contains("nothing_like_it"), "an unknown name says so");

            // Everything, for everyone a selector finds.
            Ran ran = op(server, "kegare cooldown reset @a").ok();
            assertFalse(ran.text().contains("commands.kegare"), ran.text());
            for (ServerPlayer p : List.of(a, b)) {
                for (var ability : abilities) {
                    assertEquals(0, com.cnethuka.kegare.innatetechnique.InnateTechniqueService.cooldownRemaining(p, ability), p.getScoreboardName() + ": " + ability.id());
                }
                assertFalse(com.cnethuka.kegare.innatetechnique.Burnout.isBurntOut(p), "Burnout over");
                assertEquals(0, com.cnethuka.kegare.melee.MeleeLayer.dashCooldownLeft(p), "Dash ready");
            }

            // Ops only.
            assertNotNull(run(server, a, 0, "kegare cooldown reset").thrown(), "not for someone without permission");
        });
    }

    @Test
    void rmtDebugCommands(MinecraftServer server) throws Exception {
        inGame(server, () -> {
            ServerPlayer a = online(server, "rmt_cmd_a"), b = online(server, "rmt_cmd_b");
            op(server, "kegare debug rmt fatigue rmt_cmd_a 0.3").ok();
            assertEquals(0.3, com.cnethuka.kegare.rmt.RmtFatigue.of(a), 1e-9, "fatigue set");
            op(server, "kegare debug rmt others unlock rmt_cmd_a").ok();
            assertTrue(com.cnethuka.kegare.progression.Unlocks.has(a, com.cnethuka.kegare.rmt.RmtIds.ON_ANOTHER_UNLOCK), "taught");
            assertTrue(op(server, "kegare debug rmt others unlock rmt_cmd_a").text().contains("already"), "already knows");
            op(server, "kegare debug rmt others lock rmt_cmd_a").ok();
            assertFalse(com.cnethuka.kegare.progression.Unlocks.has(a, com.cnethuka.kegare.rmt.RmtIds.ON_ANOTHER_UNLOCK), "taken away");
            String moment = op(server, "kegare debug rmt moment rmt_cmd_a rmt_cmd_b").ok().text();
            assertTrue(moment.contains("rmt_cmd_b glows for rmt_cmd_a"), moment);
            assertTrue(op(server, "kegare debug rmt moment rmt_cmd_a rmt_cmd_a").text().contains("themself"), "not on yourself");
            assertNotNull(b);
        });
    }

    // ------------------------------------------------------------------ the old forms

    @Test
    void theOldFormsAreGone(MinecraftServer server) throws Exception {
        inGame(server, () -> {
            ServerPlayer p = online(server, "old_times");
            for (String old : List.of("setmalice old_times 5", "setcapacity old_times 5", "setinnatetechnique old_times rime", "setinnatetechnique old_times kegare:rime",
                    "reroll old_times", "reroll old_times kegare:rime", "setpity old_times 1", "stun old_times 1", "orb spawn old_times", "orb status old_times",
                    "shared unlock old_times kegare:reverse_malice_technique", "shared lock old_times kegare:reverse_malice_technique",
                    "swap unswappable old_times true", "strings unmovable old_times true", "trait add old_times kegare:six_eyes",
                    "grade addxp old_times 1", "assessment addfight old_times 50 someone")) {
                Ran ran = op(server, "kegare " + old);
                assertNotNull(ran.thrown(), old);
                assertEquals(CommandSyntaxException.BUILT_IN_EXCEPTIONS.dispatcherUnknownArgument(), ran.thrown().getType(), () -> old + " gave " + ran.error());
            }
            assertEquals(500, MaliceService.get(p).capacity(), "and none of them did anything");
            assertNull(dispatcher(server).getRoot().getChild("kegarefx"));
            for (String gone : List.of("setmalice", "setcapacity", "setinnatetechnique", "reroll", "setpity", "stun", "orb", "shared", "swap", "strings")) {
                assertNull(dispatcher(server).getRoot().getChild("kegare").getChild(gone), gone);
            }
        });
    }

    /** The dev client's tree can't be built on a server, so this reads the source. */
    @Test
    void theFxCommandMovedToo() throws IOException {
        String fx = Files.readString(SOURCES.resolve("com/cnethuka/kegare/client/visual/FxDevCommand.java"));
        assertFalse(fx.contains("Commands.literal(\"kegarefx\")"));
        assertTrue(fx.contains("Commands.literal(\"kegare\")"));
        assertTrue(fx.contains("Commands.literal(\"debug\")"));
        assertTrue(fx.contains("Commands.literal(\"fx\")"));
        assertTrue(fx.contains("source.hasPermission(2)"), "and it takes the same permission as the rest");
    }

    // ------------------------------------------------------------------ what it says

    @Test
    void itSpeaksInSentencesWithDisplayNames(MinecraftServer server) throws Exception {
        inGame(server, () -> {
            ServerPlayer p = online(server, "reader");
            TraitSuccession.release(server, KegareTraits.SIX_EYES.get());
            InnateTechniqueService.setInnateTechnique(p, Rime.RIME.get());
            String rime = "Rime (" + rarity(Rime.RIME.get()) + ")";
            assertEquals("reader has " + rime + ".", op(server, "kegare innate get reader").ok().text());
            assertEquals("Gave Six Eyes to reader.", op(server, "kegare trait give reader six_eyes").ok().text());
            assertEquals("reader's traits: Six Eyes (Unique).", op(server, "kegare trait get reader").ok().text());
            assertEquals("Removed Six Eyes from reader.", op(server, "kegare trait remove reader six_eyes").ok().text());
            assertEquals("reader has no traits.", op(server, "kegare trait get reader").ok().text());
            op(server, "kegare malice capacity reader 500").ok();
            MaliceService.setCurrent(p, 120.9);
            assertTrue(op(server, "kegare malice get reader").ok().text().startsWith("reader has 120 / 500 Malice ("), "whole numbers, floored like the HUD");
            assertEquals("Set reader's Malice to 40 / 500.", op(server, "kegare malice set reader 40").ok().text());
            assertEquals("Filled reader's Malice to 500 / 500.", op(server, "kegare malice fill reader").ok().text());
            assertEquals("Stunned reader for 1.5s.", op(server, "kegare debug stun reader 1.5").ok().text());
            assertEquals("Cleared reader's stun.", op(server, "kegare debug stun reader 0").ok().text());
            assertEquals("reader was reborn.", op(server, "kegare rebirth reader").ok().text());
        });
    }

    @Test
    void rarityAndTierNamesAreInTheirColors(MinecraftServer server) throws Exception {
        inGame(server, () -> {
            ServerPlayer p = online(server, "colorful");
            InnateTechniqueService.setInnateTechnique(p, Rime.RIME.get());
            String rarity = rarity(Rime.RIME.get());
            Component line = op(server, "kegare innate get colorful").ok().said().get(0);
            assertEquals(Optional.of(Rime.RIME.get().rarity().color()), colorOf(line, rarity));
            for (InnateTechniqueRarity each : InnateTechniqueRarity.values()) {
                assertEquals(Optional.of(each.color()), colorOf(CommandArgs.rarityName(each), CommandArgs.rarityName(each).getString()));
            }

            assertEquals(Optional.of(GuiDraw.GOLD & 0xFFFFFF), colorOf(capacityLine(server, 1_000_000), "Vast"), "gold, as in the reveal");
            assertEquals(Optional.of(GuiDraw.MALICE & 0xFFFFFF), colorOf(capacityLine(server, 100), "Low"), "as on the Sorcerer screen");
            assertEquals(Optional.of(GuiDraw.RED & 0xFFFFFF), colorOf(capacityLine(server, 0), "Zero"));
        });
    }

    private static Component capacityLine(MinecraftServer server, int capacity) {
        return op(server, "kegare malice capacity colorful " + capacity).ok().said().get(0);
    }

    @Test
    void noRawIdsAndNoMissingText(MinecraftServer server) throws Exception {
        inGame(server, () -> {
            ServerPlayer p = online(server, "plain_text");
            TraitSuccession.release(server, KegareTraits.SIX_EYES.get());
            TestServer.advanceGameTime(server, KegareServerConfig.ticks(KegareServerConfig.COMBAT_TAG_SECONDS) + 1);
            List<String> commands = new ArrayList<>(List.of("help", "info plain_text", "innate list", "innate get plain_text", "innate set plain_text rime", "innate reroll plain_text",
                    "trait list", "trait give plain_text six_eyes", "trait get plain_text", "info plain_text", "trait status six_eyes", "trait release six_eyes", "trait status six_eyes", "trait get plain_text",
                    "malice get plain_text", "malice set plain_text 5", "malice fill plain_text", "malice capacity plain_text 900",
                    "grade list", "grade get plain_text", "grade set plain_text grade_2", "grade add plain_text 1", "assessment get plain_text", "assessment add plain_text 60 someone",
                    "refinement get plain_text", "refinement set plain_text 2", "insight get plain_text", "insight add plain_text dominion 1",
                    "loadout reset plain_text", "cooldown reset plain_text", "cooldown reset plain_text dash", "debug stun plain_text 1", "debug pity plain_text 3", "debug reroll plain_text swap",
                    "debug orb spawn plain_text", "debug orb status plain_text", "debug shared unlock plain_text reverse_malice_technique", "debug shared lock plain_text reverse_malice_technique",
                    "debug swap unswappable plain_text true", "debug swap unswappable plain_text false", "debug rmt fatigue plain_text 0.3", "debug rmt others unlock plain_text", "debug rmt others lock plain_text", "debug strings unmovable plain_text true", "debug strings unmovable plain_text false",
                    "grade set plain_text special_grade", "grade get plain_text", "progression reset plain_text", "rebirth plain_text 0", "malice get plain_text", "innate get plain_text", "info plain_text"));
            for (HelpCommand.Entry entry : HelpCommand.ENTRIES) commands.add("help " + entry.system());
            for (String command : commands) {
                Ran ran = op(server, "kegare " + command);
                String text = ran.thrown() != null ? ran.error() : ran.text();
                assertFalse(text.isBlank(), () -> command + " said nothing");
                assertFalse(text.contains("kegare:"), () -> command + " said: " + text);
                assertFalse(text.contains("commands.kegare") || text.contains("grade.kegare") || text.contains("malice.kegare") || text.contains("trait.kegare"), () -> command + " said: " + text);
            }
            assertNotNull(p);
        });
    }

    /** Someone gone long enough to leave the name cache leaves only a UUID behind. */
    @Test
    void aHolderWithNoKnownNameIsntShownAsAUuid(MinecraftServer server) throws Exception {
        inGame(server, () -> {
            ServerPlayer gone = online(server, "long_gone");
            ServerPlayer other = online(server, "newcomer");
            TraitSuccession.release(server, KegareTraits.SIX_EYES.get());
            op(server, "kegare trait give long_gone six_eyes").ok();
            playersByUuid(server).remove(gone.getUUID());
            String unnamed = "a player this server has no name for";
            assertEquals("Six Eyes is held by " + unnamed + ".", op(server, "kegare trait status six_eyes").ok().text());
            assertEquals("Six Eyes is unique and already held by " + unnamed + ".", op(server, "kegare trait give newcomer six_eyes").text());
            assertFalse(TraitService.has(other, KegareTraits.SIX_EYES.get()));
            assertTrue(op(server, "kegare trait release six_eyes").ok().text().contains("from " + unnamed));
        });
    }

    /** {@code sendSuccess(..., true)} would tell every other operator too. */
    @Test
    void feedbackGoesToTheSenderAlone() throws IOException {
        List<String> found = new ArrayList<>();
        try (Stream<Path> files = Files.walk(SOURCES.resolve("com/cnethuka/kegare/command"))) {
            for (Path file : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                String code = Files.readString(file);
                for (int at = code.indexOf("sendSuccess("); at >= 0; at = code.indexOf("sendSuccess(", at + 1)) {
                    found.add(code.substring(at, code.indexOf(';', at)));
                }
            }
        }
        assertEquals(List.of("sendSuccess(() -> message, false)"), found, "everything says it through CommandArgs.say");
    }

    // ------------------------------------------------------------------ nothing was lost

    @Test
    void whatMovedStillDoesItsJob(MinecraftServer server) throws Exception {
        inGame(server, () -> {
            ServerPlayer p = online(server, "mover");
            TraitSuccession.release(server, KegareTraits.SIX_EYES.get());
            TestServer.advanceGameTime(server, KegareServerConfig.ticks(KegareServerConfig.COMBAT_TAG_SECONDS) + 1);

            op(server, "kegare debug stun mover 3").ok();
            assertEquals(60, CombatHelper.stunTicksRemaining(p));
            op(server, "kegare debug stun mover 0").ok();
            assertFalse(CombatHelper.isStunned(p));

            op(server, "kegare debug pity mover 7").ok();
            assertEquals(7, RollService.get(p).rollsSinceLegendary());
            InnateTechniqueService.setInnateTechnique(p, Rime.RIME.get()); // a birth might have landed on the one forced next
            int totalRolls = RollService.get(p).totalRolls();
            op(server, "kegare debug reroll mover swap").ok();
            assertEquals(Swap.SWAP.get(), InnateTechniqueService.current(p).orElseThrow(), "the forced result");
            assertEquals(totalRolls + 1, RollService.get(p).totalRolls(), "through the roll pipeline");
            op(server, "kegare innate reroll mover").ok();
            assertEquals(totalRolls + 2, RollService.get(p).totalRolls());

            op(server, "kegare debug orb spawn mover").ok();
            assertTrue(GreenOrbService.state(p).hasOrb());
            assertTrue(op(server, "kegare debug orb status mover").ok().text().contains("orb waiting: yes (0/"));
            op(server, "kegare debug shared unlock mover reverse_malice_technique").ok();
            assertTrue(SharedAbilityService.isUnlocked(p, RmtIds.ABILITY));
            assertEquals("mover has already unlocked Reverse Malice Technique, so the orb never comes again.", op(server, "kegare debug orb spawn mover").text());
            assertEquals("mover already has Reverse Malice Technique.", op(server, "kegare debug shared unlock mover reverse_malice_technique").text());
            op(server, "kegare debug shared lock mover reverse_malice_technique").ok();
            assertFalse(SharedAbilityService.isUnlocked(p, RmtIds.ABILITY));

            op(server, "kegare debug swap unswappable mover true").ok();
            assertTrue(SwapDebug.isUnswappable(p.getUUID()));
            op(server, "kegare debug swap unswappable mover false").ok();
            assertFalse(SwapDebug.isUnswappable(p.getUUID()));
            assertEquals("mover can't be moved by strings now. This cancels StringsTargetEvent.", op(server, "kegare debug strings unmovable mover true").ok().text());
            assertEquals("mover can be moved by strings again.", op(server, "kegare debug strings unmovable mover false").ok().text());

            op(server, "kegare trait give mover six_eyes").ok();
            assertEquals("Six Eyes is held by mover.", op(server, "kegare trait status six_eyes").ok().text());
            op(server, "kegare trait release six_eyes").ok();
            assertFalse(TraitService.has(p, KegareTraits.SIX_EYES.get()));

            op(server, "kegare malice set mover 12").ok();
            assertEquals(12.0, MaliceService.get(p).current());
            op(server, "kegare malice capacity mover 800").ok();
            assertEquals(800, MaliceService.get(p).capacity());

            op(server, "kegare refinement set mover 3").ok();
            assertEquals(3, RefinementService.level(p));
            op(server, "kegare grade set mover grade_4").ok();
            double before = GradeService.get(p).xp();
            op(server, "kegare grade add mover 2").ok();
            assertEquals(before + 2, GradeService.get(p).xp(), 1e-6);
            op(server, "kegare assessment add mover 60 someone").ok();
            op(server, "kegare assessment add mover 70 someone 35").ok();
            var fights = AssessmentService.recent(p, 2);
            assertEquals(60.0, fights.get(0).technique(), 1e-6, "technique use defaults to the score");
            assertEquals(35.0, fights.get(1).technique(), 1e-6);
            int outputBefore = Insight.get(p, InsightCategory.OUTPUT); // unlocking RMT above paid some (unlocked_rmt)
            op(server, "kegare insight add mover output 4").ok();
            assertEquals(outputBefore + 4, Insight.get(p, InsightCategory.OUTPUT));
            assertEquals(1, op(server, "kegare loadout reset mover").ok().result());
            op(server, "kegare progression reset mover").ok();
            assertEquals(Grade.GRADE_4, GradeService.grade(p));
            assertEquals(0, RefinementService.level(p));
            assertEquals(0, Insight.get(p, InsightCategory.OUTPUT));

            op(server, "kegare rebirth mover 600").ok();
            assertEquals(600, MaliceService.get(p).capacity());
            op(server, "kegare rebirth mover 0").ok();
            assertTrue(MaliceService.isSevered(p));
            assertEquals("mover is Severed and can't hold an Innate Technique.", op(server, "kegare innate set mover rime").text());
            assertEquals("mover is Severed and has no Malice.", op(server, "kegare malice get mover").ok().text());
            assertEquals("mover is Severed, so the orb never comes to them.", op(server, "kegare debug orb spawn mover").text());
        });
    }

    // ------------------------------------------------------------------ helpers

    private static String rarity(InnateTechnique technique) {
        return Component.translatable(technique.rarity().translationKey()).getString();
    }

    private static List<String> names(List<Suggestion> suggestions) {
        return suggestions.stream().map(Suggestion::getText).toList();
    }

    private static Suggestion named(List<Suggestion> suggestions, String name) {
        return suggestions.stream().filter(s -> s.getText().equals(name)).findFirst().orElseThrow(() -> new AssertionError(name + " isn't in " + names(suggestions)));
    }

    /** The color a piece of text is drawn in, with styles inherited as the game inherits them. */
    private static Optional<Integer> colorOf(Component component, String text) {
        Map<String, Integer> colors = new HashMap<>();
        component.visit((style, part) -> {
            if (style.getColor() != null) colors.merge(part, style.getColor().getValue(), (a, b) -> a);
            return Optional.empty();
        }, net.minecraft.network.chat.Style.EMPTY);
        return Optional.ofNullable(colors.get(text));
    }
}

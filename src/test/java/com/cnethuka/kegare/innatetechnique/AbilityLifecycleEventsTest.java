package com.cnethuka.kegare.innatetechnique;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cnethuka.kegare.TestServer;
import com.cnethuka.kegare.api.ability.AbilityEndEvent;
import com.cnethuka.kegare.api.ability.AbilityExecuteEvent;
import com.cnethuka.kegare.api.ability.AbilityLifecycleEvent;
import com.cnethuka.kegare.api.ability.AbilityTickEvent;
import com.cnethuka.kegare.innate_technique.rime.Rime;
import com.cnethuka.kegare.loadout.LoadoutResult;
import com.cnethuka.kegare.loadout.LoadoutService;
import com.cnethuka.kegare.loadout.LoadoutService.Action;
import com.cnethuka.kegare.loadout.SharedAbilities;
import com.cnethuka.kegare.loadout.SharedAbilityService;
import com.cnethuka.kegare.malice.MaliceService;
import com.cnethuka.kegare.player.BirthService;
import com.cnethuka.kegare.progression.CastScope;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(EphemeralTestServerProvider.class)
class AbilityLifecycleEventsTest {
    private static final List<ResourceLocation> INSTALLED = new ArrayList<>();

    @AfterEach
    void cleanUp(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            uninstallFixtures();
            TestServer.removePlayers(server);
        });
    }

    @Test
    void executeEventsSurroundSuccessInsideCastScope(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            FixtureAbility ability = install(new FixtureAbility("instant", CastType.INSTANT));
            ServerPlayer player = player(server, "lifecycle_execute", ability);
            Recorder recorder = new Recorder(ability);
            NeoForge.EVENT_BUS.register(recorder);
            try {
                double before = MaliceService.get(player).current();
                CastManager.handleIntent(player, CastIntent.CAST_PRESS);
                assertEquals(List.of("beforeExecute", "execute", "afterExecute"), recorder.events);
                assertTrue(MaliceService.get(player).current() < before, "the activation cost is preserved");
                assertTrue(InnateTechniqueService.cooldownRemaining(player, ability) > 0);
            } finally {
                NeoForge.EVENT_BUS.unregister(recorder);
                CastManager.stopAll(player, EndReason.CANCELLED);
            }
        });
    }

    @Test
    void fizzleKeepsItsCostButHasNoSuccessfulExecuteEvent(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            FixtureAbility ability = install(new FixtureAbility("fizzle", CastType.INSTANT));
            ability.fizzle = true;
            ServerPlayer player = player(server, "lifecycle_fizzle", ability);
            Recorder recorder = new Recorder(ability);
            NeoForge.EVENT_BUS.register(recorder);
            try {
                double before = MaliceService.get(player).current();
                CastManager.handleIntent(player, CastIntent.CAST_PRESS);
                assertEquals(List.of("beforeExecute", "execute"), recorder.events);
                assertEquals(before - MaliceService.castCostOf(player, FixtureAbility.COST),
                        MaliceService.get(player).current(), 1.0e-6, "fizzleKeepingCost still spends Malice");
                assertEquals(0, InnateTechniqueService.cooldownRemaining(player, ability));
            } finally {
                NeoForge.EVENT_BUS.unregister(recorder);
            }
        });
    }

    @Test
    void tickAndEndEventsFollowTheirAbilityHooks(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            FixtureAbility ability = install(new FixtureAbility("toggle", CastType.TOGGLE));
            ServerPlayer player = player(server, "lifecycle_toggle", ability);
            Recorder recorder = new Recorder(ability);
            NeoForge.EVENT_BUS.register(recorder);
            try {
                CastManager.handleIntent(player, CastIntent.CAST_PRESS);
                CastManager.tick(player);
                CastManager.handleIntent(player, CastIntent.CAST_PRESS);
                assertEquals(List.of("beforeExecute", "execute", "afterExecute", "tick", "afterTick", "end:RELEASED", "afterEnd:RELEASED"),
                        recorder.events);
                assertEquals(List.of(EndReason.RELEASED), recorder.reasons);
                assertEquals(0, Recorder.unscoped, "every addon hook ran inside the ability's CastScope");
            } finally {
                NeoForge.EVENT_BUS.unregister(recorder);
                CastManager.stopAll(player, EndReason.CANCELLED);
            }
        });
    }

    @Test
    void earlyChargeReleaseEndsWithoutExecuteEventsOrCost(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            FixtureAbility ability = install(new FixtureAbility("charged", CastType.CHARGED));
            ServerPlayer player = player(server, "lifecycle_charge", ability);
            Recorder recorder = new Recorder(ability);
            NeoForge.EVENT_BUS.register(recorder);
            try {
                double before = MaliceService.get(player).current();
                CastManager.handleIntent(player, CastIntent.CAST_PRESS);
                CastManager.handleIntent(player, CastIntent.CAST_RELEASE);
                assertEquals(List.of("end:CANCELLED", "afterEnd:CANCELLED"), recorder.events);
                assertEquals(before, MaliceService.get(player).current(), 1.0e-6);
                assertEquals(0, InnateTechniqueService.cooldownRemaining(player, ability));
            } finally {
                NeoForge.EVENT_BUS.unregister(recorder);
                CastManager.stopAll(player, EndReason.CANCELLED);
            }
        });
    }

    private static ServerPlayer player(MinecraftServer server, String name, Ability ability) {
        TestServer.ensureOverworld(server);
        ServerPlayer player = TestServer.player(server, name);
        BirthService.rebirth(player, OptionalInt.of(5000));
        InnateTechniqueService.setInnateTechnique(player, Rime.RIME.get());
        SharedAbilityService.unlock(player, ability);
        assertEquals(LoadoutResult.OK, LoadoutService.edit(player, Action.SET, 8, 0, Optional.of(ability.id())));
        InnateTechniqueService.select(player, 8);
        MaliceService.refill(player);
        return player;
    }

    private static FixtureAbility install(FixtureAbility ability) {
        SharedAbilities.register(ability);
        INSTALLED.add(ability.id());
        return ability;
    }

    @SuppressWarnings("unchecked")
    private static void uninstallFixtures() {
        if (INSTALLED.isEmpty()) return;
        try {
            Field field = SharedAbilities.class.getDeclaredField("ALL");
            field.setAccessible(true);
            Map<ResourceLocation, Ability> all = (Map<ResourceLocation, Ability>) field.get(null);
            INSTALLED.forEach(all::remove);
            INSTALLED.clear();
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static final class Recorder {
        static int unscoped;
        private final Ability watched;
        final List<String> events = new ArrayList<>();
        final List<EndReason> reasons = new ArrayList<>();

        Recorder(Ability watched) {
            this.watched = watched;
            unscoped = 0;
            if (watched instanceof FixtureAbility fixture) fixture.events = events;
        }

        private void record(String event, AbilityLifecycleEvent lifecycle) {
            if (lifecycle.getContext().ability() != watched) return;
            events.add(event);
            if (CastScope.current(lifecycle.getContext().player()) != watched || CastScope.ability() != watched) unscoped++;
            assertSame(lifecycle.getContext().ability(), CastScope.current(lifecycle.getContext().player()));
        }

        @SubscribeEvent public void beforeExecute(AbilityExecuteEvent.Pre event) { record("beforeExecute", event); }
        @SubscribeEvent public void afterExecute(AbilityExecuteEvent.Post event) { record("afterExecute", event); }
        @SubscribeEvent public void afterTick(AbilityTickEvent.Post event) { record("afterTick", event); }
        @SubscribeEvent public void afterEnd(AbilityEndEvent.Post event) {
            if (event.getContext().ability() != watched) return;
            reasons.add(event.getReason());
            record("afterEnd:" + event.getReason(), event);
        }
    }

    private static final class FixtureAbility extends BaseAbility {
        static final double COST = 13;
        private List<String> events = new ArrayList<>();
        private boolean fizzle;

        FixtureAbility(String path, CastType castType) {
            super(ResourceLocation.fromNamespaceAndPath("lifecycle_test", path), castType);
        }

        @Override public double cost(ServerPlayer player) { return COST; }
        @Override public int cooldownTicks(ServerPlayer player) { return 40; }
        @Override public int minChargeTicks(ServerPlayer player) { return 5; }
        @Override public void execute(AbilityContext ctx) {
            events.add("execute");
            if (fizzle) CastManager.fizzleKeepingCost(ctx.player());
        }
        @Override public void tick(AbilityContext ctx) { events.add("tick"); }
        @Override public void onEnd(AbilityContext ctx, EndReason reason) { events.add("end:" + reason); }
    }
}

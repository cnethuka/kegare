package com.cnethuka.kegare.loadout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cnethuka.kegare.TestServer;
import com.cnethuka.kegare.combat.CombatHelper;
import com.cnethuka.kegare.config.KegareServerConfig;
import com.cnethuka.kegare.innate_technique.rime.Rime;
import com.cnethuka.kegare.innatetechnique.CastIntent;
import com.cnethuka.kegare.innatetechnique.CastManager;
import com.cnethuka.kegare.innatetechnique.EndReason;
import com.cnethuka.kegare.innatetechnique.InnateTechniqueService;
import com.cnethuka.kegare.innatetechnique.test.TestAbilities;
import com.cnethuka.kegare.innatetechnique.test.TestInnateTechniques;
import com.cnethuka.kegare.malice.MaliceService;
import com.cnethuka.kegare.player.BirthService;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/** Loadout rules on the real server code: default layout, moves, empties, refusals, toggles, reroll reset. */
@ExtendWith(EphemeralTestServerProvider.class)
class LoadoutTest {
    @Test
    void loadoutRules(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            TestServer.ensureOverworld(server);
            ServerPlayer p = TestServer.player(server, "loadout");
            BirthService.rebirth(p, OptionalInt.of(500));
            InnateTechniqueService.setInnateTechnique(p, Rime.RIME.get());
            MaliceService.refill(p);
            clearCombat(server, p);

            // Default: nine slots, the innate technique's abilities first in order, then empty; the ultimate is not one.
            assertEquals(bar(id(Rime.FROST_SHARD), id(Rime.GLACIAL_LANCE), id(Rime.FROST_FIELD), id(Rime.RIME_SHELL)),
                    LoadoutService.slots(p));

            // Placing an ability that's already slotted moves it (never a duplicate).
            assertEquals(LoadoutResult.OK, edit(p, LoadoutService.Action.SET, 0, 0, Rime.GLACIAL_LANCE.id()));
            assertEquals(bar(id(Rime.GLACIAL_LANCE), id(Rime.FROST_SHARD), id(Rime.FROST_FIELD), id(Rime.RIME_SHELL)),
                    LoadoutService.slots(p));
            // The empty slots past the technique's abilities take abilities too.
            assertEquals(LoadoutResult.OK, edit(p, LoadoutService.Action.SET, 8, 0, Rime.FROST_FIELD.id()));
            assertEquals(Optional.empty(), LoadoutService.slots(p).get(2), "moved from slot 3");
            assertSame(Rime.FROST_FIELD, LoadoutService.abilityAt(p, 8));
            assertEquals(LoadoutResult.OK, edit(p, LoadoutService.Action.SWAP, 8, 2, null));
            assertSame(Rime.FROST_FIELD, LoadoutService.abilityAt(p, 2), "and back");
            assertEquals(LoadoutResult.OK, edit(p, LoadoutService.Action.SWAP, 0, 1, null));
            assertEquals(id(Rime.FROST_SHARD), LoadoutService.slots(p).get(0));

            // An emptied slot casts nothing (and costs nothing); the ability can be slotted back later.
            assertEquals(LoadoutResult.OK, edit(p, LoadoutService.Action.CLEAR, 2, 0, null));
            assertEquals(Optional.empty(), LoadoutService.slots(p).get(2));
            InnateTechniqueService.select(p, 2);
            double before = MaliceService.get(p).current();
            CastManager.handleIntent(p, CastIntent.CAST_PRESS);
            assertNull(CastManager.state(p).casting(), "an empty slot casts nothing");
            assertEquals(before, MaliceService.get(p).current(), 1e-6);
            assertEquals(LoadoutResult.OK, edit(p, LoadoutService.Action.SET, 2, 0, Rime.FROST_FIELD.id()));
            assertSame(Rime.FROST_FIELD, LoadoutService.abilityAt(p, 2));

            // The ultimate slot (the index right after the regular ones) and anything beyond are refused.
            int size = LoadoutService.size(p);
            assertEquals(LoadoutResult.ULTIMATE_FIXED, edit(p, LoadoutService.Action.SET, size, 0, Rime.FROST_SHARD.id()));
            assertEquals(LoadoutResult.ULTIMATE_FIXED, edit(p, LoadoutService.Action.SWAP, 0, size, null));
            assertEquals(LoadoutResult.BAD_SLOT, edit(p, LoadoutService.Action.CLEAR, size + 1, 0, null));
            assertEquals(LoadoutResult.BAD_SLOT, edit(p, LoadoutService.Action.CLEAR, -1, 0, null));

            // Abilities of another innate technique, or unknown ones, are not available.
            assertEquals(LoadoutResult.UNAVAILABLE, edit(p, LoadoutService.Action.SET, 0, 0, TestAbilities.DASH.id()));
            assertEquals(LoadoutResult.UNAVAILABLE, edit(p, LoadoutService.Action.SET, 0, 0, ResourceLocation.fromNamespaceAndPath("kegare", "nope")));

            // Mid-cast and in combat: refused, nothing changes.
            InnateTechniqueService.select(p, 2);
            CastManager.handleIntent(p, CastIntent.CAST_PRESS); // Frost Field channel
            assertEquals(LoadoutResult.CASTING, edit(p, LoadoutService.Action.CLEAR, 0, 0, null));
            CastManager.stopAll(p, EndReason.CANCELLED);
            InnateTechniqueService.clearCooldowns(p);
            CombatHelper.markCombat(p);
            List<Optional<ResourceLocation>> snapshot = List.copyOf(LoadoutService.slots(p));
            assertEquals(LoadoutResult.IN_COMBAT, edit(p, LoadoutService.Action.CLEAR, 0, 0, null));
            assertEquals(LoadoutResult.IN_COMBAT, edit(p, LoadoutService.Action.RESET, 0, 0, null));
            assertEquals(snapshot, LoadoutService.slots(p));
            clearCombat(server, p);

            // A toggle taken off the bar switches off.
            MaliceService.refill(p);
            InnateTechniqueService.select(p, 3);
            CastManager.handleIntent(p, CastIntent.CAST_PRESS);
            assertTrue(Rime.RIME_SHELL.isActive(p), "setup: shell on");
            assertEquals(LoadoutResult.OK, edit(p, LoadoutService.Action.CLEAR, 3, 0, null));
            assertFalse(Rime.RIME_SHELL.isActive(p), "removed from the bar: switched off");

            // A new innate technique resets the loadout to its default layout.
            InnateTechniqueService.setInnateTechnique(p, TestInnateTechniques.KINETIC.get());
            assertEquals(bar(id(TestAbilities.DASH), id(TestAbilities.BURST)), LoadoutService.slots(p));
            assertEquals(0, InnateTechniqueService.get(p).selectedSlot());

            // Login repair: a layout built for another technique is rebuilt.
            InnateTechniqueService.setInnateTechnique(p, Rime.RIME.get());
            LoadoutService.get(p).builtFor = TestInnateTechniques.KINETIC.getId();
            LoadoutService.validate(p);
            assertEquals(LoadoutService.SLOTS, LoadoutService.size(p));
            assertEquals(id(Rime.FROST_SHARD), LoadoutService.slots(p).get(0));

            // A bar saved before there were nine slots (one per ability) keeps its order and is padded.
            LoadoutService.get(p).slots.clear();
            LoadoutService.get(p).slots.addAll(List.of(id(Rime.RIME_SHELL), id(Rime.FROST_SHARD), Optional.empty(), id(Rime.GLACIAL_LANCE)));
            LoadoutService.validate(p);
            assertEquals(bar(id(Rime.RIME_SHELL), id(Rime.FROST_SHARD), Optional.empty(), id(Rime.GLACIAL_LANCE)), LoadoutService.slots(p));
        });
    }

    /** A nine-slot bar: these entries, then empty slots. */
    @SafeVarargs
    static List<Optional<ResourceLocation>> bar(Optional<ResourceLocation>... first) {
        List<Optional<ResourceLocation>> out = new java.util.ArrayList<>(List.of(first));
        while (out.size() < LoadoutService.SLOTS) out.add(Optional.empty());
        return out;
    }

    static LoadoutResult edit(ServerPlayer p, LoadoutService.Action action, int slot, int other, ResourceLocation ability) {
        return LoadoutService.edit(p, action, slot, other, Optional.ofNullable(ability));
    }

    static Optional<ResourceLocation> id(com.cnethuka.kegare.innatetechnique.Ability ability) {
        return Optional.of(ability.id());
    }

    /** Moves game time past the combat tag. */
    static void clearCombat(MinecraftServer server, ServerPlayer p) {
        TestServer.advanceGameTime(server, KegareServerConfig.ticks(KegareServerConfig.COMBAT_TAG_SECONDS) + 1);
        assertFalse(CombatHelper.isInCombat(p), "setup: out of combat");
    }
}

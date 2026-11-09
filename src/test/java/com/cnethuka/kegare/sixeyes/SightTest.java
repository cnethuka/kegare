package com.cnethuka.kegare.sixeyes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cnethuka.kegare.TestServer;
import com.cnethuka.kegare.innate_technique.rime.Rime;
import com.cnethuka.kegare.innatetechnique.CastIntent;
import com.cnethuka.kegare.innatetechnique.CastManager;
import com.cnethuka.kegare.innatetechnique.EndReason;
import com.cnethuka.kegare.innatetechnique.InnateTechniqueService;
import com.cnethuka.kegare.malice.MaliceService;
import com.cnethuka.kegare.player.BirthService;
import com.cnethuka.kegare.trait.KegareTraits;
import com.cnethuka.kegare.trait.Trait;
import com.cnethuka.kegare.trait.TraitService;
import com.cnethuka.kegare.trait.TraitSuccession;
import com.mojang.authlib.GameProfile;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import java.util.UUID;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/** Six Eyes' perception (DESIGN §3.32): only the bearer is sent anything, the whole dimension and no other, voids, pulses. */
@ExtendWith(EphemeralTestServerProvider.class)
class SightTest {
    record Sent(ServerPlayer to, Object payload) {}

    static ServerPlayer player(MinecraftServer server, String name, int capacity, double x, double z) {
        TestServer.ensureOverworld(server);
        ServerPlayer p = TestServer.damageable(server, name);
        server.overworld().addNewPlayer(p);
        BirthService.rebirth(p, OptionalInt.of(capacity));
        TraitService.removeAll(p, Trait.RemoveReason.ADMIN);
        if (capacity > 0) {
            InnateTechniqueService.setInnateTechnique(p, Rime.RIME.get());
            MaliceService.refill(p);
        }
        p.moveTo(x, 120, z, 0f, 0f);
        return p;
    }

    static void eyes(MinecraftServer server, ServerPlayer p, boolean on) {
        if (on) {
            TraitSuccession.release(server, KegareTraits.SIX_EYES.get());
            assertEquals(TraitService.AddResult.ADDED, TraitService.add(p, KegareTraits.SIX_EYES.get(), Trait.AddReason.ADMIN));
        } else {
            TraitService.remove(p, KegareTraits.SIX_EYES.get(), Trait.RemoveReason.ADMIN);
        }
    }

    static List<Sent> capture() {
        List<Sent> sent = new ArrayList<>();
        Sight.sentHook = (to, payload) -> sent.add(new Sent(to, payload));
        return sent;
    }

    @Test
    void onlyTheBearerIsSentAnythingTheWholeDimensionAndNoOther(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            ServerPlayer bearer = player(server, "sight_bearer", 1000, 0.5, 0.5);
            ServerPlayer nether = null;
            try {
                ServerPlayer plain = player(server, "sight_plain", 600, 3.5, 0.5);
                ServerPlayer far = player(server, "sight_far", 1500, 20_000.5, -15_000.5); // kilometres away: easily
                ServerPlayer voidFar = player(server, "sight_void", 0, -25_000.5, 30_000.5);
                ServerLevel netherLevel = server.getLevel(Level.NETHER);
                assertNotNull(netherLevel, "setup: the Nether exists");
                nether = FakePlayerFactory.get(netherLevel, new GameProfile(UUID.nameUUIDFromBytes("kegare-test:sight_nether".getBytes(StandardCharsets.UTF_8)), "sight_nether"));
                netherLevel.addNewPlayer(nether);
                eyes(server, bearer, true);

                List<Sent> sent = capture();
                Sight.tick(server);
                assertFalse(sent.isEmpty(), "the bearer is sent what the eyes see");
                for (Sent s : sent) assertEquals(bearer, s.to(), "only ever the bearer: never " + s.to().getScoreboardName());

                SightPayloads.Players players = Sight.players(bearer);
                List<String> names = players.dots().stream().map(SightPayloads.Players.Dot::name).toList();
                assertTrue(names.contains("sight_plain"), "a player nearby");
                assertTrue(names.contains("sight_far"), "and one 25 km off: the whole dimension, no number");
                assertTrue(names.contains("sight_void"), "a void at the same range as everyone");
                assertFalse(names.contains("sight_nether"), "never another dimension");
                assertFalse(names.contains("sight_bearer"), "not themself");
                SightPayloads.Players.Dot v = players.dots().stream().filter(d -> d.name().equals("sight_void")).findFirst().orElseThrow();
                assertTrue(v.severed(), "a Severed player is a void");
                assertEquals(1500, players.dots().stream().filter(d -> d.name().equals("sight_far")).findFirst().orElseThrow().capacity(),
                        "dots sized by capacity");

                // Losing the eyes: told to forget, then nothing more.
                eyes(server, bearer, false);
                sent.clear();
                Sight.tick(server);
                assertEquals(1, sent.size(), "one last word");
                assertFalse(((SightPayloads.Players) sent.get(0).payload()).bearer(), "forget everything");
                sent.clear();
                Sight.tick(server);
                assertTrue(sent.isEmpty(), "and nothing after");
                assertTrue(plain.isAlive() && far.isAlive() && voidFar.isAlive());
            } finally {
                Sight.sentHook = null;
                if (nether != null) nether.serverLevel().removePlayerImmediately(nether, net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);
                eyes(server, bearer, false);
                TestServer.removePlayers(server);
            }
        });
    }

    @Test
    void loadedMobsShowWithTheirFlow(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            TestServer.ensureOverworld(server);
            ServerLevel level = server.overworld();
            ServerPlayer bearer = player(server, "sight_mobs", 1000, 0.5, 0.5);
            Cow cow = new Cow(EntityType.COW, level);
            Zombie zombie = new Zombie(EntityType.ZOMBIE, level);
            try {
                cow.moveTo(5, 120, 5, 0, 0);
                zombie.moveTo(-5, 120, 5, 0, 0);
                for (var m : List.of(cow, zombie)) {
                    m.setNoAi(true);
                    m.setNoGravity(true);
                    level.addFreshEntity(m);
                }
                eyes(server, bearer, true);
                List<SightPayloads.Mobs.Dot> dots = Sight.mobs(bearer).dots();
                SightPayloads.Mobs.Dot c = dots.stream().filter(d -> d.entityId() == cow.getId()).findFirst().orElseThrow();
                SightPayloads.Mobs.Dot z = dots.stream().filter(d -> d.entityId() == zombie.getId()).findFirst().orElseThrow();
                assertFalse(c.hostile(), "a cow's flow is faint");
                assertTrue(z.hostile(), "a zombie's is hostile");
            } finally {
                cow.discard();
                zombie.discard();
                eyes(server, bearer, false);
                TestServer.removePlayers(server);
            }
        });
    }

    @Test
    void castsPulseForTheBearerAndVoidsNever(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            ServerPlayer bearer = player(server, "pulse_bearer", 1000, 0.5, 0.5);
            try {
                ServerPlayer caster = player(server, "pulse_caster", 3000, 4.5, 0.5);
                ServerPlayer severed = player(server, "pulse_severed", 0, -4.5, 0.5);
                eyes(server, bearer, true);
                List<Sent> sent = capture();
                InnateTechniqueService.select(caster, 1);
                CastManager.handleIntent(caster, CastIntent.CAST_PRESS);
                CastManager.handleIntent(caster, CastIntent.CAST_RELEASE);
                List<SightPayloads.Pulse> pulses = sent.stream().map(Sent::payload).filter(SightPayloads.Pulse.class::isInstance).map(SightPayloads.Pulse.class::cast).toList();
                assertEquals(1, pulses.size(), "one pulse as the cast began");
                assertEquals(caster.getId(), pulses.get(0).entityId());
                assertEquals(SightPayloads.Pulse.CAST, pulses.get(0).kind());
                for (Sent s : sent) assertEquals(bearer, s.to(), "to the bearer only");
                CastManager.stopAll(caster, EndReason.CANCELLED);

                sent.clear();
                CastManager.handleIntent(severed, CastIntent.CAST_PRESS);
                assertTrue(sent.stream().noneMatch(s -> s.payload() instanceof SightPayloads.Pulse), "a void never pulses");
            } finally {
                Sight.sentHook = null;
                eyes(server, bearer, false);
                TestServer.removePlayers(server);
            }
        });
    }

    @Test
    void hostileMobsPulseJustBeforeTheyAct(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            TestServer.ensureOverworld(server);
            ServerLevel level = server.overworld();
            ServerPlayer bearer = player(server, "intent_bearer", 1000, 0.5, 0.5);
            Creeper creeper = new Creeper(EntityType.CREEPER, level);
            Skeleton skeleton = new Skeleton(EntityType.SKELETON, level);
            Ghast ghast = new Ghast(EntityType.GHAST, level);
            try {
                creeper.moveTo(6, 120, 0, 0, 0);
                skeleton.moveTo(-6, 120, 0, 0, 0);
                ghast.moveTo(0, 130, 12, 0, 0);
                for (var m : List.<net.minecraft.world.entity.Mob>of(creeper, skeleton, ghast)) {
                    m.setNoAi(true);
                    m.setNoGravity(true);
                    level.addFreshEntity(m);
                }
                eyes(server, bearer, true);
                List<Sent> sent = capture();
                MobIntent.check(level, creeper);
                MobIntent.check(level, skeleton);
                MobIntent.check(level, ghast);
                assertTrue(sent.isEmpty(), "nothing yet");

                creeper.setSwellDir(1); // about to swell
                skeleton.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BOW));
                skeleton.startUsingItem(InteractionHand.MAIN_HAND); // drawing: about to loose
                ghast.setCharging(true);
                MobIntent.check(level, creeper);
                MobIntent.check(level, skeleton);
                MobIntent.check(level, ghast);
                List<Integer> kinds = sent.stream().map(s -> ((SightPayloads.Pulse) s.payload()).kind()).toList();
                assertEquals(List.of(SightPayloads.Pulse.SWELL, SightPayloads.Pulse.LOOSE, SightPayloads.Pulse.CHARGE), kinds, "each pulses as it begins");
                sent.clear();
                MobIntent.check(level, creeper);
                assertTrue(sent.isEmpty(), "once, not every tick it lasts");
                assertEquals(0, creeper.getDeltaMovement().length(), 1e-9, "reading never moves it");
            } finally {
                Sight.sentHook = null;
                creeper.discard();
                skeleton.discard();
                ghast.discard();
                MobIntent.clear();
                eyes(server, bearer, false);
                TestServer.removePlayers(server);
            }
        });
    }
}

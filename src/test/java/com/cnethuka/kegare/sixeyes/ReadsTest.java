package com.cnethuka.kegare.sixeyes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cnethuka.kegare.TestServer;
import com.cnethuka.kegare.combat.CombatHelper;
import com.cnethuka.kegare.innate_technique.rime.FreezeService;
import com.cnethuka.kegare.innate_technique.swap.Disguises;
import com.cnethuka.kegare.innatetechnique.Burnout;
import com.cnethuka.kegare.innate_technique.rime.Rime;
import com.cnethuka.kegare.innatetechnique.CastIntent;
import com.cnethuka.kegare.innatetechnique.CastManager;
import com.cnethuka.kegare.innatetechnique.EndReason;
import com.cnethuka.kegare.innatetechnique.InnateTechniqueService;
import com.cnethuka.kegare.malice.MaliceService;
import com.cnethuka.kegare.progression.Grade;
import com.cnethuka.kegare.progression.GradeService;
import com.cnethuka.kegare.reinforcement.ReinforcementIds;
import com.cnethuka.kegare.loadout.LoadoutService;
import com.cnethuka.kegare.loadout.LoadoutResult;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.Optional;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/** Six Eyes' Reads (DESIGN §3.32): compact and full, "Void", and nothing secret. */
@ExtendWith(EphemeralTestServerProvider.class)
class ReadsTest {
    @Test
    void compactAndFullReadsAndAVoidOnlySaysVoid(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            ServerPlayer bearer = SightTest.player(server, "reads_bearer", 1000, 0.5, 0.5);
            try {
                ServerPlayer target = SightTest.player(server, "reads_target", 3000, 0.5, 6.5); // straight ahead (yaw 0 faces +z)
                ServerPlayer aside = SightTest.player(server, "reads_aside", 800, 12.5, 0.5);
                ServerPlayer severed = SightTest.player(server, "reads_void", 0, -12.5, 0.5);
                SightTest.eyes(server, bearer, true);
                bearer.moveTo(0.5, 120, 0.5, 0f, 0f);
                bearer.setYHeadRot(0f);

                ReadsPayload reads = Reads.build(bearer);
                assertEquals(3, reads.glances().size(), "everyone near enough, at a glance");
                ReadsPayload.Energy t = reads.glances().stream().filter(g -> g.entityId() == target.getId()).findFirst().orElseThrow().energy().orElseThrow();
                assertEquals(Optional.of(Rime.RIME.get().id()), t.technique(), "their innate technique");
                assertEquals((int) MaliceService.get(target).current(), t.malice(), "their current Malice");
                assertEquals(target.getHealth(), t.health(), "and their health");
                assertEquals(target.getMaxHealth(), t.maxHealth());
                ReadsPayload.Glance v = reads.glances().stream().filter(g -> g.entityId() == severed.getId()).findFirst().orElseThrow();
                assertTrue(v.severed(), "a void");
                assertEquals(Optional.empty(), v.energy(), "says nothing at all");

                // The one at the crosshair gets full detail: a charge in progress, a toggle, a cooldown.
                assertSame(target, Reads.focus(bearer), "the focus is the one at the crosshair");
                assertEquals(LoadoutResult.OK, LoadoutService.edit(target, LoadoutService.Action.SET, 3, 0, Optional.of(ReinforcementIds.ABILITY)));
                assertEquals(LoadoutResult.OK, LoadoutService.edit(target, LoadoutService.Action.SET, 4, 0, Optional.of(Rime.GLACIAL_LANCE.id())));
                InnateTechniqueService.select(target, 3);
                CastManager.handleIntent(target, CastIntent.CAST_PRESS); // Reinforcement on
                InnateTechniqueService.startCooldown(target, Rime.RIME_SHELL);
                InnateTechniqueService.select(target, 4);
                CastManager.handleIntent(target, CastIntent.CAST_PRESS); // charging the lance
                for (int i = 0; i < 5; i++) CastManager.tick(target);
                ReadsPayload.Body full = Reads.detail(target).body().orElseThrow();
                assertTrue(full.toggles().contains(ReinforcementIds.ABILITY), "active toggles");
                assertTrue(full.cooldowns().stream().anyMatch(c -> c.seconds() > 0), "cooldowns");
                ReadsPayload.Cast charging = full.cast().orElseThrow();
                assertEquals(Rime.GLACIAL_LANCE.id(), charging.ability(), "what they're charging");
                assertTrue(charging.progress() > 0f && charging.progress() <= 1f, "and how far: " + charging.progress());
                CastManager.stopAll(target, EndReason.CANCELLED);

                // Turn to the void: their Read is empty.
                bearer.moveTo(0.5, 120, 0.5, 90f, 0f); // yaw 90 faces -x
                assertSame(severed, Reads.focus(bearer));
                ReadsPayload.Focus voidRead = Reads.detail(severed);
                assertTrue(voidRead.severed() && voidRead.body().isEmpty(), "Void, and nothing else: no health, no effects, nothing");

                bearer.moveTo(0.5, 120, 0.5, 180f, 0f);
                assertNull(Reads.focus(bearer), "nobody at the crosshair: no focus");
                assertTrue(aside.isAlive());
            } finally {
                SightTest.eyes(server, bearer, false);
                TestServer.removePlayers(server);
            }
        });
    }

    /** What a Read carries, field by field: never Grade, never traits. */
    @Test
    void nothingSecretIsEverCarried() {
        for (Class<?> c : new Class<?>[] {ReadsPayload.class, ReadsPayload.Glance.class, ReadsPayload.Energy.class, ReadsPayload.MobGlance.class,
                ReadsPayload.Focus.class, ReadsPayload.Body.class, ReadsPayload.Vitals.class, ReadsPayload.Effect.class, ReadsPayload.Status.class,
                ReadsPayload.Cast.class, ReadsPayload.Cooldown.class, SightPayloads.Players.Dot.class, SightPayloads.Mobs.Dot.class}) {
            for (RecordComponent rc : c.getRecordComponents()) {
                String name = rc.getName().toLowerCase();
                for (String secret : new String[] {"grade", "trait", "refinement", "peak", "insight", "assessment", "brain", "unlock", "six", "eyes", "shrine"}) {
                    assertFalse(name.contains(secret), c.getSimpleName() + "." + rc.getName() + " must not be there");
                }
            }
        }
        assertTrue(Arrays.stream(Grade.values()).count() > 0);
    }

    @Test
    void theFocusReadsTheWholeBody(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            ServerPlayer bearer = SightTest.player(server, "reads_body_bearer", 1000, 0.5, 0.5);
            try {
                ServerPlayer target = SightTest.player(server, "reads_body", 3000, 0.5, 6.5);
                SightTest.eyes(server, bearer, true);
                bearer.moveTo(0.5, 120, 0.5, 0f, 0f);
                // Gear, and its modifiers on the attributes as an equipment change puts them (a test's fake player never ticks to do it).
                for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.CHEST, EquipmentSlot.MAINHAND}) {
                    ItemStack stack = new ItemStack(slot == EquipmentSlot.CHEST ? Items.IRON_CHESTPLATE : Items.IRON_SWORD);
                    target.setItemSlot(slot, stack);
                    stack.forEachModifier(slot, (attribute, modifier) -> target.getAttribute(attribute).addOrUpdateTransientModifier(modifier));
                }
                target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 600, 1));
                target.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, -1, 0));
                target.addEffect(new MobEffectInstance(MobEffects.POISON, 80, 0, false, false, false)); // hidden: the eyes see it anyway
                target.setHealth(14f);
                target.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 600, 0)); // 4 absorption
                target.getFoodData().setFoodLevel(17);
                target.setRemainingFireTicks(100);
                FreezeService.addStacks(target, 2, null);
                CombatHelper.stun(target, 30);
                Burnout.apply(target, 200);
                // Reinforcement on: its knockback resistance is in the attribute.
                assertEquals(LoadoutResult.OK, LoadoutService.edit(target, LoadoutService.Action.SET, 3, 0, Optional.of(ReinforcementIds.ABILITY)));
                CombatHelper.clearStun(target);
                InnateTechniqueService.select(target, 3);
                CastManager.handleIntent(target, CastIntent.CAST_PRESS);
                for (int i = 0; i < 5; i++) CastManager.tick(target);
                CombatHelper.stun(target, 30);

                assertSame(target, Reads.focus(bearer));
                ReadsPayload.Body body = Reads.detail(target).body().orElseThrow();
                ReadsPayload.Vitals v = body.vitals();
                assertEquals(14f, v.health());
                assertEquals(target.getMaxHealth(), v.maxHealth());
                assertEquals(4f, v.absorption());
                assertEquals(target.getAttributeValue(Attributes.ARMOR), v.armor(), 1e-6, "armour as it is now");
                assertTrue(v.armor() >= 6, "the chestplate counts: " + v.armor());
                assertEquals(target.getAttributeValue(Attributes.ARMOR_TOUGHNESS), v.toughness(), 1e-6);
                assertEquals(target.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE), v.knockbackResistance(), 1e-6);
                assertTrue(v.knockbackResistance() > 0, "Reinforcement's resistance shows: " + v.knockbackResistance());
                assertEquals(target.getAttributeValue(Attributes.ATTACK_DAMAGE), v.attackDamage(), 1e-6, "the sword and Strength, counted");
                assertTrue(v.attackDamage() > 5, "more than a fist: " + v.attackDamage());
                assertEquals(target.getAttributeValue(Attributes.ATTACK_SPEED), v.attackSpeed(), 1e-6);
                assertEquals(target.getAttributeValue(Attributes.MOVEMENT_SPEED), v.movement(), 1e-6);
                assertTrue(v.movement() > v.movementBase(), "Speed II, counted");
                assertEquals(17, v.food(), "hunger");
                assertEquals(4, body.effects().size(), "every effect, the hidden one too");
                ReadsPayload.Effect speed = body.effects().stream().filter(e -> e.effect().getPath().equals("speed")).findFirst().orElseThrow();
                assertEquals(1, speed.amplifier(), "its level");
                assertTrue(speed.ticks() > 500 && speed.ticks() <= 600, "and its time left");
                assertEquals(-1, body.effects().stream().filter(e -> e.effect().getPath().equals("strength")).findFirst().orElseThrow().ticks(), "one that never runs out");
                ReadsPayload.Status status = body.status();
                assertTrue(status.burning(), "burning");
                assertEquals(FreezeService.stacks(target), status.freezeStacks(), "Freeze stacks");
                assertTrue(status.freezeStacks() > 0);
                assertEquals(CombatHelper.stunTicksRemaining(target), status.stunTicks(), "stunned");
                assertEquals(Burnout.remaining(target), status.burnoutTicks(), "Burnout");
                assertTrue(body.toggles().contains(ReinforcementIds.ABILITY), "what they've switched on, as before");

                // Through the wire, exactly.
                net.minecraft.network.FriendlyByteBuf buf = new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
                ReadsPayload sent = Reads.build(bearer);
                ReadsPayload.STREAM_CODEC.encode(buf, sent);
                assertEquals(sent, ReadsPayload.STREAM_CODEC.decode(buf));
            } finally {
                SightTest.eyes(server, bearer, false);
                TestServer.removePlayers(server);
            }
        });
    }

    @Test
    void aMobIsReadAtAGlanceAndInFull(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            ServerPlayer bearer = SightTest.player(server, "reads_mob_bearer", 1000, 0.5, 0.5);
            Zombie zombie = EntityType.ZOMBIE.create(server.overworld());
            Zombie behind = EntityType.ZOMBIE.create(server.overworld());
            try {
                SightTest.eyes(server, bearer, true);
                bearer.moveTo(0.5, 120, 0.5, 0f, 0f);
                zombie.moveTo(0.5, 119, 8.5, 0f, 0f);
                zombie.setNoAi(true);
                server.overworld().addFreshEntity(zombie);
                behind.moveTo(0.5, 119, -8.5, 0f, 0f);
                behind.setNoAi(true);
                server.overworld().addFreshEntity(behind);
                zombie.hurt(server.overworld().damageSources().generic(), 3f);

                ReadsPayload reads = Reads.build(bearer);
                ReadsPayload.MobGlance glance = reads.mobs().stream().filter(m -> m.entityId() == zombie.getId()).findFirst().orElseThrow(() -> new AssertionError("the zombie in view"));
                assertEquals(zombie.getHealth(), glance.health(), "its health at a glance");
                assertEquals(20f, glance.maxHealth());
                assertTrue(reads.mobs().stream().noneMatch(m -> m.entityId() == behind.getId()), "one behind isn't in view");

                assertSame(zombie, Reads.focus(bearer), "nobody else at the crosshair: the zombie is the focus");
                ReadsPayload.Focus focus = reads.focus().orElseThrow();
                assertEquals(zombie.getId(), focus.entityId());
                ReadsPayload.Body body = focus.body().orElseThrow();
                assertTrue(body.cast().isEmpty() && body.cooldowns().isEmpty() && body.toggles().isEmpty(), "a mob casts nothing");
                assertEquals(zombie.getHealth(), body.vitals().health());
                assertEquals(zombie.getAttributeValue(Attributes.ARMOR), body.vitals().armor(), 1e-6, "a zombie's own armour");
                assertEquals(zombie.getAttributeValue(Attributes.ATTACK_DAMAGE), body.vitals().attackDamage(), 1e-6);
                assertEquals(ReadsPayload.Vitals.NONE, body.vitals().attackSpeed(), "an attribute it hasn't got");
                assertEquals(-1, body.vitals().food(), "no hunger for a mob");
            } finally {
                zombie.discard();
                behind.discard();
                SightTest.eyes(server, bearer, false);
                TestServer.removePlayers(server);
            }
        });
    }

    @Test
    void aDisguiseIsReadAsTheWearersOwnBody(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            ServerPlayer bearer = SightTest.player(server, "reads_disguise_bearer", 1000, 0.5, 0.5);
            try {
                ServerPlayer wearer = SightTest.player(server, "reads_wearer", 3000, 0.5, 6.5);
                ServerPlayer face = SightTest.player(server, "reads_face", 900, 30.5, 0.5);
                SightTest.eyes(server, bearer, true);
                bearer.moveTo(0.5, 120, 0.5, 0f, 0f);
                InnateTechniqueService.setInnateTechnique(face, com.cnethuka.kegare.innate_technique.swap.Swap.SWAP.get());
                wearer.setHealth(11f);
                face.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 200, 2));
                Disguises.apply(wearer, face, 200, Disguises.Kind.AWARENESS, wearer);
                assertTrue(Disguises.isDisguised(wearer), "setup: disguised");

                ReadsPayload.Energy energy = Reads.build(bearer).glances().stream().filter(g -> g.entityId() == wearer.getId()).findFirst().orElseThrow().energy().orElseThrow();
                assertEquals(Optional.of(Rime.RIME.get().id()), energy.technique(), "the wearer's own technique, not the face's");
                assertEquals(11f, energy.health(), "and the wearer's own health");
                ReadsPayload.Body body = Reads.detail(wearer).body().orElseThrow();
                assertEquals(11f, body.vitals().health());
                assertTrue(body.effects().isEmpty(), "the face's effects aren't the wearer's");
                Disguises.endInvolving(wearer);
            } finally {
                SightTest.eyes(server, bearer, false);
                TestServer.removePlayers(server);
            }
        });
    }

    @Test
    void onlyTheBearerIsEverSentAnyOfIt(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            ServerPlayer bearer = SightTest.player(server, "reads_sent_bearer", 1000, 0.5, 0.5);
            ServerPlayer other = SightTest.player(server, "reads_sent_other", 1000, 0.5, 6.5);
            java.util.List<ServerPlayer> sentTo = new java.util.ArrayList<>();
            try {
                Reads.sentHook = (to, payload) -> sentTo.add(to);
                Reads.tick(server);
                assertTrue(sentTo.isEmpty(), "no bearer, no Reads, to anyone");
                SightTest.eyes(server, bearer, true);
                for (int i = 0; i < 3; i++) Reads.tick(server);
                assertTrue(sentTo.size() >= 3 && sentTo.stream().allMatch(p -> p == bearer), "only ever the bearer: " + sentTo);
                assertFalse(sentTo.contains(other), "the one being read never gets their own Read, or anyone's");
            } finally {
                Reads.sentHook = null;
                SightTest.eyes(server, bearer, false);
                TestServer.removePlayers(server);
            }
        });
    }

    @Test
    void gradeNeverReachesARead(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            ServerPlayer bearer = SightTest.player(server, "reads_grade_bearer", 1000, 0.5, 0.5);
            try {
                ServerPlayer target = SightTest.player(server, "reads_grade_target", 1000, 0.5, 6.5);
                SightTest.eyes(server, bearer, true);
                ReadsPayload before = Reads.build(bearer);
                GradeService.set(target, Grade.SPECIAL_GRADE);
                ReadsPayload after = Reads.build(bearer);
                assertEquals(before.glances(), after.glances(), "a Special Grade reads the same as a Grade 4");
            } finally {
                SightTest.eyes(server, bearer, false);
                TestServer.removePlayers(server);
            }
        });
    }
}

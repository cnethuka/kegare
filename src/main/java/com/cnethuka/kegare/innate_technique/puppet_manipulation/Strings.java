package com.cnethuka.kegare.innate_technique.puppet_manipulation;

import com.cnethuka.kegare.Kegare;
import com.cnethuka.kegare.combat.CombatHelper;
import com.cnethuka.kegare.innatetechnique.CastManager;
import com.cnethuka.kegare.innatetechnique.InnateTechniqueService;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;

/**
 * Every caster's string (one at a time, runtime only: nothing is saved). A string is {@link Phase#ATTACHED} until its
 * second stage turns it into a pull, a tether or a leash, and it ends for good with the death, logout or dimension change
 * of anyone on it. Bosses can be attached to but are never moved; a {@link StringsTargetEvent} can refuse any target.
 */
public final class Strings {
    /** The string's life. Everything after ATTACHED is its second stage, with its cooldown already running. */
    public enum Phase { ATTACHED, REEL_IN, ZIP, TIE_PULL, TETHER, LEASH }

    /** How a string ended. */
    private enum End { DONE, SNAPPED, SLACK, CUT }

    public static final ResourceKey<DamageType> TIE_COLLISION = ResourceKey.create(Registries.DAMAGE_TYPE, Kegare.id("strings_tie"));

    private static final ResourceLocation LEASH_SLOW = Kegare.id("strings_leash_slow");
    /** A little lift for anything dragged along the ground, so it rides over bumps and steps instead of stopping at them. */
    private static final double HOP = 0.12;
    /** A pull's last stretch: each tick covers this fraction of what's left, but at least {@link #EASE_MIN} blocks. */
    private static final double EASE = 0.5;
    private static final double EASE_MIN = 0.12;
    /** Reel In and Zip are done within this much of the stop distance. */
    private static final double ARRIVE_SLACK = 0.25;
    /** How much of a pull's speed Reel In and Zip keep on arriving: enough to not stop dead in the air. */
    private static final double ARRIVE_KEEP = 0.2;

    private static final class Active {
        final ServerPlayer caster;
        final LivingEntity first;
        @Nullable
        LivingEntity second;
        Phase phase = Phase.ATTACHED;
        boolean firstMoves = true;
        boolean secondMoves = true;
        long endTick;

        Active(ServerPlayer caster, LivingEntity first) {
            this.caster = caster;
            this.first = first;
        }
    }

    private static final Map<UUID, Active> ACTIVE = new HashMap<>();

    private Strings() {}

    // ---------------------------------------------------------------- asking

    /** A string is out and waiting for its second stage. */
    public static boolean isAttached(ServerPlayer caster) {
        Active a = ACTIVE.get(caster.getUUID());
        return a != null && a.phase == Phase.ATTACHED;
    }

    /** What the second stage would be right now: the other half if the caster is looking at a different valid target. */
    public static StringsVariation variation(ServerPlayer caster, boolean special) {
        boolean other = otherTarget(caster).isPresent();
        if (special) return other ? StringsVariation.LEASH : StringsVariation.ZIP;
        return other ? StringsVariation.TIE : StringsVariation.REEL_IN;
    }

    /** The valid target under the crosshair, if it isn't the one the string is already on. */
    private static Optional<LivingEntity> otherTarget(ServerPlayer caster) {
        Active a = ACTIVE.get(caster.getUUID());
        if (a == null) return Optional.empty();
        return PuppetTargeting.lookedAtStringTarget(caster, PuppetConfig.STRINGS_RANGE.get()).filter(e -> e != a.first);
    }

    /** Whether the second stage can move anything at all: not when every target is a boss (Zip moves the caster). */
    static boolean canMoveAnything(ServerPlayer caster, StringsVariation variation) {
        Active a = ACTIVE.get(caster.getUUID());
        if (a == null) return false;
        return switch (variation) {
            case ZIP -> true;
            case REEL_IN -> !CombatHelper.isBoss(a.first);
            case TIE, LEASH -> !CombatHelper.isBoss(a.first) || otherTarget(caster).filter(e -> !CombatHelper.isBoss(e)).isPresent();
        };
    }

    /** A puppet on a running pull, tether or leash doesn't walk off, or catch up with its owner. */
    static boolean holds(PuppetEntity puppet) {
        for (Active a : ACTIVE.values()) {
            if (a.phase != Phase.ATTACHED && a.phase != Phase.ZIP && (a.first == puppet || a.second == puppet)) return true;
        }
        return false;
    }

    public static Phase phase(ServerPlayer caster) {
        Active a = ACTIVE.get(caster.getUUID());
        return a == null ? null : a.phase;
    }

    // ---------------------------------------------------------------- stage 1

    /** Attaches a new string (whatever the caster's last one was still doing ends). */
    static void fire(ServerPlayer caster, LivingEntity target) {
        cut(caster);
        Active a = new Active(caster, target);
        a.endTick = caster.level().getGameTime() + PuppetConfig.ticks(PuppetConfig.STRINGS_LIFE);
        ACTIVE.put(caster.getUUID(), a);
    }

    // ---------------------------------------------------------------- stage 2

    /** Called from inside the cast (paid; the cooldown starts right after). */
    static void stageTwo(ServerPlayer caster, StringsVariation variation) {
        Active a = ACTIVE.get(caster.getUUID());
        if (a == null || a.phase != Phase.ATTACHED || !intact(a)) {
            if (a != null) finish(a, End.SLACK);
            CastManager.fizzleKeepingCost(caster);
            return;
        }
        LivingEntity other = variation == StringsVariation.TIE || variation == StringsVariation.LEASH ? otherTarget(caster).orElse(null) : null;
        a.firstMoves = allowed(caster, a.first, variation);
        if (other != null) {
            a.second = other;
            a.secondMoves = allowed(caster, other, variation);
        } else {
            a.secondMoves = false;
        }
        if (!a.firstMoves && !a.secondMoves) { // refused by everything it would have moved
            caster.displayClientMessage(Component.translatable("message.kegare.strings.refused"), true);
            CastManager.fizzleKeepingCost(caster);
            finish(a, End.CUT);
            return;
        }
        long now = caster.level().getGameTime();
        switch (variation) {
            case REEL_IN -> {
                a.phase = Phase.REEL_IN;
                a.endTick = now + PuppetConfig.ticks(PuppetConfig.STRINGS_PULL_TIMEOUT);
            }
            case ZIP -> {
                a.phase = Phase.ZIP;
                a.endTick = now + PuppetConfig.ticks(PuppetConfig.STRINGS_PULL_TIMEOUT);
            }
            case TIE -> {
                a.phase = Phase.TIE_PULL;
                a.endTick = now + PuppetConfig.ticks(PuppetConfig.STRINGS_PULL_TIMEOUT);
            }
            case LEASH -> {
                a.phase = Phase.LEASH;
                a.endTick = now + PuppetConfig.ticks(PuppetConfig.STRINGS_LEASH_DURATION);
                slow(caster, true);
            }
        }
    }

    /** Fires the event; bosses are moved by nothing, but it fires for them too so listeners see every target. */
    private static boolean allowed(ServerPlayer caster, LivingEntity target, StringsVariation variation) {
        boolean refused = NeoForge.EVENT_BUS.post(new StringsTargetEvent(caster, target, variation)).isCanceled();
        // Zip moves the caster, so a boss target is fine there.
        return !refused && (variation == StringsVariation.ZIP || !CombatHelper.isBoss(target));
    }

    // ---------------------------------------------------------------- every tick

    static void tick(MinecraftServer server) {
        if (ACTIVE.isEmpty()) return;
        for (Active a : new ArrayList<>(ACTIVE.values())) tick(a);
    }

    private static void tick(Active a) {
        if (!intact(a)) {
            finish(a, End.SLACK);
            return;
        }
        if (stretchedTooFar(a)) {
            finish(a, End.SNAPPED);
            return;
        }
        boolean timeUp = a.caster.level().getGameTime() >= a.endTick;
        switch (a.phase) {
            case ATTACHED -> {
                if (timeUp) finish(a, End.SLACK);
            }
            case REEL_IN -> {
                if (timeUp || a.first.distanceTo(a.caster) <= PuppetConfig.STRINGS_ARRIVE.get() + ARRIVE_SLACK) {
                    land(a.first, ARRIVE_KEEP);
                    finish(a, End.DONE);
                } else {
                    pull(a.first, a.caster, PuppetConfig.STRINGS_PULL_SPEED.get(), PuppetConfig.STRINGS_ARRIVE.get());
                }
            }
            case ZIP -> {
                if (timeUp || a.caster.distanceTo(a.first) <= PuppetConfig.STRINGS_ARRIVE.get() + ARRIVE_SLACK) {
                    land(a.caster, ARRIVE_KEEP);
                    finish(a, End.DONE);
                } else {
                    pull(a.caster, a.first, PuppetConfig.STRINGS_PULL_SPEED.get(), PuppetConfig.STRINGS_ARRIVE.get());
                }
            }
            case TIE_PULL -> {
                double distance = a.first.distanceTo(a.second);
                double collideAt = PuppetConfig.STRINGS_COLLIDE.get();
                boolean met = distance <= collideAt;
                if (met) collide(a);
                if (met || timeUp) { // a wall in the way: no collision, but they're tethered all the same
                    if (a.firstMoves) land(a.first, 0.0);
                    if (a.secondMoves) land(a.second, 0.0);
                    a.phase = Phase.TETHER;
                    a.endTick = a.caster.level().getGameTime() + PuppetConfig.ticks(PuppetConfig.STRINGS_TETHER_DURATION);
                } else {
                    // Each covers its share of the gap, so they meet at the collision distance, not inside each other.
                    double share = (distance - collideAt) / (a.firstMoves && a.secondMoves ? 2.0 : 1.0) + 0.01;
                    double speed = Math.min(PuppetConfig.STRINGS_PULL_SPEED.get(), share);
                    if (a.firstMoves) pull(a.first, a.second, speed, 0.0);
                    if (a.secondMoves) pull(a.second, a.first, speed, 0.0);
                }
            }
            case TETHER -> {
                if (timeUp) {
                    finish(a, End.DONE);
                } else {
                    double limit = PuppetConfig.STRINGS_TETHER_DISTANCE.get();
                    if (a.firstMoves) keepWithin(a.first, a.second, limit);
                    if (a.secondMoves) keepWithin(a.second, a.first, limit);
                }
            }
            case LEASH -> {
                if (timeUp) {
                    finish(a, End.DONE);
                } else {
                    double limit = PuppetConfig.STRINGS_LEASH_DISTANCE.get();
                    if (a.firstMoves) keepWithin(a.first, a.caster, limit);
                    if (a.secondMoves) keepWithin(a.second, a.caster, limit);
                }
            }
        }
    }

    /** Anyone on it dead, gone, in another dimension, or otherwise out of reach. */
    private static boolean intact(Active a) {
        return here(a.caster, a) && here(a.first, a) && (a.second == null || here(a.second, a));
    }

    private static boolean here(LivingEntity e, Active a) {
        return e.isAlive() && !e.isRemoved() && !e.isSpectator() && e.level() == a.caster.level()
                && !(e instanceof ServerPlayer p && p.hasDisconnected())
                && !(e instanceof PuppetEntity puppet && puppet.isPossessed());
    }

    /** The string between its two ends: from the caster, except a Tie's, which runs between the two targets. */
    private static boolean stretchedTooFar(Active a) {
        double snap = PuppetConfig.STRINGS_SNAP_RANGE.get();
        if (a.phase == Phase.TIE_PULL || a.phase == Phase.TETHER) return a.first.distanceTo(a.second) > snap;
        return a.first.distanceTo(a.caster) > snap || a.second != null && a.second.distanceTo(a.caster) > snap;
    }

    private static void collide(Active a) {
        float damage = PuppetConfig.STRINGS_TIE_DAMAGE.get().floatValue();
        if (damage <= 0f) return;
        DamageSource source = source(a.caster);
        a.first.hurt(source, damage);
        a.second.hurt(source, damage);
    }

    public static DamageSource source(ServerPlayer caster) {
        return new DamageSource(caster.level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(TIE_COLLISION), caster);
    }

    /** Drags {@code e} back toward {@code other} once beyond {@code limit}: harder the further, up to the full pull. */
    private static void keepWithin(LivingEntity e, LivingEntity other, double limit) {
        double over = e.distanceTo(other) - limit;
        if (over <= 0) return;
        double speed = PuppetConfig.STRINGS_TETHER_SPEED.get() * (1.0 + over);
        pull(e, other, Math.min(speed, PuppetConfig.STRINGS_PULL_SPEED.get()), limit);
    }

    /** One tick of being dragged toward {@code to}, up to {@code stopAt} blocks short of it. */
    private static void pull(LivingEntity e, LivingEntity to, double speed, double stopAt) {
        Vec3 d = to.position().subtract(e.position());
        double length = d.length();
        double left = length - stopAt;
        if (left <= 1.0e-4) return;
        // Eases off near the end: a player's client is a tick or two behind, and at full speed would overshoot by that much.
        double step = Math.min(speed, Math.max(EASE_MIN, left * EASE));
        Vec3 v = d.scale(Math.min(step, left) / length);
        if (e.onGround() && v.y < HOP) v = new Vec3(v.x, HOP, v.z);
        e.setDeltaMovement(v);
        e.hasImpulse = true;
        e.hurtMarked = true; // a player's own client does the moving: this tells it
        e.resetFallDistance();
        if (e instanceof Mob mob) mob.getNavigation().stop();
        if (e instanceof FakePlayer) e.move(MoverType.SELF, v); // nothing else would move one
    }

    /** A pull is over: only {@code keep} of its speed is left, so it isn't thrown past where it was going. */
    private static void land(LivingEntity e, double keep) {
        e.setDeltaMovement(e.getDeltaMovement().scale(keep));
        e.hasImpulse = true;
        e.hurtMarked = true;
        e.resetFallDistance();
    }

    private static void slow(ServerPlayer caster, boolean on) {
        AttributeInstance speed = caster.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null) return;
        if (on) {
            speed.addOrUpdateTransientModifier(new AttributeModifier(LEASH_SLOW, -PuppetConfig.STRINGS_LEASH_SLOW.get(), AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        } else {
            speed.removeModifier(LEASH_SLOW);
        }
    }

    // ---------------------------------------------------------------- ending

    private static void finish(Active a, End how) {
        ServerPlayer caster = a.caster;
        ACTIVE.remove(caster.getUUID(), a);
        if (a.phase == Phase.LEASH) slow(caster, false);
        if (!caster.isAlive() || caster.isRemoved() || caster.hasDisconnected()) return;
        // A string that never got its second stage: the cooldown starts now (after one, it's already running).
        if (a.phase == Phase.ATTACHED && how != End.CUT) InnateTechniqueService.startCooldown(caster, PuppetManipulation.STRINGS);
        if (how == End.SNAPPED) caster.displayClientMessage(Component.translatable("message.kegare.strings.snapped"), true);
        if (how == End.SLACK) caster.displayClientMessage(Component.translatable("message.kegare.strings.slack"), true);
    }

    /** Ends the caster's string now, no cooldown: a new one replaces it, they possess a puppet, log out, or reroll away. */
    public static void cut(ServerPlayer caster) {
        Active a = ACTIVE.get(caster.getUUID());
        if (a != null) finish(a, End.CUT);
    }

    static void clear() {
        for (Active a : List.copyOf(ACTIVE.values())) finish(a, End.CUT);
        ACTIVE.clear();
    }
}

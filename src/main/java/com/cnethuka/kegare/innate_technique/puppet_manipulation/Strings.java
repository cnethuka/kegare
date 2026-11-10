package com.cnethuka.kegare.innate_technique.puppet_manipulation;

import com.cnethuka.kegare.Kegare;
import com.cnethuka.kegare.combat.CombatHelper;
import com.cnethuka.kegare.innatetechnique.CastManager;
import com.cnethuka.kegare.innatetechnique.InnateTechniqueService;
import com.cnethuka.kegare.innatetechnique.visual.Visual;
import com.cnethuka.kegare.innatetechnique.visual.VisualService;
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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
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

    /** What the owner's HUD shows of their string. */
    static PuppetHud.StringsHud hud(ServerPlayer caster) {
        Active a = ACTIVE.get(caster.getUUID());
        boolean attached = a != null && a.phase == Phase.ATTACHED;
        int stage = a == null ? 0 : attached ? 1 : 2;
        int left = attached ? (int) Math.max(0, Math.ceil((a.endTick - caster.level().getGameTime()) / 20.0)) : 0;
        return new PuppetHud.StringsHud(stage, attached ? variation(caster, false) : StringsVariation.REEL_IN,
                attached ? variation(caster, true) : StringsVariation.ZIP, left,
                PuppetConfig.STRINGS_FIRE_COST.get().floatValue(), PuppetConfig.STRINGS_REEL_COST.get().floatValue(), PuppetConfig.STRINGS_TIE_COST.get().floatValue(),
                PuppetConfig.STRINGS_ZIP_COST.get().floatValue(), PuppetConfig.STRINGS_LEASH_COST.get().floatValue());
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

    /** Strings Attached: {@code caster}'s running Tie has this puppet of theirs on one end and a player on the other. */
    static boolean tiesPlayerTo(ServerPlayer caster, PuppetEntity puppet) {
        Active a = ACTIVE.get(caster.getUUID());
        if (a == null || a.phase != Phase.TIE_PULL && a.phase != Phase.TETHER) return false;
        return a.first == puppet && realPlayer(a.second) || a.second == puppet && realPlayer(a.first);
    }

    /** Both ends of it are players, and neither refused it: the mastery advancements need that (§4). */
    private static boolean twoPlayers(Active a) {
        return a.firstMoves && a.secondMoves && realPlayer(a.first) && realPlayer(a.second);
    }

    private static boolean realPlayer(@Nullable LivingEntity e) {
        return e instanceof ServerPlayer player && !player.isFakePlayer();
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
        com.cnethuka.kegare.progression.Landings.land(caster, target, PuppetManipulation.STRINGS); // the string took hold
        Active a = new Active(caster, target);
        a.endTick = caster.level().getGameTime() + PuppetConfig.ticks(PuppetConfig.STRINGS_LIFE);
        ACTIVE.put(caster.getUUID(), a);
        VisualService.playOnce(caster, Visual.animation(PuppetIds.ANIM_STRINGS_FIRE));
        grip(target);
        sound(caster, SoundEvents.FISHING_BOBBER_THROW, 0.8f, 1.5f);
        StringsSync.broadcast(caster.server);
        PuppetHud.refresh(caster);
    }

    /** A knot takes hold of {@code target}. */
    private static void grip(LivingEntity target) {
        if (!(target.level() instanceof ServerLevel level)) return;
        VisualService.playAt(level, chest(target), Visual.effect(PuppetIds.FX_STRINGS_ATTACH, Visual.Anchor.BODY));
        sound(target, SoundEvents.LEASH_KNOT_PLACE, 0.9f, 1.3f);
    }

    private static void sound(LivingEntity at, SoundEvent sound, float volume, float pitch) {
        at.level().playSound(null, at.getX(), at.getY(), at.getZ(), sound, SoundSource.PLAYERS, volume, pitch);
    }

    private static Vec3 chest(LivingEntity e) {
        return e.position().add(0, e.getBbHeight() * 0.62, 0);
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
                VisualService.playOnce(caster, Visual.animation(PuppetIds.ANIM_STRINGS_REEL));
                sound(a.first, SoundEvents.FISHING_BOBBER_RETRIEVE, 1.0f, 0.8f);
            }
            case ZIP -> {
                a.phase = Phase.ZIP;
                a.endTick = now + PuppetConfig.ticks(PuppetConfig.STRINGS_PULL_TIMEOUT);
                VisualService.playOnce(caster, Visual.animation(PuppetIds.ANIM_STRINGS_ZIP));
                sound(caster, SoundEvents.FISHING_BOBBER_RETRIEVE, 1.0f, 1.3f);
            }
            case TIE -> {
                a.phase = Phase.TIE_PULL;
                a.endTick = now + PuppetConfig.ticks(PuppetConfig.STRINGS_PULL_TIMEOUT);
                VisualService.playOnce(caster, Visual.animation(PuppetIds.ANIM_STRINGS_TIE));
                grip(other);
                sound(caster, SoundEvents.FISHING_BOBBER_RETRIEVE, 1.0f, 0.6f);
            }
            case LEASH -> {
                a.phase = Phase.LEASH;
                a.endTick = now + PuppetConfig.ticks(PuppetConfig.STRINGS_LEASH_DURATION);
                slow(caster, true);
                VisualService.playOnce(caster, Visual.animation(PuppetIds.ANIM_STRINGS_LEASH));
                grip(other);
                if (twoPlayers(a)) PuppetAdvancements.award(caster, PuppetAdvancements.WALKIES);
            }
        }
        StringsSync.broadcast(caster.server);
        PuppetHud.refresh(caster);
    }

    /** Every string in {@code level}, for the clients there. */
    static List<StringsSync.Entry> entries(ServerLevel level) {
        List<StringsSync.Entry> out = new ArrayList<>();
        for (Active a : ACTIVE.values()) {
            if (a.caster.level() != level) continue;
            float limit = switch (a.phase) {
                case ATTACHED -> PuppetConfig.STRINGS_SNAP_RANGE.get().floatValue();
                case TETHER -> PuppetConfig.STRINGS_TETHER_DISTANCE.get().floatValue();
                case LEASH -> PuppetConfig.STRINGS_LEASH_DISTANCE.get().floatValue();
                case REEL_IN, ZIP, TIE_PULL -> 0f;
            };
            out.add(new StringsSync.Entry(a.caster.getId(), a.first.getId(), a.second == null ? -1 : a.second.getId(), a.phase, limit));
        }
        return out;
    }

    /** Fires the event; bosses are moved by nothing, but it fires for them too so listeners see every target. */
    private static boolean allowed(ServerPlayer caster, LivingEntity target, StringsVariation variation) {
        boolean refused = NeoForge.EVENT_BUS.post(new StringsTargetEvent(caster, target, variation)).isCanceled();
        // Zip moves the caster, so a boss target is fine there.
        boolean moves = !refused && (variation == StringsVariation.ZIP || !CombatHelper.isBoss(target));
        if (moves) {
            boolean special = variation == StringsVariation.ZIP || variation == StringsVariation.LEASH;
            com.cnethuka.kegare.progression.Landings.land(caster, target, special ? PuppetManipulation.ZIP_LEASH : PuppetManipulation.STRINGS);
        }
        return moves;
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
                boolean arrived = a.first.distanceTo(a.caster) <= PuppetConfig.STRINGS_ARRIVE.get() + ARRIVE_SLACK;
                if (timeUp || arrived) {
                    land(a.first, ARRIVE_KEEP);
                    // Only a player or a mob counts: reeling in your own puppet doesn't.
                    if (arrived && !(a.first instanceof PuppetEntity puppet && puppet.isOwnedBy(a.caster))) {
                        PuppetAdvancements.award(a.caster, PuppetAdvancements.GET_OVER_HERE);
                    }
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
                    if (twoPlayers(a)) PuppetAdvancements.award(a.caster, PuppetAdvancements.TIED_UP);
                    StringsSync.broadcast(a.caster.server);
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
        com.cnethuka.kegare.progression.Landings.during(a.caster, PuppetManipulation.STRINGS, () -> {
            a.first.hurt(source, damage);
            a.second.hurt(source, damage);
        });
        if (a.first.level() instanceof ServerLevel level) {
            Vec3 at = chest(a.first).add(chest(a.second)).scale(0.5);
            VisualService.playAt(level, at, Visual.effect(PuppetIds.FX_STRINGS_ATTACH, Visual.Anchor.BODY));
            level.playSound(null, at.x, at.y, at.z, SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 0.9f, 0.8f);
            level.playSound(null, at.x, at.y, at.z, SoundEvents.WOOL_HIT, SoundSource.PLAYERS, 1.0f, 0.7f);
        }
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
        fray(a, how);
        StringsSync.broadcast(caster.server);
        if (!caster.isAlive() || caster.isRemoved() || caster.hasDisconnected()) return;
        // A string that never got its second stage: the cooldown starts now (after one, it's already running).
        if (a.phase == Phase.ATTACHED && how != End.CUT) InnateTechniqueService.startCooldown(caster, PuppetManipulation.STRINGS);
        if (how == End.SNAPPED) caster.displayClientMessage(Component.translatable("message.kegare.strings.snapped"), true);
        if (how == End.SLACK) caster.displayClientMessage(Component.translatable("message.kegare.strings.slack"), true);
        PuppetHud.refresh(caster);
    }

    /**
     * The tell of a string giving way: fibers at both ends and halfway along. Snapping is louder than running out; a pull that
     * got there, or one cut by the caster, just stops.
     */
    private static void fray(Active a, End how) {
        boolean ranOut = how == End.SLACK || how == End.DONE && (a.phase == Phase.TETHER || a.phase == Phase.LEASH);
        if (how != End.SNAPPED && !ranOut) return;
        LivingEntity from = a.phase == Phase.TETHER || a.phase == Phase.TIE_PULL ? a.first : a.caster;
        List<LivingEntity> ends = a.phase == Phase.TETHER || a.phase == Phase.TIE_PULL ? List.of(a.second) : a.second != null ? List.of(a.first, a.second) : List.of(a.first);
        for (LivingEntity end : ends) {
            if (!(end.level() instanceof ServerLevel level) || end.level() != from.level()) continue;
            Visual snap = Visual.effect(PuppetIds.FX_STRINGS_SNAP, Visual.Anchor.BODY);
            Vec3 p0 = chest(from);
            Vec3 p1 = chest(end);
            VisualService.playAt(level, p0, snap);
            VisualService.playAt(level, p1, snap);
            VisualService.playAt(level, p0.add(p1).scale(0.5), snap);
            sound(end, how == End.SNAPPED ? SoundEvents.LEASH_KNOT_BREAK : SoundEvents.WOOL_BREAK, how == End.SNAPPED ? 1.0f : 0.6f, 1.2f);
        }
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

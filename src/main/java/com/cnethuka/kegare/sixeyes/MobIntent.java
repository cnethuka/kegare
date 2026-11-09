package com.cnethuka.kegare.sixeyes;

import com.cnethuka.kegare.mixin.MeleeAttackGoalAccessor;
import com.cnethuka.kegare.mixin.MobAttackBoxInvoker;
import com.cnethuka.kegare.mixin.RangedAttackGoalAccessor;
import com.cnethuka.kegare.mixin.ShulkerAttackGoalAccessor;
import com.cnethuka.kegare.mixin.WitherBossAccessor;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import javax.annotation.Nullable;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RangedAttackGoal;
import net.minecraft.world.entity.ai.goal.RangedBowAttackGoal;
import net.minecraft.world.entity.ai.goal.RangedCrossbowAttackGoal;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.enderdragon.phases.EnderDragonPhase;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Drowned;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.entity.monster.Guardian;
import net.minecraft.world.entity.monster.SpellcasterIllager;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.entity.monster.breeze.Breeze;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * The eyes read a hostile mob's flow before it acts (DESIGN §3.32): its outline pulses just before the attack. Read only: nothing here
 * moves, slows or stops a mob, bosses included. Checked every tick for mobs within {@link #RANGE} blocks of a bearer (the outlines' reach);
 * each case pulses once, as it begins:
 * <ul>
 * <li>{@code LOOSE}: any hostile mob drawing or charging a weapon (skeletons, pillagers, piglins, illusioners), or any mob {@link #SHOT_LEAD}
 * ticks from a shot with the vanilla ranged goal;</li>
 * <li>{@code SWELL}: a creeper starting to swell;</li>
 * <li>{@code SWING}: a melee mob {@link #SWING_LEAD} ticks before its swing, its target in reach or about to be (the first swing as it closes
 * in too): the vanilla melee goal (zombies of every kind, spiders, vindicators, endermen, silverfish, endermites, iron golems, angry wolves
 * and the like) and brain-driven melee (hoglins, zoglins, piglins, piglin brutes, the warden);</li>
 * <li>{@code CHARGE}: a ghast, blaze or breeze charging a shot;</li>
 * <li>{@code SPELL}: an evoker or illusioner casting;</li>
 * <li>{@code BEAM}: a guardian or elder guardian locking its beam on;</li>
 * <li>{@code SONIC_BOOM}: a warden starting its sonic boom;</li>
 * <li>{@code DRAGON}: the ender dragon turning to strafe, charge or breathe;</li>
 * <li>{@code SKULL}: the wither {@link #SHOT_LEAD} ticks from a skull, from any of its three heads;</li>
 * <li>{@code THROW}: a witch's potion or a drowned's trident, {@link #SHOT_LEAD} ticks before it's thrown;</li>
 * <li>{@code BULLET}: a shulker {@link #SHOT_LEAD} ticks from a bullet.</li>
 * </ul>
 * The reads go by what the AI is made of, not by species, so a modded mob built on those goals or that memory reads the same. A hostile mob
 * with AI there's nothing to read in (a modded mob's own, and vanilla's slimes, phantoms and vexes) falls back on what any mob
 * shows: {@code LOCK} as it locks onto a target, {@code LOOSE} while it uses an item, and {@code SWING} when its target comes into reach,
 * again {@link #GUESS_REST} ticks after a hit. That's a guess at when, not a reading: a ranged attack with no item shows only the lock.
 */
public final class MobIntent {
    public static final double RANGE = 64.0;
    /** Ticks before a melee swing the pulse comes. */
    public static final int SWING_LEAD = 4;
    /** Ticks before a shot the pulse comes, where the timer can be read. */
    public static final int SHOT_LEAD = 10;
    /** The fallback, with no timer to read: after a hit, its target staying in reach pulses again this many ticks on. */
    public static final int GUESS_REST = 16;
    /** What each mob was doing last tick, by kind (bit flags), and how far its target was from its reach. */
    private static final Map<LivingEntity, Last> LAST = new WeakHashMap<>();

    private record Last(int flags, int targetId, double gap) {}

    private MobIntent() {}

    static void init() {
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post e) -> tick(e.getServer()));
    }

    static void tick(MinecraftServer server) {
        for (ServerLevel level : server.getAllLevels()) {
            List<ServerPlayer> bearers = new ArrayList<>();
            for (ServerPlayer p : level.players()) if (SixEyes.isBearer(p)) bearers.add(p);
            if (bearers.isEmpty()) continue;
            java.util.Set<LivingEntity> seen = new java.util.HashSet<>();
            for (ServerPlayer bearer : bearers) {
                AABB box = bearer.getBoundingBox().inflate(RANGE);
                for (LivingEntity mob : level.getEntitiesOfClass(LivingEntity.class, box, e -> !(e instanceof net.minecraft.world.entity.player.Player))) {
                    if (seen.add(mob)) check(level, mob);
                }
            }
        }
    }

    /** One mob: what it's about to do now, against last tick; whatever began pulses. */
    static void check(ServerLevel level, LivingEntity mob) {
        Last last = LAST.get(mob);
        LivingEntity target = mob instanceof Mob m ? m.getTarget() : null;
        int targetId = target == null ? -1 : target.getId();
        double gap = target == null ? 0 : gap((Mob) mob, target);
        // How fast it and its target are coming together, measured so it doesn't matter which of them is moving.
        double closing = target != null && last != null && last.targetId() == targetId ? Math.max(0, last.gap() - gap) : 0;
        int now = flags(level, mob, target, gap, closing);
        LAST.put(mob, new Last(now, targetId, gap));
        int began = now & ~(last == null ? 0 : last.flags());
        if (began == 0) return;
        for (int kind = 1; kind <= SightPayloads.Pulse.LAST_KIND; kind++) {
            if ((began & (1 << kind)) != 0) Sight.mobPulse(level, mob, kind, RANGE);
        }
    }

    /** Which kinds of intent show on this mob right now, as bits (1 << kind). */
    private static int flags(ServerLevel level, LivingEntity mob, @Nullable LivingEntity target, double gap, double closing) {
        int f = 0;
        if (mob instanceof Enemy && !(mob instanceof Drowned) && mob.isUsingItem()) f |= bit(SightPayloads.Pulse.LOOSE); // (a drowned holds its trident up all fight)
        if (mob instanceof Creeper c && (c.getSwellDir() > 0 || c.isIgnited())) f |= bit(SightPayloads.Pulse.SWELL);
        if (mob instanceof Ghast g && g.isCharging()) f |= bit(SightPayloads.Pulse.CHARGE);
        if (mob instanceof Blaze b && b.isOnFire()) f |= bit(SightPayloads.Pulse.CHARGE); // a blaze "on fire" is a blaze charged to shoot
        if (mob instanceof Breeze b && b.getPose() == Pose.SHOOTING) f |= bit(SightPayloads.Pulse.CHARGE); // breathing in for a wind charge
        if (mob instanceof SpellcasterIllager s && s.isCastingSpell()) f |= bit(SightPayloads.Pulse.SPELL);
        if (mob instanceof Guardian g && g.hasActiveAttackTarget()) f |= bit(SightPayloads.Pulse.BEAM);
        if (mob instanceof Warden w && w.getBrain().hasMemoryValue(MemoryModuleType.SONIC_BOOM_SOUND_DELAY)) f |= bit(SightPayloads.Pulse.SONIC_BOOM);
        if (mob instanceof EnderDragon d) {
            var phase = d.getPhaseManager().getCurrentPhase().getPhase();
            if (phase == EnderDragonPhase.STRAFE_PLAYER || phase == EnderDragonPhase.CHARGING_PLAYER || phase == EnderDragonPhase.SITTING_FLAMING) {
                f |= bit(SightPayloads.Pulse.DRAGON);
            }
        }
        if (mob instanceof WitherBoss w && witherHeadReady(level, w)) f |= bit(SightPayloads.Pulse.SKULL);
        if (mob instanceof Mob m && target != null && !m.isNoAi()) f |= attackAi(m, target, gap, closing);
        return f;
    }

    /** A mob with a target: read its attack AI, or fall back on what any hostile mob shows. */
    private static int attackAi(Mob mob, LivingEntity target, double gap, double closing) {
        int f = 0;
        boolean readable = mob instanceof Creeper || mob instanceof Ghast || mob instanceof Blaze || mob instanceof Breeze || mob instanceof SpellcasterIllager
                || mob instanceof Guardian || mob instanceof EnderDragon; // these have their own reads, above
        for (WrappedGoal wrapped : mob.goalSelector.getAvailableGoals()) {
            Goal goal = wrapped.getGoal();
            if (goal instanceof MeleeAttackGoal melee) {
                readable = true;
                if (wrapped.isRunning() && !(mob instanceof Creeper) // (a creeper's melee does nothing: its swell is its attack)
                        && swingSoon(mob, target, ticksUntilSwing(mob, (MeleeAttackGoalAccessor) melee), gap, closing)) f |= bit(SightPayloads.Pulse.SWING);
            } else if (goal instanceof RangedAttackGoal ranged) {
                readable = true;
                if (wrapped.isRunning() && shotSoon(mob, target, ((RangedAttackGoalAccessor) ranged).kegare$attackTime())) f |= bit(shotKind(mob));
            } else if (goal instanceof ShulkerAttackGoalAccessor shulker) {
                readable = true;
                if (wrapped.isRunning() && shulker.kegare$attackTime() <= SHOT_LEAD) f |= bit(SightPayloads.Pulse.BULLET);
            } else if (goal instanceof RangedBowAttackGoal<?> || goal instanceof RangedCrossbowAttackGoal<?>) {
                readable = true; // they draw: read above, from the item in use
            }
        }
        Brain<?> brain = mob.getBrain();
        if (brain.checkMemory(MemoryModuleType.ATTACK_COOLING_DOWN, MemoryStatus.REGISTERED)) {
            readable = true;
            long left = brain.hasMemoryValue(MemoryModuleType.ATTACK_COOLING_DOWN) ? brain.getTimeUntilExpiry(MemoryModuleType.ATTACK_COOLING_DOWN) : 0;
            if (left <= SWING_LEAD && !holdingProjectileWeapon(mob) && swingSoon(mob, target, (int) left, gap, closing)) f |= bit(SightPayloads.Pulse.SWING);
        }
        return readable || !(mob instanceof Enemy) ? f : guess(mob, target, gap, closing);
    }

    /**
     * The melee goal's timer. A goal that gives up when its path is done is about to stop and start again, and starting zeroes the timer, so
     * that mob swings at once however long it had left. (Read from the path: asking the goal itself can change things, a spider's lets go in
     * the light.)
     */
    private static int ticksUntilSwing(Mob mob, MeleeAttackGoalAccessor melee) {
        return !melee.kegare$followsUnseen() && mob.getNavigation().isDone() ? 0 : melee.kegare$ticksUntilNextAttack();
    }

    /** What's left with AI there's nothing to read in: it's locked on, and its target is coming into reach. */
    private static int guess(Mob mob, LivingEntity target, double gap, double closing) {
        int f = bit(SightPayloads.Pulse.LOCK);
        boolean justHit = mob.getLastHurtMob() == target && mob.tickCount - mob.getLastHurtMobTimestamp() < GUESS_REST;
        if (!justHit && reachSoon(gap, closing) && mob.hasLineOfSight(target)) f |= bit(SightPayloads.Pulse.SWING);
        return f;
    }

    /** The swing's timer nearly done, and its target in reach or arriving by then; the mob can see it (or it won't swing). */
    private static boolean swingSoon(Mob mob, LivingEntity target, int ticksLeft, double gap, double closing) {
        return ticksLeft <= SWING_LEAD && reachSoon(gap, closing) && mob.hasLineOfSight(target);
    }

    private static boolean reachSoon(double gap, double closing) {
        return gap <= closing * SWING_LEAD;
    }

    private static boolean shotSoon(Mob mob, LivingEntity target, int ticksLeft) {
        if (ticksLeft < 1 || ticksLeft > SHOT_LEAD) return false; // (below 1: the goal hasn't timed its first shot yet)
        if (mob instanceof Witch w && w.isDrinkingPotion()) return false; // a witch drinking throws nothing
        return mob.hasLineOfSight(target);
    }

    private static int shotKind(Mob mob) {
        if (mob instanceof WitherBoss) return SightPayloads.Pulse.SKULL;
        return mob instanceof Witch || mob instanceof Drowned ? SightPayloads.Pulse.THROW : SightPayloads.Pulse.LOOSE;
    }

    /** The wither's two side heads: one close to firing, with a target it can see. (The main head uses the vanilla ranged goal.) */
    private static boolean witherHeadReady(ServerLevel level, WitherBoss wither) {
        if (wither.getInvulnerableTicks() > 0 || wither.isNoAi()) return false;
        int[] next = ((WitherBossAccessor) wither).kegare$nextHeadUpdate();
        for (int head = 1; head <= 2; head++) {
            int id = wither.getAlternativeTarget(head);
            if (id > 0 && next[head - 1] - wither.tickCount <= SHOT_LEAD && level.getEntity(id) instanceof LivingEntity target && wither.hasLineOfSight(target)) return true;
        }
        return false;
    }

    private static boolean holdingProjectileWeapon(Mob mob) {
        return mob.isHolding(stack -> stack.getItem() instanceof ProjectileWeaponItem weapon && mob.canFireProjectileWeapon(weapon));
    }

    /** How far its target is from its melee reach: 0 in reach. */
    private static double gap(Mob mob, LivingEntity target) {
        AABB reach = ((MobAttackBoxInvoker) mob).kegare$attackBox(), body = target.getHitbox();
        double dx = Math.max(0, Math.max(reach.minX - body.maxX, body.minX - reach.maxX));
        double dy = Math.max(0, Math.max(reach.minY - body.maxY, body.minY - reach.maxY));
        double dz = Math.max(0, Math.max(reach.minZ - body.maxZ, body.minZ - reach.maxZ));
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private static int bit(int kind) {
        return 1 << kind;
    }

    static void clear() {
        LAST.clear();
    }
}

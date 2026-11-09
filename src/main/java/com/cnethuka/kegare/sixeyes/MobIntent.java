package com.cnethuka.kegare.sixeyes;

import com.cnethuka.kegare.mixin.MeleeAttackGoalAccessor;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.enderdragon.phases.EnderDragonPhase;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.entity.monster.Guardian;
import net.minecraft.world.entity.monster.Pillager;
import net.minecraft.world.entity.monster.SpellcasterIllager;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * The eyes read a hostile mob's flow before it acts (DESIGN §3.32): its outline pulses just before the attack. Read only: nothing here
 * moves, slows or stops a mob, bosses included. Checked every tick for hostile mobs within {@link #RANGE} blocks of a bearer (the
 * outlines' reach); each case pulses once, as it begins:
 * <ul>
 * <li>{@code LOOSE}: a skeleton (any kind) drawing its bow, a pillager or piglin charging a crossbow;</li>
 * <li>{@code SWELL}: a creeper starting to swell;</li>
 * <li>{@code SWING}: a melee mob {@link #SWING_LEAD} ticks before its swing, its target in reach (every mob attacking with the vanilla
 * melee goal: zombies of every kind, spiders, vindicators, endermen, silverfish, endermites, iron golems, angry wolves and the like);</li>
 * <li>{@code CHARGE}: a ghast or blaze charging a shot;</li>
 * <li>{@code SPELL}: an evoker or illusioner casting;</li>
 * <li>{@code BEAM}: a guardian or elder guardian locking its beam on;</li>
 * <li>{@code SONIC_BOOM}: a warden starting its sonic boom;</li>
 * <li>{@code DRAGON}: the ender dragon turning to strafe, charge or breathe.</li>
 * </ul>
 */
public final class MobIntent {
    public static final double RANGE = 64.0;
    /** Ticks before a melee swing the pulse comes. */
    public static final int SWING_LEAD = 4;
    /** What each mob was doing last tick, by kind (bit flags). */
    private static final Map<LivingEntity, Integer> LAST = new WeakHashMap<>();

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
        int now = flags(mob);
        int before = LAST.getOrDefault(mob, 0);
        LAST.put(mob, now);
        int began = now & ~before;
        if (began == 0) return;
        for (int kind = 1; kind <= SightPayloads.Pulse.DRAGON; kind++) {
            if ((began & (1 << kind)) != 0) Sight.mobPulse(level, mob, kind, RANGE);
        }
    }

    /** Which kinds of intent show on this mob right now, as bits (1 << kind). */
    static int flags(LivingEntity mob) {
        int f = 0;
        if (mob instanceof AbstractSkeleton s && s.isUsingItem()) f |= bit(SightPayloads.Pulse.LOOSE);
        if (mob instanceof Pillager p && p.isChargingCrossbow()) f |= bit(SightPayloads.Pulse.LOOSE);
        if (mob instanceof Piglin p && p.isUsingItem()) f |= bit(SightPayloads.Pulse.LOOSE); // (charging its crossbow)
        if (mob instanceof Creeper c && (c.getSwellDir() > 0 || c.isIgnited())) f |= bit(SightPayloads.Pulse.SWELL);
        if (mob instanceof Ghast g && g.isCharging()) f |= bit(SightPayloads.Pulse.CHARGE);
        if (mob instanceof Blaze b && b.isOnFire()) f |= bit(SightPayloads.Pulse.CHARGE); // a blaze "on fire" is a blaze charged to shoot
        if (mob instanceof SpellcasterIllager s && s.isCastingSpell()) f |= bit(SightPayloads.Pulse.SPELL);
        if (mob instanceof Guardian g && g.hasActiveAttackTarget()) f |= bit(SightPayloads.Pulse.BEAM);
        if (mob instanceof Warden w && w.getBrain().hasMemoryValue(MemoryModuleType.SONIC_BOOM_SOUND_DELAY)) f |= bit(SightPayloads.Pulse.SONIC_BOOM);
        if (mob instanceof EnderDragon d) {
            var phase = d.getPhaseManager().getCurrentPhase().getPhase();
            if (phase == EnderDragonPhase.STRAFE_PLAYER || phase == EnderDragonPhase.CHARGING_PLAYER || phase == EnderDragonPhase.SITTING_FLAMING) {
                f |= bit(SightPayloads.Pulse.DRAGON);
            }
        }
        if (mob instanceof Mob m && aboutToSwing(m)) f |= bit(SightPayloads.Pulse.SWING);
        return f;
    }

    /** A running vanilla melee goal a few ticks from its swing, with its target already in reach. */
    static boolean aboutToSwing(Mob mob) {
        LivingEntity target = mob.getTarget();
        if (target == null || !mob.isWithinMeleeAttackRange(target)) return false;
        for (WrappedGoal goal : mob.goalSelector.getAvailableGoals()) {
            if (goal.isRunning() && goal.getGoal() instanceof MeleeAttackGoal melee) {
                int left = ((MeleeAttackGoalAccessor) melee).kegare$ticksUntilNextAttack();
                return left > 0 && left <= SWING_LEAD;
            }
        }
        return false;
    }

    private static int bit(int kind) {
        return 1 << kind;
    }

    static void clear() {
        LAST.clear();
    }
}

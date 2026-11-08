package com.cnethuka.kegare.innate_technique.time_manipulation;

import com.cnethuka.kegare.sound.KegareSounds;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Stasis Fields (DESIGN §3.26): a dome of slowed time, centred on the ground where it was cast. Every tick, after the entities move,
 * everything living inside gets heavy Slowness (bosses excepted, DESIGN §4) and every projectile inside is set to crawl:
 * {@code projectileSpeed} of the speed it came in with, the same way (so gravity can't bend it while it's in). Once it leaves every
 * field, or the field ends, it's given its own speed back, the same way. The caster's own projectiles crawl too. Runtime only.
 */
public final class StasisFields {
    static final class Field {
        final int id;
        final ServerLevel level;
        final Vec3 centre;
        final double radius;
        final long until;
        @Nullable final ServerPlayer caster;
        /** Players' projectiles caught in it (for Frozen in Flight). */
        final Set<UUID> caught = new HashSet<>();

        Field(int id, ServerLevel level, Vec3 centre, double radius, long until, @Nullable ServerPlayer caster) {
            this.id = id;
            this.level = level;
            this.centre = centre;
            this.radius = radius;
            this.until = until;
            this.caster = caster;
        }

        boolean contains(Vec3 at) {
            return at.distanceToSqr(centre) <= radius * radius;
        }
    }

    private static final List<Field> FIELDS = new ArrayList<>();
    /** Each slowed projectile and the velocity it came in with. */
    private static final Map<Projectile, Vec3> SLOWED = new WeakHashMap<>();
    private static int nextId = 1;
    /** Slowness is put on for this long, again each tick it's inside. */
    private static final int SLOW_TICKS = 10;

    private StasisFields() {}

    static Field open(ServerPlayer caster, Vec3 centre, double radius, int ticks) {
        ServerLevel level = caster.serverLevel();
        Field field = new Field(nextId++, level, centre, radius, level.getGameTime() + ticks, caster);
        FIELDS.add(field);
        PacketDistributor.sendToPlayersNear(level, null, centre.x, centre.y, centre.z, 96,
                TimeManipulation.fx(TimeNet.Fx.Kind.STASIS, field.id, List.of(centre), (float) radius, ticks));
        return field;
    }

    /** Every level's tick, after its entities. */
    static void tick(ServerLevel level) {
        long now = level.getGameTime();
        for (Iterator<Field> it = FIELDS.iterator(); it.hasNext(); ) {
            Field f = it.next();
            if (f.level != level) continue;
            if (now >= f.until) {
                it.remove();
                PacketDistributor.sendToPlayersNear(level, null, f.centre.x, f.centre.y, f.centre.z, 96,
                        TimeManipulation.fx(TimeNet.Fx.Kind.STASIS_END, f.id, List.of(), 0f, 0));
                continue;
            }
            AABB box = new AABB(f.centre, f.centre).inflate(f.radius);
            for (Entity e : level.getEntities((Entity) null, box, e -> e.isAlive() && f.contains(e.getBoundingBox().getCenter()))) {
                if (e instanceof LivingEntity living && !TimeTargeting.immune(living) && !e.isSpectator()) {
                    living.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, SLOW_TICKS, TimeConfig.STASIS_SLOWNESS.get(),
                            true, false, true), f.caster);
                } else if (e instanceof Projectile p && !SLOWED.containsKey(p) && p.getDeltaMovement().lengthSqr() > 1.0e-4) {
                    SLOWED.put(p, p.getDeltaMovement());
                    level.playSound(null, p.getX(), p.getY(), p.getZ(), KegareSounds.TIME_TICK.value(), SoundSource.PLAYERS, 0.5f, 0.6f);
                    // Frozen in Flight: other players' projectiles (the caster's own don't count).
                    if (p.getOwner() instanceof Player owner && owner != f.caster && f.caught.add(p.getUUID())
                            && f.caught.size() >= TimeAdvancements.FROZEN_IN_FLIGHT_COUNT && f.caster != null) {
                        TimeAdvancements.award(f.caster, TimeAdvancements.FROZEN_IN_FLIGHT);
                    }
                }
            }
        }
        for (Iterator<Map.Entry<Projectile, Vec3>> it = SLOWED.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Projectile, Vec3> e = it.next();
            Projectile p = e.getKey();
            if (p == null || p.level() != level) continue;
            if (!p.isAlive()) {
                it.remove();
                continue;
            }
            boolean inside = false;
            for (Field f : FIELDS) if (f.level == level && f.contains(p.position())) inside = true;
            p.setDeltaMovement(inside ? e.getValue().scale(TimeConfig.STASIS_PROJECTILE_SPEED.get()) : e.getValue());
            p.hasImpulse = true;
            p.hurtMarked = true;
            if (!inside) it.remove();
        }
    }

    /** Whether a projectile is crawling in a field right now (the client mirrors the server's). */
    public static boolean slowed(Projectile p) {
        return SLOWED.containsKey(p);
    }

    static int count() {
        return FIELDS.size();
    }

    static void clear() {
        FIELDS.clear();
        SLOWED.clear();
    }
}

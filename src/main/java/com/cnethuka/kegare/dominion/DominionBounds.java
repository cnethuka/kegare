package com.cnethuka.kegare.dominion;

import com.cnethuka.kegare.combat.CombatHelper;
import java.util.List;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Where a closed barrier stops a move (DESIGN §3.35), run on every move an entity makes ({@code EntityCollideMixin}): on the server for
 * everything living (bosses and spectators never), and on a client for its own player, who the server has told which side they're on.
 * Both sides cut the same move the same way, so a player is never pulled back for crossing it. A move is only ever cut, never lengthened
 * past the free space the game already worked out, so nothing is pushed into a wall. A clash's casters are held near where they stood.
 */
public final class DominionBounds {
    private DominionBounds() {}

    public static Vec3 limit(Entity entity, Vec3 move) {
        List<DominionEntity> all = DominionEntity.in(entity.level());
        if (all.isEmpty() || !(entity instanceof LivingEntity living) || entity.isSpectator() || CombatHelper.isBoss(entity)) return move;
        boolean client = entity.level().isClientSide;
        if (client && !(entity instanceof Player p && p.isLocalPlayer())) return move;
        Vec3 from = DominionEntity.middle(entity);
        Vec3 to = from.add(move), start = to;
        for (DominionEntity d : all) {
            if (d.isClosed()) {
                boolean inside = client ? d.localTrapped : d.trapped.contains(entity.getUUID());
                double margin = DominionEntity.margin(living);
                to = inside ? within(d.centre(), d.radius() - margin, from, to) : outside(d.centre(), d.radius() + margin, from, to);
            }
            if (!client && d.holdAt != null && entity.getUUID().equals(d.caster)) {
                to = within(d.holdAt.add(0, entity.getBbHeight() / 2.0, 0), ClashHold.leash(), from, to);
            }
        }
        return to == start ? move : to.subtract(from); // untouched: the very same move back
    }

    /** Keeps {@code to} within {@code limit} of {@code c}; one already beyond it can't go further out. */
    static Vec3 within(Vec3 c, double limit, Vec3 from, Vec3 to) {
        double dist = to.distanceTo(c);
        double allowed = Math.max(limit, from.distanceTo(c));
        if (dist <= allowed || dist < 1.0e-6) return to;
        return c.add(to.subtract(c).scale(allowed / dist));
    }

    /** Keeps {@code to} at least {@code limit} from {@code c}; one already closer can't come further in. */
    static Vec3 outside(Vec3 c, double limit, Vec3 from, Vec3 to) {
        double dist = to.distanceTo(c);
        double allowed = Math.min(limit, from.distanceTo(c));
        if (dist >= allowed) return to;
        if (dist < 1.0e-6) return from;
        return c.add(to.subtract(c).scale(allowed / dist));
    }

    /** How far a clash's caster may move from where they stood (the clash sets it). */
    public static final class ClashHold {
        private static double leash = 1.5;

        private ClashHold() {}

        public static double leash() {
            return leash;
        }

        public static void setLeash(double blocks) {
            leash = blocks;
        }
    }
}

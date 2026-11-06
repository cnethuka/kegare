package com.cnethuka.kegare.progression;

import com.cnethuka.kegare.innatetechnique.Ability;
import com.cnethuka.kegare.innatetechnique.InnateTechnique;
import com.cnethuka.kegare.innatetechnique.InnateTechniqueService;
import com.cnethuka.kegare.registry.KegareAttachments;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.annotation.Nullable;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;

/**
 * Who is really behind a hit, and with which ability. The one place that decides it, so fights, Grade, Refinement and
 * Insight all agree: a puppet's punch is its owner's, a projectile's is its caster's, and your own puppets and shell
 * never count as targets.
 */
public final class Attribution {
    /** {@code ability}: null for a plain hit (melee, a bow, anything no cast of the current technique caused). */
    public record Hit(ServerPlayer attacker, @Nullable Ability ability) {}

    /** A hit no scope or tag explains, but that a technique knows is an ability's (a possessed puppet's punch). */
    @FunctionalInterface
    public interface Attributor {
        @Nullable
        Ability attribute(ServerPlayer attacker, DamageSource source);
    }

    private static final List<Attributor> ATTRIBUTORS = new CopyOnWriteArrayList<>();

    private Attribution() {}

    public static void addAttributor(Attributor attributor) {
        ATTRIBUTORS.add(attributor);
    }

    /** Null when no player is behind this damage (environmental, a wild mob). */
    @Nullable
    public static Hit of(DamageSource source, MinecraftServer server) {
        Entity causing = source.getEntity();
        Entity direct = source.getDirectEntity();
        ServerPlayer attacker = causing instanceof ServerPlayer p ? p : null;
        if (attacker == null) attacker = owner(causing, server);
        if (attacker == null) attacker = owner(direct, server);
        if (attacker == null) return null;
        return new Hit(attacker, ability(attacker, source, causing, direct));
    }

    @Nullable
    private static Ability ability(ServerPlayer attacker, DamageSource source, @Nullable Entity causing, @Nullable Entity direct) {
        Ability running = CastScope.current(attacker);
        if (running != null) return running;
        Ability tagged = tagged(attacker, direct);
        if (tagged == null) tagged = tagged(attacker, causing);
        if (tagged != null) return tagged;
        for (Attributor attributor : ATTRIBUTORS) {
            Ability found = attributor.attribute(attacker, source);
            if (found != null) return found;
        }
        return null;
    }

    /** What this entity was spawned by, as long as that's still an ability of the attacker's CURRENT technique. */
    @Nullable
    private static Ability tagged(ServerPlayer attacker, @Nullable Entity entity) {
        if (entity == null || !entity.hasData(KegareAttachments.CAST_ORIGIN)) return null;
        CastOrigin origin = entity.getData(KegareAttachments.CAST_ORIGIN);
        if (!origin.caster().equals(attacker.getUUID())) return null;
        Optional<InnateTechnique> technique = InnateTechniqueService.current(attacker);
        return technique.flatMap(t -> t.findAbility(origin.ability())).orElse(null);
    }

    @Nullable
    private static ServerPlayer owner(@Nullable Entity entity, MinecraftServer server) {
        if (entity == null) return null;
        UUID id = ownerId(entity);
        return id == null ? null : player(server, id);
    }

    @Nullable
    private static UUID ownerId(Entity entity) {
        if (entity.hasData(KegareAttachments.CAST_ORIGIN)) return entity.getData(KegareAttachments.CAST_ORIGIN).caster();
        return entity instanceof OwnedEntity owned ? owned.ownerId() : null;
    }

    /** Your own puppet, shell or anything else your casts spawned. */
    public static boolean isOwn(ServerPlayer player, Entity entity) {
        return player.getUUID().equals(ownerId(entity));
    }

    /** What counts as hitting something: not yourself, not your own, not an armor stand, not a spectator. */
    public static boolean isRealTarget(ServerPlayer player, Entity target) {
        return target != player && target instanceof LivingEntity && !(target instanceof ArmorStand)
                && !target.isSpectator() && !isOwn(player, target);
    }

    /** An online player by UUID (players in a level but not the player list, like test fake players, count too). */
    @Nullable
    public static ServerPlayer player(MinecraftServer server, UUID id) {
        ServerPlayer player = server.getPlayerList().getPlayer(id);
        if (player != null) return player;
        for (ServerLevel level : server.getAllLevels()) {
            if (level.getPlayerByUUID(id) instanceof ServerPlayer found) return found;
        }
        return null;
    }
}

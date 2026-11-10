package com.cnethuka.kegare.innate_technique.puppet_manipulation;

import com.cnethuka.kegare.Kegare;
import com.cnethuka.kegare.combat.CombatHelper;
import com.cnethuka.kegare.innatetechnique.visual.Visual;
import com.cnethuka.kegare.innatetechnique.visual.VisualService;
import com.cnethuka.kegare.sound.KegareSounds;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Curio Burst: a puppet detonates. Damage and radius grow with its {@link PuppetTier}: full damage at the centre, half at
 * the edge, and a push away from it (bosses are hurt but never pushed). The {@code kegare:curio_burst} damage type is
 * credited to the owner and tagged as an explosion and no_knockback, so the only push is the blast's own. Never hurts the
 * owner or their puppets; breaks no blocks. Chain Burst queues several, one after another.
 */
public final class CurioBursts {
    public static final ResourceKey<DamageType> CURIO_BURST = ResourceKey.create(Registries.DAMAGE_TYPE, Kegare.id("curio_burst"));

    /** A queued detonation (Chain Burst). */
    private record Pending(PuppetEntity puppet, ServerPlayer owner, long dueTick) {}

    private static final List<Pending> QUEUE = new ArrayList<>();

    private CurioBursts() {}

    public static DamageSource source(Level level, Entity puppet, @Nullable Entity owner) {
        return new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(CURIO_BURST), puppet, owner);
    }

    /** The explosion effect, one size per tier. */
    public static ResourceLocation effect(PuppetTier tier) {
        return switch (tier) {
            case TAP -> PuppetIds.FX_CURIO_BURST_EXPLOSION_TAP;
            case HALF -> PuppetIds.FX_CURIO_BURST_EXPLOSION_HALF;
            case FULL -> PuppetIds.FX_CURIO_BURST_EXPLOSION_FULL;
        };
    }

    /** Detonates {@code puppet} now. Returns what it hurt. */
    public static List<LivingEntity> detonate(PuppetEntity puppet, ServerPlayer owner) {
        if (!puppet.isAlive() || puppet.isRemoved() || puppet.isPossessed() || !(puppet.level() instanceof ServerLevel level)) return List.of();
        Vec3 center = puppet.position().add(0, puppet.getBbHeight() / 2, 0);
        PuppetTier tier = puppet.tier();
        float damage = PuppetConfig.CURIO_DAMAGE[tier.ordinal()].get().floatValue();
        double radius = PuppetConfig.CURIO_RADIUS[tier.ordinal()].get();
        double knockback = PuppetConfig.CURIO_KNOCKBACK.get();
        DamageSource source = source(level, puppet, owner);
        if (Strings.tiesPlayerTo(owner, puppet)) PuppetAdvancements.award(owner, PuppetAdvancements.STRINGS_ATTACHED);
        puppet.discard(); // it IS the bomb

        VisualService.playAt(level, center, Visual.effect(effect(tier), Visual.Anchor.BODY));
        // An overwound toy bursting, loud enough to carry like a bang; bigger is deeper.
        level.playSound(null, center.x, center.y, center.z, KegareSounds.PUPPET_CURIO_BURST.value(), SoundSource.PLAYERS,
                0.6f + 0.25f * tier.ordinal(), 1.1f - 0.1f * tier.ordinal());
        level.playSound(null, center.x, center.y, center.z, SoundEvents.WOOL_BREAK, SoundSource.PLAYERS, 1.0f, 0.8f);
        // Under the music box, a deep heavy thump: it's a toy, and it hurts (DESIGN §3.23). Bigger is heavier.
        level.playSound(null, center.x, center.y, center.z, KegareSounds.PUPPET_CURIO_THUMP.value(), SoundSource.PLAYERS,
                0.7f + 0.25f * tier.ordinal(), 1.1f - 0.12f * tier.ordinal());

        com.cnethuka.kegare.melee.CleanDodge.areaWentOff(level, owner, com.cnethuka.kegare.melee.CleanDodge.sphere(center, radius));
        // The ground where it went off breaks whether or not it caught anyone (DESIGN §3.24): its full damage and push, the puppet's
        // charge, and the Malice behind it (the cast running now, Curio Burst or Chain Burst, and what the puppet was made of).
        com.cnethuka.kegare.impact.Impacts.blast(level, center, owner, null, damage, knockback + 0.1, 0, tier.ordinal() / 2.0,
                PuppetConfig.TIER_COST[tier.ordinal()].get(), true);
        List<LivingEntity> hurt = new ArrayList<>();
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, new AABB(center, center).inflate(radius),
                e -> e.isAlive() && !e.isSpectator() && !spared(e, owner))) {
            double d = target.position().add(0, target.getBbHeight() / 2, 0).distanceTo(center);
            if (d > radius) continue;
            float scaled = (float) (damage * (1.0 - 0.5 * d / radius));
            if (!target.hurt(source, scaled)) continue;
            hurt.add(target);
            com.cnethuka.kegare.impact.Impacts.charge(target, tier.ordinal() / 2.0); // the puppet's charge: Tap 0, Half 0.5, Full 1
            com.cnethuka.kegare.impact.Impacts.malice(target, PuppetConfig.TIER_COST[tier.ordinal()].get()); // and the Malice it was made of
            if (!CombatHelper.isBoss(target) && knockback > 0) { // bosses: damaged, never moved
                target.knockback(knockback * (1.0 - d / radius) + 0.1, center.x - target.getX(), center.z - target.getZ());
                target.hurtMarked = true;
            }
        }
        return hurt;
    }

    /** The owner, their puppets and their empty body are never hurt by their own bursts. */
    private static boolean spared(LivingEntity e, ServerPlayer owner) {
        return e == owner || e instanceof PuppetEntity p && owner.getUUID().equals(p.ownerId())
                || e instanceof PuppetShellEntity s && owner.getUUID().equals(s.ownerId());
    }

    /**
     * Chain Burst: every puppet of the owner within {@code radius} of {@code target} detonates, nearest first, one
     * after another ({@code delay} apart; the first at once). Returns how many are queued.
     */
    public static int chain(ServerPlayer owner, Entity target) {
        double radius = PuppetConfig.CHAIN_RADIUS.get();
        List<PuppetEntity> puppets = new ArrayList<>(owner.serverLevel().getEntitiesOfClass(PuppetEntity.class,
                target.getBoundingBox().inflate(radius), p -> p.isAlive() && !p.isPossessed() && p.isOwnedBy(owner)
                        && p.distanceToSqr(target) <= radius * radius));
        puppets.sort(Comparator.comparingDouble(p -> p.distanceToSqr(target)));
        long now = owner.level().getGameTime();
        int delay = PuppetConfig.ticks(PuppetConfig.CHAIN_DELAY);
        for (int i = 0; i < puppets.size(); i++) {
            if (i == 0) {
                detonate(puppets.get(0), owner);
            } else {
                QUEUE.add(new Pending(puppets.get(i), owner, now + (long) i * delay));
            }
        }
        return puppets.size();
    }

    /** Every server tick: the queued detonations that are due (dropped if the owner left). */
    static void tick(MinecraftServer server) {
        if (QUEUE.isEmpty()) return;
        List<Pending> due = new ArrayList<>();
        for (Iterator<Pending> it = QUEUE.iterator(); it.hasNext(); ) {
            Pending p = it.next();
            if (p.owner.hasDisconnected() || !p.puppet.isAlive()) {
                it.remove();
            } else if (p.owner.level().getGameTime() >= p.dueTick) {
                due.add(p);
                it.remove();
            }
        }
        for (Pending p : due) {
            com.cnethuka.kegare.progression.Landings.during(p.owner, PuppetManipulation.CHAIN_BURST, () -> detonate(p.puppet, p.owner));
        }
    }

    static int queued() {
        return QUEUE.size();
    }

    static void clear() {
        QUEUE.clear();
    }
}

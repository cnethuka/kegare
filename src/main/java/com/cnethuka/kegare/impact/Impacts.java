package com.cnethuka.kegare.impact;

import com.cnethuka.kegare.Kegare;
import com.cnethuka.kegare.innate_technique.puppet_manipulation.PuppetEntity;
import com.cnethuka.kegare.melee.MeleeImpact;
import com.cnethuka.kegare.melee.MeleeTell;
import com.cnethuka.kegare.progression.Attribution;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The Impact System's server side (DESIGN §3.23). Every hit on a living thing that has an attacker or a weapon behind it (techniques,
 * melee, projectiles, mobs) is gathered through the tick it lands in: the damage it dealt, the knockback it landed with, a throw, the
 * charge it was cast at, a Strike, a guard. At the end of the tick each victim's hits are added up into one {@link Impact}: its
 * strength and tier ({@link ImpactStrength}), where it met the body and which way it was going. Then it goes to the clients near it
 * ({@link ImpactPayload}) and to the listeners (hit sounds, battle damage). Nothing here changes what the hit did.
 */
public final class Impacts {
    private static final Map<LivingEntity, Pending> PENDING = new LinkedHashMap<>();
    /** Knockback this tick per entity: it can come before the damage it belongs to (a Strike pushes, then hurts). */
    private static final Map<LivingEntity, Double> KNOCKS = new LinkedHashMap<>();
    private static final List<Consumer<Impact>> LISTENERS = new CopyOnWriteArrayList<>();

    private Impacts() {}

    public static void init() {
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, Impacts::onDamage);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, Impacts::onKnockback);
        NeoForge.EVENT_BUS.addListener(Impacts::onServerTick);
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent e) -> clear());
        MeleeImpact.listen(Impacts::onMoment);
    }

    /** Called with every resolved hit, on the server, at the end of the tick it landed in. */
    public static void listen(Consumer<Impact> listener) {
        LISTENERS.add(listener);
    }

    public static void remove(Consumer<Impact> listener) {
        LISTENERS.remove(listener);
    }

    /** A hit that throws rather than pushes (it sets the victim's speed itself): how fast it threw them up, in blocks/tick. */
    public static void launch(LivingEntity victim, double speed) {
        Pending p = pending(victim);
        if (p != null) p.launch = Math.max(p.launch, speed);
    }

    /** The charge a hit that just landed on {@code victim} was cast at (0 to 1), where the damage source can't carry it. */
    public static void charge(LivingEntity victim, double charge) {
        Pending p = pending(victim);
        if (p != null) p.charge = Math.max(p.charge, charge);
    }

    /**
     * A hit that deals no damage but still lands on a body (Swap's trades, Shatter Shell's push). It counts with anything else that hit
     * the victim this tick.
     *
     * @param damageShare  what it did to their health, as a share of max health (0 if nothing)
     * @param knockback    its push, as vanilla knockback strength
     * @param charge       the charge it was cast at (an ultimate: 1)
     * @param breaksBlocks false for a hit that never breaks the world
     */
    public static void hit(@Nullable Entity attacker, LivingEntity victim, ImpactKind kind, double damageShare, double knockback, double charge,
                           boolean breaksBlocks) {
        Pending p = pending(victim);
        if (p == null) return;
        p.explicit = true;
        p.attacker(attacker, victim);
        if (p.from == null && attacker != null) p.from = attacker.getEyePosition();
        if (kind == ImpactKind.TECHNIQUE) p.technique = true;
        p.share += Math.max(0, damageShare);
        p.knock = ImpactStrength.stack(p.knock, Math.max(0, knockback));
        p.charge = Math.max(p.charge, charge);
        if (!breaksBlocks) p.noBreak = true;
    }

    // ---------------------------------------------------------------- gathering

    private static @Nullable Pending pending(LivingEntity victim) {
        if (!(victim.level() instanceof ServerLevel level)) return null;
        return PENDING.computeIfAbsent(victim, v -> new Pending(level, v));
    }

    private static void onDamage(LivingDamageEvent.Post event) {
        LivingEntity victim = event.getEntity();
        DamageSource source = event.getSource();
        Entity attacker = source.getEntity(), direct = source.getDirectEntity();
        if (attacker == null && direct == null) return; // falls, fire, drowning: nothing hit them
        if (attacker == victim || event.getNewDamage() <= 0) return;
        Pending p = pending(victim);
        if (p == null) return;
        p.damage += event.getNewDamage();
        p.source = source;
        p.attacker(attacker != null ? attacker : direct, victim);
        if (technique(source)) p.technique = true;
        if (direct instanceof ChargedHit charged) p.charge = Math.max(p.charge, charged.hitCharge());
        if (direct instanceof LivingEntity body && direct == attacker) {
            p.from = body.getEyePosition();
            if (p.weapon.isEmpty()) p.weapon = body.getMainHandItem().copy();
        } else if (direct != null) {
            p.from = direct.getBoundingBox().getCenter();
        } else if (source.getSourcePosition() != null) {
            p.from = source.getSourcePosition();
        }
    }

    /** A Kegare technique's own damage, or a puppet's punch. */
    private static boolean technique(DamageSource source) {
        boolean ours = source.typeHolder().unwrapKey().map(k -> k.location().getNamespace().equals(Kegare.MODID)).orElse(false);
        return ours || source.getEntity() instanceof PuppetEntity || source.getDirectEntity() instanceof PuppetEntity;
    }

    private static void onKnockback(LivingKnockBackEvent event) {
        if (event.isCanceled() || event.getStrength() <= 0 || event.getEntity().level().isClientSide) return;
        KNOCKS.merge(event.getEntity(), (double) event.getStrength(), (sofar, push) -> ImpactStrength.stack(sofar, push));
    }

    private static void onMoment(MeleeImpact moment, ServerLevel level, Vec3 at, @Nullable Entity who, @Nullable Entity other) {
        switch (moment) {
            case STRIKE_HIT -> { // the damage lands just after: the Strike makes it a fully charged hit
                if (who instanceof LivingEntity target) {
                    Pending p = pending(target);
                    if (p != null) p.charge = 1;
                }
            }
            case GUARD_HIT -> { // a guarded hit lands a tier softer
                if (who instanceof LivingEntity defender) {
                    Pending p = pending(defender);
                    if (p != null) p.guarded = true;
                }
            }
            case DASH_BURST -> sendMoment(level, ImpactConfig.Moment.DASH_BURST.tier(), ImpactPayload.SHAKE, at, null, who);
            case CLEAN_DODGE -> sendMoment(level, ImpactConfig.Moment.CLEAN_DODGE.tier(), ImpactPayload.HIT_STOP, at, null, who);
            // The parried attacker is the one it lands on: the stronger shake is theirs.
            case PARRY_PERFECT -> sendMoment(level, ImpactConfig.Moment.PARRY_PERFECT.tier(),
                    ImpactPayload.FLASH | ImpactPayload.HIT_STOP | ImpactPayload.SHAKE, at, who, other);
            case STAGGER -> sendMoment(level, ImpactConfig.Moment.STAGGER.tier(), ImpactPayload.SHAKE, at, null, who);
        }
    }

    // ---------------------------------------------------------------- resolving

    private static void onServerTick(ServerTickEvent.Post event) {
        flush();
    }

    /** Resolves everything gathered so far (the end of the tick; tests call it directly). */
    static void flush() {
        if (PENDING.isEmpty()) {
            KNOCKS.clear();
            return;
        }
        List<Pending> due = new ArrayList<>(PENDING.values());
        Map<LivingEntity, Double> knocks = new LinkedHashMap<>(KNOCKS);
        clear();
        for (Pending p : due) {
            if (!p.explicit && p.damage <= 0) continue; // a hint with no hit behind it (a Strike that was cancelled)
            Double knock = knocks.get(p.victim);
            if (knock != null) p.knock = ImpactStrength.stack(p.knock, knock);
            dispatch(resolve(p));
        }
    }

    static Impact resolve(Pending p) {
        LivingEntity victim = p.victim;
        double share = p.share + p.damage / Math.max(1f, victim.getMaxHealth());
        double strength = ImpactStrength.of(share, p.knock, p.launch, p.charge);
        ImpactTier tier = ImpactStrength.tier(strength);
        if (p.guarded) tier = tier.lower();
        AABB box = victim.getBoundingBox();
        Vec3 centre = box.getCenter();
        Vec3 from = p.from != null ? p.from : centre.add(victim.getLookAngle().scale(-2));
        Vec3 contact = box.contains(from) ? centre : box.clip(from, centre).orElse(centre);
        Vec3 direction = centre.subtract(from);
        direction = direction.lengthSqr() < 1.0e-6 ? victim.getLookAngle().scale(-1) : direction.normalize();
        ImpactKind kind = p.technique ? ImpactKind.TECHNIQUE : ImpactKind.MELEE;
        return new Impact(p.level, tier, strength, kind, p.attacker, victim, contact, direction, p.damage + p.share * victim.getMaxHealth(), p.knock,
                p.launch, p.charge, !p.noBreak, p.technique ? ItemStack.EMPTY : p.weapon, p.source);
    }

    private static void dispatch(Impact impact) {
        int parts = ImpactPayload.FLASH | ImpactPayload.HIT_STOP | ImpactPayload.SHAKE
                | (impact.tier().ordinal() >= ImpactTier.IMPACT_FRAME.ordinal() ? ImpactPayload.FRAME : 0);
        send(impact.level(), impact.tier(), impact.kind(), parts, impact.contact(), impact.direction(), impact.attacker(), impact.victim(),
                (float) impact.strength());
        LISTENERS.forEach(listener -> listener.accept(impact));
    }

    private static void sendMoment(ServerLevel level, ImpactTier tier, int parts, Vec3 at, @Nullable Entity attacker, @Nullable Entity victim) {
        Vec3 direction = attacker != null && victim != null ? victim.position().subtract(attacker.position()) : Vec3.ZERO;
        direction = direction.lengthSqr() < 1.0e-6 ? new Vec3(0, 0, 1) : direction.normalize();
        send(level, tier, ImpactKind.MELEE, parts, at, direction, attacker, victim, 0f);
    }

    /**
     * To the attacker and the victim, and (when there's a flash) everyone else within {@link ImpactConfig#WITNESS_RANGE}. A technique's
     * flash is in its Malice color: the attacker sees their own, everyone else the color of the face they wear (DESIGN §1, §3.19).
     */
    private static void send(ServerLevel level, ImpactTier tier, ImpactKind kind, int parts, Vec3 contact, Vec3 direction,
                             @Nullable Entity attacker, @Nullable Entity victim, float strength) {
        ServerPlayer striker = attacker instanceof ServerPlayer sp ? sp : null;
        int own = 0, seen = 0;
        if (kind == ImpactKind.TECHNIQUE && striker != null) {
            own = color(MeleeTell.of(striker));
            seen = color(MeleeTell.seenByOthers(striker));
        }
        int attackerId = attacker == null ? -1 : attacker.getId(), victimId = victim == null ? -1 : victim.getId();
        ImpactPayload toStriker = new ImpactPayload(tier, kind, parts, contact, direction, own, attackerId, victimId, strength);
        ImpactPayload toOthers = new ImpactPayload(tier, kind, parts, contact, direction, seen, attackerId, victimId, strength);
        double range = ImpactConfig.WITNESS_RANGE.get();
        boolean flash = (parts & ImpactPayload.FLASH) != 0;
        for (ServerPlayer player : level.players()) {
            boolean involved = player == attacker || player == victim;
            if (!involved && (!flash || player.distanceToSqr(contact) > range * range)) continue;
            PacketDistributor.sendToPlayer(player, player == striker ? toStriker : toOthers);
        }
    }

    private static int color(MeleeTell.Look look) {
        return look.kind() == MeleeTell.Kind.GLOW ? look.color() & 0xFFFFFF : 0;
    }

    static void clear() {
        PENDING.clear();
        KNOCKS.clear();
    }

    /** One victim's hits in this tick, adding up. */
    static final class Pending {
        final ServerLevel level;
        final LivingEntity victim;
        @Nullable Entity attacker;
        @Nullable Vec3 from;
        @Nullable DamageSource source;
        double damage, share, knock, launch, charge;
        boolean explicit, technique, guarded, noBreak;
        ItemStack weapon = ItemStack.EMPTY;

        Pending(ServerLevel level, LivingEntity victim) {
            this.level = level;
            this.victim = victim;
        }

        /** Credit goes to a player: a puppet's owner for the puppet's punch or burst. */
        void attacker(@Nullable Entity who, LivingEntity victim) {
            if (who instanceof PuppetEntity puppet && puppet.ownerId() != null) {
                ServerPlayer owner = Attribution.player(level.getServer(), puppet.ownerId());
                if (owner != null) who = owner;
            }
            if (who != null && who != victim && attacker == null) attacker = who;
        }
    }
}

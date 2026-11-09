package com.cnethuka.kegare.sixeyes;

import com.cnethuka.kegare.combat.CombatHelper;
import com.cnethuka.kegare.innate_technique.puppet_manipulation.Possession;
import com.cnethuka.kegare.innate_technique.puppet_manipulation.PuppetEntity;
import com.cnethuka.kegare.innate_technique.rime.FreezeService;
import com.cnethuka.kegare.innatetechnique.Burnout;
import com.cnethuka.kegare.innatetechnique.CastManager;
import com.cnethuka.kegare.innatetechnique.CastState;
import com.cnethuka.kegare.innatetechnique.CastType;
import com.cnethuka.kegare.innatetechnique.InnateTechnique;
import com.cnethuka.kegare.innatetechnique.InnateTechniqueData;
import com.cnethuka.kegare.innatetechnique.InnateTechniqueService;
import com.cnethuka.kegare.malice.MaliceData;
import com.cnethuka.kegare.malice.MaliceService;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;
import javax.annotation.Nullable;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Six Eyes' Reads (DESIGN §3.32). The eyes perceive everything about what they look at. Every player near enough to be in view gets a compact
 * Read automatically (their innate technique, current Malice and health; a void says only "Void"), every mob in view its health (its kind and
 * flow come from what the eyes already see, {@link Sight}), and the one body nearest the bearer's crosshair, player or mob, its whole state:
 * what they're doing, health, armour, attack and movement as they are now, every effect, what's happening to them and, for a player, hunger.
 * The server decides the focus from where the bearer looks, and only the bearer is sent any of it. Never Grade, Assessment, Refinement,
 * Insight, traits, the brain meter or anything §1 keeps secret.
 */
public final class Reads {
    /** Players this close are read (render distance's reach, round). */
    public static final double RANGE = 96.0;
    /** The focus: within this many degrees of the crosshair, nearest to it. */
    public static final double FOCUS_DEGREES = 20.0;
    public static final int EVERY = 4;
    /** Mobs read at a glance: this close (where a tag fades to a trace), in front of the bearer, the nearest {@link #MAX_MOBS}. */
    public static final double MOB_RANGE = 64.0, VIEW_DEGREES = 80.0;
    public static final int MAX_MOBS = 24;
    /** Told about what's sent to whom (tests). */
    static BiConsumer<ServerPlayer, Object> sentHook;
    /** Who each bearer focused last, to tell listeners of a new one. */
    private static final Map<UUID, Integer> FOCUSED = new HashMap<>();
    /** Told when a bearer's focus moves to someone new (Attunement; "Something's Missing"). */
    private static final List<BiConsumer<ServerPlayer, ServerPlayer>> FOCUS_LISTENERS = new CopyOnWriteArrayList<>();

    private Reads() {}

    static void init() {
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post e) -> {
            if (e.getServer().getTickCount() % EVERY == 0) tick(e.getServer());
        });
    }

    public static void addFocusListener(BiConsumer<ServerPlayer, ServerPlayer> listener) {
        FOCUS_LISTENERS.add(listener);
    }

    static void tick(MinecraftServer server) {
        for (ServerPlayer bearer : SixEyes.bearers(server)) {
            ReadsPayload reads = build(bearer);
            if (sentHook != null) sentHook.accept(bearer, reads);
            PacketDistributor.sendToPlayer(bearer, reads);
        }
    }

    /** What this bearer reads now. */
    public static ReadsPayload build(ServerPlayer bearer) {
        List<ReadsPayload.Glance> glances = new ArrayList<>();
        for (ServerPlayer other : bearer.serverLevel().players()) {
            if (other == bearer || other.isSpectator() || !other.isAlive() || other.distanceTo(bearer) > RANGE) continue;
            glances.add(glance(other));
        }
        List<ReadsPayload.MobGlance> mobs = new ArrayList<>();
        for (LivingEntity mob : mobsInView(bearer)) mobs.add(new ReadsPayload.MobGlance(mob.getId(), mob.getHealth(), mob.getMaxHealth()));
        LivingEntity focus = focus(bearer);
        Integer before = FOCUSED.put(bearer.getUUID(), focus == null ? null : focus.getId());
        if (focus instanceof ServerPlayer player && (before == null || before != focus.getId())) for (var l : FOCUS_LISTENERS) l.accept(bearer, player);
        return new ReadsPayload(glances, mobs, Optional.ofNullable(focus).map(Reads::detail));
    }

    public static ReadsPayload.Glance glance(ServerPlayer other) {
        MaliceData malice = MaliceService.get(other);
        if (malice.isSevered()) return new ReadsPayload.Glance(other.getId(), Optional.empty());
        return new ReadsPayload.Glance(other.getId(), Optional.of(new ReadsPayload.Energy(InnateTechniqueService.current(other).map(InnateTechnique::id),
                (int) Math.floor(malice.current()), other.getHealth(), other.getMaxHealth())));
    }

    /** A mob the eyes read: alive and not a player, an armour stand, or a puppet hidden inside whoever possesses it (they're read instead). */
    static boolean readableMob(Entity e) {
        return e instanceof LivingEntity living && !(e instanceof Player) && !(e instanceof ArmorStand) && living.isAlive()
                && !(e instanceof PuppetEntity puppet && puppet.isPossessed());
    }

    /** The mobs in front of the bearer, within {@link #MOB_RANGE}, nearest first, at most {@link #MAX_MOBS}. */
    static List<LivingEntity> mobsInView(ServerPlayer bearer) {
        Vec3 eye = bearer.getEyePosition(), look = bearer.getLookAngle();
        List<LivingEntity> out = new ArrayList<>();
        for (Entity e : bearer.serverLevel().getEntities(bearer, bearer.getBoundingBox().inflate(MOB_RANGE), Reads::readableMob)) {
            if (e.distanceTo(bearer) <= MOB_RANGE && angle(eye, look, e) <= VIEW_DEGREES) out.add((LivingEntity) e);
        }
        out.sort(Comparator.comparingDouble(e -> e.distanceToSqr(bearer)));
        return out.size() > MAX_MOBS ? out.subList(0, MAX_MOBS) : out;
    }

    private static double angle(Vec3 eye, Vec3 look, Entity e) {
        Vec3 to = e.position().add(0, e.getBbHeight() * 0.6, 0).subtract(eye);
        return Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, to.normalize().dot(look)))));
    }

    /**
     * The body nearest the bearer's crosshair, player or mob, within {@link #FOCUS_DEGREES} and {@link #RANGE}; walls don't matter to the
     * eyes.
     */
    @Nullable
    public static LivingEntity focus(ServerPlayer bearer) {
        Vec3 eye = bearer.getEyePosition(), look = bearer.getLookAngle();
        LivingEntity best = null;
        double bestAngle = FOCUS_DEGREES;
        for (ServerPlayer other : bearer.serverLevel().players()) {
            if (other == bearer || other.isSpectator() || !other.isAlive() || other.distanceTo(bearer) > RANGE) continue;
            double angle = angle(eye, look, other);
            if (angle < bestAngle) {
                bestAngle = angle;
                best = other;
            }
        }
        for (Entity e : bearer.serverLevel().getEntities(bearer, bearer.getBoundingBox().inflate(RANGE), Reads::readableMob)) {
            double angle = angle(eye, look, e);
            if (e.distanceTo(bearer) <= RANGE && angle < bestAngle) {
                bestAngle = angle;
                best = (LivingEntity) e;
            }
        }
        return best;
    }

    /**
     * Full detail on one body: a void's is empty, as it has no energy to read. A disguised player is their own body (the face is all that's
     * borrowed); a puppet or a Stand-In is its own.
     */
    public static ReadsPayload.Focus detail(LivingEntity body) {
        if (body instanceof ServerPlayer player && MaliceService.isSevered(player)) return new ReadsPayload.Focus(body.getId(), Optional.empty());
        Optional<ReadsPayload.Cast> cast = Optional.empty();
        List<ReadsPayload.Cooldown> cooldowns = new ArrayList<>();
        List<ResourceLocation> toggles = new ArrayList<>();
        if (body instanceof ServerPlayer other) {
            cast = CastManager.currentCast(other).map(active -> cast(other, active));
            long now = other.level().getGameTime();
            for (Map.Entry<ResourceLocation, InnateTechniqueData.Cooldown> e : InnateTechniqueService.get(other).cooldowns().entrySet()) {
                long left = e.getValue().endTick() - now;
                if (left > 0) cooldowns.add(new ReadsPayload.Cooldown(e.getKey(), left / 20f));
            }
            for (CastState.Active t : CastManager.activeToggles(other)) toggles.add(t.ability().id());
        }
        return new ReadsPayload.Focus(body.getId(), Optional.of(new ReadsPayload.Body(cast, cooldowns, toggles, vitals(body), effects(body), status(body))));
    }

    /** Health, defence, offence and movement as they are right now, with every modifier counted. */
    static ReadsPayload.Vitals vitals(LivingEntity body) {
        AttributeInstance movement = body.getAttribute(Attributes.MOVEMENT_SPEED);
        return new ReadsPayload.Vitals(body.getHealth(), body.getMaxHealth(), body.getAbsorptionAmount(), value(body, Attributes.ARMOR),
                value(body, Attributes.ARMOR_TOUGHNESS), value(body, Attributes.KNOCKBACK_RESISTANCE), value(body, Attributes.ATTACK_DAMAGE),
                value(body, Attributes.ATTACK_SPEED), movement == null ? ReadsPayload.Vitals.NONE : (float) movement.getValue(),
                movement == null ? ReadsPayload.Vitals.NONE : (float) movement.getBaseValue(),
                body instanceof Player player ? player.getFoodData().getFoodLevel() : -1);
    }

    private static float value(LivingEntity body, Holder<Attribute> attribute) {
        AttributeInstance instance = body.getAttribute(attribute);
        return instance == null ? ReadsPayload.Vitals.NONE : (float) instance.getValue();
    }

    /** Every active effect, the hidden ones too: the eyes see what's in the body, not what shows. */
    static List<ReadsPayload.Effect> effects(LivingEntity body) {
        List<ReadsPayload.Effect> out = new ArrayList<>();
        for (MobEffectInstance effect : body.getActiveEffects()) {
            ResourceLocation id = BuiltInRegistries.MOB_EFFECT.getKey(effect.getEffect().value());
            if (id != null) out.add(new ReadsPayload.Effect(id, effect.getAmplifier(), effect.isInfiniteDuration() ? -1 : effect.getDuration()));
        }
        out.sort(Comparator.comparing(e -> e.effect().toString()));
        return out;
    }

    static ReadsPayload.Status status(LivingEntity body) {
        ServerPlayer player = body instanceof ServerPlayer p ? p : null;
        return new ReadsPayload.Status(body.isOnFire(), FreezeService.stacks(body), CombatHelper.stunTicksRemaining(body),
                player == null ? 0 : Burnout.remaining(player), player != null && Possession.isPossessing(player));
    }

    private static ReadsPayload.Cast cast(ServerPlayer other, CastState.Active active) {
        var ability = active.ability();
        float progress;
        if (ability.castType() == CastType.CHARGED) {
            int max = ability.maxChargeTicks(other);
            progress = max <= 0 ? 1f : Math.min(1f, active.activeTicks() / (float) max);
        } else {
            int max = ability.maxChannelTicks(other);
            progress = max <= 0 ? -active.activeTicks() / 20f : Math.min(1f, active.activeTicks() / (float) max);
        }
        return new ReadsPayload.Cast(ability.id(), ability.castType().ordinal(), progress);
    }

    static void forget(UUID bearer) {
        FOCUSED.remove(bearer);
    }
}

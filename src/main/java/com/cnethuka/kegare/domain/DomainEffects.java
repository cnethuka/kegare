package com.cnethuka.kegare.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

/**
 * Strength, Resistance and Speed on an Incomplete Domain's caster while it stands (DESIGN §3.35): closed, not rising, not
 * contested. Vanilla effects, so they show as usual. Each lasts until the Domain's time is up (a crash can't leave one behind for
 * longer), and goes the moment it stops standing. Only what was put on here is taken off: a potion of their own stays.
 */
public final class DomainEffects {
    private record Given(Holder<MobEffect> effect, int amplifier) {}

    private static final Map<UUID, List<Given>> GIVEN = new ConcurrentHashMap<>();

    private DomainEffects() {}

    static void init() {
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post e) -> tick());
    }

    /** Its effects and their levels (0: level I; -1: none). */
    private static List<Given> wanted() {
        List<Given> out = new ArrayList<>();
        add(out, MobEffects.DAMAGE_BOOST, DomainConfig.INCOMPLETE_STRENGTH);
        add(out, MobEffects.DAMAGE_RESISTANCE, DomainConfig.INCOMPLETE_RESISTANCE);
        add(out, MobEffects.MOVEMENT_SPEED, DomainConfig.INCOMPLETE_SPEED);
        return out;
    }

    private static void add(List<Given> out, Holder<MobEffect> effect, ModConfigSpec.IntValue level) {
        if (level.get() >= 0) out.add(new Given(effect, level.get()));
    }

    /** Its caster, every tick it stands: their effects, lasting as long as it has left. */
    static void standing(ServerPlayer caster, DomainEntity d) {
        int left = Math.max(1, d.duration() - (d.tickCount - d.closedAt()));
        List<Given> given = GIVEN.computeIfAbsent(caster.getUUID(), id -> new ArrayList<>());
        for (Given g : wanted()) {
            MobEffectInstance now = caster.getEffect(g.effect());
            if (now != null && ours(now, g) && Math.abs(now.getDuration() - left) <= 1) continue;
            caster.addEffect(new MobEffectInstance(g.effect(), left, g.amplifier(), false, true, true));
            if (!given.contains(g)) given.add(g);
        }
    }

    /** Takes off what was put on them here (not a potion of their own). */
    static void takeOff(ServerPlayer player) {
        List<Given> given = GIVEN.remove(player.getUUID());
        if (given == null) return;
        for (Given g : given) {
            MobEffectInstance now = player.getEffect(g.effect());
            if (now != null && ours(now, g)) player.removeEffect(g.effect());
        }
    }

    private static boolean ours(MobEffectInstance now, Given g) {
        return now.getAmplifier() == g.amplifier() && !now.isInfiniteDuration() && !now.isAmbient();
    }

    /** Anyone who had them and whose Domain no longer stands round them (it ended, or a clash began): off. */
    private static void tick() {
        if (GIVEN.isEmpty()) return;
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        for (UUID id : List.copyOf(GIVEN.keySet())) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p == null) GIVEN.remove(id);
            else if (!IncompleteDomains.boosted(p)) takeOff(p);
        }
    }

    /** Whether these effects are on them from their Domain (tests). */
    static boolean has(ServerPlayer player) {
        return GIVEN.containsKey(player.getUUID());
    }
}

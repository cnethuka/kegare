package com.cnethuka.kegare.innate_technique.swap;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/**
 * Each caster's one anchor (runtime only). An anchor is used up by a recall or Send Off, times out after its lifetime,
 * and is dropped if its caster dies, logs out or changes dimension. Throwing a new one replaces the old.
 */
public final class Anchors {
    /** Caster -> their anchor. Removed entities (used, timed out, unloaded) read as gone. */
    private static final Map<UUID, AnchorEntity> ACTIVE = new HashMap<>();

    private Anchors() {}

    /** The caster's anchor, if it is still out there. */
    public static Optional<AnchorEntity> get(ServerPlayer caster) {
        AnchorEntity anchor = ACTIVE.get(caster.getUUID());
        if (anchor == null) return Optional.empty();
        if (anchor.isAlive() && anchor.level() == caster.level()) return Optional.of(anchor);
        ACTIVE.remove(caster.getUUID()); // gone (used, timed out, unloaded, or the caster is in another dimension)
        return Optional.empty();
    }

    public static boolean has(ServerPlayer caster) {
        return get(caster).isPresent();
    }

    static void set(ServerPlayer caster, AnchorEntity anchor) {
        drop(caster);
        ACTIVE.put(caster.getUUID(), anchor);
    }

    /** Used up (recall, Send Off). */
    static void consume(ServerPlayer caster, AnchorEntity anchor) {
        ACTIVE.remove(caster.getUUID());
        anchor.discard();
    }

    /** Timed out: it fades with a sound. */
    static void expire(AnchorEntity anchor) {
        ACTIVE.values().remove(anchor);
        if (anchor.level() instanceof ServerLevel level) {
            level.playSound(null, anchor.getX(), anchor.getY(), anchor.getZ(), SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.PLAYERS, 0.7f, 1.5f);
            Swapper.depart(anchor);
        }
        anchor.discard();
    }

    /** The caster's anchor goes away (death, logout, dimension change, a new throw), wherever it is. */
    static void drop(ServerPlayer caster) {
        AnchorEntity anchor = ACTIVE.remove(caster.getUUID());
        if (anchor != null && !anchor.isRemoved()) anchor.discard();
    }

    static void clear() {
        ACTIVE.clear();
    }
}

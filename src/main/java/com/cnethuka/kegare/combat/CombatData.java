package com.cnethuka.kegare.combat;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Combat timestamps in level game time (attachment {@code kegare:combat}).
 * Saved, so logging out mid-fight doesn't clear the combat tag, but NOT kept on death:
 * a respawned player starts clean.
 */
public final class CombatData {
    public static final Codec<CombatData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.LONG.optionalFieldOf("last_combat", -1L).forGetter(d -> d.lastCombatTick),
            Codec.LONG.optionalFieldOf("stunned_until", 0L).forGetter(d -> d.stunnedUntil)
    ).apply(i, CombatData::new));

    long lastCombatTick;
    long stunnedUntil;

    public CombatData() {
        this(-1L, 0L);
    }

    private CombatData(long lastCombatTick, long stunnedUntil) {
        this.lastCombatTick = lastCombatTick;
        this.stunnedUntil = stunnedUntil;
    }
}

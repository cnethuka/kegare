package com.cnethuka.kegare.progression;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.UUIDUtil;

/** A player's recent qualifying fights, oldest first. Private, kept on reroll and death. */
public final class AssessmentData {
    private static final Codec<AssessmentRules.Entry> ENTRY_CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.DOUBLE.fieldOf("score").forGetter(AssessmentRules.Entry::score),
            UUIDUtil.CODEC.fieldOf("opponent").forGetter(AssessmentRules.Entry::opponent),
            Codec.LONG.fieldOf("tick").forGetter(AssessmentRules.Entry::tick)
    ).apply(i, AssessmentRules.Entry::new));

    public static final Codec<AssessmentData> CODEC = RecordCodecBuilder.create(i -> i.group(
            ENTRY_CODEC.listOf().optionalFieldOf("history", List.of()).forGetter(d -> d.history)
    ).apply(i, AssessmentData::new));

    /** More than any requirement looks back over. */
    private static final int KEPT = 64;

    final List<AssessmentRules.Entry> history;

    public AssessmentData() {
        this(List.of());
    }

    private AssessmentData(List<AssessmentRules.Entry> history) {
        this.history = new ArrayList<>(history);
    }

    void add(AssessmentRules.Entry entry) {
        history.add(entry);
        while (history.size() > KEPT) history.remove(0);
    }

    public List<AssessmentRules.Entry> history() {
        return List.copyOf(history);
    }
}

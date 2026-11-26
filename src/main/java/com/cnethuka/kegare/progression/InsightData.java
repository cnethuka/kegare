package com.cnethuka.kegare.progression;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** A player's Insight, kept on reroll and death: the three values and every first that has already paid. */
public final class InsightData {
    public static final Codec<InsightData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.optionalFieldOf("domain", 0).forGetter(d -> d.value(InsightCategory.DOMAIN)),
            // Saved before Dominions became Domains: read, never written.
            Codec.INT.optionalFieldOf("dominion", 0).forGetter(d -> 0),
            Codec.INT.optionalFieldOf("ward", 0).forGetter(d -> d.value(InsightCategory.WARD)),
            Codec.INT.optionalFieldOf("output", 0).forGetter(d -> d.value(InsightCategory.OUTPUT)),
            Codec.STRING.listOf().optionalFieldOf("firsts", List.of()).forGetter(d -> new ArrayList<>(d.firsts))
    ).apply(i, InsightData::new));

    final Map<InsightCategory, Integer> values = new EnumMap<>(InsightCategory.class);
    final Set<String> firsts = new HashSet<>();

    public InsightData() {}

    private InsightData(int domain, int oldDomain, int ward, int output, List<String> firsts) {
        values.put(InsightCategory.DOMAIN, domain + oldDomain);
        values.put(InsightCategory.WARD, ward);
        values.put(InsightCategory.OUTPUT, output);
        for (String first : firsts) this.firsts.add(first.replace("dominion", "domain")); // as they were named before Domains
    }

    public int value(InsightCategory category) {
        return values.getOrDefault(category, 0);
    }
}

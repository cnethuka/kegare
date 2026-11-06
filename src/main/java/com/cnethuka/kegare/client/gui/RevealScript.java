package com.cnethuka.kegare.client.gui;

import com.cnethuka.kegare.innatetechnique.InnateTechniqueRarity;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;

/**
 * The order and timing of a roll reveal's scenes. Plain Java (no Minecraft types), so the timeline is unit-testable.
 * {@link RevealScreen} draws whichever scene the clock is in; the last step, {@link Scene#RESULT}, holds until the player closes it.
 */
public final class RevealScript {
    public enum Scene {
        /** A talisman burns; the flame's color hints at the rarity. */
        OMEN,
        /** Severed birth: the talisman burns out to grey ash. */
        BURN_OUT,
        RARITY,
        STARS,
        EPITHET,
        NAME,
        /** Birth: Malice capacity and tier. */
        MALICE,
        /** Severed birth: "Heaven Gave You Nothing". */
        HEAVEN,
        /** Severed birth: "Born without Malice". */
        BORN_WITHOUT,
        SIX_EYES,
        RESULT
    }

    /** One scene on the timeline, in milliseconds since the reveal began. */
    public record Step(Scene scene, long start, long length) {
        public long end() {
            return start + length;
        }

        public long age(long now) {
            return Math.max(0L, now - start);
        }

        /** 0 at the scene's start, 1 at its end (and always 1 for the open-ended {@link Scene#RESULT}). */
        public float progress(long now) {
            return length <= 0 ? 1f : Math.min(1f, age(now) / (float) length);
        }
    }

    /**
     * @param rarity  the rolled technique's rarity, null if there's no technique (Severed, or nothing was eligible)
     * @param severed a Severed birth, which has its own scenes
     * @param birth   Awakening: adds the Malice scene
     * @param sixEyes born with Six Eyes: adds its scene after the technique
     * @param full    the technique's full intro; false is the short version for a technique already seen (Rarity, then Name)
     */
    public record Input(@Nullable InnateTechniqueRarity rarity, boolean severed, boolean birth, boolean sixEyes, boolean full) {}

    /** Scene lengths in a full reveal. The total is the rarity's whole length: about 3, 5, 7 and 10 seconds. */
    private record Beats(long omen, long rarity, long stars, long epithet, long name) {}

    private static final Beats COMMON = new Beats(1100, 800, 0, 0, 1100);
    private static final Beats RARE = new Beats(1300, 800, 1200, 0, 1700);
    private static final Beats EPIC = new Beats(1500, 900, 1400, 1900, 1300);
    private static final Beats LEGENDARY = new Beats(2400, 1200, 1800, 2400, 2200);
    private static final long SHORT_RARITY = 700;
    private static final long SHORT_NAME = 1000;
    private static final long MALICE = 2000;
    private static final long SIX_EYES = 3800;
    private static final long BURN_OUT = 2800;
    private static final long HEAVEN = 2200;
    private static final long BORN_WITHOUT = 1700;

    /** How far through the Omen a Legendary's fake-out flares from purple to gold. */
    public static final float FAKE_OUT_AT = 0.5f;

    private final List<Step> steps;
    private final long total;

    private RevealScript(List<Step> steps, long total) {
        this.steps = List.copyOf(steps);
        this.total = total;
    }

    public static RevealScript of(Input input) {
        List<Scene> scenes = new ArrayList<>();
        List<Long> lengths = new ArrayList<>();
        if (input.severed()) {
            add(scenes, lengths, Scene.BURN_OUT, BURN_OUT);
            add(scenes, lengths, Scene.HEAVEN, HEAVEN);
            add(scenes, lengths, Scene.BORN_WITHOUT, BORN_WITHOUT);
        } else if (input.rarity() != null) {
            if (input.full()) {
                Beats beats = beats(input.rarity());
                add(scenes, lengths, Scene.OMEN, beats.omen());
                add(scenes, lengths, Scene.RARITY, beats.rarity());
                if (beats.stars() > 0) add(scenes, lengths, Scene.STARS, beats.stars());
                if (beats.epithet() > 0) add(scenes, lengths, Scene.EPITHET, beats.epithet());
                add(scenes, lengths, Scene.NAME, beats.name());
            } else {
                add(scenes, lengths, Scene.RARITY, SHORT_RARITY);
                add(scenes, lengths, Scene.NAME, SHORT_NAME);
            }
        }
        if (input.birth() && !input.severed()) add(scenes, lengths, Scene.MALICE, MALICE);
        if (input.sixEyes()) add(scenes, lengths, Scene.SIX_EYES, SIX_EYES);

        List<Step> steps = new ArrayList<>();
        long at = 0;
        for (int i = 0; i < scenes.size(); i++) {
            steps.add(new Step(scenes.get(i), at, lengths.get(i)));
            at += lengths.get(i);
        }
        steps.add(new Step(Scene.RESULT, at, 0));
        return new RevealScript(steps, at);
    }

    private static void add(List<Scene> scenes, List<Long> lengths, Scene scene, long length) {
        scenes.add(scene);
        lengths.add(length);
    }

    private static Beats beats(InnateTechniqueRarity rarity) {
        return switch (rarity) {
            case COMMON -> COMMON;
            case RARE -> RARE;
            case EPIC -> EPIC;
            case LEGENDARY -> LEGENDARY;
        };
    }

    public List<Step> steps() {
        return steps;
    }

    /** When the last scene ends and {@link Scene#RESULT} begins. */
    public long total() {
        return total;
    }

    public Step at(long now) {
        for (Step step : steps) {
            if (now < step.end()) return step;
        }
        return steps.get(steps.size() - 1);
    }

    /** How many stars a rarity earns: Common 1, Rare 2, Epic 3, Legendary 4. */
    public static int stars(InnateTechniqueRarity rarity) {
        return rarity.ordinal() + 1;
    }

    /** Which rarity's color the Omen's flame shows, {@code progress} (0-1) through the scene. */
    public static InnateTechniqueRarity omenFlame(InnateTechniqueRarity rarity, boolean fakeOut, float progress) {
        return fakeOut && rarity == InnateTechniqueRarity.LEGENDARY && progress < FAKE_OUT_AT ? InnateTechniqueRarity.EPIC : rarity;
    }

    /**
     * Whether this roll plays the full intro: the first time a technique is rolled on this client, or always with
     * {@code reveal.alwaysFull}. Later rolls of it play the short version.
     */
    public static boolean playsFull(boolean alreadySeen, boolean alwaysFull) {
        return alwaysFull || !alreadySeen;
    }
}

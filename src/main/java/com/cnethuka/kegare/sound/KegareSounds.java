package com.cnethuka.kegare.sound;

import com.cnethuka.kegare.Kegare;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Kegare's own sounds (DESIGN §3.10). Each is made in Python from nothing ({@code tools/sounds/}, no recordings), ships as a mono OGG
 * Vorbis at {@code assets/kegare/sounds/<file>.ogg} so it's heard from where it's played, and has a subtitle. {@code sounds.json}
 * is generated from {@link #ALL} ({@code KegareSoundProvider}); {@code SoundsTest} checks the file, the subtitle and the entry agree.
 * A new sound: its recipe in {@code tools/sounds/}, one line here, its subtitle in {@code KegareLanguageProvider}, then runData.
 */
public final class KegareSounds {
    public static final DeferredRegister<SoundEvent> SOUND_EVENTS = DeferredRegister.create(Registries.SOUND_EVENT, Kegare.MODID);

    /** One sound: its event ({@code kegare:<name>}), its file under {@code sounds/} without the extension, and its subtitle key. */
    public record Sound(String name, DeferredHolder<SoundEvent, SoundEvent> event, String file, String subtitle) {}

    private static final List<Sound> SOUNDS = new ArrayList<>();
    /** Every sound, in registration order. */
    public static final List<Sound> ALL = Collections.unmodifiableList(SOUNDS);

    // Swap (§3.19): the clap and its relatives.
    public static final DeferredHolder<SoundEvent, SoundEvent> SWAP_CLAP = register("swap.clap", "swap/clap");
    public static final DeferredHolder<SoundEvent, SoundEvent> SWAP_DOUBLE_CLAP = register("swap.double_clap", "swap/double_clap");
    public static final DeferredHolder<SoundEvent, SoundEvent> SWAP_SNAP = register("swap.snap", "swap/snap");
    public static final DeferredHolder<SoundEvent, SoundEvent> SWAP_CUPPED_CLAP = register("swap.cupped_clap", "swap/cupped_clap");
    public static final DeferredHolder<SoundEvent, SoundEvent> SWAP_BIG_CLAP = register("swap.big_clap", "swap/big_clap");
    public static final DeferredHolder<SoundEvent, SoundEvent> SWAP_FLIP = register("swap.flip", "swap/flip");
    public static final DeferredHolder<SoundEvent, SoundEvent> SWAP_FLICK = register("swap.flick", "swap/flick");
    public static final DeferredHolder<SoundEvent, SoundEvent> SWAP_TOKEN_LAND = register("swap.token_land", "swap/token_land");
    public static final DeferredHolder<SoundEvent, SoundEvent> SWAP_SHOVE = register("swap.shove", "swap/shove");
    public static final DeferredHolder<SoundEvent, SoundEvent> SWAP_TRADE = register("swap.trade", "swap/trade");

    // Puppet Manipulation (§3.20): Curio Burst's overwound toy bursting (the rest of its sounds are vanilla's).
    public static final DeferredHolder<SoundEvent, SoundEvent> PUPPET_CURIO_BURST = register("puppet_manipulation.curio_burst",
            "puppet_manipulation/curio_burst");
    /** A deep, heavy thump under the music box: the creepy toy actually hurts (§3.23). */
    public static final DeferredHolder<SoundEvent, SoundEvent> PUPPET_CURIO_THUMP = register("puppet_manipulation.curio_thump",
            "puppet_manipulation/curio_thump");

    // The Impact System's hit sounds (§3.23), played with vanilla's hurt sound, never instead of it.
    public static final DeferredHolder<SoundEvent, SoundEvent> IMPACT_PUNCH = register("impact.punch", "impact/punch");
    public static final DeferredHolder<SoundEvent, SoundEvent> IMPACT_BLADE = register("impact.blade", "impact/blade");
    public static final DeferredHolder<SoundEvent, SoundEvent> IMPACT_HEAVY = register("impact.heavy", "impact/heavy");
    public static final DeferredHolder<SoundEvent, SoundEvent> IMPACT_BOOM = register("impact.boom", "impact/boom");

    private KegareSounds() {}

    private static DeferredHolder<SoundEvent, SoundEvent> register(String name, String file) {
        DeferredHolder<SoundEvent, SoundEvent> event = SOUND_EVENTS.register(name, () -> SoundEvent.createVariableRangeEvent(Kegare.id(name)));
        SOUNDS.add(new Sound(name, event, file, "subtitles.kegare." + name));
        return event;
    }
}

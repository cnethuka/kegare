package com.cnethuka.kegare.innatetechnique;

import com.cnethuka.kegare.trait.Trait;
import com.cnethuka.kegare.trait.TraitService;
import java.util.function.Predicate;
import java.util.function.Supplier;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * A condition an ability (usually a special) needs before it can be cast. Checked server-side with the shared cast
 * checks; when one is unmet the cast fizzles with a message and costs nothing. Running charges re-check at release,
 * and channels/toggles end when one lapses.
 * <p>
 * {@link #description()} must be safe on the client (the Sorcerer screen lists it) and read naturally in both
 * "Splinter requires %s." and "Requires %s", e.g. "a rare trait" or "Rime Shell to be active".
 */
public interface Requirement {
    boolean isMet(ServerPlayer player);

    Component description();

    /** The caster holds this trait. Said as "a rare trait": what's on a card and in a message never names one (DESIGN §1). */
    static Requirement trait(Supplier<? extends Trait> trait) {
        return of(player -> TraitService.has(player, trait.get()),
                () -> Component.translatable("requirement.kegare.trait"));
    }

    /** This TOGGLE ability of the caster's is switched on. */
    static Requirement toggleActive(Ability toggle) {
        return of(player -> CastManager.isToggleActive(player, toggle),
                () -> Component.translatable("requirement.kegare.toggle_active", toggle.displayName()));
    }

    /** Anything else: a server-side test and its description. */
    static Requirement of(Predicate<ServerPlayer> test, Supplier<Component> description) {
        return new Requirement() {
            @Override
            public boolean isMet(ServerPlayer player) {
                return test.test(player);
            }

            @Override
            public Component description() {
                return description.get();
            }
        };
    }
}

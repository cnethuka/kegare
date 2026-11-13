package com.cnethuka.kegare.api.ability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cnethuka.kegare.innatetechnique.Ability;
import com.cnethuka.kegare.innatetechnique.AbilityContext;
import com.cnethuka.kegare.innatetechnique.BaseAbility;
import com.cnethuka.kegare.innatetechnique.CastType;
import com.cnethuka.kegare.innatetechnique.InnateTechnique;
import com.cnethuka.kegare.innatetechnique.InnateTechniqueRarity;
import com.cnethuka.kegare.innatetechnique.SpecialAbility;
import com.cnethuka.kegare.progression.UnlockTable;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.junit.jupiter.api.Test;

class TechniqueAbilityContributionsTest {
    private static final ResourceLocation TARGET = id("target");

    @Test
    void insertsDeterministicallyAndAppliesExplicitUnlocks() {
        TechniqueAbilityContributions.Definition definitions = new TechniqueAbilityContributions.Definition();
        FixtureAbility first = new FixtureAbility("first");
        FixtureAbility middle = new FixtureAbility("middle");
        FixtureAbility inserted = new FixtureAbility("inserted", true);
        FixtureAbility alsoInserted = new FixtureAbility("also_inserted");
        FixtureAbility appended = new FixtureAbility("appended");
        ResourceLocation target = id("ordered");
        definitions.registerAfter(target, first.id(), inserted, 4, 5);
        definitions.registerAfter(target, first.id(), alsoInserted, 6);
        definitions.register(target, appended, 7);
        definitions.register(id("another_target"), new FixtureAbility("elsewhere"), 0);

        InnateTechnique technique = definitions.applyTo(target, InnateTechnique.builder(InnateTechniqueRarity.RARE)
                .ability(first).unlockLevel(first, 0)
                .ability(middle).unlockLevel(middle, 2)).build();

        assertEquals(List.of(first, inserted, alsoInserted, middle, appended), technique.abilities());
        assertEquals(4, technique.unlockLevel(inserted));
        assertEquals(5, technique.unlockLevel(inserted.special().orElseThrow()));
        assertEquals(6, technique.unlockLevel(alsoInserted));
        assertEquals(7, technique.unlockLevel(appended));
        assertTrue(technique.hasUnlockLevels());
        assertThrows(UnsupportedOperationException.class, () -> technique.abilities().add(new FixtureAbility("late")));
        assertThrows(IllegalStateException.class, () -> definitions.applyTo(target,
                InnateTechnique.builder(InnateTechniqueRarity.RARE).ability(first)));
        assertThrows(IllegalStateException.class,
                () -> definitions.register(id("late_target"), new FixtureAbility("late_contribution"), 0));
    }

    @Test
    void rejectsDuplicateAndInvalidRegistrations() {
        TechniqueAbilityContributions.Definition definitions = new TechniqueAbilityContributions.Definition();
        FixtureAbility ability = new FixtureAbility("duplicate");
        definitions.register(TARGET, ability, 0);
        assertThrows(IllegalArgumentException.class, () -> definitions.register(TARGET, ability, 0));
        assertThrows(IllegalArgumentException.class,
                () -> definitions.register(id("other"), new FixtureAbility("duplicate"), 0));
        assertThrows(IllegalArgumentException.class,
                () -> definitions.register(TARGET, new FixtureAbility("negative"), -1));
        assertThrows(IllegalArgumentException.class,
                () -> definitions.register(TARGET, new FixtureAbility("with_special", true), 2));
        assertThrows(IllegalArgumentException.class,
                () -> definitions.register(TARGET, new FixtureAbility("without_special"), 2, 3));
        assertThrows(IllegalArgumentException.class,
                () -> definitions.register(TARGET, new FixtureAbility("invalid_special"), 2, 3));

        FixtureAbility withSpecial = new FixtureAbility("paired", true);
        assertThrows(IllegalArgumentException.class,
                () -> new TechniqueAbilityContributions.Definition().register(TARGET, withSpecial.special().orElseThrow(), 0));
    }

    @Test
    void rejectsUnknownAnchorsAndIdsAlreadyInTheDefinition() {
        TechniqueAbilityContributions.Definition missingAnchor = new TechniqueAbilityContributions.Definition();
        FixtureAbility core = new FixtureAbility("core");
        missingAnchor.registerAfter(TARGET, id("missing"), new FixtureAbility("extra"), 1);
        assertThrows(IllegalArgumentException.class, () -> missingAnchor.applyTo(TARGET,
                InnateTechnique.builder(InnateTechniqueRarity.COMMON).ability(core).unlockLevel(core, 0)));

        TechniqueAbilityContributions.Definition duplicate = new TechniqueAbilityContributions.Definition();
        duplicate.register(TARGET, new FixtureAbility("core"), 1);
        assertThrows(IllegalArgumentException.class, () -> duplicate.applyTo(TARGET,
                InnateTechnique.builder(InnateTechniqueRarity.COMMON).ability(core).unlockLevel(core, 0)));
    }

    @Test
    void insertionDoesNotMoveExistingDefaultUnlockLevels() {
        TechniqueAbilityContributions.Definition definitions = new TechniqueAbilityContributions.Definition();
        FixtureAbility first = new FixtureAbility("level_first");
        FixtureAbility second = new FixtureAbility("level_second", true);
        FixtureAbility third = new FixtureAbility("level_third");
        FixtureAbility inserted = new FixtureAbility("level_inserted");
        definitions.registerAfter(TARGET, first.id(), inserted, 3);

        InnateTechnique technique = definitions.applyTo(TARGET, InnateTechnique.builder(InnateTechniqueRarity.RARE)
                .ability(first)
                .ability(second)
                .ability(third).unlockLevel(third, 17)
                .defaultUnlockLevels()).build();

        assertEquals(UnlockTable.ability(0), technique.unlockLevel(first));
        assertEquals(UnlockTable.ability(1), technique.unlockLevel(second));
        assertEquals(UnlockTable.special(1), technique.unlockLevel(second.special().orElseThrow()));
        assertEquals(17, technique.unlockLevel(third), "an existing explicit override still wins");
        assertEquals(3, technique.unlockLevel(inserted));
    }

    @Test
    void unlockMetadataIncludesSpecialsAndTheWholeUltimate() {
        FixtureAbility base = new FixtureAbility("locked_base", true);
        FixtureAbility ultimate = new FixtureAbility("locked_ultimate", true);
        InnateTechnique technique = InnateTechnique.builder(InnateTechniqueRarity.LEGENDARY)
                .ability(base).unlockLevel(base, 1)
                .unlockLevel(base.special().orElseThrow(), 2)
                .ultimate(ultimate).unlockLevel(ultimate, 8)
                .unlockLevel(ultimate.special().orElseThrow(), 8)
                .build();

        assertTrue(technique.hasUnlockLevels());
        assertEquals(2, technique.unlockLevel(base.special().orElseThrow()));
        assertEquals(8, technique.unlockLevel(ultimate));
        assertEquals(8, technique.unlockLevel(ultimate.special().orElseThrow()));
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("addon_test", path);
    }

    /** A normal ability whose declared special points at something else is structurally invalid. */
    private static final class FixtureAbility extends BaseAbility {
        @Nullable
        private final Ability special;

        FixtureAbility(String path) {
            this(path, false);
        }

        FixtureAbility(String path, boolean withSpecial) {
            super(TechniqueAbilityContributionsTest.id(path), CastType.INSTANT);
            if (path.equals("invalid_special")) {
                this.special = new FixtureSpecial("invalid_special_move", new FixtureAbility("somebody_else"));
            } else {
                this.special = withSpecial ? new FixtureSpecial(path + "_special", this) : null;
            }
        }

        @Override public double cost(ServerPlayer player) { return 0; }
        @Override public int cooldownTicks(ServerPlayer player) { return 0; }
        @Override public void execute(AbilityContext ctx) {}
        @Override public Optional<Ability> special() { return Optional.ofNullable(special); }
    }

    private static final class FixtureSpecial extends SpecialAbility {
        FixtureSpecial(String path, Ability base) {
            super(TechniqueAbilityContributionsTest.id(path), CastType.INSTANT, () -> base);
        }

        @Override public double cost(ServerPlayer player) { return 0; }
        @Override public void execute(AbilityContext ctx) {}
    }
}

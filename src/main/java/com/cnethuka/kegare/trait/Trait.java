package com.cnethuka.kegare.trait;

import com.cnethuka.kegare.registry.KegareRegistries;
import java.util.function.DoubleSupplier;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * A per-player trait (registry {@code kegare:trait}). Traits are rolled at birth, saved, synced and kept on
 * death (except inheritable traits lost to a player kill). Subclass and override the hooks for real effects.
 */
public class Trait {
    /** Why a trait was granted. */
    public enum AddReason { BIRTH, ADMIN, INHERITED }

    /** Why a trait was taken away. */
    public enum RemoveReason {
        /** {@code /kegare trait remove}. */
        ADMIN,
        /** {@code /kegare rebirth}. */
        REBIRTH,
        /** Bearer was killed by another player (lostOnPvpDeath traits). */
        LOST_IN_PVP,
        /** {@code /kegare trait release} while the bearer was online. */
        RELEASED,
        /** Released while offline (admin or inactivity); applied on their next login. */
        REVOKED,
        /** World record says someone else holds this unique trait. */
        CONFLICT,
        /** The bearer became Severed and this trait needs Malice. */
        SEVERED
    }

    /** Builder-style trait flags. */
    public static final class Properties {
        private boolean unique;
        private boolean requiresMalice;
        private boolean inheritable;
        private boolean lostOnPvpDeath;
        private DoubleSupplier birthChance = () -> 0.0;

        /** At most one bearer per world, tracked in {@link UniqueTraitSavedData}. */
        public Properties unique() {
            this.unique = true;
            return this;
        }

        /** Needs Malice: never rolled for, inherited by or granted to a Severed player. */
        public Properties requiresMalice() {
            this.requiresMalice = true;
            return this;
        }

        /** Unique traits only: when the bearer loses it, it passes to an heir ({@link TraitSuccession}). */
        public Properties inheritable() {
            this.inheritable = true;
            return this;
        }

        /** Not kept on death when the bearer is killed by another player. */
        public Properties lostOnPvpDeath() {
            this.lostOnPvpDeath = true;
            return this;
        }

        /** Chance in [0,1] to be rolled at birth (read from config). */
        public Properties birthChance(DoubleSupplier chance) {
            this.birthChance = chance;
            return this;
        }
    }

    private final boolean unique;
    private final boolean requiresMalice;
    private final boolean inheritable;
    private final boolean lostOnPvpDeath;
    private final DoubleSupplier birthChance;

    public Trait(Properties properties) {
        if (properties.inheritable && !properties.unique) throw new IllegalArgumentException("Only unique traits can be inheritable");
        this.unique = properties.unique;
        this.requiresMalice = properties.requiresMalice;
        this.inheritable = properties.inheritable;
        this.lostOnPvpDeath = properties.lostOnPvpDeath;
        this.birthChance = properties.birthChance;
    }

    public final boolean isUnique() { return unique; }
    public final boolean requiresMalice() { return requiresMalice; }
    public final boolean isInheritable() { return inheritable; }
    public final boolean isLostOnPvpDeath() { return lostOnPvpDeath; }

    public double birthChance() {
        return birthChance.getAsDouble();
    }

    /** Extra eligibility for the birth roll, on top of {@link #requiresMalice}. Default: anyone. */
    public boolean canRollFor(ServerPlayer player) {
        return true;
    }

    /** Inheritable traits: game ticks between losing it and the heir receiving it. */
    public int successionDelayTicks() {
        return 0;
    }

    /** Inheritable traits: how the heir is chosen. */
    public TransferMode successionMode() {
        return TransferMode.RANDOM;
    }

    /** Inheritable traits: lang key broadcast (anonymously) to everyone when it passes on, or null for none. */
    @Nullable
    public String successionBroadcastKey() {
        return null;
    }

    /** Hook: called after the trait is granted. */
    public void onAdded(ServerPlayer player, AddReason reason) {}

    /** Hook: called after the trait is removed. */
    public void onRemoved(ServerPlayer player, RemoveReason reason) {}

    /** Hook: the bearer was killed by {@code killer} and lost this trait (lostOnPvpDeath traits). */
    public void onBearerKilledByPlayer(ServerPlayer victim, ServerPlayer killer) {}

    public final ResourceLocation id() {
        return KegareRegistries.TRAIT.getKey(this);
    }

    public final String descriptionId() {
        return Util.makeDescriptionId("trait", id());
    }

    public Component displayName() {
        return Component.translatable(descriptionId());
    }
}

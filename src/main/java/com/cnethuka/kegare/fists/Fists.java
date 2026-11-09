package com.cnethuka.kegare.fists;

import com.cnethuka.kegare.loadout.SharedAbilities;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;

/** Reinforced Fists (DESIGN §3.28): a shared ability every Malice user has. Registered at mod construction. */
public final class Fists {
    public static final ReinforcedFists REINFORCED_FISTS = SharedAbilities.registerForEveryone(new ReinforcedFists());
    /** Told the moment Fists go on (server tick of the press): Black Spark times its gap from here. */
    static final List<Consumer<ServerPlayer>> TURNED_ON = new CopyOnWriteArrayList<>();

    private Fists() {}

    public static void init(IEventBus modBus) {
        com.cnethuka.kegare.melee.ReinforcedStrike.addLandedListener(SecondImpact::onStrike);
    }

    public static void onTurnedOn(Consumer<ServerPlayer> listener) {
        TURNED_ON.add(listener);
    }
}

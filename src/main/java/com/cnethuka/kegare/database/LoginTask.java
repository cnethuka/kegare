package com.cnethuka.kegare.database;

import java.util.function.Consumer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ConfigurationTask;
import net.minecraft.server.network.ServerConfigurationPacketListenerImpl;
import net.neoforged.neoforge.network.event.RegisterConfigurationTasksEvent;

/**
 * Holds a player in the configuration phase while their data loads (DESIGN §3.33), without holding up the server thread: the load runs on the
 * storage's own threads and this task finishes when it's back. If it can't be loaded they never enter the world.
 */
public final class LoginTask implements ConfigurationTask {
    static final Type TYPE = new Type("kegare:database");

    private final ServerConfigurationPacketListenerImpl listener;
    private final MinecraftServer server;

    private LoginTask(ServerConfigurationPacketListenerImpl listener, MinecraftServer server) {
        this.listener = listener;
        this.server = server;
    }

    public static void register(RegisterConfigurationTasksEvent event) {
        if (KegareDatabase.isOn() && event.getListener() instanceof ServerConfigurationPacketListenerImpl impl) {
            event.register(new LoginTask(impl, net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer()));
        }
    }

    @Override
    public Type type() {
        return TYPE;
    }

    @Override
    public void start(Consumer<Packet<?>> sender) {
        KegareDatabase database = KegareDatabase.current();
        if (database == null) {
            listener.finishCurrentTask(TYPE);
            return;
        }
        var player = listener.getOwner().getId();
        database.login(player).whenComplete((result, failure) -> server.execute(() -> {
            if (failure != null || result == null || !result.admitted()) {
                listener.disconnect(Component.translatable(result == null || result.refusal() == null ? KegareDatabase.UNREACHABLE : result.refusal()));
                return;
            }
            database.loginLoaded(player, result);
            listener.finishCurrentTask(TYPE);
        }));
    }
}

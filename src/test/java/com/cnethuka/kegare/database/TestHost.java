package com.cnethuka.kegare.database;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.core.HolderLookup;
import net.minecraft.server.MinecraftServer;

/** A server as the storage sees it, for tests. The server thread is whichever is running the test, so work posted to it waits in a list. */
final class TestHost implements KegareDatabase.Host {
    final List<String> told = new CopyOnWriteArrayList<>();
    final List<Runnable> posted = new CopyOnWriteArrayList<>();
    private final MinecraftServer server;
    private final Path playerFiles;

    TestHost(MinecraftServer server, Path playerFiles) {
        this.server = server;
        this.playerFiles = playerFiles;
    }

    @Override
    public void onServerThread(Runnable task) {
        posted.add(task);
    }

    /** What the server thread would have run by now. */
    void runPosted() {
        for (Runnable task : List.copyOf(posted)) {
            posted.remove(task);
            task.run();
        }
    }

    final List<String> kicked = new CopyOnWriteArrayList<>();
    final List<String> events = new CopyOnWriteArrayList<>();

    @Override
    public void kick(UUID player, String langKey) {
        kicked.add(player + " " + langKey);
    }

    @Override
    public void claimRefused(net.minecraft.resources.ResourceLocation trait, UUID player) {
        events.add("refused " + trait + " " + player);
    }

    @Override
    public void networkEvent(String kind, net.minecraft.resources.ResourceLocation trait, UUID player) {
        events.add(kind + " " + trait + " " + player);
    }

    @Override
    public void traitsRefreshed() {}

    @Override
    public void tellOps(String langKey) {
        told.add(langKey);
    }

    @Override
    public HolderLookup.Provider registries() {
        return server.registryAccess();
    }

    @Override
    public Path playerFile(UUID player) {
        return playerFiles == null ? null : playerFiles.resolve(player + ".dat");
    }
}

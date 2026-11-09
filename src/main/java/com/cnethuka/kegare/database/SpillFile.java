package com.cnethuka.kegare.database;

import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * What couldn't be saved before the server stopped (DESIGN §3.33): {@code config/kegare-database-spill.json}, replayed on the next start before
 * anyone joins. Player data only; no settings, so no password.
 */
final class SpillFile {
    static final String FILE_NAME = "kegare-database-spill.json";

    private SpillFile() {}

    static void write(Path configDir, Map<UUID, SaveQueue.Batch> batches) throws IOException {
        JsonObject players = new JsonObject();
        batches.forEach((player, batch) -> {
            JsonObject entry = new JsonObject();
            entry.addProperty("release", batch.release);
            JsonObject changes = new JsonObject();
            batch.changes.forEach((attachment, json) -> {
                if (json == null) changes.add(attachment, JsonNull.INSTANCE);
                else changes.addProperty(attachment, json);
            });
            entry.add("changes", changes);
            players.add(player.toString(), entry);
        });
        Path temporary = configDir.resolve(FILE_NAME + ".tmp");
        Files.writeString(temporary, players.toString(), StandardCharsets.UTF_8);
        Files.move(temporary, configDir.resolve(FILE_NAME), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    /** What's in the file, or nothing if there isn't one. */
    static Map<UUID, SaveQueue.Batch> read(Path configDir) throws IOException {
        Map<UUID, SaveQueue.Batch> out = new HashMap<>();
        Path file = configDir.resolve(FILE_NAME);
        if (!Files.exists(file)) return out;
        JsonObject players = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
        for (var player : players.entrySet()) {
            JsonObject entry = player.getValue().getAsJsonObject();
            SaveQueue.Batch batch = new SaveQueue.Batch();
            batch.release = entry.get("release").getAsBoolean();
            for (var change : entry.getAsJsonObject("changes").entrySet()) {
                batch.changes.put(change.getKey(), change.getValue().isJsonNull() ? null : change.getValue().getAsString());
            }
            out.put(UUID.fromString(player.getKey()), batch);
        }
        return out;
    }

    static void delete(Path configDir) throws IOException {
        Files.deleteIfExists(configDir.resolve(FILE_NAME));
    }
}

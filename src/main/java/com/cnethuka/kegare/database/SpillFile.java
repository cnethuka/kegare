package com.cnethuka.kegare.database;

import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * What couldn't be saved before the server stopped (DESIGN §3.33): {@code config/kegare-database-spill.json}, replayed on the next start before
 * anyone joins. Player data only; no settings, so no password.
 */
final class SpillFile {
    static final String FILE_NAME = "kegare-database-spill.json";

    private SpillFile() {}

    record Contents(Map<UUID, SaveQueue.Batch> batches, List<NetworkTraits.Op> traitOps) {}

    static void write(Path configDir, Map<UUID, SaveQueue.Batch> batches, List<NetworkTraits.Op> traitOps) throws IOException {
        JsonObject players = new JsonObject();
        batches.forEach((player, batch) -> {
            JsonObject entry = new JsonObject();
            entry.addProperty("release", batch.release);
            entry.addProperty("token", batch.token.toString());
            JsonObject changes = new JsonObject();
            batch.changes.forEach((attachment, json) -> {
                if (json == null) changes.add(attachment, JsonNull.INSTANCE);
                else changes.addProperty(attachment, json);
            });
            entry.add("changes", changes);
            players.add(player.toString(), entry);
        });
        Path temporary = configDir.resolve(FILE_NAME + ".tmp");
        JsonObject root = new JsonObject();
        root.add("players", players);
        JsonArray ops = new JsonArray();
        traitOps.forEach(op -> ops.add(op.toJson()));
        root.add("traitOps", ops);
        Files.writeString(temporary, root.toString(), StandardCharsets.UTF_8);
        Files.move(temporary, configDir.resolve(FILE_NAME), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    /** What's in the file, or nothing if there isn't one. */
    static Contents read(Path configDir) throws IOException {
        Map<UUID, SaveQueue.Batch> out = new HashMap<>();
        List<NetworkTraits.Op> ops = new ArrayList<>();
        Path file = configDir.resolve(FILE_NAME);
        if (!Files.exists(file)) return new Contents(out, ops);
        JsonObject root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
        root.getAsJsonArray("traitOps").forEach(op -> ops.add(NetworkTraits.Op.fromJson(op.getAsJsonObject())));
        JsonObject players = root.getAsJsonObject("players");
        for (var player : players.entrySet()) {
            JsonObject entry = player.getValue().getAsJsonObject();
            SaveQueue.Batch batch = new SaveQueue.Batch();
            batch.release = entry.get("release").getAsBoolean();
            batch.token = UUID.fromString(entry.get("token").getAsString());
            for (var change : entry.getAsJsonObject("changes").entrySet()) {
                batch.changes.put(change.getKey(), change.getValue().isJsonNull() ? null : change.getValue().getAsString());
            }
            out.put(UUID.fromString(player.getKey()), batch);
        }
        return new Contents(out, ops);
    }

    static void delete(Path configDir) throws IOException {
        Files.deleteIfExists(configDir.resolve(FILE_NAME));
    }
}

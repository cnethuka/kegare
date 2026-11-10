package com.cnethuka.kegare.innate_technique.rime.client;

import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * {@code player_animations/rime.json}, a hand-keyed file that must stay gone: each Rime animation lives in its own file, one source
 * for each, or which one plays depends on load order.
 */
final class RimeJson {
    static final Path PATH = Path.of(System.getProperty("kegare.projectDir", "."),
            "src/main/resources/assets/kegare/player_animations/rime.json");

    private RimeJson() {}

    /** Whether rime.json exists and carries this animation. */
    static boolean has(String animation) throws IOException {
        if (!Files.exists(PATH)) return false;
        try (Reader reader = Files.newBufferedReader(PATH)) {
            return JsonParser.parseReader(reader).getAsJsonObject().getAsJsonObject("animations").has(animation);
        }
    }
}

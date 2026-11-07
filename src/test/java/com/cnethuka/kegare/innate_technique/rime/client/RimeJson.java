package com.cnethuka.kegare.innate_technique.rime.client;

import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The old hand-keyed rime.json: each of Rime's animations moved out of it into its own file from tools/anims (one source for each,
 * or which one plays depends on load order), and once the last one went, so did the file.
 */
final class RimeJson {
    static final Path PATH = Path.of(System.getProperty("kegare.projectDir", "."),
            "src/main/resources/assets/kegare/player_animations/rime.json");

    private RimeJson() {}

    /** Whether rime.json (if it's still there) carries this animation. */
    static boolean has(String animation) throws IOException {
        if (!Files.exists(PATH)) return false;
        try (Reader reader = Files.newBufferedReader(PATH)) {
            return JsonParser.parseReader(reader).getAsJsonObject().getAsJsonObject("animations").has(animation);
        }
    }
}

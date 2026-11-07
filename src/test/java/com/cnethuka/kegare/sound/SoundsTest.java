package com.cnethuka.kegare.sound;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Kegare's own sounds: every one registered in {@link KegareSounds} has its file (mono OGG Vorbis, so it's heard from where it's
 * played), its subtitle in en_us.json and its entry in the generated sounds.json, and no file under sounds/ is left unused.
 * Run {@code python3 tools/sounds/build.py} for a missing file and {@code ./gradlew runData} for a missing entry or subtitle.
 */
class SoundsTest {
    private static final Path PROJECT = Path.of(System.getProperty("kegare.projectDir", "."));
    private static final Path SOUNDS = PROJECT.resolve("src/main/resources/assets/kegare/sounds");
    private static final Path GENERATED = PROJECT.resolve("src/generated/resources/assets/kegare");

    @Test
    void everySoundHasItsMonoFileSubtitleAndEntry() throws IOException {
        assertFalse(KegareSounds.ALL.isEmpty());
        JsonObject defs = JsonParser.parseString(Files.readString(GENERATED.resolve("sounds.json"))).getAsJsonObject();
        JsonObject lang = JsonParser.parseString(Files.readString(GENERATED.resolve("lang/en_us.json"))).getAsJsonObject();
        for (KegareSounds.Sound s : KegareSounds.ALL) {
            Path file = SOUNDS.resolve(s.file() + ".ogg");
            assertTrue(Files.exists(file), s.name() + ": no " + PROJECT.relativize(file) + " (python3 tools/sounds/build.py)");
            assertEquals(1, channels(Files.readAllBytes(file)), s.name() + " must be mono to be heard from where it's played");
            assertTrue(lang.has(s.subtitle()), s.name() + ": no subtitle " + s.subtitle() + " in en_us.json");
            assertTrue(defs.has(s.name()), s.name() + " is missing from sounds.json (runData)");
            JsonObject def = defs.getAsJsonObject(s.name());
            assertEquals(s.subtitle(), def.get("subtitle").getAsString());
            assertTrue(def.toString().contains("kegare:" + s.file()), s.name() + " plays the wrong file: " + def);
        }
    }

    @Test
    void noFileIsLeftUnused() throws IOException {
        Set<String> used = KegareSounds.ALL.stream().map(KegareSounds.Sound::file).collect(Collectors.toSet());
        Set<String> unused = new TreeSet<>();
        try (Stream<Path> files = Files.walk(SOUNDS)) {
            for (Path f : files.filter(p -> p.toString().endsWith(".ogg")).toList()) {
                String name = SOUNDS.relativize(f).toString().replace('\\', '/').replaceAll("\\.ogg$", "");
                if (!used.contains(name)) unused.add(name);
            }
        }
        assertTrue(unused.isEmpty(), "sound files nothing plays: " + unused);
    }

    /** The channel count in an OGG Vorbis identification header ("\1vorbis", version, channels). */
    static int channels(byte[] ogg) {
        byte[] magic = "\u0001vorbis".getBytes(StandardCharsets.ISO_8859_1);
        outer:
        for (int i = 0; i + magic.length + 5 < ogg.length; i++) {
            for (int k = 0; k < magic.length; k++) if (ogg[i + k] != magic[k]) continue outer;
            return ogg[i + magic.length + 4] & 0xFF;
        }
        return -1;
    }
}

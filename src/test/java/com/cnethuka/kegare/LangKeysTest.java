package com.cnethuka.kegare;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Every lang key written as a string literal in the sources (messages, commands, HUD, keybinds) must exist in the
 * generated en_us.json. Catches typos and stale keys after renames. Keys built at runtime (registry names,
 * enum keys, advancements) are covered by the language provider's own validation instead.
 * Run {@code ./gradlew runData} first if this fails after adding a key.
 */
class LangKeysTest {
    private static final Path SOURCES = Path.of("src/main/java");
    private static final Path LANG = Path.of("src/generated/resources/assets/kegare/lang/en_us.json");
    private static final Pattern KEY_LITERAL =
            Pattern.compile("\"((?:message|commands|hud|key|gui)\\.kegare\\.[a-z0-9_.]*[a-z0-9_]|key\\.categories\\.kegare)\"");
    private static final Pattern JSON_KEY = Pattern.compile("^\\s*\"([^\"]+)\"\\s*:", Pattern.MULTILINE);

    @Test
    void everyLiteralLangKeyExists() throws IOException {
        Set<String> defined = new TreeSet<>();
        Matcher json = JSON_KEY.matcher(Files.readString(LANG));
        while (json.find()) defined.add(json.group(1));

        Set<String> missing = new TreeSet<>();
        Set<String> used = new TreeSet<>();
        try (Stream<Path> files = Files.walk(SOURCES)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                Matcher m = KEY_LITERAL.matcher(Files.readString(file));
                while (m.find()) {
                    used.add(m.group(1));
                    if (!defined.contains(m.group(1))) missing.add(m.group(1) + "  (" + SOURCES.relativize(file) + ")");
                }
            }
        }
        assertTrue(used.size() > 50, "expected to find the mod's lang keys, found only " + used.size());
        assertTrue(missing.isEmpty(), "Lang keys used in code but missing from en_us.json:\n  " + String.join("\n  ", missing));
    }
}

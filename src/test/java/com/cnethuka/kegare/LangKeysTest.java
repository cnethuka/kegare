package com.cnethuka.kegare;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
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
    /** Set by Gradle (tests run from a game directory, not the project); falls back to the working directory. */
    private static final Path PROJECT = Path.of(System.getProperty("kegare.projectDir", "."));
    private static final Path SOURCES = PROJECT.resolve("src/main/java");
    private static final Path LANG = PROJECT.resolve("src/generated/resources/assets/kegare/lang/en_us.json");
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

    /**
     * DESIGN §1: messages and cards must never name a unique trait or the Shrine. Every trait name in the lang file is
     * checked against every message and ability card, and the code against passing a trait's name along; the birth line is
     * the one that does, and it skips unique traits. Op commands are the documented exception.
     */
    @Test
    void messagesAndCardsNeverNameAUniqueTraitOrTheShrine() throws IOException {
        Map<String, String> lang = new TreeMap<>();
        Matcher entry = Pattern.compile("^\\s*\"([^\"]+)\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"", Pattern.MULTILINE).matcher(Files.readString(LANG));
        while (entry.find()) lang.put(entry.group(1), entry.group(2));

        assertEquals("The eyes have closed.", lang.get("message.kegare.trait.lost"));
        assertEquals("The eyes have opened.", lang.get("message.kegare.trait.inherited"));
        assertEquals("a rare trait", lang.get("requirement.kegare.trait"));

        Set<String> secret = new TreeSet<>(Set.of("shrine"));
        lang.forEach((key, value) -> {
            if (key.startsWith("trait.kegare.") && !key.endsWith(".desc")) secret.add(value.toLowerCase(java.util.Locale.ROOT));
        });
        assertTrue(secret.contains("six eyes"), "setup: found the trait names");
        Set<String> named = new TreeSet<>();
        lang.forEach((key, value) -> {
            if (!key.startsWith("message.") && !key.startsWith("death.") && !key.startsWith("hud.") && !key.startsWith("requirement.")
                    && !key.startsWith("ability.") && !key.startsWith("innate_technique.")) return;
            String lower = value.toLowerCase(java.util.Locale.ROOT);
            for (String name : secret) if (lower.contains(name)) named.add(key);
        });
        assertEquals(Set.of(), named, "text that names a secret");

        Pattern passesName = Pattern.compile("\"((?:message|requirement)\\.kegare\\.[a-z0-9_.]+)\"[^;]*?[tT]rait\\.get\\(\\)\\.displayName\\(\\)|\"((?:message|requirement)\\.kegare\\.[a-z0-9_.]+)\"[^;]*?[tT]rait\\.displayName\\(\\)");
        Set<String> passed = new TreeSet<>();
        try (Stream<Path> files = Files.walk(SOURCES)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                Matcher m = passesName.matcher(Files.readString(file));
                while (m.find()) passed.add(m.group(1) != null ? m.group(1) : m.group(2));
            }
        }
        assertEquals(Set.of("message.kegare.birth.trait"), passed, "only the birth line takes a trait's name");
    }
}

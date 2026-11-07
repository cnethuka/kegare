package com.cnethuka.kegare;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * CLAUDE.md is the agent instructions' source; AGENTS.md and AGENT.md are exact copies of it for agents that read those names.
 * Edit CLAUDE.md, then copy it over the other two.
 */
class AgentFilesTest {
    private static final Path PROJECT = Path.of(System.getProperty("kegare.projectDir", "."));

    @Test
    void theCopiesMatchClaudeMd() throws IOException {
        String source = Files.readString(PROJECT.resolve("CLAUDE.md"));
        assertTrue(source.startsWith("> Edit CLAUDE.md only"), "CLAUDE.md starts with the line saying how to edit it");
        assertTrue(source.lines().count() <= 160, "kept short: " + source.lines().count() + " lines");
        for (String copy : new String[] {"AGENTS.md", "AGENT.md"}) {
            assertEquals(source, Files.readString(PROJECT.resolve(copy)), copy + " differs from CLAUDE.md: cp CLAUDE.md " + copy);
        }
    }
}

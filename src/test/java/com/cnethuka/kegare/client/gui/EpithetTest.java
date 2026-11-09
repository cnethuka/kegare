package com.cnethuka.kegare.client.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cnethuka.kegare.innate_technique.puppet_manipulation.PuppetManipulation;
import com.cnethuka.kegare.innate_technique.rime.Rime;
import com.cnethuka.kegare.innate_technique.swap.Swap;
import com.cnethuka.kegare.innatetechnique.InnateTechnique;
import com.cnethuka.kegare.registry.KegareRegistries;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import net.minecraft.server.MinecraftServer;

/** The epithets in the generated lang file, and each real technique's signature animation (reveal's Name scene, DESIGN §3.12). */
@ExtendWith(EphemeralTestServerProvider.class)
class EpithetTest {
    private static final Path LANG = Path.of(System.getProperty("kegare.projectDir", "."), "src/generated/resources/assets/kegare/lang/en_us.json");

    private static JsonObject lang() throws IOException {
        return JsonParser.parseString(Files.readString(LANG)).getAsJsonObject();
    }

    @Test
    void realTechniquesHaveTheirEpithets(MinecraftServer server) throws IOException {
        JsonObject lang = lang();
        assertEquals("Breath Turned to Glass", lang.get(Rime.RIME.get().epithetId()).getAsString());
        assertEquals("Nothing Stays Where It Was", lang.get(Swap.SWAP.get().epithetId()).getAsString());
        assertEquals("Strings Without a Stage", lang.get(PuppetManipulation.PUPPET_MANIPULATION.get().epithetId()).getAsString());
    }

    @Test
    void everyTechniqueHasOne(MinecraftServer server) throws IOException {
        JsonObject lang = lang();
        for (InnateTechnique technique : KegareRegistries.INNATE_TECHNIQUE) {
            assertTrue(lang.has(technique.epithetId()), technique.epithetId());
            assertFalse(lang.get(technique.epithetId()).getAsString().isBlank(), technique.epithetId());
        }
    }

    @Test
    void realTechniquesHaveTheirSignatureAnimations(MinecraftServer server) {
        assertEquals("frost_shard_cast", Rime.RIME.get().signatureAnimation().orElseThrow().getPath());
        assertEquals("positions_clap", Swap.SWAP.get().signatureAnimation().orElseThrow().getPath());
        assertEquals("puppet_create_release", PuppetManipulation.PUPPET_MANIPULATION.get().signatureAnimation().orElseThrow().getPath());
    }
}

package com.cnethuka.kegare.rmt;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.cnethuka.kegare.TestServer;
import com.cnethuka.kegare.innate_technique.rime.Rime;
import com.cnethuka.kegare.innate_technique.swap.Swap;
import com.cnethuka.kegare.innatetechnique.InnateTechniqueService;
import com.cnethuka.kegare.player.BirthService;
import com.cnethuka.kegare.progression.Grade;
import com.cnethuka.kegare.progression.GradeService;
import com.cnethuka.kegare.progression.RefinementService;
import java.util.OptionalInt;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * RMT's output (DESIGN §3.17): 1 + a Grade bonus + a Peak Refinement bonus, so Grade 4 at Peak 0 heals at 1x and Special Grade at Peak
 * 10 at 1.6x; Peak, so a reroll (which resets Refinement) doesn't lower it; and later systems can change it through the hook.
 */
@ExtendWith(EphemeralTestServerProvider.class)
class RmtOutputTest {
    @Test
    void outputFromGradeAndPeak(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            TestServer.ensureOverworld(server);
            ServerPlayer p = TestServer.player(server, "rmt_output");
            BirthService.rebirth(p, OptionalInt.of(500));
            InnateTechniqueService.setInnateTechnique(p, Rime.RIME.get());
            GradeService.set(p, Grade.GRADE_4);
            RefinementService.setLevel(p, 0);
            assertEquals(0, RefinementService.peak(p), "setup: Peak 0");
            assertEquals(1.0, RmtOutput.of(p), 1e-9, "Grade 4, Peak 0: 1x");

            GradeService.set(p, Grade.GRADE_2); // half way up the ladder
            assertEquals(1.15, RmtOutput.of(p), 1e-9, "Grade 2: +0.15");
            GradeService.set(p, Grade.SPECIAL_GRADE);
            RefinementService.setLevel(p, RefinementService.maxLevel());
            assertEquals(1.6, RmtOutput.of(p), 1e-9, "Special Grade, Peak 10: 1.6x");

            InnateTechniqueService.setInnateTechnique(p, Swap.SWAP.get()); // a reroll: Refinement back to 0, Peak kept
            assertEquals(0, RefinementService.level(p), "setup: Refinement reset");
            assertEquals(1.6, RmtOutput.of(p), 1e-9, "a reroll doesn't lower it");

            RmtOutput.Modifier halve = (player, output) -> output * 0.5;
            RmtOutput.addModifier(halve);
            try {
                assertEquals(0.8, RmtOutput.of(p), 1e-9, "the hook changes it");
            } finally {
                RmtOutput.removeModifier(halve);
            }
        });
    }
}

package com.cnethuka.kegare.rmt;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.cnethuka.kegare.TestServer;
import com.cnethuka.kegare.backgroundtask.RmtHealed;
import com.cnethuka.kegare.player.BirthService;
import java.util.OptionalInt;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/** Background RMT's unlock count (DESIGN §3.31): what your own RMT heals, on you or on another, and nothing at full health. */
@ExtendWith(EphemeralTestServerProvider.class)
class RmtHealCountTest {
    @Test
    void healingOthersCountsToo(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            try {
                TestServer.ensureOverworld(server);
                ServerPlayer caster = TestServer.damageable(server, "count_caster");
                ServerPlayer other = TestServer.damageable(server, "count_other");
                BirthService.rebirth(caster, OptionalInt.of(1000));
                RmtHealed.set(caster, 0);
                other.setHealth(10f);
                ReverseMaliceTechnique.heal(caster, other, 3.0);
                assertEquals(3.0, RmtHealed.get(caster), 1e-4, "healing another counts for the caster");
                assertEquals(0.0, RmtHealed.get(other), 1e-4, "not for the one healed");
                caster.setHealth(caster.getMaxHealth() - 1f);
                ReverseMaliceTechnique.heal(caster, caster, 3.0);
                assertEquals(4.0, RmtHealed.get(caster), 1e-4, "only what it actually healed");
                ReverseMaliceTechnique.heal(caster, caster, 3.0);
                assertEquals(4.0, RmtHealed.get(caster), 1e-4, "nothing at full health");
            } finally {
                TestServer.removePlayers(server);
            }
        });
    }
}

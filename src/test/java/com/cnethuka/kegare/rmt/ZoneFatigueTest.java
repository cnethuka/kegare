package com.cnethuka.kegare.rmt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cnethuka.kegare.TestServer;
import com.cnethuka.kegare.fists.Zone;
import com.cnethuka.kegare.player.BirthService;
import java.util.OptionalInt;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/** In Black Spark's zone (DESIGN §3.28), RMT ignores injury fatigue: hits taken add none. */
@ExtendWith(EphemeralTestServerProvider.class)
class ZoneFatigueTest {
    @Test
    void hitsInTheZoneAddNoFatigue(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            try {
                TestServer.ensureOverworld(server);
                ServerPlayer p = TestServer.player(server, "zone_fatigue");
                BirthService.rebirth(p, OptionalInt.of(1000));
                RmtFatigue.reset(p);
                RmtFatigue.onDamageTaken(p, 4f);
                double after = RmtFatigue.of(p);
                assertTrue(after > 0, "out of the zone, a hit adds fatigue");
                Zone.start(p, 100);
                RmtFatigue.onDamageTaken(p, 4f);
                assertEquals(after, RmtFatigue.of(p), 1.0e-9, "in the zone it adds none");
            } finally {
                Zone.clear();
                TestServer.removePlayers(server);
            }
        });
    }
}

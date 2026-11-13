package com.cnethuka.kegare.boss;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cnethuka.kegare.TestServer;
import com.cnethuka.kegare.combat.CombatHelper;
import com.cnethuka.kegare.registry.KegareAttachments;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(EphemeralTestServerProvider.class)
class BossStunTest extends BossTestSupport {
    @Test
    void centralStunRefusesBossesButStillWorksOnOrdinaryMobs(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            WitherBoss boss = boss(server, "stun");
            Cow cow = track(new Cow(EntityType.COW, server.overworld()));
            CombatHelper.stun(boss, 10);
            CombatHelper.stun(cow, 10);
            assertEquals(0, CombatHelper.stunTicksRemaining(boss));
            assertTrue(!boss.hasData(KegareAttachments.COMBAT), "a refused stun does not create boss state");
            assertTrue(CombatHelper.stunTicksRemaining(cow) > 0);
        });
    }
}

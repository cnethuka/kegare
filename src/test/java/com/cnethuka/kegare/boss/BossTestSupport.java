package com.cnethuka.kegare.boss;

import com.cnethuka.kegare.TestServer;
import com.cnethuka.kegare.api.boss.BossFights;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import org.junit.jupiter.api.AfterEach;

abstract class BossTestSupport {
    static final ResourceLocation PHASE = ResourceLocation.fromNamespaceAndPath("kegare_test", "phase");
    private final List<LivingEntity> entities = new ArrayList<>();

    @AfterEach
    void cleanBossState(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            BossFights.resetAll(server);
            BossDamageTracker.clear();
            for (LivingEntity entity : entities) entity.discard();
            entities.clear();
            TestServer.removePlayers(server);
        });
    }

    WitherBoss boss(MinecraftServer server, String name) {
        TestServer.ensureOverworld(server);
        ServerLevel level = server.overworld();
        WitherBoss boss = EntityType.WITHER.create(level);
        boss.setNoAi(true);
        level.addFreshEntity(boss);
        entities.add(boss);
        return boss;
    }

    ServerPlayer player(MinecraftServer server, String name) {
        ServerPlayer player = TestServer.damageable(server, name);
        server.overworld().addNewPlayer(player);
        entities.add(player);
        return player;
    }

    <T extends LivingEntity> T track(T entity) {
        entities.add(entity);
        return entity;
    }

    static void advance(MinecraftServer server, BossFightImpl fight, int ticks) {
        TestServer.advanceGameTime(server, ticks);
        fight.tick();
    }

    static DamageSource source(ServerLevel level, Entity direct, Entity causing) {
        Holder<DamageType> type = level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
                .getHolderOrThrow(DamageTypes.GENERIC);
        return new DamageSource(type, direct, causing);
    }

    static boolean hit(LivingEntity victim, DamageSource source, float amount) {
        victim.invulnerableTime = 0;
        return victim.hurt(source, amount);
    }
}

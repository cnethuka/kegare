package com.cnethuka.kegare.innate_technique.puppet_manipulation;

import com.cnethuka.kegare.innatetechnique.visual.Visual;
import com.cnethuka.kegare.innatetechnique.visual.VisualService;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/** Making, finding and dissolving puppets (server side). */
public final class Puppets {
    private Puppets() {}

    public static int count(ServerPlayer owner) {
        return PuppetSavedData.get(owner.server).count(owner.getUUID());
    }

    /** At the safety cap (lag protection): Puppet Creation fizzles for free. */
    public static boolean atCap(ServerPlayer owner) {
        return count(owner) >= PuppetConfig.SAFETY_CAP.get();
    }

    /** Makes a puppet of {@code owner} at {@code tier} next to them (a free spot at their side), plays its forming. */
    public static PuppetEntity create(ServerPlayer owner, PuppetTier tier) {
        PuppetSavedData data = PuppetSavedData.get(owner.server);
        PuppetEntity puppet = PuppetEntity.create(owner, tier, PuppetManipulation.MALICE_COLOR, data.generation(owner.getUUID()));
        Vec3 at = spotBeside(owner, puppet);
        puppet.moveTo(at.x, at.y, at.z, owner.getYRot(), 0f);
        puppet.setYHeadRot(owner.getYRot());
        puppet.setYBodyRot(owner.getYRot());
        owner.serverLevel().addFreshEntity(puppet);
        data.add(owner.getUUID(), puppet.getUUID());
        VisualService.playOnce(puppet, Visual.effect(PuppetIds.FX_PUPPET_FORM, Visual.Anchor.BODY));
        owner.level().playSound(null, puppet.getX(), puppet.getY(), puppet.getZ(), SoundEvents.WOOL_PLACE, SoundSource.PLAYERS, 0.9f, 1.3f);
        return puppet;
    }

    /** Beside the owner (right, then left, then behind, then in front), on a free spot; else at their feet. */
    private static Vec3 spotBeside(ServerPlayer owner, PuppetEntity puppet) {
        Vec3 look = Vec3.directionFromRotation(0f, owner.getYRot());
        Vec3 right = new Vec3(-look.z, 0, look.x);
        for (Vec3 offset : List.of(right.scale(1.1), right.scale(-1.1), look.scale(-1.1), look.scale(1.1))) {
            Vec3 at = owner.position().add(offset);
            if (owner.level().noCollision(puppet, puppet.getDimensions(puppet.getPose()).makeBoundingBox(at))) return at;
        }
        return owner.position();
    }

    /** Whether a just-loaded puppet still belongs: its owner hasn't rerolled away since it was made. */
    static boolean isCurrent(PuppetEntity puppet) {
        UUID owner = puppet.ownerId();
        if (owner == null || !(puppet.level() instanceof ServerLevel level)) return false;
        PuppetSavedData data = PuppetSavedData.get(level.getServer());
        return data.generation(owner) == puppet.generation() && data.contains(owner, puppet.getUUID());
    }

    /** A puppet is gone for good (killed or dissolved): it no longer counts toward its owner's cap. */
    static void forget(PuppetEntity puppet) {
        UUID owner = puppet.ownerId();
        if (owner != null && puppet.level() instanceof ServerLevel level) PuppetSavedData.get(level.getServer()).remove(owner, puppet.getUUID());
    }

    /** Dissolves one puppet into Malice-colored threads (no death, no drops). */
    public static void dissolve(PuppetEntity puppet) {
        dissolveEffect(puppet);
        puppet.discard();
    }

    static void dissolveEffect(PuppetEntity puppet) {
        if (!(puppet.level() instanceof ServerLevel level)) return;
        VisualService.playAt(level, puppet.position().add(0, puppet.getBbHeight() / 2, 0), Visual.effect(PuppetIds.FX_PUPPET_DISSOLVE, Visual.Anchor.BODY));
        level.playSound(null, puppet.getX(), puppet.getY(), puppet.getZ(), SoundEvents.WOOL_BREAK, SoundSource.PLAYERS, 0.8f, 1.2f);
    }

    /**
     * The owner rerolled away from Puppet Manipulation: every puppet they own dissolves. Loaded ones go now; the
     * rest dissolve the moment their chunk loads (the owner's generation moves on).
     */
    public static void dissolveAll(MinecraftServer server, UUID owner) {
        for (PuppetEntity puppet : loaded(server, owner)) dissolve(puppet);
        PuppetSavedData.get(server).dissolveAll(owner);
    }

    /** The owner's puppets that are loaded right now, in every dimension. */
    public static List<PuppetEntity> loaded(MinecraftServer server, UUID owner) {
        List<PuppetEntity> out = new ArrayList<>();
        for (ServerLevel level : server.getAllLevels()) {
            for (Entity e : level.getEntities(PuppetRegistry.PUPPET.get(), p -> owner.equals(p.ownerId()) && p.isAlive())) {
                out.add((PuppetEntity) e);
            }
        }
        return out;
    }

    /** The owner's loaded puppets in their level within {@code range} blocks, nearest first. */
    public static List<PuppetEntity> near(ServerPlayer owner, double range) {
        List<PuppetEntity> out = new ArrayList<>(owner.serverLevel().getEntitiesOfClass(PuppetEntity.class,
                owner.getBoundingBox().inflate(range), p -> p.isAlive() && !p.isPossessed() && p.isOwnedBy(owner) && p.distanceToSqr(owner) <= range * range));
        out.sort(Comparator.comparingDouble(p -> p.distanceToSqr(owner)));
        return out;
    }

    /** A following puppet left far behind appears beside its owner, like a tamed pet. */
    static void catchUp(PuppetEntity puppet, ServerPlayer owner) {
        Vec3 at = spotBeside(owner, puppet);
        puppet.getNavigation().stop();
        puppet.moveTo(at.x, at.y, at.z, owner.getYRot(), 0f);
    }
}

package com.cnethuka.kegare.innate_technique.puppet_manipulation;

import com.cnethuka.kegare.Kegare;
import com.cnethuka.kegare.combat.CombatHelper;
import com.cnethuka.kegare.innate_technique.swap.Swapper;
import com.cnethuka.kegare.innatetechnique.CastIntent;
import com.cnethuka.kegare.innatetechnique.visual.Visual;
import com.cnethuka.kegare.innatetechnique.visual.VisualService;
import com.cnethuka.kegare.malice.MaliceService;
import com.cnethuka.kegare.network.PlayerSync;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * Possess (Puppet Creation's special): the player BECOMES one of their puppets. They take its place, its size
 * (scale attribute) and its health (max health set to the puppet's); their inventory is stashed (no items) and they
 * can't cast, except R to return. Their body stays behind as a {@link PuppetShellEntity}. The puppet itself waits
 * hidden inside them ({@link PuppetEntity#isPossessed}) and reappears where they leave it, with what health they left it.
 * <p>
 * Ways back ({@link Return}): R; the shell takes any damage (they take the hit); the puppet dies (backlash: nausea, a
 * stun and lost Malice); more than {@code maxDistance} from the shell; logout, death, a dimension change or losing the
 * technique (they return first). Returning puts back real health, size, inventory and the body's position.
 */
public final class Possession {
    static final ResourceLocation SCALE_MODIFIER = Kegare.id("possession_scale");
    static final ResourceLocation HEALTH_MODIFIER = Kegare.id("possession_health");

    /** Why a possession ended. */
    public enum Return {
        /** R again. */
        RETURNED,
        /** Something damaged the empty body. */
        SHELL_HIT,
        /** The possessed puppet died: backlash. */
        PUPPET_DIED,
        /** Beyond maxDistance from the body. */
        TOO_FAR,
        LOGOUT,
        DEATH,
        DIMENSION_CHANGE,
        TECHNIQUE_CHANGED
    }

    /** The live puppet and shell of each possession (runtime; after a restart only the saved record is left). */
    private record Refs(PuppetEntity puppet, PuppetShellEntity shell) {}

    private static final java.util.Map<UUID, Refs> REFS = new java.util.HashMap<>();

    private Possession() {}

    @Nullable
    private static PuppetEntity puppetOf(ServerPlayer player, PossessionData d) {
        Refs refs = REFS.get(player.getUUID());
        if (refs != null && !refs.puppet.isRemoved()) return refs.puppet;
        return d.puppet != null && player.serverLevel().getEntity(d.puppet) instanceof PuppetEntity p ? p : null;
    }

    @Nullable
    private static PuppetShellEntity shellOf(ServerPlayer player, PossessionData d) {
        Refs refs = REFS.get(player.getUUID());
        if (refs != null && !refs.shell.isRemoved()) return refs.shell;
        return d.shell != null && player.serverLevel().getEntity(d.shell) instanceof PuppetShellEntity s ? s : null;
    }

    static void clear() {
        REFS.clear();
    }

    /** The shell's network ID (for the link the clients draw), or -1. */
    static int shellId(ServerPlayer player) {
        PuppetShellEntity shell = isPossessing(player) ? shellOf(player, data(player)) : null;
        return shell == null ? -1 : shell.getId();
    }

    public static PossessionData data(ServerPlayer player) {
        return player.getData(PuppetRegistry.POSSESSION.get());
    }

    public static boolean isPossessing(ServerPlayer player) {
        return player.hasData(PuppetRegistry.POSSESSION.get()) && data(player).active();
    }

    // ---------------------------------------------------------------- possess

    /** Moves the player's consciousness into {@code puppet} (one of theirs, in their level). */
    public static void possess(ServerPlayer player, PuppetEntity puppet) {
        if (isPossessing(player)) return;
        Strings.cut(player); // you can't hold a string from outside your body
        ServerLevel level = player.serverLevel();
        PossessionData d = data(player);

        // The body stays behind.
        PuppetShellEntity shell = PuppetShellEntity.create(player);
        level.addFreshEntity(shell);
        d.puppet = puppet.getUUID();
        d.shell = shell.getUUID();
        d.realHealth = player.getHealth();
        d.realMaxHealth = player.getMaxHealth();
        d.bodyPos = player.position();
        d.bodyYaw = player.getYRot();
        d.bodyPitch = player.getXRot();
        d.dimension = level.dimension();
        d.tier = puppet.tier();
        Inventory inventory = player.getInventory();
        d.inventory = inventory.save(new net.minecraft.nbt.ListTag());
        d.selectedSlot = inventory.selected;
        player.closeContainer();
        player.stopUsingItem();
        inventory.clearContent();

        // Becoming the puppet: its size and its health.
        AttributeInstance scale = player.getAttribute(Attributes.SCALE);
        scale.removeModifier(SCALE_MODIFIER);
        scale.addTransientModifier(new AttributeModifier(SCALE_MODIFIER,
                puppet.getAttributeValue(Attributes.SCALE) / Math.max(1.0e-3, scale.getValue()) - 1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        AttributeInstance maxHealth = player.getAttribute(Attributes.MAX_HEALTH);
        maxHealth.removeModifier(HEALTH_MODIFIER);
        maxHealth.addTransientModifier(new AttributeModifier(HEALTH_MODIFIER, puppet.getMaxHealth() - maxHealth.getValue(),
                AttributeModifier.Operation.ADD_VALUE));
        player.setHealth(puppet.getHealth());
        player.refreshDimensions();

        REFS.put(player.getUUID(), new Refs(puppet, shell));
        Vec3 from = Swapper.bodyPos(player);
        Vec3 into = puppet.position();
        float yaw = puppet.getYRot();
        puppet.setPossessed(player);
        Swapper.moveTo(player, into, yaw, player.getXRot());
        player.resetFallDistance();

        VisualService.playAt(level, Swapper.bodyPos(player), PuppetIds.body(PuppetIds.FX_PUPPET_FORM));
        PossessionLinks.travel(level, from, Swapper.bodyPos(player));
        level.playSound(null, shell.getX(), shell.getY(), shell.getZ(), SoundEvents.SOUL_ESCAPE.value(), SoundSource.PLAYERS, 1.2f, 0.9f);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WOOL_PLACE, SoundSource.PLAYERS, 0.9f, 0.7f);
        PuppetAdvancements.award(player, PuppetAdvancements.OUT_OF_BODY);
        changed(player);
    }

    // ---------------------------------------------------------------- return

    /** Ends the possession: the player is back in their body at the shell, the puppet back where they leave it. */
    public static void release(ServerPlayer player, Return why) {
        PossessionData d = data(player);
        if (!d.active()) return;
        ServerLevel level = player.serverLevel();
        PuppetEntity puppet = puppetOf(player, d);
        PuppetShellEntity shell = shellOf(player, d);
        REFS.remove(player.getUUID());
        float leftHealth = player.getHealth();
        Vec3 leftAt = player.position();
        float leftYaw = player.getYRot();

        // Back in the body: real size, health and inventory (anything picked up meanwhile is kept, never lost).
        removeModifiers(player);
        player.setHealth(Math.max(1f, Math.min(d.realHealth, player.getMaxHealth())));
        Inventory inventory = player.getInventory();
        List<ItemStack> meanwhile = new ArrayList<>();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            if (!inventory.getItem(i).isEmpty()) meanwhile.add(inventory.getItem(i).copy());
        }
        inventory.clearContent();
        inventory.load(d.inventory);
        inventory.selected = d.selectedSlot;
        meanwhile.forEach(inventory::placeItemBackInInventory);
        player.inventoryMenu.broadcastChanges();
        Vec3 bodyAt = shell != null ? shell.position() : d.bodyPos;
        float bodyYaw = shell != null ? shell.getYRot() : d.bodyYaw;
        if (d.dimension == null || d.dimension == level.dimension()) {
            Swapper.moveTo(player, bodyAt, bodyYaw, d.bodyPitch);
        } else { // only after a crash: the body is in another dimension
            ServerLevel bodyLevel = player.server.getLevel(d.dimension);
            if (bodyLevel != null) player.teleportTo(bodyLevel, bodyAt.x, bodyAt.y, bodyAt.z, bodyYaw, d.bodyPitch);
        }
        player.resetFallDistance();
        if (shell != null) shell.discard();
        d.clear();

        // The puppet comes back out where they left it (unless it died).
        if (puppet != null) {
            puppet.setPossessed(null);
            if (why == Return.PUPPET_DIED) {
                Puppets.dissolve(puppet);
            } else {
                puppet.moveTo(leftAt.x, leftAt.y, leftAt.z, leftYaw, 0f);
                puppet.setHealth(Math.max(0.5f, Math.min(leftHealth, puppet.getMaxHealth())));
            }
        }
        if (why == Return.PUPPET_DIED) backlash(player);
        if (why == Return.DEATH) player.setHealth(0f); // the death goes ahead, in the body

        VisualService.playAt(level, Swapper.bodyPos(player), PuppetIds.body(PuppetIds.FX_PUPPET_FORM));
        VisualService.playOnce(player, Visual.animation(PuppetIds.ANIM_POSSESS_RETURN));
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SOUL_ESCAPE.value(), SoundSource.PLAYERS, 1.0f, 1.3f);
        if (why != Return.RETURNED && why != Return.LOGOUT && why != Return.TECHNIQUE_CHANGED) {
            player.displayClientMessage(Component.translatable("message.kegare.possess.snapped_back." + why.name().toLowerCase(java.util.Locale.ROOT)), true);
        }
        changed(player);
    }

    /** The possessed puppet died: nausea, a brief stun and lost Malice. */
    private static void backlash(ServerPlayer player) {
        int nausea = PuppetConfig.ticks(PuppetConfig.BACKLASH_NAUSEA);
        if (nausea > 0) player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, nausea, 0));
        CombatHelper.stun(player, PuppetConfig.ticks(PuppetConfig.BACKLASH_STUN));
        double lose = Math.min(PuppetConfig.BACKLASH_MALICE.get(), MaliceService.get(player).current());
        if (lose > 0) MaliceService.trySpend(player, lose);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WOOL_BREAK, SoundSource.PLAYERS, 1.0f, 0.6f);
    }

    private static void removeModifiers(ServerPlayer player) {
        player.getAttribute(Attributes.SCALE).removeModifier(SCALE_MODIFIER);
        player.getAttribute(Attributes.MAX_HEALTH).removeModifier(HEALTH_MODIFIER);
        player.refreshDimensions();
    }

    private static void changed(ServerPlayer player) {
        PlayerSync.markDirty(player);
        PuppetHud.markDirty(player);
        PossessionLinks.broadcast(player.server);
    }

    // ---------------------------------------------------------------- hooks

    /** Every tick for a possessing player: snap back if the puppet or body is gone, or they wandered too far. */
    static void tick(ServerPlayer player) {
        PossessionData d = data(player);
        if (!d.active()) return;
        PuppetEntity puppet = puppetOf(player, d);
        if (puppet == null || !puppet.isAlive()) {
            release(player, Return.PUPPET_DIED);
            return;
        }
        PuppetShellEntity shell = shellOf(player, d);
        if (shell == null || !shell.isAlive()) {
            release(player, Return.SHELL_HIT);
            return;
        }
        double max = PuppetConfig.POSSESS_MAX_DISTANCE.get();
        if (player.distanceToSqr(shell) > max * max) release(player, Return.TOO_FAR);
    }

    /** R returns; every other key does nothing (no casting while possessing). */
    static boolean intercept(ServerPlayer player, CastIntent intent) {
        if (!isPossessing(player)) return false;
        switch (intent) {
            case SPECIAL_PRESS -> release(player, Return.RETURNED);
            case CAST_PRESS, ULTIMATE_PRESS -> player.displayClientMessage(Component.translatable("message.kegare.possess.no_casting"), true);
            default -> { }
        }
        return true;
    }

    /** The empty body was hit: snap back, and the hit lands on the real body. */
    static boolean onShellHit(PuppetShellEntity shell, DamageSource source, float amount) {
        ServerPlayer owner = ownerOf(shell);
        if (owner == null) {
            shell.discard();
            return false;
        }
        if (source.getEntity() instanceof ServerPlayer attacker && attacker != owner) {
            PuppetAdvancements.award(owner, PuppetAdvancements.NOBODY_HOME);
        }
        release(owner, Return.SHELL_HIT);
        owner.invulnerableTime = 0;
        owner.hurt(source, amount);
        return true;
    }

    /** Whether this shell still has someone possessing (else it goes). */
    static boolean isShell(PuppetShellEntity shell) {
        return ownerOf(shell) != null;
    }

    @Nullable
    private static ServerPlayer ownerOf(PuppetShellEntity shell) {
        UUID owner = shell.ownerId();
        if (owner == null || !(shell.level().getPlayerByUUID(owner) instanceof ServerPlayer player)) return null;
        PossessionData d = player.hasData(PuppetRegistry.POSSESSION.get()) ? data(player) : null;
        return d != null && d.active() && shell.getUUID().equals(d.shell) ? player : null;
    }

    /** The possessor of {@code puppet}, if any (for the puppet's own tick). */
    @Nullable
    static ServerPlayer possessorOf(PuppetEntity puppet, @Nullable UUID possessor) {
        if (possessor == null || !(puppet.level().getPlayerByUUID(possessor) instanceof ServerPlayer player)) return null;
        return isPossessing(player) && puppet.getUUID().equals(data(player).puppet) ? player : null;
    }

    /**
     * Login after a server stop mid-possession: everything comes back (the shell and the puppet's hiding weren't
     * saved; the puppet is wherever it was last saved).
     */
    static void restoreOnLogin(ServerPlayer player) {
        if (isPossessing(player)) release(player, Return.LOGOUT);
    }

    /** Server side: everyone possessing right now (for the link and look broadcast). */
    static List<ServerPlayer> possessing(MinecraftServer server) {
        List<ServerPlayer> out = new ArrayList<>();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) if (isPossessing(p)) out.add(p);
        return out;
    }
}

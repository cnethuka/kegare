package com.cnethuka.kegare.melee;

import com.cnethuka.kegare.Kegare;
import com.cnethuka.kegare.innatetechnique.CastManager;
import com.cnethuka.kegare.innatetechnique.visual.VisualHooks;
import com.cnethuka.kegare.innatetechnique.visual.VisualService;
import com.cnethuka.kegare.innate_technique.puppet_manipulation.Possession;
import java.util.function.Supplier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/** The shared melee layer: Dash, Parry and Reinforced Strike. Not techniques: they take no slots and don't go through casting. See DESIGN §3.22. */
public final class MeleeLayer {
    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, Kegare.MODID);
    static final Supplier<AttachmentType<MeleeState>> ATTACHMENT = ATTACHMENTS.register("melee", () -> AttachmentType.builder(MeleeState::new).build());

    private MeleeLayer() {}

    public static void init(IEventBus modBus) {
        ATTACHMENTS.register(modBus);
        CastManager.addStartListener(Parry::onCastStarted);
        MeleeRules.addBlocker(Possession::isPossessing);
        VisualHooks.registerEffect(MeleeIds.FX_DASH_BURST);
        VisualHooks.registerEffect(MeleeIds.FX_DASH_TRAIL);
        VisualHooks.registerEffect(MeleeIds.FX_DASH_TRAIL_SELF);
        VisualHooks.registerEffect(MeleeIds.FX_PARRY_SPARK);
        VisualHooks.registerEffect(MeleeIds.FX_PARRY_SPARK_SELF);
        VisualHooks.registerEffect(MeleeIds.FX_GUARD_HIT);
        VisualHooks.registerEffect(MeleeIds.FX_GUARD_HIT_SELF);
        VisualHooks.registerEffect(MeleeIds.FX_STRIKE_IMPACT);
        VisualHooks.registerEffect(MeleeIds.FX_STRIKE_BURST);
        VisualHooks.registerEffect(MeleeIds.FX_STRIKE_IMPACT_PHYSICAL);
        VisualHooks.registerEffect(MeleeIds.FX_FIST_STEAM);
        VisualHooks.registerEffect(MeleeIds.FX_FIST_STEAM_SELF);
    }

    public static MeleeState state(ServerPlayer player) {
        return player.getData(ATTACHMENT);
    }

    /** Every server tick, for players who have used the layer. */
    public static void tick(ServerPlayer player) {
        if (!player.hasData(ATTACHMENT)) return;
        MeleeState state = state(player);
        Dash.tick(player, state);
        CleanDodge.tick(player, state);
        Parry.tick(player, state);
        ReinforcedStrike.tick(player, state);
        if (MeleeRules.isBlocked(player)) ReinforcedStrike.clear(player, state); // a possessed body's punch isn't theirs to reinforce
    }

    /** Everything in progress stops: a dimension change, as with casts and their visuals. */
    static void reset(ServerPlayer player) {
        if (!player.hasData(ATTACHMENT)) return;
        MeleeState state = state(player);
        Dash.cancel(player, state);
        state.ghost = null;
        state.guarding = false;
        state.strikeUntil = 0;
        VisualService.stopLoop(player, MeleeIds.GUARD_LOOP);
        MeleeTell.sync(player, state, false);
        syncHud(player);
    }

    /** Ticks until Dash is ready again. */
    public static long dashCooldownLeft(ServerPlayer player) {
        return Math.max(0, state(player).dashReadyAt - player.level().getGameTime());
    }

    /** Dash is ready now (an admin's cooldown reset), and the HUD says so. */
    public static void clearDashCooldown(ServerPlayer player) {
        state(player).dashReadyAt = 0;
        syncHud(player);
    }

    /** Tells the owner what their HUD shows, if that changed. Times, so the client counts them down itself. */
    static void syncHud(ServerPlayer player) {
        MeleeState state = state(player);
        MeleeNumbers numbers = MeleeNumbers.of(player);
        MeleeHudPayload hud = new MeleeHudPayload(state.dashReadyAt, numbers.dashCooldownTicks(), state.recoveryUntil, numbers.recoveryTicks(),
                state.strikeUntil, state.strikeTicks, false);
        if (hud.equals(state.sent)) return;
        state.sent = hud;
        PacketDistributor.sendToPlayer(player, hud);
    }

    static void sendAttackReset(ServerPlayer player) {
        MeleeState state = state(player);
        MeleeNumbers numbers = MeleeNumbers.of(player);
        PacketDistributor.sendToPlayer(player, new MeleeHudPayload(state.dashReadyAt, numbers.dashCooldownTicks(), state.recoveryUntil, numbers.recoveryTicks(),
                state.strikeUntil, state.strikeTicks, true));
    }
}

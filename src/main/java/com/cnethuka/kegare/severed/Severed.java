package com.cnethuka.kegare.severed;

import com.cnethuka.kegare.Kegare;
import com.cnethuka.kegare.malice.MaliceService;
import java.util.function.Supplier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * Severed players' physical identity (DESIGN §3.27): they trade all their Malice for a superhuman body. No Malice, no glow, no Innate
 * color: everything here comes from the body.
 */
public final class Severed {
    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, Kegare.MODID);
    static final Supplier<AttachmentType<SeveredState>> STATE = ATTACHMENTS.register("severed", () -> AttachmentType.builder(SeveredState::new).build());

    private Severed() {}

    public static void init(IEventBus modBus) {
        ATTACHMENTS.register(modBus);
        NeoForge.EVENT_BUS.addListener(SeveredBody::onIncoming);
        com.cnethuka.kegare.innatetechnique.CastManager.addInterceptor(ClosingLine::intercept); // R while Blurring
        SeveredAdvancements.init();
        NeoForge.EVENT_BUS.addListener(SeveredBody::onDamage);
        NeoForge.EVENT_BUS.addListener((PlayerTickEvent.Post e) -> {
            if (e.getEntity() instanceof ServerPlayer p && !(p instanceof FakePlayer)) {
                Senses.tick(p);
                if (Severed.is(p) && p.tickCount % 5 == 0) SeveredAdvancements.noteIfLow(p); // healed back up since doesn't matter
                if (p.hasData(STATE)) Blur.tick(p, state(p));
            }
        });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> Senses.forget(e.getEntity().getUUID()));
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerRespawnEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer p && p.connection != null) PacketDistributor.sendToPlayer(p, BlurPayload.OFF);
        });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerChangedDimensionEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer p && p.hasData(STATE)) {
                ClosingLine.cancel(p, state(p));
                state(p).forgetMotion();
            }
        });
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent e) -> Senses.clear());
    }

    /** Born with no Malice. */
    public static boolean is(Player player) {
        return MaliceService.isSevered(player);
    }

    public static SeveredState state(ServerPlayer player) {
        return player.getData(STATE);
    }

    /** G from a Severed player: Blur on or off. Anyone else's G is Technique Mode, which never reaches the server. */
    public static void toggleBlur(ServerPlayer player) {
        if (is(player)) Blur.toggle(player);
    }

    public static boolean blurring(ServerPlayer player) {
        return player.hasData(STATE) && state(player).blur;
    }
}

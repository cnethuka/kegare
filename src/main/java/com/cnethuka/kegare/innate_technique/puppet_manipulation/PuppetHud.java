package com.cnethuka.kegare.innate_technique.puppet_manipulation;

import com.cnethuka.kegare.Kegare;
import java.util.function.Consumer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * The owner's own HUD numbers: how many puppets they have and the current Directive order (on the combat panel), and
 * while possessing, the shell's health (under the crosshair, in or out of Technique Mode). Sent only when they change.
 */
public final class PuppetHud {
    private PuppetHud() {}

    /** Forces a resend on the next check. */
    static void markDirty(ServerPlayer player) {
        player.getData(PuppetRegistry.HUD_STATE.get()).last = null;
    }

    /** Every 10 ticks for holders (and anyone still possessing): send if anything changed. */
    static void check(ServerPlayer player) {
        if (player instanceof FakePlayer || player.connection == null) return;
        boolean holds = PuppetManipulation.holds(player);
        boolean possessing = Possession.isPossessing(player);
        State state = player.getData(PuppetRegistry.HUD_STATE.get());
        if (!holds && !possessing && state.last == null) return;
        PossessionData d = Possession.data(player);
        Payload now = new Payload(holds, Puppets.count(player), player.getData(PuppetRegistry.OWNER.get()).order().ordinal(),
                possessing, possessing ? d.realHealth : 0f, possessing ? d.realMaxHealth : 0f);
        if (now.equals(state.last)) return;
        state.last = now;
        PacketDistributor.sendToPlayer(player, now);
    }

    /** What was last sent (runtime attachment). */
    public static final class State {
        Payload last;
    }

    /**
     * @param holds      the player holds Puppet Manipulation (else nothing on the panel)
     * @param order      {@link PuppetOrder} ordinal
     * @param shellHealth the body's (real) health while possessing
     */
    public record Payload(boolean holds, int puppets, int order, boolean possessing, float shellHealth, float shellMaxHealth)
            implements CustomPacketPayload {
        public static final Type<Payload> TYPE = new Type<>(Kegare.id("puppet_hud"));
        public static final StreamCodec<FriendlyByteBuf, Payload> STREAM_CODEC = StreamCodec.ofMember(Payload::write, Payload::read);
        public static final Payload NONE = new Payload(false, 0, 0, false, 0f, 0f);

        /** Set by the client entry point; null on a dedicated server. */
        private static Consumer<Payload> clientHandler;

        public static void setClientHandler(Consumer<Payload> handler) {
            clientHandler = handler;
        }

        private void write(FriendlyByteBuf buf) {
            buf.writeBoolean(holds);
            buf.writeVarInt(puppets);
            buf.writeVarInt(order);
            buf.writeBoolean(possessing);
            buf.writeFloat(shellHealth);
            buf.writeFloat(shellMaxHealth);
        }

        private static Payload read(FriendlyByteBuf buf) {
            return new Payload(buf.readBoolean(), buf.readVarInt(), buf.readVarInt(), buf.readBoolean(), buf.readFloat(), buf.readFloat());
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        public static void handle(Payload payload, IPayloadContext context) {
            if (clientHandler != null) clientHandler.accept(payload);
        }
    }
}

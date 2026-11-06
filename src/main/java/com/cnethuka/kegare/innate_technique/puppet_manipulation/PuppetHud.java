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
 * The owner's own HUD numbers: their puppets against the cap and the current Directive order (on the combat panel), and
 * while possessing, the shell's health (under the crosshair, in or out of Technique Mode). Sent only when they change.
 */
public final class PuppetHud {
    private PuppetHud() {}

    /** Forces a resend on the next check. */
    static void markDirty(ServerPlayer player) {
        player.getData(PuppetRegistry.HUD_STATE.get()).dirty = true;
    }

    /** Something the HUD shows just changed: send it now, not at the next regular check. */
    static void refresh(ServerPlayer player) {
        markDirty(player);
        check(player);
    }

    /** Every 10 ticks for holders (and anyone still possessing): send if anything changed. */
    static void check(ServerPlayer player) {
        if (player instanceof FakePlayer || player.connection == null) return;
        update(player, now -> PacketDistributor.sendToPlayer(player, now));
    }

    /** {@link #check} with the sending passed in, so a test can see what goes out. */
    static void update(ServerPlayer player, Consumer<Payload> send) {
        boolean holds = PuppetManipulation.holds(player);
        boolean possessing = Possession.isPossessing(player);
        State state = player.getData(PuppetRegistry.HUD_STATE.get());
        // Nothing of theirs is on screen, so there's nothing to clear: the usual case, for everyone who never held it.
        if (!holds && !possessing && state.last == null) {
            state.dirty = false;
            return;
        }
        PossessionData d = Possession.data(player);
        Payload now = new Payload(holds, Puppets.count(player), PuppetConfig.MAX_PUPPETS.get(), player.getData(PuppetRegistry.OWNER.get()).order().ordinal(),
                possessing, possessing ? d.realHealth : 0f, possessing ? d.realMaxHealth : 0f, Strings.hud(player));
        if (!state.dirty && now.equals(state.last)) return;
        state.dirty = false;
        state.last = holds || possessing ? now : null; // the update that clears it is the last one they need
        send.accept(now);
    }

    /** What was last sent, while there's something of theirs on screen (runtime attachment). */
    public static final class State {
        Payload last;
        boolean dirty;
    }

    /**
     * @param holds      the player holds Puppet Manipulation (else nothing on the panel)
     * @param order      {@link PuppetOrder} ordinal
     * @param shellHealth the body's (real) health while possessing
     */
    /**
     * The owner's string: its stage (0 ready to fire, 1 attached, 2 at work after its second stage), what each key would do
     * right now and how long it has left (only while attached), and every cost, for the HUD and Sorcerer screen.
     */
    public record StringsHud(int stage, StringsVariation cast, StringsVariation special, int secondsLeft, float fireCost, float reelCost, float tieCost, float zipCost, float leashCost) {
        public static final StringsHud NONE = new StringsHud(0, StringsVariation.REEL_IN, StringsVariation.ZIP, 0, 0f, 0f, 0f, 0f, 0f);

        public float cost(StringsVariation variation) {
            return switch (variation) {
                case REEL_IN -> reelCost;
                case TIE -> tieCost;
                case ZIP -> zipCost;
                case LEASH -> leashCost;
            };
        }

        void write(FriendlyByteBuf buf) {
            buf.writeVarInt(stage);
            buf.writeEnum(cast);
            buf.writeEnum(special);
            buf.writeVarInt(secondsLeft);
            buf.writeFloat(fireCost);
            buf.writeFloat(reelCost);
            buf.writeFloat(tieCost);
            buf.writeFloat(zipCost);
            buf.writeFloat(leashCost);
        }

        static StringsHud read(FriendlyByteBuf buf) {
            return new StringsHud(buf.readVarInt(), buf.readEnum(StringsVariation.class), buf.readEnum(StringsVariation.class), buf.readVarInt(),
                    buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat());
        }
    }

    public record Payload(boolean holds, int puppets, int maxPuppets, int order, boolean possessing, float shellHealth, float shellMaxHealth, StringsHud strings)
            implements CustomPacketPayload {
        public static final Type<Payload> TYPE = new Type<>(Kegare.id("puppet_hud"));
        public static final StreamCodec<FriendlyByteBuf, Payload> STREAM_CODEC = StreamCodec.ofMember(Payload::write, Payload::read);
        public static final Payload NONE = new Payload(false, 0, 0, 0, false, 0f, 0f, StringsHud.NONE);

        /** Set by the client entry point; null on a dedicated server. */
        private static Consumer<Payload> clientHandler;

        public static void setClientHandler(Consumer<Payload> handler) {
            clientHandler = handler;
        }

        private void write(FriendlyByteBuf buf) {
            buf.writeBoolean(holds);
            buf.writeVarInt(puppets);
            buf.writeVarInt(maxPuppets);
            buf.writeVarInt(order);
            buf.writeBoolean(possessing);
            buf.writeFloat(shellHealth);
            buf.writeFloat(shellMaxHealth);
            strings.write(buf);
        }

        private static Payload read(FriendlyByteBuf buf) {
            return new Payload(buf.readBoolean(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readBoolean(), buf.readFloat(), buf.readFloat(), StringsHud.read(buf));
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

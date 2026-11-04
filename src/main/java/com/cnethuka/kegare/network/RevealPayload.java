package com.cnethuka.kegare.network;

import com.cnethuka.kegare.Kegare;
import com.cnethuka.kegare.client.gui.ClientGuiHooks;
import com.cnethuka.kegare.malice.MaliceRoller;
import com.cnethuka.kegare.roll.RollReason;
import com.cnethuka.kegare.roll.RollService;
import com.cnethuka.kegare.trait.Trait;
import java.util.List;
import java.util.Optional;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Server to ONE client: show the reveal for a roll that has ALREADY been decided and saved. Purely cosmetic: the
 * screen never sends anything back. Sent only to the rolling player, so birth traits (e.g. lensing_eyes) stay private.
 *
 * @param innateTechnique the result, empty for a Severed birth (or when nothing was eligible)
 * @param birth           Awakening: also shows Malice capacity/tier, the Severed reveal, and birth traits
 * @param traits          traits granted at birth, revealed only to this player
 */
public record RevealPayload(
        RollReason reason,
        Optional<ResourceLocation> innateTechnique,
        int rollNumber,
        boolean byHardPity,
        boolean birth,
        int capacity,
        int tier,
        boolean severed,
        List<ResourceLocation> traits
) implements CustomPacketPayload {
    public static final Type<RevealPayload> TYPE = new Type<>(Kegare.id("reveal"));
    public static final StreamCodec<FriendlyByteBuf, RevealPayload> STREAM_CODEC = StreamCodec.ofMember(RevealPayload::write, RevealPayload::read);

    /** A reroll (talisman, admin) or a replacement roll. */
    public static void sendRoll(ServerPlayer player, RollService.Outcome outcome) {
        send(player, new RevealPayload(outcome.reason(), Optional.of(outcome.innateTechnique().id()), outcome.rollNumber(),
                outcome.byHardPity(), false, 0, 0, false, List.of()));
    }

    /** Awakening: Malice, the Severed state or the innate technique roll, and birth traits. */
    public static void sendBirth(ServerPlayer player, MaliceRoller.Result malice, Optional<RollService.Outcome> outcome, List<Trait> traits) {
        send(player, new RevealPayload(
                RollReason.BIRTH,
                outcome.map(o -> o.innateTechnique().id()),
                outcome.map(RollService.Outcome::rollNumber).orElse(0),
                outcome.map(RollService.Outcome::byHardPity).orElse(false),
                true, malice.capacity(), malice.tier().ordinal(), malice.capacity() <= 0,
                traits.stream().map(Trait::id).toList()));
    }

    private static void send(ServerPlayer player, RevealPayload payload) {
        if (player instanceof FakePlayer || player.connection == null) return;
        PacketDistributor.sendToPlayer(player, payload);
    }

    private void write(FriendlyByteBuf buf) {
        buf.writeEnum(reason);
        buf.writeOptional(innateTechnique, FriendlyByteBuf::writeResourceLocation);
        buf.writeVarInt(rollNumber);
        buf.writeBoolean(byHardPity);
        buf.writeBoolean(birth);
        buf.writeVarInt(capacity);
        buf.writeVarInt(tier);
        buf.writeBoolean(severed);
        buf.writeCollection(traits, FriendlyByteBuf::writeResourceLocation);
    }

    private static RevealPayload read(FriendlyByteBuf buf) {
        return new RevealPayload(buf.readEnum(RollReason.class), buf.readOptional(FriendlyByteBuf::readResourceLocation),
                buf.readVarInt(), buf.readBoolean(), buf.readBoolean(), buf.readVarInt(), buf.readVarInt(), buf.readBoolean(),
                buf.readList(FriendlyByteBuf::readResourceLocation));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** Client main thread. ClientGuiHooks is only resolved when this runs, i.e. never on a dedicated server. */
    static void handle(RevealPayload payload, IPayloadContext context) {
        ClientGuiHooks.onReveal(payload);
    }
}

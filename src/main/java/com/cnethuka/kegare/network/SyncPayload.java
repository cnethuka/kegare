package com.cnethuka.kegare.network;

import com.cnethuka.kegare.Kegare;
import com.cnethuka.kegare.malice.MaliceData;
import com.cnethuka.kegare.malice.MaliceService;
import com.cnethuka.kegare.client.ClientData;
import com.cnethuka.kegare.innatetechnique.CastState;
import com.cnethuka.kegare.innatetechnique.CastManager;
import com.cnethuka.kegare.innatetechnique.CastType;
import com.cnethuka.kegare.innatetechnique.InnateTechniqueData;
import com.cnethuka.kegare.innatetechnique.InnateTechniqueService;
import com.cnethuka.kegare.trait.TraitService;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Server to owning client: full snapshot of everything the HUD needs. Ticks are level game time,
 * which the client keeps in sync on its own, so countdowns render smoothly between packets.
 */
public record SyncPayload(
        boolean born,
        boolean severed,
        int tier,
        int capacity,
        float current,
        Optional<ResourceLocation> innateTechnique,
        int selectedSlot,
        List<CooldownEntry> cooldowns,
        List<ResourceLocation> activeToggles,
        Optional<CastingEntry> casting,
        List<ResourceLocation> traits
) implements CustomPacketPayload {
    public record CooldownEntry(ResourceLocation ability, long endTick, int duration) {}

    /** @param maxTicks CHARGED: ticks to full charge; CHANNELED: max duration (0 = unlimited) */
    public record CastingEntry(ResourceLocation ability, long startTick, int maxTicks) {}

    public static final Type<SyncPayload> TYPE = new Type<>(Kegare.id("sync"));
    public static final StreamCodec<FriendlyByteBuf, SyncPayload> STREAM_CODEC = StreamCodec.ofMember(SyncPayload::write, SyncPayload::read);

    public static SyncPayload of(ServerPlayer player) {
        MaliceData malice = MaliceService.get(player);
        InnateTechniqueData innateTechnique = InnateTechniqueService.get(player);
        long now = player.level().getGameTime();

        List<CooldownEntry> cooldowns = new ArrayList<>();
        innateTechnique.cooldowns().forEach((id, cd) -> {
            if (cd.endTick() > now) cooldowns.add(new CooldownEntry(id, cd.endTick(), cd.duration()));
        });

        CastState cast = CastManager.state(player);
        List<ResourceLocation> toggles = new ArrayList<>(cast.toggles().keySet());
        Optional<CastingEntry> casting = Optional.ofNullable(cast.casting()).map(active -> new CastingEntry(
                active.ability().id(),
                active.startTick(),
                active.ability().castType() == CastType.CHARGED
                        ? active.ability().maxChargeTicks(player)
                        : active.ability().maxChannelTicks(player)));

        return new SyncPayload(
                malice.isBorn(), malice.isSevered(), malice.tier().ordinal(), malice.capacity(), (float) malice.current(),
                innateTechnique.innateTechniqueId(), innateTechnique.selectedSlot(),
                cooldowns, toggles, casting,
                new ArrayList<>(TraitService.get(player).view()));
    }

    private void write(FriendlyByteBuf buf) {
        buf.writeBoolean(born);
        buf.writeBoolean(severed);
        buf.writeVarInt(tier);
        buf.writeVarInt(capacity);
        buf.writeFloat(current);
        buf.writeOptional(innateTechnique, FriendlyByteBuf::writeResourceLocation);
        buf.writeVarInt(selectedSlot);
        buf.writeCollection(cooldowns, (b, c) -> {
            b.writeResourceLocation(c.ability());
            b.writeVarLong(c.endTick());
            b.writeVarInt(c.duration());
        });
        buf.writeCollection(activeToggles, FriendlyByteBuf::writeResourceLocation);
        buf.writeOptional(casting, (b, c) -> {
            b.writeResourceLocation(c.ability());
            b.writeVarLong(c.startTick());
            b.writeVarInt(c.maxTicks());
        });
        buf.writeCollection(traits, FriendlyByteBuf::writeResourceLocation);
    }

    private static SyncPayload read(FriendlyByteBuf buf) {
        return new SyncPayload(
                buf.readBoolean(),
                buf.readBoolean(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readFloat(),
                buf.readOptional(FriendlyByteBuf::readResourceLocation),
                buf.readVarInt(),
                buf.readList(b -> new CooldownEntry(b.readResourceLocation(), b.readVarLong(), b.readVarInt())),
                buf.readList(FriendlyByteBuf::readResourceLocation),
                buf.readOptional(b -> new CastingEntry(b.readResourceLocation(), b.readVarLong(), b.readVarInt())),
                buf.readList(FriendlyByteBuf::readResourceLocation));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** Runs on the client main thread. ClientData holds no client-only classes, so this is dist-safe. */
    static void handle(SyncPayload payload, IPayloadContext context) {
        ClientData.apply(payload);
    }
}

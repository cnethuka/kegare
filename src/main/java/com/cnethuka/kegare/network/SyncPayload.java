package com.cnethuka.kegare.network;

import com.cnethuka.kegare.Kegare;
import com.cnethuka.kegare.innate_technique.rime.FreezeService;
import com.cnethuka.kegare.innate_technique.rime.RimeConfig;
import com.cnethuka.kegare.innatetechnique.Ability;
import com.cnethuka.kegare.innatetechnique.CostEnvironment;
import com.cnethuka.kegare.innatetechnique.InnateTechnique;
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
        List<ResourceLocation> traits,
        CostEnvironment environment,
        List<AbilityCost> costs,
        int freezeStacks,
        int maxFreezeStacks,
        long frozenUntil
) implements CustomPacketPayload {
    public record CooldownEntry(ResourceLocation ability, long endTick, int duration) {}

    /** Current Malice cost and upkeep of an ability where the player stands (environment included). */
    public record AbilityCost(ResourceLocation ability, float cost, float upkeepPerSecond) {}

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

        Optional<InnateTechnique> technique = InnateTechniqueService.current(player);
        CostEnvironment environment = technique.map(t -> t.environment(player)).orElse(CostEnvironment.NEUTRAL);
        List<AbilityCost> costs = new ArrayList<>();
        technique.ifPresent(t -> {
            List<Ability> all = new ArrayList<>(t.abilities());
            t.ultimate().ifPresent(all::add);
            for (Ability a : all) costs.add(new AbilityCost(a.id(), (float) a.cost(player), (float) a.upkeepPerSecond(player)));
        });

        return new SyncPayload(
                malice.isBorn(), malice.isSevered(), malice.tier().ordinal(), malice.capacity(), (float) malice.current(),
                innateTechnique.innateTechniqueId(), innateTechnique.selectedSlot(),
                cooldowns, toggles, casting,
                new ArrayList<>(TraitService.get(player).view()),
                environment, costs,
                FreezeService.stacks(player), RimeConfig.FREEZE_MAX_STACKS.get(), FreezeService.frozenUntil(player));
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
        buf.writeFloat(environment.multiplier());
        buf.writeEnum(environment.icon());
        buf.writeCollection(environment.factors(), (b, f) -> {
            b.writeUtf(f.translationKey(), 128);
            b.writeFloat(f.multiplier());
        });
        buf.writeCollection(costs, (b, c) -> {
            b.writeResourceLocation(c.ability());
            b.writeFloat(c.cost());
            b.writeFloat(c.upkeepPerSecond());
        });
        buf.writeVarInt(freezeStacks);
        buf.writeVarInt(maxFreezeStacks);
        buf.writeVarLong(frozenUntil);
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
                buf.readList(FriendlyByteBuf::readResourceLocation),
                new CostEnvironment(buf.readFloat(), buf.readEnum(CostEnvironment.Icon.class),
                        buf.readList(b -> new CostEnvironment.Factor(b.readUtf(128), b.readFloat()))),
                buf.readList(b -> new AbilityCost(b.readResourceLocation(), b.readFloat(), b.readFloat())),
                buf.readVarInt(), buf.readVarInt(), buf.readVarLong());
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

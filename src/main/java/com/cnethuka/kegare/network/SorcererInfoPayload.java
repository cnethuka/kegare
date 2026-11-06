package com.cnethuka.kegare.network;

import com.cnethuka.kegare.Kegare;
import com.cnethuka.kegare.client.gui.ClientGuiHooks;
import com.cnethuka.kegare.combat.CombatHelper;
import com.cnethuka.kegare.config.KegareServerConfig;
import com.cnethuka.kegare.innatetechnique.Ability;
import com.cnethuka.kegare.innatetechnique.CastManager;
import com.cnethuka.kegare.innatetechnique.InnateTechnique;
import com.cnethuka.kegare.innatetechnique.InnateTechniqueRarity;
import com.cnethuka.kegare.innatetechnique.InnateTechniqueService;
import com.cnethuka.kegare.malice.MaliceData;
import com.cnethuka.kegare.malice.MaliceService;
import com.cnethuka.kegare.roll.PityRoller;
import com.cnethuka.kegare.roll.RollData;
import com.cnethuka.kegare.roll.RollService;
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
 * Server to owning client: everything the Sorcerer screen shows, computed on the server (ability costs and
 * cooldowns take the caster, so the client can't compute them itself).
 */
public record SorcererInfoPayload(
        int capacity, float current, int tier, boolean severed,
        float regenPerSecond, float regenPercent, boolean regenPaused,
        Optional<ResourceLocation> innateTechnique, List<AbilityInfo> abilities, List<AbilityInfo> shared,
        List<ResourceLocation> traits,
        int rollsSinceLegendary, int totalRolls, int hardPity, int rollsUntilSoftPity, int rollsUntilHardPity,
        int legendaryWeight, int baseLegendaryWeight
) implements CustomPacketPayload {
    /** @param castType ordinal of {@code CastType}; cooldown in ticks */
    public record AbilityInfo(ResourceLocation id, int castType, float cost, float upkeepPerSecond, int cooldownTicks, boolean ultimate,
                              Optional<SpecialInfo> special) {}

    /** An ability's special (R). {@code available}: every requirement is met right now. */
    public record SpecialInfo(ResourceLocation id, int castType, float cost, float upkeepPerSecond, boolean available) {}

    public static final Type<SorcererInfoPayload> TYPE = new Type<>(Kegare.id("sorcerer_info"));
    public static final StreamCodec<FriendlyByteBuf, SorcererInfoPayload> STREAM_CODEC = StreamCodec.ofMember(SorcererInfoPayload::write, SorcererInfoPayload::read);

    public static SorcererInfoPayload of(ServerPlayer player) {
        MaliceData malice = MaliceService.get(player);
        double percent = KegareServerConfig.regenPercent(malice.tier());

        Optional<InnateTechnique> innateTechnique = InnateTechniqueService.current(player);
        List<AbilityInfo> abilities = new ArrayList<>();
        innateTechnique.ifPresent(t -> {
            for (Ability a : t.abilities()) abilities.add(info(player, a, false));
            t.ultimate().ifPresent(u -> abilities.add(info(player, u, true)));
        });
        List<AbilityInfo> shared = new ArrayList<>();
        for (Ability a : com.cnethuka.kegare.loadout.SharedAbilities.all()) {
            if (com.cnethuka.kegare.loadout.SharedAbilityService.isUnlocked(player, a.id())) shared.add(info(player, a, false));
        }

        RollData roll = RollService.get(player);
        PityRoller.Settings settings = RollService.settings();
        int counter = roll.rollsSinceLegendary();
        boolean[] all = {true, true, true, true};
        int legendary = InnateTechniqueRarity.LEGENDARY.ordinal();

        return new SorcererInfoPayload(
                malice.capacity(), (float) malice.current(), malice.tier().ordinal(), malice.isSevered(),
                (float) (malice.capacity() * percent / 100.0), (float) percent, CombatHelper.isRegenPaused(player),
                innateTechnique.map(InnateTechnique::id), abilities, shared,
                new ArrayList<>(TraitService.get(player).view()),
                counter, roll.totalRolls(), settings.hardPity(),
                PityRoller.rollsUntilSoftPity(settings, counter), PityRoller.rollsUntilHardPity(settings, counter),
                PityRoller.effectiveWeights(settings, counter, all)[legendary], Math.max(0, settings.baseWeights()[legendary]));
    }

    private static AbilityInfo info(ServerPlayer player, Ability ability, boolean ultimate) {
        Optional<SpecialInfo> special = ability.special().map(s -> new SpecialInfo(s.id(), s.castType().ordinal(),
                (float) s.shownCost(player), (float) s.upkeepPerSecond(player), CastManager.unmetRequirement(player, s).isEmpty()));
        return new AbilityInfo(ability.id(), ability.castType().ordinal(), (float) ability.shownCost(player),
                (float) ability.upkeepPerSecond(player), ability.shownCooldownTicks(player), ultimate, special);
    }

    private void write(FriendlyByteBuf buf) {
        buf.writeVarInt(capacity);
        buf.writeFloat(current);
        buf.writeVarInt(tier);
        buf.writeBoolean(severed);
        buf.writeFloat(regenPerSecond);
        buf.writeFloat(regenPercent);
        buf.writeBoolean(regenPaused);
        buf.writeOptional(innateTechnique, FriendlyByteBuf::writeResourceLocation);
        buf.writeCollection(abilities, SorcererInfoPayload::writeInfo);
        buf.writeCollection(shared, SorcererInfoPayload::writeInfo);
        buf.writeCollection(traits, FriendlyByteBuf::writeResourceLocation);
        buf.writeVarInt(rollsSinceLegendary);
        buf.writeVarInt(totalRolls);
        buf.writeVarInt(hardPity);
        buf.writeVarInt(rollsUntilSoftPity);
        buf.writeInt(rollsUntilHardPity);
        buf.writeVarInt(legendaryWeight);
        buf.writeVarInt(baseLegendaryWeight);
    }

    private static void writeInfo(FriendlyByteBuf b, AbilityInfo a) {
        b.writeResourceLocation(a.id());
        b.writeVarInt(a.castType());
        b.writeFloat(a.cost());
        b.writeFloat(a.upkeepPerSecond());
        b.writeVarInt(a.cooldownTicks());
        b.writeBoolean(a.ultimate());
        b.writeOptional(a.special(), (b2, sp) -> {
            b2.writeResourceLocation(sp.id());
            b2.writeVarInt(sp.castType());
            b2.writeFloat(sp.cost());
            b2.writeFloat(sp.upkeepPerSecond());
            b2.writeBoolean(sp.available());
        });
    }

    private static AbilityInfo readInfo(FriendlyByteBuf b) {
        return new AbilityInfo(b.readResourceLocation(), b.readVarInt(), b.readFloat(), b.readFloat(), b.readVarInt(), b.readBoolean(),
                b.readOptional(b2 -> new SpecialInfo(b2.readResourceLocation(), b2.readVarInt(), b2.readFloat(), b2.readFloat(), b2.readBoolean())));
    }

    private static SorcererInfoPayload read(FriendlyByteBuf buf) {
        return new SorcererInfoPayload(
                buf.readVarInt(), buf.readFloat(), buf.readVarInt(), buf.readBoolean(),
                buf.readFloat(), buf.readFloat(), buf.readBoolean(),
                buf.readOptional(FriendlyByteBuf::readResourceLocation),
                buf.readList(SorcererInfoPayload::readInfo),
                buf.readList(SorcererInfoPayload::readInfo),
                buf.readList(FriendlyByteBuf::readResourceLocation),
                buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readInt(),
                buf.readVarInt(), buf.readVarInt());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    static void handle(SorcererInfoPayload payload, IPayloadContext context) {
        ClientGuiHooks.onSorcererInfo(payload);
    }
}

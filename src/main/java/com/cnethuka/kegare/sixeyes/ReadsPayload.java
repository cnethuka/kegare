package com.cnethuka.kegare.sixeyes;

import com.cnethuka.kegare.Kegare;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Six Eyes' Reads, server to the bearer only (DESIGN §3.32): every player near enough to be in view, at a glance (their innate technique,
 * current Malice and health, or a void), the health of the mobs in view, and full detail on the one body nearest the bearer's crosshair,
 * player or mob: what they're doing and the whole state of their body. Never Grade, Assessment, Refinement, Insight, traits or the brain meter:
 * there's no field for them.
 */
public record ReadsPayload(List<Glance> glances, List<MobGlance> mobs, Optional<Focus> focus) implements CustomPacketPayload {
    /** A player at a glance. Nothing for a void: there's no energy to read. */
    public record Glance(int entityId, Optional<Energy> energy) {
        static final StreamCodec<FriendlyByteBuf, Glance> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Glance::entityId,
                Energy.CODEC.apply(ByteBufCodecs::optional), Glance::energy, Glance::new);

        public boolean severed() {
            return energy.isEmpty();
        }
    }

    /** What the eyes see of a player at a glance: their technique, their current Malice, and how they are. */
    public record Energy(Optional<ResourceLocation> technique, int malice, float health, float maxHealth) {
        static final StreamCodec<FriendlyByteBuf, Energy> CODEC = StreamCodec.composite(
                ResourceLocation.STREAM_CODEC.apply(ByteBufCodecs::optional), Energy::technique,
                ByteBufCodecs.VAR_INT, Energy::malice,
                ByteBufCodecs.FLOAT, Energy::health,
                ByteBufCodecs.FLOAT, Energy::maxHealth, Energy::new);
    }

    /** A mob in view at a glance: how it is (its kind and flow come with what the eyes already see). */
    public record MobGlance(int entityId, float health, float maxHealth) {
        static final StreamCodec<FriendlyByteBuf, MobGlance> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, MobGlance::entityId, ByteBufCodecs.FLOAT, MobGlance::health, ByteBufCodecs.FLOAT, MobGlance::maxHealth, MobGlance::new);
    }

    /** What they're charging or channelling, and how far along (0 to 1; a channel with no end counts up in seconds, as -seconds). */
    public record Cast(ResourceLocation ability, int castType, float progress) {
        static final StreamCodec<FriendlyByteBuf, Cast> CODEC = StreamCodec.composite(
                ResourceLocation.STREAM_CODEC, Cast::ability, ByteBufCodecs.VAR_INT, Cast::castType, ByteBufCodecs.FLOAT, Cast::progress, Cast::new);
    }

    public record Cooldown(ResourceLocation ability, float seconds) {
        static final StreamCodec<FriendlyByteBuf, Cooldown> CODEC = StreamCodec.composite(
                ResourceLocation.STREAM_CODEC, Cooldown::ability, ByteBufCodecs.FLOAT, Cooldown::seconds, Cooldown::new);
    }

    /**
     * The body as it is right now, every modifier counted. An attribute it doesn't have is {@link #NONE}; so is {@code food} for a mob.
     *
     * @param movementBase the movement speed it has with no modifier, to say what the modifiers make of it
     */
    public record Vitals(float health, float maxHealth, float absorption, float armor, float toughness, float knockbackResistance, float attackDamage,
                         float attackSpeed, float movement, float movementBase, int food) {
        public static final float NONE = -1f;
        static final StreamCodec<FriendlyByteBuf, Vitals> CODEC = StreamCodec.of(
                (buf, v) -> {
                    for (float f : new float[] {v.health, v.maxHealth, v.absorption, v.armor, v.toughness, v.knockbackResistance, v.attackDamage,
                            v.attackSpeed, v.movement, v.movementBase}) buf.writeFloat(f);
                    buf.writeVarInt(v.food + 1);
                },
                buf -> new Vitals(buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(),
                        buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readVarInt() - 1));
    }

    /** An active effect: its level (0 is I) and the ticks it has left, -1 if it never runs out. */
    public record Effect(ResourceLocation effect, int amplifier, int ticks) {
        static final StreamCodec<FriendlyByteBuf, Effect> CODEC = StreamCodec.composite(
                ResourceLocation.STREAM_CODEC, Effect::effect, ByteBufCodecs.VAR_INT, Effect::amplifier, ByteBufCodecs.VAR_INT.map(t -> t - 1, t -> t + 1), Effect::ticks,
                Effect::new);
    }

    /** What's happening to the body: on fire, Freeze stacks, a stun's ticks left, a Burnout's ticks left, possessing a puppet. */
    public record Status(boolean burning, int freezeStacks, int stunTicks, int burnoutTicks, boolean possessing) {
        static final StreamCodec<FriendlyByteBuf, Status> CODEC = StreamCodec.composite(
                ByteBufCodecs.BOOL, Status::burning, ByteBufCodecs.VAR_INT, Status::freezeStacks, ByteBufCodecs.VAR_INT, Status::stunTicks,
                ByteBufCodecs.VAR_INT, Status::burnoutTicks, ByteBufCodecs.BOOL, Status::possessing, Status::new);
    }

    /** Everything about one body. A mob does nothing to cast, cool down or switch on. */
    public record Body(Optional<Cast> cast, List<Cooldown> cooldowns, List<ResourceLocation> toggles, Vitals vitals, List<Effect> effects, Status status) {
        static final StreamCodec<FriendlyByteBuf, Body> CODEC = StreamCodec.composite(
                Cast.CODEC.apply(ByteBufCodecs::optional), Body::cast,
                Cooldown.CODEC.apply(ByteBufCodecs.list()), Body::cooldowns,
                ResourceLocation.STREAM_CODEC.apply(ByteBufCodecs.list()), Body::toggles,
                Vitals.CODEC, Body::vitals,
                Effect.CODEC.apply(ByteBufCodecs.list()), Body::effects,
                Status.CODEC, Body::status, Body::new);
    }

    /** Full detail on the one body nearest the crosshair. Nothing for a void: there's nothing to read. */
    public record Focus(int entityId, Optional<Body> body) {
        static final StreamCodec<FriendlyByteBuf, Focus> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Focus::entityId, Body.CODEC.apply(ByteBufCodecs::optional), Focus::body, Focus::new);

        public boolean severed() {
            return body.isEmpty();
        }
    }

    public static final Type<ReadsPayload> TYPE = new Type<>(Kegare.id("six_eyes_reads"));
    public static final StreamCodec<FriendlyByteBuf, ReadsPayload> STREAM_CODEC = StreamCodec.composite(
            Glance.CODEC.apply(ByteBufCodecs.list()), ReadsPayload::glances,
            MobGlance.CODEC.apply(ByteBufCodecs.list()), ReadsPayload::mobs,
            Focus.CODEC.apply(ByteBufCodecs::optional), ReadsPayload::focus, ReadsPayload::new);

    public static Consumer<ReadsPayload> clientHandler;

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ReadsPayload p, IPayloadContext context) {
        if (clientHandler != null) context.enqueueWork(() -> clientHandler.accept(p));
    }
}

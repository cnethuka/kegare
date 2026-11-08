package com.cnethuka.kegare.impact;

import com.cnethuka.kegare.Kegare;
import java.util.function.Consumer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Server to the clients near a hit (DESIGN §3.23): what landed, where and how hard. Each client works out its own part from the two
 * ids: the attacker and the victim get the freeze, the shake and (the biggest hits) the impact frame; everyone else only the flash.
 * Cosmetic: the hit has already landed on the server.
 *
 * @param parts     which parts play ({@link #FLASH}, {@link #HIT_STOP}, {@link #SHAKE}, {@link #FRAME})
 * @param direction which way the hit was going (unit)
 * @param color     the flash's Malice color (RGB) as this client may see it; 0 is white
 * @param attacker  the attacker's entity id, or -1
 * @param victim    the victim's entity id, or -1
 */
public record ImpactPayload(ImpactTier tier, ImpactKind kind, int parts, Vec3 contact, Vec3 direction, int color, int attacker, int victim,
                            float strength) implements CustomPacketPayload {
    public static final int FLASH = 1, HIT_STOP = 2, SHAKE = 4, FRAME = 8;
    public static final int ALL = FLASH | HIT_STOP | SHAKE | FRAME;

    public static final Type<ImpactPayload> TYPE = new Type<>(Kegare.id("impact"));
    public static final StreamCodec<FriendlyByteBuf, ImpactPayload> STREAM_CODEC = StreamCodec.ofMember(ImpactPayload::write, ImpactPayload::read);

    /** Set by the client entry point; null on a dedicated server. */
    private static Consumer<ImpactPayload> clientHandler;

    public static void setClientHandler(Consumer<ImpactPayload> handler) {
        clientHandler = handler;
    }

    public boolean has(int part) {
        return (parts & part) != 0;
    }

    private void write(FriendlyByteBuf buf) {
        buf.writeByte(tier.ordinal());
        buf.writeByte(kind.ordinal());
        buf.writeByte(parts);
        buf.writeDouble(contact.x);
        buf.writeDouble(contact.y);
        buf.writeDouble(contact.z);
        buf.writeFloat((float) direction.x);
        buf.writeFloat((float) direction.y);
        buf.writeFloat((float) direction.z);
        buf.writeInt(color);
        buf.writeVarInt(attacker + 1);
        buf.writeVarInt(victim + 1);
        buf.writeFloat(strength);
    }

    private static ImpactPayload read(FriendlyByteBuf buf) {
        ImpactTier tier = ImpactTier.byId(buf.readByte());
        ImpactKind kind = ImpactKind.values()[Math.floorMod(buf.readByte(), ImpactKind.values().length)];
        int parts = buf.readByte();
        Vec3 contact = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
        Vec3 direction = new Vec3(buf.readFloat(), buf.readFloat(), buf.readFloat());
        return new ImpactPayload(tier, kind, parts, contact, direction, buf.readInt(), buf.readVarInt() - 1, buf.readVarInt() - 1, buf.readFloat());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ImpactPayload payload, IPayloadContext context) {
        if (clientHandler != null) clientHandler.accept(payload);
    }
}

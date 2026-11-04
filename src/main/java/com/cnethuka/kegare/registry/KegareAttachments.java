package com.cnethuka.kegare.registry;

import com.cnethuka.kegare.Kegare;
import com.cnethuka.kegare.malice.MaliceData;
import com.cnethuka.kegare.combat.CombatData;
import com.cnethuka.kegare.network.SyncState;
import com.cnethuka.kegare.roll.RollData;
import com.cnethuka.kegare.innatetechnique.CastState;
import com.cnethuka.kegare.innatetechnique.InnateTechniqueData;
import com.cnethuka.kegare.trait.TraitData;
import java.util.function.Supplier;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * All per-entity state is stored in NeoForge data attachments.
 * <pre>
 *   Malice          saved, kept on death   capacity/tier/severed fixed at birth + current reserve
 *   traits      saved, kept on death   set of trait IDs
 *   innate technique   saved, kept on death   innate technique, selected slot, cooldowns
 *   roll        saved, kept on death   pity counter
 *   combat      saved, reset on death  combat tag + stun timestamps
 *   cast_state  runtime only           active charge/channel/toggles
 *   sync_state  runtime only           client sync dirty flag
 * </pre>
 */
public final class KegareAttachments {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, Kegare.MODID);

    public static final Supplier<AttachmentType<MaliceData>> MALICE = ATTACHMENT_TYPES.register("malice",
            () -> AttachmentType.builder(() -> new MaliceData()).serialize(MaliceData.CODEC).copyOnDeath().build());

    public static final Supplier<AttachmentType<TraitData>> TRAITS = ATTACHMENT_TYPES.register("traits",
            () -> AttachmentType.builder(() -> new TraitData()).serialize(TraitData.CODEC).copyOnDeath().build());

    public static final Supplier<AttachmentType<InnateTechniqueData>> INNATE_TECHNIQUE = ATTACHMENT_TYPES.register("innate_technique",
            () -> AttachmentType.builder(() -> new InnateTechniqueData()).serialize(InnateTechniqueData.CODEC).copyOnDeath().build());

    public static final Supplier<AttachmentType<RollData>> ROLL = ATTACHMENT_TYPES.register("roll",
            () -> AttachmentType.builder(() -> new RollData()).serialize(RollData.CODEC).copyOnDeath().build());

    public static final Supplier<AttachmentType<CombatData>> COMBAT = ATTACHMENT_TYPES.register("combat",
            () -> AttachmentType.builder(() -> new CombatData()).serialize(CombatData.CODEC).build());

    public static final Supplier<AttachmentType<CastState>> CAST_STATE = ATTACHMENT_TYPES.register("cast_state",
            () -> AttachmentType.builder(() -> new CastState()).build());

    public static final Supplier<AttachmentType<SyncState>> SYNC_STATE = ATTACHMENT_TYPES.register("sync_state",
            () -> AttachmentType.builder(() -> new SyncState()).build());

    private KegareAttachments() {}
}

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
import com.cnethuka.kegare.innatetechnique.visual.VisualState;
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
 *   loadout     saved, kept on death   what each regular ability slot holds
 *   shared_abilities saved, kept on death unlocked shared abilities, last green-orb roll
 *   cast_state  runtime only           active charge/channel/toggles
 *   sync_state  runtime only           client sync dirty flag
 *   visual_state runtime only          cosmetic loops playing on an entity (re-sent to late trackers)
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

    /** Burnout's ticks left (DESIGN §3.25): saved, but not copied on death, so dying clears it. */
    public static final Supplier<AttachmentType<com.cnethuka.kegare.innatetechnique.Burnout.Data>> BURNOUT = ATTACHMENT_TYPES.register("burnout",
            () -> AttachmentType.builder(() -> new com.cnethuka.kegare.innatetechnique.Burnout.Data())
                    .serialize(com.cnethuka.kegare.innatetechnique.Burnout.Data.CODEC).build());

    public static final Supplier<AttachmentType<com.cnethuka.kegare.loadout.LoadoutData>> LOADOUT = ATTACHMENT_TYPES.register("loadout",
            () -> AttachmentType.builder(() -> new com.cnethuka.kegare.loadout.LoadoutData())
                    .serialize(com.cnethuka.kegare.loadout.LoadoutData.CODEC).copyOnDeath().build());

    public static final Supplier<AttachmentType<com.cnethuka.kegare.loadout.SharedAbilityData>> SHARED_ABILITIES = ATTACHMENT_TYPES.register("shared_abilities",
            () -> AttachmentType.builder(() -> new com.cnethuka.kegare.loadout.SharedAbilityData())
                    .serialize(com.cnethuka.kegare.loadout.SharedAbilityData.CODEC).copyOnDeath().build());

    /** Background Task slots (DESIGN §3.31): saved, kept on death and through rerolls. */
    public static final Supplier<AttachmentType<com.cnethuka.kegare.backgroundtask.BackgroundTaskData>> BACKGROUND_TASKS = ATTACHMENT_TYPES.register(
            "background_tasks", () -> AttachmentType.builder(() -> new com.cnethuka.kegare.backgroundtask.BackgroundTaskData())
                    .serialize(com.cnethuka.kegare.backgroundtask.BackgroundTaskData.CODEC).copyOnDeath().build());

    /** Health healed with your own channelled RMT, in total (DESIGN §3.31): saved, kept on death. */
    public static final Supplier<AttachmentType<com.cnethuka.kegare.backgroundtask.RmtHealed>> RMT_HEALED = ATTACHMENT_TYPES.register(
            "rmt_healed", () -> AttachmentType.builder(() -> new com.cnethuka.kegare.backgroundtask.RmtHealed())
                    .serialize(com.cnethuka.kegare.backgroundtask.RmtHealed.CODEC).copyOnDeath().build());

    public static final Supplier<AttachmentType<CastState>> CAST_STATE = ATTACHMENT_TYPES.register("cast_state",
            () -> AttachmentType.builder(() -> new CastState()).build());

    public static final Supplier<AttachmentType<SyncState>> SYNC_STATE = ATTACHMENT_TYPES.register("sync_state",
            () -> AttachmentType.builder(() -> new SyncState()).build());

    public static final Supplier<AttachmentType<VisualState>> VISUAL_STATE = ATTACHMENT_TYPES.register("visual_state",
            () -> AttachmentType.builder(() -> new VisualState()).build());

    public static final Supplier<AttachmentType<com.cnethuka.kegare.progression.GradeData>> GRADE = ATTACHMENT_TYPES.register("grade",
            () -> AttachmentType.builder(() -> new com.cnethuka.kegare.progression.GradeData())
                    .serialize(com.cnethuka.kegare.progression.GradeData.CODEC).copyOnDeath().build());

    public static final Supplier<AttachmentType<com.cnethuka.kegare.progression.AssessmentData>> ASSESSMENT = ATTACHMENT_TYPES.register("assessment",
            () -> AttachmentType.builder(() -> new com.cnethuka.kegare.progression.AssessmentData())
                    .serialize(com.cnethuka.kegare.progression.AssessmentData.CODEC).copyOnDeath().build());

    public static final Supplier<AttachmentType<com.cnethuka.kegare.progression.RefinementData>> REFINEMENT = ATTACHMENT_TYPES.register("refinement",
            () -> AttachmentType.builder(() -> new com.cnethuka.kegare.progression.RefinementData())
                    .serialize(com.cnethuka.kegare.progression.RefinementData.CODEC).copyOnDeath().build());

    public static final Supplier<AttachmentType<com.cnethuka.kegare.progression.InsightData>> INSIGHT = ATTACHMENT_TYPES.register("insight",
            () -> AttachmentType.builder(() -> new com.cnethuka.kegare.progression.InsightData())
                    .serialize(com.cnethuka.kegare.progression.InsightData.CODEC).copyOnDeath().build());

    public static final Supplier<AttachmentType<com.cnethuka.kegare.progression.UnlockData>> UNLOCKS = ATTACHMENT_TYPES.register("unlocks",
            () -> AttachmentType.builder(() -> new com.cnethuka.kegare.progression.UnlockData())
                    .serialize(com.cnethuka.kegare.progression.UnlockData.CODEC).copyOnDeath().build());

    public static final Supplier<AttachmentType<com.cnethuka.kegare.progression.Landings.State>> LANDING_STATE = ATTACHMENT_TYPES.register("landing_state",
            () -> AttachmentType.builder(() -> new com.cnethuka.kegare.progression.Landings.State()).build());

    /** On any entity a cast spawned: which cast (see {@link com.cnethuka.kegare.progression.CastScope}). */
    public static final Supplier<AttachmentType<com.cnethuka.kegare.progression.CastOrigin>> CAST_ORIGIN = ATTACHMENT_TYPES.register("cast_origin",
            () -> AttachmentType.builder(() -> com.cnethuka.kegare.progression.CastOrigin.NONE)
                    .serialize(com.cnethuka.kegare.progression.CastOrigin.CODEC).build());

    private KegareAttachments() {}
}

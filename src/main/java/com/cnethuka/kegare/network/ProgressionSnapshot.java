package com.cnethuka.kegare.network;

import com.cnethuka.kegare.innatetechnique.Ability;
import com.cnethuka.kegare.progression.AssessmentRules;
import com.cnethuka.kegare.progression.AssessmentService;
import com.cnethuka.kegare.progression.Grade;
import com.cnethuka.kegare.progression.GradeService;
import com.cnethuka.kegare.progression.Insight;
import com.cnethuka.kegare.progression.InsightCategory;
import com.cnethuka.kegare.progression.RefinementData;
import com.cnethuka.kegare.progression.RefinementService;
import com.cnethuka.kegare.progression.TechniqueUnlocks;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * A player's progression, part of {@link SyncPayload}, which only ever goes to its owner: Grade, Assessment and Refinement
 * are private. The client only displays these.
 *
 * @param grade the Grade's ordinal
 * @param gradeXpNeeded what the next step costs in XP; 0 when it takes none, or there is no next step
 * @param assessment where they stand against the next step's Assessment, if it takes one
 * @param refinementXpNeeded to the next level; 0 at the top
 * @param locked what of their technique is still locked, with the level each one needs
 * @param brain the brain meter (DESIGN §3.17), rounded, 0 to 100
 */
public record ProgressionSnapshot(int grade, float gradeXp, float gradeXpNeeded, Optional<Assessment> assessment,
                                  int refinement, float refinementXp, float refinementXpNeeded, int peakRefinement, int maxRefinement,
                                  int dominion, int ward, int output, Map<ResourceLocation, Integer> locked, int brain) {
    public record Assessment(float average, float averageNeeded, float technique, float techniqueNeeded, int fights, int fightsNeeded,
                             int opponents, int opponentsNeeded, int peak, int peakNeeded, boolean severed) {}

    public static ProgressionSnapshot of(ServerPlayer player) {
        Grade grade = GradeService.grade(player);
        AssessmentRules.Status status = AssessmentService.status(player);
        Optional<Assessment> assessment = Optional.ofNullable(status).map(s -> new Assessment((float) s.average(), (float) s.requirement().average(),
                (float) s.technique(), (float) s.requirement().technique(), s.fights(), s.requirement().fights(), s.opponents(), s.requirement().opponents(), s.peakRefinement(), s.requirement().peakRefinement(), s.severed()));
        RefinementData refinement = RefinementService.get(player);
        int max = RefinementService.maxLevel();
        Map<ResourceLocation, Integer> locked = new HashMap<>();
        for (Map.Entry<Ability, Integer> entry : TechniqueUnlocks.locked(player).entrySet()) locked.put(entry.getKey().id(), entry.getValue());
        return new ProgressionSnapshot(grade.ordinal(), (float) GradeService.get(player).xp(), grade.next().map(GradeService::xpCost).orElse(0.0).floatValue(), assessment,
                refinement.level(), (float) refinement.xp(), refinement.level() >= max ? 0f : (float) RefinementService.xpForLevel(refinement.level() + 1),
                refinement.peak(), max,
                Insight.get(player, InsightCategory.DOMINION), Insight.get(player, InsightCategory.WARD), Insight.get(player, InsightCategory.OUTPUT), locked,
                (int) Math.round(com.cnethuka.kegare.rmt.BrainDamage.get(player)));
    }

    /** The Refinement level this needs, or -1 if it isn't locked. */
    public int lockedLevel(ResourceLocation ability) {
        return locked.getOrDefault(ability, -1);
    }

    void write(FriendlyByteBuf buf) {
        buf.writeVarInt(grade);
        buf.writeFloat(gradeXp);
        buf.writeFloat(gradeXpNeeded);
        buf.writeOptional(assessment, (b, a) -> {
            b.writeFloat(a.average());
            b.writeFloat(a.averageNeeded());
            b.writeFloat(a.technique());
            b.writeFloat(a.techniqueNeeded());
            b.writeVarInt(a.fights());
            b.writeVarInt(a.fightsNeeded());
            b.writeVarInt(a.opponents());
            b.writeVarInt(a.opponentsNeeded());
            b.writeVarInt(a.peak());
            b.writeVarInt(a.peakNeeded());
            b.writeBoolean(a.severed());
        });
        buf.writeVarInt(refinement);
        buf.writeFloat(refinementXp);
        buf.writeFloat(refinementXpNeeded);
        buf.writeVarInt(peakRefinement);
        buf.writeVarInt(maxRefinement);
        buf.writeVarInt(dominion);
        buf.writeVarInt(ward);
        buf.writeVarInt(output);
        buf.writeMap(locked, FriendlyByteBuf::writeResourceLocation, FriendlyByteBuf::writeVarInt);
        buf.writeVarInt(brain);
    }

    static ProgressionSnapshot read(FriendlyByteBuf buf) {
        return new ProgressionSnapshot(buf.readVarInt(), buf.readFloat(), buf.readFloat(),
                buf.readOptional(b -> new Assessment(b.readFloat(), b.readFloat(), b.readFloat(), b.readFloat(), b.readVarInt(), b.readVarInt(),
                        b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readBoolean())),
                buf.readVarInt(), buf.readFloat(), buf.readFloat(), buf.readVarInt(), buf.readVarInt(),
                buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                buf.readMap(FriendlyByteBuf::readResourceLocation, FriendlyByteBuf::readVarInt), buf.readVarInt());
    }
}

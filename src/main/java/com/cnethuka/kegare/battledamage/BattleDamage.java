package com.cnethuka.kegare.battledamage;

import com.cnethuka.kegare.battledamage.BattleDamageConfig.Mode;
import com.cnethuka.kegare.impact.Impact;
import com.cnethuka.kegare.impact.ImpactStrength;
import com.cnethuka.kegare.impact.ImpactTier;
import com.cnethuka.kegare.impact.Impacts;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.Clearable;
import net.minecraft.world.Container;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.LecternBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Battle damage (DESIGN §3.24): big hits break the world round where they land. Strength comes from the Impact System (§3.23), so a
 * hit breaks what its strength says and light hits never break anything. One rule decides each block: the hit's power, falling off
 * with distance, against the block's blast resistance ({@link #breaks}). Whatever breaks shatters into rubble: nothing drops, not the
 * block and not what was knocked off it, so a technique is never a pickaxe. A container spills its contents (in PERMANENT mode, if
 * {@code spillContents}); in RESTORING mode it comes back full instead. Each block fires NeoForge's block-break event first, so spawn
 * protection and claim mods can say no. COSMETIC mode breaks nothing: the clients only show the cracks and the debris.
 */
public final class BattleDamage {
    /** Blocks from this far away hear (and see) the damage. */
    private static final double SEEN_FROM = 96;
    /** At most this many break sounds per hit: the rest are the same sound again. */
    private static final int SOUNDS_PER_HIT = 6;

    /** While set, item drops landing in it are swallowed: a torch or a door's other half knocked off never drops either. */
    private static @Nullable AABB swallowing;
    /** Per dimension: {game time, blocks broken} for the last second, for {@code maxBlocksPerSecond}. */
    private static final Map<ResourceKey<Level>, ArrayDeque<long[]>> RECENT = new HashMap<>();
    private static List<? extends String> protectedFrom = List.of();
    private static Set<ResourceLocation> protectedBlocks = Set.of();
    private static List<TagKey<Block>> protectedTags = List.of();

    public record Result(List<BlockPos> broken, List<BlockPos> wouldBreak) {}

    private BattleDamage() {}

    public static void init() {
        NeoForge.EVENT_BUS.addListener(BattleDamage::onEntityJoin);
        Impacts.listen(BattleDamage::onImpact);
        Impacts.listenBlasts(BattleDamage::onBlast);
        BattleScars.init();
        WallSlams.init();
    }

    private static void onImpact(Impact impact) {
        if (!impact.breaksBlocks() || !allowed(impact.level(), impact.attacker())) return;
        WallSlams.watch(impact);
        if (impact.tier().ordinal() < BattleDamageConfig.MIN_TIER.get().ordinal()) return;
        hit(impact.level(), impact.contact(), impact.strength(), impact.tier(), impact.attacker());
    }

    /** An area blast breaks the blocks round its own centre, hit or miss, as hard as it would hit a body there. */
    private static void onBlast(Impacts.Blast blast) {
        if (!blast.breaksBlocks() || !allowed(blast.level(), blast.attacker())) return;
        if (blast.tier().ordinal() < BattleDamageConfig.MIN_TIER.get().ordinal()) return;
        hit(blast.level(), blast.centre(), blast.strength(), blast.tier(), blast.attacker());
    }

    /** A player's hit (or their technique's) always; a mob's or nobody's only where mobs may grief. */
    static boolean allowed(ServerLevel level, @Nullable Entity attacker) {
        if (attacker instanceof Player) return true;
        return !BattleDamageConfig.MOBS_NEED_GRIEFING.get() || level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING);
    }

    /** A slam into a wall, floor or ceiling at {@code speed} (blocks/tick), meeting it at {@code at}: a crater, no damage. */
    static void slam(ServerLevel level, Vec3 at, double speed, @Nullable Entity attacker) {
        double strength = BattleDamageConfig.SLAM_STRENGTH_PER_SPEED.get() * speed;
        ImpactTier tier = ImpactStrength.tier(strength);
        if (tier.ordinal() < BattleDamageConfig.MIN_TIER.get().ordinal()) return;
        hit(level, at, strength, tier, attacker);
    }

    // ---------------------------------------------------------------- the rule

    /** How hard a hit of this strength hits the world at its centre. */
    public static double power(double strength, ImpactTier tier) {
        return tier == ImpactTier.TOP ? BattleDamageConfig.TOP_POWER.get() : BattleDamageConfig.STRENGTH_SCALE.get() * strength;
    }

    /** How far from where it landed it still breaks anything. */
    public static double radius(double strength, ImpactTier tier) {
        if (tier == ImpactTier.TOP) return BattleDamageConfig.MAX_RADIUS.get();
        return Math.min(BattleDamageConfig.MAX_RADIUS.get(), BattleDamageConfig.BASE_RADIUS.get() + BattleDamageConfig.RADIUS_PER_STRENGTH.get() * strength);
    }

    /** Power left {@code distance} from the centre: all of it there, none at the radius. */
    public static double powerAt(double power, double radius, double distance) {
        return distance >= radius ? 0 : power * (1 - distance / radius);
    }

    /**
     * Whether a block breaks to this much power: more than its blast resistance, and never a block vanilla makes unbreakable
     * (negative hardness: bedrock, barriers, End portal frames, command blocks), a fluid, or anything on the protected list.
     */
    public static boolean breaks(BlockState state, BlockGetter level, BlockPos pos, double power) {
        if (state.isAir() || state.getBlock() instanceof LiquidBlock) return false;
        if (state.getDestroySpeed(level, pos) < 0) return false;
        if (isProtected(state)) return false;
        return power > state.getBlock().getExplosionResistance();
    }

    public static boolean isProtected(BlockState state) {
        List<? extends String> list = BattleDamageConfig.PROTECTED.get();
        if (list != protectedFrom) { // parsed again whenever the config's list changes
            Set<ResourceLocation> blocks = new HashSet<>();
            List<TagKey<Block>> tags = new ArrayList<>();
            for (String entry : list) {
                String s = entry.trim();
                ResourceLocation id = ResourceLocation.tryParse(s.startsWith("#") ? s.substring(1) : s);
                if (id == null) continue;
                if (s.startsWith("#")) tags.add(TagKey.create(Registries.BLOCK, id));
                else blocks.add(id);
            }
            protectedBlocks = blocks;
            protectedTags = tags;
            protectedFrom = list;
        }
        if (protectedBlocks.contains(state.getBlockHolder().unwrapKey().map(ResourceKey::location).orElse(null))) return true;
        for (TagKey<Block> tag : protectedTags) if (state.is(tag)) return true;
        return false;
    }

    // ---------------------------------------------------------------- breaking

    private record Candidate(BlockPos pos, BlockState state, double distance) {}

    /** A hit of this strength lands at {@code contact}: breaks what the rule says (as the mode allows), and shows it to the clients. */
    public static Result hit(ServerLevel level, Vec3 contact, double strength, ImpactTier tier, @Nullable Entity attacker) {
        Mode mode = BattleDamageConfig.MODE.get();
        double radius = radius(strength, tier), power = power(strength, tier);
        List<Candidate> candidates = new ArrayList<>();
        int r = Mth.ceil(radius);
        BlockPos centre = BlockPos.containing(contact);
        for (BlockPos pos : BlockPos.betweenClosed(centre.offset(-r, -r, -r), centre.offset(r, r, r))) {
            if (!level.isLoaded(pos)) continue;
            double d = distance(contact, pos);
            if (d >= radius) continue;
            BlockState state = level.getBlockState(pos);
            if (breaks(state, level, pos, powerAt(power, radius, d))) candidates.add(new Candidate(pos.immutable(), state, d));
        }
        candidates.sort(Comparator.comparingDouble(Candidate::distance));
        int cap = Math.min(BattleDamageConfig.MAX_PER_HIT.get(), budget(level));
        List<BlockPos> broken = new ArrayList<>(), wouldBreak = new ArrayList<>();
        List<BlockState> states = new ArrayList<>();
        if (mode == Mode.COSMETIC) {
            for (Candidate c : candidates) {
                if (wouldBreak.size() >= cap) break;
                wouldBreak.add(c.pos);
                states.add(c.state);
            }
        } else {
            Player breaker = attacker instanceof ServerPlayer player ? player : FakePlayerFactory.getMinecraft(level);
            Map<BlockPos, BlockState> before = mode == Mode.RESTORING ? snapshot(level, contact, radius + 1.5) : Map.of();
            List<BattleScars.Piece> pieces = new ArrayList<>();
            for (Candidate c : candidates) {
                if (broken.size() >= cap) break;
                BattleScars.Piece piece = shatter(level, c.pos, c.state, breaker, mode);
                if (piece == null) continue;
                broken.add(c.pos);
                states.add(c.state);
                pieces.add(piece);
            }
            spend(level, broken.size());
            if (mode == Mode.RESTORING && !broken.isEmpty()) {
                // What came down with them (a torch on the wall, a door's other half) comes back too.
                Set<BlockPos> done = new HashSet<>(broken);
                before.forEach((pos, was) -> {
                    if (!done.contains(pos) && !was.isAir() && level.getBlockState(pos).isAir()) pieces.add(new BattleScars.Piece(pos, was, null));
                });
                BattleScars.get(level).add(level, contact, pieces);
            }
            for (int i = 0; i < Math.min(SOUNDS_PER_HIT, broken.size()); i++) {
                BlockPos pos = broken.get(i);
                BlockState state = states.get(i);
                level.playSound(null, pos, state.getSoundType().getBreakSound(), SoundSource.BLOCKS, 1.0f, 0.75f + 0.1f * i);
            }
        }
        BattleScars.fightNear(level, contact);
        send(level, contact, radius, power, mode == Mode.COSMETIC, mode == Mode.COSMETIC ? wouldBreak : broken, states);
        return new Result(broken, wouldBreak);
    }

    /**
     * Breaks one block, if nothing forbids it: spawn protection and the world border, then the block-break event. Returns what it
     * was (for RESTORING), or null if it stays.
     */
    private static @Nullable BattleScars.Piece shatter(ServerLevel level, BlockPos pos, BlockState state, Player breaker, Mode mode) {
        if (!level.mayInteract(breaker, pos)) return null;
        // Adventure mode is vanilla's own "you don't break blocks here".
        if (breaker instanceof ServerPlayer player && player.blockActionRestricted(level, pos, player.gameMode.getGameModeForPlayer())) return null;
        BlockEvent.BreakEvent event = new BlockEvent.BreakEvent(level, pos, state, breaker);
        NeoForge.EVENT_BUS.post(event);
        if (event.isCanceled()) return null;
        CompoundTag saved = null;
        BlockEntity entity = level.getBlockEntity(pos);
        if (entity != null && mode == Mode.RESTORING) saved = entity.saveWithFullMetadata(level.registryAccess());
        boolean spill = mode == Mode.PERMANENT && BattleDamageConfig.SPILL_CONTENTS.get();
        if (entity instanceof Container container) {
            if (spill) Containers.dropContents(level, pos, container);
        } else if (entity instanceof LecternBlockEntity lectern && lectern.hasBook() && spill) {
            Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), lectern.getBook().copy());
        }
        // Emptied first: removing it must not drop what it held (vanilla's chests would), and the drops below are swallowed.
        if (entity instanceof Clearable clearable) clearable.clearContent();
        swallowing = new AABB(pos).inflate(2.0);
        try {
            level.removeBlock(pos, false);
        } finally {
            swallowing = null;
        }
        return new BattleScars.Piece(pos, state, saved);
    }

    private static void onEntityJoin(EntityJoinLevelEvent event) {
        AABB area = swallowing;
        if (area != null && event.getEntity() instanceof ItemEntity item && area.contains(item.position())) event.setCanceled(true);
    }

    private static Map<BlockPos, BlockState> snapshot(ServerLevel level, Vec3 contact, double radius) {
        Map<BlockPos, BlockState> out = new LinkedHashMap<>();
        int r = Mth.ceil(radius);
        BlockPos centre = BlockPos.containing(contact);
        for (BlockPos pos : BlockPos.betweenClosed(centre.offset(-r, -r, -r), centre.offset(r, r, r))) {
            if (level.isLoaded(pos)) out.put(pos.immutable(), level.getBlockState(pos));
        }
        return out;
    }

    /** From a point to the nearest point of a block. */
    static double distance(Vec3 at, BlockPos pos) {
        double x = Mth.clamp(at.x, pos.getX(), pos.getX() + 1), y = Mth.clamp(at.y, pos.getY(), pos.getY() + 1), z = Mth.clamp(at.z, pos.getZ(), pos.getZ() + 1);
        return at.distanceTo(new Vec3(x, y, z));
    }

    // ---------------------------------------------------------------- the cap

    /** How many more blocks may break in this dimension this second. */
    static int budget(ServerLevel level) {
        long now = level.getGameTime();
        ArrayDeque<long[]> recent = RECENT.computeIfAbsent(level.dimension(), k -> new ArrayDeque<>());
        while (!recent.isEmpty() && now - recent.peekFirst()[0] >= 20) recent.pollFirst();
        long used = 0;
        for (long[] e : recent) used += e[1];
        return (int) Math.max(0, BattleDamageConfig.MAX_PER_SECOND.get() - used);
    }

    private static void spend(ServerLevel level, int blocks) {
        if (blocks > 0) RECENT.computeIfAbsent(level.dimension(), k -> new ArrayDeque<>()).addLast(new long[] {level.getGameTime(), blocks});
    }

    static void forgetRecent() {
        RECENT.clear();
    }

    // ---------------------------------------------------------------- the clients

    private static void send(ServerLevel level, Vec3 contact, double radius, double power, boolean cosmetic, List<BlockPos> blocks,
                             List<BlockState> states) {
        int[] ids = new int[states.size()];
        for (int i = 0; i < ids.length; i++) ids[i] = Block.getId(states.get(i));
        BattleDamagePayload payload = new BattleDamagePayload(contact, (float) radius, (float) power, cosmetic, List.copyOf(blocks), ids);
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(contact) <= SEEN_FROM * SEEN_FROM) PacketDistributor.sendToPlayer(player, payload);
        }
    }
}

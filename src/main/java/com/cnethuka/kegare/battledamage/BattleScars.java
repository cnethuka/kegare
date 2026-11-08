package com.cnethuka.kegare.battledamage;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * RESTORING mode's promise (DESIGN §3.24): every block battle damage broke comes back, piece by piece, once the fight has moved on.
 * Saved with the dimension ({@code data/kegare_battle_scars.dat}), so a restart doesn't lose a single block. A scar is one hit's
 * blocks; it waits until no battle damage has happened within {@code quietRadius} of it for {@code delay}, then rebuilds one block
 * every {@code interval} ticks: solid blocks first, lowest first, so what hangs on them has something to hang on. A block comes back
 * only where its space is empty again (never over something a player put there since: a container that can't come back drops what
 * was in it, so nothing is lost), with its block entity's contents. A block with someone standing in it or right beside it waits for
 * them to move, and the rest of the scar comes back round them. Scars made in RESTORING mode rebuild even if the mode is changed
 * afterwards: they were promised.
 */
public final class BattleScars extends SavedData {
    private static final String NAME = "kegare_battle_scars";

    /** One block to bring back: what it was, and its block entity's data (a container's contents) if it had one. */
    public record Piece(BlockPos pos, BlockState state, @Nullable CompoundTag blockEntity) {}

    static final class Scar {
        final Vec3 centre;
        final List<Piece> pieces;
        long quietSince;
        long nextAt;

        Scar(Vec3 centre, List<Piece> pieces, long quietSince) {
            this.centre = centre;
            this.pieces = pieces;
            this.quietSince = quietSince;
        }
    }

    private final List<Scar> scars = new ArrayList<>();

    static void init() {
        NeoForge.EVENT_BUS.addListener((LevelTickEvent.Post e) -> {
            if (e.getLevel() instanceof ServerLevel level) get(level).tick(level);
        });
    }

    public static BattleScars get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(new SavedData.Factory<>(BattleScars::new, BattleScars::load, null), NAME);
    }

    /** How many blocks are still waiting to come back. */
    public int pending() {
        int n = 0;
        for (Scar s : scars) n += s.pieces.size();
        return n;
    }

    void add(ServerLevel level, Vec3 centre, List<Piece> pieces) {
        List<Piece> sorted = new ArrayList<>(pieces);
        sorted.sort(Comparator.comparing((Piece p) -> p.state.isCollisionShapeFullBlock(level, p.pos) ? 0 : 1)
                .thenComparingInt(p -> p.pos.getY())
                .thenComparingDouble(p -> Vec3.atCenterOf(p.pos).distanceToSqr(centre)));
        scars.add(new Scar(centre, sorted, level.getGameTime()));
        setDirty();
    }

    /** Battle damage at {@code at}: the fight is still going there, so scars near it wait again. */
    static void fightNear(ServerLevel level, Vec3 at) {
        BattleScars data = get(level);
        double r = BattleDamageConfig.RESTORE_QUIET_RADIUS.get();
        for (Scar s : data.scars) {
            if (s.centre.distanceToSqr(at) <= r * r) {
                s.quietSince = level.getGameTime();
                data.setDirty();
            }
        }
    }

    /** Drops every pending rebuild (tests). */
    void forget() {
        scars.clear();
        setDirty();
    }

    /** Every scar starts rebuilding now (the debug command, and filming). */
    public void restoreNow(ServerLevel level) {
        long delay = Math.round(BattleDamageConfig.RESTORE_DELAY.get() * 20);
        for (Scar s : scars) s.quietSince = level.getGameTime() - delay;
        setDirty();
    }

    void tick(ServerLevel level) {
        if (scars.isEmpty()) return;
        long now = level.getGameTime();
        long delay = Math.round(BattleDamageConfig.RESTORE_DELAY.get() * 20);
        int interval = BattleDamageConfig.RESTORE_INTERVAL.get();
        for (Iterator<Scar> it = scars.iterator(); it.hasNext(); ) {
            Scar s = it.next();
            if (now - s.quietSince < delay || now < s.nextAt) continue;
            if (s.pieces.isEmpty()) {
                it.remove();
                setDirty();
                continue;
            }
            // The first in order that can come back now: its chunk is there and nobody is standing in it or right beside it. One person
            // in the crater must not hold the whole scar up, and the blocks round them wait so they're never walled into a pit.
            int next = -1;
            for (int k = 0; k < s.pieces.size() && next < 0; k++) {
                Piece p = s.pieces.get(k);
                if (level.isLoaded(p.pos) && !occupied(level, p)) next = k;
            }
            if (next < 0) { // all waiting: try again a little later
                s.nextAt = now + 20;
                continue;
            }
            Piece piece = s.pieces.remove(next);
            restore(level, piece);
            s.nextAt = now + interval;
            setDirty();
            if (s.pieces.isEmpty()) it.remove();
        }
    }

    /** Something alive stands in its space or right beside it (within a block across): it waits for them to move. */
    static boolean occupied(ServerLevel level, Piece piece) {
        if (piece.state.getCollisionShape(level, piece.pos).isEmpty()) return false;
        return !level.getEntitiesOfClass(LivingEntity.class, new AABB(piece.pos).inflate(1.0, 0.0, 1.0), LivingEntity::isAlive).isEmpty();
    }

    static void restore(ServerLevel level, Piece piece) {
        BlockState now = level.getBlockState(piece.pos);
        if (!now.isAir() && !now.canBeReplaced()) { // something else is there now: never over it
            dropContents(level, piece);
            return;
        }
        // No shape updates while it's put back: what hangs on a block that isn't back yet must not pop off and drop.
        level.setBlock(piece.pos, piece.state, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        if (piece.blockEntity != null) {
            BlockEntity entity = level.getBlockEntity(piece.pos);
            if (entity != null) {
                entity.loadWithComponents(piece.blockEntity, level.registryAccess());
                entity.setChanged();
                level.sendBlockUpdated(piece.pos, piece.state, piece.state, Block.UPDATE_CLIENTS);
            }
        }
        level.updateNeighborsAt(piece.pos, piece.state.getBlock());
        level.playSound(null, piece.pos, piece.state.getSoundType().getPlaceSound(), SoundSource.BLOCKS, 0.5f, 1.2f);
    }

    /** A container that can't come back drops what it held where it stood, so nothing is ever lost. */
    private static void dropContents(ServerLevel level, Piece piece) {
        if (piece.blockEntity == null || !piece.blockEntity.contains("Items", Tag.TAG_LIST)) return;
        NonNullList<ItemStack> items = NonNullList.withSize(256, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(piece.blockEntity, items, level.registryAccess());
        Containers.dropContents(level, piece.pos, items);
    }

    // ---------------------------------------------------------------- saving

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Scar s : scars) {
            CompoundTag st = new CompoundTag();
            st.putDouble("x", s.centre.x);
            st.putDouble("y", s.centre.y);
            st.putDouble("z", s.centre.z);
            st.putLong("quietSince", s.quietSince);
            ListTag pieces = new ListTag();
            for (Piece p : s.pieces) {
                CompoundTag pt = new CompoundTag();
                pt.put("pos", NbtUtils.writeBlockPos(p.pos));
                pt.put("state", NbtUtils.writeBlockState(p.state));
                if (p.blockEntity != null) pt.put("blockEntity", p.blockEntity);
                pieces.add(pt);
            }
            st.put("pieces", pieces);
            list.add(st);
        }
        tag.put("scars", list);
        return tag;
    }

    static BattleScars load(CompoundTag tag, HolderLookup.Provider registries) {
        BattleScars data = new BattleScars();
        HolderGetter<Block> blocks = registries.lookupOrThrow(Registries.BLOCK);
        for (Tag t : tag.getList("scars", Tag.TAG_COMPOUND)) {
            CompoundTag st = (CompoundTag) t;
            List<Piece> pieces = new ArrayList<>();
            for (Tag p : st.getList("pieces", Tag.TAG_COMPOUND)) {
                CompoundTag pt = (CompoundTag) p;
                BlockPos pos = NbtUtils.readBlockPos(pt, "pos").orElse(null);
                if (pos == null) continue;
                pieces.add(new Piece(pos, NbtUtils.readBlockState(blocks, pt.getCompound("state")),
                        pt.contains("blockEntity", Tag.TAG_COMPOUND) ? pt.getCompound("blockEntity") : null));
            }
            Scar scar = new Scar(new Vec3(st.getDouble("x"), st.getDouble("y"), st.getDouble("z")), pieces, st.getLong("quietSince"));
            if (!pieces.isEmpty()) data.scars.add(scar);
        }
        return data;
    }
}

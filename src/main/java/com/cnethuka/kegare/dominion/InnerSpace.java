package com.cnethuka.kegare.dominion;

import com.cnethuka.kegare.api.dominion.DominionInterior;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.Vec3;

/**
 * One Dominion's inner space (DESIGN §3.35): its plot in the inner space dimension, built of real blocks while its barrier spreads, and
 * what it remembers to let everyone out again. Server only; never saved (a plot is cleared the next time it's used).
 */
public final class InnerSpace implements DominionInterior {
    /** Blocks set at most a tick while it's being built (whatever's left is finished as it seals). */
    static final int BUILD_BUDGET = 3000;
    /** How far past its radius the floor reaches, so nobody sees its edge. */
    static final int FLOOR_MARGIN = 3;

    final int slot;
    final ServerLevel level;
    final BlockPos centre;
    int radius;
    /** Its view in the inner space: the barrier's other side (drawn as its sky). */
    @Nullable DominionEntity inner;
    /** Where each mob taken in came from. */
    final Map<UUID, Vec3> mobOrigins = new HashMap<>();
    /** Its shell in the world: where, and how big (to draw things back in to it as they're let out). */
    ServerLevel outerLevel;
    Vec3 outerCentre;
    double outerRadius;
    final RandomSource random;
    @Nullable private Build build;
    /** It's ending: everyone is being let out. */
    boolean collapsing;
    /** Its chunk tickets: how far round the plot, and the shell's chunk in the world. */
    int heldReach;
    @Nullable ChunkPos heldOuter;

    InnerSpace(int slot, ServerLevel level, BlockPos centre, int radius, ServerLevel outerLevel, Vec3 outerCentre, double outerRadius) {
        this.slot = slot;
        this.level = level;
        this.centre = centre;
        this.radius = radius;
        this.outerLevel = outerLevel;
        this.outerCentre = outerCentre;
        this.outerRadius = outerRadius;
        this.random = RandomSource.create();
    }

    // ---------------------------------------------------------------------------------------------- DominionInterior

    @Override public ServerLevel level() { return level; }
    @Override public BlockPos centre() { return centre; }
    @Override public int radius() { return radius; }
    @Override public RandomSource random() { return random; }

    @Override
    public void place(BlockPos pos, BlockState state) {
        if (within(pos)) level.setBlock(pos, state, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS);
    }

    /** Within its dome (and its floor). */
    boolean within(BlockPos pos) {
        double dx = pos.getX() + 0.5 - (centre.getX() + 0.5), dz = pos.getZ() + 0.5 - (centre.getZ() + 0.5);
        return dx * dx + dz * dz <= (radius + FLOOR_MARGIN) * (radius + FLOOR_MARGIN)
                && pos.getY() >= centre.getY() - 3 && pos.getY() <= centre.getY() + radius + 2;
    }

    /** Where you stand at its middle. */
    public Vec3 middle() {
        return new Vec3(centre.getX() + 0.5, centre.getY(), centre.getZ() + 0.5);
    }

    /** How much bigger it is than its shell in the world, across. */
    double spread() {
        return outerRadius <= 0 ? 1 : radius / outerRadius;
    }

    // ---------------------------------------------------------------------------------------------- building

    /** Starts building it for this Dominion (from scratch: whatever the plot held is cleared first). */
    void build(DominionSpec spec) {
        this.radius = spec.innerRadius();
        this.build = new Build(spec);
    }

    /** A share of the building, each tick while the barrier spreads. */
    void buildSome() {
        if (build != null && build.step(BUILD_BUDGET)) build = null;
    }

    /** All of what's left, now: it's sealing. */
    void finishBuilding() {
        while (build != null && !build.step(Integer.MAX_VALUE)) {
            // keep going
        }
        build = null;
    }

    boolean built() {
        return build == null;
    }

    /**
     * The build, in steps: clear the plot (only sections that hold anything are looked through, so a fresh plot costs nearly nothing),
     * lay the floor (its ground over two layers of foundation), stand its ruins, then let its technique furnish it.
     */
    private final class Build {
        final DominionSpec spec;
        final BlockState ground;
        int stage;
        final List<BlockPos> clear = new ArrayList<>();
        int at;
        final List<BlockPos> floor = new ArrayList<>();
        final List<BlockPos> ruins = new ArrayList<>();

        Build(DominionSpec spec) {
            this.spec = spec;
            this.ground = spec.look().ground().flatMap(id -> BuiltInRegistries.BLOCK.getOptional(id)).map(Block::defaultBlockState)
                    .orElse(DominionRegistry.WASTELAND_GROUND.get().defaultBlockState());
        }

        /** Does up to {@code budget} blocks' worth. Returns whether it's done. */
        boolean step(int budget) {
            int used = 0;
            while (used < budget) {
                switch (stage) {
                    case 0 -> { // what the plot holds now
                        findLeftovers();
                        stage = 1;
                        at = 0;
                    }
                    case 1 -> {
                        if (at >= clear.size()) {
                            stage = 2;
                            at = 0;
                            layOut();
                            break;
                        }
                        set(clear.get(at++), Blocks.AIR.defaultBlockState());
                        used++;
                    }
                    case 2 -> {
                        if (at >= floor.size()) {
                            stage = 3;
                            at = 0;
                            break;
                        }
                        BlockPos pos = floor.get(at++);
                        set(pos, pos.getY() == centre.getY() - 1 ? ground : DominionRegistry.DOMINION_FOUNDATION.get().defaultBlockState());
                        used++;
                    }
                    case 3 -> {
                        if (at >= ruins.size()) {
                            stage = 4;
                            break;
                        }
                        set(ruins.get(at++), DominionRegistry.WASTELAND_RUIN.get().defaultBlockState());
                        used++;
                    }
                    case 4 -> {
                        spec.hooks().furnish(InnerSpace.this);
                        return true;
                    }
                    default -> {
                        return true;
                    }
                }
            }
            return false;
        }

        /** Every block in its reach that isn't air: only in the sections that hold any. */
        private void findLeftovers() {
            int r = radius + FLOOR_MARGIN + 1, top = centre.getY() + radius + 3, bottom = centre.getY() - 4;
            int minCx = (centre.getX() - r) >> 4, maxCx = (centre.getX() + r) >> 4, minCz = (centre.getZ() - r) >> 4, maxCz = (centre.getZ() + r) >> 4;
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            for (int cx = minCx; cx <= maxCx; cx++) {
                for (int cz = minCz; cz <= maxCz; cz++) {
                    LevelChunk chunk = level.getChunk(cx, cz);
                    for (int sy = level.getSectionIndex(Math.max(bottom, level.getMinBuildHeight())); sy <= level.getSectionIndex(Math.min(top, level.getMaxBuildHeight() - 1)); sy++) {
                        LevelChunkSection section = chunk.getSection(sy);
                        if (section.hasOnlyAir()) continue;
                        int baseY = level.getSectionYFromSectionIndex(sy) << 4;
                        for (int y = 0; y < 16; y++) {
                            int wy = baseY + y;
                            if (wy < bottom || wy > top) continue;
                            for (int x = 0; x < 16; x++) {
                                for (int z = 0; z < 16; z++) {
                                    if (section.getBlockState(x, y, z).isAir()) continue;
                                    pos.set((cx << 4) + x, wy, (cz << 4) + z);
                                    double dx = pos.getX() - centre.getX(), dz = pos.getZ() - centre.getZ();
                                    if (dx * dx + dz * dz <= r * r) clear.add(pos.immutable());
                                }
                            }
                        }
                    }
                }
            }
        }

        /** Its floor and its ruins. */
        private void layOut() {
            int r = radius + FLOOR_MARGIN;
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (dx * dx + dz * dz > r * r) continue;
                    for (int dy = 1; dy <= 3; dy++) floor.add(centre.offset(dx, -dy, dz));
                }
            }
            if (!spec.look().ruins()) return;
            int count = Math.max(4, Math.round((float) (Math.PI * radius * radius / 120.0)));
            for (int i = 0; i < count; i++) {
                double angle = Mth.TWO_PI * (i + random.nextFloat() * 0.7f) / count;
                double dist = radius * (0.25 + random.nextDouble() * 0.6);
                int x = centre.getX() + Mth.floor(Math.cos(angle) * dist), z = centre.getZ() + Mth.floor(Math.sin(angle) * dist);
                boolean alongX = random.nextBoolean();
                if (random.nextFloat() < 0.45f) { // a column, broken off
                    int h = 2 + random.nextInt(3);
                    for (int y = 0; y < h; y++) ruins.add(new BlockPos(x, centre.getY() + y, z));
                } else { // a wall stub, its top uneven
                    int length = 2 + random.nextInt(3);
                    for (int k = 0; k < length; k++) {
                        int h = 1 + random.nextInt(3);
                        for (int y = 0; y < h; y++) ruins.add(new BlockPos(x + (alongX ? k : 0), centre.getY() + y, z + (alongX ? 0 : k)));
                    }
                }
            }
        }

        private void set(BlockPos pos, BlockState state) {
            level.setBlock(pos, state, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS);
        }
    }
}

package com.cnethuka.kegare.dominion;

import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.levelgen.synth.SimplexNoise;

/**
 * Kegare's wasteland (DESIGN §3.35), the inner space of a Dominion with nothing of a technique in it, built of vanilla blocks only: its
 * ground a patchwork of tuff, andesite, stone, cobblestone and coarse dirt; ruins of stone bricks, cracked and broken, with rubble round
 * them; dead bushes, loose stones and pebbles over the rest. Nothing in it falls (no gravel or sand), so nothing it's made of ever lands in
 * the world when everyone's let out. Each one is laid out afresh.
 */
final class Wasteland {
    private final long seed;
    private final SimplexNoise patches;

    Wasteland(RandomSource random) {
        this.seed = random.nextLong();
        this.patches = new SimplexNoise(random);
    }

    /** A number from 0 to 1 for a spot, the same every time it's asked. */
    private double roll(int x, int z, int salt) {
        long h = (x * 3129871L) ^ (z * 116129781L) ^ (salt * 0x9E3779B97F4A7C15L) ^ seed;
        h = (h ^ (h >>> 33)) * 0xff51afd7ed558ccdL;
        h = (h ^ (h >>> 33)) * 0xc4ceb9fe1a85ec53L;
        return ((h ^ (h >>> 33)) >>> 11) * 0x1.0p-53;
    }

    /** The top of the floor here: patches of dirt, of rock, and grey ground between, with an old paving stone now and then. */
    BlockState ground(int x, int z) {
        double n = patches.getValue(x / 9.0, z / 9.0) + 0.35 * patches.getValue(x / 3.0 + 100, z / 3.0 + 100);
        double j = roll(x, z, 1);
        if (roll(x, z, 2) < 0.015) return Blocks.CRACKED_STONE_BRICKS.defaultBlockState();
        if (n < -0.45) return (j < 0.75 ? Blocks.COARSE_DIRT : Blocks.TUFF).defaultBlockState();
        if (n > 0.4) {
            if (j < 0.4) return Blocks.COBBLESTONE.defaultBlockState();
            if (j < 0.7) return Blocks.ANDESITE.defaultBlockState();
            if (j < 0.85) return Blocks.COBBLED_DEEPSLATE.defaultBlockState();
            return Blocks.STONE.defaultBlockState();
        }
        if (j < 0.45) return Blocks.TUFF.defaultBlockState();
        if (j < 0.8) return Blocks.ANDESITE.defaultBlockState();
        if (j < 0.9) return Blocks.STONE.defaultBlockState();
        return Blocks.COBBLESTONE.defaultBlockState();
    }

    /** Under the top, over the bedrock. */
    BlockState below(int x, int z) {
        double j = roll(x, z, 3);
        return (j < 0.5 ? Blocks.STONE : j < 0.8 ? Blocks.TUFF : Blocks.ANDESITE).defaultBlockState();
    }

    /** One of its ruins' bricks. */
    private BlockState brick(RandomSource r) {
        double j = r.nextDouble();
        if (j < 0.45) return Blocks.STONE_BRICKS.defaultBlockState();
        if (j < 0.8) return Blocks.CRACKED_STONE_BRICKS.defaultBlockState();
        if (j < 0.92) return Blocks.COBBLESTONE.defaultBlockState();
        if (j < 0.97) return Blocks.ANDESITE.defaultBlockState();
        return Blocks.CHISELED_STONE_BRICKS.defaultBlockState();
    }

    /** A broken top: half a brick, or a step turned any way. */
    private BlockState brokenTop(RandomSource r) {
        if (r.nextBoolean()) return Blocks.STONE_BRICK_SLAB.defaultBlockState();
        return Blocks.STONE_BRICK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.Plane.HORIZONTAL.getRandomDirection(r));
    }

    /** A bit of rubble lying on the ground. */
    private BlockState rubble(RandomSource r) {
        double j = r.nextDouble();
        if (j < 0.15) return Blocks.COBBLESTONE_SLAB.defaultBlockState();
        if (j < 0.3) return Blocks.STONE_BRICK_SLAB.defaultBlockState();
        if (j < 0.4) return Blocks.ANDESITE_SLAB.defaultBlockState();
        if (j < 0.5) return Blocks.TUFF_SLAB.defaultBlockState();
        if (j < 0.65) return Blocks.COBBLESTONE.defaultBlockState();
        if (j < 0.75) return Blocks.ANDESITE.defaultBlockState();
        if (j < 0.85) return Blocks.CRACKED_STONE_BRICKS.defaultBlockState();
        return Blocks.STONE_BRICK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.Plane.HORIZONTAL.getRandomDirection(r));
    }

    /**
     * Everything that stands on its floor ({@code floorY} is the first layer above it), within {@code reach} of its middle: its ruins (one
     * for about every 120 blocks of floor, between a quarter of the way out and its sky), the rubble round them, and dead bushes, loose
     * stones and pebbles over the rest. Bushes stand only on dirt. On a floor of its own block ({@code ownGround}) only the ruins and their
     * rubble stand.
     */
    Map<BlockPos, BlockState> standing(BlockPos middle, int radius, double reach, boolean ownGround, RandomSource r) {
        Map<BlockPos, BlockState> out = new LinkedHashMap<>();
        int floorY = middle.getY();
        int count = Math.max(4, Math.round((float) (Math.PI * radius * radius / 120.0)));
        for (int i = 0; i < count; i++) {
            double angle = Mth.TWO_PI * (i + r.nextFloat() * 0.7f) / count;
            double dist = radius * (0.25 + r.nextDouble() * 0.55);
            ruin(out, middle.getX() + Mth.floor(Math.cos(angle) * dist), floorY, middle.getZ() + Mth.floor(Math.sin(angle) * dist), r);
        }
        int span = ownGround ? -1 : Mth.ceil(reach);
        for (int dx = -span; dx <= span; dx++) {
            for (int dz = -span; dz <= span; dz++) {
                int x = middle.getX() + dx, z = middle.getZ() + dz;
                BlockPos at = new BlockPos(x, floorY, z);
                if (out.containsKey(at)) continue;
                double j = roll(x, z, 4);
                if (ground(x, z).is(Blocks.COARSE_DIRT)) {
                    if (j < 0.07) out.put(at, Blocks.DEAD_BUSH.defaultBlockState());
                } else if (j < 0.015) {
                    out.put(at, Blocks.STONE_BUTTON.defaultBlockState().setValue(ButtonBlock.FACE, AttachFace.FLOOR)
                            .setValue(ButtonBlock.FACING, Direction.Plane.HORIZONTAL.getRandomDirection(r)));
                } else if (j < 0.03) {
                    out.put(at, rubble(r));
                }
            }
        }
        out.keySet().removeIf(p -> {
            double dx = p.getX() + 0.5 - (middle.getX() + 0.5), dz = p.getZ() + 0.5 - (middle.getZ() + 0.5);
            return dx * dx + dz * dz > reach * reach;
        });
        return out;
    }

    /** One ruin at a spot: a broken column, a wall stub, a corner of two, or a column fallen over, with rubble round it. */
    private void ruin(Map<BlockPos, BlockState> out, int x, int y, int z, RandomSource r) {
        double kind = r.nextDouble();
        if (kind < 0.3) { // a column, broken off
            int h = 2 + r.nextInt(4);
            for (int k = 0; k < h; k++) out.put(new BlockPos(x, y + k, z), brick(r));
            if (r.nextBoolean()) out.put(new BlockPos(x, y + h, z), brokenTop(r));
        } else if (kind < 0.65) { // a wall stub, its top uneven, maybe a gap in it
            wall(out, x, y, z, r.nextBoolean() ? Direction.EAST : Direction.SOUTH, 3 + r.nextInt(4), 4, r);
        } else if (kind < 0.82) { // a corner: two low walls meeting
            Direction a = Direction.Plane.HORIZONTAL.getRandomDirection(r), b = r.nextBoolean() ? a.getClockWise() : a.getCounterClockWise();
            wall(out, x, y, z, a, 2 + r.nextInt(3), 3, r);
            wall(out, x, y, z, b, 2 + r.nextInt(3), 3, r);
        } else { // a column fallen over: its stump, and the rest lying along the ground
            Direction along = Direction.Plane.HORIZONTAL.getRandomDirection(r);
            int stump = 1 + r.nextInt(2), length = 3 + r.nextInt(3);
            for (int k = 0; k < stump; k++) out.put(new BlockPos(x, y + k, z), brick(r));
            for (int k = 2; k < 2 + length; k++) out.put(new BlockPos(x + along.getStepX() * k, y, z + along.getStepZ() * k), brick(r));
            if (r.nextBoolean()) out.put(new BlockPos(x + along.getStepX() * (2 + length), y, z + along.getStepZ() * (2 + length)), brokenTop(r));
        }
        int bits = 3 + r.nextInt(4);
        for (int i = 0; i < bits; i++) {
            BlockPos at = new BlockPos(x + r.nextInt(7) - 3, y, z + r.nextInt(7) - 3);
            out.putIfAbsent(at, rubble(r));
        }
    }

    private void wall(Map<BlockPos, BlockState> out, int x, int y, int z, Direction along, int length, int maxHeight, RandomSource r) {
        int h = 1 + r.nextInt(maxHeight);
        for (int k = 0; k < length; k++) {
            h = Mth.clamp(h + r.nextInt(3) - 1, 1, maxHeight);
            if (k > 0 && k < length - 1 && r.nextFloat() < 0.15f) continue; // a gap
            int bx = x + along.getStepX() * k, bz = z + along.getStepZ() * k;
            for (int j = 0; j < h; j++) out.put(new BlockPos(bx, y + j, bz), brick(r));
            if (r.nextFloat() < 0.3f) out.put(new BlockPos(bx, y + h, bz), brokenTop(r));
        }
    }
}

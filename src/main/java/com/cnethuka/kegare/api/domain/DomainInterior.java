package com.cnethuka.kegare.api.domain;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A Domain's inner space as it's built (DESIGN §3.35), handed to {@link DomainExpansion#furnish} once Kegare has cleared it and laid
 * its floor (and its ruins, if its look has them): place whatever else your technique's inner space holds. It's a plot of its own in a
 * dimension of its own, rebuilt every time, so nothing placed here ever touches the world.
 */
public interface DomainInterior {
    ServerLevel level();

    /** The middle of its floor: the block you stand on top of is just below this. */
    BlockPos centre();

    /** How far it reaches from its centre, in blocks: its sky is a dome of this radius. */
    int radius();

    RandomSource random();

    /** Places a block, if it's within its dome (anywhere else is ignored). */
    void place(BlockPos pos, BlockState state);
}

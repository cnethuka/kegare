package com.cnethuka.kegare.api.dominion;

import java.util.Optional;
import net.minecraft.resources.ResourceLocation;

/**
 * What a Dominion's inside looks like, drawn by each client over the real world (nothing in the world changes): the sky and fog inside the
 * barrier, the barrier's own color, a pattern laid over the ground's top faces (pixel art at 16 texels a block, a {@code textures/marks/}
 * name such as {@code kegare:dominion_waste}), ruins standing in it, dust drifting through it, and whether its shell is cracked.
 *
 * @param skyColor     RGB of the inside of the shell: the sky from inside
 * @param fogColor     RGB of the fog inside
 * @param fogEnd       how far you see inside, in blocks
 * @param shellColor   RGB of the barrier seen from outside
 * @param groundPattern a block mark laid over every top face inside, or empty for none
 * @param ruins        broken ruins stand in it
 * @param dustColor    RGB of dust drifting through it, or -1 for none
 * @param cracked      the shell shows cracks (an Incomplete Dominion's does)
 */
public record DominionLook(int skyColor, int fogColor, float fogEnd, int shellColor, Optional<ResourceLocation> groundPattern, boolean ruins,
                           int dustColor, boolean cracked) {
}

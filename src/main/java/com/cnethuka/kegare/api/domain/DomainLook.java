package com.cnethuka.kegare.api.domain;

import java.util.Optional;
import net.minecraft.resources.ResourceLocation;

/**
 * What a Domain looks like (DESIGN §3.35): its shell from outside, the sky and fog of its inner space, the block its inner space's floor
 * is made of, ruins standing on it, dust drifting through it, and whether its shell is cracked. The inner space is built of real blocks in
 * a dimension of its own; its sky is drawn by the clients.
 *
 * @param skyColor   RGB of its inner space's sky overhead
 * @param fogColor   RGB of the fog in its inner space, and of its sky at the horizon (it blends to {@code skyColor} overhead)
 * @param fogEnd     how far you see in its inner space, in blocks
 * @param shellColor RGB of the barrier seen from outside, a solid wall of it (0x000000: pitch black, as the Incomplete Domain's)
 * @param ground     the block its inner space's floor is made of (a block ID), or empty for Kegare's wasteland ground (a patchwork of
 *                   vanilla tuff, andesite, stone, cobblestone and coarse dirt)
 * @param ruins      Kegare's ruins stand on its floor: broken columns and walls of vanilla stone bricks, with rubble round them (and on
 *                   the wasteland ground, dead bushes, loose stones and pebbles)
 * @param dustColor  RGB of dust drifting through its inner space, or -1 for none
 * @param cracked    the shell closes already cracked: white cracks glowing on it from the start, as if it had lost a quarter of its
 *                   strength (every shell cracks as it's hit, cracked or not)
 */
public record DomainLook(int skyColor, int fogColor, float fogEnd, int shellColor, Optional<ResourceLocation> ground, boolean ruins,
                           int dustColor, boolean cracked) {
}

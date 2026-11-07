"""Splinter's player animation -> player_animations/splinter.json (in the Glacial Lance's style: the whole body, in Blockbench,
from Python).

Three shards flung in a fan, so the throw is flat, not overhand like Frost Shard's flick: a backhand sweep, the hand drawn across
the chest to the left shoulder as the chest turns away, then whipped out flat to the right through the aim, the fan leaving as it
passes the middle. Lighter and quicker than the flick: a small turn of the hips, the free hand only balancing.

Directions as in melee.py: hip +X leans forward, +Y twists right, +Z tilts left. Arms: -X raises forward (-90 = level), +Y swings
a raised arm toward the player's right, the right arm's +Z swings it out to its side. Your own first-person hands stay vanilla
(RimeClient).
"""
from kit import Anim, pose

DRAWN = pose(hip=(-2, -24, 0), right_arm=(-84, -58, 0), left_arm=(-30, 14, -6))
SWEPT = pose(hip=(5, 16, 0), hip_pos=(0, -0.2, -0.2), right_arm=(-92, 40, 6), left_arm=(14, 0, -16))

# The code blends every animation in over its first 3 ticks (PlayerAnimations), so the draw sits at 2 ticks, where most of it shows:
# any sooner and it's swallowed by the blend.
splinter_cast = (Anim("splinter_cast", 0.43, lerp="catmullrom", fade_out=4,
                      note="A backhand fan: the hand drawn across the chest, then whipped out flat through the aim, the free "
                           "hand balancing.")
                 .key(0.0, pose())
                 .key(0.1, DRAWN, "linear")
                 .key(0.16, SWEPT, "linear")
                 .key(0.23, pose(hip=(5, 18, 0), hip_pos=(0, -0.2, -0.2), right_arm=(-80, 48, 10), left_arm=(16, 0, -16))))

ANIMS = [splinter_cast]

"""Frost Shard's player animation -> player_animations/frost_shard.json (in the Glacial Lance's style: the whole body, in Blockbench,
from Python).

A dart thrown hard and fast: the hand flicks up by the ear as the chest turns away, then snaps straight out at the aim with the chest
coming round behind it, the free hand pulled back to pay for it. The shard leaves as it's cast, so the snap comes almost at once and
the rest is follow-through.

Directions as in melee.py: hip +X leans forward, +Y twists right, +Z tilts left. Arms: -X raises forward, +X swings back, the right
arm's +Z swings it out to its side. Your own first-person hands stay vanilla (RimeClient).
"""
from kit import Anim, pose

COCKED = pose(hip=(-3, 18, 0), right_arm=(-150, -8, 14), left_arm=(-62, -12, 0))
SNAPPED = pose(hip=(8, -16, 0), hip_pos=(0, -0.3, -0.3), right_arm=(-90, -14, 0), left_arm=(22, 0, -12))

frost_shard_cast = (Anim("frost_shard_cast", 0.45, lerp="catmullrom", fade_out=5,
                         note="A dart flicked: up by the ear, then snapped straight out at the aim with the chest behind it, the free "
                              "hand pulled back.")
                    .key(0.0, pose())
                    .key(0.04, COCKED, "linear")
                    .key(0.09, SNAPPED, "linear")
                    .key(0.2, pose(hip=(9, -19, 0), hip_pos=(0, -0.3, -0.3), right_arm=(-70, -20, 0), left_arm=(24, 0, -12))))

ANIMS = [frost_shard_cast]

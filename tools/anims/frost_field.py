"""Frost Field's player animation -> player_animations/frost_field.json (in the Glacial Lance's style: the whole body, in Blockbench,
from Python).

The channel pours cold out over the ground ahead: a beat to draw both hands in to the chest, then they thrust out low at the aim, the
right palm leading and the left hand braced on its forearm, the body leaning in behind them. It holds there while the frost runs
out, straining, a slow press through the arms and shoulders. Legs are left to walking, as everywhere.

Directions as in melee.py: hip +X leans forward, +Y twists right, +Z tilts left. Arms: -X raises forward (-90 = level), +Y swings
a raised arm toward the player's right, the right arm's +Z swings it out to its side (the left arm's -Z). Your own first-person
hands stay vanilla (RimeClient).
"""
from kit import Anim, merge, pose

DRAWN = pose(hip=(-4, 6, 0), right_arm=(-50, -20, 10), left_arm=(-56, 28, -6))
THRUST = pose(hip=(10, -4, 0), hip_pos=(0, -0.8, -0.5), right_arm=(-74, -6, 2), left_arm=(-66, 32, -2))
_PRESS = merge(THRUST, hip=(13, -4, 0), hip_pos=(0, -1.0, -0.6), right_arm=(-69, -5, 2), left_arm=(-61, 31, -2))

frost_field_channel = (Anim("frost_field_channel", 1.0, loop=True, fade_in=2, loop_from=0.2,
                            note="Hands drawn in, then thrust out low at the aim, the left braced on the right forearm, leaning "
                                 "in; it holds there, pressing, while the frost runs out ahead.")
                       .key(0.1, DRAWN)
                       .key(0.2, THRUST)
                       .key(0.6, _PRESS)
                       .key(1.0, THRUST))

ANIMS = [frost_field_channel]

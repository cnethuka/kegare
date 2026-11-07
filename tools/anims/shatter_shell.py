"""Shatter Shell's player animation -> player_animations/shatter_shell.json (in the Glacial Lance's style: the whole body, in
Blockbench, from Python).

The burst is instant (the knockback lands the tick it's cast), so there's no wind-up to show: the body is thrown open on the cast
itself, arms flung wide and up to the sides, chest out, leaning back, risen a little, as the plates fly off (ShellClient.burst)
and the ring goes out (ShatterClient). It holds there a moment, opening a little more, then drops back, the arms falling to the
sides. The 2-tick blend in from wherever the body was is the fling. Legs are left to walking, as everywhere.

Directions as in melee.py: hip +X leans forward, +Y twists right, +Z tilts left. Arms: -X raises forward (-90 = level), the right
arm's +Z swings it out to its side (the left arm's -Z). Your own first-person hands stay vanilla (RimeClient).
"""
from kit import Anim, pose

FLUNG = pose(hip=(-13, 0, 0), hip_pos=(0, 0.5, 0.3), right_arm=(-28, 0, 104), left_arm=(-28, 0, -104))
OPEN = pose(hip=(-15, 0, 0), hip_pos=(0, 0.6, 0.35), right_arm=(-22, 0, 114), left_arm=(-22, 0, -114))
DROP = pose(hip=(4, 0, 0), hip_pos=(0, -0.3, 0), right_arm=(-4, 0, 34), left_arm=(-4, 0, -34))

shatter_shell_cast = (Anim("shatter_shell_cast", 0.8, lerp="catmullrom", fade_in=2, fade_out=5,
                           note="Thrown open on the burst: arms flung wide and up, chest out, leaning back; held a moment, then "
                                "dropped back.")
                      .key(0.1, FLUNG)
                      .key(0.3, OPEN, "linear")
                      .key(0.55, DROP))

ANIMS = [shatter_shell_cast]

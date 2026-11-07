"""Ice Path's player animation -> player_animations/ice_path.json (in the Glacial Lance's style: the whole body, in Blockbench,
from Python).

A slide on the ice it lays: a push off, then a skater's glide. Feet split, the left ahead and the right trailing, the body sunk a
little onto them; the chest leant into the slide; the arms out for balance, the left reaching ahead and low, the right swept back
and out. While it holds, a slow sway from side to side, the arms trading a little height, as a glide on ice keeps its balance. The
legs are keyed here (a slide isn't walking); the head is left to the look, which is where the slide goes.

Directions as in melee.py: hip +X leans forward, +Y twists right, +Z tilts left; legs and arms -X swing forward. Arms: the right
arm's +Z swings it out to its side, the left arm's -Z. Your own first-person hands stay vanilla (RimeClient).
"""
from kit import Anim, merge, pose

PUSH = pose(hip=(8, -10, 0), hip_pos=(0, -0.4, 0), body_pos=(0, -0.4, 0), right_leg=(22, 0, 4), left_leg=(-10, 0, -2),
            right_arm=(-30, 0, 18), left_arm=(20, 0, -24))
GLIDE = pose(hip=(16, 8, 4), hip_pos=(0, -1.0, -0.6), body_pos=(0, -1.1, 0), right_leg=(24, 0, 6), left_leg=(-22, 0, -3),
             right_arm=(26, 10, 58), left_arm=(-38, 6, -46))
_SWAY = merge(GLIDE, hip=(16, 6, -3), right_arm=(20, 10, 50), left_arm=(-44, 6, -54))

ice_path_slide = (Anim("ice_path_slide", 1.3, loop=True, fade_in=2, loop_from=0.3,
                       note="A push off, then a skater's glide: feet split, sunk onto them, leant in, arms out for balance, "
                            "swaying side to side.")
                  .key(0.1, PUSH)
                  .key(0.3, GLIDE)
                  .key(0.8, _SWAY)
                  .key(1.3, GLIDE))

ANIMS = [ice_path_slide]

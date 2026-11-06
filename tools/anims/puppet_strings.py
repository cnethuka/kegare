"""Strings' five player animations, as first poses for Blockbench (see tools/blockbench/build_string_animations.py).

Unlike tools/anims/puppet_manipulation.py this is NOT the source of truth: art/blockbench/string_animations.bbmodel is. These
poses only fill that project the first time. Every animation starts and ends on rest keys and has no fades, because a plain
Blockbench export can't carry PAL's beginTick/endTick; legs are left to the game except on Zip, which is airborne.

The tell of each (DESIGN §3.15): one hand thrown out (fire); hand over hand (Reel In); one arm up and ahead, trailing like a
kite (Zip); arms flung wide, then wrists crossed (Tie); both fists low and wide, leaning back (Leash).
"""
from kit import Anim, pose

REST = pose()

# One hand: cocked back across the body, thrown out at the target, a beat holding it there.
fire = (Anim("strings_fire", 0.6, note="One hand cocks back and throws straight out at the target.")
        .key(0.0, REST)
        .key(0.07, pose(hip=(-4, -18, 0), right_arm=(-34, 0, 34), left_arm=(-24, 0, -12)))
        .key(0.14, pose(hip=(8, 20, 0), hip_pos=(0, -0.5, -0.5), right_arm=(-100, -20, 0), left_arm=(20, 0, -18)))
        .key(0.26, pose(hip=(6, 15, 0), hip_pos=(0, -0.25, -0.25), right_arm=(-94, -15, 0), left_arm=(14, 0, -14)))
        .key(0.44, pose(hip=(2, 5, 0), right_arm=(-44, -5, 2), left_arm=(5, 0, -6)))
        .key(0.6, REST))

# Hand over hand: each hand reaches out and hauls back to the hip in turn, leaning back against the weight.
_R_HAUL = dict(right_arm=(-26, -6, 12), left_arm=(-104, 8, 0))
_L_HAUL = dict(right_arm=(-104, -8, 0), left_arm=(-26, 6, -12))
reel = (Anim("strings_reel", 1.0, note="Hand over hand: each hand hauls back in turn, leaning back against the weight.")
        .key(0.0, REST)
        .key(0.08, pose(hip=(-4, 0, 0), right_arm=(-106, -6, 0), left_arm=(-90, 6, 0)))
        .key(0.2, pose(hip=(-9, -12, 0), hip_pos=(0, -0.5, 0.75), **_R_HAUL))
        .key(0.34, pose(hip=(-9, 12, 0), hip_pos=(0, -0.5, 0.75), **_L_HAUL))
        .key(0.48, pose(hip=(-9, -12, 0), hip_pos=(0, -0.5, 0.75), **_R_HAUL))
        .key(0.62, pose(hip=(-9, 12, 0), hip_pos=(0, -0.5, 0.75), **_L_HAUL))
        .key(0.8, pose(hip=(-3, 0, 0), right_arm=(-34, -4, 6), left_arm=(-34, 4, -6)))
        .key(1.0, REST))

# Airborne: the string arm up and ahead, the body strung out behind it, legs trailing; a crouching catch on landing.
_FLY = dict(hip=(30, 7, 0), hip_pos=(0, -0.5, -1), right_arm=(-158, -8, 16), left_arm=(44, 0, -24), right_leg=(24, 0, 4), left_leg=(38, 0, -4))
zip_ = (Anim("strings_zip", 0.95, note="Yanked off the ground: the string arm up and ahead, body and legs trailing behind it.")
        .key(0.0, REST)
        .key(0.07, pose(hip=(-8, 0, 0), right_arm=(-122, -6, 20), left_arm=(10, 0, -12)))
        .key(0.17, pose(**_FLY))
        .key(0.56, pose(**{**_FLY, "hip": (26, 5, 0), "right_arm": (-152, -8, 16)}))
        .key(0.72, pose(hip=(14, 0, 0), hip_pos=(0, -1, 0), right_arm=(-60, -8, 14), left_arm=(14, 0, -26), right_leg=(-14, 0, 2), left_leg=(8, 0, -2)))
        .key(0.95, REST))

# Both arms flung wide, each holding a target, then slammed together till the wrists cross: the knot, jerked tight.
tie = (Anim("strings_tie", 1.0, note="Arms flung wide, then slammed together until the wrists cross; the knot is jerked tight.")
       .key(0.0, REST)
       .key(0.1, pose(hip=(-8, 0, 0), right_arm=(-26, 0, 108), left_arm=(-26, 0, -108)))
       .key(0.22, pose(hip=(-10, 0, 0), right_arm=(-28, 0, 120), left_arm=(-28, 0, -120)))
       .key(0.34, pose(hip=(12, 0, 0), hip_pos=(0, -1, 0), right_arm=(-88, -34, 0), left_arm=(-88, 34, 0)))
       .key(0.42, pose(hip=(14, 0, 0), hip_pos=(0, -1, 0), right_arm=(-94, -46, 0), left_arm=(-94, 46, 0)))
       .key(0.58, pose(hip=(5, 0, 0), hip_pos=(0, -0.5, 0.5), right_arm=(-66, -14, 10), left_arm=(-66, 14, -10)))
       .key(0.8, pose(hip=(2, 0, 0), right_arm=(-28, -4, 6), left_arm=(-28, 4, -6)))
       .key(1.0, REST))

# Two leads in two fists: reaches for both, then yanks them back and down, low and wide, leaning back; held.
leash = (Anim("strings_leash", 1.25, note="Two leads in two fists: yanked back low and wide, leaning back, and held.")
         .key(0.0, REST)
         .key(0.09, pose(hip=(5, 0, 0), right_arm=(-62, 0, 24), left_arm=(-62, 0, -24)))
         .key(0.2, pose(hip=(-12, 0, 0), hip_pos=(0, -1, 0.75), right_arm=(-12, 8, 36), left_arm=(-12, -8, -36)))
         .key(0.34, pose(hip=(-8, 0, 0), hip_pos=(0, -0.75, 0.5), right_arm=(-22, 6, 30), left_arm=(-22, -6, -30)))
         .key(0.9, pose(hip=(-7, 0, 0), hip_pos=(0, -0.5, 0.5), right_arm=(-24, 6, 28), left_arm=(-24, -6, -28)))
         .key(1.25, REST))

ANIMS = [fire, reel, zip_, tie, leash]

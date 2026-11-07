"""Swap's player animations -> player_animations/swap.json (DESIGN §3.19): every swap is a clap, so every cast is the hands.

The feel is a clean magic trick: the hands meet in one sharp beat and the swap has already happened. Each move claps its own way,
so the moves tell apart at a glance. The casts are instant and every animation is blended in over its first 3 ticks
(PlayerAnimations), so a clap's hands go straight for each other and meet as the blend ends: the swap lands on the sound, the
hands a moment after. The claps meet low in front of the chest, about where the melee guard sits, so in first person the hands
come up at the bottom corners of the view and never cover its middle; anything wide goes out to the sides, never up.

Directions as in melee.py: hip +X leans forward, +Y twists right, +Z tilts left. Arms: -X raises forward (-90 = level), the right
arm's +Z swings it out to its side (the left arm's -Z); raised forward, the right arm's -Y draws it in across the chest (the left's
+Y). Legs are left to walking.
"""
import json
from pathlib import Path

from kit import Anim, pose

REST = pose()


def clap(lean=5, x=-82, inward=27, drop=0.0):
    """Both hands together in front of the chest: {x} how high, {inward} how far each arm is drawn in."""
    return pose(hip=(lean, 0, 0), hip_pos=(0, drop, 0), right_arm=(x, -inward, 0), left_arm=(x, inward, 0))


def apart(x=-64, out=26, turn=14, lean=-3):
    """Hands sprung apart, palms out: the trick done."""
    return pose(hip=(lean, 0, 0), right_arm=(x, turn, out), left_arm=(x, -turn, -out))


DROP = pose(hip=(1, 0, 0), right_arm=(-24, 4, 10), left_arm=(-24, -4, -10))

# Positions: the plain clap. The hands go straight for each other and meet, then spring open, palms out; then dropped.
positions_clap = (Anim("positions_clap", 0.57, fade_in=1, fade_out=3,
                       note="The plain clap: the hands go straight for each other and meet low in front of the chest, then spring "
                            "open, palms out; then dropped.")
                  .key(0.05, clap())
                  .key(0.14, clap(7, -80, 29, -0.3), "linear")
                  .key(0.28, apart())
                  .key(0.42, DROP))

# Switchback: two claps (a beat apart, the second harder), then the right hand turned back toward the chest, a "come back".
switchback_cast = (Anim("switchback_cast", 0.85, fade_in=1, fade_out=4,
                        note="A double clap: together, a hand's width apart, together again harder; then the right hand turns back "
                             "toward the chest, the come-back that promises the swap back.")
                   .key(0.05, clap())
                   .key(0.13, clap(7, -80, 29, -0.3), "linear")
                   .key(0.2, apart(-76, 8, 10, 2), "linear")
                   .key(0.28, clap(9, -82, 30, -0.5), "linear")
                   .key(0.42, pose(hip=(2, 0, 0), right_arm=(-74, -46, 0), left_arm=(-34, 6, -10)))
                   .key(0.65, pose(hip=(1, 0, 0), right_arm=(-62, -40, 0), left_arm=(-24, 4, -10))))

# The swap back of a Switchback: the right hand out to the side at chest height and a sharp snap of it, down and in.
switchback_snap = (Anim("switchback_snap", 0.5, fade_in=1, fade_out=3,
                        note="The swap back: the right hand flicked out to the side at chest height, then snapped down and in, "
                             "the left hand still.")
                   .key(0.05, pose(hip=(-2, 4, 0), right_arm=(-78, 16, 34)))
                   .key(0.12, pose(hip=(4, -4, 0), right_arm=(-58, -6, 18)), "linear")
                   .key(0.35, pose(hip=(1, 0, 0), right_arm=(-30, 0, 12))))

# Awareness: a clap, then the hands turn over and open wide at chest height like a card shown both sides: the looks change hands.
awareness_cast = (Anim("awareness_cast", 0.75, fade_in=1, fade_out=4,
                       note="A clap, then the hands turn over and fan open wide at chest height, a card shown front and back; held "
                            "a moment, then dropped.")
                  .key(0.05, clap(4, -84, 26))
                  .key(0.14, clap(6, -82, 28, -0.2), "linear")
                  .key(0.26, pose(hip=(-4, 0, 0), right_arm=(-80, 30, 48), left_arm=(-80, -30, -48)))
                  .key(0.42, pose(hip=(-5, 0, 0), right_arm=(-76, 34, 54), left_arm=(-76, -34, -54)))
                  .key(0.55, DROP))

# Mimic: hands cupped together up by the right side of the face (a hollow clap there, out of your own view's middle), then the right
# hand drawn away down past the cheek as the face is taken.
mimic_cast = (Anim("mimic_cast", 0.75, fade_in=1, fade_out=4,
                   note="A cupped clap up by the right side of the face, the head tipped toward it; then the right hand drawn down "
                        "past the cheek, the face taken.")
              .key(0.05, pose(hip=(0, 10, 2), right_arm=(-118, -6, 28), left_arm=(-110, 46, 0)))
              .key(0.14, pose(hip=(2, 12, 3), right_arm=(-122, -10, 24), left_arm=(-116, 50, 0)), "linear")
              .key(0.34, pose(hip=(2, 8, 1), right_arm=(-96, -24, 12), left_arm=(-58, 22, 0)))
              .key(0.55, pose(hip=(1, 2, 0), right_arm=(-40, -8, 8), left_arm=(-24, 6, -8))))

# Anchor's throw: a token flicked forward off the fingers, underhand: low, then out level at the right side, a little follow-through.
anchor_throw = (Anim("anchor_throw", 0.45, fade_in=1, fade_out=3,
                     note="A token flicked forward underhand: the right hand low and back, then out level at the right side with a "
                          "little follow-through.")
                .key(0.05, pose(hip=(4, -6, 0), right_arm=(24, 0, 12), left_arm=(-8, 0, -8)))
                .key(0.13, pose(hip=(-2, 4, 0), right_arm=(-88, 8, 14), left_arm=(-4, 0, -10)), "linear")
                .key(0.3, pose(hip=(1, 2, 0), right_arm=(-70, 4, 12), left_arm=(-4, 0, -8))))

# Anchor's recall: the right hand reaches toward the anchor, then the left claps onto it as it's pulled back to the chest.
anchor_recall = (Anim("anchor_recall", 0.6, fade_in=1, fade_out=3,
                      note="The right hand reaches out toward the anchor and is pulled back to the chest, the left hand clapping "
                           "onto it on the way.")
                 .key(0.05, pose(hip=(6, 0, 0), right_arm=(-86, -4, 6), left_arm=(-40, 10, -10)))
                 .key(0.15, pose(hip=(4, 0, 0), hip_pos=(0, -0.2, 0), right_arm=(-76, -30, 0), left_arm=(-80, 28, 0)), "linear")
                 .key(0.3, pose(hip=(-2, 0, 0), right_arm=(-62, -34, 0), left_arm=(-66, 30, 0)))
                 .key(0.45, DROP))

# Send Off: a clap, then both palms shoved straight out at the target, the body leaning in behind them.
send_off_cast = (Anim("send_off_cast", 0.7, fade_in=1, fade_out=4,
                      note="A clap, then both palms shoved straight out at the target, the body leaning in behind them; held, "
                           "then dropped.")
                 .key(0.05, clap(2, -78, 26))
                 .key(0.12, clap(4, -76, 28), "linear")
                 .key(0.22, pose(hip=(14, 0, 0), hip_pos=(0, -0.4, -0.8), right_arm=(-92, -12, 0), left_arm=(-92, 12, 0)), "linear")
                 .key(0.4, pose(hip=(12, 0, 0), hip_pos=(0, -0.3, -0.6), right_arm=(-88, -10, 0), left_arm=(-88, 10, 0)))
                 .key(0.5, DROP))

# Exchange: a clap, then the hands slide past each other, each to the other side (the trade), and part.
exchange_cast = (Anim("exchange_cast", 0.75, fade_in=1, fade_out=4,
                      note="A clap, then the hands slide past each other, each to the other's side, the trade; then they part.")
                 .key(0.05, clap(4, -80, 26))
                 .key(0.13, clap(6, -80, 28, -0.2), "linear")
                 .key(0.3, pose(hip=(3, 0, 0), right_arm=(-74, -50, 0), left_arm=(-86, 50, 0)))
                 .key(0.45, apart(-60, 20, 10))
                 .key(0.55, DROP))

# Momentum Exchange: a hard clap with the body thrown forward into it, then the arms flung back behind, the push given away.
momentum_cast = (Anim("momentum_cast", 0.7, fade_in=1, fade_out=4,
                      note="A hard clap with the body thrown forward into it, then both arms flung back behind and the chest up: "
                           "the push given away.")
                 .key(0.05, clap(10, -80, 27, -0.4))
                 .key(0.13, clap(14, -78, 29, -0.7), "linear")
                 .key(0.28, pose(hip=(-8, 0, 0), hip_pos=(0, 0, 0.5), right_arm=(42, 0, 16), left_arm=(42, 0, -16)), "linear")
                 .key(0.5, pose(hip=(-3, 0, 0), right_arm=(14, 0, 10), left_arm=(14, 0, -10))))

# Total Swap: the biggest clap. Arms flung wide (out, never up: a raised arm fills the first-person view), slammed together low with
# the knees giving under it, then thrown wide open: everything has changed hands.
total_swap_clap = (Anim("total_swap_clap", 1.0, fade_in=1, fade_out=5,
                        note="The biggest clap: arms flung wide, slammed together low with the knees giving under it, then thrown "
                             "wide open; held, then dropped.")
                   .key(0.05, pose(hip=(-8, 0, 0), right_arm=(-24, 0, 76), left_arm=(-24, 0, -76)))
                   .key(0.16, pose(hip=(-10, 0, 0), hip_pos=(0, 0.3, 0), right_arm=(-28, 0, 82), left_arm=(-28, 0, -82)))
                   .key(0.24, clap(12, -78, 29, -1.2), "linear")
                   .key(0.3, clap(13, -76, 29, -1.3))
                   .key(0.46, pose(hip=(-9, 0, 0), right_arm=(-50, 18, 74), left_arm=(-50, -18, -74)))
                   .key(0.65, pose(hip=(-6, 0, 0), right_arm=(-44, 14, 66), left_arm=(-44, -14, -66)))
                   .key(0.75, DROP))

ALL = [positions_clap, switchback_cast, switchback_snap, awareness_cast, mimic_cast, anchor_throw, anchor_recall, send_off_cast,
       exchange_cast, momentum_cast, total_swap_clap]
# The rework lands one move at a time: the moves done so far are built from here, the rest still play their old hand-keyed
# animation from swap_legacy.json (the swap.json before the rework), which goes once every move is done.
DONE = {"positions_clap"}
ANIMS = [a for a in ALL if a.name in DONE]
_LEGACY = json.loads((Path(__file__).parent / "swap_legacy.json").read_text())["animations"]
LEGACY = {name: anim for name, anim in _LEGACY.items() if name not in DONE}

"""Severed's player animations -> player_animations/severed.json (DESIGN §3.27): a body and nothing else. No casting gestures: weight,
breath and footing. Every one is physical: a breath out and a low stance, a planted push-off, a skid to a stop, hands on knees,
and a body thrown back off a wall.

Directions as in swap.py: hip +X leans forward, +Y twists right, +Z tilts left; hip_pos -Y sinks (never more than 1 px). Arms: -X
raises forward (-90 = level), +X swings back, the right arm's +Z swings it out to its side (the left arm's -Z). Head: -X looks up.
Legs are left to walking. In your own first person, Blur's start, the launch and the arrival keep the hands low (shown only while
you look about level); winded and the wall crash leave them to vanilla (hands on knees and arms flung out would cross the view).
"""
from kit import Anim, pose

# Blur's start: a breath out, the shoulders dropping, into a low, forward stance; held a moment, then easing up a little.
blur_on = (Anim("blur_on", 0.7, fade_in=2, fade_out=5,
                note="A breath out and a low stance: the shoulders drop, the body leans forward and sinks, arms loose and a little "
                     "back; held, then easing up.")
           .key(0.1, pose(hip=(-4, 0, 0), right_arm=(-6, 0, 6), left_arm=(-6, 0, -6)))
           .key(0.25, pose(hip=(16, 0, 0), hip_pos=(0, -1.0, 0), right_arm=(22, 0, 10), left_arm=(22, 0, -10)))
           .key(0.45, pose(hip=(14, 0, 0), hip_pos=(0, -0.8, 0), right_arm=(18, 0, 8), left_arm=(18, 0, -8))))

# Closing Line's launch: planted and pushed off: deep forward lean, both arms flung back, the body low; the first frame is the
# push, because the server has already started the rush.
closing_line_launch = (Anim("closing_line_launch", 0.45, fade_in=1, fade_out=4,
                            note="A planted push-off: the body pitched forward and low, both arms flung back behind, all of it driven "
                                 "off the back foot.")
                       .key(0.05, pose(hip=(30, 0, 0), hip_pos=(0, -1.0, 0), right_arm=(48, 0, 12), left_arm=(48, 0, -12)))
                       .key(0.25, pose(hip=(24, 0, 0), hip_pos=(0, -0.6, 0), right_arm=(36, 0, 10), left_arm=(36, 0, -10))))

# Arriving: a skid to a stop, leaning back against it, arms out low in front for balance, a fist ready.
closing_line_arrive = (Anim("closing_line_arrive", 0.55, fade_in=1, fade_out=5,
                            note="A skid to a stop: leaning back against the speed, sinking, the arms out low in front for balance, the "
                                 "right fist closing.")
                       .key(0.05, pose(hip=(-14, 0, 0), hip_pos=(0, -1.0, 0), right_arm=(-48, -6, 14), left_arm=(-40, 6, -18)))
                       .key(0.3, pose(hip=(-6, 0, 0), hip_pos=(0, -0.6, 0), right_arm=(-56, -10, 8), left_arm=(-30, 4, -14))))

# Winded: bent over, hands on the knees, the head down, the back rising and falling with each breath.
winded = (Anim("winded", 1.6, fade_in=4, fade_out=6, look=False,
               note="Winded: bent over, hands on the knees, head hanging, the back heaving with three ragged breaths.")
          .key(0.2, pose(hip=(36, 0, 0), hip_pos=(0, -1.0, 0), head=(22, 0, 0), right_arm=(-28, 0, -4), left_arm=(-28, 0, 4)))
          .key(0.5, pose(hip=(30, 0, 0), hip_pos=(0, -0.6, 0), head=(18, 0, 0), right_arm=(-24, 0, -4), left_arm=(-24, 0, 4)))
          .key(0.8, pose(hip=(37, 0, 0), hip_pos=(0, -1.0, 0), head=(24, 0, 0), right_arm=(-28, 0, -4), left_arm=(-28, 0, 4)))
          .key(1.3, pose(hip=(32, 0, 0), hip_pos=(0, -0.7, 0), head=(20, 0, 0), right_arm=(-25, 0, -4), left_arm=(-25, 0, 4))))

# The wall crash: thrown back off it, head snapped back, arms flung out wide (out, never up), then slumping forward.
wall_crash = (Anim("wall_crash", 0.8, fade_in=1, fade_out=6, look=False,
                   note="Thrown back off the wall: the head snapped back, the arms flung out wide, then slumping forward, dazed.")
              .key(0.05, pose(hip=(-22, 0, 0), head=(-24, 0, 0), right_arm=(10, 0, 52), left_arm=(10, 0, -52)))
              .key(0.25, pose(hip=(-10, 0, 0), head=(-10, 0, 0), right_arm=(4, 0, 34), left_arm=(4, 0, -34)))
              .key(0.5, pose(hip=(14, 0, 0), hip_pos=(0, -0.8, 0), head=(16, 0, 0), right_arm=(-12, 0, 10), left_arm=(-12, 0, -10))))

ANIMS = [blur_on, closing_line_launch, closing_line_arrive, winded, wall_crash]

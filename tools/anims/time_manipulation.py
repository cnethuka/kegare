"""Time Manipulation's player animations -> player_animations/time_manipulation.json (DESIGN §3.26).

Precise, elegant and cold: a hand that knows exactly where it's going, no wasted motion, every move ending on a held beat. The
gestures are a watchmaker's and a duellist's: a fingertip set on a point, a crown wound between finger and thumb, a blade flicked
off the back of the hand, a palm laid flat on the air. Nothing is flailed; nothing is held up in the view: in your own first person
every cast but two keeps the hands low and to the sides (shown only while you look about level), and those two (Standstill's watch
raised before the face, Stasis Field's palm pushed at the ground) leave the hands to vanilla there.

Directions as in swap.py: hip +X leans forward, +Y twists right, +Z tilts left. Arms: -X raises forward (-90 = level), the right
arm's +Z swings it out to its side (the left arm's -Z); raised forward, the right arm's -Y draws it in across the chest (the left's
+Y). Legs are left to walking.
"""
from kit import Anim, pose

REST = pose()
# Hands loose at the sides, a little out: the composure every cast settles back into.
POISED = pose(hip=(1, 0, 0), right_arm=(-12, 0, 8), left_arm=(-12, 0, -8))

# Point Maker, the mark: the right hand points down at the block ahead, a fingertip set on it with one small, exact tap.
point_maker_mark = (Anim("point_maker_mark", 0.6, fade_in=2, fade_out=4,
                         note="A fingertip set on the spot: the right arm points down and ahead at the marked block, taps once, "
                              "precisely, and is drawn back; the left hand stays at the side.")
                    .key(0.1, pose(hip=(6, -4, 0), right_arm=(-52, -6, 4), left_arm=(-10, 0, -6)))
                    .key(0.2, pose(hip=(8, -4, 0), right_arm=(-44, -6, 4), left_arm=(-10, 0, -6)), "linear")
                    .key(0.26, pose(hip=(7, -4, 0), right_arm=(-50, -6, 4), left_arm=(-10, 0, -6)))
                    .key(0.4, POISED))

# Point Maker, the return: arriving, already composed: the left hand closes over the chest (a watch lid shut), then both settle.
point_maker_return = (Anim("point_maker_return", 0.55, fade_in=1, fade_out=4,
                           note="Arrived and composed: upright, the left hand brought in to close over the chest like a watch "
                                "lid shut, the right a little out; then both settle at the sides.")
                      .key(0.05, pose(hip=(-2, 0, 0), right_arm=(-18, 0, 16), left_arm=(-58, 30, -4)))
                      .key(0.18, pose(hip=(-1, 0, 0), right_arm=(-14, 0, 12), left_arm=(-62, 34, -2)), "linear")
                      .key(0.35, POISED))

# Rewind: the right hand held out at the target, then turned back counter-clockwise and drawn in to the body: the clock wound back.
rewind_cast = (Anim("rewind_cast", 0.7, fade_in=1, fade_out=5,
                    note="The right arm held out level at the target, then turned back the way a clock never goes: round "
                         "counter-clockwise (out, up a little, in) and drawn back to the body, the upper body leaning back.")
               .key(0.05, pose(hip=(2, -6, 0), right_arm=(-84, -4, 0), left_arm=(-14, 0, -8)))
               .key(0.18, pose(hip=(0, -2, 0), right_arm=(-90, 8, 14), left_arm=(-14, 0, -8)))
               .key(0.3, pose(hip=(-3, 4, 0), right_arm=(-70, -20, 8), left_arm=(-14, 0, -8)))
               .key(0.45, pose(hip=(-4, 6, 0), right_arm=(-46, -26, 2), left_arm=(-14, 0, -8))))

# Accelerate: the left hand cups an unseen watch low before the chest, the right winds its crown: three quick, small twists.
accelerate_toggle = (Anim("accelerate_toggle", 0.6, fade_in=1, fade_out=4,
                          note="Winding the crown: the left hand cups a watch low in front of the chest, the right hand meets it "
                               "and gives the crown three quick, small twists; then let go.")
                     .key(0.05, pose(hip=(4, 0, 0), right_arm=(-60, -22, 0), left_arm=(-56, 24, 0)))
                     .key(0.12, pose(hip=(4, 2, 0), right_arm=(-62, -28, 6), left_arm=(-56, 24, 0)), "linear")
                     .key(0.19, pose(hip=(4, 0, 0), right_arm=(-60, -22, -2), left_arm=(-56, 24, 0)), "linear")
                     .key(0.26, pose(hip=(4, 2, 0), right_arm=(-62, -28, 6), left_arm=(-56, 24, 0)), "linear")
                     .key(0.33, pose(hip=(4, 0, 0), right_arm=(-60, -22, -2), left_arm=(-56, 24, 0)), "linear")
                     .key(0.4, pose(hip=(2, 0, 0), right_arm=(-30, -6, 10), left_arm=(-34, 8, -8))))

# Fast Forward: the right arm sweeps round in front of the body, clockwise, out to the right and on round: the hands of a clock
# whirled forward. The upper body turns with it.
fast_forward_cast = (Anim("fast_forward_cast", 0.6, fade_in=1, fade_out=5,
                          note="A sweep round the clock: the right arm starts across the chest to the left and swings out round "
                               "the front to the right, the body turning with it, the left hand low; held a beat at the end.")
                     .key(0.05, pose(hip=(4, 14, 0), right_arm=(-66, -40, 0), left_arm=(-16, 0, -10)))
                     .key(0.15, pose(hip=(4, 2, 0), right_arm=(-80, -6, 0), left_arm=(-16, 0, -10)), "linear")
                     .key(0.25, pose(hip=(3, -12, 0), right_arm=(-70, 22, 26), left_arm=(-16, 0, -10)), "linear")
                     .key(0.35, pose(hip=(2, -16, 0), right_arm=(-44, 18, 40), left_arm=(-16, 0, -10))))

# Decelerate: a blade flicked off the back of the hand: the right arm drawn in across the chest, then snapped straight out at
# the target, sidearm and flat, and held there a beat.
decelerate_cast = (Anim("decelerate_cast", 0.5, fade_in=1, fade_out=4,
                        note="A thrown blade: the right hand drawn in across the chest to the left shoulder, then flicked straight "
                             "out flat at the target, backhand, the body unwinding behind it; held a beat.")
                   .key(0.05, pose(hip=(2, 16, 0), right_arm=(-74, -46, 0), left_arm=(-20, 4, -8)))
                   .key(0.13, pose(hip=(4, -6, 0), right_arm=(-86, 2, 4), left_arm=(-24, 0, -12)), "linear")
                   .key(0.3, pose(hip=(3, -8, 0), right_arm=(-82, 6, 6), left_arm=(-24, 0, -12))))

# Stasis Field: the right palm laid flat on the air and pushed down at the ground ahead, the body sinking a touch over it; held.
stasis_field_cast = (Anim("stasis_field_cast", 0.7, fade_in=2, fade_out=5,
                          note="A palm laid on the air: the right arm pushed down and ahead at the ground where the field opens, "
                               "slow and certain, the body sinking a touch over it; held, then drawn back.")
                     .key(0.1, pose(hip=(6, -4, 0), hip_pos=(0, -0.5, 0), right_arm=(-58, -4, 2), left_arm=(-12, 0, -10)))
                     .key(0.25, pose(hip=(10, -4, 0), hip_pos=(0, -1.0, 0), right_arm=(-40, -4, 2), left_arm=(-12, 0, -12)), "linear")
                     .key(0.45, pose(hip=(10, -4, 0), hip_pos=(0, -1.0, 0), right_arm=(-38, -4, 2), left_arm=(-12, 0, -12))))

# Standstill: the signature. A watch raised before the face in the right hand, the left arm laid out low to the side; one exact
# press of the thumb (the click time stops on); held, utterly still.
standstill_cast = (Anim("standstill_cast", 1.0, fade_in=3, fade_out=6, look=False,
                        note="The watch raised: the right hand brought up before the face, the left arm laid out low and wide; "
                             "the thumb presses once (a small, exact dip of the hand), then everything is held, utterly still.")
                   .key(0.15, pose(hip=(-2, 8, 0), head=(6, 6, 0), right_arm=(-118, -30, 0), left_arm=(-20, 0, -34)))
                   .key(0.32, pose(hip=(-2, 8, 0), head=(8, 6, 0), right_arm=(-112, -30, 0), left_arm=(-20, 0, -34)), "linear")
                   .key(0.38, pose(hip=(-2, 8, 0), head=(8, 6, 0), right_arm=(-118, -30, 0), left_arm=(-20, 0, -34)), "linear")
                   .key(0.7, pose(hip=(-2, 8, 0), head=(6, 6, 0), right_arm=(-118, -30, 0), left_arm=(-20, 0, -34))))

ANIMS = [point_maker_mark, point_maker_return, rewind_cast, accelerate_toggle, fast_forward_cast, decelerate_cast,
         stasis_field_cast, standstill_cast]

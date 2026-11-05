"""Writes assets/kegare/player_animations/puppet_manipulation.json (Player Animation Library / Bedrock format).

Run from the project root: python3 tools/gen_puppet_anims.py
Rotations are degrees per bone. The caster animations play through PAL. puppet_idle_spin plays on the puppet itself
through Kegare's PuppetPoses sampler (PAL animates players only), which reads the same file: there, "body" turns the
whole puppet and arm/head rotations use the model's own axes (z positive = the right arm swings out).
"""
import json
from pathlib import Path

OUT = Path("src/main/resources/assets/kegare/player_animations/puppet_manipulation.json")


def keys(*frames):
    out = {}
    for t, v, e in frames:
        out["0.0" if t == 0 else repr(float(t))] = {"vector": list(v), "easing": e}
    return out


def anim(length, bones, loop=False):
    return {"animation_length": length, "loop": loop, "bones": {b: {"rotation": keys(*f)} for b, f in bones.items()}}


A = {}

# Idle spin ("kuru kuru"), on the puppet: one full turn every 0.9 s, arms out, head tipped back a little.
A["puppet_idle_spin"] = anim(0.9, {
    "body": [(0, (0, 0, 0), "linear"), (0.9, (0, 360, 0), "linear")],
    "right_arm": [(0, (0, 0, 72), "linear"), (0.45, (0, 0, 64), "easeInOutSine"), (0.9, (0, 0, 72), "easeInOutSine")],
    "left_arm": [(0, (0, 0, -72), "linear"), (0.45, (0, 0, -64), "easeInOutSine"), (0.9, (0, 0, -72), "easeInOutSine")],
    "head": [(0, (-12, 0, 0), "linear"), (0.9, (-12, 0, 0), "linear")],
}, loop=True)

# Puppet Creation, charging (loops): both hands held out, fingers working the strings in turn.
A["puppet_create_charge"] = anim(0.8, {
    "right_arm": [(0, (-62, -14, 6), "easeInOutSine"), (0.2, (-74, -10, 4), "easeInOutSine"), (0.4, (-62, -14, 6), "easeInOutSine"),
                  (0.6, (-56, -16, 8), "easeInOutSine"), (0.8, (-62, -14, 6), "easeInOutSine")],
    "left_arm": [(0, (-62, 14, -6), "easeInOutSine"), (0.2, (-56, 16, -8), "easeInOutSine"), (0.4, (-62, 14, -6), "easeInOutSine"),
                 (0.6, (-74, 10, -4), "easeInOutSine"), (0.8, (-62, 14, -6), "easeInOutSine")],
    "head": [(0, (14, 0, 0), "linear"), (0.8, (14, 0, 0), "linear")],
}, loop=True)

# Puppet Creation, release: the strings are pulled taut, then the right hand flicks down and out to where the puppet stands.
A["puppet_create_release"] = anim(0.5, {
    "right_arm": [(0, (-70, -12, 6), "easeOutQuad"), (0.07, (-96, -4, 2), "easeOutQuad"), (0.18, (-30, 10, 40), "easeOutQuad"),
                  (0.32, (-24, 8, 36), "easeInOutSine"), (0.5, (0, 0, 0), "easeInOutSine")],
    "left_arm": [(0, (-70, 12, -6), "easeOutQuad"), (0.12, (-40, 10, -14), "easeOutQuad"), (0.5, (0, 0, 0), "easeInOutSine")],
    "torso": [(0, (0, 0, 0), "easeOutQuad"), (0.18, (0, -14, 0), "easeOutQuad"), (0.5, (0, 0, 0), "easeInOutSine")],
    "head": [(0, (14, 0, 0), "easeOutQuad"), (0.18, (8, -20, 0), "easeOutQuad"), (0.5, (0, 0, 0), "easeInOutSine")],
})

# Possess: two fingers to the temple, then the hand flung toward the puppet as the mind leaves.
A["possess_cast"] = anim(0.6, {
    "right_arm": [(0, (0, 0, 0), "easeOutQuad"), (0.12, (-150, -40, -20), "easeOutQuad"), (0.28, (-146, -42, -18), "linear"),
                  (0.38, (-92, 10, 8), "easeOutQuad"), (0.6, (0, 0, 0), "easeInOutSine")],
    "head": [(0, (0, 0, 0), "easeOutQuad"), (0.12, (10, -12, 0), "easeOutQuad"), (0.3, (14, -10, 0), "linear"),
             (0.38, (-8, 0, 0), "easeOutQuad"), (0.6, (0, 0, 0), "easeInOutSine")],
    "torso": [(0, (0, 0, 0), "easeOutQuad"), (0.38, (4, 6, 0), "easeOutQuad"), (0.6, (0, 0, 0), "easeInOutSine")],
})

# Returning to the body: a jolt (head snaps up, arms twitch out), then the body settles.
A["possess_return"] = anim(0.5, {
    "head": [(0, (24, 0, 0), "linear"), (0.08, (-16, 0, 0), "easeOutQuad"), (0.5, (0, 0, 0), "easeInOutSine")],
    "right_arm": [(0, (0, 0, 0), "linear"), (0.08, (-20, 0, 24), "easeOutQuad"), (0.5, (0, 0, 0), "easeInOutSine")],
    "left_arm": [(0, (0, 0, 0), "linear"), (0.08, (-20, 0, -24), "easeOutQuad"), (0.5, (0, 0, 0), "easeInOutSine")],
    "torso": [(0, (10, 0, 0), "linear"), (0.1, (-4, 0, 0), "easeOutQuad"), (0.5, (0, 0, 0), "easeInOutSine")],
})

OUT.write_text(json.dumps({"format_version": "1.8.0", "animations": A}, indent=2) + "\n")
print("wrote", OUT, "with", len(A), "animations")

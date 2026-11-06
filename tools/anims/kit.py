"""Kegare's player-animation authoring kit (Player Animation Library, PAL 1.1.6).

Animations are written pose-to-pose in Python (tools/anims/<technique>.py) and exported to
src/main/resources/assets/kegare/player_animations/<technique>.json in Blockbench's own Bedrock format
({"post": [x, y, z], "lerp_mode": "catmullrom"}), which PAL reads natively. Previews render the same data on a
Blockbench rig (tools/blockbench/) so every pose is checked visually before it ships.

Bones (PAL names; Blockbench pixels; the player faces -Z, its right is +X):
    body (pivot 0,12,0)        the whole player (moves the legs too)
    hip  (pivot 0,12,0)        VIRTUAL: baked into torso, head and both arms (their rotation and position). Use it
                               for upper-body twists, leans, bows and weight shifts that leave the legs planted. PAL
                               documents custom pivot bones, but a "hip" bone had no effect in game on 1.1.6 (with
                               model/parents at the file root and per animation alike), so it's baked instead.
    torso, head (0,24,0), right_arm (5,22,0), left_arm (-5,22,0), right_leg (2,12,0), left_leg (-2,12,0)
Conventions (checked against PAL in game): rotation +X swings what hangs below a pivot backward (arms forward
need -X), head -X looks up, position +Z is backward, +Y up. PAL's "bend" channel (elbows, knees) drew nothing in
game in any format tried, so limbs are rigid and it's never used.

How PAL combines an animation with the vanilla pose (checked in game): a channel the animation keyframes REPLACES
the vanilla value for its whole length; a channel it leaves out keeps the vanilla value. So:
- The head's rotation is left to the player's look unless an animation asks for it (look=False): casts that aim
  keep the head on the target. Its position still follows the hip.
- Legs are left out (walking and running carry on during a cast).
- Casts fade in from and out to whatever the player was doing (PAL's beginTick / endTick), instead of snapping to a
  rest pose: the first key sits at the end of the fade-in, the last key where the fade-out starts.
"""
from __future__ import annotations

import json
import math
from dataclasses import dataclass, field
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
OUT_DIR = ROOT / "src/main/resources/assets/kegare/player_animations"

PIVOTS = {
    "body": (0.0, 12.0, 0.0), "hip": (0.0, 12.0, 0.0), "torso": (0.0, 24.0, 0.0), "head": (0.0, 24.0, 0.0),
    "right_arm": (5.0, 22.0, 0.0), "left_arm": (-5.0, 22.0, 0.0), "right_leg": (2.0, 12.0, 0.0), "left_leg": (-2.0, 12.0, 0.0),
}
UPPER = ("torso", "head", "right_arm", "left_arm")  # what the virtual hip carries
BONES = ("body", "hip", "torso", "head", "right_arm", "left_arm", "right_leg", "left_leg")
ZERO = (0.0, 0.0, 0.0)


# ---------------------------------------------------------------------------------------------- rotation maths
# Blockbench shows a Bedrock bone rotation (x, y, z) and applies it to three.js as Euler(-x, -y, z) in ZYX order,
# i.e. M = Rz(z) * Ry(-y) * Rx(-x). Positions are applied as (-x, y, z).

def _rx(a):
    c, s = math.cos(a), math.sin(a)
    return [[1, 0, 0], [0, c, -s], [0, s, c]]


def _ry(a):
    c, s = math.cos(a), math.sin(a)
    return [[c, 0, s], [0, 1, 0], [-s, 0, c]]


def _rz(a):
    c, s = math.cos(a), math.sin(a)
    return [[c, -s, 0], [s, c, 0], [0, 0, 1]]


def _mul(a, b):
    return [[sum(a[i][k] * b[k][j] for k in range(3)) for j in range(3)] for i in range(3)]


def _apply(m, v):
    return tuple(sum(m[i][k] * v[k] for k in range(3)) for i in range(3))


def matrix(rot):
    x, y, z = (math.radians(v) for v in rot)
    return _mul(_mul(_rz(z), _ry(-y)), _rx(-x))


def euler(m):
    """Inverse of matrix(): three.js setFromRotationMatrix(m, 'ZYX'), back to Blockbench's displayed values."""
    m31 = max(-1.0, min(1.0, m[2][0]))
    ay = math.asin(-m31)
    if abs(m31) < 0.9999999:
        ax = math.atan2(m[2][1], m[2][2])
        az = math.atan2(m[1][0], m[0][0])
    else:
        ax, az = 0.0, math.atan2(-m[0][1], m[1][1])
    return (-math.degrees(ax), -math.degrees(ay), math.degrees(az))


def bake_hip(hip_rot, hip_pos, bone, rot, pos):
    """A bone carried by the virtual hip -> its own (rotation, position) with the hip folded in."""
    if hip_rot == ZERO and hip_pos == ZERO:
        return rot, pos
    h = matrix(hip_rot)
    new_rot = euler(_mul(h, matrix(rot)))
    hx, hy, hz = PIVOTS["hip"]
    px, py, pz = PIVOTS[bone]
    # scene space: displayed position (x, y, z) is (-x, y, z)
    d = (-pos[0], pos[1], pos[2])
    rel = (px + d[0] - hx, py + d[1] - hy, pz + d[2] - hz)
    moved = _apply(h, rel)
    hp = (-hip_pos[0], hip_pos[1], hip_pos[2])
    world = (hx + moved[0] + hp[0], hy + moved[1] + hp[1], hz + moved[2] + hp[2])
    off = (world[0] - px, world[1] - py, world[2] - pz)
    return new_rot, (-off[0], off[1], off[2])


# ---------------------------------------------------------------------------------------------- poses

def _vec(v):
    return tuple(float(x) for x in v) if v is not None else ZERO


@dataclass
class Pose:
    """A complete pose: every bone not given is at rest. Values in degrees / pixels."""
    rot: dict = field(default_factory=dict)
    pos: dict = field(default_factory=dict)

    def r(self, bone):
        return _vec(self.rot.get(bone))

    def p(self, bone):
        return _vec(self.pos.get(bone))

    def baked(self):
        """bone -> (rotation, position) for PAL's real bones, with the hip folded into the upper body."""
        out = {}
        hr, hp = self.r("hip"), self.p("hip")
        for b in BONES:
            if b == "hip":
                continue
            rot, pos = self.r(b), self.p(b)
            if b in UPPER:
                rot, pos = bake_hip(hr, hp, b, rot, pos)
            out[b] = (rot, pos)
        return out


def pose(**bones) -> Pose:
    """pose(right_arm=(-90, 0, 0), body_pos=(0, -1, 0), hip=(0, 20, 0), ...): '<bone>' is a rotation,
    '<bone>_pos' a position."""
    p = Pose()
    for k, v in bones.items():
        if k.endswith("_pos"):
            p.pos[k[:-4]] = _vec(v)
        else:
            p.rot[k] = _vec(v)
    return p


def blend(a: Pose, b: Pose, t: float) -> Pose:
    """Linear mix of two poses (for building in-betweens by hand)."""
    out = Pose()
    for bone in set(a.rot) | set(b.rot):
        out.rot[bone] = tuple(x + (y - x) * t for x, y in zip(a.r(bone), b.r(bone)))
    for bone in set(a.pos) | set(b.pos):
        out.pos[bone] = tuple(x + (y - x) * t for x, y in zip(a.p(bone), b.p(bone)))
    return out


def merge(base: Pose, **bones) -> Pose:
    """A copy of base with some bones replaced."""
    out = Pose(dict(base.rot), dict(base.pos))
    over = pose(**bones)
    out.rot.update(over.rot)
    out.pos.update(over.pos)
    return out


REST = Pose()


# ---------------------------------------------------------------------------------------------- animations

@dataclass
class Key:
    t: float
    pose: Pose
    lerp: str


class Anim:
    """Pose-to-pose animation. Keys are complete poses; between them PAL interpolates each bone with the key's
    lerp mode ('catmullrom' = smooth arcs through the poses, 'linear' = straight, constant speed)."""

    def __init__(self, name: str, length: float, loop: bool = False, lerp: str = "catmullrom", note: str = "",
                 look: bool = True, fade_in: int = 0, fade_out: int = 0, loop_from: float | None = None):
        """
        :param look: leave the head's rotation to the player's look (its position still follows the hip)
        :param fade_in: ticks to fade in from the current (vanilla) pose; the first key must sit at fade_in / 20 s
        :param fade_out: ticks to fade back to the vanilla pose after the last key; the length is last key + fade
        :param loop_from: loop back to this time (seconds) instead of the start (an intro, then the loop)
        """
        self.name, self.length, self.loop, self.lerp, self.note = name, length, loop, lerp, note
        self.look, self.fade_in, self.fade_out, self.loop_from = look, fade_in, fade_out, loop_from
        self.keys: list[Key] = []

    def key(self, t: float, p: Pose, lerp: str | None = None) -> "Anim":
        self.keys.append(Key(round(t, 4), p, lerp or self.lerp))
        self.keys.sort(key=lambda k: k.t)
        return self

    def to_pal(self) -> dict:
        """PAL / Blockbench Bedrock JSON for this animation. Bones that never move are left out (so the vanilla
        pose shows through); a bone that moves gets a keyframe at every key."""
        baked = [(k, k.pose.baked()) for k in self.keys]
        bones = {}
        for b in BONES:
            if b == "hip":
                continue
            for chan, idx in (("rotation", 0), ("position", 1)):
                if b == "head" and chan == "rotation" and self.look and not any(k.pose.rot.get("head") for k in self.keys):
                    continue  # the head keeps looking where the player looks
                values = [bk[b][idx] for _, bk in baked]
                if all(all(abs(x) < 1e-6 for x in v) for v in values):
                    continue
                frames = {}
                for (k, bk) in baked:
                    v = bk[b][idx]
                    frames[f"{k.t:g}" if k.t else "0.0"] = {"post": [round(x, 3) for x in v], "lerp_mode": k.lerp}
                bones.setdefault(b, {})[chan] = frames
        out = {"animation_length": self.length, "loop": self.loop, "bones": bones}
        if self.loop_from is not None:
            out["loopTick"] = self.loop_from  # seconds, despite the name
        extra = {}
        if self.fade_in:
            extra["beginTick"] = self.fade_in
        if self.fade_out:
            extra["endTick"] = round(self.keys[-1].t * 20)
        if extra:
            out["player_animation_library"] = extra
        return out

    def to_preview(self) -> dict:
        """The same animation for Blockbench, which knows no fades: rest keys stand in for them."""
        pal = self.to_pal()
        for chans in pal["bones"].values():
            for frames in chans.values():
                if self.fade_in and "0.0" not in frames:
                    frames["0.0"] = {"post": [0, 0, 0], "lerp_mode": "catmullrom"}
                if self.fade_out:
                    frames[f"{self.length:g}"] = {"post": [0, 0, 0], "lerp_mode": "catmullrom"}
        pal.pop("player_animation_library", None)
        pal.pop("loopTick", None)
        return pal

    def check(self):
        """Basic sanity: starts and (unless looping) ends at rest; keys inside the length."""
        problems = []
        if not self.keys:
            return ["no keys"]
        first, last = self.keys[0], self.keys[-1]
        if abs(first.t - self.fade_in / 20) > 1e-6:
            problems.append(f"first key must sit at the end of the fade-in ({self.fade_in / 20}s), not {first.t}s")
        if last.t > self.length + 1e-6:
            problems.append("key past the end")
        if self.fade_out and abs(self.length - (last.t + self.fade_out / 20)) > 1e-6:
            problems.append(f"length must be the last key + the fade-out ({last.t + self.fade_out / 20}s)")
        if self.loop and self.loop_from is not None:
            a = next((k for k in self.keys if abs(k.t - self.loop_from) < 1e-6), None)
            if a is None:
                problems.append("loop_from must be a key time")
            elif a.pose.baked() != last.pose.baked() or abs(last.t - self.length) > 1e-6:
                problems.append("a loop's last key (at the length) must repeat the pose at loop_from")
        return problems


def write(file_stem: str, anims: list[Anim], extra: dict | None = None) -> Path:
    """Writes OUT_DIR/<file_stem>.json with every animation; extra maps names to hand-written PAL animations."""
    doc = {"format_version": "1.8.0", "animations": {}}
    for a in anims:
        bad = a.check()
        if bad:
            raise ValueError(f"{a.name}: " + "; ".join(bad))
        doc["animations"][a.name] = a.to_pal()
    for name, pal in (extra or {}).items():
        doc["animations"][name] = pal
    path = OUT_DIR / f"{file_stem}.json"
    path.write_text(json.dumps(doc, indent=1) + "\n")
    return path

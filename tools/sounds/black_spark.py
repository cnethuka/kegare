"""Black Spark's crack (DESIGN §3.28): one sharp moment. A hard snap of the air at the point of contact, a quick spray of black crackle
and, under it, the body taking it. Original synthesis, nothing sampled.

SOUNDS maps the file it ships as (assets/kegare/sounds/black_spark/<name>.ogg) to how it's made. CANDIDATES are the versions made for
choosing (run/sounds_preview/black_spark/, with --preview); the first ships.
"""
from __future__ import annotations

import random

from impact import _crack, _thud, deep_finish
from synth import RATE, bandpass, bursts, delay, highpass, mix, mul, noise, room, tone


def _crackle(seconds: float, seed: int, start: float, length: float, density: int, centre: float = 3400.0, grain: float = 0.0009) -> list[float]:
    """Electric crackle: a dense scatter of tiny bright snaps over {length}, thinning out."""
    n = int(seconds * RATE)
    rng = random.Random(seed)
    times = sorted(start + length * rng.random() ** 1.8 for _ in range(density))
    levels = [0.25 + 0.75 * rng.random() for _ in times]
    return mul(highpass(bandpass(noise(seconds, seed + 7), centre, 0.7), 1200), bursts(n, times, grain, levels=levels))


def crack_sharp() -> list[float]:
    """1: sharp. A hard snap right at the hit, a short spray of crackle off it, and the body's thump under both."""
    s = 0.7
    snap = mix((1.0, _crack(s, 101, 4200, length=0.0025, level=1.0)), (0.7, _crack(s, 103, 1800, length=0.006)))
    crackle = _crackle(s, 105, 0.004, 0.16, 60)
    body = _thud(s, 72, 0.08, sweep=0.5, seed=107, body=0.6)
    return deep_finish(room(mix((1.0, snap), (0.7, crackle), (0.75, body)), 0.5, 0.07), 0.95)


def crack_double() -> list[float]:
    """2: double. Two snaps a hair apart (the hit, then the Malice), drier, with less crackle."""
    s = 0.6
    first = _crack(s, 111, 3600, length=0.003)
    second = delay(_crack(0.6, 113, 5200, length=0.002, level=1.0), 0.011)
    crackle = _crackle(s, 115, 0.012, 0.08, 28, centre=4200)
    body = _thud(s, 80, 0.06, sweep=0.45, seed=117, body=0.5)
    return deep_finish(room(mix((0.8, first), (1.0, second), (0.5, crackle), (0.6, body)), 0.4, 0.05), 0.95)


def crack_split() -> list[float]:
    """3: split. The snap with a falling tear through it, as if the air split, and a longer crackle dying away."""
    s = 0.9
    snap = _crack(s, 121, 3000, length=0.003)
    tear = mul(tone(s, 2600, 0.05, sweep=0.7), [1.0] * int(s * RATE))
    crackle = _crackle(s, 123, 0.0, 0.3, 80, centre=2800, grain=0.0012)
    body = _thud(s, 64, 0.1, sweep=0.5, seed=127, body=0.7)
    return deep_finish(room(mix((1.0, snap), (0.35, tear), (0.6, crackle), (0.8, body)), 0.7, 0.09), 0.95)


SOUNDS = {"crack": crack_sharp}
CANDIDATES = {"1_sharp": crack_sharp, "2_double": crack_double, "3_split": crack_split}

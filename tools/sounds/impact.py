"""The Impact System's hit sound (DESIGN §3.23), played with vanilla's own hurt sound, never instead of it. A plain hit sounds like
vanilla only.

- boom: big hits of any kind (Heavy and up), underneath vanilla's: deep, and played louder and lower as the tier grows.

It has three versions (CANDIDATES, written to run/sounds_preview/impact/ with --preview); the first ships.
Curio Burst's thump is in puppet_manipulation.py. Everything is made here from noise and tones: no recordings.
"""
from __future__ import annotations

import math
import random

from synth import RATE, bandpass, bursts, fade_tail, highpass, lowpass, mix, mul, noise, normalize, room, tone, trim


def deep_finish(x: list[float], peak: float = 0.89) -> list[float]:
    """As synth.finish, but keeping the lows: its 120 Hz highpass would take a boom's body away. Only rumble below 30 Hz goes."""
    return fade_tail(normalize(trim(highpass(x, 30.0)), peak))


def _thud(seconds: float, freq: float, decay: float, sweep: float = 0.45, seed: int = 1, body: float = 0.6) -> list[float]:
    """A body taking a hit: a low sine that drops in pitch as it dies, and a puff of muffled noise for the flesh of it."""
    n = int(seconds * RATE)
    low = tone(seconds, freq, decay, sweep=sweep)
    flesh = mul(lowpass(noise(seconds, seed), 420), bursts(n, [0.0], decay * 0.45))
    return mix((1.0, low), (body, flesh))


def _crack(seconds: float, seed: int, centre: float, length: float = 0.004, level: float = 1.0) -> list[float]:
    """A crack: a few milliseconds of bright noise, starting hard."""
    n = int(seconds * RATE)
    return mul(highpass(bandpass(noise(seconds, seed), centre, 0.8), 700), bursts(n, [0.0], length, levels=[level]))


def _swish(seconds: float, seed: int, length: float, low: float, high: float) -> list[float]:
    """Air cut fast: noise swelling and falling away, its pitch rising through it (three bands, each a little later and higher).
    Severed's sounds use it (DESIGN §3.27)."""
    n = int(seconds * RATE)
    out = [0.0] * n
    for k, f in enumerate((low, math.sqrt(low * high), high)):
        band = bandpass(noise(seconds, seed + k), f, 2.2)
        peak = length * (0.35 + 0.22 * k)
        env = [0.0] * n
        for i in range(n):
            t = i / RATE
            if t < peak:
                env[i] = (t / peak) ** 2
            else:
                env[i] = math.exp(-(t - peak) / (length * 0.22))
        out = mix((1.0, out), (0.8, mul(band, env)))
    return out


def _crunch(seconds: float, seed: int, start: float, spread: float, count: int, centre: float) -> list[float]:
    """Something giving way: a fast scatter of tiny hard clicks, crowded at the front. Severed's sounds use it (DESIGN §3.27)."""
    n = int(seconds * RATE)
    rng = random.Random(seed)
    times = sorted(start + spread * rng.random() ** 2.2 for _ in range(count))
    levels = [0.5 + 0.5 * rng.random() for _ in times]
    return mul(bandpass(noise(seconds, seed + 7), centre, 1.1), bursts(n, times, 0.0025, levels=levels))


def _rumble(seconds: float, seed: int, decay: float, cutoff: float = 140.0) -> list[float]:
    n = int(seconds * RATE)
    return mul(lowpass(lowpass(noise(seconds, seed), cutoff), cutoff), bursts(n, [0.0], decay))


# ---------------------------------------------------------------- boom

def boom_deep() -> list[float]:
    """Deep and round: a 48 Hz drop with a rumble under it, a soft knock at the front."""
    s = 1.1
    return deep_finish(room(mix((1.0, tone(s, 48, 0.32, sweep=0.35)), (0.6, _rumble(s, 101, 0.28)),
                                (0.35, _thud(s, 140, 0.03, seed=102))), 0.9, 0.18))


def boom_rumble() -> list[float]:
    """More rumble than tone: like something far bigger landing, rolling on longer."""
    s = 1.4
    return deep_finish(room(mix((0.7, tone(s, 42, 0.4, sweep=0.3)), (1.0, _rumble(s, 111, 0.45, 110)),
                                (0.3, _thud(s, 120, 0.04, seed=112))), 1.0, 0.22))


def boom_punchy() -> list[float]:
    """Shorter, with a hard click to start it: it hits rather than rolls."""
    s = 0.8
    return deep_finish(room(mix((1.0, tone(s, 55, 0.2, sweep=0.5)), (0.5, _rumble(s, 121, 0.16)),
                                (0.5, _crack(s, 122, 1800, length=0.003))), 0.8, 0.14))


CANDIDATES = {"boom_1_deep": boom_deep, "boom_2_rumble": boom_rumble, "boom_3_punchy": boom_punchy}

# The first ships.
SOUNDS = {"boom": boom_deep}

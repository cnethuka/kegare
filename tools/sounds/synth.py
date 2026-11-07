"""Small synthesis kit for Kegare's sounds: seeded noise, RBJ biquad filters, envelopes, a short room. Pure Python, mono,
44.1 kHz floats in [-1, 1]. Every sound is made here from nothing: no recordings, no samples from anywhere."""
from __future__ import annotations

import math
import random

RATE = 44100


def silence(seconds: float) -> list[float]:
    return [0.0] * int(round(seconds * RATE))


def noise(seconds: float, seed: int) -> list[float]:
    rng = random.Random(seed)
    return [rng.uniform(-1.0, 1.0) for _ in range(int(round(seconds * RATE)))]


def _biquad(x: list[float], b0, b1, b2, a0, a1, a2) -> list[float]:
    b0, b1, b2, a1, a2 = b0 / a0, b1 / a0, b2 / a0, a1 / a0, a2 / a0
    y = [0.0] * len(x)
    x1 = x2 = y1 = y2 = 0.0
    for i, v in enumerate(x):
        out = b0 * v + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
        x2, x1, y2, y1 = x1, v, y1, out
        y[i] = out
    return y


def bandpass(x: list[float], freq: float, q: float) -> list[float]:
    """Constant 0 dB peak gain band-pass (RBJ)."""
    w = 2 * math.pi * freq / RATE
    alpha = math.sin(w) / (2 * q)
    return _biquad(x, alpha, 0.0, -alpha, 1 + alpha, -2 * math.cos(w), 1 - alpha)


def highpass(x: list[float], freq: float, q: float = 0.707) -> list[float]:
    w = 2 * math.pi * freq / RATE
    alpha, c = math.sin(w) / (2 * q), math.cos(w)
    return _biquad(x, (1 + c) / 2, -(1 + c), (1 + c) / 2, 1 + alpha, -2 * c, 1 - alpha)


def lowpass(x: list[float], freq: float, q: float = 0.707) -> list[float]:
    w = 2 * math.pi * freq / RATE
    alpha, c = math.sin(w) / (2 * q), math.cos(w)
    return _biquad(x, (1 - c) / 2, 1 - c, (1 - c) / 2, 1 + alpha, -2 * c, 1 - alpha)


def bursts(n: int, times: list[float], decay: float, tail_at: float | None = None, tail_decay: float = 0.05,
           tail_level: float = 0.5, levels: list[float] | None = None) -> list[float]:
    """An envelope of sharp hits: each starts instantly at its time (s) and dies away with time constant {decay};
    an optional tail from {tail_at} dies away with {tail_decay}. Hits add, then the whole is clipped to 1."""
    env = [0.0] * n
    levels = levels or [1.0] * len(times)
    for t0, lv in zip(times, levels):
        s = int(t0 * RATE)
        for i in range(s, n):
            v = lv * math.exp(-(i - s) / (decay * RATE))
            if v < 1e-4:
                break
            env[i] += v
    if tail_at is not None:
        s = int(tail_at * RATE)
        for i in range(s, n):
            env[i] += tail_level * math.exp(-(i - s) / (tail_decay * RATE))
    return [min(1.0, v) for v in env]


def tone(seconds: float, freq: float, decay: float, start: float = 0.0, sweep: float = 0.0) -> list[float]:
    """A sine that starts at {start} and dies away (time constant {decay}); {sweep} bends its pitch down by that ratio."""
    n = int(round(seconds * RATE))
    out = [0.0] * n
    phase = 0.0
    s = int(start * RATE)
    for i in range(s, n):
        t = (i - s) / RATE
        f = freq * (1.0 - sweep * (1 - math.exp(-t / max(decay, 1e-4))))
        phase += 2 * math.pi * f / RATE
        out[i] = math.sin(phase) * math.exp(-t / decay)
    return out


def mul(a: list[float], b: list[float]) -> list[float]:
    return [x * y for x, y in zip(a, b)]


def mix(*layers: tuple[float, list[float]]) -> list[float]:
    """Sum of (gain, signal) pairs, as long as the longest."""
    n = max(len(s) for _, s in layers)
    out = [0.0] * n
    for g, s in layers:
        for i, v in enumerate(s):
            out[i] += g * v
    return out


def delay(x: list[float], seconds: float) -> list[float]:
    return [0.0] * int(round(seconds * RATE)) + x


def room(x: list[float], size: float = 1.0, wet: float = 0.18, damp: float = 3500.0) -> list[float]:
    """A short, dry room: four feedback combs into an all-pass, low-passed, mixed under the dry signal."""
    combs = [0.0297, 0.0371, 0.0411, 0.0437]
    tail = int(0.35 * size * RATE)
    src = x + [0.0] * tail
    acc = [0.0] * len(src)
    for d in combs:
        k = int(d * size * RATE)
        g = 0.72 if size >= 1 else 0.6
        buf = [0.0] * len(src)
        for i in range(len(src)):
            buf[i] = src[i] + (g * buf[i - k] if i >= k else 0.0)
            acc[i] += buf[i] * 0.25
    k, g = int(0.005 * RATE), 0.5
    ap = [0.0] * len(acc)
    for i in range(len(acc)):
        prev = ap[i - k] if i >= k else 0.0
        ap[i] = -g * acc[i] + (acc[i - k] if i >= k else 0.0) + g * prev
    ap = lowpass(ap, damp)
    return [d + wet * w for d, w in zip(src, ap)]


def fade_tail(x: list[float], seconds: float = 0.02) -> list[float]:
    n = min(len(x), int(seconds * RATE))
    out = list(x)
    for i in range(n):
        out[len(x) - n + i] *= 1 - i / n
    return out


def trim(x: list[float], floor: float = 2e-4) -> list[float]:
    """Drops the silent end."""
    end = len(x)
    while end > 1 and abs(x[end - 1]) < floor:
        end -= 1
    return x[:end]


def normalize(x: list[float], peak: float = 0.89) -> list[float]:
    top = max(abs(v) for v in x) or 1.0
    return [v * peak / top for v in x]


def finish(x: list[float], peak: float = 0.89) -> list[float]:
    return fade_tail(normalize(trim(highpass(x, 120.0)), peak))

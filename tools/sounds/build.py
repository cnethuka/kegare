"""Builds Kegare's sounds: python3 tools/sounds/build.py [--preview]

    writes src/main/resources/assets/kegare/sounds/<technique>/<name>.ogg (mono OGG Vorbis, 44.1 kHz)
    --preview also writes the candidates to choose from (Swap's clap to run/sounds_preview/clap/, Curio Burst's burst to
              run/sounds_preview/puppet_manipulation/, the hit sounds and Curio Burst's thump to run/sounds_preview/impact/,
              Time Manipulation's click, tick, stop and resume to run/sounds_preview/time_manipulation/),
              with a .wav of each to open anywhere

A new sound: add a recipe to the technique's module (SOUNDS), run this, add its SoundEvent to KegareSounds and its subtitle to
KegareLanguageProvider, then runData (sounds.json is generated). SoundsTest checks that the four agree and that every file is mono.
"""
import sys
import wave
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
import impact  # noqa: E402
import ogg  # noqa: E402
import puppet_manipulation  # noqa: E402
import swap  # noqa: E402
import time_manipulation  # noqa: E402
from synth import RATE  # noqa: E402

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "src/main/resources/assets/kegare/sounds"
TECHNIQUES = {"swap": swap.SOUNDS, "puppet_manipulation": puppet_manipulation.SOUNDS, "impact": impact.SOUNDS,
              "time_manipulation": time_manipulation.SOUNDS}
PREVIEWS = {"clap": swap.CLAP_CANDIDATES, "puppet_manipulation": puppet_manipulation.BURST_CANDIDATES,
            "impact": {**impact.CANDIDATES, **puppet_manipulation.THUMP_CANDIDATES},
            "time_manipulation": time_manipulation.CANDIDATES}


def wav(path: Path, samples: list[float]) -> None:
    with wave.open(str(path), "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(RATE)
        w.writeframes(b"".join(int(max(-1, min(1, s)) * 32767).to_bytes(2, "little", signed=True) for s in samples))


def main(argv):
    for technique, sounds in TECHNIQUES.items():
        for name, make in sounds.items():
            path = OUT / technique / f"{name}.ogg"
            samples = make()
            ogg.write(path, samples, RATE)
            print(f"wrote {path.relative_to(ROOT)} ({len(samples) / RATE:.2f} s, {ogg.channels(path)} channel)")
    if "--preview" in argv:
        for folder, candidates in PREVIEWS.items():
            out = ROOT / "run/sounds_preview" / folder
            out.mkdir(parents=True, exist_ok=True)
            for name, make in candidates.items():
                samples = make()
                ogg.write(out / f"{name}.ogg", samples, RATE)
                wav(out / f"{name}.wav", samples)
                print(f"preview {out.relative_to(ROOT)}/{name}.ogg ({len(samples) / RATE:.2f} s)")


if __name__ == "__main__":
    main(sys.argv[1:])

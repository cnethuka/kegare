> Edit CLAUDE.md only, then copy it over AGENTS.md and AGENT.md (`cp CLAUDE.md AGENTS.md && cp CLAUDE.md AGENT.md`). `AgentFilesTest` fails if they differ.

# Kegare: notes for coding agents

## What this is
- An unofficial, fan-made Jujutsu Kaisen mod, built for PvP. NeoForge 21.1.x, Minecraft 1.21.1, Java 21, ModDevGradle.
  Mod id `kegare`, package `com.cnethuka.kegare`, branch `main`, remote `origin`.
- **The task you're given is the spec.** Do what it asks, name any small extra you add, and nothing more. If it conflicts
  with DESIGN.md, say so in your report instead of guessing.

## Where things live
- `DESIGN.md` is the source of truth for every system. Main sections: §1 Naming rule and secret identities, §2 Data model,
  §3 Systems (§3.1 Malice, §3.2 Traits, §3.3 Innate technique framework, §3.4 Rolls, §3.5 Input and networking, §3.6 HUD,
  §3.8 Commands, §3.9 Config, §3.10 Data generation and art, §3.11 Advancements, §3.12 GUIs, §3.13 Animations and VFX,
  §3.14 Rime, §3.15 Specials and the tell rule, §3.16 Loadout, §3.17-3.18 RMT, §3.19 Swap, §3.20 Puppet Manipulation,
  §3.21 Progression, §3.22 Melee), §4 Extension points, §5 Future. Read the section before touching a system; update it in the
  same commit when behaviour or visuals change.
- `README.md`: required mods, content list, dev commands and keys.
- `tools/anims/*.py` → `assets/kegare/player_animations/*.json` (`python3 tools/anims/build.py [--preview names]`).
- `tools/sounds/*.py` → `assets/kegare/sounds/**/*.ogg` (`python3 tools/sounds/build.py [--preview]`), registered in
  `sound/KegareSounds` (DESIGN §3.10).
- `tools/blockbench/`: Python model builders (`build_*.py`), block marks, Blockbench setup and the per-asset notes in its README.
- `art/blockbench/`: `.bbmodel` projects. `tools/gen_art.py`: GUI and item art.
- Player-facing text: `data/KegareLanguageProvider.java` → `src/generated/.../en_us.json` (never hand-edit generated files).
- Network version: `network/KegareNetwork.VERSION`, documented in DESIGN §3.5.

## Build, test, data
- Gradle needs JDK 21: point `JAVA_HOME` at one.
- Compile: `./gradlew compileJava -q`. Full suite: `./gradlew test` (JUnit runs inside FML on a throwaway server).
- Data: `./gradlew runData` after any lang, advancement, tag or config-option change. Run it and `test` as separate Gradle
  invocations. Afterwards `git status` must show no changes from runData.
- Jar: `./gradlew build -x test` → `build/libs/kegare-<version>.jar`.

## Workflow
- Commit each working step as you finish it, not all at the end. Each commit builds and passes tests on its own. End every
  commit message with the co-author line your harness gives.
- Push to `origin main` only after the full test suite passes. Never force push. If a push is refused for lack of access, stop and
  say so; never look for other credentials.
- runData must leave no changes. Rebuild the jar at the end of a task.
- Bump the network version when a payload format changes (one place, DESIGN §3.5) and say so in the report.
- Report honestly what you tested, and say what you couldn't see, hear or check. Don't rely on what you couldn't verify.
- When a prompt says STOP and report, stop.
- Ask before adding dependencies or installing anything.

## Visual rules
- Models and animations come from Python scripts or are hand-edited in Blockbench, chosen per asset, and each asset has exactly
  one source. A Python-built file is never hand-edited: the next build overwrites it. A hand-edited one never has its build
  script re-run, which would overwrite the edits. Hand-edited today: Strings' `art/blockbench/string_segment.bbmodel`,
  `string_knot.bbmodel` and `string_animations.bbmodel` (DESIGN §3.20, `tools/blockbench/README.md`). Everything else is
  Python. A new asset says which it is.
- Marks painted on blocks are pixel art at 16 pixels per block with hard pixels (per-pixel glow is fine). A technique's own
  objects (lances, shards, spikes, plates) are smooth: meshes, high-res opaque textures and Photon glow. Fist flames stay
  pixel style.
- No antialiasing or soft alpha on low-res textures.
- Every effect keeps working with the Reduced and Vanilla effect settings.

## Design rules
- Server-authoritative. Gameplay numbers go in `kegare-server.toml` (DESIGN §3.9).
- Instant Cast is the only control scheme, in Technique Mode (G): a number key casts, hold R + a number for the special, hold
  the number key to charge or channel, V for the ultimate. Right-click is always vanilla and never casts (DESIGN §3.5).
- Every special has a tell other players can see or hear at the moment it's cast (DESIGN §3.15 and its tell table).
- Bosses (`c:bosses`) take damage but are never moved, swapped or disabled.
- Code IDs match display names. Player-facing text uses display names, never raw IDs.

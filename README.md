# Kegare

Cursed-energy PvP framework for NeoForge 21.1.x (Minecraft 1.21.1, Java 21). Players are born with **Malice**
(cursed energy) and an **Innate Technique**, or are born **Severed**. "Kegare" is the mod name, and is reserved
for the future hostile spirit mobs. See [DESIGN.md](DESIGN.md).

## Required mods (everyone installs these)

Every player **and** the server need these in their `mods` folder, for Minecraft 1.21.1 / NeoForge 21.1:

| Mod | Version | Download | Why |
|---|---|---|---|
| Player Animation Library (by ZigyTheBird, *not* KosmX's playerAnimator) | 1.1.6 or newer | [Modrinth](https://modrinth.com/mod/player-animation-library) | player animations |
| Photon (by KilaBash) | 2.2.10 or newer | [Modrinth](https://modrinth.com/mod/photon-editor) | ability visual effects |
| LDLib2 (by KilaBash) | 2.2.45 or newer | [Modrinth](https://modrinth.com/mod/ldlib) | required by Photon |

KilaGraph, which Photon needs, is bundled inside Photon. Don't install it separately. Pick the NeoForge 1.21.1
file of each one. Minecraft refuses to start and names the missing mod if one is absent.

## Content

- **Rime** (Rare): Frost Shard, Glacial Lance, Frost Field and Rime Shell; Freeze stacks; cheaper in the cold. See
  DESIGN.md §3.14.
- Test innate techniques (`[Test] …`) stay available while `roll.includeTestInnateTechniques = true`.

## Dev commands

| Command | What it does |
|---|---|
| `./gradlew build` | Compile, run unit tests, build `build/libs/kegare-<version>.jar` |
| `./gradlew test` | Unit tests (roll/pity math, trait rules, lang keys) plus in-game tests on a throwaway server (e.g. no reroll path changes Malice capacity) |
| `./gradlew runData` | Regenerate lang, models, tags, recipes, advancements into `src/generated/resources` |
| `./gradlew runClient` | Dev client |
| `./gradlew runClient -PquickPlayWorld=<world>` | Dev client, straight into an existing singleplayer world |
| `./gradlew runClient -PquickPlay=localhost` | Dev client, straight onto a server |
| `./gradlew runServer` | Dev dedicated server (`run/eula.txt` must say `eula=true`) |
| `/kegarefx export` (dev client only) | Rebuild Rime's Photon effects into `assets/kegare/fx/rime/` from `RimeFxAuthoring`; `/kegarefx play <name>` previews one |
| `python3 tools/gen_art.py [--preview sheet.png]` | Regenerate all GUI and item art (needs Pillow); see DESIGN.md §3.10 |

Requires JDK 21 (e.g. Temurin). Point `JAVA_HOME` at it if it isn't your default Java.

Keys: **C** cast, **R** special, **V** ultimate, **G** Technique Mode (shows the combat panel; then number keys 1–N
pick abilities while the scroll wheel stays on the hotbar, or the other way round via Sorcerer screen → Settings, and
right-click also casts), **K** Sorcerer screen. See DESIGN.md §3.5.

Admin/debug commands (op): `/kegare info`, `setmalice`, `setcapacity`, `setinnatetechnique`,
`reroll [innate_technique]`, `setpity`, `trait add|remove|release|status`, `rebirth [capacity]`, `stun`.
The optional arguments force a result, which is handy for testing the reveal screens. See DESIGN.md §3.8.

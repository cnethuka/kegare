# Kegare

Kegare is a Jujutsu Kaisen-inspired Minecraft mod, built mainly for PvP with friends. It runs on NeoForge 21.1.x
(Minecraft 1.21.1, Java 21).

Players are born with **Malice** (cursed energy) and an **Innate Technique**, or are born **Severed**, with neither.
"Kegare" is the mod's name, and is reserved for the future hostile spirit mobs. See [DESIGN.md](DESIGN.md).

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

- **Malice:** rolled once at birth into a tier (Zero, Low, Average, High or Vast) and fixed from then on. See DESIGN.md §3.1.
- **Innate techniques**, rolled by rarity and rerolled with a Purification Talisman. Each ability on the bar also has a
  special on **R**:
  - **Swap** (Common): trade places, faces, negative effects and momentum with whoever you're looking at. See DESIGN.md §3.19.
  - **Rime** (Rare): Frost Shard, Glacial Lance, Frost Field and Rime Shell; Freeze stacks; cheaper in the cold. See
    DESIGN.md §3.14.
  - **Puppet Manipulation** (Epic): half-size puppets of yourself to command, possess, detonate or use as a double, plus
    Strings to pull and tie targets. See DESIGN.md §3.20.
- **Loadout:** nine ability slots and a fixed ultimate. See DESIGN.md §3.16.
- **Progression:** a private Grade ladder earned mostly in PvP, and promoted by how well you use your technique rather than by
  winning; Refinement, which unlocks a technique's abilities as you land them; and Insight from first-time encounters. It
  changes no damage, costs or capacity. See DESIGN.md §3.21.
- **Reverse Malice Technique:** a shared healing ability, found through a green orb only its finder can see. See
  DESIGN.md §3.17–3.18.
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
| `/kegare debug fx export` (dev client only) | Rebuild every technique's Photon effects into `assets/kegare/fx/` from the authoring classes (`RimeFxAuthoring`, `MeleeFxAuthoring`, …); `/kegare debug fx play <name>` previews one |
| `python3 tools/gen_art.py [--preview sheet.png]` | Regenerate all GUI and item art (needs Pillow); see DESIGN.md §3.10 |

Requires JDK 21 (e.g. Temurin). Point `JAVA_HOME` at it if it isn't your default Java.

Keys: **G** Technique Mode (shows the combat panel). In it, a number key casts that slot (hold to charge or channel), **R** +
number key casts its special, **R** alone casts the selected ability's special, and **V** is the ultimate. The scroll wheel stays on
the hotbar. Out of the mode, number keys are the hotbar and nothing casts. **K** Sorcerer screen. Right-click is always vanilla.
See DESIGN.md §3.5.

Commands read `/kegare <system> <action> [player] [value]` and take short names (`/kegare innate set Steve rime`).
`/kegare help` lists what you can run: `info`, `innate`, `trait`, `malice`, `rebirth`, `loadout`, `grade`, `assessment`,
`refinement`, `insight`, `progression`, and `debug` for anything that exists only for testing. All but `help` need
permission level 2. See DESIGN.md §3.8.

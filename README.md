# Kegare

Cursed-energy PvP framework for NeoForge 21.1.x (Minecraft 1.21.1, Java 21). Players are born with **Malice**
(cursed energy) and an **Innate Technique**, or are born **Severed**. "Kegare" is the mod name, and is reserved
for the future hostile spirit mobs. See [DESIGN.md](DESIGN.md).

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
| `python3 tools/gen_art.py [--preview sheet.png]` | Regenerate all GUI and item art (needs Pillow); see DESIGN.md §3.10 |

Requires JDK 21 (e.g. Temurin). Point `JAVA_HOME` at it if it isn't your default Java.

Keys: cast **R**, ultimate **V**, next ability **Z** (tap to cycle, hold for the radial menu), previous **X**,
Sorcerer screen **K**.

Admin/debug commands (op): `/kegare info`, `setmalice`, `setcapacity`, `setinnatetechnique`,
`reroll [innate_technique]`, `setpity`, `trait add|remove|release|status`, `rebirth [capacity]`, `stun`.
The optional arguments force a result, which is handy for testing the reveal screens. See DESIGN.md §3.8.

# Kegare

Cursed-energy PvP framework for NeoForge 21.1.x (Minecraft 1.21.1, Java 21). Players are born with **Malice**
(cursed energy) and an **Innate Technique**, or are born **Severed**. "Kegare" is the mod name, and is reserved
for the future hostile spirit mobs. See [DESIGN.md](DESIGN.md).

## Dev commands

| Command | What it does |
|---|---|
| `./gradlew build` | Compile, run unit tests, build `build/libs/kegare-<version>.jar` |
| `./gradlew test` | Unit tests (roll/pity math, trait rules, lang keys) |
| `./gradlew runData` | Regenerate lang, models, tags, recipes, advancements into `src/generated/resources` |
| `./gradlew runClient` | Dev client |
| `./gradlew runClient -PquickPlayWorld=<world>` | Dev client, straight into an existing singleplayer world |
| `./gradlew runClient -PquickPlay=localhost` | Dev client, straight onto a server |
| `./gradlew runServer` | Dev dedicated server (`run/eula.txt` must say `eula=true`) |
| `python3 tools/gen_placeholders.py` | Regenerate the placeholder textures (needs Pillow) |

Requires JDK 21 (e.g. Temurin). Point `JAVA_HOME` at it if it isn't your default Java.

Admin/debug commands (op): `/kegare info`, `setmalice`, `setcapacity`, `setinnatetechnique`, `reroll`, `setpity`,
`trait add|remove|release|status`, `rebirth`, `stun`. See DESIGN.md §3.8.

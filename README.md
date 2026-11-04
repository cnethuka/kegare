# Kegare

Cursed-energy PvP framework for NeoForge 21.1.x (Minecraft 1.21.1, Java 21). See [DESIGN.md](DESIGN.md).

## Dev commands

| Command | What it does |
|---|---|
| `./gradlew build` | Compile, run unit tests, build `build/libs/kegare-<version>.jar` |
| `./gradlew test` | Unit tests for the roll/pity math |
| `./gradlew runData` | Regenerate lang, models, tags, recipes, advancements into `src/generated/resources` |
| `./gradlew runClient` | Dev client |
| `./gradlew runClient -PquickPlayWorld=<world>` | Dev client, straight into an existing singleplayer world |
| `./gradlew runClient -PquickPlay=localhost` | Dev client, straight onto a server |
| `./gradlew runServer` | Dev dedicated server (accept the Minecraft EULA in `run/eula.txt` first) |
| `python3 tools/gen_placeholders.py` | Regenerate the placeholder textures (needs Pillow) |

Requires JDK 21 (e.g. Temurin). Point `JAVA_HOME` at it if it isn't your default Java.

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
| Curios API (by TheIllusiveC4) | 9.5.1 or newer | [Modrinth](https://modrinth.com/mod/curios) | the slot eyewear sits in |

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

## Postgres storage (optional)

Kegare's saved data can live in Postgres instead of the world's files, so several servers can share it and leaderboards work. It's off by default, and then nothing changes and no
connection is opened. Only a dedicated server can use it, and the driver comes inside the mod's jar, so nobody installs anything. Vanilla's own player data (inventory, ender
chest, health, position), other mods' data, `PuppetSavedData` and `BattleScars` stay in the world. Design: DESIGN §3.33.

**Setup**
1. Have Postgres running and make a database for Kegare: `CREATE ROLE kegare LOGIN PASSWORD '...'; CREATE DATABASE kegare OWNER kegare;`
2. Start the server once to generate `config/kegare-database.toml`, then stop it. This file is never sent to clients; don't put these settings in `kegare-server.toml`, which is.
3. Set `enabled = true`, `url` and `username`, and give each server sharing the database its own `serverId`. For the password, prefer the environment variable
   `KEGARE_DB_PASSWORD`, which wins over the file's `password`.
4. Turn the whitelist on and start the server. Kegare builds its own tables, in a schema called `kegare`, and `/kegare db status` should say it's connected.

**Moving an existing world over**
1. `/kegare db migrate dry` says what would be copied, and `/kegare db migrate` copies every player and the unique traits. It's safe to run twice and leaves the world's files alone.
2. Now turn the whitelist off. Before this, a brand new player's roll can't see who holds a unique trait in the world. Anyone who was already on this world is safe either way:
   a player with no rows whose world file has Kegare's data gets it imported when they log in, never a fresh roll.

**Switching back**
1. With nobody online if you can, run `/kegare db export`: everything goes back into the world's files.
2. Stop the server straight away, set `enabled = false`, and start it again. Stats stay in the database, as a world has nowhere to keep them.

**If the database goes down** the game carries on and saves are queued, with one warning to ops when it fails and one when it's back. Nobody can join until it's back (they're told why,
and never get a fresh roll). If the server stops first, what's queued goes to `config/kegare-database-spill.json` and goes in on the next start: don't delete that file.

**Several servers** share a database by each having their own `serverId`. A player can be on one at a time, and their data moves with them. Their position and inventory
don't: those are Minecraft's. If a server dies, its players can join elsewhere after `leaseStaleSeconds` (45). Run `/kegare db migrate` on each server that has an existing world.

**Stats and leaderboards.** `/kegare top <stat>` works for anyone: `pvp_kills`, `deaths`, `fights`, `damage_dealt`, `black_sparks` or `longest_chain`. For a website or a Discord bot,
the `kegare_public` schema holds read-only views of those stats and of announced advancements, and nothing private. Make a login that can read them and nothing of Kegare's with
`psql -d kegare -v password='...' -f tools/db/public_reader.sql`.

**Backups** are yours to set up: with storage on, the database is the only copy of this data. `pg_dump` the `kegare` schema.

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
| `/kegare debug impact <player> <tier> <attacker\|victim\|witness> <melee\|technique>` | Play an Impact System tier on that player's own screen only (nothing lands), to tune how it looks in the client config's `[impact]` (DESIGN §3.23) |
| `/kegare debug impact log <true\|false>` | Every hit's tier and the numbers behind it go to its attacker and victim, to tune the server's `[impact]` thresholds |
| `/kegare debug screen <player> <effect> <seconds>` | Play a whole-screen effect (`kegare:grayscale`) on a player's screen; 0 stops it |
| `/kegare debug battle restore` | Start rebuilding everything battle damage broke in RESTORING mode now (DESIGN §3.24) |
| `python3 tools/gen_art.py [--preview sheet.png]` | Regenerate all GUI and item art (needs Pillow); see DESIGN.md §3.10 |

Requires JDK 21 (e.g. Temurin). Point `JAVA_HOME` at it if it isn't your default Java.

Keys: **G** Technique Mode (shows the combat panel). In it, a number key casts that slot (hold to charge or channel), **R** +
number key casts its special, **R** alone casts the selected ability's special, and **V** is the ultimate. The scroll wheel stays on
the hotbar. Out of the mode, number keys are the hotbar and nothing casts. **K** Sorcerer screen. **M** the Six Eyes' map (only with the
eyes). **J** Curios' screen, for eyewear (moved off G, which is Technique Mode). Right-click is always vanilla.
See DESIGN.md §3.5.

Commands read `/kegare <system> <action> [player] [value]` and take short names (`/kegare innate set Steve rime`).
`/kegare help` lists what you can run: `top`, `db`, `info`, `innate`, `trait`, `malice`, `rebirth`, `loadout`, `grade`, `assessment`,
`refinement`, `insight`, `progression`, and `debug` for anything that exists only for testing. All but `help` and `top` need
permission level 2. See DESIGN.md §3.8.

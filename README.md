# Kegare

**An unofficial, fan-made Jujutsu Kaisen mod for Minecraft 1.21.1 (NeoForge), made for PvP between friends.**

Kegare brings the world of Gege Akutami's *Jujutsu Kaisen* into Minecraft. Every player is born with **Malice**, the mod's take on
cursed energy, and an **Innate Technique** to fight with, or is born **Severed**: no Malice at all, and a superhuman body in its
place. Fights between players are the heart of it.

It's also inspired by [Jujutsu Kaisen: Cursed Fate](https://www.curseforge.com/minecraft/mc-mods/cursed-fate), another Jujutsu
Kaisen mod. Seeing how Cursed Fate lays out its menus, ability tabs and loadout bar shaped how Kegare organises its own. Only ideas
were taken: every file in Kegare is its own, and most of the anime's terms get new names (Malice for cursed energy, Black Spark for
Black Flash).

> **Unofficial.** Kegare is a fan project. It isn't affiliated with or endorsed by Gege Akutami, Shueisha, MAPPA or anyone else
> behind *Jujutsu Kaisen*, or by the makers of Cursed Fate. NOT AN OFFICIAL MINECRAFT PRODUCT. NOT APPROVED BY OR ASSOCIATED WITH
> MOJANG OR MICROSOFT.

> **Made with generative AI.** Kegare's code and assets were made with generative AI, from a human designer's specifications.
> See [How Kegare was made](#how-kegare-was-made).

**Status:** early development (version 0.1.0-alpha). Expect changes, balance passes and the odd bug. Dominions, and the hostile spirits
that will share the mod's name, are still to come.

## Features

**Born with it**
- **Malice** is rolled once, on first join, into a tier (Low, Average, High or Vast, or Zero, which makes you Severed) and never
  changes.
- **Innate Techniques** are rolled by rarity and rerolled with a Purification Talisman. Every ability on the bar also has a
  special on **R**:
  - **Swap** (Common): trade places, faces, negative effects and momentum with whoever you're looking at.
  - **Rime** (Rare): Frost Shard, Glacial Lance, Frost Field and Rime Shell; Freeze stacks; cheaper in the cold.
  - **Puppet Manipulation** (Epic): half-size puppets of yourself to command, possess, detonate or use as a double, plus
    Strings to pull and tie targets.
  - **Time Manipulation** (Legendary): a mark to come back to, your own clock run fast and everyone else's slow, and at the
    last, time stopped, which burns your technique out for a while.
- **Severed** players trade all their Malice for a superhuman body: senses that outline every player and mob nearby, **Blur**
  for speed, and **Closing Line**, a straight rush at a target.
- **Traits**, a few of them unique: only one player in the world holds each, and the game never tells anyone else who.

**Fighting**
- **Melee for everyone:** Dash, Parry and Reinforced Strike, in and out of Technique Mode.
- **Reinforced Fists** wrap Malice round your fists. Land the Malice within a hair of a punch and it becomes a **Black Spark**,
  a far heavier hit.
- **Hits that feel heavy:** a short freeze, a camera shake, a flash where the hit lands, and an impact frame for the biggest
  ones. It's all feel: nothing in it changes what a hit does.
- **Battle damage:** big hits break the world, so cover breaks and the ground changes mid-fight. By default what breaks stays
  broken; a server can make it rebuild after the fight, or show cracks and debris only.
- **Too fast for the eye:** someone Blurring, or sped up by Time Manipulation, is seen a moment behind where they really are.

**Growing**
- **Loadout:** nine ability slots and a fixed ultimate.
- **Progression:** a private Grade ladder earned mostly in PvP, and promoted by how well you use your technique rather than by
  winning; Refinement, which unlocks a technique's abilities as you land them; and Insight from first-time encounters. It
  changes no damage, costs or capacity.
- **Reverse Malice Technique:** a shared healing ability, found through a green orb only its finder can see.
- **Background Tasks:** keep Reinforcement or Reverse Malice Technique running without a bar slot, weaker than on the bar.

**Also**
- **Eyewear:** a Blindfold and Sunglasses, worn in their own Curios slot.
- **Your own settings:** ability effects at Full, Reduced or Vanilla, screen shake from 0 to 200%, and impact frames on or off
  (Sorcerer screen, then Settings).
- **Server-authoritative:** every gameplay number is in `config/kegare-server.toml`.
- **Optional Postgres storage**, so several servers can share players, and leaderboards.

Every system is described in full in [DESIGN.md](DESIGN.md).

## Installing

Kegare runs on Minecraft 1.21.1 with NeoForge 21.1.x. Every player **and** the server need Kegare and these mods in their
`mods` folder:

| Mod | Version | Download | Why |
|---|---|---|---|
| Player Animation Library (by ZigyTheBird, *not* KosmX's playerAnimator) | 1.1.6 or newer | [Modrinth](https://modrinth.com/mod/player-animation-library) | player animations |
| Photon (by KilaBash) | 2.2.10 or newer | [Modrinth](https://modrinth.com/mod/photon-editor) | ability visual effects |
| LDLib2 (by KilaBash) | 2.2.45 or newer | [Modrinth](https://modrinth.com/mod/ldlib) | required by Photon |
| Curios API (by TheIllusiveC4) | 9.5.1 or newer | [Modrinth](https://modrinth.com/mod/curios) | the slot eyewear sits in |

KilaGraph, which Photon needs, is bundled inside Photon. Don't install it separately. Pick the NeoForge 1.21.1 file of each
one. Minecraft refuses to start and names the missing mod if one is absent.

Until there's a published release, build Kegare's jar yourself: see [Building from source](#building-from-source).

## Playing

**Keys**
- **G** toggles Technique Mode, which shows the combat panel. In it, a number key casts that slot (hold it to charge or
  channel), **R** + a number key casts that slot's special, **R** alone casts the selected ability's special, and **V** is the
  ultimate. The scroll wheel stays on the hotbar. Out of the mode, number keys are the hotbar and nothing casts.
- Severed players have no Technique Mode: for them **G** is Blur, and **R** while Blurring is Closing Line.
- **K** opens the Sorcerer screen. **M** opens the Six Eyes' map (only for a player with the Six Eyes trait). **J** opens Curios' screen, for eyewear.
- Right-click is always vanilla.

See DESIGN.md §3.5.

**Commands** read `/kegare <system> <action> [player] [value]` and take short names (`/kegare innate set Steve rime`).
`/kegare help` lists what you can run: `top`, `db`, `info`, `innate`, `trait`, `malice`, `rebirth`, `loadout`, `grade`,
`assessment`, `refinement`, `insight`, `progression`, and `debug` for anything that exists only for testing. All but `help` and
`top` need permission level 2. See DESIGN.md §3.8.

## Running a server

Every gameplay number lives in `config/kegare-server.toml`, created on the first start. See DESIGN.md §3.9.

### Postgres storage (optional)

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

## For mod developers

The [addon API guide](docs/addon-api.md) shows how to register new innate techniques and abilities, contribute extra
loadout attacks and lifecycle hooks to existing techniques, and build boss encounters with timed attacks, phases and
extensible runtime metrics. Addons install on both client and server. Boss AI, targeting, visuals, balance and persistence
remain addon-owned; Kegare adds no Mahoraga content, boss saved-data schema, rewards or network payloads. Technique
contributions are extra ordinary attacks, not alternate R-specials. Design: [DESIGN.md §3.34](DESIGN.md#334-addon-api-abilities-attacks-and-boss-encounters).

## How Kegare was made

**Generative AI disclosure:** Kegare would not exist without generative AI. Every part of the mod a player runs, its code and
its assets, was made with it, under a human designer's direction.

Kegare was designed by CNethuka: its lore, systems, rules and balance. It was built with AI assistance (Claude Code):
the code and the assets (models, textures, animations and sounds) were produced with AI from the designer's
specifications, then tested and refined in play. Nothing is copied from other mods or from the anime.

`CLAUDE.md` holds the rules the AI coding agents work by. `AGENTS.md` and `AGENT.md` are exact copies of it, for tools that look
for those names.

## Contributing

Help is welcome: bug reports, playtest notes, ideas and code. Before you start:
- Read [DESIGN.md](DESIGN.md). It's the source of truth for every system, and a change to how something works updates it in the
  same commit.
- Keep it original. Nothing is copied from other mods, the anime or the manga: no code, art, models, animations, sounds or text.
- Run `./gradlew test` before you open a pull request. If you changed text, advancements, tags or config options, run
  `./gradlew runData` first and commit what it writes.
- Report security problems privately, as [SECURITY.md](SECURITY.md) describes, not in a public issue.

### Using AI responsibly

Kegare was made with AI, and you're welcome to use AI tools when you help with it. Use them responsibly:
- **You answer for what you submit.** Read and understand every line before you send it, and test it in game. "The AI wrote
  it" doesn't explain a bug.
- **Say what AI did.** Mention in your issue or pull request what AI helped with.
- **No unchecked output.** Don't send pull requests you haven't reviewed, or bug reports you haven't reproduced yourself.
- **Respect other people's work.** Don't use AI to copy or imitate another mod's, the anime's or the manga's code, art, models,
  animations or sounds, and don't feed their files to an AI to make lookalikes.
- **Keep private things private.** Don't paste passwords, keys, server logs with players' details, or anyone's personal data
  into an AI tool.
- **Agents follow the rules too.** A coding agent should work from `CLAUDE.md`. To change those rules, edit `CLAUDE.md`, then copy
  it over `AGENTS.md` and `AGENT.md` (a test checks they match).

## Building from source

Requires JDK 21 (e.g. Temurin). Point `JAVA_HOME` at it if it isn't your default Java.

| Command | What it does |
|---|---|
| `./gradlew build` | Compile, run unit tests, build `build/libs/kegare-<minecraft version>-<version>.jar` (for example `kegare-1.21.1-0.1.0-alpha.jar`) |
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

The Postgres tests are skipped unless `KEGARE_TEST_DB_URL` points at a throwaway database (with its user and password in the
URL's query). Every test starts from an empty database, so use one you don't need.

Files under `src/generated/resources` are written by `./gradlew runData` from the classes in `data/`: edit those classes, never
the generated files.

Assets are edited directly, and each has one source: models in Blockbench (`art/blockbench/*.bbmodel`, exported over the
shipped OBJ and textures), player animations in Blockbench (`assets/kegare/player_animations/*.json`), textures in an image editor
and sounds as `.ogg` files. See DESIGN.md §3.10.

## Security

Please report vulnerabilities privately: see [SECURITY.md](SECURITY.md).

## License

Kegare is **All Rights Reserved**: see [LICENSE](LICENSE). Please ask before you copy, modify or redistribute any of its code
or assets. *Jujutsu Kaisen* belongs to Gege Akutami and Shueisha, and Minecraft to Mojang.

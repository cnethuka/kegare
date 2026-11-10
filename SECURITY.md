# Security policy

Kegare is a hobby project. We still take security seriously, and thank you for helping keep players and servers safe.

## Supported versions

Only the latest release and the `main` branch get security fixes. Kegare is in early development (0.1.x), so please update
before you report.

## Reporting a vulnerability

**Please don't report vulnerabilities in public issues, pull requests or chats.**

Report them privately on GitHub: open this repository's **Security** tab and choose **Report a vulnerability**. If that option
isn't there, open an issue that only asks for a private way to get in touch, with no details.

A good report says:
- what the problem is and what an attacker could do with it
- the steps to reproduce it, with the Kegare version and the other mods' versions
- whether it needs a modified client, operator permission or access to the server's files

We'll reply as soon as we can. This is a hobby project, so there's no fixed timeline and no bug bounty. Please give us a
reasonable time to fix the problem before you talk about it publicly. If you'd like, we'll credit you in the fix.

If you used an AI tool to find or describe a problem, check it yourself first: please only send problems you've reproduced.

## What counts

Kegare is server-authoritative: the client only asks, and the server decides what happens. A vulnerability is anything that
breaks that, or that hurts players or servers. For example:
- a client, even a modified one, crashing or freezing the server or other players' games through Kegare's network messages
- a client casting without paying Malice, skipping cooldowns or locks, or acting on other players beyond what the rules allow
- a player learning what Kegare keeps private about other players: their Grade, Assessment, Refinement or Insight, their traits,
  or who holds a unique trait
- running code, or reading or writing files, on a server or another player's machine
- with Postgres storage: SQL injection, leaked database credentials, the public reader role reaching private data, or saved data
  lost or corrupted

## What doesn't

- bugs in Minecraft, NeoForge or the mods Kegare requires: please report those to their own projects
- balance problems and ordinary gameplay bugs: open a normal issue
- anything that needs operator permission: operators can already see and change everything through `/kegare` commands
- general cheats that have nothing to do with Kegare, such as X-ray or flying hacks
- problems on a server whose owner has exposed its files, database or console

## Keeping your server safe

- Give operator permission only to people you trust.
- Keep `config/kegare-database.toml` private. It's never sent to players, but anyone who can read it can reach your database.
  Prefer the `KEGARE_DB_PASSWORD` environment variable to a password in the file.
- Don't expose Postgres to the internet. For a website or a bot, use the read-only login from `tools/db/public_reader.sql`,
  never Kegare's own.
- Back up your database (`pg_dump` the `kegare` schema), and don't delete `config/kegare-database-spill.json`: it holds saves
  that couldn't reach the database yet.
- Get Kegare and its required mods only from this repository and the mods' official pages.

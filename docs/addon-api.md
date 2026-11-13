# Kegare addon API

Kegare supports three addon paths:

1. A completely new innate technique and its abilities.
2. Extra loadout attacks for an existing technique, plus hooks around existing abilities.
3. Boss entities with timed attacks, encounter phases, damage accounting, and addon-defined metrics.

The API is server-authoritative. Clients send the same cast intents as usual; the server resolves the ability, validates
its unlocks, requirements, cost, and cooldown, then runs addon code. Boss addons likewise own their entity AI and choose
attacks on the server.

This version does **not** add alternate R-special selection, boss rewards, a custom boss HUD, automatic persistence, or a
Mahoraga implementation.

## Setup and loading rules

- Depend on Kegare and NeoForge for Minecraft 1.21.1. Until Kegare publishes a Maven artifact, use a Gradle project
  dependency or a local jar in the normal NeoForge development setup.
- Declare the appropriate Kegare dependency in `neoforge.mods.toml` so Kegare loads first.
- Install the addon on both the server and connecting clients. Synced technique definitions let the client render names,
  icons, costs, cooldowns, and loadouts, but they do not download addon code, entities, renderers, or assets.
- Use your own namespace for every new technique, ability, phase, attack, and metric ID.
- Put your gameplay numbers in your own server config. Kegare does not copy addon values into `kegare-server.toml`.
- Supply your own translations. Kegare's datagen validates `kegare` keys only and does not require addon translations in
  Kegare's `en_us.json`.

## 1. A new innate technique and ability

Use the existing ability contracts directly: `Ability`, `BaseAbility`, `AbilityContext`, `CastType`, and, when needed,
`SpecialAbility`. Do not wrap a boss in a fake player or a fake innate technique; bosses have a separate API below.

```java
public final class CinderBoltAbility extends BaseAbility {
    public CinderBoltAbility() {
        super(ResourceLocation.fromNamespaceAndPath("example_mod", "cinder_bolt"), CastType.INSTANT);
    }

    @Override public double cost(ServerPlayer player) {
        return 20; // Read your own config in real content.
    }

    @Override public int cooldownTicks(ServerPlayer player) {
        return 40;
    }

    @Override public void execute(AbilityContext context) {
        ServerPlayer caster = context.player();
        // Spawn/damage/etc. on the server. CastManager has already validated and paid for this cast.
    }
}
```

There are four cast types:

- `INSTANT`: pay and execute on press.
- `CHARGED`: tick while held, then validate, pay, and execute on a valid release; `context.charge()` is 0–1.
- `CHANNELED`: pay and execute on press, then tick while held with upkeep.
- `TOGGLE`: pay and execute to switch on, tick with upkeep, and end on another press.

Register the technique from your mod constructor with the synced Kegare registry:

```java
public static final ResourceLocation CINDER_ART_ID =
        ResourceLocation.fromNamespaceAndPath("example_mod", "cinder_art");

public static final DeferredRegister<InnateTechnique> TECHNIQUES =
        DeferredRegister.create(KegareRegistries.INNATE_TECHNIQUE_KEY, "example_mod");

public ExampleMod(IEventBus modBus) {
    TECHNIQUES.register(modBus);
}

static {
    TECHNIQUES.register("cinder_art", () ->
            TechniqueAbilityContributions.applyTo(CINDER_ART_ID,
                    InnateTechnique.builder(InnateTechniqueRarity.RARE)
                            .ability(new CinderBoltAbility())
                            .defaultUnlockLevels())
                    .build());
}
```

`TechniqueAbilityContributions.applyTo` is optional for a standalone technique. Including it lets another addon extend
your technique by ID. You can instead use `unlockLevel(ability, level)` for each ability, special, and ultimate. Every
registered technique is rollable by default; this API does not add a roll opt-out.

At minimum, provide these translations in your own language provider:

```text
innate_technique.example_mod.cinder_art
innate_technique.example_mod.cinder_art.desc
innate_technique.example_mod.cinder_art.epithet
ability.example_mod.cinder_bolt
ability.example_mod.cinder_bolt.desc
```

The normal loadout, Refinement, Assessment, progression, cooldown, HUD, and synced-registry paths now apply. A
`SpecialAbility` uses the ordinary R-special controls and shares its base ability's cooldown. Only one special per base
ability is supported.

## 2. Add an attack to an existing technique

Register contributions during mod construction, before registry entry suppliers are evaluated. A contribution is an
ordinary loadout ability with its own cost, cooldown, requirements, cast type, visuals, and Refinement unlock. It is not
an alternate special for the anchored ability.

```java
public ExampleMod(IEventBus modBus) {
    TechniqueAbilityContributions.registerAfter(
            ResourceLocation.fromNamespaceAndPath("kegare", "rime"),
            ResourceLocation.fromNamespaceAndPath("kegare", "frost_shard"),
            new CinderBoltAbility(),
            4);
}
```

Use `register` instead of `registerAfter` to append. If the ability has a special, supply both unlock levels:

```java
TechniqueAbilityContributions.register(techniqueId, ability, 4, 6);
```

Placement and validation rules:

- An insertion anchor must identify an existing **ordinary** ability, not a special or ultimate.
- Contributions sharing an anchor retain registration order; later unrelated contributions append deterministically.
- Inserting attacks does not move existing abilities' default-table Refinement levels or overwrite explicit levels.
- IDs must be unique across the complete technique, including specials and the ultimate.
- A contributed ordinary ability cannot itself be a special, and its special must point back to that ordinary ability.
- Missing special levels, negative levels, unknown anchors, duplicates, and registration after construction fail clearly.
- Applying the first registry definition closes contribution registration. Do not attempt runtime or hot registration.

Removing an addon is safe: existing saved-loadout cleanup empties slots whose ability IDs no longer resolve.

## 3. Hook an existing ability's lifecycle

Listen on `NeoForge.EVENT_BUS` and filter by the ability ID. These are non-cancellable notifications, not a replacement
for the shared cast checks.

```java
public final class ExampleAbilityEvents {
    private static final ResourceLocation FROST_SHARD =
            ResourceLocation.fromNamespaceAndPath("kegare", "frost_shard");

    @SubscribeEvent
    public static void afterExecute(AbilityExecuteEvent.Post event) {
        AbilityContext context = event.getContext();
        if (!context.ability().id().equals(FROST_SHARD)) return;

        // A follow-up effect belongs to this cast automatically: this listener already runs inside its CastScope.
    }

    @SubscribeEvent
    public static void afterEnd(AbilityEndEvent.Post event) {
        EndReason reason = event.getReason();
        // Filter and clean up addon-owned runtime state here.
    }
}
```

Available events:

| Event | When it runs |
|---|---|
| `AbilityExecuteEvent.Pre` | After validation and payment, immediately before `Ability.execute` |
| `AbilityExecuteEvent.Post` | Immediately after successful `execute`; an instant `fizzleKeepingCost` cast does not produce it |
| `AbilityTickEvent.Post` | Immediately after each charge/channel/toggle `Ability.tick` |
| `AbilityEndEvent.Post` | Immediately after `Ability.onEnd`, with its `EndReason` |

All four run inside the triggering ability's `CastScope`, so projectiles and summons spawned by a hook inherit the usual
attribution. Rejected casts do not emit execution events. The hooks do not permit changing Kegare's already-decided cost,
cooldown, or input behavior.

For indirect damage that the normal `CastScope`/`CastOrigin` attribution cannot explain, use
`Attribution.addAttributor`. Read `Ability`, `Landings`, and `DESIGN.md` §3.21 before adding custom no-target or delayed
progression behavior.

## 4. Add a boss and its encounter

Create the entity, model, renderer, animations, targets, movement, spawning, balance, and loot with ordinary NeoForge
APIs. Add its entity type to the common `c:bosses` entity-type tag. Kegare bosses can be damaged, but Kegare techniques
must not move, swap, freeze, stun, or otherwise disable them. The shared `CombatHelper.stun` helper refuses bosses;
third-party code remains responsible for its own movement and status effects.

Start one explicit encounter for the living server-side boss, normally from your spawn/encounter logic:

```java
ResourceLocation opening = ResourceLocation.fromNamespaceAndPath("example_mod", "opening");
BossFight fight = BossFights.start(bossEntity, opening);
fight.join(player); // Optional: damaging or being damaged by the boss also joins a player.
```

`BossFights.start` rejects a non-server entity, a dead entity, a type outside `c:bosses`, and a second active encounter
for the same boss. Use `BossFights.find(boss)` or `find(encounterId)` to retrieve it. End an addon reset with
`BossFights.reset(boss)`, another reason with `end`, or all encounters on a server with `resetAll(server)`.

The addon decides phases and transitions:

```java
fight.setPhase(ResourceLocation.fromNamespaceAndPath("example_mod", "enraged"));
```

Equal consecutive phase IDs are ignored. Phase history is recorded in the snapshot. Kegare does not impose leash,
proximity, health-threshold, or reward policies.

## 5. Timed boss attacks

One `BossAttackController` belongs to each encounter. It runs one attack at a time through:

```text
windup -> active -> recovery -> per-attack cooldown
```

Durations are server ticks and must be non-negative. At least one of windup, active, or recovery must be nonzero;
zero-length leading phases are skipped. The cooldown starts when the attack completes, is cancelled, or its encounter
ends. The framework drives ticks automatically; calling `tick()` again in the same game tick is a no-op.

```java
public final class WheelCrash implements BossAttack {
    private static final ResourceLocation ID =
            ResourceLocation.fromNamespaceAndPath("example_mod", "wheel_crash");

    @Override public ResourceLocation id() { return ID; }

    @Override public BossAttackTiming timing() {
        return new BossAttackTiming(20, 4, 12, 30);
    }

    @Override public void onStart(BossAttackContext context) {
        // Play the visible/audible tell here, at the beginning of windup.
    }

    @Override public void onTick(BossAttackContext context) {
        if (context.state() != BossAttackState.ACTIVE || context.stateTicks() != 0) return;
        LivingEntity target = context.target();
        if (target == null || !target.isAlive()) return;
        target.hurt(context.boss().damageSources().mobAttack(context.boss()), 8);
        // Direct boss damage during this attack is credited to its attack instance.
    }

    @Override public void onEnd(BossAttackContext context, BossAttackEndReason reason) {
        // Release addon-owned attack state. This runs once for completion or cancellation.
    }
}
```

Your AI chooses the target and when to start:

```java
BossAttackStartResult result = fight.attacks().start(new WheelCrash(), chosenTarget);
if (result == BossAttackStartResult.ON_COOLDOWN) {
    // Pick another action, or wait.
}
```

Other start results distinguish an already-running attack and an ended encounter. `cancel()` cancels the active attack;
`activeAttack()`, `activeAttackInstance()`, and `cooldownTicksRemaining(id)` expose its current state. Kegare never
selects or invalidates the target for you.

### Projectiles and summons

Tag anything whose later damage should retain encounter and attack provenance:

```java
Snowball projectile = new Snowball(context.level(), context.boss());
context.markOrigin(projectile);
context.level().addFreshEntity(projectile);
```

The return value of `markOrigin` is the same entity for convenient chaining. `boss_attack_origin` is runtime-only and is
not saved. A direct hit from the boss while an attack is running also credits that attack. Other boss damage still
counts as encounter outgoing damage, without an attack ID.

All callbacks receive live server objects. Use them only on the server thread and do not retain them as a detached
report. `BossAttackEvent.Started` and `Ended`, like encounter events, contain immutable snapshots instead.

## 6. Encounter metrics and persistence

`BossFight.snapshot()` returns a `BossFightSnapshot`: immutable, detached data containing UUIDs, resource IDs, and numeric
values, never live entities, levels, registries, or damage sources. Collections and the per-participant maps copied into
it cannot be mutated.

Built-in data includes:

- Encounter ID, boss UUID/type, start/end tick, duration, current phase, phase history, and end reason.
- Participants and their join/first-activity/last-activity ticks. Participants remain in reports after death or logout.
- Total incoming and outgoing raw, post-mitigation, and health-clamped effective damage, with hit counts.
- Per-player damage to/from the boss and per-ability incoming damage.
- Per-attack starts, completions, cancellations, damage, and hits.
- Killing-blow player, ability, damage type, and tick when attribution resolves one.
- All custom metric values.

`raw` is the damage entering Kegare's `LivingDamageEvent.Pre` capture; `postMitigation` is the final
`LivingDamageEvent.Post` amount; `effective` is clamped to the target's health before that hit, preventing overkill from
inflating effective totals. Each Post is matched to the newest pending Pre for the same victim and identical
`DamageSource`; this preserves properly nested hits and lets different sources complete out of order. Aborted captures
are cleared at tick end so they cannot claim later hits. Delayed player abilities use `CastOrigin` IDs rather than
looking up whatever technique the player holds later.

These encounter metrics are independent of PvP Grade, Refinement, Impact aggregation, `FightTracker`, and Kegare's
Postgres stat events.

### Custom adaptation-style metrics

A custom key has a namespace and one numeric type, `long` or `double`. Kegare does not need to understand its meaning.

```java
private static final BossMetric<Double> ADAPTATION =
        BossMetric.doubleMetric(ResourceLocation.fromNamespaceAndPath("example_mod", "adaptation"));
private static final BossMetric<Long> WHEEL_TURNS =
        BossMetric.longMetric(ResourceLocation.fromNamespaceAndPath("example_mod", "wheel_turns"));

fight.increment(ADAPTATION, 0.25);
fight.increment(WHEEL_TURNS, 1L);
double progress = fight.get(ADAPTATION); // Zero when unset.
fight.set(ADAPTATION, 0.0);
```

A metric ID already holding one numeric type cannot be reused as the other in that encounter. Long increments reject
overflow. Values appear under their resource IDs in `snapshot().customMetrics()`.

For example, an adaptation system can react to `BossDamageEvent`, find the still-live encounter by UUID, and increment
its own metric from the event's damage type/amount. The snapshot in the event is detached, so call `BossFights.find` if
you intentionally want to mutate the live fight.

### Events and addon-owned storage

The game bus provides:

- `BossEncounterEvent.Started`, `PhaseChanged`, and `Ended`.
- `BossAttackEvent.Started` and `Ended`, including the attack instance ID and end reason.
- `BossDamageEvent`, with direction, target, participant, ability/damage type, optional attack provenance, and amounts.
- `BossMetricEvent`, with the changed metric ID and value.

```java
@SubscribeEvent
public static void saveResult(BossEncounterEvent.Ended event) {
    BossFightSnapshot report = event.getEncounter();
    // Hand this immutable report to your own file/database/queue implementation.
}
```

Kegare writes no boss SavedData, database table, reward, or migration. Persistence belongs to the listener. An encounter
ends exactly once on defeat, removal/despawn, explicit reset/end, or server stop. Server shutdown uses
`BossFightEndReason.SERVER_STOP`; restart attempts from teardown callbacks are rejected and listener failures do not
strand other encounters.

## Scope and compatibility

This API adds no packets and changes no payload format; `KegareNetwork.VERSION` remains **27**. Addon entities and any
custom rendering/HUD remain the addon's networking responsibility. All casting continues to use Technique Mode's current
number/R/V controls, and right-click remains vanilla.

The implementation and its in-game tests live under `api.ability`, `api.boss`, and the internal `boss` package. See
`DESIGN.md` §3.34 and §4 for the system-level contract and test inventory.

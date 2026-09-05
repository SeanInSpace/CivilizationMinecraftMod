# Kingdoms

A Millénaire-class civilization simulation for Minecraft 26.2 on NeoForge — autonomous NPC settlements with individually simulated inhabitants, self-directed building, and settlements that defend themselves.

**Players start at [PLAYING.md](PLAYING.md)**: craft a Founding Charter, use it on
open ground, and the town takes it from there.

Design docs: [FOUNDING.md](FOUNDING.md) (how a settlement grows from camp to town,
and how it daughters the next one) · [BUILD_DECISIONS.md](BUILD_DECISIONS.md) (what
settlements build) · [POPULATION.md](POPULATION.md) (families, growth, jobs) ·
[DEFENSE.md](DEFENSE.md) (raids, guards, the town wall) ·
[docs/CITIZENS.md](docs/CITIZENS.md) (what every citizen does, and in what order) ·
[docs/HAULERS.md](docs/HAULERS.md) (a design for the porter trade, not yet built) ·
[KEYSTONE.md](KEYSTONE.md) (the blueprint mod shipped alongside).
The worklist is [GOALS.md](GOALS.md).

**Why NeoForge:** the two closest existing analogs both chose it independently —
Millénaire's 2026 clean-slate rewrite, and MineColonies officially. The loader
choice is not about the AI (both loaders give identical access to Mojang's classes,
and this project barely uses vanilla AI anyway); it is about first-party persistence
and hooks for per-entity and town-scoped state.

---

## Layout

```
common/     Simulation core — plain Java, NO Minecraft, NO loader
neoforge/   Platform layer — everything that touches Minecraft
keystone/   A second, standalone mod: blueprints of any size (KEYSTONE.md)
```

**The split is the whole architecture.** `common/` models kingdoms, settlements and people as data that ticks on a slow scheduler, and can be fully unit-tested in milliseconds without launching a game. `neoforge/` renders that simulation into the world: registration, events, persistence, networking, and the entity "view" layer.

`neoforge/` depends on `common/` and on `keystone/`. `common/` depends on nothing, and `keystone/` depends on neither — it ships as its own jar and knows nothing about Kingdoms, the way Structurize does not know about MineColonies.

The one seam between them is [`WorldBridge`](common/src/main/java/com/kingdoms/sim/platform/WorldBridge.java), implemented by [`NeoForgeWorldBridge`](neoforge/src/main/java/com/kingdoms/neoforge/bridge/NeoForgeWorldBridge.java).

> **Rule:** if you want to `import net.minecraft.*` inside `common/`, don't. Add a method to `WorldBridge` instead. Every time you honor this rule you keep the simulation testable and the Fabric port cheap. Every time you break it, both get harder.

---

## Requirements

| | |
|---|---|
| **Java** | **25** — required by Minecraft 26.1+ |
| Gradle | 9.7.0 (via the included wrapper) |
| NeoForge | 26.2.0.59 |
| ModDevGradle | 2.0.144 |

You currently have **Java 17** installed. Gradle runs fine on 17, and `settings.gradle` includes the Foojay toolchain resolver so Gradle **auto-downloads JDK 25** for compilation on first build. This is verified working — the whole project was built and tested this way. Installing [Temurin JDK 25](https://adoptium.net/temurin/releases/?version=25) and pointing `JAVA_HOME` at it is still worth doing, mainly so your IDE agrees with Gradle.

### Verified

As of Phase 0–2 completion, on this machine:

- `:common:test` — 118/118 passing, covering every planner, the hydration state machine, and the stress benchmark
- **Stress:** 20 settlements, 640 people, 340 buildings — 520 µs per sim step, amortized 5.2 µs per game tick
- `:neoforge:build` — green; dedicated server boots the mod ("Initialized 3 dimension simulation(s)")

First build takes ~4 minutes (Minecraft decompile); subsequent builds ~10 seconds.

---

## Commands

Run the simulation tests — no Minecraft required, takes seconds:

```bash
./gradlew :common:test
```

Build the mod jar:

```bash
./gradlew :neoforge:build
```

Launch a dev client:

```bash
./gradlew :neoforge:runClient
```

Launch a dev server:

```bash
./gradlew :neoforge:runServer
```

---

## Playtesting

**The no-cheats loop (as of Phase 5):** craft a **Founding Charter** (paper + emeralds + book), use it on open ground, and a named town is founded with four settlers. Sneak-use a charter for a written report on the nearest settlement. See [PLAYING.md](PLAYING.md).

Walk within ~96 blocks of a settlement and its residents appear as villagers in the *outfit of their trade* — a mason-clothed builder named "Ada Baker — Builder" — who wander near their family homes, trade on right-click, and walk back when they stray. Walk away and they dissolve back into data; the simulation never stops either way. Buildings are timber cabins, fenced farms and cobble watchtowers (or your own `.nbt` templates — see PLAYING.md), on foundations that handle slopes.

Two things follow from the view-layer design that are worth knowing while testing:

- **Killing a view villager kills the person.** Removed from the roster and their family, permanently. Deaths are real.
- Villagers are **vanilla underneath** — they may flee zombies, gossip, or try to breed. Their wandering position is written back each second; anything vanilla does to them beyond moving is cosmetic.

Launch a dev client:

```bash
./gradlew :neoforge:runClient
```

Create a world, then in chat (needs cheats on, or op on a server):

```
/civ found Normandy
/civ populate 3 BUILDER
/civ build bakery 12
/civ info
```

`info` reports every kingdom, settlement, population, threat level, and build progress. Then fast-forward instead of waiting 5 seconds per step:

```
/civ step 6
/civ info
```

The bakery leaves the queue, appears under `built (1)`, and a stone platform with a gold marker is placed at the position you queued it from. Remove the builders and it stalls — that is the entire profession system as currently implemented.

To see deferred placement, queue a build and immediately walk far enough away that the chunk unloads, then `/civ step 6` and come back. `info` will show the building as `[PENDING placement]` until a step runs with the chunk loaded.

### The full command set

| Command | Effect |
|---|---|
| `/civ found <name>` | Found a kingdom + settlement at your position |
| `/civ info` | Dump all simulation state, including jobs and visible-villager counts |
| `/civ populate <count> <profession>` | Add residents to the nearest settlement |
| `/civ build <blueprint> <work>` | Queue a build task |
| `/civ threat <level>` | Set threat on the nearest settlement |
| `/civ step [count]` | Force simulation steps immediately |
| `/civ raid [strength]` | Force a raid — real zombies if you stand there, arithmetic if run from the console |

All require permission level 2 (gamemaster) and can be disabled with `debug.commands_enabled` in the config.

### Config

Server config lives at `<world>/serverconfig/kingdoms-server.toml` (defaults are written on first run): simulation interval, steps per birth, observed radius, villager cap per settlement, and the debug-command switch.

### What to actually verify

- **Persistence.** Found a kingdom, quit to title, reload the world, `/civ info`. Everything should still be there. This is the single most valuable thing to test, because it exercises every codec.
- **Growth while away.** Queue a build, walk 500 blocks away so the chunk unloads, come back, `/civ info`. Progress should have continued — that is the "records not entities" architecture working.
- **Threat decay.** `/civ threat 5`, then `/civ step 5`, then check it is 0 and does not go negative.

### Dev server instead of client

```bash
./gradlew :neoforge:runServer
```

This will stop on first launch until you accept Minecraft's EULA by setting `eula=true` in the generated `run/eula.txt`. **I have not done that for you** — it is a legal agreement, and accepting it is your call, not mine.

---

## What is here

**`common/`** — the simulation, complete and tested:

- `geom/SimPos` — loader-free block position
- `person/Person` — an inhabitant **as a record, not an entity** (see below)
- `person/Profession` — placeholder enum, to become datapack-driven
- `settlement/Settlement` — roster, claim radius, build queue, threat level
- `settlement/BuildTask` — construction that progresses in unloaded chunks
- `kingdom/Kingdom` — settlements plus diplomacy
- `kingdom/Standing` — diplomatic posture
- `world/SimWorld` — root; owns the slow tick
- `platform/WorldBridge` — the seam

**`neoforge/`** — minimal but working wiring:

- `KingdomsMod` — entrypoint; one `SimWorld` per dimension, driven from the server tick
- `bridge/NeoForgeWorldBridge` — translates `SimPos` ↔ `BlockPos`, answers "is anyone watching?"
- `save/KingdomsSavedData` — durable kingdom storage, written with the level
- `save/KingdomsCodecs` — codecs for every simulation type
- `command/KingdomsCommand` — `/civ` debug commands; the only window into the sim right now

### A 26.2 API note that will bite you

`ResourceLocation` is now **`net.minecraft.resources.Identifier`**. Nearly every tutorial and Stack Overflow answer still says `ResourceLocation`, and will not compile. Build it with `Identifier.fromNamespaceAndPath(MOD_ID, path)`.

Related renames in the same area: `DimensionDataStorage` → `SavedDataStorage`, reached via `ServerLevel.getDataStorage()`. `SavedData` itself is now bare — just dirty tracking — with serialization supplied by a `SavedDataType<>(Identifier, Supplier<T>, Codec<T>)`.

When in doubt, read the real thing rather than a tutorial. The decompiled sources are already on disk at `neoforge/build/moddev/artifacts/minecraft-patched-26.2.0.59-sources.jar`.

---

## What a step actually does

Be clear-eyed about this: **the simulation currently contains two arithmetic rules.** Everything else is modeled, persisted, and inert.

The full call chain, every 100 game ticks:

```
SimWorld.step()                          builds a SimContext(bridge, step, settings)
  └─ for each Kingdom:  Kingdom.step(ctx)
       ├─ ExpansionPlanner.advance(ctx)      chartered towns daughter camps — FOUNDING.md
       └─ for each Settlement:  Settlement.step(ctx)
            ├─ advanceStage(ctx)             camp → homestead → … → town
            ├─ planNextBuild(ctx)            the stage's program, then the catalogue
            ├─ PathPlanner.advance(ctx)      one building joined to the road network
            ├─ PerimeterPlanner.advance(ctx) stakes and raises the wall — TOWN
            ├─ InnPlanner.advance(ctx)       the caravan calls
            ├─ advanceBuildQueue(ctx)
            ├─ materializePending(ctx)
            ├─ FoodPlanner.advance(ctx)      fields, errands, hunger, starvation
            ├─ HaulPlanner.advance(ctx)      loads walking across the village
            ├─ LumberPlanner.advance(ctx)    the camp claims its woodland
            ├─ JobPlanner.retrainOne()
            ├─ PopulationPlanner.advance(ctx)
            ├─ decayThreat()
            └─ RaidPlanner.advance(ctx)
```

**Rule 0 — planning.** If the build queue is empty, decide what to build next and
queue it. Below village size that decision belongs entirely to the **stage's own
build program**; from village size the catalog's priorities resume, with the town
hall gated to town stage. The settlement claims the ground it needs. Full rules in
**[BUILD_DECISIONS.md](BUILD_DECISIONS.md)** and **[FOUNDING.md](FOUNDING.md)**.

**Rule 0.5 — jobs.** Below village size the staffing table does not run at all:
every settler is a **pioneer** who labors as builder and farmer at once, and
trades crystallize as the stages demand them (a sentry and a woodcutter when the
camp fortifies; the rest dissolve into the ordinary table at village). From village
size, at most one idler per step takes up the trade the settlement is most short
of. Working residents are never reassigned by the table — the mix corrects through
idlers and newborns — but a genuine shortage still can: a camp that runs out of
timber names a woodcutter on the spot.

**Rule 0.75 — population.** Newcomers are gathered into families, families claim empty houses, and housed families with spare room have children. A family with no house, or a full house and nowhere to move, does not grow. **Children take the settlement's most-needed job**, falling back to the family trade when nothing is short. Full rules in **[POPULATION.md](POPULATION.md)**.

**Rule 1 — construction.** Count residents who labor as `BUILDER` (which includes
every pioneer below village size). Add that many work units to the *first* task in the build queue. If it reaches `requiredWork`, drop it from the queue and **record a `Building`** on the settlement, stamped with the step it finished on. If there are no builders, or the queue is empty, nothing happens.

**Rule 2 — deferred placement.** For each recorded building not yet drawn, ask the bridge whether its chunk is loaded. If it is, paint it into the world and mark it materialized. If not, leave it pending and try again next step.

**Rule 3 — threat decay.** If `threatLevel > 0`, subtract 1.

**Rule 4 — hostile pressure.** Threat rises to match real hostiles inside the claim. On each settlement's own raid clock (hashed, deterministic), a raid strikes: real zombies if a player is watching, arithmetic against the garrison if not, with losses paid in lives and everything written to the settlement's persisted event log. Full rules in **[DEFENSE.md](DEFENSE.md)**.

That is the whole thing. `stepsElapsed` increments and the step ends.

Rule 2 is where the two-fidelity architecture actually earns itself: a building finished in an unloaded chunk is real in the simulation immediately, and appears as blocks whenever someone next turns up. Construction is never gated on being watched.

### Modeled but inert

These exist as data, serialize correctly, and are read by nothing:

| Thing | Status |
|---|---|
| `FARMER`, `TRADER` | Staffed by the job planner, but farms produce nothing and nobody trades |
| `Kingdom.diplomacy` / `Standing` | Stored and persisted, never consulted by any logic |
| `threatLevel` as an *input* | Raised by hostiles and raids, decays, shown in info — but nothing acts on it yet (guards do not muster, construction does not pause) |

Nothing founds a settlement except the debug command.

Two rules that ARE live and worth knowing: **a view villager killed means that person dies** — roster and family, permanently — and **settlements are raided on their own clocks** whether or not anyone is watching (`defense.raids_enabled` to turn off). See [DEFENSE.md](DEFENSE.md).

### What buildings do now

A completed `BuildTask` becomes a `Building` on the settlement — blueprint id, origin, the step it finished on, and whether it has been drawn yet. The settlement is the authority on what it has built; the blocks in the world are a projection of that list, not the other way round.

This matters more than it looks. Reconstructing settlement state by scanning placed blocks is the trap that makes these mods unmaintainable — a player breaks a wall and your data model is now a guess. Here, the simulation always knows, and the world is repaintable from it.

`NeoForgeWorldBridge.materializeBlueprint` currently draws **placeholder geometry** — a 5×5 stone brick platform with oak corner posts and a gold block marker. Deliberately crude: the value is in *when* it is called, not what it looks like. Replacing it with datapack-defined blueprints changes nothing above it.

---

## The two rules that make this scale

**1. People are records. Entities are views.**

A `Person` exists whether or not any chunk is loaded. When a player comes within `OBSERVED_RADIUS` (96 blocks), the platform layer may spawn a mob to *represent* that person, and writes state back to the record before despawning it.

Authoritative state never lives on the entity. Thousands of `Person` records are cheap; thousands of ticking `Brain` entities are not possible on any loader or hardware.

**2. The simulation ticks slowly.**

Minecraft ticks 20×/second. `SimWorld.SIM_INTERVAL_TICKS` is 100 — one simulation step every 5 seconds. That is roughly two orders of magnitude of headroom, and players cannot perceive the difference in settlement growth or economy.

Raising that interval is the cheapest performance lever you have. Reach for it before optimizing anything else.

---

## Next steps

The live worklist is **[GOALS.md](GOALS.md)** — this is the simulation-layer view
of the same thing, kept short.

1. **Datapack-driven content.** The one structural gap left, and the reason
   Millénaire could carry six cultures: **21 building types, 11 professions**, a
   nutrition table and a staffing table, all hardcoded in Java. Blueprints are
   already data (see [KEYSTONE.md](KEYSTONE.md)); the *tables* are not. The build
   catalog is the one to do first, since that is what lets cultures want
   different things.
2. **Construction materials, and the builder's hut screen.** Both halves exist —
   timber in the stores, and an ordered block sequence whose cursor can stop. What
   is missing is the interactive supply GUI: walk up, see what a build needs, hand
   it over.
3. **Tuning.** Postponed all along, deliberately. Every loop is watchable now, so
   the numbers can be judged rather than guessed.
4. **Age and mortality**, once there is enough economy for population pressure to
   mean something.

---

## Renaming the project

The mod is scaffolded as `kingdoms` / `com.kingdoms`. To rename:

1. Edit `mod_id`, `mod_name` and `mod_group_id` in [`gradle.properties`](gradle.properties)
2. Rename the `com/kingdoms` package directories in both modules
3. Update `MOD_ID` in `KingdomsMod`
4. Update `rootProject.name` in [`settings.gradle`](settings.gradle)

Everything else reads from `gradle.properties`, including `neoforge.mods.toml`.

---

## Not yet included

- `pack.mcmeta` — add when you ship assets or data, with the `pack_format` matching 26.2
- Data generation run config
- Networking / UI
- Any client-side code

---

## License

GPL-3.0 — see [LICENSE](LICENSE).

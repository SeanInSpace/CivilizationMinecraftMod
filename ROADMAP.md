# Roadmap to MVP

**Target:** the smallest version of this mod that a stranger could install, play, and understand without being told what it is.

Companion docs: [BUILD_DECISIONS.md](BUILD_DECISIONS.md), [POPULATION.md](POPULATION.md), [MODLOADER_DECISION.md](MODLOADER_DECISION.md).

---

## What "MVP" means here

One paragraph, and everything below is judged against it:

> You start a settlement. It has **visible inhabitants** who live in houses, hold jobs, and raise families. It **builds itself** over time into recognisable buildings. When night falls and mobs come, **guards defend it** — and it survives, or doesn't, whether or not you are standing there watching. You can find out what's going on without typing a debug command.

That is the original brief: *entities with individual AI, and a town that takes care of itself against mobs.* The simulation underneath is largely built. **Almost none of what a player would actually see or feel is.**

---

## Where things stand

**Done and tested (38 tests):**

- Kingdoms → settlements → families → people, all persisting correctly
- Settlements decide what to build and where, expanding territory as they grow
- Families claim houses, have children, split when full; housing gates growth
- Two-fidelity architecture proven: construction continues in unloaded chunks, materialises on return
- Slow-tick scheduler, save/load round-trip, debug commands

**The gap:** all of it is invisible. There are zero entities, buildings are stone placeholders, nothing threatens the town, and the only way in is a cheat command.

---

## Phases

Ordered by dependency and by how much each unlocks — not by size. Effort is rough relative sizing, not a schedule.

### Phase 0 — Groundwork · **S** · ✅ DONE

| Item | Status |
|---|---|
| `WorldBridge.surfaceHeight` + terrain-aware plots | ✅ Plots snap to terrain at planning time when the chunk is loaded; placement snaps again regardless, so estimates self-correct. |
| Config file | ✅ `serverconfig/kingdoms-server.toml`: sim interval, steps per birth, observed radius, villager cap, debug-command switch. Read once at server start into an immutable `SimSettings`. |
| Stress test | ✅ **20 settlements, 640 people, 340 buildings: 520 µs per sim step — amortized 5.2 µs per game tick** (~0.01% of the 50ms budget). Runs in CI as a determinism check too: population lands on exactly 640 every time. |

---

### Phase 1 — Inhabitants you can see · **L** · ✅ DONE

Implemented as recommended: vanilla `Villager` entities carrying a serialized `person_id` data attachment, spawned within the observed radius (96, configurable), released 32 blocks past it (hysteresis, so nobody flickers at the boundary), capped per settlement.

The hydration state machine lives in `common/` (`EmbodimentPlanner`) and is unit-tested without a game — spawn, release, hysteresis, cap, no-double-spawn. The NeoForge side (`PersonEntityManager`) only executes the plan, and maintains the invariants:

- **One entity per person, ever.** The join hook culls any tagged villager the manager didn't spawn this session — a crash-leaked entity is cancelled on chunk load rather than becoming a duplicate.
- **Records are the authority.** Positions flow entity → record every second; release writes back and discards; server stop releases everything before the level saves.
- **Death is real:** a view villager killed means the person dies — roster and family. The seed of Phase 3.

Known quirks, accepted for MVP: villagers are vanilla underneath (they may panic, gossip, attempt to breed); a straggler beyond 24 blocks from home is walked back once a second rather than given a real schedule.

---

### Phase 2 — Jobs assigned by need · **S** · ✅ DONE

`JobPlanner`: a staffing table in the build-catalogue mould (builder 1 + per 5 @ 90, guard per 8 @ 80, farmer per 5 @ 70, trader per 15 @ 50). Newborns take the most-needed trade, falling back to family inheritance when fully staffed; one idler per step retrains. Working residents are never reassigned. Fully unit-tested; a settlement founded entirely with builders now grows its own guards and farmers — the Phase 3 precondition is met.

---

### Phase 3 — The town defends itself · **M** · ✅ DONE

The original brief, delivered. Full spec: **[DEFENSE.md](DEFENSE.md)**.

- **Threat detection** ✅ — threat mirrors real hostiles inside the claim each step, and raids raise it in both fidelities; decay reads as "how recently something happened".
- **Observed combat** ✅ — raids spawn real zombies in a ring at the town edge; guards engage the nearest hostile once a second (charge, strike, vanilla retaliation — guards can lose). Every villager death kills its person through the existing death path.
- **Unobserved combat** ✅ — deterministic arithmetic: guards ×2 + structure bonuses (watchtower +3) versus `1 + pop/8 + jitter`. Repelled cleanly, or the deficit is paid in lives — guards first. Casualties leave their families; emptied houses free up.
- **Consequences & evidence** ✅ — every settlement keeps a persisted, bounded event log ("Raid of 4 repelled…", "Esa Cooper was killed"), shown in `/kingdoms info`. The done-when criterion — leave overnight, return, read what happened — is literally a feature.

No randomness anywhere: raid clocks and strengths hash settlement id + step, preserving whole-sim determinism. `/kingdoms raid [strength]` forces one for testing; `defense.raids_enabled` config switch for peaceful mode. Deliberate MVP gaps (zombies-only, no building damage, threat is informational) are listed in DEFENSE.md.

---

### Phase 4 — Buildings that look like buildings · **M** · ✅ DONE

Implemented template-first with a procedural fallback (`BlueprintPlacer`):

- **Structure templates win when present** — drop `data/kingdoms/structure/<name>.nbt` in a datapack (authored in-game with structure blocks) and towns build that instead. Per-culture architecture with zero code.
- **Procedural fallback out of the box** — houses are timber-framed cabins with windows, doors and lanterns; the hall is stone-brick; farms are fenced tilled fields with water and crops; watchtowers are 8-block cobble towers with crenellations; store/workshop get barrels and benches.
- **Terrain fit** — every placement lays a cobble foundation beneath the footprint and clears headroom, so buildings sit sanely on slopes.

Scoping note vs the original plan: rather than hand-authoring `.nbt` art blind, the *infrastructure* for templates is done and the fallback is decent. Authoring real per-building templates in-game is now a pure content task.

---

### Phase 5 — Meeting the town · **M** · ✅ DONE

- **Founding Charter item** — craftable (paper + emeralds + book), used on ground founds a deterministically-named settlement seeded with four settlers; refuses sites too close to existing claims; consumed on use.
- **Trading & job visuals** — view villagers now carry the matching vanilla profession (builder→mason, guard→weaponsmith, farmer→farmer, trader→librarian, idler→nitwit), which buys the job-specific *outfit* and working vanilla *trade offers* on right-click, both nearly free.
- **In-game information** — sneak-use a charter for a written report of the nearest settlement: population, families, defense, construction, recent history. No debug commands needed to play.

Scoping note: the report is chat text, not a GUI screen — a screen is client-side work that adds polish, not information.

---

### Phase 6 — Shippable · **S** · ✅ DONE

- Mod icon (`icon.png`, wired via `logoFile`), version bumped to **0.5.0**, metadata complete. `pack.mcmeta` deliberately omitted — NeoForge synthesizes pack metadata for mod jars, and shipping one with a wrong format number only causes warnings.
- `/kingdoms` debug commands gated behind `debug.commands_enabled` config.
- Stress benchmark runs in every build (still 640-exact, ~530 µs/step with defense in the loop).
- **[PLAYING.md](PLAYING.md)** — the player-facing guide: installing, crafting the charter, what the town does on its own, config, custom building templates.

**Ship check:** `kingdoms-0.5.0.jar` contains the item assets, recipe, lang, icon and metadata; dedicated server boots it clean.

---

## Deliberately not in MVP

Each of these is real work that makes the mod better and none of it is needed to prove the thing works.

| Deferred | Why it can wait |
|---|---|
| **Age and mortality** | Towns growing forever is a balance problem, not a broken-mod problem. Add once there is something to balance against. |
| **Natural worldgen** | Large. The founding charter covers the MVP need. |
| **Datapack-driven content** | I argued earlier for doing this before content volume grows — that still holds, but at six building types and one culture the retrofit is cheap, precisely because `BuildingType` is already just numbers. It becomes urgent when you want a second culture, not before. |
| **Multiple cultures** | Depends on datapacks. This is the first thing to do *after* MVP. |
| **Kingdom diplomacy** | Modelled and persisted, read by nothing. Needs multiple settlements interacting, which needs a reason for them to. |
| **Economy and resources** | Buildings cost only labour. Making them cost materials is a whole subsystem. |
| **Migration between settlements** | Needs several towns worth travelling between first. |
| **Custom entity models** | Vanilla villagers are fine. Big art and client-code cost, no mechanical gain. |
| **Fabric port** | The `common/` split keeps it cheap whenever you want it. No reason now. |

---

## Decision points

Two calls in here are genuinely revisitable, flagged rather than buried:

1. **Vanilla `Villager` vs a custom entity** (Phase 1). Recommending vanilla for speed. Reconsider if you need behaviour that fights the vanilla brain harder than expected.
2. **Founding charter vs natural worldgen** (Phase 5). Recommending the charter for MVP. Worldgen is closer to the Millénaire experience and is the obvious first post-MVP feature.

---

## The critical path

```
Phase 0 ✅ → 1 (villagers) ✅ → 2 (jobs) ✅ → 3 (defense) ✅ → 4 (buildings) ✅ → 5 (charter) ✅ → 6 (ship) ✅
```

**MVP complete.** The original brief — individual inhabitants, a town that takes care of itself against mobs — is a playable, installable mod: craft a charter, found a town, and it lives. What remains beyond MVP is in the deferred table below; the first two worth doing are **datapack-driven cultures** (the catalogue, staffing table and structures are all shaped for it) and **age/mortality**.

One caveat carried forward: everything is machine-verified (64 tests, server boot), but the *feel* — building spacing, raid pacing, villager behaviour on real terrain — needs sustained in-game play, and the numbers it will suggest changing all live in config or one-line constants.

# Roadmap

**Where this project is, how it got here, and what is worth doing next.**

Phases 0–6 below were the plan to reach a playable MVP; all are complete. Everything
built since is recorded under [Beyond MVP](#beyond-mvp--what-actually-got-built),
because it was driven by playtesting rather than by this document.

Companion docs: [PLAYING.md](PLAYING.md) (for players) · [BUILD_DECISIONS.md](BUILD_DECISIONS.md) · [POPULATION.md](POPULATION.md) · [DEFENSE.md](DEFENSE.md) · [EXPANSION.md](EXPANSION.md) · [VILLAGER_AI.md](VILLAGER_AI.md) · [MODLOADER_DECISION.md](MODLOADER_DECISION.md)

---

## What "MVP" meant

One paragraph, and the phases were judged against it:

> You start a settlement. It has **visible inhabitants** who live in houses, hold jobs, and raise families. It **builds itself** over time into recognisable buildings. When night falls and mobs come, **guards defend it** — and it survives, or doesn't, whether or not you are standing there watching. You can find out what's going on without typing a debug command.

That was the original brief: *entities with individual AI, and a town that takes care
of itself against mobs.* It is met. A charter founds a town, and the town lives.

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

> **Later reversed.** Settlers are no longer villagers at all — the vanilla Brain
> assumes the entity owns its own state, which fights records-first at every turn.
> They are now a plain humanoid wearing the default skin, with almost no AI of their
> own. See [VILLAGER_AI.md](VILLAGER_AI.md).

---

### Phase 2 — Jobs assigned by need · **S** · ✅ DONE

`JobPlanner`: a staffing table in the build-catalogue mould (builder 1 + per 5 @ 90, guard per 8 @ 80, farmer per 5 @ 70, trader per 15 @ 50). Newborns take the most-needed trade, falling back to family inheritance when fully staffed; one idler per step retrains. Working residents are never reassigned. Fully unit-tested; a settlement founded entirely with builders now grows its own guards and farmers — the Phase 3 precondition is met.

---

### Phase 3 — The town defends itself · **M** · ✅ DONE

The original brief, delivered. Full spec: **[DEFENSE.md](DEFENSE.md)**.

- **Threat detection** ✅ — threat mirrors real hostiles inside the claim each step, and raids raise it in both fidelities; decay reads as "how recently something happened".
- **Observed combat** ✅ — raids spawn real zombies in a ring at the town edge; guards engage the nearest hostile once a second (charge, strike, vanilla retaliation — guards can lose). Every villager death kills its person through the existing death path.
- **Unobserved combat** ✅ — deterministic arithmetic: guards ×2 + structure bonuses (watchtower +3) versus `1 + pop/8 + jitter`. Repelled cleanly, or the deficit is paid in lives — guards first. Casualties leave their families; emptied houses free up.
- **Consequences & evidence** ✅ — every settlement keeps a persisted, bounded event log ("Raid of 4 repelled…", "Esa Cooper was killed"), shown in `/civ info`. The done-when criterion — leave overnight, return, read what happened — is literally a feature.

No randomness anywhere: raid clocks and strengths hash settlement id + step, preserving whole-sim determinism. `/civ raid [strength]` forces one for testing; `defense.raids_enabled` config switch for peaceful mode. Deliberate MVP gaps (zombies-only, no building damage, threat is informational) are listed in DEFENSE.md.

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
- **Trading & job visuals** — view villagers carried a matching vanilla profession, which bought job-specific outfits and trade offers nearly free. *(Lost when settlers stopped being villagers; right-click now greets, gives food, or reads their pockets instead.)*
- **In-game information** — sneak-use a charter for a written report of the nearest settlement: population, families, defense, construction, recent history. No debug commands needed to play.

Scoping note: the report is chat text, not a GUI screen — a screen is client-side work that adds polish, not information.

---

### Phase 6 — Shippable · **S** · ✅ DONE

- Mod icon (`icon.png`, wired via `logoFile`), version bumped to **0.5.0**, metadata complete. `pack.mcmeta` deliberately omitted — NeoForge synthesizes pack metadata for mod jars, and shipping one with a wrong format number only causes warnings.
- `/civ` debug commands gated behind `debug.commands_enabled` config.
- Stress benchmark runs in every build (still 640-exact, ~530 µs/step with defense in the loop).
- **[PLAYING.md](PLAYING.md)** — the player-facing guide: installing, crafting the charter, what the town does on its own, config, custom building templates.

**Ship check:** `kingdoms-0.5.0.jar` contains the item assets, recipe, lang, icon and metadata; dedicated server boots it clean.

---

## Beyond MVP — what actually got built

The six phases above delivered the brief and were finished at `76496ba`. Everything
since has been unplanned work, driven by playtesting rather than by this document.
Recorded here so the roadmap stops lying about where the project is.

| Added | What it is |
|---|---|
| **Village day** | Trades walk to their workplaces by day, everyone home at dusk, civilians shelter under threat. |
| **Food chain** | Fields → granary → market → family pantry → mouths, every link real held state. |
| **Hunger & starvation** | Personal hunger 0–99, visible debuffs at 60, permanent death at 99. |
| **Hauling** | Goods never teleport — somebody walks each load, and it exists nowhere else while carried. |
| **Real inventories** | Settlers carry actual items and eat the actual item they hold. Hand food over; sneak-click to read pockets. |
| **Visible construction** | Buildings rise block by block in mason's order, laid by builders carrying the material. |
| **Kingdom expansion** | A full town sends a founding party out to daughter a new settlement. |
| **The timber trade** | Lumberjacks fell and replant inside a work area you direct with a hut block. |
| **Self-repair** | A town that notices somebody cannot reach their own front door, and builds them steps. |

Two of the MVP decision points were revisited in the process:

1. **Vanilla `Villager` → custom entity.** Reversed. The villager Brain assumes the
   entity owns its own state, which fights the records-first architecture at every
   turn; settlers are now a plain humanoid with the Steve skin and almost no AI of
   their own. See [VILLAGER_AI.md](VILLAGER_AI.md).
2. **Founding charter vs worldgen.** Unchanged — the charter still carries it, and
   worldgen is still the obvious first thing to add.

---

## Still deliberately out

| Deferred | Standing |
|---|---|
| **Datapack-driven content** | **The one structural gap.** Deferred at "six building types and one culture"; it is now nine buildings, six professions, a nutrition table, a staffing table and a work-area system, all hardcoded. Still tractable — everything is numbers and string ids — but the argument for waiting weakens with every trade added. |
| **Multiple cultures** | Blocked on datapacks. The reason to do them. |
| **Natural worldgen** | Untouched. Towns are player-founded or daughtered from an existing one. |
| **Age and mortality** | Untouched. People die of violence and starvation, never of years. |
| **Kingdom diplomacy** | Still modelled, still persisted, still read by nothing. Now that kingdoms genuinely have several settlements, it finally has something to act on. |
| **Materials for construction** | Buildings still cost only labour — but timber now accumulates, and the block plan is already ordered so a supply gate stops the cursor mid-list. Both halves are waiting. |
| **Fabric port** | Cheap whenever wanted; `common/` has stayed clean of Minecraft imports throughout. |

---

## Where the effort should go next

In rough order of how much each unlocks:

1. **Datapacks.** Everything above is shaped for it and nothing else can start until
   it lands. It is what separates *Millénaire-shaped* from *Millénaire-like*.
2. **Construction materials.** The two halves already exist — timber in the stores,
   and an ordered block plan whose cursor can stop. Joining them makes the lumber
   trade matter and gives hauling a second purpose.
3. **Tuning.** Deliberately postponed all along. Now that every loop is watchable,
   the numbers can be judged instead of guessed.
4. **Age and mortality**, once there is enough economy for population pressure to
   mean something.

---

## Status

**118 tests**, all green. Stress benchmark: 20 settlements, 640 people, 340
buildings at roughly 0.6 ms per simulation step — about 0.01% of a tick budget.

The standing caveat has not changed, only sharpened: everything is machine-verified,
and the *feel* is not. The last three features — lumberjacks, real inventories and
access repairs — pass their tests and load clean but have never been watched running
in a world.

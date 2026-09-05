# What every citizen does, and in what order

Written from the code, not from the design. Where this and the code disagree,
the code is right and this is a bug.

The question a player asks is "why is that person doing *that*?" and the answer
is almost always an ordering — some earlier rule claimed them before the obvious
one got a chance. So this document is arranged by order of application rather
than by profession alone: first the two clocks and what each of them runs, then
one table per trade with the trigger that fires, the action it produces, and the
constant that gates it.

---

## 1. Two clocks

A person is one record — `com.kingdoms.sim.person.Person` — and exactly one of
those records exists whether or not anybody is looking at them. What changes is
who moves them.

| | **The clock** | **Watched** |
|---|---|---|
| Driver | `Settlement.step`, from the slow scheduler | `PersonEntityManager.tick`, from the game tick |
| Cadence | every `SimWorld.SIM_INTERVAL_TICKS` = 100 ticks (5 s) | every `PersonEntityManager.TICK_INTERVAL` = 20 ticks (1 s); construction every 5 |
| Who is in it | everyone | everyone within `observedRadius` (96 blocks), up to `embodyCapPerSettlement` (64) |
| Movement | `HaulPlanner.stepToward`, `ABSTRACT_TRAVEL_BLOCKS` = 12 per step | real mob navigation |
| Production | arithmetic: rate × heads | real blocks broken and placed |

They are not alternatives. **Both run every step, on the same records.** The
watched pass steers bodies and does real work; the clock keeps the books and
fills in whatever the bodies could not manage. Which is the rule the whole design
turns on:

> **Being watched must never starve a town, and must not bankrupt one either.**

Every trade with a watched worker therefore has a *grace* constant — how long the
clock stands aside before deciding the real hands are not managing and paying out
anyway. They are all the same number for the same reason:

| Constant | Value | Guards |
|---|---|---|
| `FoodPlanner.WATCHED_HARVEST_GRACE_STEPS` | 12 | a farm nobody can reach |
| `LumberPlanner.WATCHED_WORK_GRACE_STEPS` | 12 | a camp on open grass |
| `MinePlanner.WATCHED_WORK_GRACE_STEPS` | 12 | a mine with no exposed stone |
| `HaulPlanner.EMBODIED_STALL_STEPS` | 12 | a delivery the pathfinder cannot make |
| `Settlement.WATCHED_BUILD_GRACE_STEPS` | 12 | builders boxed out of a roof course |

`EmbodimentPlanner.RELEASE_MARGIN` (32 blocks) is the hysteresis that stops
somebody standing on the radius flickering in and out of existence.

---

## 2. The order the clock applies

`Settlement.step`, in source order. Anything earlier in this list claims a person
before anything later gets to ask.

| # | Pass | What it decides about people |
|---|---|---|
| 1 | `putAwayLoosePile` | — (goods) |
| 2 | `advanceStage` → `StagePlanner.crystallize` | pioneers become guards, lumberjacks, idlers |
| 3 | `planNextBuild` | — (the queue everyone else reads) |
| 4 | `PathPlanner`, `PerimeterPlanner` | — |
| 5 | `InnPlanner` | — (caravan, every 48 steps) |
| 6 | `advanceBuildQueue` | builders lay work; a carpenter counts as one extra pair of hands |
| 7 | `materializePending` | — |
| 8 | **`FoodPlanner.advance`** | **farmers, traders and one member per household get errands** |
| 9 | **`SupplyPlanner.advance`** | **one bulk courier a step — see §4** |
| 10 | `HaulPlanner.advance` | everybody carrying anything takes a step of their walk |
| 11 | `LumberPlanner`, `MinePlanner`, `SmithPlanner` | unwatched production credited |
| 12 | `equipWorkers` | one tool issued a step |
| 13 | `JobPlanner.retrainOne` | at most one person changes trade |
| 14 | `PopulationPlanner.advance` | births, housing, eviction |
| 15 | `trackFedStreak`, `decayThreat` | — |
| 16 | `RaidPlanner.advance` | alarm tier; raid resolution |
| 17 | `RepairPlanner.advance` | queues repairs |

**Food before supply is load-bearing.** A farmer or trader who has been given
their own trade's errand at step 8 is already carrying something at step 9, so the
bulk courier check finds them busy and passes over them. Reverse the two and the
town's dinner loses to its timber.

## 3. The order the watched pass applies

`PersonEntityManager.tick`, per settlement, in source order:

`syncPositions` → `EmbodimentPlanner` (release, then embody) → `pickUpLitter` →
`unloadAtStore` → `ringTheBell` → **`dailyRoutine`** → `checkHouseAccess` →
`workLumberjacks` → `workFarmers` → `workMiners` → `workShepherds` → `layPaths` →
`workWall` → `PerimeterLayer.draw` → `StoreSync.reconcile` → `freeStrandedPeople`
→ `applyHungerEffects` → `guardCombat`.

`dailyRoutine` runs **before** the trade workers and decides where a body is
walking; the trade workers then override that steering block by block for the
trades that have one. `dailyRoutine`'s own order of questions, per person:

1. **Builder on an active site?** `continue` — `tickConstruction` is steering them.
2. **Lumberjack, calm, daylight, not hauling, camp claimed?** `continue` — `workLumberjacks` steers.
3. **Farmer, calm, daylight, not hauling?** `continue` — `workFarmers` steers.
4. Otherwise: show the load if carrying, then pick a destination —
   **alarm** (`Alarm.callsIn`) → home;
   **night** and not a guard → home;
   otherwise `workplaceFor`.

`workplaceFor` itself is ranked: an outstanding **haul** outranks everything; then
putting away an armful worth `Economy.WORTH_THE_WALK` (20) or a full six slots;
then the day job by profession.

---

## 4. Who carries a load

Two separate things are called "hauling" and they are not the same:

* **Trade errands** — a farmer's field-to-granary run, a trader's granary-to-stall
  run, a family's shopping. Handed out by `FoodPlanner.assignHauls` /
  `assignPantryRuns`. These *are* the trade's work.
* **Bulk supply** — timber and stone toward whatever is being built. Handed out by
  `SupplyPlanner.advance`, one a step, and the courier is chosen by
  `HaulPlanner.courierFor`.

`courierFor` is the answer to "why is my carpenter carrying a stack of supplies?"
and it now goes:

| Tier | Who | Why |
|---|---|---|
| 1 | `IDLER` | nothing is lost, and only this tier short-circuits the search |
| 2 | a trade with nothing in front of it — see below | a standing worker is worth less than a walking one |
| — | **never** a `BUILDER` (or a pioneer laboring as one) | the demand *is* a build |
| — | **never** a `GUARD` | nothing takes a load off a guard when the bell rings |
| 3 | **nobody** — the load waits | a waiting haul costs a walk; a stopped workshop costs everything it would have made |

Tier 2 asks `HaulPlanner.hasWorkInFront`, which is the town's opinion of whether
this trade has anything queued today:

| Trade | Has work when |
|---|---|
| `FARMER` | any `CROP_FARM` stands |
| `LUMBERJACK` | a `LUMBER_CAMP` stands **and** `LumberPlanner.wantsMoreTimber` |
| `MINER` | a `MINE` stands **and** `MinePlanner.wantsMoreStone` |
| `SMITH` | `SmithPlanner.hasWorkInFront` — a smithy, something under its ceiling, and iron + fuel |
| `MILLER` | **never** — see below |
| `CARPENTER` | a `CARPENTRY` stands **and** the build queue is not empty |
| `SHEPHERD` | an `ANIMAL_FARM` stands |
| `TRADER` | `FoodPlanner.hasStallToStock` — the granary holds `TRADER_CARRY` (12) **and** some stall is below `MARKET_STOCK_CAP` |
| `IDLER`, `PIONEER` | never |
| `BUILDER`, `GUARD` | always (they are excluded outright anyway) |

A `PIONEER` is listed as never busy but is **not** tier 1: only `IDLER`
short-circuits the search with an immediate return. A pioneer falls through to
this table, answers "nothing in front of me", and is recorded as tier-2 slack —
so an idler always beats a pioneer, and a pioneer competes with slack tradesmen
on whatever order `residents()` happens to yield.

The `MILLER` row is the one place this table says *never* for a trade that has a
building standing. It is deliberate, and it is the mirror image of the carpenter.
Both contributions are pure headcounts that never ask what the person is holding
(`FoodPlanner.millRuns`, `Settlement.advanceBuildQueue`), and neither has a
watched worker — so on today's arithmetic a hauling carpenter and a hauling
miller both cost the town exactly nothing. The difference is what a player sees:
a carpenter has a bench to be dragged away from, and `workplaceFor` ranks an
outstanding haul above every day job, so conscripting him visibly empties the
workshop that was the whole complaint. A miller has no bench — he stands beside
the mill doing nothing anybody can watch. So the carpenter keeps his exemption
against the day the headcount becomes real work, and the miller is the town's
free pair of hands.

The `TRADER` row asks `FoodPlanner` rather than restating its gate, for the same
reason the smith's row asks the forge. "A market stands and the granary holds
twelve" is a different question from "is there an errand for this trader": a lone
stall already at its cap passes the first and fails the second, and a trader
believed busy on that step is a courier the town had and did not use.

The carpenter is the strict case and it is not an accident: `SupplyPlanner` only
moves goods *toward a build*, so it only asks for a courier while something is
being built — and a carpenter's whole contribution is the pre-cut components that
speed that build (`Settlement.advanceBuildQueue` counts a working carpentry as one
extra builder). A carpenter in a town with a carpentry is never free at the exact
moment the question is asked.

---

## 5. The trades

Every table below is in the order the code applies the rules. "Clock" is what
happens with nobody watching; "Watched" is what a player sees. Where the two
columns disagree, §6 says so.

### Everybody, before their trade

| # | Trigger | Action | Gate |
|---|---|---|---|
| 1 | `hunger >= 60` and the town is **not** starving | stops farming, hauling, building; drops any load back where it came from | `Person.HUNGER_WEAK`, `FoodPlanner.heldBackByHunger` |
| 2 | `hunger >= 60`, town **is** starving | keeps working — the weakness rule is suspended, because weak hands bring in no food | `Settlement.isStarving` |
| 3 | `hunger >= 30` | eats from pockets, else the family pantry | `Person.HUNGER_HUNGRY`, `PANTRY_PER_MEMBER` = 3 |
| 4 | `hunger >= 90`, nothing in pockets | fetches from granary → stall → field, in that order; if the pockets are full, eats where it stands | `Person.HUNGER_SEVERE`, `eatWhereItStands` |
| 5 | `hunger == 99` for 10 steps | dies, permanently, into the town's history | `STARVATION_GRACE_STEPS` |
| 6 | alarm raised and `Alarm.callsIn(trade)` | walks (WARY) or runs (ALARMED) home | `WARY_AT` = `Danger.ROUTINE`, `ALARMED_AT` = `Danger.OVERMATCH` |
| 7 | dark outside, not a guard | goes home | `level.isDarkOutside()` |
| 8 | carrying goods worth 20+, or six full slots | detours to the market to hand them in | `Economy.WORTH_THE_WALK` = 20, `Inventory.SLOTS` |
| 9 | no tool, not an idler | issued one from the rack, one person per step | `SmithPlanner.issueTool` |

`Alarm.callsIn` is worth reading twice: at **WARY** only trades that
`worksBeyondTheWalls` (lumberjack, miner) come in — everyone else keeps working,
because a town that downs tools over one skeleton never gets anything done. At
**ALARMED** everybody but the watch goes home.

### PIONEER

The founding generalist. Below VILLAGE a pioneer *is* the builder and the farmer
(`Settlement.laborsAs`), which is what lets a camp of four function before the
staffing table would give it a single farmer.

| # | Trigger | Clock | Watched | Gate |
|---|---|---|---|---|
| 1 | stage below VILLAGE, larder under 5/mouth | forages 1 meal per 3 hands | same (arithmetic only) | `FORAGERS_PER_MEAL` = 3, `FORAGE_CEILING_PER_MOUTH` = 5 |
| 2 | a build is queued | counts as a builder | walks to the site, lays blocks | `laborsAs(BUILDER)` |
| 3 | nothing queued | counts as a farmer | walks to the nearest farm | `laborsAs(FARMER)` |
| 4 | VILLAGE reached | becomes an `IDLER` | — | `StagePlanner.crystallize` |

There is no weakness gate on foraging. The weak foraging anyway is exactly what
breaks the starvation spiral.

### FARMER

| # | Trigger | Clock | Watched | Gate |
|---|---|---|---|---|
| 1 | any crop farm stands | credits 1 food/step per working hand into a field, +50 % if a mill runs | `FarmWorker` harvests real wheat into the field's store | `FOOD_PER_FARMER_PER_STEP` = 1, `FARMERS_PER_FARM` = 2, `FARM_STORE_CAP` = 40 |
| 2 | field has ≥ 12 unclaimed, granary has room | errand: field → granary, 12 | same errand; the body walks it | `WORTH_LEAVING_THE_ROWS` = `FARMER_CARRY` = 12 |
| 2a | town is starving | errand fires for a single loaf | same | `Settlement.isStarving` |
| 3 | carrying an errand | 12 blocks per step toward it | steered by `workplaceFor`, floored after 12 stalled steps | `ABSTRACT_TRAVEL_BLOCKS`, `EMBODIED_STALL_STEPS` |
| 4 | on the field, not hauling | — | one action per pass: **harvest, then plant, then tend** | `FarmWorker.nextJob` |
| 5 | town has ≥ 2 fields | — | works the field the roster deals them, not the nearest | `FieldRoster` |

Ordering note on #4. Tending used to come second and starved fields to death: a
field with any growing crop always has something to tend, so the planting branch
was reached only in the instant every crop was simultaneously ripe. **Harvest,
plant, tend** is correct and is asserted by `FarmWorkerTest`. Verified still in
place.

Threshold note on #2. It used to be 1, and the field errand therefore fired every
step. A farmer with an errand is a farmer out of the rows — `workFarmers` skips
anyone with `haul() != null`, correctly, because they are on the road — so a
watched field grew one loaf, emptied, grew one loaf, emptied, and three farmers
walked laps. `FoodPlanner.alreadyPromised` additionally subtracts errands already
outstanding, so two farmers are never dispatched to the same twelve loaves.

Two books are kept, and they close at opposite ends of the walk.
`alreadyPromised` asks what a *field* still has to give and counts only errands
not yet picked up, because grain on a back has genuinely left the field.
`onTheRoadTo` asks what the *granary* still has room for and counts every
outstanding load, because nothing has arrived until it is set down. Both exist
because of the same change: at a load of one or two loaves an overshoot was
rounding, and at twelve it is destroyed food — `deposit` spoils whatever will not
fit rather than duplicating it.

### BUILDER

| # | Trigger | Clock | Watched | Gate |
|---|---|---|---|---|
| 1 | build queued, not weak | work granted by head count | walks to the site; `tickConstruction` steers block by block | `advanceBuildQueue` |
| 2 | needs a material not in hand | — | `fetchLoad` — walks to the nearest store that actually *holds* it, draws a load | `BuildLoad.pickUp`, `LOAD_REACH` = 4 |
| 3 | boxed out of reach | — | a block is placed anyway after `STALL_PASSES_BEFORE_ASSIST` = 20 passes | |
| 4 | hands laying nothing for too long | falls through to the clock | — | `Settlement.WATCHED_BUILD_GRACE_STEPS` = 12 |
| 5 | queue empty | — | `Foreman.work` — roads, then wall stations | `workWall` |
| 6 | queue empty and nothing public to do | — | walks to the hall | `workplaceFor` |
| — | asked to courier bulk goods | **never** | **never** | `HaulPlanner.courierFor` |
| — | released from view mid-trip | carried load returned to the town's books | — | `BuildLoad.putBack` |

### GUARD

| # | Trigger | Clock | Watched | Gate |
|---|---|---|---|---|
| 1 | always | counts `GUARD_POWER` = 2 toward the town's defense | — | `RaidPlanner` |
| 2 | no hostile in sight | — | patrols the perimeter vertices; falls back to the watchtower | `patrolPost` |
| 3 | hostile in sight | — | arms up, charges, strikes | `GUARD_STRIKE_RANGE` = 2.5, `GUARD_DAMAGE` = 4 |
| 4 | the hostile blows up | — | one hit, retreat 12 blocks, back in after 45 ticks | `Menace.blowsUp`, `FUSE_RESET_TICKS` = 45 |
| 5 | night, alarm — anything | never goes home | never goes home | `Alarm.callsIn` returns false for GUARD |
| — | asked to courier bulk goods | **never** | **never** | `HaulPlanner.courierFor` |

### LUMBERJACK

| # | Trigger | Clock | Watched | Gate |
|---|---|---|---|---|
| 1 | camp stands, `wantsMoreTimber` | 8 timber + 1 sapling per head per step, split evenly between camps | `LumberjackWorker` fells real trunks, 1 timber per log, 1 sapling per 4 | `WOOD_PER_STEP` = 8, `Workforce.shareOf`, `MAX_SAPLINGS` = 128 |
| 2 | stores full, or no trunks left | stops | replants a sapling on bare ground | `wantsMoreTimber` |
| 3 | axes idle 12 steps while watched | clock resumes crediting | — | `WATCHED_WORK_GRACE_STEPS` |
| 4 | alarm at **WARY** or above | — | comes inside | `worksBeyondTheWalls` |
| 5 | at the timber ceiling | — | — | eligible as a bulk courier (tier 2) |

### MINER

The lumberjack one layer down; `MinePlanner` mirrors `LumberPlanner` deliberately
rather than sharing code with it.

| # | Trigger | Clock | Watched | Gate |
|---|---|---|---|---|
| 1 | mine stands, `wantsMoreStone` | 6 stone + 1 iron per head per step, split between mine heads | `MinerWorker` cuts real faces, 1 stone / 2 iron per block | `STONE_PER_STEP` = 6, `IRON_PER_STEP` = 1, `MAX_IRON` = 256 |
| 2 | — | — | cuts downward and inward, never below `FLOOR_MARGIN` = 6 or past `MAX_DEPTH` = 20 | |
| 3 | picks idle 12 steps while watched | clock resumes crediting | — | `WATCHED_WORK_GRACE_STEPS` |
| 4 | alarm at **WARY** or above | — | comes inside | `worksBeyondTheWalls` |
| 5 | at the stone ceiling | — | — | eligible as a bulk courier (tier 2) |

### SMITH

| # | Trigger | Clock | Watched | Gate |
|---|---|---|---|---|
| 1 | smithy stands, not weak | 1 item per smith per step | *no watched worker* — the clock does it either way | `OUTPUT_PER_SMITH` = 1 |
| 2 | making something | spends 2 iron + 1 timber | same | `IRON_PER_ITEM`, `FUEL_PER_ITEM` |
| 3 | choosing what | tools, then weapons, then armor | same | `MAX_TOOLS` 64, `MAX_WEAPONS` 32, `MAX_ARMOR` 32 |
| 4 | every rack full, or no ore | idle | walks to the smithy and stands there | `SmithPlanner.hasWorkInFront` |
| 5 | idle by #4 | — | — | eligible as a bulk courier (tier 2) |

### MILLER

| # | Trigger | Clock | Watched | Gate |
|---|---|---|---|---|
| 1 | a mill stands and at least one miller lives here | the whole town's harvest yields 50 % more | same | `FoodPlanner.millRuns`, `growHarvest` |
| 2 | — | — | walks to the mill and stands there | `workplaceFor` |
| 3 | **always**, mill or no mill | — | — | eligible as a bulk courier (tier 2) |

The miller has no watched worker: the mill's effect is one line in `growHarvest`.
The full grain-and-bread economy is still a GOALS entry.

### CARPENTER

| # | Trigger | Clock | Watched | Gate |
|---|---|---|---|---|
| 1 | a carpentry stands, ≥ 1 carpenter, ≥ 1 able builder | the build crew counts one higher | same | `Settlement.advanceBuildQueue` |
| 2 | — | — | walks to the carpentry and stands there | `workplaceFor` |
| 3 | the build queue is empty | — | — | eligible as a bulk courier (tier 2) |
| — | the build queue is **not** empty | **never a courier** | **never a courier** | `hasWorkInFront` |

Like the miller, no watched worker: the discount is applied to the crew rather
than to the bill, so one lever covers both fidelities.

### SHEPHERD

| # | Trigger | Clock | Watched | Gate |
|---|---|---|---|---|
| 1 | animal farm stands | *nothing* — livestock is world-only | `ShepherdWorker` stocks each pen to 4 of its kind | `PER_PEN` = 4, `Culture.pennedAnimals` |
| 2 | pens full | — | leaves them; vanilla breeds them | |
| 3 | alarm **ALARMED** | — | goes home (the pens are on a ring plot, not beyond the walls) | `Alarm.callsIn` |
| 4 | no animal farm | — | — | eligible as a bulk courier (tier 2) |

### TRADER

| # | Trigger | Clock | Watched | Gate |
|---|---|---|---|---|
| 1 | granary holds ≥ 12, a stall below cap | errand: granary → market, 12 | same errand, walked | `TRADER_CARRY` = 12, `MARKET_STOCK_CAP` = 150 |
| 2 | a player opens the market | prices and deals, buy and sell | same | `Market`, `Market.hasTrader` |
| 3 | no trader alive and able | the town will not deal with a player at all | same | `Market.hasTrader` |
| 4 | — | — | walks to the nearest market | `workplaceFor` |
| 5 | granary under 12, no stall, or **every stall at cap** | — | — | eligible as a bulk courier (tier 2) |

### IDLER

| # | Trigger | Clock | Watched | Gate |
|---|---|---|---|---|
| 1 | anything wants carrying | **first choice of courier**, always | same | `HaulPlanner.courierFor` |
| 2 | the family pantry is low | first choice of shopper | same | `FoodPlanner.freeMember` |
| 3 | the town is short of a trade | first choice for retraining | — | `JobPlanner.retrainOne` |
| 4 | nothing else | — | mills about the family home | `workplaceFor` |
| — | tools | never issued one | — | `equipWorkers` skips idlers |

### Every household, whatever the trades in it

| # | Trigger | Clock | Watched | Gate |
|---|---|---|---|---|
| 1 | pantry below `size × 3`, nobody in the family already carrying | one member is sent shopping: market first, granary otherwise | same | `PANTRY_PER_MEMBER` = 3, `FETCH_MAX` = 8 |
| 2 | who goes | idler first, then any member who is not a guard or a builder | same | `FoodPlanner.freeMember` |
| 3 | **nobody in the family is embodied** | the pantry is filled by arithmetic, up to what a shopper would have carried | — | `carryItHomeUnwatched` |

Note #2: a farmer *can* be pulled off the rows to feed their own family, and that
is deliberate — a household of nothing but farmers has nobody else to send, and
the alternative is a family that never eats.

---

## 6. Where the two fidelities differ

Everything below is a real difference, not an implementation detail.

1. **Production.** Unwatched, output is `rate × heads` and lands at the nearest
   store to the workplace. Watched, output is whatever real hands actually
   manage, and the clock is suppressed for as long as they are managing it
   (`Building.harvestedWithin`). Twelve steps of no real work and the clock takes
   over again.
2. **Farmers with errands.** Unwatched, a farmer on a haul is *still counted* as a
   farm hand by `countFarmHands`, so the field keeps producing. Watched,
   `workFarmers` skips them and the field genuinely stops. This asymmetry is why
   the fetch threshold matters so much more to a watched town.
3. **The last link of the food chain.** For a household with nobody embodied,
   `carryItHomeUnwatched` fills the pantry as arithmetic. Where anybody *is*
   embodied, they walk it. Only the last link — stocking stalls here as well
   delivered the same load twice.
4. **Delivery.** Unwatched hauls always arrive. Watched ones arrive when the
   navigation manages it, or after `EMBODIED_STALL_STEPS` = 12, whichever is
   first.
5. **Shepherds and livestock.** Entirely watched. There is no abstract herd; an
   unwatched animal farm produces nothing at all.
6. **Raids.** Watched, real hostiles spawn and entity combat decides. Unwatched,
   defense power versus raid strength, resolved into the event log.
7. **Litter.** Only embodied people pick things up, so an unwatched town never
   acquires anything it did not produce.
8. **Undrawn buildings.** `FieldRoster.fields` requires `isMaterialized`, so a
   farm that exists in the simulation but has not been painted into the world is
   off the roster — a farmer cannot walk rows that are not there. The clock keeps
   the field producing regardless (`growHarvest` does not filter on
   materialization), which is what stops a town starving on a farm it has not
   seen yet.

## 7. Known differences that are faults, not design

Both live in `PersonEntityManager`, which this document's author does not own.
They are written up in full in the report that accompanies this file.

* `workFarmers`, `workLumberjacks` and `dailyRoutine` gate on
  `person.isTooWeakToWork()` rather than
  `FoodPlanner.heldBackByHunger(person, settlement.isStarving())`. The clock
  suspends the weakness rule while a town is starving — precisely so weak hands
  go on farming — and the watched loop does not. So in the exact circumstance the
  suspension exists for, a watched town's farmers stand still while an unwatched
  one's keep working. The town does not die (the 12-step harvest floor covers it)
  but the player sees the fields abandoned during a famine.

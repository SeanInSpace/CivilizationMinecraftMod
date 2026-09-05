# The hauler

A design for a porter profession. **Nothing here is built yet.** What exists
today is the courier rule in `HaulPlanner.courierFor` (see `docs/CITIZENS.md`
§4), which decides who is spare enough to carry a load. This document is the
plan for the trade that would answer that call first.

---

## A note on the real thing

Before a town had carts it had backs. Medieval European towns supported a whole
tier of people whose living was carrying: **porters** who moved sacks and barrels
by hand and by hand-barrow, sworn into fraternities that held a monopoly on
particular goods at particular gates; **carters** and **carriers** who owned a
horse and a cart and worked between the market, the mills and the wharf; and, in
English towns from the fifteenth century, the **common carrier** — a licensed
regular service running to a fixed timetable between named inns, obliged to
accept any lawful goods offered. The economically interesting part is that this
was a *trade*, not a chore. Carrying was not what a baker did when the bread was
in the oven; it was what a man with strong shoulders and no other capital did all
day, and towns organized around it — porters' guilds fixed rates, cities
licensed carters, and the inn-yard became the freight terminal. A town that grows
past the point where everybody carries their own goods hires somebody to carry
them. That is the shape this profession should have.

---

## Who

A new `Profession.HAULER`, staffed by `JobPlanner` from the ordinary table.

```java
new ProfessionNeed(Profession.HAULER, 0, 12, 42, BuildingRole.STORE)
```

**Ratio: one per twelve residents, zero base, gated on a store standing.** The
reasoning, in the order it matters:

* **Zero base.** A camp must never spend one of its four founding settlers on
  walking. `SupplyPlanner`'s existing javadoc says exactly this and it is right.
* **Gated on `BuildingRole.STORE`,** the same way the lumberjack is gated on a
  camp. A hauler with nowhere to carry to has no job; the store *is* the job. The
  first storehouse arrives at `FORTIFIED`, which is also the first stage where a
  town has two places goods can be and therefore a first reason for anybody to
  move them between them.
* **Per twelve, not per store.** One-per-store was the first instinct and it is
  wrong: store count grows with a town's *storage need*, not with its traffic, so
  a town that raised three storehouses to hold one big founding kit would hire
  three haulers with nothing to do. Traffic scales with producers and with
  distance, and both scale with population. Twelve sits between the miner (12)
  and the mill (20) — a village of twenty-five gets two porters, which is about
  right for a town with a market, a granary and two storehouses.
* **Priority 42,** below every producing trade and above nothing. A town short of
  a farmer and short of a hauler wants the farmer. The hauler is an efficiency,
  and efficiencies come last.

A hauler is a specialist: it does not fell, cut, forge or farm. Its only output
is that goods are where they are wanted.

## What they move

Three flows, in priority order. This is a queue, and the order below is its
ordering function.

| # | Flow | Exists today as | Notes |
|---|---|---|---|
| 1 | **materials to build sites** | `SupplyPlanner` | the only demand-driven one; the signal is a build |
| 2 | **produce to granary** | farmers' own errands in `FoodPlanner` | field → granary, `FARMER_CARRY` = 12 |
| 3 | **stock to market** | traders' own errands in `FoodPlanner` | granary → stall, `TRADER_CARRY` = 12 |

Build materials first because a build that is waiting is a build that has
stopped, and the queue behind it is head-blocking. Produce second because it
feeds people. Market stock last because the granary already feeds people and the
stall is a convenience on top of it.

**Do farmers and traders keep their errands?** Yes, and this is the important
design decision. A farmer carrying their own harvest to the granary is *farming*
— it is the last motion of the harvest, the field is theirs, and a player watching
a farmer walk a sack of grain in reads it as a farmer working. Take that away and
the farm becomes a place where wheat is produced by nobody in particular. The
hauler picks up the flows that presently have no owner (bulk supply) and *relieves*
the ones that do when there is a hauler free: a full field with a hauler standing
by should be collected by the hauler, so the farmer stays in the rows. Concretely:
flows 2 and 3 are offered to a hauler first and fall back to the farmer or trader
when no hauler is free, which is exactly what happens today with no haulers alive.

**Do builders keep `fetchLoad`?** Yes. `PersonEntityManager.fetchLoad` is a
builder walking to a shelf and picking up a stack, and it is the only place the
watched path charges a town for a building. It must stay, for the same reason a
haul that waits must not stop a workshop: a builder who cannot fetch their own
load is a builder who deadlocks when the town has no hauler. The hauler makes
that walk *shorter* — by keeping the store beside the site stocked — rather than
replacing it. `SupplyPlanner`'s destination choice (`nearestStore(buildQueue head
site)`) is already exactly this.

## How the queue works

**`HaulPlanner` becomes the single queue.** Today three places create errands
(`SupplyPlanner`, `FoodPlanner.assignHauls`, `FoodPlanner.assignPantryRuns`) and
each picks its own carrier by its own rules. That is the arrangement that let a
carpenter be conscripted, and it will not survive a fourth flow being added.

The shape:

```
HaulPlanner.post(HaulTask)      // anybody may file a request
HaulPlanner.assign(Settlement)  // one pass, oldest request first
HaulPlanner.advance(...)        // unchanged: walk what has been assigned
```

`assign` offers each pending request to, in order:

1. **A free `HAULER`** — nearest to the pickup, so two porters do not cross.
2. **An `IDLER`** — as now, tier 1 of `courierFor`.
3. **A trade with nothing in front of it** — as now, tier 2 of `courierFor`.
4. **Nobody.** The request stays queued.

**Trades never displace a hauler.** If a hauler exists and is free, it goes; a
lumberjack at the timber ceiling is only ever the second-best answer, because the
ceiling can lift again at any moment and the hauler's day cannot.

Requests are *persisted with the settlement*, unlike today's, where an unassigned
shortage is simply recomputed next step. Persistence is what makes "the load
waits" observable — `/civ info` should be able to say *3 loads waiting, no
carrier* — and it is what turns the waiting case from an invisible non-event into
something a player can act on by training a hauler.

One assignment per step, as `SupplyPlanner` does now, and for the same reason:
the arithmetic is applied at pickup, not at dispatch, so several requests against
the same shelves all look affordable at the moment they are handed out.

## Distance and load

* **One load per trip.** `SupplyPlanner.LOAD` = 64 for bulk, `FARMER_CARRY` /
  `TRADER_CARRY` = 12 for food. Keep them; they are tuned against real playtest
  numbers (the food figure was raised from four after a run banked 156 of harvest
  in the fields with the granary at four).
* **Worth the walk.** A request is only filed when the load clears a threshold —
  `SupplyPlanner.SHORTAGE` = 32 at the destination and a full `LOAD` of headroom
  at the source, `FoodPlanner.WORTH_LEAVING_THE_ROWS` = 12 at a field. The
  hysteresis is not decoration: an "even the stores out" rule oscillates, because
  every move it makes creates the imbalance that justifies moving something back.
  The generalization of `Economy.WORTH_THE_WALK` (20 in value) to bulk requests is
  the natural next constant.
* **Nearest carrier to the pickup**, not nearest to the destination. The empty leg
  is the wasted one.
* **No distance cap.** A town's claim radius is already the bound, and a cap would
  strand an outlying lumber camp — which is precisely the case a hauler exists
  for.
* **A hauler carrying food eats it if starving.** `HaulPlanner` already abandons
  errands for `heldBackByHunger`, and already suspends that while the town is
  starving, because a town whose carriers all set their grain down at the farm
  gate on the same afternoon never eats again. Do not touch that.

## What happens unwatched

Nothing changes. `HaulPlanner.advance` walks an unembodied carrier
`ABSTRACT_TRAVEL_BLOCKS` = 12 a step and sets the load down at the far end, so a
delivery takes about as long whether or not anyone is looking; a watched carrier
who cannot path gets `EMBODIED_STALL_STEPS` = 12 and then the clock delivers
anyway. The hauler inherits all of it by being an ordinary `Person` with an
ordinary `HaulTask`. **No new abstract-fidelity code should be needed at all** —
that is the test of whether this design is right.

The one addition: an unwatched town with no hauler should still see its requests
served by tier 2 and 3, so removing the profession from a save cannot deadlock a
town's logistics.

## Later

Deliberately not in the first version, in the order they should arrive:

1. **The warehouse yard.** A `BuildingRole` for a dedicated depot with a larger
   ceiling, sited between the producers and the market rather than on the ring —
   the inn-yard as freight terminal. Gives the hauler a home to stand at when
   idle, which is what `workplaceFor` will want.
2. **Carts.** A hauler with a cart carries `LOAD × 3` and walks slower. The cart
   is a built thing the carpentry makes, which gives the carpentry a second
   reason to exist.
3. **Pack animals.** A donkey led along a laid road; ties the `PathNetwork` to
   the economy for the first time, since a cart on a paved street should be
   meaningfully faster than one on grass.
4. **Inter-settlement carriers.** The common carrier proper — a hauler who walks
   between two of the kingdom's settlements on a fixed round, which is the
   natural home for `ExpansionPlanner`'s trade between towns.
5. **Ambush.** Goods on a back are already real; a carrier killed on the road
   already costs the town that food. A raid that targets carriers rather than
   walls is a story the existing state can already tell.

## The tests to write

In `common`, so they run in milliseconds and without a game.

**Staffing**

1. `aCampNeverStaffsAPorter` — below `FORTIFIED`, with no store, `JobPlanner.mostNeeded` never returns `HAULER`.
2. `aVillageWithAStoreStaffsOne` — one store, twelve residents, one hauler wanted.
3. `aFarmerIsWantedBeforeAPorter` — shortfalls in both, `mostNeeded` returns `FARMER`.
4. `aTownDoesNotHireAPorterPerStorehouse` — three storehouses, twelve residents, still one hauler.

**The queue**

5. `aPostedLoadSurvivesAStepWithNobodyToCarryIt` — post, assign with nobody free, the request is still queued next step.
6. `theHaulerGoesFirst` — a hauler and an idler both free; the hauler carries.
7. `anIdlerCarriesWhenNoHaulerLives` — the fallback that keeps old saves working.
8. `aTradeNeverDisplacesAHauler` — a lumberjack at the ceiling and a free hauler; the hauler goes.
9. `buildMaterialsOutrankMarketStock` — two requests queued, the site's load is assigned first.
10. `oneAssignmentAStep` — three requests, three haulers, one assignment.
11. `theNearestHaulerToThePickupIsChosen` — two haulers, the near one goes.

**Relief, not replacement**

12. `aFarmerKeepsTheirOwnHarvestErrandWithNoHaulerFree` — today's behavior, unchanged.
13. `aFreeHaulerCollectsTheFieldAndTheFarmerStaysInTheRows` — the point of the profession.
14. `aBuilderStillFetchesTheirOwnLoad` — `BuildLoad.pickUp` still charges the town; no deadlock without a hauler.

**The invariants that must not break**

15. `nothingIsCreatedOrLostOnTheRoad` — total town holdings before == after, across a full round trip (the shape `SupplyPlannerTest.aRoundTripActuallyMovesTheGoods` already uses).
16. `aHaulerTooWeakToWorkPutsTheLoadBackWhereItCameFrom` — and does not while the town is starving.
17. `anUnwatchedHaulerStillDelivers` — `ABSTRACT_TRAVEL_BLOCKS` walks it home.
18. `aDestinationPulledDownMidTripDropsTheLoadOnTheGround` — never into nowhere.
19. `aSaveWithQueuedRequestsReloadsThem` — round-trip through `KingdomsCodecs`.

**Regression, from the report this began with**

20. `aCarpenterIsNeverAskedWhileTheBuildQueueHasWorkInIt` — already covered by `CourierChoiceTest`; extend it to assert the hauler is asked instead.

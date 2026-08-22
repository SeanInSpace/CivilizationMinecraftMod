# Founding

**How a party of settlers becomes a town — the staged priority structure that
replaces "build the hall first."**

The verdict that forced this design: no realistic settlement starts by building
a government. Today the catalogue puts the town hall at priority 100 above
everything, so four settlers' first act is civic architecture while they sleep
in the open and their bread runs out. The founding experience is the core of
this mod, and it deserves a real progression: camp, then food, then safety,
then permanence — with the hall as the capstone of a town worth governing, not
the tent-pole of a camp that is not.

The player supplied Phases 3 and 4 of this progression. Phases 0–2 are derived
to meet them, and Phase 5 closes the arc onto the systems the mod already has.

---

## The shape of it

A settlement carries a **stage**, and each stage carries two programs: an
ordered **build program** (what gets raised, in what order) and a **staffing
program** (who does what while it stands at that stage). Stages advance on
**conditions, not day counts** — the day ranges below are pacing targets to
tune against, because a stage that advances on a timer advances over a party
that is failing.

```
CAMP  →  HOMESTEAD  →  FORTIFIED  →  VILLAGE  →  TOWN
day 0     days 0–2      days 5–8     days 9+     later
```

This is also where the **general job-reassignment rework** lives. The staffing
table today is one global list with population thresholds — which is how a
founding party ended up with idlers it could not turn into farmers. Under
stages, early settlers are **pioneers**: generalists with no fixed profession,
taking work from a task pool (build, forage, haul, plant). Professions
crystallize as the stages demand them — the sentry at FORTIFIED, the
specialists at VILLAGE — and reassignment is a stage-transition and shortage
event, never a reflex fired by a structure completing.

---

## Phase 0 — Arrival (day 0)

The charter lands the party at a validated site (dry, flat enough — the site
checks that already exist). First acts, in order:

- **Stake the claim.** A camp post — a cheap marker at what will one day be the
  hall's site. Not a hall. It is where the party's intentions live: clicking it
  reports the stage, the program, and what is blocking advancement.
- **Ground the packs.** A supply cache (a crate, not a building) becomes the
  communal store. `TownStores` is already a ledger; the cache is its first
  physical address.

**Advance when:** the claim is staked. Effectively immediate; the phase exists
so the arrival reads as an arrival.

## Phase 1 — Shelter and hearth (days 0–2)

- **P1 Bunkhouse.** One communal building that sleeps the whole party. The
  existing cabin shape at minimal cost. Shelter for everyone — but communal
  bunks do **not** count as family housing, so no births yet (see Phase 4).
- **P2 Hearth.** A campfire at the camp's centre: light against spawns, the
  cooking point the mill will one day replace.
- **P3 Cache formalized.** The crate gets a roof if weather ever matters;
  otherwise it simply persists as the pre-storehouse store.

**Staffing:** everyone is a pioneer. The work planners accept pioneers for any
labour while the settlement is below FORTIFIED.

**Advance when:** the bunkhouse stands and everyone slept under it.

## Phase 2 — Food security (days 2–5)

- **P1 Farm plot.** The farm the party should have built first all along —
  planted, tended, at grade (all of which now works).
- **P2 Interim food.** Pioneers forage while the first crop grows: a simple
  gathering task against nearby grass/berries/animals that trickles food into
  the cache. This is what replaces "thirty-two loaves as a fuse."
- **P3 First granary.** Small, adjacent to the farm.

**Advance when:** food income per step ≥ appetite per step, measured over a
rolling window — the party is genuinely feeding itself, not merely provisioned.

## Phase 3 — Defensive enclosure and sanitation (days 5–8)

*As specified by the player, with the mechanics mapped:*

- **P1 Boundary construction (palisade / trench).** Builders erect a perimeter
  around the work radius: a wooden palisade with gated choke points, or a
  2-block-deep trench dug by the excavation system that already exists. This
  introduces the **perimeter subsystem** — a segmented closed curve with gates,
  stored on the settlement — with deliberately cheap geometry: an offset hull
  around the claimed plots. The α-shape / active-contour wall already in
  `GOALS.md` is the TOWN-stage implementation of the *same interface*; the
  palisade proves the pipeline (curve → courses → gates) before the advanced
  geometry arrives. Gates go where paths cross the curve, which is one of the
  reasons the path network must remember where its paths are.
- **P2 Guard assignment and sentry post.** One pioneer crystallizes into a
  **sentry** — the first fixed profession — equipped from the founding kit's
  arms. Patrol pathfinding nodes are the perimeter's own vertices: the curve
  doubles as the patrol route, walked the way lumberjacks walk their trees, to
  intercept zombies and spiders before they reach the bunks.
- **P3 Storehouse upgrade.** The cache becomes a proper **Town Storehouse**:
  segregated sorting (food, timber, minerals, finished tools — the ledger keys
  that already exist, given shelves), and the **player trade interface** —
  extending the warehouse post's existing donate/bill screen into two-way
  trade.

**Advance when:** the perimeter is closed, a sentry patrols it, and the
storehouse stands.

## Phase 4 — Structural division of labour and growth (days 9+)

*As specified by the player:*

- **P1 Dedicated residences.** Builders raise 2-person cottages and move
  families out of the bunkhouse. **Births gate on family housing** — the
  reproduction loop unlocks here and not before, which is what makes the
  bunkhouse a stage and not a destination. The bunkhouse remains as lodging
  for the unmarried and for newcomers.
- **P2 Specialized workshops**, each crystallizing its profession:
  - **Blacksmith** — exists (the smith); gains tool *repair* alongside
    production, fed by miners' ore.
  - **Mill / Bakery** — new. Fields begin yielding **grain**; the mill
    multiplies it into bread at a real ratio, and unmilled grain feeds people
    poorly. This introduces the raw/processed food distinction the economy has
    been missing, and retires the hearth.
  - **Carpentry / Masonry** — new. Pre-crafts components (stairs, slabs,
    fences, refined blocks) that **discount the work cost of subsequent
    builds** — the town literally gets faster at building itself, which is the
    economic argument for the workshop tier.
- **P3 Trade and diplomacy post.** The market exists; an **Inn** joins it —
  the interaction point for wandering traders (vanilla's, hooked), players,
  and eventually other settlements.

**Advance when:** half the population lives in family housing and two
workshops operate.

## Phase 5 — Town (the capstone)

- **The town hall, at last** — built once there is a town worth governing, and
  worth the ceremony of being the stage's headline build.
- **Expansion gates on it**: `ExpansionPlanner` may not found a daughter
  settlement until the hall stands. Government precedes colonies.
- **The wall replaces the palisade** — the α-shape / active-contour perimeter
  from `GOALS.md`, on the same perimeter interface the palisade proved, for
  settlements that can afford it. Small villages keep their palisade.
- The existing building-level upgrade system takes over from here.

---

## What this touches

**The priority core (new, in `common/`).** A `SettlementStage` enum persisted
on the settlement; a stage program table (ordered build wants + staffing needs
per stage). `chooseNext` consults the stage program before the catalogue; the
catalogue's per-resident scaling becomes the VILLAGE+ behaviour it should
always have been. `JobPlanner` reads the stage's staffing program instead of
one global list, and learns the pioneer.

**The pioneer (change, `common/`).** A profession that is allowed to do any
early labour. Work planners at CAMP–HOMESTEAD accept pioneers; crystallization
converts them as stages demand.

**The perimeter (new, `common/` shape + `neoforge/` courses).** Curve + gates
on the settlement; palisade builder (fence courses / trench via the existing
excavation); sentry patrols along its vertices; the advanced wall slots in
later on the same interface.

**Food processing (change, `common/`).** Grain as a distinct store; the mill's
conversion ratio; foraging as a pioneer task.

**Housing tiers (change, `common/`).** Bunkhouse shelters but does not breed;
births require family homes (`PopulationPlanner`).

**New blueprints (`neoforge/`).** Bunkhouse, hearth, cache, palisade segment +
gate, cottage, mill, carpentry, inn. All procedural first; all replaceable by
authored blueprints through the seam that already exists.

**Back-compatibility.** Existing saves infer a stage from their census (a hall
means TOWN; count backward from there). New foundings start at CAMP. Stage is
one optional codec field.

**Not reverted.** The crisis staffing, queue preemption and distress reporting
that landed recently stay: they are the safety net *under* this progression,
and the progression is what should make them rarely fire.

---

## Order of work

All six steps are built. What each one landed:

1. **Stage machine + programs** — DONE. `SettlementStage` + `StagePlanner`:
   condition-gated advancement, per-stage programs, the hall gated to TOWN,
   the charter lands a CAMP of pioneers, `laboursAs` carries generalists,
   crystallization fills posts and never doubles them. Old saves load as TOWN.
2. **Camp content** — DONE. Camp post (stage report on right-click), cache,
   bunkhouse, hearth; foraging with the hand-to-mouth ceiling (half the fed
   window, so berries never graduate a homestead); births gated on family
   homes; `requestProducer` can retrain a pioneer, which is what lets a camp
   bootstrap its own timber.
3. **Perimeter v1 + sentry** — DONE. `Perimeter` (vertices/gates/laid) +
   `PerimeterPlanner` (rectangle staking, paid raising) + `PerimeterLayer`
   (posts, torches, fence gates in-world) + vertex patrols. Ring-aware siting
   keeps civic buildings behind the wall; producers stay outriders.
4. **Storehouse + player trade** — DONE. Donations in (logs, stone, bread, to
   capacity), timber out (emeralds, above a reserve), full ledger on the post.
5. **Cottages + birth gating; the mill; carpentry; the inn** — DONE. Couples
   move out of the bunks as cottages rise; the mill grinds +50% from the same
   harvest; carpentry adds a pair of hands to every crew; the inn's caravan
   trades surplus bread for iron on a 48-step rhythm.
6. **Hall as capstone; expansion gated** — DONE. TOWN's program is the hall;
   expansion requires the hall standing AND the parent affording the founding
   kit, which the parent now actually pays for (daughters no longer conjure
   their stores). Daughters land as CAMPs of pioneers and climb the same
   ladder.

The 300-step pin (`StageProgressionTest.aCampLeftAloneClimbsTheWholeLadderToTown`)
drives a charter party camp-to-TOWN headless: timber bootstrapped, streak fed,
palisade closed and walked, cottages filled, hall standing.

## The second way a settlement is founded: daughters

*(Absorbed from the former `EXPANSION.md`. Founding by charter and founding by
daughter are now literally the same ladder, so they belong in one document.)*

Code: [`ExpansionPlanner`](common/src/main/java/com/kingdoms/sim/kingdom/ExpansionPlanner.java).

> **A full settlement does not stop growing — it sends people out to found the
> next one.**

A town that has filled up sends a founding party of its youngest families out to
plant a new settlement under the same banner, ~160 blocks away. The parent,
relieved of its emigrants, resumes growing. When it fills again, it founds again.
**Sprawl is the steady state**, and a kingdom is what accumulates.

The rules, all deterministic:

- **Only a full settlement founds.** Population at the ceiling is the trigger —
  the cap is not a wall, it is a pump.
- **Only a chartered town founds.** The settlement must have reached TOWN and have
  its hall actually standing. A settlement still climbing the ladder pours its
  people into the climb instead of sending them away.
- **The parent pays the dowry.** Founding costs the same kit a player's charter
  grants — timber, stone and provisions — taken out of the parent's own stores. A
  town that cannot outfit its emigrants keeps them until it can. Nobody is sent
  into the wilderness empty-handed, which is exactly how the very first founding
  party nearly died.
- **One frontier town at a time.** No settlement founds while any sibling is still
  young (below 10 people). A kingdom consolidates before it stretches again.
- **Whole families emigrate, never fragments.** The party is up to 6 people,
  chosen youngest-families-first — the founding generation leaves, the elders who
  built the parent stay.
- **Nobody you can see teleports.** A family with any member currently embodied as
  an entity stays home. Emigration happens in the abstract fidelity only.
- **The daughter is a camp.** Emigrants arrive as pioneers in a fresh CAMP and
  climb the whole ladder their parent climbed, hall last. Their old trades are
  re-earned as the camp crystallizes them: a smith on a bare hillside is a pioneer
  whatever their papers say.
- **The site** is picked by hashed direction at 160 blocks, stepping further out
  when the kingdom's own claims are in the way.

Both towns record the event:

```
[step 412] 6 set out to found Ravenholm
[step 412] Ravenholm founded by settlers from Oakstead
```

The full loop, end to end, with no player input:

```
families grow → town fills → hall stands → party departs → daughter camp founded
     ↑                                                            ↓
  parent regrows ← ceiling lifted by emigration      climbs the ladder itself
```

Hold a Founding Charter and walk the frontier: each green ring is another
settlement the kingdom planted itself.

### Not yet, on the expansion side

- **Emigration is instant** — a departure step, not a walked journey. Visible
  caravans belong with the travel system.
- **Sites ignore terrain and other kingdoms** — a daughter can be planted near a
  rival's claim. Site *quality* is a later concern.
- **Nothing links the towns afterward** — no trade, no migration back, no shared
  defence. The inn's caravan is the seam that will carry it; the kingdom is a
  family tree, not yet an economy.

---

## The wall interface, for the α-shape work

The concave wall in GOALS replaces exactly one method:
`PerimeterPlanner.stake(Settlement)`. Everything downstream reads the
`Perimeter` it returns — an ordered vertex loop (`vertices()`, which are also
the patrol nodes), gate positions (`gates()`), and the ring walk
(`ringPositions()`, the positions the layer stamps and the laying cursor
counts). Persistence, paid raising, the closed flag, gate drawing, sentry
patrol and ring-aware siting all work through that surface and need no
changes. Two care points: keep vertices in walk order (the ring is drawn
corner to corner), and keep gates ON the loop (gateways are recognised by
proximity to ring positions). The α-wall is for settlements that can afford
it — small villages keep the palisade, which is why the tiers share the
interface instead of replacing it.

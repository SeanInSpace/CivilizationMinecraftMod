# Goals

Working list for the self-sufficiency milestone. Items move to **Done** with a
one-line summary, and are dropped entirely once they have been played and hold up
— this file is a worklist, not a changelog. The changelog is the git history; the
long history is `ROADMAP.md`.

**Milestone:** a town that secures its own materials, feeds itself, equips itself,
and can be traded with — without the player doing any of it for them.

The open work is ordered below. Each phase says what it is for and what it waits
on. A boundary between two of them is a point where the work changes character,
or where the later thing genuinely needs the earlier one to have landed. Where an
item is not properly specified, the open question is written down rather than
papered over.

---

## In flight

Being built right now, in parallel, by separate hands. Two of the phases below
wait on this section outright, and several of the visual questions at the bottom
will need asking again once it lands.

### The founding death spiral

A fresh town built its hall, ran out of materials, bootstrapped a mine, staffed
it with nobody, and starved to death idle. Six distinct faults, each sufficient
to have killed them, in the order the ratchet turned:

1. **A founding party can never farm.** The staffing table wants zero farmers
   below population five, and the charter lands four settlers with eight
   provisions each. Food production is impossible by design on day one; the
   thirty-two packed loaves are a fuse, not a larder.
2. **The build queue is head-blocking with no survival lane.** New wants are
   only ordered when the queue is empty, so a head task stalled unaffordable
   freezes all ordering forever — a farm cannot even be queued while the hall
   waits for stone.
3. **The producer bootstrap orders the building, not the worker.** The mine
   arrives; the staffing table may have nobody eligible to run it (only idlers
   retrain, and gates besides). The town builds the tool of its own rescue and
   cannot pick it up.
4. **Hunger disqualifies the hungry from the jobs that would feed them.** Weak
   people stop farming, hauling, building — and stay ineligible — so less food
   means weaker means less food. No crisis override exists anywhere.
5. **The spiral is silent.** No distress state, no escalating events, nothing on
   the posts or the hall; the player performs the autopsy.
6. **Founding economics are untested.** The kit demonstrably does not cover the
   first hall, so the day-one bootstrap detour is guaranteed, not chosen — and
   no test pins what the kit must afford.

Under way, one part per link that kills:

- [ ] **Survival reflexes.** A settlement crisis state (starving residents, or
      total food under a floor). In crisis: retraining ignores the idler-only
      rule, the weakness gate, and population thresholds — somebody farms NOW;
      farm and granary preempt the queue head; a head task stalled unaffordable
      for N steps is parked behind them instead of blocking.
- [ ] **The bootstrap comes with hands.** Ordering a producer force-retrains one
      resident to its trade in the same breath, or refuses and says why. A pinned
      test: the founding kit affords the hall and first house; provisions outlast
      the road to the first harvest.
- [ ] **Distress is audible.** Crisis leads every post report and the hall
      screen, escalates in the event log, flags the vitals line, and the auditor
      gains a town-level fault: starving with a frozen build queue.

### The ground a town takes

Three changes with one subject — what a building does to the land under it.
Between them they move every plot boundary and every doorway height in town,
which is why the walking work waits for them.

- [ ] **A smaller footprint.** The two-block skirt of flattened land round every
      building goes. `BlueprintPlacer.APRON_MARGIN` is currently 2, and
      `plotOf` reports the walls plus that margin on each side — so the skirt is
      simultaneously what the map draws, what the lamp outlines, and what keeps
      one plot off the next.
- [ ] **Build up, not only down.** A site is presently made flat by digging.
      A building whose ground falls away should be given foundations to stand on
      instead of having the hill removed from under it.
- [ ] **Never build in water.** Building in water is forbidden outright rather
      than discouraged.

---

## Phase 1 — the food chain holds while somebody is watching

**For:** a town that reliably feeds itself with a player standing in it, not only
headless. Everything after this is judged by long runs that assume food works.

**Waits on:** the survival-reflex work above, which rewrites who holds which job
and when. Changing job assignment underneath it would be two hands on the same
wheel.

- [ ] Farmers are handed a haul every step they are free, so hauling monopolises
      them: the fields fill, the granary and the stalls drain, and the market
      runs dry while somebody is watching. `FoodPlanner.assignHauls` gives an
      errand to any farmer without one, and nothing holds any of them back for
      the rows. **What to decide:** whether to reserve a share of farmers from
      hauling, to rest a farmer after a delivery, or to demote hauling below
      tending until the farm's own store is near its cap. The last is the one
      that matches how the shortage actually reads — food produced but sitting
      in the wrong store.

---

## Phase 2 — ground a person can walk

**For:** getting people from grade to a door. Both items are the same problem
looked at from two ends, and neither is worth doing twice.

**Waits on:** the whole of *the ground a town takes*. Route planning through the
gaps between plots is planning against boundaries that are about to move, and
what a steep site even looks like changes once buildings stand on foundations
rather than in a hole.

- [ ] **A path network, rather than a bundle of separate tracks.** `PathLayer`
      draws one Bresenham line from a door to the hall, paved three wide,
      knowing nothing about any other path or any plot. So routes never share a
      trunk or meet at a junction; they follow the ground up whatever gradient
      the terrain has, including faces nobody can climb; and they cross plots
      instead of threading the gaps between them. **What to decide:** whether
      the network is planned for the whole town and re-planned as it grows, or
      grown a route at a time by reusing what is already laid — and whether a
      grade too steep to walk is stepped (which makes it the same job as the
      item below) or routed around.
- [ ] **Steps to a workplace, not only to a home.** `BuildPlanner.requestAccessStairs`
      works, but the only caller is the cannot-reach-home check in
      `PersonEntityManager`. A farm or a mine cut into a hillside gets no such
      repair, which is why `/civ audit` can report a workplace with no doorway
      at grade and nothing ever comes to fix it. Re-run the audit on a hillside
      town after the foundation work before deciding what the flights have to
      reach — the answer may be somewhere else entirely by then.

---

## Phase 3 — somewhere to go

**For:** the first thing outside the town's own walls, and the counter the quest
board already advertises. `Tallies.HIDEOUTS_CLEARED` exists, is shown the moment
anything raises it, and nothing raises it.

**Waits on:** Phase 1, because a town that cannot spare anyone is a poor place to
test an expedition. Not strictly on Phase 2, though a patrol that cannot get out
of its own town would prove very little.

- [ ] Hideouts, so `hideouts_cleared` counts something. **Genuinely
      under-specified — four things to settle first:**
      *Who clears one?* If the player does and the nearest town credits it, this
      is a worldgen-and-loot job. If the town sends guards, it is a new kind of
      errand: people leaving the claim, which nothing does today.
      *Where do they come from?* Adopting vanilla structures (pillager outposts
      are already out there) or placed deliberately by the kingdom planner at a
      distance from a town.
      *What is a cleared one worth?* Loot, standing, a fall in threat level, or
      only the tally.
      *Should raids come from them?* `RaidPlanner` currently schedules raids by
      hashing the settlement id against the step number — they come from
      nowhere. Sourcing them from a nearby hideout would make clearing one mean
      something mechanical instead of decorative. That is the decision with the
      most consequence attached, so make it first.

---

## Phase 4 — the seams prove themselves

**For:** two extension points that are asserted to work and have never been
loaded. Both are claims in comments; this phase turns them into facts or finds
out otherwise.

**Waits on:** nothing structural — but it comes last because it buys no play
until the town beneath it survives, feeds itself and can be walked through.

- [ ] **A second culture, to prove the hook earns its keep.** The claim is that
      a second culture is a table entry rather than new code. Known to be false
      in at least these places:
      - `BlueprintPlacer.animalFarm` sizes the compound from
        `Culture.DEFAULT.penCount()` while `ShepherdWorker` stocks the pens from
        the *settlement's* culture. Two cultures with different beasts would
        build one shape and stock another; the shepherd would go looking for
        pens that were never laid.
      - `BuildCatalogue` reserves a fixed 21-block plot for
        `kingdoms:animal_farm`. A culture with more animals wants a deeper
        compound than the town set aside for it.
      - Every settlement starts on `BuildCatalogue.DEFAULT`, one hardcoded list,
        and the only `setCatalogue` call in the mod hands a daughter town its
        parent's copy. Nothing picks a catalogue by culture.
      - Settlement names hash the position (`SettlementNames`); given names and
        surnames are hardcoded lists in `PopulationPlanner`. Neither reads a
        culture.

      The blueprint side, by contrast, is ready and untouched:
      `BlueprintPlacer.styleCandidates` already resolves `kingdoms:norman/house`
      and falls back to plain `kingdoms:house`, so a culture only draws what it
      wants to differ on. Nothing yet *produces* a styled id — the catalogue
      emits plain ones and `Culture` carries a layout, not a style.
      **What to decide:** how far a culture is allowed to reach. Animals and
      building styles alone is a day's work; catalogue, professions, staffing
      and names is a different project.

- [ ] **A `.blueprint` reader, for the MineColonies/Structurize content
      ecosystem.** Structurize's format is extended structure NBT and Keystone
      already has the seam: implement `BlueprintSource`, call
      `Blueprints.register`, no call sites touched. `KEYSTONE.md` puts the
      adapter at roughly 150 lines.
      **After the culture work**, softly: the reader's whole value is content
      that varies, and a second culture is what creates the appetite for it —
      and whatever the culture work settles about styled ids is the shelf an
      imported pack of huts would have to be filed on.

---

## Needs eyes, not tests

Deliberately outside the phases. Everything here runs without throwing and
produces the right numbers; what no automated check can confirm is whether it
*looks* right, and no agent can answer any of it. This list is worked through by
playing, not by scheduling. Several of these will want asking again after the
in-flight footprint and foundation work, which changes what a town looks like on
sloping ground.

- [ ] Are the animal pens actually separated, and do the beasts stay in them?
- [ ] Do the wider paths reach the doors, and stay clear of trees now?
- [ ] Do guards visibly carry swords once a smithy stands?
- [ ] Is a hillside build's material cost bearable in real time, or a grind?
- [ ] Does the quest board read well, or is it a wall of numbers?
- [ ] Does the town overview screen look the part, and are the icons legible?
- [ ] Do the lamp's building outlines read clearly, or is a dense town a blur?
- [ ] Does the town map read as a plan, and is the fixed claim scale right?
- [ ] Do turned buildings actually face the centre, doors and stairs included?
- [ ] Does a level-2 building read as an upgrade, and does the old one clear cleanly?
- [ ] Is builders walking to the warehouse for every load too slow to watch?
- [ ] Does a crew of six digging a hillside read as a crew, or as a scrum?
- [ ] Does the excavation stake feel like a usable tool for marking out ground?
- [ ] Does the town read as spaced-out now, or has it become sprawling?
- [ ] Does the post-then-hole-then-walls sequence read as construction, or as clutter?
- [ ] Do the hollow planned plots on the map read as plans, or as bugs?
- [ ] Does a farmer working the rows read as farming — harvest, tend, replant?

---

## Done

*Short on purpose. Everything from the milestone-complete era has been dropped —
it was proven by the endurance and client playtests and it lives in the git
history and in `ROADMAP.md`. What is left is the recent work, kept until a run
has been watched over it.*

- **Gates yield to citizens.** A closed fence gate is a wall to vanilla mobs —
  which kept animals penned and shepherds penned in with them. Gates now work
  like saloon doors: a citizen walking up swings one open, the town shuts it
  after a moment with nobody near, and pens are only ever open for the seconds
  somebody is passing through. Real wooden doors from authored blueprints open
  for citizens too, the vanilla way.

- **The wheat is the food now.** Generation was the last fully abstract producer;
  it follows the lumber camp's rule at last. Watched farms produce through real
  hands — harvest mature wheat into the farm's stores and replant in one swing,
  tend growth forward, plant bare soil — while the clock works unwatched farms,
  stands aside where real harvests are fresh, and floors a watched farm nobody
  can reach. The starving eat from the rows as a last resort, because a watched
  town jams with food capped at the farms while hauling lags.

- **Being watched must never starve a town.** A parked client beside a steep town
  killed all 25 residents: embodied haulers must genuinely walk, mob navigation
  cannot climb everything a town builds on, and being watched is what embodies
  them — so the player's presence was the famine. Errands now get a fair spell of
  real walking, then the clock delivers. Economy itself proven sound headless:
  total food 302→911 over 750 steps while population grew 36→46.

- **Two audit false positives, both caught by the player from inside the world.**
  A dirt-path doorstep fails vanilla's sturdy-face test, so the houses with a
  track laid to their door were the ones flagged "no way in" — doorsteps are now
  judged standable, with a one-step tolerance. And the "items popping crops"
  drizzle was support-loss drops from ordinary excavation (grass and leaf litter
  popping off dug blocks); diggers clear the plant first now, and loose items
  only testify beside a field that is also losing its planting.

- **The crop mystery, solved by instruments.** Fields kept churning into seed
  items through three plausible-and-wrong theories (trampling, light, placement
  order — the last ruled out by a probe that never fired). The auditor now
  remembers where crops stood and names what replaced each one that vanished;
  every report said the same thing: water over intact farmland. The farm was
  built one block too deep — its ground layer is drawn below its base, so the
  standard floor convention sank the field to where any pond at natural grade
  holds its water, and fields flooded from the rim. Farms now base at the first
  air block, putting farmland exactly where a player tills.

- **Towns no longer sprawl into the next biome.** Site searches permanently
  consumed ring-plot indices on every rejected candidate, and relocation checks
  search every simulation step — a town beside a lake burned hundreds of slots
  without building anything and planned farms 260 blocks out. Indices are now
  spent only when a plot is actually taken.

- **A building announces itself from the first day.** The post block is the first
  thing laid at a new site — standing at its final spot from the moment the
  ground is surveyed, its cell withheld from the excavation so no digger levels
  it — and clicking it while work is under way reports what is being built and
  how far along it is. Planned buildings show on the surveyor's lamp and on the
  town map too, drawn hollow, so the town's intentions are as visible as its
  walls.

- **The town audits itself.** `/civ audit` walks every loaded building and
  reports what live play kept finding and logs never showed: standing water in
  the rooms, floors buried in or perched over their own ground, walls through
  another building's walls, no doorway at grade on any side, fields half bare or
  strewn with popped seed items, and buildings the simulation records but the
  world never drew. The same sweep runs on its own once a minute (debug-gated)
  and writes `AUDIT` lines to the log only when a town's fault list changes — so
  the scripted playtests now catch world-geometry regressions that used to need
  a person walking through the town.

- **Buildings were being built through each other.** Ring slots were only ever
  candidate points and nothing knew how broad a farm is, so plots overlapped —
  and since raising a building excavates its plot first, a plot laid over a
  standing granary did not squeeze in beside it, it demolished it. Every building
  type now declares the ground it takes, two plots may never touch, and rings are
  spaced for the buildings that actually go on them. The urgent-producer path had
  its own copy of the bug: it took the next ring slot unchecked. Steps are exempt,
  being a path to a door rather than a plot.

- **Trees are felled, not written off.** A crown ten blocks up has nowhere anybody
  can stand, so top-down digging could only ever set it aside and build around a
  trunk left standing in the floor. A tree in a site is now one job at its stump,
  priced at what every log in it would take, and comes down in one stroke. And a
  site whose top is out of reach starts lower: unreachable blocks are set aside
  rather than destroyed, which lifts them off the column beneath so the dig can
  get on at the highest layer somebody can actually get to. Only a block that
  fails three separate times is given up on.

- **A plot with a tree on it surveyed its floor at the top of the tree.** The
  heightmap counts a trunk as the surface, so the building was pitched into the
  branches. Ground level now walks down through growth, and the plot heights, the
  stair flights and the paths all use it.

- **Digging rebuilt.** A block now takes exactly the ticks vanilla says it takes
  for the tool in hand, spent one at a time off the server tick rather than
  sampled every fifth tick against a work budget, and it visibly cracks while it
  happens. Diggers stand beside the block, never in it, on a square checked for a
  body-sized gap, footing and a real A* path. The job itself is sliced top down —
  a block is only offered when nothing above it in its column is still wanted — cut
  into 3x3 cells that diggers claim one at a time from a shared pool, so the crowd
  spreads along the face instead of converging on one block, and load balances
  itself over broken ground whatever the headcount. Comes with the excavation
  stake: mark two corners, and the nearest town clears the box.

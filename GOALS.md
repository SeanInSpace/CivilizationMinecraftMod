# Goals

Working list for the self-sufficiency milestone. Items move to **Done** with a
one-line summary, and are dropped entirely once they have been played and hold up
— this file is a worklist, not a changelog. The changelog is the git history.

**Milestone:** a town that secures its own materials, feeds itself, equips itself,
and can be traded with — without the player doing any of it for them.

Where an item is not properly specified, the open question is written down rather
than papered over.

---

## Where the mod stands

Roughly 22,700 lines of source across three modules — `common` 8,600 (pure
simulation, never imports Minecraft), `neoforge` 11,600 (the world, the entities,
the blocks), `keystone` 2,500 (blueprints). 374 tests pass and there are no
TODO markers anywhere in the source.

The milestone is substantially met: a charter-founded party of four climbs
camp → homestead → fortified → village → town on its own, feeds itself, equips
itself from its own forge, walls itself, and expands. That has been watched
end to end, most recently over 570 unattended steps to 47 residents.

Two structural weaknesses are worth stating plainly at the top, because they
shape most of what follows.

**Most of the platform half is still only reachable by playing.** `neoforge`
has a test source set now, and the auditor's geometry has a seam in front of
its block reads, so that much can be driven from a hand-built world. The rest
cannot. The JUnit game populates the registries and answers questions about
them, and that is all: item components are never bound, so no test here can
hold an `ItemStack`, and nothing supplies a `ServerLevel`. The blueprint
placer, the excavation, the chest mirror's world half and every entity
behaviour are still covered only by playtests. The seam that fixed this for the
auditor is the pattern for fixing it elsewhere, and it is cheap — one narrow
interface, one wrapper, one fake.

**The audit is the only instrument.** It is good, and it is now self-checking,
but everything it cannot reach — anything about how the town *reads* — still
needs a person. The "Needs eyes" list is not a backlog; it is the part of the
work that cannot be delegated.

---

## Open

- [ ] **Crops are still being lost, and the recorded cause is not the whole
      cause.** A run today reported `half the field is bare — 72 farmland, 34
      planted` and `bare AND strewn with 21 items` on the same farm, repeatedly.
      The flooding fix is in and correct — farms base at the first air block, so
      farmland sits where a player would till it — but the field is still
      emptying. Since the auditor names what replaced each vanished crop, the
      next step is to read those reports rather than theorise: three plausible
      causes have already been wrong once each (trampling, light, placement
      order). Treat any new theory as unproven until the auditor names it.

- [ ] **Buildings with no doorway at grade — and the check is probably right.**
      Four in one run: a storehouse, a lumber camp and two houses. The doorway
      check is now tested against hand-built worlds and passes every case its
      own javadoc claims — a door on any of the four sides, a doorstep that is
      the town's own dirt path, a door at the head of its own stair, a door onto
      a shelf a block proud, a shut fence gate, and the two genuine failures
      (a door onto a pit, a door the terrain has closed over). So this is very
      likely a real defect in what gets built rather than a false positive in
      what gets reported. **Where to look next:** the check reads the wall ring
      at the *recorded* floor (`Footprint.y`), so the leading suspicion is
      buildings whose foundation courses leave the real doorway at a different
      height from the record — go and stand at `148, 107, -48` and compare the
      door's actual y against what `/civ info` reports for that building.

- [ ] **Put the same seam in front of `StoreSync`.** The auditor now reads the
      world through `WorldView` and its geometry is tested against fakes; the
      chest mirror still takes a `ServerLevel` directly, so the one part of the
      storage work with no unit test is the part that actually touches chests —
      finding a store's container, reading a player's withdrawal back out,
      laying the ledger onto shelves. The seam it needs is narrower than the
      auditor's: find a block entity, read and write its slots. The
      reconciliation arithmetic is already covered in `:common`; what is missing
      is everything around it.

- [ ] **Goods do not move between stores, because nothing asks them to.**
      Produce now lands at the store nearest where it was made and a builder
      fetches from the nearest store that actually holds what they need, so
      nothing deadlocks. What is missing is the courier. `HaulPlanner` already
      walks a load between two points at both fidelities, with the goods on the
      carrier's back the whole way — the transport half is done. The missing
      half is a *demand signal*: `HaulTask` is food-shaped (`FARM`, `GRANARY`,
      `MARKET`, `HOME`, routed through `FoodPlanner.withdraw`/`deposit`) and
      would need generalising. **What to decide:** a naive "even out the
      stores" rule oscillates. The non-oscillating version is demand-driven —
      a builder short at their nearest store *is* the request — which is
      MineColonies' request system in miniature. Worth reading how they do it:
      resolver priority is the whole of their locality rule (own building 200,
      crafting 125, warehouse default, retry 50).

- [ ] **Unwatched production is attributed to one camp.** `LumberPlanner.campPos`
      and `MinePlanner.minePos` return the *first* camp or mine they find, so a
      town with two lumber camps puts all its unwatched timber at one of them.
      Still geometry rather than list order, and the watched path knows the
      exact block — but a large town spreads its goods more coarsely than it
      should. Fixing it means splitting the aggregate worker counts by which
      camp each is assigned to.

- [ ] **A store is recognised by its name.** `Building.isStore()` matches
      blueprint ids containing `storehouse` or `warehouse`. Upgrades are safe
      today (`storehouse_l2` still matches, verified in play) but a store
      blueprint that is ever called something else would silently stop being a
      holder, and its goods would sit in a ledger nothing reads. A declared
      building role would end the whole class of problem.

- [ ] **Fix digging time — it is currently too fast.** A block takes exactly the
      ticks vanilla gives a player with the right iron tool
      (`Excavation.digTicks`), and a whole crew digs in parallel, so a site
      vanishes quicker than labour should read. Settlers are not players with
      fresh iron tools; the pace needs slowing until an excavation reads as
      work.

- [ ] **Create buildings in a much more intelligent way.**

- [ ] **Walls for advanced settlements.** Compute an α-shape concave hull around
      all settlement asset bounding boxes with a safety margin, then optimize
      this perimeter using an active contour (Snake) energy-minimization model
      over a multi-layer terrain influence map. The energy function balances
      perimeter length and curvature against a terrain cost field that rewards
      natural chokepoints, plateau edges, and contour-line alignment while
      heavily penalizing steep elevation changes and deep water. Once the 2D
      closed spline settles into its local minimum, sample intersections with
      outgoing A* pathfinding flow fields to inject gatehouses, then output the
      final segmented curve to the wall-construction pipeline. A wall blueprint
      builds the defensive perimeter. **Advanced settlements only** — small
      villages and towns that cannot afford one do not get one.
      **The seam is ready:** the founding work landed `Perimeter` (vertex loop,
      gates, laying cursor) and everything downstream of it — persistence, paid
      raising, gate drawing, sentry patrol, ring-aware siting. This wall
      replaces exactly one method, `PerimeterPlanner.stake`; the handoff notes
      live in `FOUNDING.md` under "The wall interface".

- [ ] **A second culture, to prove the hook earns its keep.** The claim is that
      a second culture is a table entry rather than new code. Known to be false
      in at least these places: `BlueprintPlacer.animalFarm` sizes the compound
      from `Culture.DEFAULT.penCount()` while `ShepherdWorker` stocks the pens
      from the *settlement's* culture; `BuildCatalogue` reserves a fixed plot
      for `kingdoms:animal_farm` regardless of how many beasts a culture keeps;
      every settlement starts on `BuildCatalogue.DEFAULT` and nothing picks a
      catalogue by culture; settlement and person names read no culture at all.
      The blueprint side is ready and untouched — `styleCandidates` already
      resolves `kingdoms:norman/house` and falls back to plain — but nothing
      yet *produces* a styled id. **What to decide:** how far a culture is
      allowed to reach. Animals and building styles alone is a day's work;
      catalogue, professions, staffing and names is a different project.

---

## Housekeeping

- [ ] **Twenty stale worktrees and eighteen stale branches** under
      `.claude/worktrees/`, left over from batched agent runs. Four carry
      unmerged commits totalling roughly 1,500 lines, tests included:
      blueprint transforms with a 117-line `TransformsTest`
      (`a1189e6c722c47a38`); a mine rework with a 279-line `MinePlannerTest`
      (`a551f32fe3c7672c4`); a `BuildPlanner` change with `SupplyTest` additions
      (`a75374de228c69b47`); and an `UnwatchedBridge` with a 231-line test
      (`ae276793f6c09e959`) — its build change is superseded now, but the bridge
      itself was never looked at. All four predate the storage reshape and touch
      files that have since changed underneath them, so they want reading and
      re-deriving rather than merging. Decide keep-or-drop per branch, then
      clear the rest — twenty worktrees is a slow `git status` and a standing
      invitation to merge something stale.

---

## Needs eyes, not tests

Deliberately unordered. Everything here runs without throwing and produces the
right numbers; what no automated check can confirm is whether it *looks* right,
and no agent can answer any of it. This list is worked through by playing, not by
scheduling. Several of these want asking again now that the footprint and
foundation work has landed, which changes what a town looks like on sloping
ground.

- [ ] Does opening a town's stores read as the town's stores, or as a loot chest?
- [ ] With two stores standing, does the split between them read as sensible —
      timber by the woods, stone by the mine — or as goods scattered at random?
- [ ] Does a builder fetching from the *nearest* store still read as too much
      walking, now that they no longer cross the village for every load?
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
- [ ] Does a crew of six digging a hillside read as a crew, or as a scrum?
- [ ] Does the excavation stake feel like a usable tool for marking out ground?
- [ ] Does the town read as spaced-out now, or has it become sprawling?
- [ ] Does the post-then-hole-then-walls sequence read as construction, or as clutter?
- [ ] Do the hollow planned plots on the map read as plans, or as bugs?
- [ ] Does a farmer working the rows read as farming — harvest, tend, replant?
- [ ] Does the distress banner on the posts and the hall read clearly, and only
      when it should?

---

## Done

- [x] **The auditor's geometry can be run without a town.** Its block reads go
      through `WorldView` — nine questions and a clock — with `LevelWorldView`
      answering them from a real server and a `FakeWorld` answering them from a
      `HashSet` of solid blocks. `TownAuditor` is down to five Minecraft
      imports, only one of which is a live-server type, and that only for the
      convenience overloads callers use. Thirteen tests now put a house in a
      hand-built world and ask what the auditor makes of it. The immediate
      payoff: the doorway check passes every case its own javadoc claims, which
      moves "four buildings with no way in" from an unexplained report to a
      probable real defect — see Open.

- [x] **`neoforge` has tests at last.** Eleven thousand lines had none, because
      everything in the module can reach Minecraft and Minecraft cannot be
      constructed inside a JUnit run. ModDevGradle's `unitTest` starts a real
      bootstrapped game on the test classpath — FML loads, both mods load, 1564
      items answer to their ids. Fifteen tests so far: the auditor's judgement
      and its self-check, and the join between the ledger's words and the game's
      registry, which nothing had ever checked — a typo or a vanilla rename in
      `Resources` would have shown up only as a store that quietly could not pay
      anything out. What the environment will and will not do is written down in
      `StoreChestBlockEntityTest`, measured rather than assumed, so the next
      person does not spend an afternoon rediscovering that item components are
      never bound.

- [x] **The town's goods are somewhere in particular.** A settlement kept one
      number per resource and no notion of where any of it was, which is what
      let two chests each hand out the same timber. Buildings hold goods now, a
      loose pile holds whatever is not yet indoors, and the town total is
      summed on demand rather than stored — so the sum that had to be got right
      is not computed anywhere. One chest mirrors exactly one building's
      ledger; carrying goods between stores is a real transfer rather than
      something that had to be made invisible. Produce lands at the store
      nearest where it was made, a builder walks to the shelves they actually
      draw from, and a player's donation goes to the door they left it at. The
      founding kit is real at last: it arrives on open ground, because a party
      that has just stepped off the road has nowhere to put anything, and is
      swept inside the moment they raise a store. Verified over 570 unattended
      steps from a charter founding — 4 settlers to 47, timber in the
      storehouse by the lumber camp, stone and iron in the warehouse by the
      mines, iron split 13/251 across the two and summing to the total.

- [x] **A charter's party, where a test can reach it.** The founding party was
      written out inside `FoundingCharterItem`, so the one path a player can
      take was the one path no test could reach — and `/civ found`, which every
      scripted run uses, quietly raised a settlement with a kit and nobody to
      spend it. Both come through `Founding.party` now, so a headless run
      founds what a charter founds.

- [x] **The auditor can be asked to prove it is awake.** A silent auditor and a
      healthy town read identically from outside, so a clean sweep meant
      nothing. `/civ audit selftest` runs it against cases with known answers,
      each in both directions — a fault that must be caught and a near miss
      that must not be. Verified by deliberately blinding the overlap detector
      and confirming exactly one check went red.

- [x] **A `.blueprint` reader, for the MineColonies/Structurize content
      ecosystem.** `StructurizeNbt` decodes the dense `y → z → x` cell array
      (two palette indices per `int`, padding short on an odd cell count) and
      `BlockSubstitutions` answers for the foreign blocks a modpack-authored
      file is full of — Structurize's instruction blocks semantically, the
      common pack fixtures by name, everything else by suffix, properties kept
      throughout so roofs still slope. Registered as a source at priority 90;
      no call site anywhere was touched, which is what the seam was for. Drop a
      `.blueprint` in the same folder as the `.nbt` files and ask for it the
      same way. Pinned by `StructurizeNbtTest` and proven in-world on a real
      MineColonies schematic.

- [x] **The paths are remembered.** `PathNetwork` holds the roads as
      axis-aligned segments plus the buildings already joined, both persisted;
      `PathPlanner` joins one building a step, branching off the nearest
      existing way unless the hub is genuinely closer, routing at right angles,
      and leaving by the door the building actually faces
      (`Building.doorstep()`, which the access-repair stairs now share).
      `PathLayer` draws and mends through one operation — a stretch is re-laid
      only once a quarter of it has grown over, one stretch a sweep. The town
      map draws the network under the buildings. The hub is the hall when there
      is one and the camp post before that, which is what gives a camp streets
      from its first day; the old hall-only hub meant no settlement below TOWN
      had any roads at all. Over-long routes leave a building unjoined and
      retry as the network reaches it, instead of being recorded as connected
      and dropped. Pinned by `PathNetworkTest`.

- [x] **The palisade sites its gates on the streets.** One to a side, on
      whichever road reaches furthest that way, re-sited while the wall goes up
      and fixed when it closes. This was the `FOUNDING.md` promise that the
      unremembered network made impossible.

- [x] **The founding party — the staged progression.** All six steps of
      `FOUNDING.md` are built: camp → homestead → fortified → village → town,
      condition-gated, hall last. Pioneers labour as generalists below VILLAGE
      and crystallize as the stages demand them; the camp forages under a
      hand-to-mouth ceiling; the palisade closes and a sentry walks its
      vertices; the storehouse trades with the player; cottages unlock births;
      mill, carpentry and inn each earn their keep; expansion gates on the
      hall and the parent pays the daughter's founding kit — daughters land as
      camps of pioneers and climb the same ladder. Pinned end to end by
      `StageProgressionTest.aCampLeftAloneClimbsTheWholeLadderToTown`. The
      perimeter's α-wall handoff is documented in `FOUNDING.md` ("The wall
      interface"): the concave wall replaces `PerimeterPlanner.stake` and
      nothing else.

*Short on purpose. Everything from the milestone-complete era has been dropped —
it was proven by the endurance and client playtests and it lives in the git
history. What is left is the recent work, kept until a run has been watched over
it.*

- **The ground a town takes, taken differently.** The two-block skirt of
  flattened land halved to one, with the catalogue's plot reservations narrowed
  to match so the tightening is real in play and not only in the placer. The
  base of a building now comes from the plot's median column height rather than
  the origin column alone, so ground that falls away is packed up with
  foundation courses instead of the hill being cut out from under the walls.
  And water sites are refused outright — every column, the full depth a
  building occupies, sized for the widest plot in the catalogue. Verified in a
  playtest: 34 buildings, zero audit faults, worst distance from centre 60
  blocks where the same town used to pass 130.

- **A town in trouble says so.** A four-rung distress reading — steady, hungry,
  failing, dying — leads every building post's report and the hall's overview
  screen, and the audit sweep's vitals line carries a food-reserve figure and a
  distress verdict for every settlement, loaded or not. A famine now reaches
  the log minutes before it reaches an obituary.

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
  stake: mark two corners, and the nearest town clears the box. (Pace is now
  judged too fast in play — see Open.)

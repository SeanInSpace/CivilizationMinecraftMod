# Goals

Working list for the self-sufficiency milestone. Items move to **Done** with a
one-line summary, and are dropped entirely once they have been played and hold up
— this file is a worklist, not a changelog. The changelog is the git history.

**Milestone:** a town that secures its own materials, feeds itself, equips itself,
and can be traded with — without the player doing any of it for them.

Where an item is not properly specified, the open question is written down rather
than papered over.

---

## Open

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

## Needs eyes, not tests

Deliberately unordered. Everything here runs without throwing and produces the
right numbers; what no automated check can confirm is whether it *looks* right,
and no agent can answer any of it. This list is worked through by playing, not by
scheduling. Several of these want asking again now that the footprint and
foundation work has landed, which changes what a town looks like on sloping
ground.

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
- [ ] Does the distress banner on the posts and the hall read clearly, and only
      when it should?

---

## Done

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
  the log minutes before it reaches an obituary. (The founding-party fix this
  was built around has been re-cut — see Open — but the reporting stands on its
  own.)

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
  stake: mark two corners, and the nearest town clears the box. (Pace is now
  judged too fast in play — see Open.)

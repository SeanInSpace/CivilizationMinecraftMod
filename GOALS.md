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

Roughly 24,200 lines of source across three modules — `common` 9,600 (pure
simulation, never imports Minecraft), `neoforge` 12,100 (the world, the
entities, the blocks), `keystone` 2,500 (blueprints). 477 tests across 52
classes, and no TODO markers anywhere in the source.

The milestone is substantially met: a charter-founded party of four climbs
camp → homestead → fortified → village → town on its own, feeds itself, equips
itself from its own forge, walls itself, and expands. That has been watched
end to end, most recently over 570 unattended steps to 47 residents.

Two structural weaknesses are worth stating plainly at the top, because they
shape most of what follows.

**The rest of the platform half is still only reachable by playing.** `neoforge`
has a test source set now, and the two subsystems that kept producing bugs — the
auditor's geometry and the chest mirror — each read the world through a seam, so
both can be driven from fakes. What is left has none: the blueprint placer, the
excavation, and every entity behaviour. The JUnit game populates the registries
and answers questions about them, and that is all — item components are never
bound, so no test here can hold an `ItemStack`, and nothing supplies a
`ServerLevel`. The seam is the pattern for fixing that, and it is cheap: one
narrow interface, one wrapper, one fake. It is also worth doing in the order
the bugs appeared, not the order the packages are listed.

**The audit is the only instrument.** It is good, and it is now self-checking,
but everything it cannot reach — anything about how the town *reads* — still
needs a person. The "Needs eyes" list is not a backlog; it is the part of the
work that cannot be delegated.

---

## Open

- [ ] **The placer and the excavation are only half reachable.** Their pure
      decisions are tested now — where a building's floor sits across a sloping
      plot, and where a digger can stand to reach a block — but everything that
      actually reads or writes blocks still needs a world. Neither has a seam
      like `WorldView` or `Shelves`. The excavation's would be the easier of the
      two and would look much like the auditor's, being mostly questions about
      what is at a position; the placer's is harder because it writes far more
      than it reads, and the interesting question there is whether a plan can be
      produced as a list of placements and only then applied, which would make
      the plan itself testable without a seam at all.


- [ ] **Create buildings in a much more intelligent way.** Never specified, so
      here is what it appears to mean after a few weeks of watching towns build
      themselves. Three separate questions, worth separating because they have
      different answers:
      **Where.** Half done. A town now weighs the nearest dozen usable plots
      against how far each is from its own streets and takes the closest to one,
      rather than the first slot that fits — and it advances its ring index only
      to the first fit, so choosing more carefully does not also make it creep
      outward faster. What is left is that this only bites once a town has
      streets, and a town nobody has visited has none: `PathPlanner` needs a
      building's doorstep, which needs its footprint, which is not known until
      the structure is actually placed. So an unwatched town still sites by ring
      order. The other half of the rule is unbuilt — nothing prefers ground near
      the buildings it works with, the lumber camp by the woods or the granary
      by the fields.
      **What next.** Half done. Ties are broken on the shortfall as a *share* of
      what is wanted, so the first of a kind outranks the fourth of another — a
      town no longer raises a fourth house while it has nowhere to put anything.
      What is still true is that nothing reacts to circumstance: a mine is worth
      no more the moment stone runs short than it was the day before. The
      urgent-producer path is the one place that reasons about need, and it is
      still a special case bolted beside the table rather than the rule.
      **How well.** The premise was wrong and the fix was underneath it.
      Choosing upgrades "more intelligently" was pointless while upgrading
      gained nothing: every ceiling counted store *buildings*, so a storehouse
      raised to level two held exactly what it had before. Capacity now counts
      levels, which makes "improve the lowest first" an even distribution of a
      real effect rather than of none. What is still true is that a level only
      changes capacity and the drawing — nothing else about a building improves,
      and a grander smithy forges no faster.


---

## Needs eyes, not tests

Deliberately unordered. Everything here runs without throwing and produces the
right numbers; what no automated check can confirm is whether it *looks* right,
and no agent can answer any of it. This list is worked through by playing, not by
scheduling. Several of these want asking again now that the footprint and
foundation work has landed, which changes what a town looks like on sloping
ground.

- [ ] The larder's ceiling counts granaries and storehouses but not warehouses,
      while the timber and stone ceilings count every store. Preserved rather
      than changed while making capacity scale with level, because it may well
      be deliberate — a warehouse full of logs is not a pantry. Worth somebody
      deciding on purpose.
- [ ] Three buildings a town still report `no way in`, down from nine. The
      doorway check itself is tested and correct, and the floor rule that was
      sinking buildings into hillsides is fixed — so what is left is most likely
      the plots the new rule perches rather than sinks, which the auditor also
      reports and which wants somebody to go and look at one.
- [ ] Does the palisade read as a wall around a town now, rather than a box
      around a field? It follows the buildings and drifts along contours; both
      are claims about how it looks from inside the gate.
- [ ] Does digging read as labour now, at `Excavation.LABOUR_FACTOR` of two —
      and does a watched town still get its farm up in time? The factor is one
      named constant, so this is a question about a number, not a rewrite. Three
      was the tempting value and was deliberately not taken: watched digging is
      not on the clock, and the stall guard measures whether the queue head
      moved rather than how fast, so slowing the dig slows a watched town's
      building outright.
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

- [x] **The four rescued branches are decided.** Read, judged one at a time,
      and their value re-derived on main rather than merged — all four predated
      the storage reshape and touched files that had moved underneath them. The
      keystone one was right and is now in, verified against a real MineColonies
      file rather than taken on trust. The mine one was right and was
      understated: the same hole was in timber as well as stone, and fixing it
      turned up a worse bug of my own. The `RESCUE_HEAD_START` one was verified
      broken in an earlier session — its gate can never lift below VILLAGE,
      because births need a family home and the bunkhouse is not one — and is
      dropped. The `UnwatchedBridge` one diagnosed `/civ step` correctly and is
      superseded: a grace on real work covers it without a wrapper, and covers
      every other watched-but-idle case too. The refs are left in place as an
      archive; nothing in them is now unrepresented on main.

- [x] **A town builds in its own style.** The placer composes the styled path
      at the last possible moment, from the culture of the town whose ground it
      is standing on, so `kingdoms:norman/house` is tried before
      `kingdoms:house` and a culture inherits every building it has not drawn.
      Applying the style at the file lookup rather than carrying it in the id is
      what made this small: every comparison of a blueprint id against a
      catalogue row strips a level suffix and none of them strips a culture
      folder, so a styled id would have quietly stopped matching its own row.
      The id stays plain everywhere it is reasoned about. **Unexercised until
      somebody draws one** — no styled blueprint exists yet, so today every
      culture still falls through to the same shapes, which is exactly what it
      should do.

- [x] **A second people, and the pens that belong to them.** `NORMAN` is now a
      real entry rather than a name every lookup fell through on, and `HIGHLAND`
      is the second — goats and rabbits where the lowlanders keep pigs and cows.
      The live defect it exposed: the placer sized the animal compound from the
      default culture while the shepherd stocked its pens from the settlement's
      own, which agreed for exactly as long as there was one culture. The placer
      now reads the culture of the town whose claim the ground falls in. A test
      over every culture holds them to the plot the catalogue reserves, so a
      fifth pen fails loudly rather than as a compound built through a wall.

- [x] **The wall follows the town instead of boxing it.** A concave hull over
      the plot corners, dug in from the convex hull so an outlying farm adds
      corners rather than fortifying the field between; then a greedy active
      contour settles the line onto the ground, each vertex weighing how uneven
      the ground under it is against how long and crooked the line through it
      is, so a wall drifts along a contour rather than marching up one. Every
      candidate move is checked for containment first: the terrain may move the
      line anywhere it likes and may never talk it into leaving a building
      outside. Staked at 196 posts in a headless run where the rectangle wanted
      half again as many.

- [x] **The crops were never being destroyed.** Four theories had been wrong
      about this — trampling, light, placement order, flooding — and the fourth
      was a real bug that really was fixed, which is why nobody questioned the
      premise. The evidence that broke it was in the log all along: the audit
      reported the same `72 farmland, 34 planted` on three consecutive sweeps
      while the vanished-crop tracker, which names what replaced each crop that
      disappears, never fired once. Nothing was being destroyed. The field was
      simply never being filled. `FarmWorker` ordered its jobs harvest, tend,
      plant — and tending nudges one crop's age up by one, so a field with any
      growing crop in it always has something to tend and the planting branch
      was reached only in the instant every crop was simultaneously ripe. Now
      harvest, plant, tend: fill the field, then optimise it. **Still wants a
      run watched over it** — the "strewn with items" fault only fires beside a
      field that is also bare, so it should go quiet too, and that is a claim
      about play rather than about the ordering.

- [x] **Goods move to the store that is about to need them.** `SupplyPlanner`
      sends at most one courier a step, and the signal is a build rather than a
      difference between two stores — what is the town raising, which store is
      nearest it, is that store short. An "even the stores out" rule oscillates,
      because every move it makes creates the imbalance that justifies moving
      something back; this only ever moves goods toward work already waiting on
      them. A source must hold a full load *above* the shortage line before it
      gives any away, which is the hysteresis that stops two stores passing the
      same timber back and forth forever. `HaulTask` carries a resource now, so
      the courier rides the same errand system the food economy has always used
      — walked at both fidelities, with the load on somebody's back the whole
      way. Builders are passed over when picking a carrier: the demand *is* a
      build, so sending its own builder to fetch its materials would stop the
      work in order to supply it.

- [x] **Unwatched production lands at the site that made it.** The aggregate
      planners credited everything to whichever camp or mine was listed first,
      so a town with two of either piled its goods at one and left the other's
      shelves empty for good. `Workforce.shareOf` divides the crew between the
      sites — evenly, remainder to the earliest — and each share is put down at
      its own camp. The shares add back up to the crew, which is the invariant
      that matters: the rate was priced against the whole crew, so losing
      somebody to rounding would quietly slow the town down.

- [x] **A building says what it is for, once.** Eleven places worked out what
      they were looking at by searching a blueprint id for a substring, and the
      dangerous case was quiet: a store blueprint ever renamed would simply have
      stopped counting as one, its goods left in a ledger nothing reads.
      `BuildingRole` matches the bare building name with namespace, culture
      folder and level suffix stripped, on exact names rather than substrings.
      `JobPlanner`'s staffing table names roles instead of spelling strings.
      Zero substring matches on blueprint ids remain in the source. Also cleared
      fourteen stale agent worktrees and branches that held nothing not already
      in main; the four that carry real work are kept, and listed under Open.

- [x] **The chest mirror can be run without a chest.** `StoreSync` reads the
      world through `StoreWorld` (find a building's shelves, say the books
      moved) and `Shelves` (slots, in resources and counts). Deliberately not in
      `ItemStack`s: a JUnit game never binds item components, so a seam that
      spoke in stacks would have been untestable for exactly the reason the code
      above it was — and "sixty-four of what the town calls wood" is the truer
      thing to say anyway. `ChestShelves` does the translating, `LevelStoreWorld`
      owns the chest hunt and its cache, and `StoreSync` is down to one Minecraft
      import for the convenience overload. Twelve tests, including both ways this
      once made timber out of nothing — each store answering only to its own
      ledger, and a stack carried between two stores reading as a withdrawal and
      a donation rather than as nothing at all.

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

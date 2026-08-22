# Goals

Working list for the self-sufficiency milestone. Items move to **Done** with a
one-line summary, and are dropped entirely once they have been played and hold up
— this file is a worklist, not a changelog. The changelog is the git history.

**Milestone:** a town that secures its own materials, feeds itself, equips itself,
and can be traded with — without the player doing any of it for them.

---

## In progress

### Needs eyes, not tests
Everything below runs without throwing and produces the right numbers. What no
automated check can confirm is whether it *looks* right:

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

### Next
- [ ] Hideouts, so `hideouts_cleared` counts something
- [ ] A second culture, to prove the hook earns its keep
- [ ] `.blueprint` reader, for the MineColonies/Structurize content ecosystem

---

## Done

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


- **Playtest of the five additions, and two bugs it found.** Levels broke every
  remaining `endsWith` id match — the food chain, the workplace lookup, the path
  layer and the animal farm all stopped recognising a building the moment it was
  improved. And the farm-to-granary link could not keep up: a run ended with 156
  of harvest banked in the fields, the granary at four, and somebody starving in
  the middle of it. Both fixed; a 500-step run now reaches 48 people and 67
  buildings with the fields clear and nobody hungry enough to matter.

- **Five things Millénaire and MineColonies do better, added.** Site validation
  (no more building in lakes or across ravines), orientation (doors face the town
  centre instead of all facing south), building levels 1–3 raised in place with
  the old walls coming down as part of the excavation, a real per-item bill of
  materials with player supply at the warehouse post, and builders who fetch a
  load from the stores in person rather than conjuring materials at the wall.

- **Clearing only clears what is in the way.** Anything a block can be placed into
  — snow, grass, flowers — is never dug; the course landing on it covers it over.
  Dig cost is vanilla's own break time with the right tool rather than a flat
  figure, so soil goes in one pass instead of three. Leaves are held to the end of
  the excavation, and lumberjacks now fell from the stump rather than the crown,
  so they stop trying to reach a trunk from inside its own canopy.

- **Town Map.** A drawn plan of the nearest settlement: blank ground, buildings
  in green at their real footprints, north up, player marked. Scale follows the
  town's claim so any size town fills the square.

- **Building dimensions are tracked.** `Footprint` (floor height, width, depth,
  height) recorded when a plan is built — from the survey on the hand-built path,
  reported back by the bridge on the stamped one — persisted, and backfilled by
  measurement for worlds that predate it.
- **Surveyor's Lamp.** Hold it and every nearby building draws its bounds in
  sparks. Server-side particles like the charter's claim ring, so a vanilla
  client sees it too.

- **Town overview screen.** Right-click the Town Hall post: name, population and
  the whole ledger, a row per resource with its own icon. First real GUI in
  Kingdoms — a server-to-client payload and a drawn panel, built on the same
  extract-render-state shape Keystone's wand screen uses. `/civ overview` opens
  it without walking to a hall.

- **Three faults from live play.** Trees inside the village blocked every path
  (lumberjacks only worked the woodland claim, and replanted anywhere in it);
  paths were a single block wide; and buildings were placed twice — the hand-built
  and stamped paths used different floor conventions, so a build watched partway
  and then abandoned produced two copies a course apart.

- **Endurance run.** 700 steps from five settlers: three settlements, 55 / 32 / 5
  buildings, both mature towns fully equipped (48/48 and 35/35), twelve raids
  repelled, zero exceptions, zero starvation. Iron capped afterwards — the forge
  stops at its ceilings and the ore was piling up unspent.
- **Client playtest.** Quickplay into a played-out save: world loads, all 44
  buildings materialize — smiths, animal farm, watchtowers, markets — and nothing
  in `kingdoms` or `keystone` throws across a 5,959-line log.

- **Headless playtest, and five bugs it found.** Buildings were free when
  unwatched; production ran only in the view layer so an unwatched town could
  never make anything; staffing wanted no lumberjack below ten residents; two
  producers ordered out of turn shared one plot; and settlers starved beside a
  full granary because food could only reach them through the family pantry.
  All fixed — a town now goes from five settlers to 48 people, 44 buildings,
  every trade staffed and two thirds equipped, with nobody starving.
- **Opt-in quickplay** (`-Pquickplay=<world>`), so a missing world can no longer
  break every launch.

- **Generic town ledger.** `TownStores`: resource id → amount, all-or-nothing
  spending. Replaced four hardcoded ints; codec writes one map and still reads the
  old flat keys, so existing worlds migrate.
- **Supply is limited.** Laying a block spends wood or stone, keyed off the same
  tags that pick the digging tool. Digging costs only sweat.
- **Self-sufficiency loop.** A founding party arrives stocked; when a build cannot
  be paid for, `requestProducer` orders the lumber camp or mine that fixes it,
  ahead of the thing nobody can afford. Producers are exempt from material cost so
  the bootstrap can always run.
- **Warehouse.** Building, post, and it raises the storage ceiling like a storehouse.
- **Smith.** Iron (from ore the miners cut through) plus timber as fuel becomes
  tools, then weapons, then armour. Tools are issued to workers one a step; guards
  draw a sword and chestplate from the rack and hit harder for it.
- **Larger farms.** 7×7 → 11×11, work 30 → 45, one per 6 residents rather than 5.
- **Animal farm.** Fenced compound split into strip pens, one species per pen, list
  taken from the culture. Shepherds stock them a beast at a time and leave vanilla
  to do the breeding.
- **Market hours.** Stalls open 1000–11000. The post reports hours and stock, and
  sells bread for emeralds during them — keeping a food reserve back so a town can
  never be bought into starvation.
- **Paths.** Tracks laid from each building's door to the hall, following the
  terrain. Only grass and dirt are paved and only foliage is cleared, so a path
  can never eat a wall.
- **Quest board.** Reads whatever counters a settlement happens to keep rather than
  a fixed list — so a stat can be tallied by a datapack or an addon and appear
  without the board being told. Seeded with mobs slain, raids repelled, buildings
  raised, trees felled, stone cut, goods traded. "Hideouts cleared" is a name the
  board will show the moment anything raises it.
- **Culture hook.** `Culture` carries the penned-animal list and a layout id, with
  one default — so a second culture is a table entry, not new code.
- **A post on every building**, and `/civ info` reads the ledger, the deeds, the
  culture and how much of the workforce is equipped.

# How settlements decide what to build

**Status:** implemented and working. Simple on purpose — this is the first version, meant to be replaced by datapack-driven content, not to be clever.

Code: [`BuildPlanner`](common/src/main/java/com/kingdoms/sim/settlement/BuildPlanner.java), [`BuildCatalogue`](common/src/main/java/com/kingdoms/sim/settlement/BuildCatalogue.java), [`BuildingType`](common/src/main/java/com/kingdoms/sim/settlement/BuildingType.java).

---

## The rule, in one sentence

> **A settlement builds the most important thing it is currently short of.**

Everything below is detail on what "important", "short of", and "where" mean.

---

## The four questions

Every simulation step, an idle settlement asks four things in order.

### 1. Am I free to start something?

If anything is already in the build queue, **stop — do nothing.** A settlement finishes what it started before considering anything else.

This is why a town builds one thing at a time rather than starting six projects at once. It also keeps the arithmetic honest: because only one project can be in flight, counting "how many do I have" can ignore work in progress without ever double-counting.

### 1.5. Does my stage have a program?

**Below village size, the catalogue does not run at all.** A settlement climbs a
founding ladder — camp, homestead, fortified, village, town — and each stage has
its own ordered list of what to build. That list is worked top to bottom and is
the *whole* of the plan: a camp raises its camp post and cache, a homestead its
bunkhouse, hearth, farm and granary, in that order, however loudly the shortfall
table below would like something else.

From village size the catalogue scan resumes for growth, and one rule outlives the
ladder: **the town hall may only be ordered at town stage.** It is the capstone of
the last stage, not the opening move. See [FOUNDING.md](FOUNDING.md) for the
programs and the conditions that graduate a settlement between them.

A program entry naming a blueprint the catalogue does not know is skipped rather
than built as a marker, which keeps the machine honest while content catches up.

### 2. What am I short of?

For each building type in the catalogue:

- **Am I big enough?** If population is below the type's minimum, skip it entirely. A hamlet of three does not consider a watchtower.
- **How many do I want?** `wanted = base + (population ÷ per-residents)`, using integer division so it rounds down. A flat `base` is a count that never changes with size — you want exactly one town hall whether you are 5 people or 500.
- **How many do I have?** Completed buildings of that type only.
- **Shortfall** = wanted − have. If it is zero or less, this type is satisfied, so skip it.

### 3. Which shortfall matters most?

Among everything short, **highest priority wins.** Not "biggest shortfall" — priority.

That ordering is deliberate. A settlement of 20 short of both a market and eight
houses builds the houses first, because housing gates growth and a market does
not, even when the two shortfalls are the same size.

*(This paragraph used to say a town builds its hall first "because the hall is
more important". It did, and it was wrong — a founding party's first act was civic
architecture while it slept in the open. The hall is now gated to town stage and
the priority column no longer decides anything until a settlement gets there.)*

Ties are broken by larger shortfall, then alphabetically by id. The alphabetical fallback exists purely so the outcome is deterministic — the same town state always produces the same decision, on every machine.

### 4. Where does it go?

Plots are handed out in **expanding rings** around the settlement centre, packed by circumference:

- Ring radii run `12, 22, 32, …` blocks out.
- Each ring holds as many plots as fit at roughly **10-block spacing** along its circumference (minimum 8) — 8 in the first ring, 13 in the second, 20 in the third, and so on.
- Alternate rings are staggered by half a slot, so plots never line up into rays.

Rings fill densely from the inside out — empty space near the town is used before the town expands. *(The first version used a constant eight plots per ring; live playtesting revealed the result was an eight-legged star with ever-growing gaps between spokes.)* Plots are never reused and buildings never overlap.

Height is always the settlement centre's height. There is no terrain awareness at all yet — see limitations.

**Territory follows building.** If the chosen plot falls outside the current claim, the settlement expands its claim radius to reach it plus an 8-block margin. Towns grow their borders by building, rather than having a fixed boundary set at founding. The claim never shrinks.

### Repairs jump the queue

One kind of job is not chosen by the shortfall table at all. When a resident
repeatedly fails to get home at dusk — the door standing above them, near enough
that steps would help — the settlement orders a **flight of steps up to that
door** and puts it at the *head* of the queue, pausing whatever was under way.

The flight starts **at the doorway it serves**, not at whatever the heightmap
reports for that column — which is the roof of the very house the door is set into.
*(It did once. The steps were built across the roof, useless, and buried the door on
the way.)*

The order is refused if a flight is already queued or already standing, so a
stuck resident cannot flood the queue. Failures must persist for several seconds
first, so one unlucky bit of pathfinding does not commission masonry. The steps
march out from the door, one block down and one out, until they meet the ground,
so the run is exactly as long as the drop demands.

This is the first job the town gives itself in response to something being
*wrong*, rather than something being wanted.

### How construction looks

Buildings rise **visibly, block by block**, in a mason's order:

1. When work begins, the site is surveyed once and the terrain height is locked in.
   The floor course sits at **grade** — on the last solid block, not on the first
   air above it — so a building is set into the ground rather than standing a block
   proud of it, and its doorway opens at walking height. *(It did not, once. You
   had to step up into every house, which is no doorway at all.)*
2. **The ground in the way is dug out first**, block by block, top down so nobody
   undermines what they are standing on. Even on the flat this is real work: the
   topsoil under the floor has to come out. On a slope it is most of the job.
3. **The cut runs two blocks past the walls**, levelling a shelf around the
   building. A floor at grade is only half of being able to walk in — on anything
   steeper than a gentle slope the hillside still stands over the doorway, and the
   building ends up at the bottom of a hole with its door buried. Only soil and
   living rock are taken, never wood or worked stone, so a shelf cut for one
   building can never take a bite out of the one next door. On flat ground it
   costs nothing: there is nothing above grade to remove.
4. Blocks are laid **bottom layer first** — the cobble foundation is genuinely the first course.
5. Within each layer, **full blocks go down before partial blocks** (lanterns, fences, crops, water).
6. Strictly **one layer at a time**: the next layer starts only when the one below is satisfied. Supplies are assumed satisfied for now — this ordering is exactly where a future supply gate slots in: the cursor simply stops mid-list when materials run out. That list is `LoadedBlueprint.sequence()`, and its index is the build cursor.

**Digging is slower than laying, and needs the right tool.** Laying a block is one
work unit; shifting one costs whatever vanilla says it takes to break, with the
right tool in hand — soil in a single pass, stone two, deepslate three, capped so
an obsidian outcrop cannot be watched for four hundred ticks. Builders hold the correct tool for what they are digging (shovel for
soil, pickaxe for stone, axe for wood) and swing at a block several times before it
gives, so excavation reads as effort rather than deletion. Spoil does not drop: there
is nowhere to put it yet.

Excavation is charged **on top of** the catalogue's cost rather than squeezed into it.
A building is spread over `work` builder-steps of *laying*; the digging is extra. So
the same house takes noticeably longer cut into a hillside than raised on the flat,
which is the honest answer and makes flat ground worth choosing.

**Bedrock refuses the site outright.** Anything that cannot be shifted at all means
the plot is unbuildable: the town abandons the job, says so in its history, and takes
the next plot rather than proposing the same impossible spot forever. Obsidian is not
bedrock — it is merely slow, and a town is welcome to spend the time.

One thing this does not yet do: the town does not have to **own** the tools; builders
are handed them.

**Where there is a hand, there is no clock.** If builders exist in the world as entities, they are the only thing that raises a wall — nothing accrues beside them, so a site nobody has walked to does not progress at all, and "40% built" means 40% of the blocks are standing where you can count them. A build cannot finish while its walls are still going up, so nothing is ever stamped over work in progress.

Builders have to be **at the site** — within about sixteen blocks. Being a builder somewhere in town is not enough, and neither is the stall-assist that covers a block nobody can path to: no hand present, no block.

Where no builder is embodied, the abstract clock is all there is, and the building materializes whole when its chunk next loads. The test is deliberately the *builders* and not the chunk, because the two do not coincide: settlers are released 128 blocks out while chunks stay loaded to 160 — and forever in spawn or force-loaded chunks. Keying on the chunk left a band you walk through routinely where nobody could lay a block and no clock ran either, and construction simply stopped. The two figures are kept in step, so a build that loses its builders part-way carries on from where the masonry actually got to rather than jumping.

`/civ step` drives the real construction too. Stepping passes no game ticks, so the builders would otherwise be granted blocks they never lay — and the finished building would appear on top of the half-built one. Anything standing where a block is about to go is lifted clear rather than entombed.

**Where the shape comes from.** Before falling back to its built-in shapes, a build asks [Keystone](KEYSTONE.md) for a blueprint — a file you scanned in-game or shipped in a datapack. Blueprints carry full block states, so an authored building arrives with its stairs, doors and fences facing the way they were drawn, and it is laid course by course exactly like a generated one. *(Earlier versions stamped datapack templates into the world whole, which quietly excluded the best-looking buildings from the best-looking part of the mod.)*

Ids resolve most-specific-first, so `kingdoms:norman/house` falls back to `kingdoms:house` and then to the built-in house. That is the whole of how cultures get their own architecture.

---

## The current catalogue

Twenty-one types. The **plot** column is the ground each one takes — its walls plus
the shelf cleared around them — and two plots may never overlap, which is what
stops a town building its granary through the side of its own hall.

**The founding programs** (see [FOUNDING.md](FOUNDING.md)). Priority 0 and "always
want 0" keep these off the catalogue scan entirely: only a stage's own program ever
orders one, so an established town never retrofits a camp.

| Building | Work | Min pop | Always want | Plus one per | Priority | Houses | Plot |
|---|---|---|---|---|---|---|---|
| Camp post | 6 | 1 | — | — | — | — | 7 |
| Supply cache | 10 | 1 | — | — | — | — | 7 |
| Bunkhouse | 22 | 1 | — | — | — | 6 people | 11 |
| Hearth | 12 | 1 | — | — | — | — | 9 |
| Cottage | 16 | 1 | — | — | — | 3 people | 9 |
| Mill | 30 | 1 | — | — | — | — | 9 |
| Carpentry | 30 | 1 | — | — | — | — | 9 |
| Inn | 35 | 1 | — | — | — | — | 11 |

**The growth catalogue**, ordered by priority — what a village and a town build as
they fill out.

| Building | Work | Min pop | Always want | Plus one per | Priority | Houses | Plot |
|---|---|---|---|---|---|---|---|
| Town hall | 40 | 1 | 1 | — | 100 | — | 13 |
| House | 20 | 1 | 1 | 3 residents | 80 | 4 people | 11 |
| Granary | 25 | 4 | 1 | 20 residents | 75 | — | 9 |
| Farm | 45 | 4 | 0 | 6 residents | 70 | — | 15 |
| **Lumber camp** | 30 | 5 | 1 | 30 residents | **68** | — | 9 |
| **Stone mine** | 35 | 8 | 1 | 30 residents | **66** | — | 9 |
| Warehouse | 35 | 6 | 1 | 25 residents | 64 | — | 11 |
| Market | 30 | 6 | 1 | 25 residents | 62 | — | 9 |
| Watchtower | 45 | 12 | 0 | 12 residents | 60 | — | 7 |
| Smithy | 40 | 10 | 1 | 40 residents | 57 | — | 9 |
| Animal farm | 45 | 10 | 1 | 40 residents | 56 | — | 19 |
| Storehouse | 30 | 6 | 1 | 15 residents | 55 | — | 9 |
| Workshop | 35 | 8 | 0 | 8 residents | 50 | — | 9 |

The town hall's priority of 100 now only matters *at town stage* — until then the
stage gate keeps it off the table however loudly the number argues.

**Shelter, then food, then materials, then everything else.** The lumber camp and
the mine sit above the market and the workshop on purpose: a town that cannot fell
its own timber or cut its own stone has nothing to trade and nothing to build the
next thing out of. *(The lumber camp used to sit at 58, below the watchtower — so a
town bought and crafted before it could supply itself.)*

**Every building carries its own post.** A block on the floor that names the
building, says what it is for, and reports the town's running totals — the same
idea colony mods use, where a building is a thing you walk up to and read rather
than a name in a menu. The lumber camp's and the mine's posts take orders as well
as give reports: click to resize the claim, sneak-click to move it.

Read a row as a sentence. Houses: *"always want one, plus another per three people; each holds a family of four; priority 80; 20 builder-steps each."*

The **Houses** column is what makes population growth possible — see [POPULATION.md](POPULATION.md). Only buildings with a capacity can be lived in, and a family that fills its house stops growing until another is built. Housing supply is deliberately set to run ahead of population so towns never freeze; if you retune the house row, keep `capacity × wanted > population` at every size.

**Work** is measured in builder-steps: one resident with the `BUILDER` profession contributes 1 work per simulation step. Ten builders finish a 40-work town hall in four steps; one builder takes forty.

---

## Worked example: a town of ten

A settlement with 10 residents, starting from nothing, wants:

| Building | Calculation | Wanted |
|---|---|---|
| Town hall | flat 1 | 1 |
| House | 1, plus 10 ÷ 3 = 3 | 4 |
| Granary | flat 1, plus 10 ÷ 20 = 0 | 1 |
| Farm | 10 ÷ 5 | 2 |
| Market | flat 1, plus 10 ÷ 25 = 0 | 1 |
| Lumber camp | flat 1, plus 10 ÷ 30 = 0 | 1 |
| Watchtower | population 10 < minimum 12 | — not yet |
| Storehouse | flat 1, plus 10 ÷ 15 = 0 | 1 |
| Workshop | 10 ÷ 8 | 1 |

Twelve buildings total, constructed strictly in priority order:

```
town hall → house ×4 → granary → farm ×2 → market → lumber camp → storehouse → workshop → idle
```

Total cost: 40 + (4 × 20) + 25 + (2 × 30) + 30 + 30 + 30 + 35 = **330 builder-steps.**

With 3 builders that is 110 simulation steps — roughly **9 minutes** of real time at one step per 5 seconds.

Those 4 houses hold 16 people, which is why a town of 10 keeps growing rather than stalling. As births push the population to 12, the watchtower unlocks and a fifth house is wanted — so the town starts building again, which permits more growth, and so on.

**Growth drives building, and building permits growth.** That loop is the simulation.

---

## Tuning it

All the behaviour lives in one table in `BuildCatalogue`. No logic changes needed.

- **Want a building earlier in the sequence** → raise its priority.
- **Want more of it as towns grow** → lower its per-residents number. `1 per 2` produces far more than `1 per 8`.
- **Want exactly one, ever** → set base to 1 and per-residents to 0.
- **Want it gated behind town size** → raise its minimum population.
- **Want it to take longer** → raise its work cost.

To add a building type, add a row. The planner never needs to know it exists.

---

## What this deliberately does not do

Being explicit, because these gaps are choices rather than oversights:

- **No resources or cost.** Buildings need labour and nothing else — no wood, no stone, no money. A settlement can build a town hall out of thin air.
- **No prerequisites between buildings.** A workshop does not require a storehouse. The priority ordering produces a sensible sequence in practice, but nothing enforces it.
- **No terrain awareness.** Plots are placed by pure geometry. A building will happily land in a lake, inside a mountain, or floating over a ravine. Fixing this means asking the world for a surface height, which is a `WorldBridge` method that does not exist yet.
- **No demolition or repair.** Buildings are permanent once recorded. Breaking the blocks does not remove the building from the settlement's memory.
- **Nothing shrinks.** Population loss never causes a settlement to want fewer buildings — shortfall simply goes to zero and stays there.
- **One project at a time**, even for a town of 200 with 50 builders.

---

## Where this goes next

In rough order of value:

1. ~~**Population growth.**~~ Done — see [POPULATION.md](POPULATION.md). Births now drive building demand, and housing gates births.
2. **Move the catalogue into datapacks.** `BuildingType` is already nothing but numbers and a string id, specifically so this is a serialization job rather than a redesign. This is also what lets different cultures want different things.
3. ~~**Terrain-aware placement.**~~ Done — plots snap to the surface at planning time when the chunk is loaded, and placement snaps again regardless.
4. **Resource costs.** Once settlements have stores, buildings can consume them, and a settlement can be *unable* to build rather than merely uninterested.
5. **Prerequisites.** A blacksmith requiring a storehouse, expressed in the catalogue rather than implied by priority.

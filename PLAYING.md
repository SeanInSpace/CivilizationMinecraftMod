# Kingdoms — Player Guide

Autonomous settlements for Minecraft 26.2 (NeoForge). Found a town and it looks after itself: settlers build, take up trades, raise families, and defend the walls — whether or not you stick around to watch.

## Installing

1. Install [NeoForge](https://neoforged.net/) for Minecraft 26.2 (build 26.2.0.59 or later).
2. Drop `kingdoms-x.y.z.jar` into your `mods/` folder. Server installs work the same way; clients joining a server also need the mod.

## Founding a town

Craft a **Founding Charter**:

```
paper    emerald  paper
emerald  book     emerald
paper    emerald  paper
```

Use it on open ground and a settlement is founded on the spot — it names itself, and four settlers appear and get to work. The charter is consumed.

You cannot found a town too close to an existing one; the overlay message will tell you who is in the way.

## What happens next

You do not manage anything. The town runs itself, on these rules:

- **It digs before it builds.** A site is cut out of the ground first — builders
  swing shovels at soil and pickaxes at stone, and the ground gives way slowly.
  Only then do the walls go up. Buildings sit *in* the ground rather than on it, so
  you can walk straight in through the door instead of stepping up into it, and the
  cut runs a couple of blocks past the walls to leave a shelf you can walk round.
  A town on a hillside takes visibly longer to build than one on the flat — cutting
  a terrace out of a slope is most of the work.
- **It grows through stages, and the hall comes last.** A founding party is a
  camp, not a town. It stakes a camp post and a supply cache, then puts up a
  bunkhouse and a hearth to sleep and cook, then a farm and a granary to feed
  itself. Only once it is reliably fed does it fortify — and fortifying means a
  watch rather than a wall: a lumber camp, a storehouse, and a sentry standing
  over them. Cottages, a mill, a carpentry, a market and an inn make it a
  village. The **town hall is the last thing built, not the first** — no real
  settlement starts by building a government, and the wall waits for the town
  alongside it. Every step up is earned by conditions rather than a timer, so a
  party that is struggling stays where it is until it is not. Buildings go up
  whether you are there or not; if you are away, they appear when you return.
- **Early settlers turn their hand to anything.** Below village size there are no
  fixed trades: everyone is a **pioneer** who builds, farms and hauls as the camp
  needs, and forages wild food to keep the party fed before the first field is
  sown. Foraging is hand-to-mouth on purpose — it will keep a camp alive and it
  will never make it prosper. Trades settle as the stages call for them: the
  sentry and the woodcutter when the camp fortifies, the rest when the village
  opens its workshops.
- **It feeds itself — or fails to.** Food never teleports: harvest waits in the fields until a farmer walks out, shoulders it and carries it to the granary; market hands carry loads to the stall; one member of each family walks to market and back with the shopping. You can watch grain cross the village in their hands — and a carrier killed on the road takes the load with them. Every settler carries and eats their own food. Hunger is personal now: the underfed grow visibly weak (they stop working and slow down), and the starving die — permanently, with a line in the town's history. Watch the chain in the reports: granary, fields, market, pantries.
- **It secures its own materials.** Timber and stone come before market stalls and
  workshops, because a town that cannot supply itself has nothing to trade. Miners
  cut stone inside the mine's claim, deepening the workings rather than stripping
  the surface, and stop when the stores are full.
- **It works the woodland.** Once a town builds a **Lumber Camp**, its lumberjacks fell trees inside the camp's claim, carry the timber to the town's stores, and replant saplings so the wood grows back. They stop when the stores are full rather than clear-cutting, and they never touch a tree outside the claim.
- **It walls itself, once it is a town.** A wall is one of the last things a
  settlement builds rather than one of the first: a party that has only just
  learned to feed itself has better uses for its timber than several hundred
  posts, and nothing below town size builds one. At town stage it stakes a
  palisade around everything it has built and raises it post by post as its
  stores allow — a timber and three coin the post — pausing whenever a real
  building needs the crew. Gates are cut where the streets reach, so the ways out
  of town line up with the ways through it, and the sentry named long before,
  when the camp fortified, finally has a line to walk. The town keeps growing
  past that line afterward and that is normal — a walled town with suburbs is
  still a walled town. It only moves the wall once the suburbs have become the
  town: more buildings outside the line than inside it, the standing wall paid
  for to its last post, and no oftener than once in 500 steps. When it does, the
  old ring comes down as the new one goes up, so a town never ends up with a
  fence through its middle.
- **It wears roads between its doors.** Every building is joined to the town's
  road network, branching off whichever way already passes closest instead of
  running its own line to the middle — which is what gives a town streets and
  junctions rather than a star of tracks. Roads turn at right angles, leaving
  square ground between them to build against, and they leave by the door a
  building actually faces. The network is remembered, so it survives a restart,
  and a stretch that grows over is re-laid.
- **Its workshops earn their keep.** A working mill gets half again as much bread
  out of the same harvest. A carpentry cutting components ahead of need puts an
  extra pair of hands on every building site. An inn brings a trade caravan on a
  steady rhythm, swapping surplus bread for iron the town cannot mine.
- **It repairs its own mistakes.** Houses are placed by geometry, so on a slope a door can end up above anything a settler can climb. If somebody keeps failing to get home at dusk, the town notices and orders a flight of steps up to that door — jumping the build queue, because a family locked out matters more than the next workshop.
- **It keeps village hours.** By day, farmers head to the fields, builders to the construction site, traders to the storehouse and guards to the watchtower; at dusk everyone but the watch walks home. When danger is near, civilians run indoors and only guards hold the ground.
- **Families grow into family housing.** People pair into households and have
  children only when there is room in a home of their own. The bunkhouse shelters
  the whole founding party and breeds nobody — communal bunks are a stage, not a
  destination — so the first cottage is the moment a camp can start growing. No
  houses, no growth: the builders set the pace of everything.
- **Jobs staff themselves.** From village size the town gives one trade per step
  to whatever it is most short of, and children take up the same. Before that the
  pioneers cover everything, and a shortage can still pull one of them into a
  trade — a camp that runs out of timber names a woodcutter on the spot. The name
  tag tells you who does what.
- **Raids come.** Every so often, scaled to the town's size. If you are there, you'll see the attackers arrive, **civilians run for their homes**, and the guards charge. If you are not, the fight still happens — arithmetically, garrison against raiders — and people can die either way. Towns under 6 people are left alone.
- **Deaths are permanent.** A settler killed — by a raid, a creeper, or you — is gone: struck from their family and the roster.
- **Full towns found new ones.** A town that has filled up, built its hall, and
  can afford to outfit a party sends one out to plant a daughter settlement ~160
  blocks away under the same kingdom. The parent pays the kit out of its own
  stores — nobody is sent into the wilderness empty-handed — and the emigrants
  arrive as pioneers in a fresh camp, climbing the same ladder their parent
  climbed. Left alone long enough, one charter becomes a realm. See it with the
  charter's border sparkles.

The early game is the dangerous part. A young camp has neither wall nor watch: the
first sentry is named when the settlement fortifies, a watchtower comes later still
at 12 residents, and a wall of its own not until it is a town. Between arriving and
fortifying a party is genuinely fragile, and a raid that kills the wrong settler is
felt. Help it through those years — wall it, light it, stand guard yourself — and it
will outgrow the danger.

## Checking on a town

**Hold a Founding Charter** and every nearby settlement's border appears as a ring of green sparkles laid over the terrain — the town's claim, which grows as it builds outward.

**Sneak-use a Founding Charter** anywhere to get a report on the nearest settlement: population, families, defense, what is under construction, and its recent history —

```
=== Oakstead ===
Population 11 (beds for 16), 3 families
Defense 2, threat 0
Buildings: 6 (building kingdoms:farm 40%)
Recent history:
  Raid of 2 repelled by the garrison (defense 2), no losses
```

## Talking to settlers

| Action | Effect |
|---|---|
| **Right-click** | A word in passing |
| **Right-click holding food** | Hand it over — they will eat it when hunger bites |
| **Sneak right-click** | Read their pockets: what they carry, how hungry they are, what errand they are running |

Settlers carry **real food** and eat the actual item they hold, best first — so a loaf you give a starving settler is the loaf that saves them. A founding party sets out with bread in hand, which is what carries a new town through its first minutes before there is a larder to fetch from.

## Seeing what stands where

**Use a Town Map** and the nearest settlement opens as a plan: blank ground with
every building picked out in green, the road network drawn in earth beneath them,
north up, your own position marked. It is
deliberately not a Minecraft map — a map draws terrain, and terrain is exactly
what this strips away so the shape of the town is readable. The scale follows the
town's claim, so a hamlet and a city fill the same square.

Buildings only appear once their size is known, which happens when they are
actually built.

Craft or grab a **Surveyor's Lamp** from the Kingdoms creative tab and hold it.
Every building within about 48 blocks draws its own outline in sparks — the floor
and roof rectangles and the four corner posts. The outline is the building's whole
**plot**: the walls plus the ground cleared around them, which is the land the town
has taken for it. It places nothing and changes nothing; it is
purely a lens.

It pairs with the Founding Charter, which shows the town's *claim* as a ring of
green sparkles. One tells you where the town ends, the other where each building
begins.

## Supplying a build

**Right-click the Warehouse post empty-handed** and you get the bill for whatever
is being built: every block still to be laid, how many, and whether the town has
the stock to pay for it. Lines the town is short of are picked out in amber —
those are the ones you can do something about.

**Right-click it holding a stack** and it goes into the stores. Planks and logs
count as timber, stone and cobble as stone, iron ingots as iron, food as food.
Anything the town cannot use is refused rather than swallowed.

Builders draw their materials from those stores in person: a load is picked up at
the warehouse and carried to the site, and the stock leaves the ledger the moment
it is shouldered. A town with no warehouse falls back to a storehouse, then to the
hall.

## Trading with the storehouse

The **Storehouse post** is the town's counter, and it arrives when the settlement
fortifies — a long while before the wall that will drink everything in it.

| Action | Effect |
|---|---|
| **Right-click empty-handed** | The whole ledger: timber, stone, food, saplings |
| **Right-click holding logs, cobble or bread** | The town takes the donation, up to what its racks hold |
| **Right-click holding emeralds** | It sells you timber, eight logs to the coin |

It keeps a reserve of timber back whatever you offer, on the same rule the market
keeps seed corn: a town cannot be bought out of its own repairs. If you want a
settlement walled sooner, carrying logs to this door is the fastest way to do it —
the wall waits on town stage, and town stage waits on everything built before it.

## The town overview

**Right-click the Town Hall post** and the town's books open on screen: its name,
how many live there, and every resource it owns — food, timber, stone, iron,
tools, weapons, armor — each with its own icon and count.

`/civ overview` opens the same screen for the nearest settlement, if walking to
the hall is inconvenient.

## Reading a building

Every building has a **post** standing on its floor — a block naming what it is.
Right-click one and it tells you the town it belongs to, what that building does,
and the running totals: population, buildings, food, timber, stone. You never have
to guess what you are looking at.

## Directing the lumberjacks and miners

The lumber camp's **control post** — the crafting-bench-looking block inside the hut — is how you give woodland orders, the same way colony mods use hut blocks. You can also craft or place a Lumber Camp post yourself from the Tools tab.

| Action | Effect |
|---|---|
| **Right-click the post** | Grow the working radius one step, then wrap back to the smallest |
| **Sneak right-click** | Move the working area to where *you* are standing |

The **mine post** works exactly the same way, so learning one teaches the other —
click to resize the workings, sneak-click to point the miners at an outcrop. It
also reports how much stone the town is holding against what it can store.

Every click reports the resulting orders in chat, so you can point the camp at a particular wood and keep it off the trees you want left standing. The current claim also shows in the charter report and `/civ info`.

## Configuration

Per-world settings in `<world>/serverconfig/kingdoms-server.toml`:

| Setting | Default | What it does |
|---|---|---|
| `simulation.interval_ticks` | 100 | How often the world thinks (in game ticks) |
| `population.steps_per_birth` | 8 | Family growth speed |
| `population.max_per_settlement` | 48 | Births stop at this size — the growth ceiling |
| `view.observed_radius` | 96 | How close you must be to see villagers |
| `view.max_villagers_per_settlement` | 64 | Entity cap per town |
| `defense.raids_enabled` | true | Turn off for peaceful building |
| `defense.raid_interval_steps` | 50 | Time between raids |
| `debug.commands_enabled` | true | The `/civ` operator commands |

## Custom building styles

Kingdoms builds whatever you draw. Every building it places asks **Keystone** —
the blueprint mod shipped alongside it — for a structure first, and only falls
back to its built-in shapes when you have not supplied one.

The fastest way in is the **Blueprint Wand** (Tools tab):

1. Build something.
2. Click one corner with the wand, sneak-click the opposite corner.
3. Right-click the air, and name it `kingdoms:house`.

From then on your town builds *your* house — course by course, with the stairs
and doors facing the way you placed them. Name it `kingdoms:town_hall`,
`kingdoms:granary`, `kingdoms:watchtower` and so on for the rest. The
founding-era buildings take blueprints the same way: `kingdoms:camp_post`,
`kingdoms:cache`, `kingdoms:bunkhouse`, `kingdoms:hearth`, `kingdoms:cottage`,
`kingdoms:mill`, `kingdoms:carpentry`, `kingdoms:inn`.

There is **no size limit**. Vanilla's structure block stops at 48 blocks per
axis; the wand does not, so a keep or a curtain wall is as easy as a cottage.

Datapacks still work too: drop an `.nbt` at `data/kingdoms/structure/<name>.nbt`.
Files you scan yourself take precedence over those.

Cultures get their own architecture for free: a blueprint named
`kingdoms:norman/house` is used by Norman settlements, and anything a culture has
not drawn falls back to the common building.

See **[KEYSTONE.md](KEYSTONE.md)** for the full tool.

## For operators

`/civ` on its own lists everything. At permission level 2 it offers `found`,
`info`, `overview`, `populate`, `build`, `threat`, `step`, `raid`, `hunger` and
`audit`.

`info` is the full X-ray — stage, population, families, jobs, roads, food chain,
timber, defense and history. `overview` opens the town-hall screen from anywhere.
`step 50` fast-forwards the simulation. `raid` starts trouble on demand, `hunger 85`
makes a town stagger, and `audit` reports every fault the town inspector can see —
buildings nobody can get into, stores that have run dry, a build queue that has
stopped moving.

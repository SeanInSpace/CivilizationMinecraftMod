# Civilization — Player Guide

Autonomous settlements for Minecraft 26.2 (NeoForge). Found a town and it looks after itself: settlers build, take up trades, raise families, and defend the walls — whether or not you stick around to watch.

## Installing

1. Install [NeoForge](https://neoforged.net/) for Minecraft 26.2 (build 26.2.0.59 or later).
2. Drop `civilization-x.y.z.jar` into your `mods/` folder. Server installs work the same way; clients joining a server also need the mod.

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
Buildings: 6 (building civilization:farm 40%)
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

## The town map

Three ways to open it:

- **Right-click the Town Hall post.** The hall is where a town's books belong.
- **Use a Town Map** anywhere, and the nearest settlement opens.
- **Sneak-use the Wayfinder** while you are standing inside a town's borders. The
  needle's job is getting you there; this is what it has to say once you arrive.

It is deliberately not a Minecraft map — a map draws terrain, and terrain is
exactly what this strips away so the shape of the town is readable.

**The plan** fills the left of the window. Drag it to move, wheel to zoom, press
**R** to fit the whole claim back in the pane. North is up, there is a scale bar
in the bottom corner, and your own position and heading are marked in white.

| What you see | What it is |
|---|---|
| **Faint white lines** | The streets the plan laid out, walked or not |
| **Solid earth lines** | Roads the town has opened — where you can actually go |
| **Dashed earth lines** | Stretches it has drawn but not opened yet |
| **Gray ring** | The wall, closed or not |
| **Thin white circle** | The claim — the same border the Founding Charter shows |
| **Amber shapes** | Homes |
| **Green shapes** | Food: farms, granary, hearth, mill. Farms are hatched |
| **Brown shapes** | Timber and stone: lumber camp, mine, stores |
| **Blue shapes** | Workshops, the market, the inn |
| **Violet shape** | The town hall |
| **Red shapes** | Anything that helps hold a wall |
| **Hollow shapes** | Planned, not built. An amber ring means damaged |
| **Small dark-green dots** | The forester's stand — the trees it keeps planted |
| **Colored dots** | Settlers, one color per trade, moving as they move |

Each building is drawn at its real size and shape, with a short tick on the side
its door is on. A round hut is drawn round.

**Hover a building** for what it is, its level, whether it is whole or damaged,
whether a lane reaches its door, who lives or works there, and what it is
holding. **Hover a settler** for their name, trade, hunger, what errand they are
on, whose roof they sleep under and where they work. **Click either** and the
panel beside the map jumps to it.

**The panel** on the right has five tabs:

- **Town** — the whole reading: stage, how many are housed, the larder in all
  four places food sits, the grain that is not bread yet, the trees left standing
  and the rock left in the seam, the watch against the threat, the king, the
  treasury, the trades, the stores. A town in trouble says so at the top.
- **People** — every settler, sorted by name, trade or hunger (click the line
  above the list to change). Click one to center the map on them.
- **Built** — every building grouped by what it is for, with its condition. Click
  one to center the map on it.
- **Queue** — what is going up, how far along, and what each build is waiting on.
  A build stuck at nought per cent almost always has a reason written here.
- **History** — the last fifty things that happened to the town, newest first.

The map **refreshes every second** while it is open, and keeps where you dragged
it to and what you had picked. A town steps while you are looking at it.

`/civ map` opens it for the nearest settlement if walking to the hall is
inconvenient, and `/civ overview` still opens the plain resource ledger the hall
used to show. Both need cheats.

The map needs Civilization installed on the client.

## Seeing the plan on the ground

Craft or grab a **Surveyor's Lamp** from the Civilization creative tab and hold it.
The nearest town's plan is drawn over the world in steady white lines out to
about 128 blocks, following the lie of the land a block above the ground:

- **Solid white** — the roads the town has opened, which is where you can walk.
- **Dashed white** — stretches it has drawn but not opened yet.
- **Faint white** — the streets the plan laid out, whether or not anybody has
  walked them.
- **Gray** — the wall's ring, closed or not.
- **White rectangles** — each building's whole **plot**: the walls plus the
  ground cleared around them, which is the land the town has taken for it. A
  short tick on one side marks the door, so you can see what a building faces.
  A plot that is queued rather than standing is drawn fainter.

The lines are drawn through terrain on purpose — the view worth having is from a
rise, and that is exactly the view a hill would hide. Buildings only appear once
their size is known. It places nothing and changes nothing; it is purely a lens.

It pairs with the Founding Charter, which shows the town's *claim* as a ring of
green sparkles. One tells you where the town ends, the other where each building
begins.

The lamp needs Civilization installed on the client. A vanilla client connected to a
Civilization server still gets the charter's ring, which is made of ordinary
particles, but not the survey.

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

## The quest board

The **Quest Board** hangs inside the town hall. Right-click it and the town tells
you what it is short of, what is frightening it, and what it would like somebody
to go and look at — five notices at most, each with the reason written under it.

Nothing on that board is invented. A town asking for bread is a town whose
granary is empty; one asking for saplings has felled its cutting ground bare; one
asking you to walk out to the mine has cut the seam out and would like a
stranger's eyes on it. You can go and check every one of them.

| Kind of ask | What it wants | How it is finished |
|---|---|---|
| **Deliver** | Food, timber, stone, iron or saplings | Hand them in at the **storehouse** |
| **Slay** | Hostiles put down inside the town's bounds | Kill them yourself — the guards doing it does not count |
| **Clear** | Whatever has moved into a wrecked building | Kill them near the wreck |
| **Visit** | Somewhere the town wants looked at | Stand within six blocks of the mark |

Each row carries **Accept**, and then **Abandon** or **Claim** once it is yours.
Giving a job back loses whatever you had done toward it, so take one you mean to
finish. Somebody else's job shows as *Taken* and has no button. An offer nobody
takes comes down after a while; a job you have accepted gets a much longer leash,
and finished work waits for you however long you leave it.

The **Done** tab on the footer is what the town remembers being done for it — the
last ten jobs anybody finished there.

### Standing

Every job you finish raises your **standing** with that settlement, shown in the
board's header. It is that town's opinion and nobody else's — helping a village on
one coast buys you nothing on the other.

| Standing | They call you |
|---|---|
| 0 | Stranger |
| 20 | Trusted |
| 60 | Honored |
| 120 | Sworn |

A town that has a friend asks for more and pays better, which is the whole
progression: the first board you read at a strange settlement wants sixteen
loaves, and the board at the town you have been supplying all winter wants forty
and pays for them.

### What it pays

**Coin, in emeralds, out of the town's own treasury** — the same purse the market
stall trades from, so a coin earned at the board buys grain at the stall. A poor
town offers less, and says so on the row rather than promising money it has not
got. Slaying and clearing also throw in sixteen of whatever the town has more of
than it can store.

## The town overview
## The town's books

**Right-click the Town Hall post** and the town map opens — see [The town
map](#the-town-map) above, whose *Town* tab is the whole ledger and a good deal
more.

`/civ overview` still opens the older, plainer screen: the town's name, how many
live there, and every resource it owns — food, timber, stone, iron, tools,
weapons, armor — each with its own icon and count. It needs cheats.

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

Per-world settings in `<world>/serverconfig/civilization-server.toml`:

| Setting | Default | What it does |
|---|---|---|
| `simulation.interval_ticks` | 100 | How often the world thinks (in game ticks) |
| `population.steps_per_birth` | 8 | Family growth speed |
| `population.max_per_settlement` | 48 | Births stop at this size — the growth ceiling |
| `view.observed_radius` | 96 | How close you must be to see villagers |
| `view.max_villagers_per_settlement` | 64 | Entity cap per town |
| `defense.raids_enabled` | true | Turn off for peaceful building |
| `defense.raid_interval_steps` | 50 | Time between raids |
| `economy.unwatched_yield_percent.<resource>` | 0 | Share of the yield credited when nobody is near |
| `economy.watched_floor_percent.<resource>` | 0 | Share credited when you *are* near but nobody is working |
| `worldgen.enabled` | true | Whether the world has towns in it you did not found |
| `worldgen.reach` | 256 | How close you must come before one is raised |
| `worldgen.region` | 512 | Blocks across a region, which holds at most one town — **the density dial** |
| `worldgen.site_chance` | 35 | Percent of regions that hold a town at all |
| `worldgen.wayfinder_on_join` | true | Whether you are handed a wayfinder on your first join |
| `worldgen.arrangements.<shape>` | see below | How likely each town shape is, weighted against the others |
| `debug.commands_enabled` | true | The `/civ` operator commands |

### Finding the towns you did not found

A new world does not wait for you to go looking. The nine regions around the
world spawn are settled whatever the dice say, and they are raised as the level
loads rather than when somebody walks past — so the first time you look around,
there is a town about **two hundred blocks away**, and eight more within a few
minutes' walk.

Two things point you at them.

**The join message.** Every time you log in, chat lists the settlements within
1024 blocks — nearest first, at most five — with a distance and a compass point:

```
Settlements within 1024 blocks:
  Haldstead — 186 blocks NE
  Corbray — 604 blocks S
  a Norman crossroads — 911 blocks W (not raised yet)
  an orc Warhost stronghold — 980 blocks NE (not raised yet)
```

A town with a name has been raised and has people in it. One described by its
people and its shape is a place the seed says a town *will* be, which nobody has
been close enough to build yet — walk at it and it will be standing by the time
you arrive. `/civ sites` prints the same list on demand, with the operator
detail underneath it.

The line names the race when the neighbors are **not** human — "an orc Warhost
stronghold", "a goblin Mire warren" — and leaves it off when they are, because
"a human Norman crossroads" is a mouthful for the ordinary case. `/civ info`
names the race of every town outright.

**The wayfinder.** A compass whose needle points at a settlement instead of at
spawn. You are given one on your first join (turn that off with
`worldgen.wayfinder_on_join`), already aimed at the nearest town; right-click to
move it on to the next one out, and again to cycle back round. Sneak-right-click
once you are inside a town's borders and it opens that town's map instead — the
needle has nothing left to tell you there. It is also craftable:

```
—         iron     —
iron      redstone iron
—         emerald  —
```

### How dense you want your world

`worldgen.region` is how wide a square of world holds at most one town, and it
is the only dial that really changes what a world feels like. **512 is the
default and there is a reason it stays there.**

| `worldgen.region` | Towns within 4096 blocks | Closest two towns can be |
|---|---|---|
| 256 | ~300 | 160 blocks |
| 512 (default) | ~82 | 320 blocks |
| 1024 | ~20 | 640 blocks |

Set it to 256 and you get something close to Millénaire's density — roughly four
times as many towns for the same ground. Two things pay for it:

- **Towns grow into each other.** The margin that keeps neighbours apart is a
  fraction of the region, so halving the region halves the minimum separation:
  320 blocks becomes 160. A grown town here is 150 to 300 blocks across. Two
  centers 160 apart means two towns building on the same ground, with each
  other's claims, fields and roads arguing over it — and neither town knows the
  other exists until both are standing.
- **Every town costs tick budget.** A raised town is a simulation step, a
  manager pass, and — whenever you are near enough to see it — a crowd of
  embodied villagers. Four times the towns is four times that bill wherever they
  are clustered.

Above 512 the margin only grows, so 1024 and 2048 cost nothing but walking.

`worldgen.site_chance` is the other half of the density: the percent of regions
that hold a town at all, 35 by default. That number is not new — it is what the
mod has always done, now written down where a world can change it. Lowering it
thins the scatter without bringing any two towns closer together. **The nine
regions around spawn ignore it**, so even at 0 a world still starts beside a
town and the rest of the map is empty.

Changing either dial on an existing world moves every site that has not been
raised yet. Towns already standing stay exactly where they are — the ledger
remembers what was decided, not what the arithmetic would say today.

### Who lives out there, and what they build

Three **races**, and a race has several **cultures** — the race is the body, the
culture is the town.

| Race | Cultures | Health | Attack | Pace |
|---|---|---|---|---|
| **Humans** | Norman, highland, burgher, vale | 20 | — | normal |
| **Orcs** | one for now: the Warhost | 30 | +1 | a tenth slower |
| **Goblins** | one for now: the Mire folk | 14 | — | a tenth quicker |

Orcs take half again the punishment a man does, hit for one more, and are a shade
slower on their feet — never faster than their own walking pace, because nobody in
this mod has a second gear. Goblins are the other end of it.

Which shape a town is laid out in is a weighted draw, and the shape decides who
builds it. The human arrangements ship on:

| Shape | Weight | Who builds it |
|---|---|---|
| `green` | 100 | Norman |
| `crossroads` | 100 | burgher |
| `thorp` | 70 | highland |
| `ring_streets` | 70 | vale |
| `radial_concentric` | 60 | burgher |
| `crescents` | 40 | vale |
| `high_street` | 40 | burgher |
| `bastide` | 30 | Norman |

Everything else is at **0**, which means never: the four road-less lattices
(`ring`, `warren`, `stronghold`, `organic`) would give you a world of scattered
huts, and the orc and goblin shapes are off until there is an orc town worth
walking into. Set any of them above zero in the config and they come back.

### How much of a town's income is imaginary

Civilization runs at two fidelities. Stand near a lumber camp and a lumberjack fells
an actual tree; walk away and he becomes a number, and the clock credits the
timber on his behalf — otherwise every town you are not looking at would stop
dead. Those two tables decide how much of that credit a world actually grants,
one entry each for `wood`, `stone`, `iron` and `saplings`.

**Food is not on either table, and there is no setting that puts it back.** A
loaf is not a percentage — it is a crop that was sown, ripened and cut, and a
town eats whatever came off its own fields. So a town you have walked away from
does not get fed by arithmetic: it builds farms, puts farmers in them, harvests,
hauls the grain in and eats it, exactly as it would with you standing there. If
its fields fail, it goes hungry, and no number in this file will save it.

**`economy.unwatched_yield_percent`** is the share an unwatched building earns.
**The default is 0: nothing is conjured, anywhere.** An unwatched town still
runs — it builds, hauls, eats, spends what it holds, and its people go on
living — but it gains nothing it did not already have. Every log and every
stone has to come from a real hand or a real quarry. Raise it to 70 and a town
you walk away from keeps producing at a discount; raise it to 100 and absence
costs it nothing, which is what the mod did originally. Fractions are carried
between steps, so a camp earning one log a step really does bank seven logs in
ten at 70.

**`economy.watched_floor_percent`** is the share a building earns while you are
standing there and the real workers have produced nothing for a while — a camp
that has felled every tree in its claim, a mine on flat grass with no shaft sunk.
This used to be full rate, so that being looked at could never cost a town its
income. The default 0 means that in front of a
player, only real work counts; raise it if you would rather a stuck worker cost
the town nothing.

`/civ info` prints both in effect, so you can always tell what a world is
actually running.

Foraging does not go through these tables at all any more. A camp gathers only
what is actually growing around it — berries, mushrooms, wild crops, windfall
apples under oak — and gathering depletes the patch, which grows back slowly.
On a superflat world or in a desert there is nothing to forage and a camp must
farm or die. See **Living off the land** below.

### Living off the land

A new camp eats by foraging, and foraging only turns up what is actually
growing where you pitched it. Berry bushes, mushrooms, melons and pumpkins,
wild crops nobody planted, and apples under an oak canopy all count; grass
yields a little seed. Cactus does not count, dead bushes do not count, and
sand does not count — so a party dropped in a desert or on a bare superflat
world forages **nothing at all**, and has to get a field in the ground or die
trying.

Picking depletes the patch. Every meal gathered is booked against the ground it
came from and grows back at about one meal every four steps, which is roughly
what four people eat. A camp in a wood can sit still and just about live off
it; a camp trying to *grow* on wild food strips the wood and then goes hungry.
Berries are a reprieve, not an economy.

### Where a worldgen town's supplies come from

Every town the world generates arrives holding what its own buildings could
plausibly be holding, and nothing else:

- one harvest sitting in each standing field, and a granary a quarter full
- one building's worth of timber and stone per lumber camp and mine — enough
  to mend a wall, not to raise the next thing on its list
- no iron unless a smithy stands, and never any tools, weapons or armor: those
  are forged out of iron somebody mined

A town with no field, no granary and no camp of its own keeps a charter party's
kit, because with none of those it is a charter party.

## Custom building styles

Civilization builds whatever you draw. Every building it places asks **Keystone** —
the blueprint mod shipped alongside it — for a structure first, and only falls
back to its built-in shapes when you have not supplied one.

The fastest way in is the **Blueprint Wand** (Tools tab):

1. Build something.
2. Click one corner with the wand, sneak-click the opposite corner.
3. Right-click the air, and name it `civilization:house`.

From then on your town builds *your* house — course by course, with the stairs
and doors facing the way you placed them. Name it `civilization:town_hall`,
`civilization:granary`, `civilization:watchtower` and so on for the rest. The
founding-era buildings take blueprints the same way: `civilization:camp_post`,
`civilization:cache`, `civilization:bunkhouse`, `civilization:hearth`, `civilization:cottage`,
`civilization:mill`, `civilization:carpentry`, `civilization:inn`.

There is **no size limit**. Vanilla's structure block stops at 48 blocks per
axis; the wand does not, so a keep or a curtain wall is as easy as a cottage.

Datapacks still work too: drop an `.nbt` at `data/civilization/structure/<name>.nbt`.
Files you scan yourself take precedence over those.

Cultures get their own architecture for free: a blueprint named
`civilization:norman/house` is used by Norman settlements, and anything a culture has
not drawn falls back to the common building. The folder is the culture's own name
and not its race's, so a Norman town draws from `norman/` even though its id is
`civilization:human/norman`.

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

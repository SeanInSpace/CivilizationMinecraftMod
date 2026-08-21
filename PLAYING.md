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
- **It builds what it lacks** — town hall first, then houses, farms, a storehouse, workshops, watchtowers as it grows. Buildings go up whether you are there or not; if you are away, they appear when you return.
- **It feeds itself — or fails to.** Food never teleports: harvest waits in the fields until a farmer walks out, shoulders it and carries it to the granary; market hands carry loads to the stall; one member of each family walks to market and back with the shopping. You can watch grain cross the village in their hands — and a carrier killed on the road takes the load with them. Every settler carries and eats their own food. Hunger is personal now: the underfed grow visibly weak (they stop working and slow down), and the starving die — permanently, with a line in the town's history. Watch the chain in the reports: granary, fields, market, pantries.
- **It secures its own materials.** Timber and stone come before market stalls and
  workshops, because a town that cannot supply itself has nothing to trade. Miners
  cut stone inside the mine's claim, deepening the workings rather than stripping
  the surface, and stop when the stores are full.
- **It works the woodland.** Once a town builds a **Lumber Camp**, its lumberjacks fell trees inside the camp's claim, carry the timber to the town's stores, and replant saplings so the wood grows back. They stop when the stores are full rather than clear-cutting, and they never touch a tree outside the claim.
- **It repairs its own mistakes.** Houses are placed by geometry, so on a slope a door can end up above anything a settler can climb. If somebody keeps failing to get home at dusk, the town notices and orders a flight of steps up to that door — jumping the build queue, because a family locked out matters more than the next workshop.
- **It keeps village hours.** By day, farmers head to the fields, builders to the construction site, traders to the storehouse and guards to the watchtower; at dusk everyone but the watch walks home. When danger is near, civilians run indoors and only guards hold the ground.
- **Families grow into the housing.** People pair into households, claim houses, and have children only when there is room. No houses, no growth — the builders set the pace of everything.
- **Jobs staff themselves.** Children take up whatever the town is short of; idlers and surplus workers retrain. The name tag tells you who does what.
- **Raids come.** Every so often, scaled to the town's size. If you are there, you'll see the attackers arrive, **civilians run for their homes**, and the guards charge. If you are not, the fight still happens — arithmetically, garrison against raiders — and people can die either way. Towns under 6 people are left alone.
- **Deaths are permanent.** A settler killed — by a raid, a creeper, or you — is gone: struck from their family and the roster.
- **Full towns found new ones.** At the population ceiling, a party of young families departs and plants a daughter settlement ~160 blocks away under the same kingdom. Left alone long enough, one charter becomes a realm. See it with the charter's border sparkles.

The early game is the dangerous part. A young town has no guards (the first one takes up the post at 8 residents) and no watchtower (built at 12). Help it through its vulnerable years — wall it, light it, stand guard yourself — and it will eventually outgrow the danger.

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

Craft or grab a **Surveyor's Lamp** from the Kingdoms creative tab and hold it.
Every building within about 48 blocks draws its own outline in sparks — the floor
and roof rectangles and the four corner posts, so you can read a building's real
footprint without counting blocks. It places nothing and changes nothing; it is
purely a lens.

It pairs with the Founding Charter, which shows the town's *claim* as a ring of
green sparkles. One tells you where the town ends, the other where each building
begins.

## The town overview

**Right-click the Town Hall post** and the town's books open on screen: its name,
how many live there, and every resource it owns — food, timber, stone, iron,
tools, weapons, armour — each with its own icon and count.

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
`kingdoms:granary`, `kingdoms:watchtower` and so on for the rest.

There is **no size limit**. Vanilla's structure block stops at 48 blocks per
axis; the wand does not, so a keep or a curtain wall is as easy as a cottage.

Datapacks still work too: drop an `.nbt` at `data/kingdoms/structure/<name>.nbt`.
Files you scan yourself take precedence over those.

Cultures get their own architecture for free: a blueprint named
`kingdoms:norman/house` is used by Norman settlements, and anything a culture has
not drawn falls back to the common building.

See **[KEYSTONE.md](KEYSTONE.md)** for the full tool.

## For operators

`/civ` on its own lists everything. At permission level 2 it offers `found`, `info`, `populate`, `build`, `threat`, `step`, `raid` and `hunger`. `info` is the full X-ray — population, families, jobs, food chain, timber, defense and history; `step 50` fast-forwards; `raid` starts trouble on demand; `hunger 85` makes a town stagger.

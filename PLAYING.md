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

- **It builds what it lacks** — town hall first, then houses, farms, a storehouse, workshops, watchtowers as it grows. Buildings go up whether you are there or not; if you are away, they appear when you return.
- **It feeds itself — or fails to.** Food never teleports: harvest waits in the fields until a farmer walks out, shoulders it and carries it to the granary; market hands carry loads to the stall; one member of each family walks to market and back with the shopping. You can watch grain cross the village in their hands — and a carrier killed on the road takes the load with them. Every settler carries and eats their own food. Hunger is personal now: the underfed grow visibly weak (they stop working and slow down), and the starving die — permanently, with a line in the town's history. Watch the chain in the reports: granary, fields, market, pantries.
- **It works the woodland.** Once a town builds a **Lumber Camp**, its lumberjacks fell trees inside the camp's claim, carry the timber to the town's stores, and replant saplings so the wood grows back. They stop when the stores are full rather than clear-cutting, and they never touch a tree outside the claim.
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

## Directing the lumberjacks

The lumber camp's **control post** — the crafting-bench-looking block inside the hut — is how you give woodland orders, the same way colony mods use hut blocks. You can also craft or place a Lumber Camp post yourself from the Tools tab.

| Action | Effect |
|---|---|
| **Right-click the post** | Grow the working radius one step (8 → 64, then back to 8) |
| **Sneak right-click** | Move the working area to where *you* are standing |

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

Every building the mod places checks for a structure template first: put an `.nbt` file at `data/kingdoms/structure/<name>.nbt` in a datapack (e.g. `house.nbt`, `town_hall.nbt` — authored in-game with structure blocks) and towns will build *your* architecture instead of the built-in one. No code, no config.

## For operators

`/civ` (permission level 2) offers `found`, `info`, `populate`, `build`, `threat`, `step`, and `raid` for inspecting and prodding the simulation. `info` is the full X-ray; `step 50` fast-forwards; `raid` starts trouble on demand.

# Goblins

What a goblin camp is, and what it does.

The mod has had goblins for as long as it has had races: a body worth fourteen
health, quick and easy to kill, one culture (`civilization:goblin/mire`), a
script-drawn skin. What it did not have was anywhere for them to live that was
not a village with green beds in it — they farmed, they kept a market, they
traded, and the only thing goblin about a goblin settlement was the color of the
wool.

A goblin camp is a different *kind* of settlement, not a village with a different
palette. It is a **hostile scavenger camp**: small, transient, armed, living off
what it can pick and what it can take.

One goblin culture ships. That is a gap in the table rather than a claim about
goblins; a second is another entry in `Culture`, and everything below reads off
the race rather than the culture id, so a second mire people would be a camp by
being goblin.

---

## Where they are

Only in **swamp, mangrove swamp, dark forest, old-growth pine taiga and
old-growth spruce taiga** — bog and deep wood, which is terrain a player crosses
rather than settles in.

The site chooser (`SettlementSites`) is arithmetic on a world seed with no level
behind it, which is what lets a world's towns be enumerated without loading a
chunk — and a biome needs a level. So the draw is blind and the gate is applied
where the ground is actually generated, in `WorldgenSettlements`. A region that
drew a camp on the wrong ground gets **an ordinary town instead**, re-drawn on the
same spot with the hostile arrangements taken out of the weights; rejecting
outright would have made the goblin weight a hole in the map.

The config weight `worldgen.arrangements.goblin_camp` ships at **150** against a
friendly total of 610, so about **one region in five draws a camp** and the biome
gate thins that to however much of a world is bog and dark wood. On an ordinary
overworld that is a handful of camps within a few thousand blocks rather than one
on every horizon. Set it to 0 to turn them off entirely.

**The spawn region never draws one.** A player's first town must not shoot at
them, so the nine anchored spawn sites exclude every arrangement only hostiles
build — see `SettlementSites.siteIn`. Nor does the wayfinder handed out on login
point at a camp if there is anything friendlier in earshot; clicking it still
reaches the camp, and the line it prints says `hostile`.

## What a camp looks like

`goblin_camp` — `GoblinCampLayout`, the head of the mire goblins' layout list.
The warren (`warren`) is still behind it, and a world that wants settled goblins
who farm and trade can turn that on instead; everything hostile is read off the
race, not the shape.

An irregular huddle of small plots round an open middle. Candidates come off a
golden-angle spiral and are accepted or refused one at a time against everything
already placed, so nothing lines up into a row or a ring and the edge is ragged.
Plot zero is the trampled middle, and the camp's first building stands on it.

Measured, out from the middle on the **wider axis** — which is the metric the
overlap check uses and therefore the one a walk costs:

| goblins | reach |
|---|---|
| 6 | 13 blocks |
| 8 | 14 |
| 12 | 25 |
| 20 (a big camp) | 28 |

So a camp of the size a world seeds — eight — is genuinely inside a twenty-block
walk of its own fire. Twelve is not, and **cannot** be: twelve plots at an
eleven-block separation do not fit in a twenty-block circle under any
arrangement, because about ten is the ceiling. For comparison, the warren put its
first *outlying knot* at fifty-two, before any of the huts in it.

**No streets at all.** That is what a camp is, the same statement the warren
makes, so a camp reports no frontage and is outside the frontage invariants in
`LayoutTest` — those ask only of the arrangements that draw roads
(`Layouts.isStreetsFirst`). No relaxation was needed. The road layer wears a
track between the stockade's gate and the loot pile and lays nothing else.

Two camps are never the same camp twice: the spiral is turned by a hash of the
camp's own center.

## What is in it

Four buildings, all of them the goblins' and nobody else's — `Homes` keeps a
hovel out of a Norman village as firmly as it keeps a cottage out of a camp.

- **Hovel** (`5×5`, 3 beds). Packed mud over a stick frame with the lowest wall in
  the mod: two courses, so a goblin ducks going in. What a cottage is to a
  village.
- **Tent** (`5×5`, 2 beds). Hide stretched over four poles, guy ropes at the
  sides, and no walls at all — the first building here with none. What a camp
  pitches for whoever has not got into a hovel.
- **Loot pile** (`9×7`, four courses). The camp's *whole* store, and pointedly not
  a building: a ring of sharpened stakes round a heap of barrels and chests, with
  a cage either side. It is a `STORE` by role, so the store machinery finds it the
  way it finds a warehouse, and it is capped at one per camp — granary, storehouse
  and warehouse are all this same heap for these people.
- **Chieftain's hut** (`9×9`, 4 beds). The largest roof in a camp by four blocks,
  with skulls on stakes at the corners. It is a `HALL` by role, and until it
  stands nobody is in charge.

The **stockade** is the goblins' wall style, through `PerimeterLayer`: spruce
stakes rather than oak fence, a **pointed top on every stake** rather than a lamp
on every eighth, and **unlit** — a camp that lit its own perimeter would be a camp
telling you where it is. The recognizing side of that file stays culture-blind on
purpose (every people's post counts as a post), because a block the wall lays and
does not recognize is how it grew hundred-block towers once already.

The fire pit in the middle is the `hearth` every stage program raises.

A seeded camp arrives at stage `TOWN` — not flattery: nothing in this mod stakes a
palisade before TOWN, and the TOWN program is what raises the hall. A village
found in the world is still growing; **a camp found in the world is finished.** It
is the whole of what a band of goblins ever builds, and what it does from there is
raid.

## Who is in charge

- **A chieftain**, crowned by `KingPlanner` once the chieftain hut stands — the
  same rule that crowns an orc king, in a smaller roof. Worth
  `KING_GUARD_BONUS` (two guards) to the camp's defense and half again his
  people's health, and he does no work at all.
- **A shaman**, which is new and is the camp's own. A second title, no building
  needed, named from the longest-standing goblin who is free once the camp has
  four in it. He is worth **one extra meal a step** at the foraging.

**The scatter rule.** Lose *both* and the camp walks away: two goblins slip out
every step until the place is empty, and what is left standing — hovels, tents,
stockade, whatever is still in the loot pile — is a camp a player can walk into
and loot. While *either* of them lives it holds together, which is most of what
makes the shaman worth killing and most of what makes him worth keeping alive.

The rule reads three things the save already holds and adds no field of its own:
it is a camp, nobody holds either title, and `mourningUntil` is nonzero — which
`KingPlanner` stamps the step a leader falls and nothing else does. That last
condition is what stops a camp scattering on its first step, before it has got
round to naming anybody.

## How they eat

**They never farm.** Not at any size, not at any stage. `Homes` refuses the farm
outright, and with it the mill, the carpentry, the market, the inn, the animal
farm and both libraries — a mill grinds a harvest nobody cut and a library in a
swamp is a joke. They keep the lumber camp, the mine, the smith, the watchtower
and the workshop, because stripping a wood, digging a hole and beating a shiv out
over a fire are exactly what scavengers do.

What they have instead is **foraging, forever**. The wild-food path every young
settlement uses was gated on "below VILLAGE" — right for a founding party on its
way to a field, and a camp that starved the step it graduated. A camp forages at
every stage of its life, and its ceiling is **11 loaves per mouth** against a
village's 5: the low ceiling exists to push a settlement towards a farm, and a
camp has no farm to be pushed towards, so it held the larder permanently under
the fed streak and locked the camp in HOMESTEAD forever with no sentry and no
palisade.

It is not a free lunch. Foraging is still capped by what is actually growing
within forty-eight blocks, and the patch depletes as it is picked. A camp in a
mire eats; a camp on bare rock starves whatever the ceiling says.

A camp fields **foragers** where a village fields farmers — a real profession
(`Profession.FORAGER`), one pair of hands plus another per four mouths, with no
building to work at. `JobPlanner.needsFor` swaps that one row for a people who
never farm and leaves the rest of the table alone.

## How they raid

Every raid in this mod came out of nowhere and put nothing anywhere: a hash of a
settlement id against a step number, a strength, and no other end to it. A camp is
the first raider with a name, a home and a heap to put the takings in.

On its own clock — about one raid in **300 steps**, with the offset hashed from
the camp's own id so two camps near one village do not arrive together — a camp
sends **half the goblins it can spare**, rounded up, never fewer than three and
never so many that nobody is left. The shaman stays; the chieftain goes, and the
party is worth his morale for it. The target is the **nearest non-goblin
settlement within 512 blocks** with anybody living in it. Camps do not raid camps.

A party is worth **one point a body** plus the chieftain's bonus, capped at
`MAX_RAID_STRENGTH` — deliberately the same currency the woods' own raids are
counted in, so a camp's raid is not secretly twice as hard.

**The town decides what it costs it.** `RaidPlanner.raidBy` is where all of it
resolves, and the camp knows none of the rules: the grace a new town gets, the
early cap that keeps a young town's raid a probe, the margin under which nobody
dies, the order casualties are picked in, and the switch to real bodies when
somebody is watching are all rules about *being raided*.

- **Repelled** — nothing moves. The town keeps its stores and the camp keeps its
  event log.
- **Broken through** — **a quarter** of the town's **food, iron and coin** moves to
  the loot pile, and both event logs say so. Timber and stone are not in it; a
  goblin party is not carrying logs home across five hundred blocks. A quarter
  rather than everything because a camp that emptied a granary would end the town,
  and a dead neighbor is a camp with nothing to raid.
- **Either way** the camp pays for the walk: **one goblin per two points of defense
  the town fielded**, capped at half the party, so a raid on a walled town hurts
  and a single bad night does not end the camp.

**Watched raids fight but do not carry.** When a player is near the target the
party is spawned as real goblin bodies, armed out of the camp's own armory, and
entity combat decides it — the guards engage them because the danger table scores
a goblin, and they fight back. What a watched party does **not** do is bring
anything home. That is a real gap rather than an oversight: the unwatched
arithmetic is complete, and the honest alternative would have been to move the
stores when the party spawns, which pays a camp for a raid it might lose.

## Fighting them

**A goblin attacks a player inside its camp's claim** (or sixteen blocks,
whichever is further), like a pillager outpost and unlike a village. Walk out and
they stop — a camp defends a swamp, it does not stalk you across a continent.
Creative and spectator players are ignored, the weak stay put, and the shaman
never leaves the fire.

Everybody in a camp is **armed all the time**, the way everybody in a warhost is
and for the opposite reason: an orc because every orc is a warrior, a goblin
because there is nobody in a camp who will not have to run. Four weapons, each
forged twice, and every one of them **one-handed** — a shiv, a club, a spear and a
sling. That is not a shortcut. A goblin is fourteen health and gets no damage
bonus from his body at all, so his answer to being hit is not being where the blow
lands; and because nothing in the camp is a two-hander, every goblin in the line
has a hand free for a sling and the camp can shoot a creeper. The shiv is the
fastest thing in either armory and still does less damage over time than an orc's
falchion, which is the trade the two peoples are.

On the danger table (`Menace`) a goblin is worth **2** — a skeleton's rung, still
one guard's job and not a comfortable one — and a chieftain **3**, a guard's whole
attention. So human and orc guards engage them on sight, and three of them
arriving at a village puts it indoors. Nobody fears his own kind: the sighting
sweep skips a settler of the same race as the witness, or a camp would have
reported its own eight residents as eight hostiles and sat behind its own alarm
forever.

## Reports

`/civ info` names a goblin settlement `a goblin camp — HOSTILE` on the same line
as its name, and prints its chieftain, its shaman, and `SCATTERING` when it is
coming apart. `/civ list` marks the row `— HOSTILE`. The login greeting and the
wayfinder both read:

```
  a goblin camp — 340 blocks NE — hostile
```

## The goblin egg

`goblin_spawn_egg` exists now. It was missing on the honest ground that no
ordinary world held a goblin settlement to put one in; ordinary worlds hold camps,
so the egg has somewhere to go and does exactly what the other two do — it
recruits into the **nearest camp** rather than spawning a loose goblin, because a
settler body with no person behind it is culled the instant it joins the world.
That it recruits into a hostile settlement is the point of it rather than a hole:
you have made that camp one goblin stronger.

## Placeholders

The stake's point is a pointed dripstone and the chieftain's trophies are skeleton
skulls, in the same sense the orc palette's bone and hide are placeholders — see
`ORCS.md`. They are real blocks on the line rather than textures, which is why the
wall's `isOurs` knows about the point.

# Orcs

What an orc settlement is, now that it is not two rectangles.

The orcs had a lattice and a gridiron, and both of them said the same thing:
an orc settlement is a garrison. This is what they build when they are *living*
somewhere — a camp drawn round a chief, with round houses on it.

One orc culture ships, `civilization:orc/warhost`. That is a gap in the table rather
than a claim about orcs; a second is another entry in `Culture`.

---

## The war camp — `orc_ring`

`OrcRingLayout`, and the head of the warhost's layout list. The two rectangles
(`stronghold`, `stronghold_streets`) are still there behind it.

From the air, at sixty-four huts — `@` the great hut's plot, `#` a hut,
`.` carriageway, one character to a six-block square:

```
  ...         #  #     ..     ..
 ..        #        #  .       ...
..             ..     ..         ..
.        #  .... ......           .
          ...        ...  #
      #  ..  #  #  # ....
        ..           .  ..  #
     # ..           ..   ..
       .  #      #  . #   ..  #
      .      #     ..      ..
...  ..         ....        ..
  ....  #  #  ...  .. #   #  .. #
   .....     ..     ..        .
   .   ...  ..   #   .      # ..
  .. #   .... #    # .  #      .
  .    #   ..        ..        .
  .        .          .        .
  .      # .  #  @  # . #   #  . #
  .. #     ..         .   #    .
   .      # .  #   #  ...   # ..
   .        ..   #    . ...   .  #
 # .. #      ..      ..   ... .
    .      #  ........   #   ...
     .  #     .  .     #    .. ...
   # ..      .. #          ..     .
      ..   # .      #     ..  #
    #  ..    . #         ..
        ..  .           ..  #
      #  .. .      #   ..
           ..   #    ... #        .
.        # ....     ..            .
..        ..  ......             ..
 ..       .                     ..
   ..     .         #          ..
    ..   ..                   ..
```

Four things make it a camp rather than the vale folk's ring village:

- **The yard is whole.** No lane crosses it. The gates start *at* the first ring
  road and run outward, so the ground inside the ring is one unbroken round of
  trampled earth. A green with four lanes through it is a Rundling; this is a
  muster ground.
- **The middle is reserved.** Plot zero is the center and fronts no street.
- **The huts face in.** Frontage is offered on both faces of every ring and taken
  nearest-first, so the inner faces fill before the outer ones without anybody
  having to say so.
- **It is tight.** The first ring runs at 34 blocks against the village's 40, and
  the whole camp is the densest arrangement in the game.

Measured, on a flat plan at the origin:

| huts | reach | frontage | streets |
|-----:|------:|---------:|--------:|
|   24 |    52 |      95% |       8 |
|   64 |    98 |      98% |       8 |
|  140 |   145 |      99% |       8 |

For comparison, the green village reaches 243 by 85 at a hundred and forty.

It ships weighted 100 in `worldgen.arrangements`, alongside the two best human
shapes. It is the only non-human arrangement that is on by default; the warren
and the two orc rectangles stay at zero.

### The setback rule this cost

A plot is refused when its square comes within a half-carriageway of a road, and
a square reaches √2 of its half-width **at the corners**. On a straight street
that corner points along the road and costs nothing. On a ring it points *at* the
road, radially, on every diagonal — so the depth a plot needs from a ring road is
`ROAD_HALF + onACurve(DEFAULT_SPAN / 2 + CURB)`, which comes to thirteen exactly,
and the ordinary setback of thirteen therefore clears by nothing at all.

At the ordinary setback the yard rim held **four huts of eight** — the four on the
axes — and the plan reported full frontage the whole time, because frontage counts
plots that were *taken* and says nothing about offers that were refused. The camp
made up the difference by opening another ring and reached 117 blocks to hold
sixty-four huts instead of 98.

One block deeper, and `OrcRingLayoutTest` asserts the rim count rather than only
the frontage.

---

## The huts

Two new buildings, and they are the same building at two sizes. Both are
**octagons** — the first round footprints in the mod. `BuildingSizes.Size` grew a
`chamfer`: a count of blocks cut back from each of the four corners, which is one
test on `|dx| + |dz|` and gives 3-5-7-7-7-5-3 at seven across.

| | span | chamfer | wall | beds | who lives there |
|---|---:|---:|---:|---:|---|
| `hut` | 7×7 | 2 | 3 | 3 | the warband |
| `great_hut` | 13×13 | 3 | 5 | 6 | the chief, and his hearth-guard |

The walls come from `BlueprintPlacer.hall`, which was already a general shape
walker — it lays wall wherever a covered cell has an uncovered one beside it, so
an octagon gets eight faces and eight corner posts without the placer learning
what an octagon is.

**The roof is a cone and not a pyramid.** `Parts.hipRoof` takes its rise from how
far each cell is from open air in any direction, so over a square it is a pyramid
and over an octagon it is a cone: the chamfered corners *are* open air, so the
courses step in on the diagonals as well as on the faces, and it comes to a single
block rather than to a ridge. A hut rises four courses in rings of 3-5-7-7-7-5-3,
5-7-7-7-5, 3-5-5-5-3 and one; `RoundHouseTest` asserts that every course covers
strictly fewer columns than the one below it and that the top course is one block.
Nothing in the roof code changed — it was measuring the shape all along.

### Who builds what

`Homes` is one table of substitutions and everything else is derived from it. For
the warhost: cottage and dwelling become the hut, longhouse and croft become the
great hut. From that one table:

- a war camp never wants a cottage, because it has a replacement for one;
- a human town never wants a hut, because a hut is somebody else's replacement.

`StagePlanner` maps its program through it — the VILLAGE program still says "two
cottages" and a camp raises two huts — and `BuildPlanner.chooseNext` filters the
catalog by it. A grown camp of forty-nine measured 17 huts, 1 great hut, and no
cottages, dwellings, longhouses or crofts.

The great hut is capped at one by its own catalog row (`base 1`, nothing per
resident), gated at fourteen residents, and ranked just under the hut — shelter
for the warband before a throne for the chief.

### What is placeholder

- **The palette.** The cone is spruce stairs, the trim is bone blocks, the wall
  band is brown wool standing in for hide. These are four constants in
  `BlueprintPlacer` rather than columns on `HouseStyle`, because a hut is a
  building only one people raises and five blank entries to carry one real one is
  not a table. When a second people builds something round, they move.
- **The post-block textures.** Both posts use vanilla textures through
  `cube_bottom_top`, exactly as every other building post does. No new art.
- **No door.** Hut doorways are gaps, like every other building in the mod.
- **`Footprint` does not know about the chamfer.** Anything asking "is this column
  inside a building" — whether somebody is indoors, what the town map fills in —
  treats a hut as its 7×7 box. That is conservative rather than wrong (it claims
  four corner cells that are actually open ground) and it is deliberate: the
  chamfer would otherwise be a save-format change on every building in every
  world.

---

## The king

`KingPlanner`, and `Profession.KING`.

1. **Only orcs crown one.** The one place in the mod that reads `Race` to decide
   behavior rather than to set a number on a body. A king is a political fact
   about a people, not a stat.
2. **No seat, no king.** Nobody is crowned until the great hut stands.
3. **The oldest guard, else the oldest resident.** Nobody in this simulation has
   an age, so "oldest" means longest-resident — the order people joined the town,
   which is the thing the rule was actually reaching for and is deterministic
   across a reload. When people get ages this becomes a comparison and nothing
   else about the rule changes.
4. **Exactly one**, enforced every step rather than assumed once.
5. **He never works.** Not by a special case in every planner — by being absent
   from `JobPlanner.DEFAULT_NEEDS`, so nothing ever wants a king and nothing ever
   retrains one away. The two lanes that reach *past* the table skip him by name:
   the starving town's donor scan and the courier.
6. **While he lives the warband fights harder.** `+2` guard strength, counted in
   `Garrison` (what a town recruits by) *and* in `RaidPlanner.defensePower` (what
   it fights by). A watchtower is deliberately in only the second — it can be
   knocked down before the raid it was built for — and a king cannot be: either he
   is alive when the raid arrives or the bonus is zero.
7. **When he falls the camp mourns** for 100 steps, logs it, and then the same
   rule picks the next one.

On the body: a gold helmet in the head slot (no drop — it is a title, not loot)
and `Race.maxHealth() × 1.5`, so an orc king stands at 45 against his warband's
30. Applied by `PersonEntity.wearCrown`, run every manager pass and idempotent by
construction, because a title changes and a race does not — `applyRace` runs once
at embodiment and cannot answer for a guard crowned later.

The alarm's rally point is the great hut: anybody with no roof of their own runs
there when the bell goes, instead of to a point on the map.

`/civ info` names him, or says whether the camp has no great hut yet or is still
mourning.

### The realm's name

A shire is named after its first town; a warband is named after its chief's line.
`Kingdom.nameFor` gives *the Blacktusk Warband*, drawn from the culture's own
family names and from where the town stands, so the same seed gives the same
warband. It is deliberately **not** the living king's name: a kingdom's name is
final and is fixed at founding, long before anybody is crowned, so a realm named
after its current king would have to be renamed on every succession.

---

## Known gaps

- **The great hut does not actually land on the middle.** The *plan* reserves plot
  zero for it and the test asserts that. What stands there is the siting code's
  business, and it gives the same answer for every arrangement in the game: a town
  builds shelter, then food, then safety, so the camp post takes plot zero on the
  first step and the great hut is raised much later, wherever the plot cursor has
  reached — 115 blocks out in a measured camp of forty-nine. `Culture.BURGHER`'s
  javadoc records exactly the same thing about the radial town's green. Fixing it
  means letting a building reserve a plot, which is a change to siting rather than
  to a layout.
- **A war camp still raises a town hall** at the TOWN stage, because that is the
  stage's headline build and the stage program is written once for everybody. A
  chief's hall and a town hall in the same camp is one civic building too many.
- **Mourning is not saved.** The crown itself is, because it is a profession on a
  settler; the hundred steps of grief are not, so a server restart during a
  mourning crowns the next king immediately. That is a few minutes against a save
  format change on every settlement in every world.

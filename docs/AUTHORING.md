# Authoring buildings

How to build a house in creative and have towns raise it in place of the one the
code draws.

Every building in the game has a drawn version — a method in the placer that
lays blocks by arithmetic. An *authored* building is a file that stands in for
one of those. Drop the file in, and the next cottage a town raises is yours.

---

## The short version

```
/civ blueprint region 100 64 -40 106 68 -34     # mark the box; hold the lamp to see it
... build inside it ...
/civ blueprint scan norman/cottage              # take it, facing the way the door faces
/civ blueprint check norman/cottage             # hold it against the tables
/civ blueprint place norman/cottage             # put one down and walk round it
```

The file lands in `<world>/civilization/blueprints/norman/cottage.nbt`. Any norman
town in that world now builds your cottage.

---

## Where files live, and which one wins

Four places, consulted in this order. The first hit is used and the rest are
never asked.

| Order | Where | Path | Good for |
|---|---|---|---|
| 1 | This world's folder | `<world>/civilization/blueprints/<style>/<name>.nbt` | Your own work, in the world it belongs to. Travels with the save. |
| 2 | The shared folder | `<gamedir>/keystone/blueprints/civilization/<style>/<name>.nbt` | Work in progress you want in every world. |
| 3 | Imported Structurize | the same shared folder, `<name>.blueprint` | MineColonies schematic packs. |
| 4 | The mod jar and datapacks | `data/civilization/blueprints/<style>/<name>.nbt` | Shipping a culture's buildings in a pack. |

A world file always beats a shipped one of the same name, so a pack's cottage is
replaced by yours without deleting anything.

Below all four is the drawn building. A name nothing has a file for is drawn by
the code, which is why a culture only has to author the buildings it wants to
differ on.

### Styles

A town asks for its culture's version first and the plain version second. A
norman town raising a cottage asks for:

1. `civilization:norman/cottage`
2. `civilization:cottage`

So `norman/cottage` changes norman cottages and nobody else's, and `cottage`
changes everybody's. The style folder is the last segment of the culture id —
`norman`, `warhost`, `mire`, `vale`, `burgher`, `highland`.

---

## Building one

Build it in creative, anywhere. Rules that matter:

- **Both spans odd.** A building is placed about its middle cell and turned
  about it. An even-spanned building has no middle, so every quarter turn moves
  it half a block.
- **No bigger than the drawn one.** The town plan reserved ground for the
  declared size before it knew your file existed. `/civ blueprint check` prints
  the declared size when it refuses one.
- **The floor is course nought.** Whatever you put at the bottom of the region
  is what lands at ground level. Furniture stands one course above it.
- **Put a building post in it.** The block a player clicks to read what a
  building is. One is added for you if you leave it out, in the first free cell
  above the floor — which is rarely where you wanted it.
- **Cut a real doorway.** A hole in the front wall a person's head fits through,
  two courses above the floor. An arch, a gate or an open span all count; a
  closed door block counts too.
- **Beds are real beds**, and there must be exactly as many as the building
  sleeps. A cottage sleeps three. The check tells you the number.

### The anchor

One cell of your building lands on the plot the town chose. By default that is
the **building post**, found automatically when you scan. Give a third
coordinate to `scan` to choose a different cell.

Get this wrong and the building stands beside its plot instead of on it.

### Which way it faces

The direction **you are looking when you scan** is the direction the building's
front faces. Stand with your back to the building and look out the way its door
looks out.

The file records it, and the town turns the building by it so the door ends up
on the street whichever way the plot is oriented. A file that says nothing is
taken as facing south (`+z`).

---

## The commands

### `/civ blueprint region <x1 y1 z1> <x2 y2 z2>`

Marks the box you are about to take, and records which way you are facing.
**Hold the surveyor's lamp** and the box is drawn round it in the same lines a
building plot is, with a tick on the side the front is on.

`/civ blueprint region clear` puts it away. The mark is not saved and does not
survive a restart.

### `/civ blueprint scan <name> [<x1 y1 z1> <x2 y2 z2> [<ax ay az>]]`

Takes the region and writes it to this world's blueprint folder. With no
coordinates it uses the marked region.

- `<name>` is `style/building`, e.g. `norman/cottage`, or a bare `cottage`.
- The optional third position is the anchor. Left out, the building post is used.
- Crops in the region are counted and recorded, which is what a farm's yield is
  worked out from.

There is no size limit on the format, but the command refuses a region over
250,000 blocks — at that size it is a mistyped coordinate rather than a castle.

### `/civ blueprint check <name>`

Reads the file back and holds it against the same tables the drawn buildings are
held to. Prints what it found — size, beds, crops, post, door — and then every
finding, as one of:

- **REFUSED** — do not use this file. Fix it and scan again.
- **WARNING** — it will work, and you probably meant something else.
- **NOTE** — worth knowing, and not a fault.

### `/civ blueprint place <name>`

Puts one down where you stand, anchored the way a town anchors it and turned to
face the way you are looking. What you walk round is what a town would raise.

### `/civ blueprint list`

Every authored building this world can see, and where the folders are.

---

## What the check enforces

| Rule | Why |
|---|---|
| Both spans odd | A building turns about a middle cell. Without one it moves half a block per quarter turn. |
| No wider or deeper than the declared size | The plan reserved ground for the declared size. A bigger file is built through the neighbour, and the town never notices. |
| Fits the reserved plot, with its doorstep | Same reason, measured the other way. |
| Exactly the declared number of beds | The town houses people by the catalog's number, not by counting beds. One short is a settler standing in the dark all night. |
| A way through the front wall at head height | Workers walk to the doorstep and expect to get in. |
| Nothing more than one block outside the footprint | Ground past the footprint belongs to whatever the plan puts there next. |
| Nothing outside the footprint below course 3 | Up high that is an eave. At floor level it is a wall on the neighbour's doorstep. |
| A farm has crops in it | A town's whole food supply is a count of crop blocks. A field with none starves the town and looks fine. |

A **post** is a note rather than a rule: one is added if you leave it out.

Only the *size* rules stop a building being placed. A file that is the right size
but is short a bed is placed and works — badly — and says so in the log. Run
`check`.

---

## What an authored building reads from the file, and what it still reads from the tables

This matters, because the simulation never looks at the world. It has to be
*told* where things are.

**From your file, found once when the building is raised and remembered:**

- every bed, both halves, and which way each one lies
- the doorstep — the block outside the door people walk to
- how many crop blocks a field holds
- the footprint: how wide, how deep, how tall

**Still from the tables, for every building:**

- how many people live here (the catalog)
- what the building is for, and what it stores
- how much ground its plot holds
- who works there, and how many of them

So you can move the beds and cut the door wherever you like. You cannot make a
cottage sleep four by putting four beds in it — put four beds in it and the
check refuses the file.

A building the code drew is unaffected by all of this and goes on reading the
tables exactly as it always did.

---

## Blockbench

A model made in Blockbench is not a building — it is a model, and models are for
items and entities. To turn Blockbench work into a *building*:

1. Export it, or rebuild it, as blocks in the world. Blockbench's own structure
   export writes a vanilla structure NBT.
2. Load that with a **vanilla structure block** (`/give @s structure_block`),
   place it into the world, and look at it.
3. Scan it with `/civ blueprint scan` like anything else.

The scanner is not a structure block and has none of its limits — there is no
48-block ceiling, so a keep or a whole street can be taken in one go. But a
Blockbench export that came through a structure block *was* built under that
limit, which is worth knowing before you design something 60 blocks across.

Structurize `.blueprint` files (MineColonies schematic packs) are read directly,
by content rather than by extension — a Structurize export saved as `.nbt` is
still recognized for what it is. Their `primary_offset` is honored as the anchor.

---

## Shipping a building in a pack

Put the file at `data/<namespace>/blueprints/<style>/<name>.nbt` in a datapack or
a mod jar. It is read for the id `<namespace>:<style>/<name>`.

`data/<namespace>/structure/<name>.nbt` — where vanilla keeps structure templates
— is also read, second. Use `blueprints/` for buildings and leave `structure/`
for vanilla structures.

A world file of the same name always wins, which is the point: a player can
replace any building you ship without touching your pack.

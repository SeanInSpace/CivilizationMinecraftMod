# Keystone

**A standalone blueprint mod: author structures of any size, place them with
orientation, and let other mods build them course by course.**

Keystone ships as its own jar and knows nothing about Kingdoms. Kingdoms depends
on it, never the reverse — the same relationship Structurize has to MineColonies.

---

## Why it exists

Kingdoms could not reach kingdom-scale civilisations on hand-coded geometry. Four
things blocked it, and Keystone removes all four:

| Blocker | Before | Now |
|---|---|---|
| **Orientation** | Placements carried a `Block` and were laid as `defaultBlockState()`. Stairs, doors, slabs and logs all faced default. | Every block carries a full `BlockState`. |
| **Content is code** | Eleven structures hand-written in Java; six were one `cabin()` function plus decorations. | Structures are files. |
| **48-block ceiling** | Vanilla's structure block caps every axis at 48. No keeps, no curtain walls. | No limit — see below. |
| **Authored ≠ built** | A datapack `.nbt` was stamped in whole; only generated shapes were built by hand. | One path. Everything rises course by course. |

### The 48-block thing

The limit everyone associates with structure files is
`StructureBlockEntity.MAX_SIZE_PER_AXIS = 48`. It belongs to the structure
**block** — the authoring UI — not to the **format**, which has no size field
worth the name. Keystone never touches a structure block, so nothing here has a
ceiling. An 80×2×1 wall is verified in the test log; a castle is the same code.

---

## The format

Keystone reads and writes **vanilla structure NBT**. Deliberately: files authored
with structure blocks, shipped in datapacks, or exported by other tools all load
unchanged, and anything Keystone writes can be loaded by a vanilla structure
block in turn.

It is parsed by hand rather than through `StructureTemplate`, because that class
keeps its palettes private and its only public reader — `filterBlocks` — filters
by one block you already know, which cannot enumerate a structure. Parsing the
tag ourselves also means *we* choose the ordering, which is what makes
course-by-course construction possible at all.

Damaged content degrades rather than crashes: an unreadable palette entry becomes
air, a block with an out-of-range palette index is skipped. One bad file in a
community pack must not take the loader down with it.

---

## Where blueprints come from

Sources are consulted in priority order, first hit wins:

| Source | Priority | Location |
|---|---|---|
| **Folder** | 100 | `<gamedir>/keystone/blueprints/<namespace>/<path>.nbt` |
| **Datapack** | 50 | `data/<namespace>/structure/<path>.nbt` |
| *(Kingdoms' procedural shapes)* | — | fallback inside Kingdoms, when no file matches |

The folder is **global, not per-world**, so a building laid out in a creative
world is usable in the survival world you actually play.

Identifier paths permit dots, so `../../secrets` parses happily as an id — every
resolved path is normalised and re-checked against the blueprint root before use.

### Styles

`Blueprints.loadFirst` takes a list of candidates, which is how architectural
styles work: Kingdoms asks for `kingdoms:norman/house`, then `kingdoms:house`,
then falls back to its built-in house. A culture only has to draw the buildings
it wants to differ on.

---

## The wand

Craft nothing — it is in the Tools creative tab as **Blueprint Wand**.

**Scanning** (nothing selected):

| Action | Effect |
|---|---|
| Click a block | First corner |
| Sneak-click a block | Second corner |
| Right-click the air | Name and save what lies between |

Name it `my_house` to save under Keystone's namespace, or `kingdoms:house` to
save into another mod's — which is how a building you drew replaces one a
settlement would otherwise build for itself.

**Placing** (after `/keystone select`):

| Action | Effect |
|---|---|
| Right-click | Place where the outline sits |
| Sneak-right-click | Turn it a quarter turn |

An outline of sparks follows your crosshair, drawn server-side with the same
technique the Kingdoms charter uses for settlement borders.

> **Not a translucent ghost.** 26.2 moved world rendering to an
> extract-and-submit pipeline (`SubmitCustomGeometryEvent`,
> `ExtractLevelRenderStateEvent`). A block-by-block phantom is reachable there
> and is the obvious next improvement, but an outline already answers what you
> need before committing — where, which way, how big — with no client render code
> to get wrong.

---

## Commands

All at permission level 2.

```
/keystone list
/keystone info <blueprint>
/keystone save <name> <from> <to>
/keystone select <blueprint>|none
/keystone place <blueprint> <pos> [rotation]
```

`rotation` accepts `90`/`cw`, `180`, `270`/`ccw`. `save` is the command form of
the wand and shares the same code path, so a blueprint written either way is
written identically — and it makes the whole thing scriptable.

---

## Using it from another mod

Talk to `BlueprintSource` and `Blueprints`, never to a concrete source:

```java
Optional<LoadedBlueprint> found =
        Blueprints.load(level, base, id, Rotation.CLOCKWISE_90, Mirror.NONE);
```

A `LoadedBlueprint` offers two views:

- **`sequence()`** — the construction order: bottom course first, full blocks
  before partial ones within a course, deterministic below that. Air excluded,
  because a builder cannot carry it. The list index is the build cursor, which is
  exactly where a materials gate would stop.
- **`all()`** — everything including air, for stamping a structure whole.

Structure void is absent from both: it means "leave whatever is here", the one
instruction that is never a placement.

Ordering is computed against `EmptyBlockGetter`, so it depends only on the
blueprint — which is what makes a resolved blueprint safe to cache and share
between sites. Sources that read the world declare `cacheable() == false`.

### Adding a source

Implement `BlueprintSource`, call `Blueprints.register(...)`. This is also the
swap-out seam: if Structurize ever ships for 26.2, an adapter over its
`Blueprint` (which exposes a palette and a `short[][][]`) is roughly 150 lines
and one registration line, with no call sites touched.

---

## Limits, deliberately

- **Air is not scanned.** Files stay proportional to the building rather than its
  bounding box — an empty 100-cube would otherwise be a million entries. The cost
  is that a placed blueprint does not carve out space. Use `structure_void` to
  mean "leave this alone".
- **Entities are not captured.** Blocks and block entities only.
- **No datafixing.** A blueprint from a much older version may lose blocks to the
  degrade-to-air path rather than being upgraded.
- **No `.blueprint` reader yet.** Structurize's format is extended structure NBT,
  so a reader would be small and additive — and would buy its whole content
  ecosystem without depending on the mod.

---

## Testing note

`BlockState` cannot exist in a plain JUnit test here. `SharedConstants`' static
initialiser calls `FMLEnvironment.isProduction()`, which throws *"There is no
current FML Loader"* outside a real launch. So:

- **Unit tests** cover the coordinate maths only (`TransformsTest`), which is
  pure integer arithmetic over `BlockPos` and `Rotation` — types that never touch
  `SharedConstants`.
- **Everything else is verified against a running dedicated server**, driven over
  RCON, asserting real block states with `/execute if block`.

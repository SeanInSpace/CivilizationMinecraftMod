# Changelog

What changed, in the order it changed. Newest first.

Entries are written for somebody coming back to this after a month. A line
says what is different in the game, not which files moved — the commit
messages carry the reasoning and the measurements.

---

## Town styles, worldgen sites, and builders who carry their bricks

Ten units of work, landed together.

### New

- **Five new town arrangements**, all streets-first, taking the roster to
  thirteen:
  - **crossroads** — two roads meeting at a market square nobody may build on,
    with short ribs off the arms. Reads as a cross from the air, not a grid.
  - **bastide** — a planned grid round an open market place, inside a circuit
    road. A founder's town, laid out at once.
  - **thorp** — a wandering track with cul-de-sac lanes off it, each ending in a
    yard with buildings on three sides. A comb.
  - **crescents** — a straight spine with half-circle lanes looped off it,
    alternating sides, each enclosing a green.
  - **green** — the Angerdorf: a lens-shaped green with a street down each side
    meeting at both ends, and back lanes behind.
- **A people can build more than one shape of town.** `Culture` carries a list
  of arrangements rather than one, and a settlement picks from it by its own
  centre, then keeps that choice in the save. Every people's historical layout
  stays first, so nothing already standing is rearranged.
- **`/civ sites`** — lists the settlements world generation would place near
  you: region, position, people, distance. Decided arithmetically from the world
  seed on a 512-block grid, with a derived 320-block minimum separation. Places
  nothing yet.
- **`/civ seed <stage> [population]`** — raises a settlement already built at a
  stage, with residents, stores and households, which then lives and grows
  normally. Founding previously always produced a four-pioneer camp.
- **A settler's pockets, in creative.** Sneak right-click one to see what they
  carry, what building load they hold and what errand they are on. A lens: the
  possessions are the simulation's and nothing can be taken.
- **`/civ buildtest` takes an arrangement** — `/civ buildtest 5 64 crossroads`.

### Changed

- **A builder may only lay a block whose material they are carrying.** The town
  ledger is charged once, when the load is picked up, and never again at the
  wall. The stall assist no longer finishes a step for empty hands. The clock —
  what an unwatched town does — is untouched, deliberately.
- **Buildings come up to the kerb.** They are measured before placing and set
  down a block off the paved edge, instead of sitting at the plan's setback,
  which left a house six blocks of bare grass back from the road it fronts.
- **The surveyor's lamp draws lines.** Marks a quarter-block apart instead of
  sparks every two blocks, and it draws the streets as well as the buildings.
- **Panel chrome lives in one place.** All four screens shared the same colours,
  header and row banding by copy; the town overview had quietly drifted two
  pixels from the rest.

### Fixed

- **A rendered town survives the save.** It was never written to disk, so
  reopening the world gave back bare streets and no kingdom.
- **Roads are paved where they serve somebody**, rather than within a radius of
  the town centre — which kept a ring town's spokes entire and threw away a
  thorp's outer tracks, stranding the lanes and yards hung off them.

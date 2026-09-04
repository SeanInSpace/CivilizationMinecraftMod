# Changelog

What changed, in the order it changed. Newest first.

Entries are written for somebody coming back to this after a month. A line
says what is different in the game, not which files moved — the commit
messages carry the reasoning and the measurements.

---

## Buildings the size of their plots, and roads that cross water

### Changed

- **Buildings are two to five times bigger, and a village is dense.** Every
  building's size was a literal in the method that drew it while its plot was a
  column in the catalogue, and the two had drifted to about a factor of two
  apart -- a cottage drawn five blocks across on nine blocks of reserved ground,
  a house drawn five on eleven. Every street in the mod was laid out for
  buildings twice the size of the ones put on it, which is the whole of why a
  village read as huts scattered in a field. One table declares it now and both
  halves read it. A cottage is seven by seven, a house nine, an inn eleven by
  nine, a hall thirteen by eleven.
- **A house comes up to the street it fronts.** There was a rule for this and it
  only ever ran in `/civ buildtest`, so every settlement that actually grew --
  and every settlement world generation raised -- kept a setback drawn for the
  largest building that might have stood there. Measured on a grown town: front
  wall to kerb down from eight and a half blocks to five, and wall to wall
  between neighbours from nine to six.
- **A building too big for its setback backs off instead.** The same rule both
  ways, because a building broader than twice the setback could otherwise never
  front a street at all: every offer it was made stood in the carriageway.
- **`/civ buildtest` shows what a town would actually build.** It kept its own
  copy of the kerb rule and measured to the wrong edge of the street.

### New

- **A longhouse and a croft**, six beds each, for towns past fourteen and
  eighteen residents. The croft is the mod's first building that is not a
  rectangle: a corner is declared cut away, so the yard in the crook is never
  excavated, never scraped flat and never claimed, and it turns with the house.
- **A library**, twenty-three by seventeen, one per town and not wanted below
  forty residents. Wider than the plan's own plot pitch, so it takes two
  frontages -- which is the behaviour big buildings needed and nothing had
  proved.
- **Roads cross rivers.** Water was impassable to a road in three places at
  once, so a river severed a town's network and a settlement on two banks was
  two settlements sharing a name. A crossing is now priced rather than refused,
  up to twenty-four blocks, and where a laid road meets water it gets a planked
  deck with fence railings, an arch over the middle and piers to the bed.

### Fixed

- **A plot could be looked up in a plan of one plot**, so the rule that passes
  over a house fronting a road the hillside refused had been answering for plot
  zero and no other since it was written.
- **A town no longer walks to the horizon to avoid a poor plot.** That rule was
  a veto and assumed the next offer was nearby; on an arrangement whose lanes
  are refused in whole families it is not, and the plot cursor ran out past the
  town. It is a heavy preference now.
- The founding kit pays for the bigger buildings it is asked to buy, and the
  timber ceiling was raised above it so the grant is not silently shaved on
  arrival.

### Notes

- Bigger buildings are more work, so towns build more slowly: a wall that closed
  at step 373 now closes at 463. Three test thresholds moved with it.

---

## The mod says the same licence the repository does

### Fixed

- **The built mod declared "All Rights Reserved" while the repository is
  GPL-3.0.** Both jars carried it, because `mod_license` in `gradle.properties`
  is expanded into each `neoforge.mods.toml` — so anyone reading the mod list in
  game was told they had no rights to it at all, while the LICENSE file and the
  README granted them everything. Now `GPL-3.0-only`, matching the LICENSE text.

---

## Towns in a generated world, instead of villages

### New

- **Settlements generate in a new world.** No vanilla villages: the village
  structure set is emptied by datapack, and towns of this mod take their place.
  Where a town belongs is arithmetic on the world seed — a 512-block grid, one
  candidate per region, at least 320 blocks apart — so the same seed always gives
  the same world and nothing is stored until it is settled. Nothing is built
  until a player comes within reach, at which point the ground is scored once, a
  town is raised already built at village stage, and the region is written down
  so it never happens twice. A site on water or a cliff is refused and recorded
  as refused.
- **`worldgen.arrangements` in the server config** — one weight per arrangement,
  against each other. **Green is 100 and everything else 0**, so a fresh world is
  a world of green villages. Zero means never; all-zero means no preference
  rather than no towns.
- **`worldgen.enabled`** and **`worldgen.reach`** (default 256 blocks) alongside
  it. Villages stay suppressed either way — that part is a datapack, not a
  setting, so a world that wants them back needs a datapack that puts them back.
- **`/civ sites`** now names the arrangement each site will be built in, and
  reads the weights the world is actually configured with.

### Notes

- Three of the first four sites on the test seed were refused for ground, all
  near spawn and all water. That is the siting rule working, but it means a
  coastal world will be thinner than the grid suggests.

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

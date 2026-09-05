# Changelog

What changed, in the order it changed. Newest first.

Entries are written for somebody coming back to this after a month. A line
says what is different in the game, not which files moved — the commit
messages carry the reasoning and the measurements.

---

## The whole repository spells American now

Nothing in the game is different. 2041 British spellings became American ones,
across prose, player-facing text and the names in the code itself, following the
worklist in [docs/american-spelling-audit.md](docs/american-spelling-audit.md).
The suite is 1072 tests before and after.

The visible part is small: the inn now offers beds for *travelers*, the smithy
makes *armor*, `/civ` reports a town's *center*, and a town seeded short says its
*program* wanted a building it could not have. The invisible part is most of it —
`BuildCatalogue` is `BuildCatalog`, `Settlement.centre()` is `Settlement.center()`,
`laboursAs` is `laborsAs`, `KERB` is `CURB`. Three files were renamed to match the
types in them: `BuildCatalogue.java`, `KerbTest.java`, `LevellingTest.java`.

**Existing worlds load unchanged.** Four strings that look like spellings are
save keys — `"centre"` in three codecs and the value `"armour"` behind
`TownStores.ARMOR` — and renaming any of them would empty that field out of every
town on load. They keep the spelling they were first written with, and each now
carries a comment saying so. The one place this shows is the stores panel, which
still reads **Armour**, because it capitalizes the key rather than holding a word
of its own.

The survey viewer's data contract moved as one piece: `tools/survey.py` writes
`center` and `defense`, `tools/townview.html` reads them, and the four committed
fixtures in `surveys/` were rewritten to match. A survey JSON captured before
this will not draw; re-run `survey.py` on the log.

Four words that look British and are not were left alone: `carriageway`,
`tarmac`, `metalled` and `timber` are vocabulary rather than orthography, and the
project means them.

## Walls at the charter, hands on the wall, and every creature's true face

Eight units, landed together. A town builds its one wall as a chartered town
and moves it only when its suburbs outgrow it; its builders plant that wall,
pave its streets and mend its houses by hand; a hungry settler eats first; and
every creature in the world, modded or not, sees the townspeople and is seen.

### New

- **`/civ info` names anybody standing still with a job to do.** One line per
  settler who has a trade and has not moved a block in fifteen seconds: their
  name, their trade, how long they have stood there, where, how hungry they are
  in both the number and the word, and what errand they are on. Builders get two
  more facts, because they are the ones this was written for — whether the
  construction pass has taken charge of them at all, and whether it could find a
  route to the block it wants them at. The several ways a settler ends up frozen
  look identical from inside the simulation; this is what tells them apart in a
  world. The food line also says how many people are currently off to eat.
- **Two documents that answer "why is that person doing *that*?"**
  [docs/CITIZENS.md](docs/CITIZENS.md) is every profession as a table — the
  trigger that fires, the action it produces, the constant that gates it, in the
  order the code applies them, with a column for what happens unwatched and a
  column for what a player sees, and a closing section on where the two genuinely
  differ. It is written from the code rather than from the design, so where it
  and the code disagree the code is right and the document is the bug.
  [docs/HAULERS.md](docs/HAULERS.md) is a design, not an implementation: the
  porter trade a town wants once it outgrows everybody carrying their own goods —
  who staffs it and at what ratio, the three flows it takes over, the single
  queue that replaces three planners each picking their own carrier, and twenty
  named tests to write when it is built.

- **Damaged buildings are repaired by builders, from five percent.** A house
  that lost a wall used to come back whole in a single instant, the entire
  blueprint re-placed by a command, and only once a quarter of it was gone. A
  repair is now the missing blocks and nothing else -- the plan is the
  difference between the drawing and the world, laid by the same crew that
  raises a house, with the materials for exactly those blocks carried from the
  store -- and it is booked at five percent of the building missing, or two
  blocks on a small one. A building with a repair booked is never written off
  as a ruin; one the town cannot start repairing is left to be.

### Changed

- **The wall is planted by builders, out of planks they carried.** A settler did
  walk out to the next position on the line and swing at it, and the swing put
  nothing in the ground: it moved a counter, and a sweep stamped the posts in
  twenty-four a second wherever the ring happened to be loaded. The timber came
  off the books the way a tax does. Now a builder walks to the storehouse,
  shoulders sixteen planks, and plants sixteen posts one course at a time before
  walking back — two fences and, on every eighth, its lantern, which is exactly
  what the sweep lays for a town nobody is watching, out of the same list. A post
  still costs one timber and three coin; the timber simply leaves the warehouse
  when somebody picks it up rather than when the post goes in, so a wall can now
  empty a store. Which post is next is read off the ground, so one interrupted
  halfway is finished rather than restarted, and a town watched, then left, then
  come back to neither loses posts nor pays for them twice.
- **The old wall comes down by hand as well.** A town that outgrows its palisade
  stakes a wider one and used to have the old posts vanish under a sweep. Pulling
  them up is now the first job a spare builder is given — ahead of the new wall
  and the roads, because while the old line stands there are two walls round one
  town — and half the timber comes back on the shelves. Half rather than all: a
  post that has stood a generation in the ground is firewood, and a town that
  recovered its whole wall every time it moved one would be re-staking at a
  profit.
- **A street is opened by walking it.** A settler used to walk to the middle of a
  planned run, swing once, and the whole street — grading, gravel and the bridge
  over the brook — appeared behind them. A crew now paves it as they walk, a
  cross-section at a time, the way a road gang actually works: the column
  underfoot and the width either side of it. Nothing is carried, because a dirt
  path is shovel work — so a paving crew never queues at a storehouse and can
  never be the reason a town runs out of timber. A run is written down as opened
  when they reach the far end of it, so a player who walks away halfway leaves a
  half-paved street that the town finishes on its own. Bridges are unchanged and
  still go in whole; an arch is not a sequence of columns.
- **The wall comes before the roads.** Roads used to be the first public work a
  spare builder was offered, on the reasoning that a road is what lets everybody
  else get to work faster. A growing town always has one more street planned, so
  in practice it answered every new outlying shed with a fresh stretch of track
  and never got back to the ring. A half-built wall is a town that cannot shut
  its gate; a half-built lane is a walk across grass. Both still wait for the
  build queue: shelter and stores first, and a repair counts.
- **A guard fights what the town is afraid of.** The town's own eyes had been
  widened to every creature the danger table has an opinion about; the watch had
  not, and went on charging anything filed as a monster. The two lists disagreed
  in both directions — a town could be shut indoors by phantoms no guard would
  look up at, and could send the watch out at an enderman standing calmly in a
  field that had frightened nobody. One list now. A guard goes for the nearest
  thing he could actually come to blows with, so a phantom overhead no longer
  stands between him and the zombie at the gate, and he draws on no neighbor of
  his. The damage, the reach and the creeper's fuse are untouched.
- **Everything in the world can see the townspeople, and the town can see
  everything in the world.** Two halves of the same hole. Vanilla's creatures
  hunt players and vanilla villagers, and a settler is neither — so a pillager
  band walked through a town without noticing anybody in it, a skeleton on a
  roof shot at nothing, and only zombies had ever been told otherwise, by name.
  Now every creature that enters a level is asked what it is: anything hostile
  hunts settlers one slot behind the way it hunts a player, which is exactly
  where vanilla puts its own hunt for villagers, and anything neutral — a wolf,
  a bee, an enderman, a polar bear — leaves them alone until one of them draws
  on it and then remembers who did. Asked by what the creature *is*, so a mod's
  own monster is covered without the mod being named. Two consequences worth
  saying out loud. The zombified piglin stops hunting settlers: the old line
  armed every zombie, and a zombified piglin is a zombie by descent, so it used
  to come for a Nether town unprovoked — which is not what a zombified piglin
  is, and it now waits to be hit like the rest of its kind. And the only settler
  who can hit anything is a guard, who still only fights things that walk, so
  the neutrals that will actually bite a settler back today are endermen and
  zombified piglins; the wolf is wired and waiting on the watch. The other half
  is what a town can see: the sweep only ever collected
  things that walk, so a ghast, a phantom, a slime, the ender dragon and any
  modded boss that flies were invisible to the alarm however carefully the
  danger table graded them. They count now. So a town is wary of phantoms on the
  third night and of slimes in a swamp, which is new and is meant; and it is no
  longer permanently wary of an enderman standing in a field, which is also new,
  because a neutral creature only counts while its quarrel is with one of the
  town's own people. No creature's danger number changed. **The watch has not
  caught up:** a guard still only picks fights with things that walk, so a town
  can now be frightened of a phantom overhead that nobody will go out to.
- **A town walls itself once, at its charter, and lives inside that line.** Two
  faults, at opposite ends. A settlement that grew past its wall stayed past it
  for the rest of the world's life — 58 of 85 buildings outside their own ring at
  seven hundred steps — and the wall that was built to answer that followed the
  town instead, re-staked whenever a candidate line came out an eighth longer:
  four to seven times over fourteen hundred steps in every one of the thirteen
  arrangements, a settlement permanently redrawing its own outline. Nothing is
  staked before TOWN now, and a chartered town stakes its circuit once, around
  what it is on that day. What it builds afterwards goes up outside the wall as
  suburbs, unwalled, which is where every medieval town actually put its growth.
  A second circuit needs all three of: more ground-holding buildings outside the
  line than inside it, the standing wall paid for to its last post, and five
  hundred steps — the whole founding ladder — since it was last staked. Across
  the same thirteen arrangements the line now moves once or not at all. FORTIFIED
  keeps its name and means what it says: a watch, not a wall. Any settlement that
  already has a wall keeps it, at any stage. When the line does move the old one
  is still pulled up as the new one goes up — a wall left inside a wall is a
  fence through the middle of a town, and it shuts a settler out of their bed the
  same as any other. Only two courses of fence or a gate come down, so pens,
  field boundaries and bridge railings survive, and the posts already raised
  travel with the town: a wall moved outward is the same wall, and nobody is
  charged twice.

### Fixed

- **The carpenter stays at his bench.** A player watched his carpenter shoulder a
  stack of supplies and walk it across the village with the carpentry standing
  empty behind him. The rule that sent him was a list of two exemptions — not a
  builder, not a farmer — which reads as a short list of exemptions and is really
  a long list of conscripts: on any village with workshops in it, the next person
  found is a craftsman, and the town bought a delivery with a workshop. A load is
  offered in tiers now — an idler first, then a trade with genuinely nothing in
  front of it today (a lumberjack at the timber ceiling, a miner at the stone
  ceiling, a smith at a cold forge, a trader whose only stall is already full,
  and the miller always), never a builder, never a guard — and if
  nobody qualifies **the load waits**. That is the deliberate answer, not a
  missing case: a waiting haul costs the town a walk it will make later, and a
  stopped workshop costs it everything that workshop would have made in the
  meantime. Goods sit on a shelf and are still there next step; an hour a
  carpenter spends on the road is an hour of components the build crew never
  gets, and the build is what the delivery was for. The miller is the deliberate
  exception and the mirror image: his contribution is the same kind of headcount
  the carpenter's is, but he has no bench to be dragged away from — unwatched he
  is a term in a multiplier, watched he stands beside the mill — so he is the one
  tradesman a walk costs nothing at all, and he is never refused.
- **Farmers stay in the rows.** The field sent for collection at a single loaf,
  so it sent for one every step — and a farmer with an errand is a farmer out of
  the field, because the watched loop skips anyone on the road, correctly. A
  watched field therefore grew one loaf, emptied, grew one loaf, emptied, and the
  player standing in it saw three farmers walking laps and nobody farming. Worse,
  two of the three arrived at a field somebody had already cleared and walked
  home with nothing. A field is collected at a full load now, twelve rather than
  one, and errands already outstanding are subtracted from what a field is
  holding, so two farmers are never dispatched to the same twelve loaves. The
  town's throughput is unchanged — the same grain reaches the granary in fewer,
  fuller journeys — and in between them the farmers are in the field, which is
  what a farm is supposed to look like. Suspended while the town is starving: a
  loaf is a life then, and the walk is worth making for any of it. Fuller loads
  cost something the small ones did not, so the granary's headroom now subtracts
  what is already walking toward it: stock does not move until a carrier arrives,
  the same twenty spaces were otherwise offered again on every step of a walk
  that takes several, and a granary told to accept more than it holds spoils the
  difference rather than duplicating it.

- **Somebody weak with hunger goes and eats.** Between hunger 60 and 89 a
  settler was barred from working and barred from shopping at the same time —
  the errand that fills a family's larder is only ever given to somebody who is
  *not* too weak to run it — so a person with empty pockets and an empty larder
  stood exactly still, getting hungrier, until 90, when the town finally reached
  into the granary on their behalf. Guards and builders were never shoppers at
  any hunger at all. Now anyone past 60 puts the job down, walks to the nearest
  thing they can eat — the family larder, a market stall, a field, the granary —
  eats it there, and takes the job back up. Nobody's trade changes and no work
  in progress is lost, because the errand is the whole of the suspension. Two
  exceptions: the watch does not leave the wall for dinner while the town is
  alarmed, and somebody with nowhere at all to walk to stays on the job, because
  a starving idler is worse off than a starving worker. An unwatched town is fed
  exactly as it was — out of sight the last leg is still the clock's.
- **A builder is not left standing on the roof they just finished.** The day's
  routine used to stand aside for every builder whenever anything at all was
  queued, on the reasoning that the construction pass had them. It gives up
  before it reaches a single builder for half a dozen reasons — the plot is
  blocked, the hole is not dug, a clearance order is running, there is nothing
  the crew can lay this second — and in every one of those the crew was being
  steered by nobody. They are handed back to the day's routine now, which is
  what turns a frozen figure into somebody milling about a site.
- **A builder boxed out of the next block can move again.** Asking vanilla
  navigation for a route it cannot make does not merely fail: it throws away the
  path the settler was already walking. A roof course with nowhere to stand was
  therefore asked for four times a second, and the asking held the builder
  perfectly still while the site's stall assist laid the rest of the wall around
  them. After four refusals the body is left to itself for a few seconds before
  the route is tried again.
- **The wall is built where it was staked.** On a slanted stretch the posts
  were laid in an L that could stand seven blocks off the line every check had
  approved -- through a hearth, a carpentry and a town hall on one measured
  seed -- and the staking never asked about plots the town had already
  ordered. Across 117 grown towns, buildings with a wall through them went
  from 738 to 68, with no town sprawling further for it. Structures
  overlapping each other: none in 67 million pair checks.

### Notes

- **A watched town's farmers stand still during a famine and an unwatched town's
  do not**, and this round leaves it open. The clock suspends the weakness rule
  while a town is starving — precisely so that weak hands go on farming, because
  weak hands that stop bring in no food and the town never recovers — but the
  watched loop asks the plain "too weak to work?" question in all nine places it
  gates on hunger, and so does not suspend anything. The town does not die; the
  twelve-step harvest floor covers it. But in the exact circumstance the
  suspension exists for, the player sees the fields abandoned. The fix is one
  predicate substituted in a file this round's author did not own, and it is
  written up in [docs/CITIZENS.md](docs/CITIZENS.md) §7.

---

## Two blocks between walls, and nine things a town could not do

### New

- **A building can be pulled down, and the town notices.** Nothing in the mod
  had ever removed a building, so a cottage a creeper flattened went on housing
  a family, counting toward the beds that gate every birth, and having roads
  routed to a door that was not there. A structure found with less than a
  quarter of its walls standing, three sweeps running, is written off: its goods
  go on the loose pile rather than out of the town's books, its family moves
  into whatever is empty or joins the housing queue, its repair order is
  canceled and its road forgotten. A town that loses a cottage now wants
  another one. Nothing is written off unless the town saw it standing first —
  otherwise every field, pen and watchtower in the mod, none of which has a wall
  at head height, would be condemned the day it was built.
- **A market with prices that say why.** The stall was vanilla's villager
  trading screen, which has room for a number and nowhere at all for "they are
  starving". It is the mod's own screen now, and every row carries the reason
  beside the price — *they are starving*, *more than they can store* — so a
  shortage is legible from the road, which is the whole of what makes a moving
  price a game rather than a table. The price is re-derived from the town as it
  is when you press the button, not as it was when the screen opened.
- **`/civ info` and the `AUDIT` line say how fast the town is being run.** Three
  figures — passes a minute, the worst single gap in the last minute, and how
  much history that is — printed beside the rate the town is meant to get.
  Measured per world, so the Nether's empty one can never be mistaken for the
  overworld's.

### Changed

- **Two blocks between any two walls, everywhere.** A plot used to be a
  building, the doorstep ring around it, and one further block belonging to
  nobody. That block is gone: two doorsteps that touch are two buildings that
  each have one. Across the thirteen arrangements the gap from a wall to its
  nearest neighbor's went from a median of six blocks to four, and every
  arrangement now touches two somewhere where the tightest used to be three. No
  town sprawls further for it — the stronghold pulled in from 191 blocks across
  to 134 and the bastide from 208 to 184. Nine, the cottage's own separation,
  was built and measured and is worse: a plan that offers at the smallest
  building's floor offers most of its frontage to buildings that are refused.
- **A town out of good ground takes the least bad plot it looked at.** Having
  examined ninety-six candidate plots and refused every one, a town used to take
  the next slot on the ring with no terrain check at all — so the better the
  siting rules got, the more often the search exhausted itself and the more
  buildings were placed on ground nobody had looked at. Ground is scored now
  rather than passed or failed, and the best of what was examined is taken. On
  ground that refuses in families — a lake, a hillside, a quarter nobody has
  loaded — a town that managed nine buildings in five hundred steps now builds
  forty-five, on less than half the fault and never worse than three courses
  where it used to accept eight. On the rough test seed: 47 buildings against 46,
  the plot cursor at 166 rather than 195, and three doorsteps off a road rather
  than four.
- **The danger scale has rungs, and a stranger is no longer a zombie.** Anything
  the table had not named read as the mildest thing in it, so an unrecognized
  horror was a shambling corpse until it was inside the walls. What is not named
  is now read off what the game itself says — whether it is hostile, a raider,
  ranged, a boss — so a modded boss reads at the top of the scale and an archer
  nobody named reads above a skeleton. Three unnamed hostiles now reach the panic
  tier where six shambling ones were needed. Naming a creature is what earns it a
  *lower* number. Seven vanilla hostiles are named at last, the drowned among
  them: it was a zombie by descent and read as one, trident and all.
- **The wall draws by real seconds rather than by sweeps.** Its post budget was
  per pass of the town manager, which is a budget per second only while the
  passes arrive once a second. It earns posts from the time actually elapsed
  now, capped at five seconds of arrears — so ten minutes away lays a hundred and
  twenty posts on the sweep you come back to, not fourteen thousand four
  hundred. The looking is deliberately not paced: a scan that did not happen is
  owed to nobody, and looking is the expensive half.


### Fixed

- **A family in a house the town cannot name is left alone.** A home whose
  blueprint matched no catalog entry — a renamed cottage, a building from a mod
  no longer loaded, an older save — reported a capacity of zero, and every caller
  read zero as *full*. The family counted as permanently overcrowded and shed a
  member into every vacancy that appeared until there was nobody left: three
  members gone in seventy-two steps, measured. Unknown is a different answer from
  zero now, and it means no opinion — nobody sheds, nobody is born, and the house
  is not offered to anyone hunting for one. Three members still three at two
  hundred steps.
- **A building is drawn the size its plot was reserved for.** The market was
  converted to read its size from the shared table, kept an old literal, and went
  on being drawn five blocks across where nine had been set aside for it —
  through a whole in-world run. Every one of the twenty-four kinds is now
  measured against the table without needing a world to draw it in.
- **A town's armory could be bought at a coin an ingot** by anyone who knew what
  the ledger called it. The stall answered for any word in the books, and weapons
  and armor had no price and no reserve, so the fallback price fell out as a
  single coin and the whole holding read as spare.
- **Buying logs and selling planks minted money**, at four planks the log against
  a spread of three to two.
- **A town refused to sell goods it demonstrably owned.** The counter drew from
  the nearest store and gave up if that one was empty, so a market beside the
  granary would not sell timber sitting in a storehouse across the village —
  silently, because a deal declined and a deal unreachable look identical from
  outside.
- **The storehouse destroyed emeralds.** It took a player's coin and handed out
  logs without the town's treasury moving, so trading with a town made money
  vanish out of the world.
- **A wall is never staked through a house.** The line may now not be dug across
  a plot at all — the two rules that were there guard the loop against itself and
  the plots against being left outside, and a building's corners sit happily
  inside a line that runs over its floor.


### Notes

- Measured in a world on the rough test seed, 8675309: **0 of 80 drawn buildings
  standing in water**, and the audit reported none. The town re-staked its wall
  at step 400 and again after, 688 posts to 1134 to 1292.
- The town manager is **not** starved of ticks, which had been the standing
  theory for six runs. Unwatched under `/civ step` load it runs 57.8 passes a
  minute with a worst gap of 4.8 seconds; with a player standing in the town,
  60.0 against the 60 intended.
- **A town grown 511 steps unwatched inside a force-loaded box materialized
  nothing at all** — all 120 of its buildings still pending placement, the wall
  reporting nothing even looked at — and then drew 80 buildings within a hundred
  and fifty seconds of a player standing in it. Nothing was slow; nothing was
  drawn. So the unwatched path does not treat force-loaded ground as ground it
  may build on, and that is the open item this round leaves behind.
- **The storehouse and the market disagree about timber by a factor of sixteen.**
  The storehouse sells eight logs the emerald and the market buys at two emeralds
  the log, so a town holding both can be pumped for about fifteen coin a click.
  It predates this work and closing it means choosing between the founding arc's
  helping hand and the market being a town's only counter.
- Verified in a world by the manager:

  Tried in a world by the manager and NOT seen firing, for a reason worth more than a tick: a cottage with its walls and roof taken off was reported by the auditor as "mostly gone — 0% of its walls still standing" on three sweeps, but the town rebuilt it after every flattening — twenty fills over four minutes, twenty `Materialized` lines — so no three sweeps in a row ever agreed and the write-off count kept resetting. That is the repair planner doing what it is for, and the design defers to it on purpose; the write-off itself is held by thirty-four tests and has not been watched in a world. To see it, flatten a house in a town that has no timber to mend it with.

  Opened in a world by the manager on seed 8675309: right-clicking the market post of a town with two coin in its treasury showed four goods, each with its reason beside the price — "They can spare it" on food, wood and stone, "More than they can store" on iron — and the footer "Prices move with what the town is short of. Paid in emeralds." The post stands a block off center because the stall is turned to face its street, which is worth knowing before clicking at it from a script.

---


## Buildings the size of their plots, and roads that cross water

### Changed

- **Buildings are two to five times bigger, and a village is dense.** Every
  building's size was a literal in the method that drew it while its plot was a
  column in the catalog, and the two had drifted to about a factor of two
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
  wall to curb down from eight and a half blocks to five, and wall to wall
  between neighbors from nine to six.
- **A building too big for its setback backs off instead.** The same rule both
  ways, because a building broader than twice the setback could otherwise never
  front a street at all: every offer it was made stood in the carriageway.
- **`/civ buildtest` shows what a town would actually build.** It kept its own
  copy of the curb rule and measured to the wrong edge of the street.

### New

- **A longhouse and a croft**, six beds each, for towns past fourteen and
  eighteen residents. The croft is the mod's first building that is not a
  rectangle: a corner is declared cut away, so the yard in the crook is never
  excavated, never scraped flat and never claimed, and it turns with the house.
- **A library**, twenty-three by seventeen, one per town and not wanted below
  forty residents. Wider than the plan's own plot pitch, so it takes two
  frontages -- which is the behavior big buildings needed and nothing had
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

## The mod says the same license the repository does

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
  center, then keeps that choice in the save. Every people's historical layout
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
- **Buildings come up to the curb.** They are measured before placing and set
  down a block off the paved edge, instead of sitting at the plan's setback,
  which left a house six blocks of bare grass back from the road it fronts.
- **The surveyor's lamp draws lines.** Marks a quarter-block apart instead of
  sparks every two blocks, and it draws the streets as well as the buildings.
- **Panel chrome lives in one place.** All four screens shared the same colors,
  header and row banding by copy; the town overview had quietly drifted two
  pixels from the rest.

### Fixed

- **A rendered town survives the save.** It was never written to disk, so
  reopening the world gave back bare streets and no kingdom.
- **Roads are paved where they serve somebody**, rather than within a radius of
  the town center — which kept a ring town's spokes entire and threw away a
  thorp's outer tracks, stranding the lanes and yards hung off them.

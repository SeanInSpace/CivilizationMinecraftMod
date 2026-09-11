# Changelog

What changed, in the order it changed. Newest first.

Entries are written for somebody coming back to this after a month. A line
says what is different in the game, not which files moved — the commit
messages carry the reasoning and the measurements.

## A forester in a meadow is standing on good ground

### Fixed

- **A town you find has trees around its lumber camp again — on a superflat, on
  a plain, and in every arrangement its people build in.** Two separate faults
  were between a seeded camp and its wood, and either one alone was enough to
  leave it standing in an empty field.

- **The ground a tree may be planted on now includes grass.** Both the stand a
  seeded camp is given and the saplings its forester puts back were checking for
  bare dirt underfoot — dirt, coarse dirt and rooted dirt, and nothing else.
  Grass is what the top block of a plain, a meadow, a forest or a flat world
  actually is, so a camp on any of them was offered two dozen perfectly good
  squares and turned down every one of them in turn. The test is now the same one
  the game itself applies before it will let a sapling stand: grass, podzol and
  mycelium as well as the dirts, mud, moss and farmland. On a superflat — where
  there is no other wood in the world to hide it — this was the difference
  between a dozen trees and none.

- **A camp's woodland now reaches out past the houses however far the town has
  grown.** The claim a seeded camp takes has to clear the edge of the village,
  because a forester will not replant among the houses and trees planted there
  are felled once and never come back. That claim used to stop at sixty-four
  blocks, which is as far as the dial on a camp block goes. A village laid out in
  rings never outgrows that; a crossroads town carries its frontage out along
  four arms, a warren buds knots off knots, and both leave the village edge a
  hundred blocks and more from the middle. The camp's whole claim then lay
  *inside* the town, every square of it was refused as somebody's doorstep, and
  the camp quietly wrote off its debt having planted nothing. It now widens as far
  as it actually takes, and looks another belt further out again when the first
  one is too hemmed in by lanes and frontage to hold a stand.

- **A town raised at the far end of a flight is no longer the one that suffers.**
  World generation raises a settlement long before its chunks load, so it builds
  on for however long the flight in takes, and by the time its lumber camp is
  drawn the village has grown. That was exactly the case that lost the wood: the
  same town reached immediately was fine. Nine of the fourteen arrangements in
  the table lost the whole stand this way, the two ring plans kept theirs, and
  that is why it went unnoticed until towns started being built in the
  arrangement the world chose.

- **And a camp waiting to be given its wood no longer waits forever.** It needs
  to see the ground before it plants, so that the trees it gets are the dozen
  nearest the camp rather than whichever half happened to be loaded. What it
  waits for is now those squares rather than every square of a claim that may be
  three hundred blocks across — which no server keeps loaded at once.

- **One idle right-click on a camp block no longer strips a camp of its wood.**
  A claim the town widened past the dial's own top comes down to that top on the
  first click, instead of collapsing to the smallest claim there is.

---
## The surveyor's lamp holds still

### Changed

- **The lamp draws solid white lines that stay put.** It used to paint the town
  out of particles once every four seconds, which meant the picture spent most
  of its life fading and you were reading a flicker. The plan is now drawn every
  frame, so you can stand still and look at it, walk along a street and watch it
  hold, or back off onto a rise and see the whole shape at once.

- **The lines say what they are.** Roads the town has opened are solid; stretches
  it has drawn but not opened are dashed, because a street nobody has walked out
  yet is not somewhere you can go and drawing it the same as a real one was a lie
  told in light. The streets the plan laid out are drawn fainter behind both, and
  the wall's ring is gray rather than white so it is not mistaken for a road.

- **Buildings are drawn as their plots, with a mark at the door.** A flat
  rectangle on the ground — the walls plus the land cleared around them — and a
  short tick on the side the building faces. The old wireframe boxes described a
  volume nobody was asking about and hid the building inside them; what the lamp
  is for is reading how a town is arranged, and a door you can see is most of
  that. A plot that is queued rather than built is drawn fainter.

- **It reaches further and shows through hills.** The survey runs out to about
  128 blocks instead of 48, and the lines are drawn over the terrain rather than
  behind it. The view worth having is from a rise looking down, and that is
  precisely the view the ground would have blocked.

- **The lamp now needs Kingdoms on the client.** A vanilla client on a Kingdoms
  server sees nothing when it holds one. The Founding Charter's claim ring is
  unchanged and still works for anybody, because a ring is one circle and a plan
  is not.

## Nothing a citizen breaks is thrown away

### Changed

- **Every block a citizen breaks now goes into the town's supplies.** Clearing a
  building plot, taking a tree off the line of a wall, driving a road through a
  wood, cutting the hillside out from under a floor — all of it used to be
  destroyed on the argument that only a lumber camp makes timber and only a mine
  makes stone. Six oaks felled to stand a cottage on their stumps are six oaks'
  worth of timber, whatever the felling was for, and now the town has it. A camp
  is what makes wood grow back; it was never what made a felled tree real.

- **You can watch it happen.** A settler fills their pockets as they dig — one
  armful, about a stack — and when those are full, or when the hole is finished,
  they shoulder the load, walk it to the nearest storehouse and set it down.
  Several materials means several trips, in the order they picked them up. A town
  with no storehouse yet piles it on the ground where it was dug, which is how
  the first storehouse gets paid for.

- **A town built out of sight is credited with the same thing.** Where nobody is
  watching, a building is drawn all at once and the ground it clears is counted
  block by block as it goes. What used to be credited there was a flat guess —
  one course of earth over the plot's footprint, whether the plot was a meadow or
  a forest. It is the real count now.

- **Logs, stone, earth and iron; leaves and litter are still worth nothing.** A
  town keeps four bulk materials, so that is what a broken block can come to: any
  log is timber, stone and its cousins and the ores of metals the town does not
  track are stone, dirt and sand and gravel and clay are earth, and iron ore is
  iron. A crown of leaves, a crop, a pane of glass and somebody's furnace are
  worth nothing, as before.

- **A store that is full still refuses what will not fit.** Dug timber lands
  under exactly the ceiling a lumberjack's timber lands under: a town does not
  gain the capacity to keep a forest just because it cleared one. A tree taken
  out of the lumber camp's own wood also comes off the camp's books, so the same
  trunk is never paid for twice — a tree standing on a house plot in the middle
  of the village was never the forester's and leaves the camp's ledger alone.

---

## The footpath goes round the house

### Fixed

- **Houses are no longer built on top of roads, and roads are no longer laid
  through houses.** Across thirteen arrangements grown on two terrains, founded
  and world-generated, watched and away — 104 towns and about 2,900 buildings —
  there were **972 cases of a road's gravel running inside a building's walls.
  There are now none.** Every single one of the 972 was a footpath, and that is
  exactly why nothing had ever caught it: a town kept its buildings off its
  carriageways and treated the little three-wide lanes between doors as though
  they did not exist.

- **A lane from a door now goes round whatever is in the way.** It used to run
  two straight lines from the doorstep to the nearest road and consult nothing in
  between, so any house standing on that line got a path driven through it — 245
  of the 972. A lane now keeps clear of every building that is standing and every
  one that has been ordered, takes its turn further out or meets the road a few
  blocks further along when that is what it takes to get round, and is simply not
  laid at all if the only way through is somebody's kitchen.

- **And it no longer scrapes down the side of its own house.** The commonest case
  of all — 415 of the 972 — was a building gravelled by its own path, which left
  the door, turned too soon, and came straight back along its own side wall. A
  path now leaves by the door and turns clear of the building it came out of.

- **A building will not be raised on a footpath somebody walks.** The remaining
  194 were houses sited squarely on a lane that was already there. A plot may
  still be laid over the dead end of a lane that led to a door which is no longer
  there — that is ground the town made and can take back — but a path that runs
  through from one place to another is now a road like any other, and the town
  builds somewhere else.

- **A town that cannot reach a door says so.** About seven doors in a thousand
  end up with no lane at all, because every line to every road near them would
  have to cross a wall. Those are left unjoined and counted on the `/civ info`
  roads line, which now reads "joined of standing" rather than a bare number — a
  door the town admits it has not reached, instead of a path through a bedroom.
## Roads look trodden and houses stand in daylight

### Fixed

- **Leaf litter no longer lies on top of a new road.** A way laid through a
  birch or cherry wood came out with the forest floor still scattered across
  the gravel, which made the whole road read as something painted on after the
  world was made rather than as a path somebody had walked. Everything growing
  on the stones now comes off them when the stones go down — litter, grass,
  ferns, flowers, snow, wildflowers, petals and all — on bridges as well as on
  the ground.

- **The verge is left alone, so a road still runs through a meadow.** Only the
  paved strip is cleared. The block either side of the carriageway keeps
  whatever grows on it, which is what stops a country lane reading as a trench
  of bare dirt.

- **A tree standing in the road comes down whole**, and so do the branches over
  it, so a way through a wood is a way through a wood rather than a tunnel with
  a trunk in the middle. A road that runs under a bridge or a raised floor is
  left to run under it.

- **Buildings no longer finish with a canopy across the roof.** A house raised
  in a forest used to have the crown of the next tree lying over its tiles and
  its own chimney buried in leaves, because the crew only ever cleared the cells
  they were about to lay a block in. The whole plot and its doorstep ring are
  now cleared to the sky of growth — logs, leaves and ground cover — so you can
  see the building you just paid for. Stone, ore and the hillside itself are
  untouched: this clears a wood, not a mountain.

- **Trunks leaning on the walls are felled.** Any tree rooted within two blocks
  of a building's plot comes down when the site is cleared, which is the one
  place the town touches ground it does not own. Its crown is left to wither the
  way any felled tree's does. Trees three blocks out and further are somebody
  else's, and the trees in a lumber camp's own woodland are never touched — a
  forester's stand is not in anybody's way.

- **None of that timber counts as a harvest.** Wood cleared off a site is spoil,
  exactly like the earth dug out from under a floor. A town still has to raise a
  lumber camp and cut its own trees to have any.

- **Repairs leave the clearing alone.** Mending a damaged building never puts
  back leaves that were cleared off its roof, and litter drifting onto a finished
  road is not treated as damage — it does not block anything, and a town that
  repaved a street over a flower would be rewriting half of itself every sweep.

## Felling one tree fells one tree

### Fixed

- **A tree is its trunk, and nothing else comes down with it.** Chopping a tree
  used to take every tree whose leaves touched it — and in a forest, where the
  canopies all run into one another, that is the whole wood. One builder clearing
  a single oak off the line of a wall could flatten the horizon behind it in a
  tick. Now a felling follows the wood and only the wood: the logs joined to the
  stump, out to the far tip of a fancy oak's branches, and there it stops. Two
  oaks whose crowns have grown into each other are two trees and come down one at
  a time.

- **Trunks that really are one tree still come down as one.** A dark oak's
  double-thick trunk, the branch columns hung on its corners, an acacia's bend, a
  fancy oak's diagonal limbs, and two saplings somebody pushed into the ground
  side by side are all one piece of wood and all come down together — because
  they are actually touching, which is the only test there is.

- **Leaves are left to fall on their own.** Nothing rips a canopy out any more.
  Pull the logs and the leaves start dropping by themselves over the following
  minute, the way they do when you cut a tree down yourself, with the odd sapling
  and apple in the litter. A wall builder who only needs a branch out of the way
  takes the branch and leaves the tree it grew on standing.

- **A felling can no longer run away with itself.** One stroke of an axe is
  capped at a tree's worth of wood and confined to the space a tree can occupy,
  so even a wood grown into one solid tangle comes apart a tree at a time instead
  of in one enormous bite.
## A guard does not shoot through a wall

### Fixed

- **Guards no longer loose arrows at creepers they cannot see.** A guard picked
  his range off the distance to the creeper and nothing else, so a creeper on the
  far side of a barn, a hill or a closed door put him in exactly the right place
  to stand still and fire into the masonry between them — a whole fight's worth
  of arrows, none of which ever arrived. Every shot now needs a clear line from
  his eye to the creeper's, and a guard who has not got one does not take it.

- **He goes and finds an angle instead of standing there.** Rather than holding a
  range he cannot shoot down, a guard with the shot blocked walks round to a spot
  he can shoot from — the nearest one he can reach, at his usual walking pace, on
  a ring around the creeper at the middle of the band he already fights in. If
  there is genuinely nowhere, he closes to the near edge of the band and no
  further: he will never walk into the blast looking for a shot.

- **And he does not fire through a farmer.** A townsperson standing on the arrow's
  path now stops the shot. The guard waits or moves for the angle, which is what
  he should have been doing all along instead of putting a shaft through his own
  neighbor's back.

- **He watches for a second before the first arrow.** A creeper that flickers
  into view crossing a doorway used to be enough to trigger a shot at where it no
  longer was. He now needs an unbroken view of it before he draws — the same wait
  a vanilla skeleton takes — so the first arrow of a fight goes where the creeper
  is.

## You can tell a smithy from a mill without clicking on it

### Changed

- **Every trade building is now its own shape.** They were all the same box with
  two block types swapped, so the only way to know what you were looking at was
  to walk up and click the post. The smithy has an open forge bay — a whole wall
  gone, standing on two posts — with a brick chimney climbing past the ridge, an
  anvil, a slack tub and a stone floor. The mill is a two-story tower with four
  canvas sweeps turning on the wall away from its door, a millstone on the ground
  floor and a ladder to the loft. The carpentry has a lean-to down one side
  stacked with logs, a stonecutter at the saw bench, and sawn boards piled in the
  yard. The workshop has a cart-wide double door with a trade board swinging over
  it, and a loom, a smithing table and a cartographer's desk inside. The lumber
  camp is no longer a hut at all — it is a hip roof on eight posts, open on every
  side, with a woodpile, a chopping block and a fire under it.

- **And so is every store and every yard.** The mine head has a timber headframe
  standing right up through its low roof, over the shaft the miners cut, with a
  stub of rail running out of the door. The granary sits on a stone stand with
  staddle piers under the eaves and its far gable slatted open, so you can see
  the hay stacked inside from the street. The storehouse is a long low shed with
  a canopy over a three-wide door and barrels and chests against every foot of
  wall. The warehouse is the same building at twice the height, with a second
  floor and the stair up to it running up the outside. The farm keeps a scarecrow
  on its far corner and a tool shelter against the fence. The animal compound has
  a byre at the head of the first pen, with feed and a water trough under a roof.

- **All of it in the local idiom.** Every one of these is built in its people's
  own palette and roofed the way that people roofs — so a highland mill and a
  burgher mill are the same trade and obviously not the same town. Fences round
  the fields and the pens are the local wood too, instead of oak everywhere.

- **The field and the pens are exactly as they were.** Not one crop block moved
  and no compound gained or lost a pen, so nothing about what a farm or a herd is
  worth has changed.

- **Trade buildings cost more to raise, by about half.** All that shape is blocks
  somebody carries: a lowland storehouse went from about 210 blocks of work to
  about 305, a mill from 175 to 350, and the warehouse — which gained a whole
  second floor — from 350 to about 690. The lumber camp went the other way and
  got cheaper, from about 170 to 165, which is right for the shelter a town
  throws up before it owns anything.
## You can tell the hall from the inn from the library

### Changed

- **The town hall is the tallest roof on the street, and it says so.** It used
  to be the plain box in stone brick, told apart from a storehouse only by being
  bigger, with a gold block sitting invisibly at the top of its wall. It now has
  a belfry on the ridge with the gold on top of it — the thing you pick a town's
  middle out by from a hillside — a two-course stone base under walls a course
  higher than anything else on the street, an entrance three blocks wide with a
  paved landing in front of it, a bell hanging under the porch, the town's
  banners either side of the door, and its colors on a pole.

- **The market has no roof, and that is what makes it a market.** It was a
  nine-by-nine lid of spruce on eight posts, which is a bus shelter. It is now a
  paved open square: a well in the middle and six traders' pitches round the
  edge, each with its own striped awning over a barrel. Cheaper than the lid it
  replaces, and you can see the sky from the middle of it.

- **The inn has two storeys, a sign, and somewhere to put a horse.** A sign over
  the door, lanterns on posts either side of it, a stable lean-to against the
  gable end with fodder in it, and inside a taproom with a bar, tables and a
  flight of stairs up to the rooms. It is the only building in a town with an
  upstairs.

- **The library is a reading room rather than a stack room.** Books line the
  walls two courses high instead of standing in ranks across the floor, tall
  arched windows are cut into the stone, and a galleried walkway runs round an
  open middle so you can see the whole room from either level.

- **The watchtower is two storeys taller and stopped pretending to be a house.**
  Arrow loops instead of glass, a ladder up the outside instead of a stair
  eating the room, merlons round the top, and a fire burning on it that can be
  seen from the next valley. The bell is still up there and the watch can still
  reach it.

- **The hearth has a roof and the camp has its colors.** The cooking fire now
  stands under a canopy on four corner posts, with a cauldron beside it and log
  seats round it, so it stops going out in the rain. The claim post flies a
  banner, and the supply cache keeps its barrels under a tarpaulin.

- **All of it in the palette of whoever built it.** Every one of these is drawn
  in its own people's stone, timber and roofing, the same way their houses are,
  so a burgher hall and a goblin hall are recognizably the same building and
  recognizably not the same town.

- **The civic buildings cost more to build.** Roughly double for the hall, the
  inn and the library, and about three quarters more for the tower; the market
  got slightly cheaper. This is blocks somebody has to carry, so a town spends
  longer on its hall than it used to.

## Houses have roofs, and every people builds its own

### New

- **Every home has a real roof.** Cottages, houses, longhouses, crofts and the
  camp bunkhouse used to be finished off with a flat slab laid across the top of
  the walls, which is why a village read as a row of sheds however well the
  streets were laid out. They now have pitched roofs — stairs rising from the
  eaves to a ridge, the gable ends closed in, and the eaves overhanging the wall
  by a block so a house looks like it is sheltering something. The roof is
  entirely above the walls, so no room got shorter.

- **Seven peoples, and you can tell whose town you are standing in.** House
  styles are now a property of the culture rather than of the building. The
  lowlanders build oak with the frame showing through, a cobble course at the
  foot of the wall and a porch over the door. The hill folk build spruce on
  stone with a hipped roof and a chimney. The vale folk build in pale stripped
  oak with dormer windows in the front slope. The burghers build in brick on a
  stone-brick plinth under a dark oak roof, with a chimney on every house. The
  goblins build dark oak and mud under a squat capped hip. The orcs keep their
  flat roof and get battlements round it, because that is their look and not an
  oversight.

- **No two houses on a street are the same house.** Which side the chimney
  climbs, whether the windows have shutters, and how close together the extra
  panes sit are all decided by where the building stands. A row of cottages is a
  row rather than one cottage repeated — and a given house is the same house
  every time you come back to it, so nothing a repair crew looks at ever
  changes under them.

- **Windows you can count.** A building used to get one pane on each wall. It
  now gets a row of them, spaced between the timber uprights, plus shutters
  either side where the style calls for them. Glass costs the town nothing, so
  this is free light.

- **The croft keeps its L and gets a roof that bends round it.** The one
  building in the game that is not a rectangle is roofed by measuring, so the
  hip wraps the corner and puts a valley in the crook by itself instead of
  stepping over the yard.

### Changed

- **Houses cost more to build, by roughly three quarters.** A roof is blocks
  somebody has to carry: a lowland cottage went from about 190 blocks of work to
  about 330, and a longhouse from about 440 to about 730. A new settlement's
  charter grant still covers its camp comfortably — the bunkhouse is the only
  home a founding party raises, and it takes about half the timber the charter
  hands over.
## A town the world put there is a town when you find it

### Fixed

- **A village you discover already has its streets.** A settlement raised by
  world generation was written into existence complete — fourteen buildings, a
  hundred and twenty people, full granaries — and not one yard of road. It could
  not have had any: a generated town is watched from its first moment, because it
  is raised on account of you walking up to it, and a town somebody is standing in
  has no clock. So it planned one track a step and walked out one a step, in front
  of you, for several minutes. A town that has stood for a generation now arrives
  with its roads already walked: every door on the network, every street of its
  arrangement routed round the hills it actually sits in, and every stretch the
  ground will take already trodden. A ring village measured forty-two ways at
  three hundred steps, none of them waiting.

- **And what it builds after you find it is still built by hand.** The roads a
  discovered town arrives with are a record of what it did before you got there,
  the same as its houses. The first cottage it raises with you watching gets its
  lane walked out by a builder, one stretch at a time, exactly as a chartered
  town's does — nothing opens itself in front of anybody.

- **A forester you find has a wood to work.** A generated town's lumber camp
  stood in an empty field. The stand it was owed was planted at the moment the
  camp was first drawn, which is the moment its own chunk arrives — the far edge
  of what you can see — while the trees belong in a belt further out again, in
  ground nobody has loaded yet. Nothing could be planted there, and the debt was
  struck off in the same breath, so every single generated town lost its wood at
  exactly the moment it was supposed to get one. The camp now keeps the debt until
  the whole of its woodland can be seen, and plants the full dozen when it can.

- **A building moved off bad ground takes its road with it.** A town written down
  before anybody looked at the ground may still shift a plot off a river on the
  step it is first seen. The way that had been run to it stayed pointing at the
  plot it left, and the plot it took never got one. It is planned again now.

- **A street too steep to walk no longer holds up the ones behind it.** Drawing
  the roads of a town you have just arrived at stopped dead at the first stretch
  the ground had refused, and everything past it was laid at one a second — so a
  village with forty ways drew them one at a time while you stood in it. The
  refused stretch is stepped over.
## The town goes to bed

### New

- **Real beds, and people asleep in them.** Every home in the mod was furnished
  with a couple of blocks of white wool standing in for a bed, because a bed is
  two blocks that have to agree with each other and nobody had worked out how to
  lay one a block at a time. Cottages, houses, longhouses, crofts and bunkhouses
  now have actual beds — both halves, the right way round, pillow to the wall —
  and they survive being built by hand because a builder lays block states
  exactly as they were drawn.

- **One bed per head.** A home has exactly as many beds as it is allowed to
  house: three in a cottage, four in a house, six each in a longhouse, a croft
  and a bunkhouse. There is no longer a house where somebody has the right to
  live and nowhere to lie down. Each resident has their *own* bed and goes back
  to the same one every night.

- **Beds are the color of the people who sleep in them.** Lowlanders under plain
  white, townsfolk under red, vale folk under woad blue, hill people under undyed
  brown, goblins green, orcs black. It is the only piece of furniture there is one
  of per person, so it is the cheapest thing in a room to say whose room it is.

- **Night means something now.** At dusk everybody but the watch walks home, goes
  to their own bed and lies down until dawn. A sleeping settler is genuinely
  asleep: nobody steers them, no work gets handed to them, and they do not count
  as a spare pair of hands to anything. The watch keeps the night as before.

- **Anything that matters still gets them up.** The bell, something hostile
  wandering into view, a creeper, or being too weak with hunger to sleep it off —
  any of those and the house empties. Merely walking a loaf home does not; a
  settler who is a bit peckish every evening is left to sleep.

- **Nothing breaks when there is no bed.** An idler with no home still turns in at
  the town center. A house from an older save still furnished with wool, a bed a
  player has broken, and a household more crowded than its house has beds for all
  end the same way they always did: the settler stands at home through the night.
  Nobody goes hunting the village for a spare mattress.

- **A generated town is built in the arrangement the world chose.** The layout
  was handed to the town after its buildings had already been stood on the
  plots of its people's default arrangement, so the streets were drawn for one
  shape around houses sited by another. Towns generated from now on are laid
  out in the arrangement the config weights picked; towns already generated
  keep their ground.

- **No shutter across a doorway.** A window one block from the door hung its
  shutter leaf in the cell outside the doorway. The leaf is skipped wherever the
  wall behind it is a gap.

---

## A town you founded gets its roads walked out

### Fixed

- **A settlement you founded now has roads.** A camp raised from a charter
  planned a track from every new door to whatever passed nearest, and then left
  almost all of them as lines on a plan. Nobody ever walked them: the town you
  are standing in has no clock — that is the rule, and it stays — and the only
  moment it would spare anybody for a street was the single step between one
  building being finished and the next being ordered. A growing town plans two
  stretches for every building it raises and was walking out about one, so the
  untrodden half grew for as long as the town did. Fifty steps into a founding,
  a camp had four of its eight stretches; two hundred steps in, seventeen of
  twenty-seven. It now has all of them.

- **A party of four puts three on the bunkhouse and one on the road.** Shelter
  and stores still come first, and that has not been loosened — it is now a rule
  about the *last* pair of hands rather than about all of them. A settlement down
  to one able builder still keeps them at the building site and its streets wait,
  exactly as before.

- **Only the roads get this.** The palisade still waits for the build queue to
  clear, because a post is a plank the next building is owed and a ring is
  hundreds of them. A track costs the town nothing but somebody's afternoon.

- **The paver is left alone while they are out there.** Whoever is spared is off
  the building site for as long as the work lasts, so the site and the foreman
  are not steering the same person in two directions, and the day's routine no
  longer walks them back to the square halfway down a lane.

---

## The watch carries a sword and a bow, and shoots the creepers

### New

- **Every guard is holding a weapon, from his first day.** He used to stand
  around empty-handed and only reached for a blade when something hostile
  wandered into view — and then only if the smithy had made one, so most towns
  had a watch of unarmed men who looked exactly like farmers. A guard now carries
  a **wooden sword and a bow** the moment he takes the post, out of nobody's
  stores, whether or not there is anything to fight. It is the watch's own kit,
  and a town that has never built a forge still posts somebody who is armed.

- **The bow is visible in his off hand.** Sword in the leading hand, bow in the
  other, and they swap over when the fight calls for it, so you can tell a guard
  from a villager at a glance and tell what he thinks of what he is looking at.

- **Creepers are shot, not stabbed.** A guard who sees one draws the bow, walks
  to a range of **eight to fourteen blocks** — outside the blast, inside a
  reliable shot — and looses an arrow a second until it is dead, four or five of
  them. If the creeper closes inside eight he backs away, still shooting. This
  replaces the old hit-and-run dance, where he swung once, ran, waited out the
  fuse and walked back in: it worked, and it looked like a man who had never
  been told what a bow is for.

- **Guards do not run out of arrows.** There is no arrow store, no fletcher, and
  nothing to haul. That is a deliberate simplification: an ammunition economy is
  a lot of new machinery to answer a question nobody was asking.

### Changed

- **The smithy now buys an upgrade rather than a weapon.** The first iron sword
  off the rack replaces a guard's wooden one, once, and stays with him. In
  damage: bare hands **4** a swing, the watch's wooden sword **5**, the forge's
  iron sword **7**. A forge is still worth building — it is just no longer the
  difference between armed and unarmed.

- **A guard who stops being a guard hands the kit back.** The wooden sword and
  the bow vanish with the job; the iron sword goes back on the rack for the next
  man, including when a body is put away because you walked out of sight of the
  town. The town's weapon count stopped quietly leaking.

- **Everything else is still met with the sword**, unchanged — walked up to and
  hit, never backed away from. A guard with the bow up who is charged by a zombie
  draws the sword again as it comes into reach.

- **Nothing moves faster than a walk**, retreat included. That rule is untouched.

---

## `/civ info` no longer sends you to a command that does not exist

### Fixed

- **The empty-world message pointed at `/kingdoms found`**, which was never the
  command; it is `/civ found <name>`. The same wrong name stood in three other
  "no settlement nearby" refusals. All four now say `/civ found`.

---

## Grain is not bread until somebody bakes it

### New

- **Wheat is cut as grain, and grain is not food.** A farmer swinging at a ripe
  block used to produce a loaf of bread on the spot, which quietly made the
  sickle the town's bakery. A cut block is a sheaf now. It sits on the farm's
  own shelf until somebody carries it in, and nobody can eat it on the way.

- **Towns bake.** Grain becomes bread at one building: the **mill**, if one
  stands and a miller works it, and the **hearth or granary** otherwise. The
  hearth needs no trade at all — a camp bakes at its fire, and whoever is there
  does it — and turns out four loaves a step, one for each sheaf. That is a
  fifteen-person village fed four times over, and comfortably more than four
  fields can bring in, so the ground and not the oven is still what limits a
  town's food.

- **The mill earns its fifty per cent.** It used to add half again to the whole
  town's harvest wherever that harvest happened to be lying. It now grinds
  actual grain that somebody actually carried to it, six sheaves per miller per
  step, at three loaves for every two sheaves — nine loaves a step against a
  hearth's four, out of the same wheat. A mill nobody hauls to grinds nothing.

- **A town with full sacks and no oven starves, and you can see why.**
  `/civ info` grew a line: `grain: G on farms, F at the mill/granary · bread: B`,
  and it says `NOBODY BAKES` when the settlement has raised nowhere to do it.
  A hundred sheaves in the fields and an empty larder is now a readable fault
  with a building-shaped fix rather than a mystery.

### Changed

- **Farmers carry grain to the oven instead of loaves to the larder.** Same
  walk, same full load of twelve, same rule about not leaving the rows for less
  — the destination moved. Traders still take bread from the larder to the
  stall, and eating is unchanged: bread only, out of the pantry, the larder, the
  stall, and finally the fields.

- **A seeded town arrives mid-harvest.** Its fields start with a full shelf of
  grain rather than a shelf of loaves, because a field is where wheat is cut and
  not where it is baked. The granary still arrives a quarter full of bread.

- **The flour is not walked back from the mill, deliberately.** The two-leg
  design — grain out, bread home — was built and then measured, and it strangled
  every town below about forty people: one miller grinds nine loaves a step and
  one spare pair of hands moves about one, so the bread stacked up at the mill,
  the stones stopped for want of shelf room, and the granary drained while the
  town sat on four thousand loaves it could not reach. What the stones make now
  joins the town's larder where it is made. The leg that is real is the one that
  decides anything.

### Fixed

- **The animal farm has stopped pretending to be a wheat field.** Every place
  the food chain looked for a farm matched the blueprint path's *suffix*, and
  `kingdoms:animal_farm` ends in "farm" — so since the day the compound was
  added, every pen of sheep and cows has been counted as a crop field: handed a
  seventy-one-block ripeness ledger it has no wheat for, sent farmers, and
  credited with a harvest out of nowhere. Buildings are matched by role now. A
  town with a compound loses a phantom field's worth of food it was never
  growing; the fix for that is a farm.

### Notes

Three unwatched survival runs at the shipped 0/0 yields, on ground with nothing
to forage, nobody ever visiting. No deaths and nobody starving in any of them.
Grain and bread split out, at the same marks as last time:

| Run | Step | Pop | Bread (of it, larder) | Grain on fields | Grain at the oven |
|---|---|---|---|---|---|
| Camp of four, one field | 400 | 4 | 412 (400) | 6 | 4 |
| | 800 | 4 | 756 (740) | 9 | 0 |
| | 1500 | 4 | 1012 (1000) | 40 | 200 |
| Camp of four, timber and stone given | 400 | 9 | 620 (589) | 45 on 3 | 0 |
| | 800 | 39 | 1266 (998) | 228 on 6 | 182 (mill) |
| | 1500 | 80 | 1438 (998) | 476 on 12 | 182 (mill) |
| Seeded village of eight | 400 | 18 | 664 (597) | 120 on 3 | 192 (mill) |
| | 800 | 18 | 665 (599) | 120 on 3 | 192 (mill) |
| | 1000 | 18 | 664 (597) | 120 on 3 | 192 (mill) |

The grain that banks up at the late marks is not a jam in the chain: those towns
have filled their larders to capacity, the oven stops when there is nowhere to
put a loaf, and the sheaves back up behind it — first at the mill, then in the
fields. The way out of it is a bigger granary, which is the correct thing for a
town with more wheat than shelves to be short of.

The rough-ground fixture is the measurement that settled the mill's design. With
the flour walked home it reached eighteen people and stopped there for good; with
the flour baked into the larder it reaches thirty by step five hundred and a
hundred and twenty-four by twelve hundred.

---
## The wood is a wood, and the mine runs out

### Changed

- **A lumber camp's timber is the trees it is actually standing in.** Wood used
  to be a percentage of an imagined felling, and the percentage ships at zero —
  so a town you walked away from brought in no timber at all, jammed its build
  queue on the first cottage it could not pay for, and never raised another
  building as long as it stood. Every camp now keeps a stand: so many trees
  standing, so many saplings coming up. Lumberjacks fell four logs each a step
  out of it, save a sapling off every fourth log, and plant them back on ground
  they have cleared. It is the same ledger whether you are watching or not.

- **A mine's stone is the rock actually under it, and there is a finite amount
  of it.** A mine is sized from the ground when its chunks are first read — a
  mine in a mountainside is worth a great deal and a mine sunk into a superflat
  is worth almost nothing — and its miners cut six blocks a step out of that,
  with an ingot's worth of iron for every six. **When the seam is empty the mine
  is cut out.** It stops for good, `/civ info` says CUT OUT, and the town's
  history records the day. Nothing grows back down there.

- **`/civ info` says what the trades are standing on.** Two new lines per town:
  the trees left in each camp's claim and the saplings coming up behind them,
  and the blocks left in each mine. A camp on open grass with no seed in the box
  reads BARE — which is a real state a town can be in, and the honest answer to
  "why is my town not building". Ground nobody has loaded says so too, rather
  than reading as felled.

- **Walking up to a camp or a mine counts what is really there.** The trees are
  their own truth: on the step a player arrives the claim is re-counted and the
  world's number wins, whatever the ledger believed. Real axes and real picks
  debit the same ledger block by block, so watching a town work no longer pays
  it twice — and no longer pays it at all for standing still.

- **Being watched floors nothing here either.** The twelve-step grace that put
  the clock back into a watched camp or mine is gone, the way it went from the
  fields. In front of a player, only real work counts.

- **The yield tables now govern nothing.** `economy.unwatched_yield_percent` and
  `economy.watched_floor_percent` still exist and still load, and there is no
  longer anything in the game that reads them: food went first, and timber,
  saplings, stone and iron have followed. Nothing in the mod is conjured any
  more. Old config files load unchanged and simply have no effect.

- **A stand and a seam survive a save.** Both go into the world file. A save
  written before today comes back *uncounted* rather than empty — an old camp
  finds out what it stands in the day somebody loads its ground, and an old mine
  is unsurveyed rather than spent.

- **Timber is scarcer than it was, everywhere.** A claim renews itself out of
  saplings that take an in-game day to come up, so a town is timber-poor for its
  first few hundred steps and then compounds as its replanting comes in. Towns
  build more slowly early and want more camps than they used to.

### Notes

- The numbers, all derived off the watched worker rather than picked: a log is
  20 ticks for a settler with an axe (hardness 2, iron axe speed 6, doubled by
  `Excavation.LABOR_FACTOR`), so a hundred-tick step is five logs of chopping
  and four after the walk between trunks. A block of stone is 16 ticks, so six
  and a quarter a step, and six after the shuffle to the next face. A tree is
  six logs — a vanilla oak trunk is four to six, and one oak in ten comes up
  fancy at twenty-odd. A sapling comes up in about 24,000 ticks, 240 steps: one
  random tick per 1,365, one advance in seven, two advances to a tree, plus the
  nights it sleeps through.

- Measured at the shipped 0/0, unwatched, nobody ever visiting, on ground with a
  stand of 12 and a seam of 2,000. A founding party of four: a VILLAGE of 7 with
  11 buildings at step 400 (267 logs felled, 34 trees coming up, 990 left in the
  seam); the same village with 14 buildings at 800 (793 felled, seam 726); and a
  TOWN of 45 with 37 buildings and seven fields at 1500, having cut 3,857 logs
  and dug the mine out entirely. It used to be four people and one field
  forever.

- A seeded village of eight on the same ground: 13 buildings and 14 people at
  400, a TOWN of 16 at 800, and 42 buildings, 65 people and ten fields at 1500,
  with the mine cut out under it.

- A camp with nothing standing and no seed in the box brings in nothing, at
  either fidelity, for as long as that is true. That is not tuned around.

## Bread is grown, not granted

### Changed

- **Food has left the abstraction tables, and there is no setting that puts it
  back.** Timber, stone, iron and saplings still answer to
  `economy.unwatched_yield_percent` and `economy.watched_floor_percent`; a loaf
  does not. A field is sown, ripens and is cut, and a town eats whatever came
  off it — whether or not anybody was standing there to see it. An old config
  file that still names food in either table loads perfectly well; the line is
  simply dropped the first time the game reads it.

- **A town orders its next field while the granary is still half full.** The
  rule is one farm per fifteen mouths, rounded up and never nought, and it is
  derived rather than guessed: two hands to a field, a loaf a step each, and a
  loaf carries somebody fifteen steps — so a field flat out feeds thirty, and
  the planner plans on half that for the walking, the hauling and the bad days.
  A founding party of four wanted no farm at all under the old rule, and a
  homestead could grow from four residents to twenty on the single field its
  charter named. A field is also now the one thing allowed past a job that has
  visibly stopped, so a town can no longer want a farm silently forever behind
  a cottage it has no timber for.

- **Somebody actually works the field.** The staffing table wanted a farmer per
  five residents and had no idea what was standing in the town, so a party of
  four with a farm in the middle of it wanted none — and got none. Measured
  before the fix: the farm went up on step 30, nobody was ever sent to it, and
  the party ate its founding provisions down to nothing and was extinct by step
  500 with a working farm twenty paces away. Every field now claims two pairs
  of hands the moment it stands, over and above whatever the population asks
  for, and the town has them out there within twenty steps.

- **A slower founding, deliberately.** A town that raises a second field on the
  way up spends builder-steps on it like anything else, so the ladder to a
  walled town takes about a quarter longer than it did. That is the price of a
  founding that feeds itself.
## The field is the harvest

### Changed

- **An unwatched town farms, and the farming is real.** Yesterday's change left
  every town you walked away from starving, because food was a percentage of an
  imagined harvest and the percentage now ships at zero. It is not a percentage
  any more. Each farm knows the field it actually stands in -- 71 blocks of
  wheat, counted off the blueprint the builders lay -- and that field ripens on
  Minecraft's own clock, about thirty-one minutes from planting to mature,
  whether or not anybody is there to see it. The town's farmers cut what has
  ripened and put the rows back to seed. No setting anywhere scales it. A town
  nobody has visited for a week has been farming that whole week, and there is a
  field's worth of wheat behind every loaf in its granary.

- **A field is worth what a field is worth.** Two farmers working one eleven-by
  -eleven make a bit under a loaf a step, which feeds about thirteen people. It
  used to be two loaves a step regardless, which fed thirty. Towns need more
  fields than they did, and a town with one field has a ceiling.

- **A full farm stops the harvest and the wheat stays standing.** Nothing rots
  and nothing is lost -- the field simply waits, ripe, until a farmer carries a
  load to the granary and comes back for the rest. If you find a town with
  hundreds of loaves banked in its fields and an empty granary, that is hauling
  and not growing, and now you can see it.

- **Being watched no longer floors anything.** A watched farm used to fall back
  to the clock after twelve steps without a real harvest, so that looking at a
  town could never starve it. Gone. A field whose farmers cannot reach it grows
  a fine crop of ripe wheat that nobody cuts, and you can walk over and see
  exactly that. Where there is a hand there is no clock, with no exceptions
  left.

- **The crops agree with the ledger when you walk up.** The first moment a
  player comes within range of a farm, its wheat is set to show what the town
  has actually grown -- so many blocks mature, the rest young. Walk away for a
  session and come back, and the field looks like the harvest the town has been
  living on rather than whatever the chunk loader last happened to leave there.

- **A field's growing survives a save.** How ripe a farm is now goes into the
  world file. A worldgen village can sit unvisited across a dozen sessions and
  keep the harvest it grew in each of them.

- **A building too big for its plot backs off the road properly.** A compound
  offered a street plot narrower than it needs used to be shuffled part of the
  way out of the carriageway and left standing in it. It now retreats the whole
  way, or stays where it was -- never half.

### Notes

- Measured at the shipped defaults, nothing conjured, nobody ever coming to
  look, on ground with nothing edible on it. A founding party of four: step 400
  a VILLAGE of 4 with 63 loaves, step 800 a VILLAGE of 4 with 148, step 1500 a
  VILLAGE of 4 with 408 -- all of it off one field. It used to be out of bread
  at 400 and empty at 800.

- A seeded village of eight with three fields: 18 people and 739 loaves at step
  400, and exactly the same at 800 and 1000. It fills its houses and then sits
  there, fed.

- The same founding party given fields to work and timber to build with:
  a TOWN of 9 at step 400, 38 at 800 and 81 at 1500, with 958 loaves. That is
  the shape the food chain has when nothing else is in its way.

- **What is still in the way, and it is not the field.** The camp of four never
  orders a second farm -- handed all the timber and stone it can use it raises
  eighteen buildings by step 500 and exactly one of them is a field. And at the
  shipped defaults it cannot build at all, because an unwatched lumber camp
  still brings in no timber. Both are being looked at separately. Neither is
  fixed by making wheat grow faster, and neither was papered over here.

---

## No more supplies out of nowhere

### Changed

- **Nothing is conjured any more, by default.** The two abstraction knobs that
  landed two days ago shipped at seventy percent unwatched; both now ship at
  **zero**. A town you are not watching still runs -- it builds, hauls, eats,
  spends what it holds and its people go on living their lives -- but it gains
  nothing it did not already have. Every log, every block of stone and every
  loaf now has to come from a real hand, a real harvest, or wild food actually
  growing where the town stands. Set `economy.unwatched_yield_percent` back to
  70, or to 100, if you would rather the towns you left behind kept producing.

- **Foraging only turns up what is actually growing there.** A camp used to
  conjure a meal per three pairs of hands every step, anywhere: a party on bare
  superflat ate exactly as well as one in a berry-thick taiga, and a party in
  the middle of a desert lived on sand indefinitely. Foragers now bring back
  berry bushes, mushrooms, melons and pumpkins, wild crops nobody planted, and
  apples from oak canopy -- what is really within reach of the camp, and no
  more. Cactus is not food. A camp in a desert or on superflat forages nothing
  at all and must get a field in the ground or die trying.

- **And the patch runs out.** Everything picked is booked against the ground it
  came from and grows back at roughly one meal every four steps, which is about
  what four people eat. A camp in a wood can sit still and just about live off
  it; a camp trying to grow on wild food strips the wood and then goes hungry.

- **Worldgen towns arrive holding what their own buildings could hold.** One
  harvest sitting in each standing field, a granary a quarter full, and one
  building's worth of timber and stone per lumber camp and mine -- enough to
  mend a wall, not to raise the next thing on the list. No iron unless a smithy
  stands, and never any tools, weapons or armour: those are forged out of iron
  somebody mined. A town with no field, no granary and no camp of its own keeps
  a charter party's kit, because with none of those it is a charter party.

### Notes

- Measured at the new defaults, with nobody ever coming to look. A founding
  party of four on ground with nothing edible on it reaches step 400 still four
  strong, out of bread, two of them too weak to work, and is gone by step 800.
  A seeded village of twelve reaches step 300 with one loaf between them, four
  weak and two starving, and is empty by step 600. The same two runs at
  seventy percent unwatched end as a town of thirteen with 196 loaves, and a
  town of forty-two with 738.

- That is the setting doing exactly what it says, not a bug: at zero, a town
  nobody ever visits eventually ends. Foraging keeps a camp alive in a wood
  while it is still a camp -- past HOMESTEAD, pioneers stop foraging and the
  fields have to carry it, and unwatched fields at zero grow nothing. If you
  want abandoned towns to persist on their own, the knob is the answer and it
  is one line in the config.

- The free first producer building (`isFreeToBuild`) and the `/civ found`
  charter kit are untouched. A new camp still gets its start; what it does not
  get is a supply of food and timber from nowhere for the rest of its life.
## A forester you find already has a wood to work

### New

- **A town you discover standing has trees around its lumber camp.** Every town
  that was there before you were — the ones world generation puts on the map, and
  the ones seeded by command — used to raise a lumber camp on whatever the
  terrain generator happened to leave there, which on a plain is grass. Its
  lumberjacks walked out, found nothing standing, and the town's only timber came
  from a clock ticking somewhere out of sight. Now the wood is actually there: up
  to a dozen full-grown trees, planted on the ground the camp claims, the day the
  town is first drawn.

- **They are the trees the country grows.** Spruce in the taiga, acacia on the
  savanna, jungle in the jungle, birch in a birch forest, dark oak under a dark
  forest, cherry in a grove, and oak everywhere else — grown with the same
  generator the world uses, so a seeded wood looks like it has always been there
  rather than like something that was placed.

- **A camp on bad ground gets a smaller wood, not a levelled one.** Nothing is
  filled, cleared or flattened to make room for a tree. A square that turns out
  to be lake, cliff, bare stone or already under a canopy is simply passed over
  and the next one tried, so a camp on a lakeshore ends up with the stand a camp
  on a lakeshore would have.

- **The trees stand where the forester will replant them.** Five blocks apart, so
  two canopies do not grow through each other and a lumberjack who has felled one
  is a short walk from the next; and out past the houses rather than between them,
  because a tree in the village blocks the paths and is one the forester is
  forbidden to put back. A seeded camp therefore claims its woodland a little
  wider than a new one does, far enough out to have somewhere to replant.

- **A seeded camp keeps saplings on hand.** Eight of them, on the shelves nearest
  the camp — enough to put back what the stand loses while the wood renews itself.
  It is a possession of a camp that has been standing for years, not something the
  town produced.

- **A town you found yourself is unchanged.** The four settlers off a charter get
  the country they walked into, and their lumberjacks clear it and plant saplings
  exactly as before. Only a town that skipped the road is handed a wood it is
  supposed to have already had.

---

## Nothing builds itself while you are standing there

### Changed

- **A town you are watching builds with hands or not at all.** Walls, houses,
  streets and repairs all had a way of falling back to the clock while a player
  stood in front of them -- after a dozen patient steps, or whenever the crew
  happened to have nobody in the world just then: capped out of bodies, not yet
  spawned, too hungry to work, or simply on the far side of the village. The
  result was a building that assembled itself in front of you. That fallback is
  gone entirely. If anybody is inside the observed radius of the work, only real
  hands laying real blocks move it forward.

- **Judged at the work, not at the town.** A plot, a wall stretch or a lane far
  enough out that nobody is near it is still built on the clock, even while the
  square is full of people. Standing in the market does not freeze the outskirts.

- **A town that grew while you were away is untouched.** With nobody in range
  the clock runs exactly as it always has, and what it finished still appears
  whole when you walk back into the chunk.

- **`/civ info` says why a site is not moving.** A watched build with nobody on
  it now reads `WAITING ON HANDS` and names the reason -- no builder has reached
  the site, or none is fit to work -- instead of sitting at nought per cent with
  no explanation.

### Notes

- The consequence is real and intended: a watched site whose builders can never
  reach it never finishes. That is a navigation problem to fix, not something to
  paper over with masonry out of the air. The idle report is there to point at
  the culprit.
## A town with more enemies than guards now goes and finds some guards

### Changed

- **A frightened town musters.** The staffing table wanted one guard per eight
  residents and had no idea what was standing in the treeline, so a village of
  nine kept exactly one guard and met a raid of six with him. A town now compares
  what it is afraid of against the watch it has, and while the threat is the
  bigger of the two, somebody takes up the sword every step until the numbers
  meet. One person a step, as with every other change of trade — you can watch it
  happen rather than finding a militia where your farmers were.

- **The number it recruits to is the number it dies by.** A guard turns back two
  of a raid, so the watch a fright calls for is half the threat, rounded up: three
  guards for the tier that empties the streets, five for the worst thing on the
  ladder, eight for the largest raid there is. A town that reaches its number
  genuinely survives the raid that number was read off — the two used to be
  separate sums that could quietly disagree. Standing watchtowers do not count
  toward it, so a town keeps a margin in hand for the day one gets knocked down.

- **The volunteers come from wherever they are least missed.** Idle hands first,
  then whichever trade is standing furthest above the minimum the table says it
  cannot do without. Never a guard, never the last farmer, and never the last
  builder — a town that answers a raid by conscripting its only field hand wins
  the raid and starves in the fortnight after. Hunger still outranks fear: a
  starving town crystallizes its farmer before it raises its militia.

- **A frightened town builds the watchtower next**, and the smithy too if it has
  not got one — the forge is what puts a weapon in the hands it is busy
  recruiting. This is the only thing allowed to jump the priority table, and it
  does not interrupt anything: whatever is half-built gets finished first. Only a
  famine is urgent enough to drop the job in hand.

- **It all lapses on its own.** Threat fades a point a step, and the moment it
  falls back under the watch the ordinary table resumes. Nothing has to be
  switched off, and the guards already raised drain away no faster than surplus
  guards ever did.

- **`/civ info` says so.** A town that is short shows a `garrison` line — how many
  guards it has, what it is afraid of, and how many it wants. A town that is not
  short says nothing, because there is nothing to say.
## A dead town's roads stay broken

### Fixed

- **A village with nobody left in it no longer mends its own streets.** The
  clock had been taught to stop for a town whose last resident is buried, but
  the road sweep never heard about it: it kept walking the network a stretch at
  a second, putting the gravel back wherever grass had grown over it or a
  player had dug it out. Laying a new road and patching an old one are the same
  act down in the blocks, and nothing above them knew the difference. Now it
  does. A plague village's roads go the way its fields do -- over, under, and
  eventually gone -- and the place finally looks abandoned instead of
  maintained.

- **A road a dead town built before it died is still shown.** The rule cuts
  between a record and a repair, exactly as it does for buildings: a town that
  grew and walked its streets out while you were away still lays them on the
  ground the first time you arrive, even if everyone died in the meantime.
  After that they are on their own.

- **Restarting the server no longer re-lays every road in every town.** Which
  streets had actually been put down was remembered only until the world
  closed, so each start quietly drew the whole network again. For a living town
  that was invisible work; for a dead one it was the bug above, coming back
  every time you loaded the save.
- **And so does its wall.** The sweep that keeps a palisade whole re-placed
  any post missing from a dead town's ring exactly as it does for a living one.
  It now stands aside for a town with nobody in it. A ring that was staked but
  never stamped into the world waits on paper until somebody lives there again.


---

## How much of a town's income is imaginary is now yours to set

### New

- **A table for how much an unwatched town produces.** Every resource gain in
  the mod was abstract when nobody was near enough to see it -- the only way a
  town you walked away from keeps existing, but "all of it, always" was never a
  decision anybody made. `economy.unwatched_yield_percent` now sets the share
  the clock credits, one entry each for wood, stone, food, iron and saplings,
  and defaults to **70**. A hundred is exactly the old behavior. Zero means a
  resource is only ever gained by work somebody could have stood and watched.

- **A table for what a watched building earns when nobody is working it.** A
  camp that has felled its last tree, a mine on flat grass with no shaft sunk,
  a farmer who cannot path to the field: after twelve quiet steps the clock used
  to step back in at full rate, so that being looked at could never starve a
  town. `economy.watched_floor_percent` sets that floor and defaults to **0** --
  in front of a player, only real work counts. Raise it to 100 for the old
  behavior.

- **`/civ info` says which numbers a world is running**, on one line when they
  are uniform and per resource when they are not.

### Changed

- **A town you live in now outproduces one you abandoned.** At the defaults an
  unwatched camp brings in seven logs where it used to bring eight, and a
  watched one whose crew has stopped brings none at all. A founding party left
  alone for four hundred steps still climbs the whole ladder and eats well
  doing it -- it simply arrives with thirteen people rather than eighteen.

### Notes

- Percentages are carried between steps rather than rounded off each time, so a
  field earning a single loaf a step banks seven loaves in ten at 70 rather than
  none at all forever. The carry is counted in hundredths as whole numbers,
  because ten steps of seven tenths in binary floating point comes to
  6.999999999999999 and would have quietly cost every town a loaf.

- Work a player can actually watch is never scaled by either table, and neither
  is anything that is not a gain: hauling between stores, the timber refunded by
  a pulled fence post, the forge turning iron into tools, or the caravan's
  barter at the inn -- that one buys its iron with real loaves at a stated rate,
  and shaving the return would change the exchange rate rather than reduce the
  abstraction. Foraging is scaled, but always at the unwatched rate: no forager
  has ever been embodied, so a floor of zero would starve a founding party in
  front of the player who came to watch it.
## Fear makes a settler think, not sprint

### Changed

- **Nobody moves faster because something scary is nearby.** A settler who
  spotted a creeper used to break into a run more than twice their working
  pace, a town under alarm ran for its doors half again as fast as it walked to
  the fields, and a guard charged. All of that is gone. There is now one
  walking pace, and every citizen moves at it in every state -- working,
  called in by the bell, closing on a hostile, or running from a creeper. It is
  the fastest walk the town already had, so nobody is slower than you have seen
  them; what you no longer see is anyone who was plainly holding out on you all
  morning.
- **Settlers see a creeper coming much sooner.** The notice distance went from
  ten blocks to eighteen -- worked out from how far somebody walking actually
  needs in order to still be outside the blast, given a second to react and a
  path that bends around a fence before it points away. Ten was short, which is
  why a fleeing farmer needed to sprint to survive at all.
- **A frightened settler runs somewhere, not just away.** Where their own house
  is close enough to be shelter, they head for that door -- the same door the
  alarm sends them to -- instead of picking a random direction and ending up
  alone in a field. If the way home would take them past the creeper, they do
  not take it; they get clear first.
- **Work stops when the danger arrives, not when the block is finished.** A
  miner, lumberjack, digger or builder with anything hostile inside the notice
  radius downs tools that moment rather than completing the swing in hand, and
  is not sent back to the workplace next second while the thing is still
  standing there. They go to their door and stay there until the ground is
  clear.
- **What the alarm decides is unchanged.** Wary still walks people indoors and
  alarmed still sends them home immediately. Only the speed it used to hand out
  is gone.
## A dead town is a dead town

### Fixed

- **A settlement whose people are all dead no longer builds.** An emptied town
  went on opening its streets, planting wall posts, trading loaves for iron at
  its inn and generally getting on with the week, with every last resident in
  the ground. Now a town with nobody alive in it does nothing at all: no
  building rises, no road opens, no post goes in, no store grows, and the
  ledger sits exactly where the last person left it.
- **The town is left standing, not swept away.** A plague village keeps its
  buildings, its walls and its roads, and it is neither demolished nor marked
  abandoned. Buildings the simulation had already finished still appear when
  you walk into their chunks — that is a record of work done while somebody was
  alive to do it, not new progress — and the town's memory of danger still
  fades. Found or migrate somebody into it and it picks up exactly where it
  stopped.
- **It says so once.** The first step a town is found empty it reports that it
  has no one left, and then it is quiet.

---

## A world of crossroads towns

### Changed

- **A fresh world generates the burghers' crossroads towns instead of the
  Norman green.** `worldgen.arrangements` still holds one arrangement at 100
  and the rest at 0; the one is now `crossroads`. The green stays the best
  measured shape and is not going anywhere -- this is the next people getting
  their turn in front of a player. Worlds already generated keep whatever their
  config file says, since the server config is written once; set
  `crossroads = 100` and `green = 0` there to follow.

### Notes

- Chosen by measurement, all thirteen arrangements grown from a camp on smooth
  ground (700 steps) and on the rough recorded seed (500 steps). Buildings per
  thousand square blocks of the town's box, doors more than eight blocks from
  an opened road, and share of planned road opened:

  | arrangement | smooth density / stranded / opened | rough density / stranded / opened |
  |---|---|---|
  | green | 1.09 / 6 / 94% | 1.07 / 0 / 90% |
  | crossroads | 0.67 / 3 / 92% | 0.69 / 3 / 86% |
  | thorp | 0.56 / 2 / 89% | 0.75 / 4 / 84% |
  | ring_streets | 0.56 / 3 / 95% | 0.55 / 3 / 83% |
  | radial_concentric | 0.53 / 3 / 95% | 0.71 / 4 / 81% |
  | crescents | 0.73 / 5 / 92%, 353 blocks out | 0.93 / 5 / 80% |
  | high_street | 0.42 / 11 / 81% | 0.34 / 2 / 80% |
  | bastide | 0.78 / 24 / 73% | 1.04 / 8 / 78% |
  | stronghold_streets | 0.85 / 26 / 70% | 0.88 / 7 / 61% |

  The four lattices are denser than any of these and have no roads. Bastide
  and the ruled gridiron strand a quarter of their doors on smooth ground: the
  block interiors are planned frontage the road opening never reaches, which
  is the thing to fix before either is the default.

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
- Measured in a world on the rough test seed, 8675309, grown unwatched to
  TOWN and past it: no wall at step 100 (the command answers "has not staked a
  wall yet"), one staking at step 172 of 688 posts, and no second line through
  step 711 -- the shape the rules promise. The stage at the moment of staking is
  not in that log (the town history keeps only its last few events); that claim
  rests on WallRestakeTest and the code path, and is stated as such.
- With a player standing in the same town for four minutes: the wall went from
  563 posts standing to 646 -- the builders planted the 83 that were missing,
  and the count of posts shut by a building fell from 4 to 0. The road count
  climbed from 312 runs to 428 while the opened count held at 210: the crew was
  on the wall and the houses, which is the stated order, so paving by hand was
  not seen in the window and rests on its tests.
- The meal errand fires in a world: at a worst hunger of 64 the info line read
  "3 off to eat". The idle line named what a player had watched: two builders
  still for 49 and 38 seconds at a site that has them, errand none -- the stall
  assist laying one block in five while the crew stands, which is build pacing
  and was left alone on purpose. No SIZE MISMATCH, no exception, no ruin.
- The town manager paced 42 to 60 passes a minute with a player in town, 57.7
  unwatched under step load -- unchanged by any of this.

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

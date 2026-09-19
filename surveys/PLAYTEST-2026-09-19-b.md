# Playtest — 2026-09-19 (b): re-test of F1–F15

Two fresh worlds on `main` at `4242954`, NeoForge 26.2, dedicated server on 25565 with
RCON on 25575, plus a connected GUI client. Both worlds generated from nothing; the
client joined with `-PjoinServer=localhost:25565` and the commands went over RCON so a
burst could not be mistyped.

| Run | Seed | Starter town | Grown to |
|---|---|---|---|
| A | 8675309 | **Ashmarch**, human/burgher, raised at world start at (240, 74, 368) | step ~1580 (pop 60 at its peak) |
| B | 20260919 | **Ashmarch**, human/highland, at (−280, 64, 392) | step ~1640 (pop 12 at its peak) |

`neoforge/run/config/civilization-server.toml` was checked first and had
`worldgen.region = 1024`, the shipped default. `debug.commands_enabled = true`, so the new
`DAWNBELL`, `CARAVAN` and `ITEMPOP` lines were available. Harness settings:
`pause-when-empty-seconds=-1`, `view-distance=12`, `difficulty=normal`,
`spawn-monsters=false`, `generate-structures=false`.

**Two harness notes that shape the results.**

1. I lost the first 500 steps of run A to my own mistake: the player was left in
   **spectator**, and `playersWithin` excludes spectators, so the town grew unwatched.
   That is how run A reached pop 60 while run B, grown correctly with a creative player
   standing in it, never got past 12. The difference is itself the biggest finding in this
   report — see **N1**.
2. Both towns **starved to death** during the run (run A at step 1213, run B at ~1590), so
   the later people-side items were measured on towns repopulated with `/civ populate`.
   Every such row says so. `/civ populate` names people `Farmer 6`, which is why the
   screenshots carry placeholder names; the naturally-grown people had real ones
   (`Steven Bergemann`, `Trijntje Meulenaar`, `Cailean Cairney`).

---

## The table

| # | Item | Verdict | Evidence |
|---|---|---|---|
| F1 | Starter town inside the 256–512 band | **PASS** | join message: `Ashmarch — 364 blocks SE` (A), `Ashmarch — 355 blocks SW` (B) |
| F2 | Boards raised and inscribed | **FAIL** | `dressing: 0 of 168` (A) / `0 of 64` (B); sign sweep finds **one** sign per town, both **blank on both faces** |
| F3 | The caravan arrives | **PASS** | `CARAVAN Ashmarch arrives at 169, 69, 256`; two pack llamas photographed on the road |
| F4 | The dawn bell is found | **PASS** | `DAWNBELL Ashmarch day 1 bell 258, 70, 526` — the exact block a `clone …filtered minecraft:bell` sweep found |
| F5 | Rain gets outdoor workers indoors | **FAIL** | cover 14 of 19 clear → 14 of 18 in rain; people standing in the open went **up**, 9 → 12 |
| F6 | `/civ step 800` survives | **PASS** | returned in 0.37 s, `50 a tick — 50 done, 750 to come`, drained in ~6 s, no watchdog, no crash report |
| F7 | The starter is never hostile | **PASS** | home region draws `human/burgher radial_concentric` (A) and `human/highland thorp` (B) |
| F8 | Smoke per building, through the ridge | **PASS, thin** | plume photographed over a cottage chimney and a puff over the hearth ridge; one particle per pot every ~2 s |
| F9 | People walk home; nobody at the tree line | **PASS** | at `time set 18000`, **31 of 31** citizens within 60 blocks of the centre; `0 could not reach a bed` |
| F10 | One nameplate, crosshair-only, ≤4 blocks | **PASS** | five settlers within 3 blocks, exactly one plate (`Builder 18 — Builder`) on the one under the crosshair; none at 6 blocks |
| F11 | Buildings are not buried | **FAIL** | `/civ audit`: **3 of 30** buried (A), **11 of 16** buried (B), the mill under 7 courses |
| F12 | A camp leaves trees standing | **FAIL** | run A `2 trees standing` from step ~120 onward; run B **`0 trees standing, 10 coming up`** at step 1642 |
| F13 | ITEMPOP litter | **PARTIAL** | 215/min → **39.2/min** measured over a controlled 153 s window; 80 of the last 100 are still saplings and sticks |
| F14 | `/civ sites` names what will be raised | **PASS** | r(−1,−1) draws `goblin/mire goblin_camp`, the player listing reads `a Vale ring streets` |
| F15 | The person panel reads one hunger | **PASS** | `Carpenter · hunger 58/99 (hungry)` with no contradicting errand line |
| — | Roads caveat: "nothing is there on arrival" | **PASS** | a full street network is on the ground within **3 seconds** of a player arriving |

---

## Evidence, per row

### F1 — PASS

Run A join message, verbatim from `clientA.out` (newlines unescaped):

```
Settlements within 2048 blocks:
  Ashmarch — 364 blocks SE
  a Vale ring streets — 775 blocks NW (not raised yet)
  a Burgher radial concentric — 781 blocks SW (not raised yet)
  a Norman green — 1453 blocks SW (not raised yet)
  a Highland thorp — 1489 blocks SE (not raised yet)
```

Run B join message, verbatim:

```
Settlements within 2048 blocks:
  Ashmarch — 355 blocks SW
  a Vale ring streets — 809 blocks NW (not raised yet)
  a Burgher crossroads — 1703 blocks NW (not raised yet)
  a Burgher crossroads — 1917 blocks SW (not raised yet)
```

`/civ sites` shows the home region's own site was taken in both worlds rather than
abandoned for a neighbour — the whole of the fix:

| Run | Home region | Site | Outcome |
|---|---|---|---|
| A | r(0, 0) x=320 z=320 `human/burgher radial_concentric` | 387 m | `founded at x=240 z=368` — 364 blocks |
| B | r(−1, 0) x=−320 z=320 `human/highland thorp` | 346 m | `founded at x=−280 z=392` — 355 blocks |

747 → 364 and 808 → 355. Exactly one town is raised inside 1024 in both worlds.

### F2 — FAIL

The mod's own counter says it plainly, on both seeds, for the whole life of both towns:

* Run A, step 1580: `dressing: 0 of 168 raised … square 0/1 yard 0/19 woodpile 0/1
  haystack 0/9 crates 0/2 hedge 0/41 avenue_tree 0/63 signpost 0/8 grave 0/24`
* Run B, step 1642: `dressing: 0 of 64 raised … square 0/1 … signpost 0/5 inn_sign 0/1 grave 0/24`
* `/civ dressing` → `Ashmarch: 0 of 168` and `Ashmarch: 0 of 64`. Nothing has been raised
  to list.

It is not timber and it is not the priority chain. I watched run A for **160 seconds with
the town watched and the reason line empty** — no `(waiting: …)` clause at all, wood stock
steady at 117–134 against a floor of 32, lamps 342 of 356 (96%, inside `keepingUp`) — and
`piecesRaised` did not move once, from `0 of 150` to `0 of 155`. The gate is open and
nothing raises anything.

Block sweeps (`clone … filtered #minecraft:all_signs`, 29-block boxes, validated each time
against a planted control sign that returned `cloned 1 block(s)`):

* **Run A**, x 150..300, z 340..570, y 60..120, 144 boxes: **1 sign**, at (261, 67, 516), the inn.
* **Run B**, x −330..−220, z 350..490: **0 signs**. Extending west over the inn,
  x −360..−320, z 410..470: **1 sign**, at (−341, 75, 437).

Both are blank:

```
261, 67, 516   front_text {messages: ["", "", "", ""]}   back_text {messages: ["", "", "", ""]}
-341, 75, 437  front_text {messages: ["", "", "", ""]}   back_text {messages: ["", "", "", ""]}
```

No square board, no crossing post, no headstone — run A had **24 graves planned** and two
towns' worth of dead by the end. Photograph: `playtest2_a_inn_sign_blank.png`, the inn
front with the blank board over the door.

### F3 — PASS

The new `CARAVAN` lines carried the whole diagnosis. With the player at spawn the refusal
was `nobody within 96.0 of the gate 169, 69, 256 or the inn (261, 64, 517)` — so the
inn-side test the fix added is present and working. With a player in the inn yard the
refusal became `no footing at the edge (169, 74, 256)` for two windows, and then:

```
[09:17:42] CARAVAN Ashmarch arrives at 169, 69, 256
[09:21:42] CARAVAN Ashmarch arrives at 169, 69, 256
```

Standing at the gate: `@e[type=minecraft:llama,distance=..120]` and
`@e[tag=civilization_caravan,distance=..120]` both matched on **69 of 70 consecutive
one-second polls**, and `@e[type=civilization:person,tag=civilization_caravan]` matched too.
Photograph: `playtest2_caravan_llamas.png` — two pack llamas, one chested, on a lead,
coming up the road past the fences. Run B repeats it: `CARAVAN Ashmarch arrives at
−280, 64, 377`, six windows in a row.

One caveat that belongs with the fix rather than against it — see **N4**: run A's gate is
**277 blocks from the inn the caravan is calling at**, so a player waiting in the inn yard
is told a caravan called and never sees it.

### F4 — PASS

`clone … filtered minecraft:bell` around the hall at (258, 65, 532) found exactly one bell,
at **(258, 70, 526)** — again six blocks off the origin column and five up, this time in z.
Crossing a day boundary with a living town:

```
[08:50:14] DAWNBELL Ashmarch day 0 bell NOT FOUND — the morning is silent   (before the hall was built, step 105)
[09:11:32] DAWNBELL Ashmarch day 1 bell 258, 70, 526
```

The finder returns the exact block the sweep found. (Run B never finished its hall —
`civilization:town_hall 0/85 (0%)` — so it has no bell and printed no `DAWNBELL` line.)

### F5 — FAIL

Run A's starter is **no longer a savanna** — `execute if biome ~ ~ ~ minecraft:birch_forest`
→ **Test passed** at Ashmarch — so the re-siting incidentally fixed the harness problem the
predecessor hit. Both seeds get rain. Run B was used for the census (highland thorp on
coastal grass, real rain confirmed by photograph, `playtest2_b_rain_ground.png`, unmistakable
streaks).

Census of every embodied citizen within 90 blocks of the centre, asking per person whether
any non-air block stands 2–7 above them:

| | embodied | under cover |
|---|---|---|
| clear, noon | 19 | 14 |
| 75 s into `weather rain 12000` | 18 | 14 |

No movement toward cover. A second measurement, with the player standing between the town
and the farm so both ends were embodied, counted people standing under open sky:

| | at the farm plot | standing in the open |
|---|---|---|
| clear, noon | 3 | 9 |
| raining | 0 | **12** |
| clear again | 0 | 9 |

So the rows do empty in the wet — but the people who leave them stand outside in it, and
the open-air count goes up rather than down. The "stop working" half of the fix is visible;
the "go indoors" half is not. (The farm stayed empty after the rain stopped, so I cannot
attribute the 3 → 0 to the weather alone.)

### F6 — PASS

```
> civ step 800
Running 800 simulation step(s) at most 50 a tick — 50 done, 750 to come; 818 total
```

Returned in **0.37 s**. The books went 768 → 1569 within six seconds and the server stayed
up: RCON answered every poll for the next minute, no `watchdog` or `single server tick took`
line anywhere in the log, and no crash report newer than the predecessor's
`crash-2026-09-19_04.28.21-server.txt`. The command is no longer a loaded gun *for the
server*. It is still one for the town — see **N2**.

### F7 — PASS

`/civ sites` in both worlds:

```
A:  r(0, 0)   x=320  z=320  civilization:human/burgher radial_concentric  387m  founded at x=240 z=368
B:  r(-1, 0)  x=-320 z=320  civilization:human/highland thorp             346m  founded at x=-280 z=392
```

Seed 20260919's home region drew `orc/warhost orc_ring` in the predecessor's run and now
draws a highland thorp, exactly as the changelog claims. Neither join message carries a
`— hostile` marker.

### F8 — PASS, thin

`playtest2_b_dusk_smoke.png`: a clear grey column rising from the stone chimney stack of the
cottage left of centre, and a shorter wisp over the building to its right, at `time set
14000` from about 15 blocks. A second frame catches a puff directly at the apex of the
hearth's ridge, which is the flue-less-roof case. A control frame taken at the same time
**400 blocks from any building** shows the same scattered white ambient specks and no
column, so the columns are the mod's and the specks are not.

The plume is very thin: `SMOKE_EVERY = 2` manager passes and one particle per pot, so about
one particle every two seconds per chimney. Most still frames of a roof show nothing at all.
It reads as an occasional wisp rather than a chimney going.

### F9 — PASS

Run B, repopulated to 31, `time set 18000`, player standing in the middle:

```
night: nobody in bed, 0 could not reach a bed, 31 awake (31 with something inside notice)
people embodied: 32 ; within 60 blocks of the centre: 31
furthest: (-222, 68, 424) 67 blocks
```

Against the predecessor's "not one citizen within 60 blocks" and "14 could not reach a bed".
Photograph: `playtest2_b_night_square.png` — a crowd standing on the lit street in the middle
of town at midnight. Nobody is in bed, but the town has 12 beds for 31 people, so that is the
`/civ populate` overload rather than the night.

### F10 — PASS

`playtest2_b_nameplate_crosshair.png`: three settlers placed 3 blocks in front of the player,
two more beside them, and **exactly one plate** — `Builder 18 — Builder` — on the one under
the crosshair. No overlap, nothing like `BrihtwaCuthberRingwJoaderTrader`.
`playtest2_b_nameplate_6m_none.png`: the same crowd at 6 blocks, no plate at all.

### F11 — FAIL

```
A:  Ashmarch: 30 buildings seen, 4 fault(s)
      hearth @ 240, 73, 395       buried — the ground stands up to 3 above its floor on every side
      lumber_camp @ 259, 69, 465  buried — …
      mine @ 83, 70, 381          buried — …
      house @ 164, 79, 565        no way in — 1 gap(s) in 32 wall columns

B:  Ashmarch: 16 buildings seen, 11 fault(s)
      bunkhouse, hearth, granary, lumber_camp, storehouse, two cottages, carpentry — buried, 3 courses
      mill @ -312, 69, 439        buried — up to 7 above its floor on every side
      inn @ -336, 72, 437         buried — up to 6
      town_hall @ -290, 64, 392   buried — up to 6
```

Run A is 3 of 30, about what the predecessor found (3 of 36). Run B, on terraced hillside, is
**11 of 16** — two thirds of the town, including the hall and the inn. Photograph:
`playtest2_b_buried_mill.png`, the mill sitting in a cut with grass shelves above its ground
floor on both sides.

Note both seeds bury the *seeded* buildings (step 0) as well as the grown ones, and the
changelog's fix is explicitly about "buildings raised as a town grows".

### F12 — FAIL

* Run A: `49 trees standing` at step 17, `2 trees standing, 83 coming up` by step ~120, and
  `2 trees standing` at every reading from there to step 1580, with
  `deeds: trees_felled=2323`.
* Run B at step 1642: **`wood: 0 trees standing, 10 coming up`** — the literal reading the
  fix exists to forbid.

The reserve does engage — run A's `trees_felled` froze at 2323 and the stand later regrew to
63 once the camp stopped cutting — but it holds at the floor of two, not at a quarter of the
stand as first counted (a quarter of 49 is 12). Run B reached zero.

What is better than before is the *picture*: run A is not a bowl of bare terraces, because
the forest outside the lumber camp's claim is untouched (`playtest2_a_overhead.png`). The
claim itself is bare.

### F13 — PARTIAL

Controlled window on run A, town working, items cleared first:

```
ITEMPOP 100 over 153s = 39.2/min
```

215.3/min before, 3.9/min claimed, **39.2/min measured**. A large improvement and not the
claimed one. The composition is the tell — the last 100 lines of the window:

```
birch_sapling 39, oak_sapling 28, stick 13, leather 6, egg 6, rotten_flesh 5, apple 3
```

80 of 100 are saplings and sticks, which is leaf decay off felled crowns — the exact thing
the changelog says was fixed by taking crowns down with the trunk. Only 20 are the livestock
drops the changelog says should be all that is left. Run B's lifetime average over 32 minutes
was 384 lines, about 12/min, on a town that felled far less.

### F14 — PASS

Run A's `/civ sites` debug table lists `r(-1, -1) x=-519 z=-481 civilization:goblin/mire
goblin_camp 775m unresolved`, and the player-facing listing above it reads `a Vale ring
streets — 775 blocks NW (not raised yet)`. Run B the same: `r(-2, -1) … goblin/mire
goblin_camp 1703m` is advertised as `a Burgher crossroads — 1703 blocks NW`. The listing now
names what will actually be raised.

### F15 — PASS

`playtest2_b_person_panel.png`:

```
Farmer 6 — Farmer
Carpenter · hunger 58/99 (hungry)
Empty-handed.
Their own belongings; nothing can be taken.
```

One hunger reading, and no `too weak to work` line under a `well fed` header. The panel does
still contradict itself in a different way — the title says Farmer, the body says Carpenter —
see **N7**.

### Roads caveat — PASS

Run A, player teleported into a town that had never been watched (all 13 buildings
`[PENDING placement]`, `0 visible as villagers`, player 364 blocks away at spawn):

| | roads | what is on the ground |
|---|---|---|
| T+3 s | 70 runs, 659 blocks | a laid dirt-path street under the player's feet with settlers walking on it |
| T+90 s | 78 runs, 761 blocks | the same, plus the outer runs |

`playtest2_a_on_arrival_ground.png` (3 seconds after arrival) and
`playtest2_a_on_arrival_overhead.png` (one minute later, y=200): a continuous network of
streets with a lane to every door, not "bare grass between scattered buildings". Run B is the
cleaner picture still — `playtest2_b_on_arrival_overhead.png`, taken before a single `/civ
step` was typed, shows a full lattice of lanes, fields and buildings. Both towns are laid out
and paved on arrival.

Road quality in both towns is good: A `roads: 176 runs, 1868 blocks, 44 of 47 buildings
joined`; B `52 runs, 647 blocks, 16 of 16 buildings joined`.

---

## Faults, worst first

**N1 — A town starves whenever a player is standing in it.**
This is the run. Same town, same 300 steps, the only difference being where the player stood:

| | granary | grain |
|---|---|---|
| player 600 blocks away, 300 steps | 0 → 157 → 329 → **449** | 12 reaching the mill/granary |
| player standing in the town, 300 steps | 467 → 360 → 250 → **144** | frozen at 15 on farms, **0 at the mill/granary** at every reading |

Under a player's eye the grain never leaves the field. I ruled out the two obvious
confounders: with `weather clear` held and `/civ threat 0` re-issued every five seconds for
four bursts, the watched town still went 134 → 47 → 1 → 0 and died. The wheat is real and
growing (72 `minecraft:wheat` blocks at ages 0–6 around the farm plot), the farmers are
embodied (`31 visible as villagers`), and `workFarmers` is being reached — nothing is
harvested anyway.

The consequence is not subtle. Run A: pop 60 at step 706, `[step 1205] Steven Bergemann
starved … [step 1213] Ashmarch has no one left`. Run B, grown correctly from step 0 with a
player present, never got past pop 12 and its granary went 132 → 48 → 0 by step 334. The town
map says it out loud: **`THIS TOWN IS FAILING / The larder is empty / No food anywhere in the
town`** (`playtest2_b_townmap_failing.png`). A player who walks to the world's one guaranteed
town and stays there watches it die.
*Likely files:* `neoforge/src/main/java/com/civilization/neoforge/view/PersonEntityManager.java`
(`workFarmers`, ~line 2039, and the `FarmWorker.work` call under it) and
`common/src/main/java/com/civilization/sim/work/FoodPlanner.java` — the watched harvest
credits nothing and nothing hauls the grain to the mill.

**N2 — `/civ step N` starves the town it is stepping.**
The watchdog fix (50 a tick) decouples book time from wall time completely: `/civ step 800`
advances the ledger 800 steps in about 120 real ticks, during which the crew — who in a
watched town do *all* the work — get six seconds. The town eats 800 steps of food and does
six seconds of farming. Run A's entire population died inside that one command. Given N1 this
is the same fault seen from the other end, but it is worth naming separately because `/civ
step` is the tool every tester and every changelog measurement uses, and it now silently
produces a corpse where it used to produce a crash.
*Likely file:* `neoforge/src/main/java/com/civilization/neoforge/command/StepBurst.java`.

**N3 — The dressing is still zero, and now for no stated reason.**
`0 of 168` (A) and `0 of 64` (B), for the entire life of both towns, with the new
`(waiting: …)` clause **absent** during a 160-second watched observation in which nothing
was raised. One blank sign per town, exactly as before. The three new report lines are a real
improvement — they proved the timber gate and the priority gate were *not* the cause — but
the work itself never happens. No square, no crossing post, no headstone, in a town with 24
graves planned and its whole population in them.
*Likely files:* `common/src/main/java/com/civilization/sim/work/PublicWorks.java`
(`DressingWork`, ~line 851, and `of()`'s ordering at ~line 84 — `DressingWork` is last behind
five works that always have something to do, and `HANDS_KEPT_ON_BUILDINGS = 1`), and
`neoforge/src/main/java/com/civilization/neoforge/view/Foreman.java` (~363, ~747).

**N4 — The caravan calls at the inn and arrives at the far gate.**
Run A: the inn is at (261, 64, 511), the arrival is logged and photographed at
(169, 69, 256) — **277 blocks away**, at the far end of the longest opened street. A player
standing in the inn yard for two full visit windows saw nothing; I only found the wagon by
teleporting to the gate. The player-side gate the fix added works (the refusal moved past
"nobody within 96 of the gate or the inn"), but what the player is then invited to watch is
not where the wagon appears. Run B's town is small enough that its gate (−280, 64, 377) is
15 blocks from the middle, which is why it reads correctly there.
*Likely file:* `neoforge/src/main/java/com/civilization/neoforge/view/Caravans.java` — the
wagon should walk to the inn, or arrive at the nearest gate to it.

**N5 — Two thirds of run B's town is buried.**
11 of 16 buildings, the mill under 7 courses, the inn under 6, the town hall under 6
(`playtest2_b_buried_mill.png`). The shelf rule the changelog added is for buildings "raised
as a town grows"; nine of these eleven are step-0 seeded buildings, and the hall and inn are
grown ones. On the terraced ground seed 20260919 gives, the whole town is sunk.
*Likely file:* whatever sites the seeded plan, alongside the grown-building path the shelf
rule was added to.

**N6 — A town 500 blocks across, whose far half can never be built.**
Run A ended with buildings from (−257, 66, 355) to (269, ·, ·) and (−7, 95, 838) — a span of
424 × 497 blocks and a maximum radius of **429 blocks from the centre**, against the
predecessor's 174 × 145. Eight buildings sat `[PENDING placement]`, all of them 380–500
blocks out, and the build queue head read `WAITING ON HANDS: waiting for hands at
(−206, 355), out of sight`. Watchedness is judged for the whole claim, so with a player at
the centre the outskirts are neither built by hand (nobody can reach them) nor by the clock
(the town counts as watched). Photograph: `playtest2_a_sprawl_400m.png`, houses and lanes
400 blocks from the middle.
*Likely files:* the plot-siting path in `common/src/main/java/com/civilization/sim/settlement/`
and the watched/unwatched split in `PersonEntityManager`.

**N7 — The nameplate and panel title carry a stale trade.**
`playtest2_b_person_panel.png`: the title reads `Farmer 6 — Farmer` and the line under it
reads `Carpenter · hunger 58/99`. The greetings show the same name-vs-trade drift
(`Farmer 8 — Guard`, `Farmer 6 — Miller`). Jobs are reassigned constantly — run A's
`jobs:` line moved guards 7 → 10 → 12 → 14 inside four minutes — so the plate a player reads
is routinely a trade the settler no longer has. Seen on a repopulated town, so the *names*
are `/civ populate`'s; the mismatch between the title's trade and the body's is not.

**N8 — Litter is 39 a minute, and 80 per cent of it is still tree decay.**
215/min → 39.2/min is a real fix, but the changelog's claim that "what is left is livestock"
is not what the log says: of the last 100 lines in the measured window, 39 are birch saplings,
28 oak saplings and 13 sticks. Felled crowns are still decaying into saplings and sticks
after the trunk has gone.
*Likely file:* `neoforge/src/main/java/com/civilization/neoforge/world/TownBlocks.java`.

**N9 — The rain stops the work and leaves the workers standing in it.**
Cover did not move (14 of 19 → 14 of 18) and the number under open sky rose from 9 to 12.
Whatever takes the farmers off the rows is not handing them a doorway.
*Likely file:* `common/src/main/java/com/civilization/sim/person/Leisure.java` and the
`rainedOff` callers in `PersonEntityManager`.

**N10 — Both seeds' starter town is called Ashmarch.**
Seed 8675309 and seed 20260919 raise a `human/burgher` town and a `human/highland` town, in
different biomes 600 blocks apart in different worlds, and both are named **Ashmarch**. The
name pool for a town is apparently not drawn off the seed. Cosmetic, but it is the first word
a new player reads and it will be the same word in every world.

**N11 — A house with no way in.**
`/civ audit` on run A: `civilization:house @ 164, 79, 565: no way in — 1 gap(s) in 32 wall
columns; outside the first gap north −1=minecraft:leaf_litter(nothing to stand on) |
+0=minecraft:air | +1=minecraft:air`. One of 30. The audit catches it, which is the point of
the audit; nothing puts it right.

**N12 — Smoke is one particle every two seconds.**
`SMOKE_EVERY = 2` and one particle per pot means a chimney is usually showing nothing at all.
The fix is correct — per building, through the ridge, both photographed — but at this rate a
player walking through a town at dusk will read the roofs as cold.

**N13 — A detached chimney stack.**
`playtest2_b_buried_mill.png`: the mill's stone flue stands as a free-floating column a block
clear of the roof it belongs to, going up from ground level beside the building rather than
out of it. Visible on two other roofs in the same frame.

---

## Files

Screenshots, all under `surveys/`:

| File | What |
|---|---|
| `playtest2_a_on_arrival_overhead.png` | run A three seconds after a player first arrives — streets already laid |
| `playtest2_a_on_arrival_ground.png` | the same arrival at head height |
| `playtest2_a_overhead.png` | run A's town at step 1580 |
| `playtest2_a_inn_sign_blank.png` | the inn, its one board blank on both faces |
| `playtest2_a_sprawl_400m.png` | houses and lanes 400 blocks from the centre |
| `playtest2_caravan_llamas.png` | two pack llamas on the road, in a live visit window |
| `playtest2_b_on_arrival_overhead.png` | run B before a single `/civ step` — a full lane network |
| `playtest2_b_overhead.png` | run B's town in daylight |
| `playtest2_b_townmap_failing.png` | the town map: `THIS TOWN IS FAILING / The larder is empty` |
| `playtest2_b_night_square.png` | midnight, 31 of 31 citizens in the middle of town |
| `playtest2_b_nameplate_crosshair.png` | one plate, on the settler under the crosshair |
| `playtest2_b_nameplate_6m_none.png` | the same crowd at 6 blocks, no plate |
| `playtest2_b_person_panel.png` | the person panel, one hunger reading |
| `playtest2_b_dusk_smoke.png` | a plume over a cottage chimney at dusk |
| `playtest2_b_rain_ground.png` | real rain over the town |
| `playtest2_b_buried_mill.png` | the mill sunk into the hillside, and a detached flue |

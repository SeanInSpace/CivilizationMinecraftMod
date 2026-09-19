# Playtest — 2026-09-19 (c): re-test of N1–N13

Two fresh worlds on `main` at `08d6763`, NeoForge 26.2, dedicated server on 25565 with RCON
on 25575, plus a connected GUI client joined with `-PjoinServer=localhost:25565`. Both worlds
generated from nothing. `neoforge/run/config/civilization-server.toml` was checked before the
first run and read `worldgen.region = 1024`, `observed_radius = 96.0`,
`unwatched_yield_percent` all 0, `debug.commands_enabled = true`. Harness settings:
`pause-when-empty-seconds=-1`, `gamemode=creative`, `force-gamemode=true`, `difficulty=normal`,
`view-distance=12`, `spawn-monsters=false`, `generate-structures=false`.

| Run | Seed | Starter town | Grown to |
|---|---|---|---|
| A | 8675309 | **Fairwater**, human/burgher `radial_concentric`, raised at world start at (240, 74, 368) | step 2814 (pop peaked 201) |
| B | 20260919 | **Greenfield**, human/highland `thorp`, at (−280, 64, 392) | step 3213 (pop 185) |

**The player was in creative and standing in the square for every watched measurement.**
Spectator was used only for camera work, never for a count. Every burst was `/civ step 100`
followed by a 15 s real-time gap, so book time and hand time were the same on both sides of
each comparison; the "unwatched" side moved the player 600–800 blocks away and ran the
identical protocol.

**One harness note.** My first `/civ info` reads were truncated: a long RCON reply arrives in
several 4096-byte packets and a naive client reads one. Two early readings in this run
(`civ audit` "0 faults", and a `civ dressing` that came back as the tail of `civ info`) were
wrong for that reason and have been re-taken. Anyone reading a `/civ` command over RCON should
drain the socket, not read one packet.

---

## The table

| # | Item | Verdict | Evidence |
|---|---|---|---|
| N1 | A watched town does not starve | **PARTIAL** | Run A watched 300 steps in the square: granary **208 → 568 → 732 → 804**, nobody starved — the 2026-09-19-b collapse is gone. But run B, watched 650 steps from step 18, held **pop 12, granary 722 → 538, and `0 at the mill/granary` at all six readings**; moved 642 blocks away it went pop 11 → 28, granary → 1000, mill grain 0 → 74 → **184**. See **C1**. |
| N2 | `/civ step 800` does not produce a corpse | **PASS** | A: watched, pop 84 → 201, granary 1394 → 1764 at the 300-step mark, `0` watchdog lines, no crash report. B: watched 800 pop 64 → 143, **0 starved**; unwatched 800 immediately after, pop 143 → 185, **0 starved**. |
| N3/F2 | Boards raised and inscribed | **PARTIAL** | Both towns raise real, inscribed boards: A `(230, 73/75/77, 467)` and B `(−278, 67/68, 421-423)` read `Fairwater · ^ to the hall · north` / `Greenfield · ^ to the hall · north` under `data get block`. The `/civ info` reason line is never empty. But **the square board does not exist in the world on either seed** (**C3**), and on run B the dressing stood at **0 of 81 through 1,287 watched steps** (**C1**). |
| N4 | The caravan arrives near the inn | **PASS** | A: `CARAVAN Fairwater arrives at 175, 77, 534`, inn at (186, 76, 518) — **19 blocks**. B: `arrives at −343, 72, 444`, inn at (−336, 72, 437) — **10 blocks**. A `Caravan from the road` entity plus `llama Count: 2` and `civilization_caravan Count: 3` were selected 8 blocks from B's inn. |
| N5/F11 | Buildings are not buried | **FAIL** | A: `0 of 13` at step 0, `0 of 31` at step 2814 — clean. B: **`2 of 15` at step 0** (mill under 7 courses, inn under 6 — the same two buildings and the same coordinates the predecessor logged), still `2 of 15` at step 659, and **`27 of 48` by step 3213**, including the town hall under 6 and twenty-four houses under 4. |
| N6 | The far half of a large town gets built | **FAIL** | A at step 1194: **31 of 57 `[PENDING placement]`**, and `/civ audit` names seven of them `recorded here, but nothing stands on the ground — and it was the same last sweep`. By step 2176 Fairwater spans **x −971 … +871, z … 1454** with **166 pending**, five herds reading `NOTHING TO FEED THEM`, four mines `CUT OUT`, and a miller walking to (341, 74, 999). Greenfield ends **1,384 blocks wide** with **221 pending**. At step ~600 both towns were still compact (0 pending), so this is a size threshold, not a cold start. |
| N7 | Nameplate and panel carry the same trade | **PASS, unproven refresh** | `playtest3_a_nameplate_crosshair.png` / `playtest3_a_person_panel.png`: plate `Quirin Lindhorst — Lumberjack`, panel title the same, panel body `Lumberjack · hunger 29/99 (well fed)`, greeting `Quirin Lindhorst — Lumberjack: "Getting dark. I'm for home."` — no drift anywhere. I could not exercise the refresh: over a 100-step and a 300-step burst, **0 of 38 tracked settlers changed trade**, and there is no `/civ` reassign command. |
| N8/F13 | Litter | **FAIL, worse** | Controlled window on run A, items cleared first, town working in daylight: **2,259 `ITEMPOP` lines over 540 s = 251.0/min**. Composition: `oak_sapling 1757, stick 462, leather 16, egg 15, rotten_flesh 5, lead 2, arrow 1, bone 1`. Of the last 100 lines, **100 are saplings and sticks**. 215/min → 39.2/min → **251/min**, and the claim that what is left is livestock is 1.7 % true. |
| N9/F5 | Rain gets workers under a roof | **PASS** | Run A, `execute if biome 240 74 368 minecraft:birch_forest` → Test passed, so rain really falls. Cover within 90 blocks of the centre: clear noon **1 of 24**; 75 s into rain **0 of 23**; 135 s into rain **11 of 21**, and the shelterers are standing at (261–262, 70, 432–433) — the granary doorway at (262, 69, 432). `playtest3_a_rain.png`. It takes about two minutes and half the town stays out in it, but the doorway is real. |
| N10 | Starter names vary with the seed | **PASS** | Seed 8675309 → **Fairwater**; seed 20260919 → **Greenfield**. (`Ashmarch` still exists in the pool — it is the name the 1,692-block neighbour drew in run B.) |
| N11 | A house with no way in | **PASS** | `no way in` count: **0** on run A at step 1194, **0** at step 2814, **0** on run B at step 659 and at step 3213. |
| N12 | Smoke is a plume | **PASS** | `playtest3_a_dusk_smoke.png`: six or seven grey puffs stacked over one stone chimney at `time set 13500`, plus a second wisp on the next roof. Visible as a plume in daylight too (`playtest3_a_nameplate_crosshair.png`). |
| N13 | Detached chimney stacks | **not reproduced** | Every chimney photographed on both seeds rises out of its own roof ridge — `playtest3_a_dusk_smoke.png`, `playtest3_b_buried_mill.png`. Listed as unfixed; I could not find one detached. |
| F12 | A camp leaves trees standing | **FAIL** | Run B's first lumber camp read **`wood: 2 trees standing`** at step 143 and at eleven of the thirteen readings up to step 1583 — 1,400 steps parked on the floor of two, the literal reading the reserve exists to forbid. Run A held 18 per camp to step 1194 and one of its seven camps was reading 2 at step 2176. Both recover once a camp stops cutting (B reached 447 by step 2396). `trees_felled=63218` on run A. |
| F1/F7 | Starter inside the 256–512 band, never hostile | **PASS** | A join message `Fairwater — 366 blocks SE`; B `Greenfield — 351 blocks SW`. `human/burgher radial_concentric` and `human/highland thorp`; no `— hostile` marker on either. |
| F14 | `/civ sites` names what will be raised | **PASS, with a contradiction** | B's player listing advertises `a Burgher crossroads — 1696 blocks NW` while the operator table on the same screen says `r(-2, -1) … civilization:goblin/mire goblin_camp`. I flew there and settled it: `WORLDGEN raised Ashmarch at (−1576, 118, −564) — civilization:human/burgher laid out as crossroads`. **The player line is right and the debug table is wrong.** |
| — | Roads on arrival | **PASS** | Run B, town never watched, all 14 buildings `[PENDING placement]`, player 351 blocks away. At **T+3 s**: houses, hearth, lanterns and a citizen standing in front of the player (`playtest3_b_on_arrival_t3.png`); `roads: 50 runs, 638 blocks, 15 of 15 buildings joined`, **0 pending**. The overhead a minute later is a full lane network (`playtest3_b_on_arrival_overhead.png`). |
| — | Night census at 18000 | **PARTIAL** | A: `nobody in bed, 0 could not reach a bed, 61 awake`, 33 embodied within 120 blocks, 13 under cover, 20 in the open. B: `nobody in bed, 0 could not reach a bed, 60 awake`, 31 embodied within 90 blocks, 12 under cover, 19 in the open. Nobody is at the tree line — but with the town reading `pop 185/380 housed`, **not one settler is in a bed**, under a line that says none of them failed to reach one. See **C6**. |
| — | Nameplates | **PASS** | Exactly one plate, on the settler under the crosshair, with four others within 4 blocks carrying none (`playtest3_a_nameplate_crosshair.png`); no plate at all on a crowd at 4 blocks off-crosshair (`playtest3_a_street_lamps.png`). |
| — | The dawn bell is found | **PASS** | A: `DAWNBELL Fairwater day 1 bell 234, 78, 368`; B: `DAWNBELL Greenfield day 1 bell −296, 69, 392`. Both verified with `execute if block … minecraft:bell` → Test passed. |

---

## Evidence for the two headline rows

### C1 — the watched/unwatched split, measured three times

**Run B is the clean experiment**: the same town, the same 15-second-gapped bursts, the player
moved and moved back.

| | steps | pop | granary | grain at the mill | lamps | dressing |
|---|---|---|---|---|---|---|
| watched, in the square | 18 → 659 | 12 → **11** | 510 → 722 → **538** | **0, 0, 0, 0, 0, 0** | **22/140 → 22/140** | **0/55 → 0/58** |
| unwatched, 642 blocks off | 659 → 972 | 11 → **28** | 538 → **1000** | 0 → 74 → **184** | 22 → **99** | 0/78 |
| watched again | 972 → 1287 | 28 → 39 | 1000 flat | 160, 156, 138 | **99/218 → 99/218** | **0/81** |
| unwatched, one burst | 1287 → 1397 | 39 → 47 | 998 | 176 | 99 → **207** | 0/109 |

And the same split inside two back-to-back `/civ step 800` bursts on that town:

| per 800-step burst | watched (player in the square) | unwatched (player 800 blocks off) |
|---|---|---|
| lamps | 360/360 → 362/533 — **+2** | 362/533 → **1220/1220 — +858** |
| dressing | 32/144 → 33/234 — **+1** | 33/234 → **90/448 — +57** |
| pop | 64 → 143 | 143 → 185 |
| starved | 0 | 0 |

Run A shows the food half of it once and then grows out of it: watched from step 45, granary
**208 → 568 → 732 → 804** with `0 at the mill/granary` at three of four readings, pop frozen at
12 with two families sitting at `growth 24/24` and `pop 12/12 housed`; 300 unwatched steps took
it to pop 27 and lamps `0/144 → 192/196`; after that it kept pace while watched.

So the ledger fix landed — a watched town no longer *starves* — but **the hands still do not
do the work the clock would have done**, and the shortfall lands on lamps, on the dressing
gated behind lamps, and on the houses that births are gated behind.

### C2 — the sky columns

`playtest3_a_sky_columns.png` and `playtest3_b_sky_columns.png`. Both towns grow solid columns
out of their square to the build limit:

* Run A, probed: eleven columns in a ring round the square at (254, 368) reach **y = 250**;
  the column at (252, 368) is solid from **y 60 to y 318**. It is `minecraft:stone_brick_wall`
  at y 90 and y 150, `minecraft:stone_bricks` at y 250 and a fence block at y 300.
  A second shaft stands at (232, ·, 508). The wide shot catches seven of them, one of which is
  a lattice of fences dozens of blocks across.
* Run B, at step 1503 with only 15 of 120 dressing pieces raised: (−276, ·, 388) is solid from
  **y 58 to y 318**, with a twin at (−276, ·, 396) — both one block from the square at
  (−276, 64, 392).

### C3 — the square board is in the ledger and not on the ground

`/civ dressing` on run A: `#2 square at (254, 74, 368) facing 0 reads [Fairwater, town,
founded day 1, ]`. Probing every block in a 9 × 17 × 9 box round that point
(`data get block`, 1,539 blocks, y 64 … 80) finds **no sign block entity at all**. The column is
air from y 70 up and the ground is at y 69 — the piece is recorded five blocks above its own
terrain. Run B repeats it exactly: `#0 square at (−276, 64, 392) reads [Greenfield, town,
founded day 1, ]`, and 729 probed blocks round it contain no sign. The crossing posts in the
same listing *are* real, so this is the square piece specifically.

---

## Faults, worst first

**C1 — A watched town still does not raise its lamps, and the dressing is gated behind them.**
Greenfield stood at `lamps: 22 of 140` for **650 watched steps** and `99 of 218` for another
**300**, with `dressing: 0 of 81 (waiting: lamps 99 of 218 standing)` the whole time, on stores
of `wood=1296 stone=912 iron=301`. Move the player 642 blocks away and 77 lamps go up in 300
steps; a watched `/civ step 800` raises **2** lamps and the unwatched 800 straight after it
raises **858**. Population is caught in the same net — pop 12 frozen for 650 watched steps with
ten families at `growth 72/72` and `39/44 housed`, then 11 → 28 the moment nobody was looking.
This is the 2026-09-19-b fault with the food stripped out of it: the ledger reconciliation fixed
the grain, and nothing was done for the works the clock used to draw.
*Likely files:* `common/src/main/java/com/civilization/sim/work/PublicWorks.java` (the lamp work
and `availableTo`'s ordering) and the watched branch in
`neoforge/src/main/java/com/civilization/neoforge/view/PersonEntityManager.java` /
`Foreman.java`.

**C2 — Both towns build a column of wall out of their square to the height limit.**
Solid from y 58–60 up to **y 318** — 260 blocks — in stone brick wall, stone bricks and fence,
eleven of them ringing run A's square and two beside run B's. They are visible from four hundred
blocks away and they are the first thing a player sees of the world's one guaranteed town
(`playtest3_a_sky_columns.png`, `playtest3_b_sky_columns.png`, `playtest3_a_sky_columns_close.png`).
Run B has them with only 15 of 120 dressing pieces raised, so it is not a late-game effect.
*Likely file:* whatever lays `wall` segments — run A's `AUDIT` line carries `wall=366/784`,
and the square is reserved ground, so the wall course and the reserved square are being resolved
to the same column and stacked instead of walked.

**C3 — The square board is reported raised, inscribed, and is not there.**
`/civ dressing` prints its text on both seeds; `data get block` over 2,268 blocks around the two
listed positions finds no sign. Run A's listed y is 74 and the ground under it is 69. The
crossing posts from the same listing are real and inscribed, so the ledger is right about them
and wrong about the square. A player who reads `square 1/1` and walks to the middle of town
finds nothing.
*Likely file:* the square piece's placement in the dressing/`Furnishings` path — it takes the
settlement centre's y rather than the ground at its own column.

**C4 — Litter is 251 a minute and 98 per cent of it is tree decay.**
2,259 `ITEMPOP` lines in 540 s on a working run-A town with the items cleared first:
1,757 oak saplings, 462 sticks, 40 everything else. The predecessor measured 39.2/min and the
changelog claims ~4. Felled crowns are still decaying into saplings and sticks. With
`trees_felled=63218` by the end of run A this is the dominant source of dropped items in the
world.
*Likely file:* `neoforge/src/main/java/com/civilization/neoforge/world/TownBlocks.java`.

**C5 — A town grows to a kilometre across and then cannot feed itself.**
Fairwater at step 2176: buildings from **x −971 to +871 and z to 1454**, 166 `[PENDING
placement]`, `roads: 383 runs … 157 of 221 buildings joined`, five herds reading `NOTHING TO
FEED THEM — nothing breeds`, four mines `CUT OUT`, a miller with an errand `heading for
(341, 74, 999)`, and `grain: 1300 on farms, 12 at the mill/granary` against a granary of
706/13800. Sixty-three settlers starved to death in **the same second**, 12:30:59, inside a
300-step burst, and the population fell 201 → 142. Greenfield ends 1,384 blocks wide with 221 pending.
`/civ audit` names the symptom itself — `recorded here, but nothing stands on the ground — and
it was the same last sweep`, seven times on run A at step 1194.
*Likely files:* the plot-siting path in `common/src/main/java/com/civilization/sim/settlement/`
— nothing bounds how far a plot may be sited from the centre — and the haulage that is supposed
to bring grain in from a farm five hundred blocks out.

**C6 — Nobody ever gets into a bed.**
`night: nobody in bed, 0 could not reach a bed, 61 awake` on run A at `time set 18000`, and
`nobody in bed, 0 could not reach a bed, 60 awake` on run B, which read `pop 185/380 housed`.
The two clauses cannot both be doing their job: either the sleep step never runs or the
"could not reach" test is answering a question nobody asked. The predecessor saw the same line
and attributed it to `/civ populate` overload; these towns grew naturally and are under half
occupied.

**C7 — Seed 20260919 still buries its seeded buildings, and then buries the grown ones too.**
`2 of 15` at step 0 — `mill @ −312, 69, 439` under 7 courses and `inn @ −336, 72, 437` under 6,
the same two buildings at the same coordinates the predecessor logged — unchanged at step 659,
and **27 of 48** by step 3213, the town hall under 6 and twenty-four houses under 4
(`playtest3_b_buried_mill.png`). The shelf rule holds on run A's ground (0 of 31 at step 2814)
and does not hold on this terracing.

**C8 — A camp still parks on two trees.**
`wood: 2 trees standing` on run B from step 143 to step 2396 — 2,250 steps at the floor the
reserve was supposed to raise — and one of run A's seven camps reads 2 at step 2176. The stand
does recover once a camp stops cutting (run B reached 447 later), so the reserve engages; it
engages at two.

**C9 — `/civ sites`' operator table disagrees with the line the player is shown.**
`r(-2, -1) … civilization:goblin/mire goblin_camp` in the debug table, `a Burgher crossroads —
1696 blocks NW` in the listing above it. Flying there raises
`Ashmarch … civilization:human/burgher laid out as crossroads`, so the player line is the
correct one — but the table an operator reads to debug siting is telling him the opposite, and
the predecessor recorded the same contradiction as a pass.

**C10 — A long `/civ` reply is unreadable over RCON.**
`/civ info` and `/civ dressing` exceed one 4096-byte RCON packet, and Minecraft's own client
handles one packet per read. A tester who does not drain the socket gets a silently truncated
answer — my first `civ audit` read `0 fault(s)` where the real answer was seven, and a
`civ dressing` came back as the tail of the previous `civ info`. Worth a note in the debug
command's own help, since every measurement in these surveys comes through it.

---

## Files

| File | What |
|---|---|
| `playtest3_a_on_arrival.png` | run A at head height seconds after a player first arrives |
| `playtest3_a_overhead_step0.png` | run A from y=215 before a single `/civ step` — lanes already laid |
| `playtest3_a_sky_columns.png` | seven columns rising out of Fairwater to the build limit |
| `playtest3_a_sky_columns_close.png` | four of them from 150 blocks up, above the cloud layer |
| `playtest3_a_nameplate_crosshair.png` | one plate on the settler under the crosshair; a chimney plume behind him |
| `playtest3_a_street_lamps.png` | the same street from 4 blocks off-crosshair — no plate; lamp posts standing |
| `playtest3_a_person_panel.png` | the person panel: title, trade and hunger all agreeing |
| `playtest3_a_crossing_post.png` | the fingerpost at (230, ·, 467), inside a birch canopy |
| `playtest3_a_rain.png` | real rain over the town, settlers at the granary door and in the open |
| `playtest3_a_night_street.png` | midnight on a lit street, the columns silhouetted behind |
| `playtest3_a_dusk_smoke.png` | a plume off a stone chimney at `time set 13500` |
| `playtest3_b_on_arrival_t3.png` | run B three seconds after arrival — buildings, lanterns, a named settler |
| `playtest3_b_on_arrival_overhead.png` | the same town from y=150, a full lane network |
| `playtest3_b_buried_mill.png` | the mill sunk to its window line, under 7 courses |
| `playtest3_b_thorp_street.png` | Greenfield at ground level — log houses, chimneys, lanes, stock |
| `playtest3_b_sky_columns.png` | the same columns on the other seed, on a town with 15 of 120 dressing pieces |
